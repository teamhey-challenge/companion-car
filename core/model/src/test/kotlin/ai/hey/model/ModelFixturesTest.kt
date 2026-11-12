package ai.hey.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** Parses the shared protocol fixtures and checks that decode -> encode reproduces them. */
class ModelFixturesTest {
    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/protocol-fixtures/$name")!!.bufferedReader().use { it.readText() }

    private fun JsonElement.withoutNulls(): JsonElement = when (this) {
        is JsonObject -> JsonObject(filterValues { it !is JsonNull }.mapValues { it.value.withoutNulls() })
        is JsonArray -> JsonArray(map { it.withoutNulls() })
        else -> this
    }

    private inline fun <reified T> roundTrip(element: JsonElement) {
        val value = HeyJson.decodeFromJsonElement(serializer<T>(), element)
        val again = HeyJson.encodeToJsonElement(serializer<T>(), value)
        assertEquals(element.withoutNulls(), again.withoutNulls())
    }

    @Test fun carViewRoundTrips() {
        val element = HeyJson.parseToJsonElement(fixture("car-view-card-sent.json"))
        roundTrip<CarView>(element)
        val view = HeyJson.decodeFromJsonElement(serializer<CarView>(), element)
        assertEquals(TripState.CARD_SENT, view.state)
        assertEquals(Lang.Ja, view.passengerLang)
        assertEquals(1, view.pendingPins.size)
        assertEquals(PinProposalStatus.Pending, (view.dropoff as PassengerPin).status)
        assertEquals(PinProposalStatus.Pending, view.pendingPins.single().status)
    }

    @Test fun passengerViewRoundTrips() {
        roundTrip<PassengerTripView>(HeyJson.parseToJsonElement(fixture("passenger-view-in-trip.json")))
    }

    @Test fun arrivedPassengerViewRoundTripsWithTheNewSummaryFields() {
        val element = HeyJson.parseToJsonElement(fixture("passenger-view-arrived.json"))
        roundTrip<PassengerTripView>(element)
        val view = HeyJson.decodeFromJsonElement(serializer<PassengerTripView>(), element)
        assertEquals(2987, view.summary?.elapsedSec)
        assertEquals(52300, view.summary?.routeDistanceM)
        assertEquals(DropoffKind.Curated, view.agreedDropoff?.kind)
        assertEquals("Sogong-ro", view.agreedDropoff?.street?.latin)
        assertEquals("Side entrance (Sogong-ro side)", view.dropoff?.labelLocal)
    }

    @Test fun proposeCarriesTheStructuredFieldsOfACandidate() {
        val candidate = DropoffCandidate(
            "c1", DropoffKind.Entrance, EntranceType.Side, "3", StreetName("을지로", "Eulji-ro"),
            "을지로 쪽 옆 출입구", "을지로 쪽 옆 출입구", LatLng(37.5649, 126.9803), 78.0,
        )
        val sent = HeyJson.encodeToJsonElement(DropoffProposeData.serializer(), DropoffProposeData.of(candidate, DropoffReasonId.SideCloser))
        val expected = HeyJson.parseToJsonElement(
            """{"point":{"lat":37.5649,"lng":126.9803},"label":"을지로 쪽 옆 출입구","kind":"entrance","entranceType":"side","ref":"3","street":{"ko":"을지로","latin":"Eulji-ro"},"reasonId":"sideCloser","candidateId":"c1"}""",
        )
        assertEquals(expected, sent)
    }

    @Test fun clientEnvelopesRoundTrip() {
        val list = HeyJson.parseToJsonElement(fixture("envelopes.json")).jsonObject.getValue("clientToServer").jsonArray
        assertEquals(20, list.size)
        for (envelope in list) {
            val obj = envelope.jsonObject
            val data = obj.getValue("data")
            when (val type = obj.getValue("type").jsonPrimitive.content) {
                "join.approve", "join.decline" -> roundTrip<JoinDecision>(data)
                "card.submit" -> roundTrip<CardInput>(data)
                "card.update" -> roundTrip<CardUpdate>(data)
                "route.started" -> roundTrip<RouteInfo>(data)
                "route.failed" -> roundTrip<RouteFailedData>(data)
                "trip.progress" -> roundTrip<ProgressInfo>(data)
                "dropoff.propose" -> roundTrip<DropoffProposeData>(data)
                "dropoff.decide" -> roundTrip<DropoffDecideData>(data)
                "request.spoken" -> roundTrip<RequestSpokenData>(data)
                "reply.send" -> roundTrip<ReplySendData>(data)
                "driving.state" -> roundTrip<DrivingStateData>(data)
                "card.recheck", "trip.arrive", "trip.end", "trip.leave" -> assertEquals(JsonObject(emptyMap()), data)
                "dropoff.respond", "request.send", "device.lang" -> assertNotNull(data) // passenger-only, not modelled on the car
                else -> error("fixture message type not covered: $type")
            }
        }
    }

    @Test fun carBuiltEnvelopeMatchesFixtureShape() {
        val built = envelopeOf(ClientType.ROUTE_STARTED, "e6", 1794320000005, RouteInfo(3120, 52300, 7900))
        val expected = HeyJson.parseToJsonElement(fixture("envelopes.json")).jsonObject
            .getValue("clientToServer").jsonArray.first { it.jsonObject.getValue("type").jsonPrimitive.content == "route.started" }
        assertEquals(expected, HeyJson.parseToJsonElement(built))
    }

    @Test fun serverEnvelopesParse() {
        val list = HeyJson.parseToJsonElement(fixture("envelopes.json")).jsonObject.getValue("serverToClient").jsonArray
        val parsed = list.map { parseServerMessage(it.toString()) }
        assertEquals(5, parsed.size)
        assertEquals(SpeechPriority.Normal, (parsed[0] as ServerMessage.Say).data.priority)
        assertEquals(SpeechPriority.High, (parsed[1] as ServerMessage.Say).data.priority)
        assertEquals(NoticeKind.DropoffProposed, (parsed[2] as ServerMessage.Notice).data.kind)
        assertEquals(ErrorCode.STALE_VERSION, (parsed[3] as ServerMessage.Error).data.code)
        assertEquals("e10", (parsed[3] as ServerMessage.Error).data.ref)
        val limited = (parsed[4] as ServerMessage.Error).data
        assertEquals(ErrorCode.RATE_LIMITED, limited.code)
        assertEquals("out_2", limited.clientRef)
        assertEquals(ServerMessage.Ack("e1"), parseServerMessage("""{"type":"ack","id":"e1"}"""))
    }

    @Test fun snapshotEnvelopeParsesAsCarView() {
        val view = HeyJson.parseToJsonElement(fixture("car-view-card-sent.json"))
        val msg = parseServerMessage("""{"type":"trip.snapshot","id":"s9","ts":1,"data":$view}""") as ServerMessage.Snapshot
        assertEquals("K7M2QX4R", msg.view.carCode)
        assertEquals(null, parseServerMessage("""{"type":"trip.snapshot","id":"s9","ts":1,"data":{"role":"passenger"}}"""))
        assertEquals(null, parseServerMessage("not json"))
    }
}

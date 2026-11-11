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
        assertEquals(DropoffBy.Passenger, view.dropoff?.by)
        assertEquals(ProposalStatus.Pending, view.dropoff?.status)
    }

    @Test fun passengerViewRoundTrips() {
        roundTrip<PassengerTripView>(HeyJson.parseToJsonElement(fixture("passenger-view-in-trip.json")))
    }

    @Test fun clientEnvelopesRoundTrip() {
        val list = HeyJson.parseToJsonElement(fixture("envelopes.json")).jsonObject.getValue("clientToServer").jsonArray
        assertEquals(19, list.size)
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
                "dropoff.respond", "request.send" -> assertNotNull(data) // passenger-only, not modelled on the car
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
        assertEquals(4, parsed.size)
        assertEquals(SpeechPriority.Normal, (parsed[0] as ServerMessage.Say).data.priority)
        assertEquals(SpeechPriority.High, (parsed[1] as ServerMessage.Say).data.priority)
        assertEquals(NoticeKind.DropoffProposed, (parsed[2] as ServerMessage.Notice).data.kind)
        assertEquals(ErrorCode.STALE_VERSION, (parsed[3] as ServerMessage.Error).data.code)
        assertEquals("e10", (parsed[3] as ServerMessage.Error).data.ref)
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

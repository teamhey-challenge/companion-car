package ai.hey.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
        assertEquals(listOf(ConditionId.MeetEntrance, ConditionId.Temperature, ConditionId.Quiet, ConditionId.Music), view.card?.conditions)
        assertEquals(true, view.card?.quiet)
        assertEquals(MusicPref.Low, view.card?.music)
        assertEquals(DriverProposalStatus.Elsewhere, view.dropoff?.status)
        assertEquals(ReplyKind.Voice, view.replies.single().kind)
        assertEquals(null, view.replies.single().replyId)
    }

    /** The set of every quick reply is read from hey-shared's fixture (generated from QUICK_REPLY_META), so drift on either side fails here. */
    @Test fun quickReplySetsFollowTheCatalog() {
        val catalog = HeyJson.parseToJsonElement(fixture("quick-replies.json")).jsonObject
        val fromShared = catalog.entries.associate { (id, set) ->
            HeyJson.decodeFromJsonElement(QuickReplyId.serializer(), JsonPrimitive(id)) to
                when (set.jsonPrimitive.content) {
                    "answer" -> QuickReplySet.Answer
                    "driverFirst" -> QuickReplySet.DriverFirst
                    else -> error("unknown set $set")
                }
        }
        assertEquals(QuickReplyId.entries.toSet(), fromShared.keys)
        assertEquals(fromShared, QuickReplyId.entries.associateWith { it.set })
        assertEquals(listOf(QuickReplyId.Yes, QuickReplyId.Cannot, QuickReplyId.Moment), quickRepliesOf(QuickReplySet.Answer))
    }

    @Test fun replySendEncodesButtonAndVoiceFlat() {
        assertEquals(
            HeyJson.parseToJsonElement("""{"kind":"voice","requestId":"req_1","textKo":"네, 입구 앞에서 기다릴게요."}"""),
            HeyJson.encodeToJsonElement(ReplySendData.serializer(), ReplySendData.voice("req_1", "네, 입구 앞에서 기다릴게요.")),
        )
        assertEquals(
            HeyJson.parseToJsonElement("""{"kind":"button","replyId":"stopHere"}"""),
            HeyJson.encodeToJsonElement(ReplySendData.serializer(), ReplySendData.button(null, QuickReplyId.StopHere)),
        )
    }

    @Test fun passengerViewRoundTrips() {
        roundTrip<PassengerTripView>(HeyJson.parseToJsonElement(fixture("passenger-view-in-trip.json")))
    }

    /** 2.0.0 put the per-device join check code on the passenger view; it must survive the round trip, not be dropped. */
    @Test fun passengerViewKeepsTheJoinCheckCode() {
        val element = HeyJson.parseToJsonElement(fixture("passenger-view-in-trip.json")).jsonObject
        val withCode = JsonObject(element + ("joinCheckCode" to JsonPrimitive("42")))
        roundTrip<PassengerTripView>(withCode)
        assertEquals("42", HeyJson.decodeFromJsonElement(serializer<PassengerTripView>(), withCode).joinCheckCode)
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
            """{"point":{"lat":37.5649,"lng":126.9803},"label":"을지로 쪽 옆 출입구","kind":"entrance","entranceType":"side","ref":"3","street":{"ko":"을지로","latin":"Eulji-ro"},"reasonId":"sideCloser"}""",
        )
        assertEquals(expected, sent)
    }

    @Test fun clientEnvelopesRoundTrip() {
        val fixtures = HeyJson.parseToJsonElement(fixture("envelopes.json")).jsonObject
        val list = fixtures.getValue("clientToServer").jsonArray
        assertTrue("the fixture lists client envelopes", list.isNotEmpty())
        val types = list.map { it.jsonObject.getValue("type").jsonPrimitive.content }.toSet()
        for (envelope in list) {
            val obj = envelope.jsonObject
            val data = obj.getValue("data")
            when (val type = obj.getValue("type").jsonPrimitive.content) {
                "join.approve", "join.decline" -> roundTrip<JoinDecision>(data)
                "card.submit" -> roundTrip<CardSubmit>(data)
                "card.update" -> roundTrip<CardUpdate>(data)
                "route.started" -> roundTrip<RouteInfo>(data)
                "route.failed" -> roundTrip<RouteFailedData>(data)
                "trip.progress" -> roundTrip<ProgressInfo>(data)
                "dropoff.propose" -> roundTrip<DropoffProposeData>(data)
                "request.spoken" -> roundTrip<RequestSpokenData>(data)
                "reply.send" -> {
                    roundTrip<ReplySendData>(data)
                    val parsed = HeyJson.decodeFromJsonElement(serializer<ReplySendData>(), data)
                    when (parsed.kind) {
                        ReplyKind.Button -> assertNotNull(parsed.replyId)
                        ReplyKind.Voice -> assertNotNull(parsed.textKo)
                    }
                }
                "driving.state" -> roundTrip<DrivingStateData>(data)
                "card.recheck", "trip.arrive", "trip.end", "trip.leave" -> roundTrip<EmptyData>(data)
                "invite.rotate" -> roundTrip<InviteRotate>(data)
                "dropoff.respond", "request.send", "device.lang" -> assertNotNull(data) // passenger-only, not modelled on the car
                else -> error("fixture message type not covered: $type")
            }
        }
        // no drift: every type the car can send is in the fixture, and every fixture type is decoded above
        val carTypes = ClientType::class.java.declaredFields
            .filter { it.type == String::class.java && java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .map { it.isAccessible = true; it.get(null) as String }
        assertTrue("ClientType values: $carTypes", carTypes.isNotEmpty())
        assertEquals("car types without a fixture", emptySet<String>(), carTypes.toSet() - types)
    }

    /** The first frame on the socket is not an envelope: `{ type: "auth", token, protocol }`. */
    @Test fun authFirstFrameRoundTrips() {
        val auth = fixtures().getValue("auth").jsonObject
        assertEquals(AuthMessage.TYPE, auth.getValue("type").jsonPrimitive.content)
        roundTrip<AuthMessage>(auth)
        assertEquals(
            AuthMessage(token = "tok_example", protocol = "3.0.0"),
            HeyJson.decodeFromString(AuthMessage.serializer(), """{"type":"auth","token":"tok_example","protocol":"3.0.0"}"""),
        )
    }

    private fun fixtures() = HeyJson.parseToJsonElement(fixture("envelopes.json")).jsonObject

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

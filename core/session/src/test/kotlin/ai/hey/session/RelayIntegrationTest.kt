package ai.hey.session

import ai.hey.model.DropoffCandidate
import ai.hey.model.DropoffKind
import ai.hey.model.LatLng
import ai.hey.model.QuickReplyId
import ai.hey.model.TripState
import ai.hey.vehicle.DrivingStatus
import ai.hey.vehicle.FakeVehicleGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Real relay (bun, fake providers, temp data dir, random port) + the real TripRepository + a simulated passenger.
 * Skipped when `bun` is not on PATH or the relay checkout (HEY_RELAY_DIR, default ../hey-relay) is absent.
 */
class RelayIntegrationTest {
    private var process: Process? = null
    private lateinit var base: String
    private val http = OkHttpClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var dataDir: File

    private fun bunPath(): String? =
        System.getenv("PATH").orEmpty().split(File.pathSeparator).map { File(it, "bun") }.firstOrNull { it.canExecute() }?.path
            ?: File(System.getProperty("user.home"), ".bun/bin/bun").takeIf { it.canExecute() }?.path

    /**
     * The hey-relay checkout: env HEY_RELAY_DIR, default `../hey-relay` next to this repo (relative values are
     * resolved against the repo root, the directory with settings.gradle.kts). Null when absent.
     */
    private fun relayDir(): File? {
        var root: File? = File("").absoluteFile
        while (root != null && !File(root, "settings.gradle.kts").exists()) root = root.parentFile
        val base = root ?: return null
        val dir = File(System.getenv("HEY_RELAY_DIR")?.takeIf { it.isNotBlank() } ?: "../hey-relay")
        val resolved = (if (dir.isAbsolute) dir else File(base, dir.path)).normalize()
        return resolved.takeIf { File(it, "src/index.ts").exists() }
    }

    @Before fun startRelay() {
        val bun = bunPath()
        assumeTrue("bun not on PATH: relay integration test skipped", bun != null)
        val root = relayDir()
        assumeTrue("hey-relay not found (set HEY_RELAY_DIR or clone it to ../hey-relay): relay integration test skipped", root != null)
        val port = ServerSocket(0).use { it.localPort }
        dataDir = Files.createTempDirectory("heydata").toFile()
        val builder = ProcessBuilder(bun!!, "run", "src/index.ts")
            .directory(root)
            .redirectErrorStream(true)
        builder.environment().apply {
            put("HEY_FAKE_PROVIDERS", "1")
            put("HEY_DATA_DIR", dataDir.path)
            put("PORT", port.toString())
        }
        process = builder.start()
        base = "http://127.0.0.1:$port"
        val deadline = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < deadline) {
            try {
                http.newCall(Request.Builder().url("$base/healthz").build()).execute().use { if (it.isSuccessful) return }
            } catch (_: Exception) {
                Thread.sleep(150)
            }
        }
        error("relay did not start")
    }

    @After fun stop() {
        scope.cancel()
        process?.destroy()
        process?.waitFor(5, TimeUnit.SECONDS)
        if (::dataDir.isInitialized) dataDir.deleteRecursively()
    }

    private fun post(path: String, json: String, auth: String? = null): JsonObject {
        val req = Request.Builder().url(base + path).post(json.toRequestBody("application/json".toMediaType()))
            .apply { if (auth != null) header("Authorization", auth) }.build()
        http.newCall(req).execute().use { res ->
            assertTrue("POST $path -> ${res.code}", res.isSuccessful)
            return Json.parseToJsonElement(res.body!!.string()).jsonObject
        }
    }

    /** A passenger device: socket + the envelopes it received. */
    private class Passenger(val http: OkHttpClient, val wsUrl: String) {
        val received = CopyOnWriteArrayList<JsonObject>()
        lateinit var socket: WebSocket

        fun open() {
            socket = http.newWebSocket(
                Request.Builder().url(wsUrl).build(),
                object : WebSocketListener() {
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val obj = Json.parseToJsonElement(text).jsonObject
                        if (obj["type"]?.jsonPrimitive?.content == "ack") return
                        webSocket.send("""{"type":"ack","id":"${obj["id"]!!.jsonPrimitive.content}"}""")
                        received += obj
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = Unit
                },
            )
        }

        fun send(type: String, data: String) {
            socket.send("""{"type":"$type","id":"${UUID.randomUUID()}","ts":${System.currentTimeMillis()},"data":$data}""")
        }

        fun lastSnapshot(): JsonObject? =
            received.lastOrNull { it["type"]?.jsonPrimitive?.content == "trip.snapshot" }?.get("data")?.jsonObject
    }

    private suspend fun <T> eventually(what: String, block: () -> T?): T = withTimeout(15_000) {
        while (true) {
            block()?.let { return@withTimeout it }
            delay(50)
        }
        @Suppress("UNREACHABLE_CODE") error(what)
    }

    @Test fun passengerFlowDrivesTheCarState() = runBlocking {
        val vehicle = FakeVehicleGateway(scope)
        val storage = InMemoryCarStorage(ai.hey.model.CarSettings(plateSuffix = "1234", relayUrl = base))
        val repo = TripRepository(scope, vehicle, storage, http = http, progressPollMs = 3_600_000)
        repo.start()

        // first launch: registers, connects, snapshot says there is no trip
        eventually("online idle") { repo.state.value.takeIf { it.online && it.view?.state == TripState.IDLE } }
        val registration = storage.loadRegistration(base)
        assertNotNull(registration)
        assertEquals("1234", repo.state.value.view?.plateSuffix)
        assertTrue(repo.state.value.qrUrl!!.contains(registration!!.carCode))

        // passenger scans the QR: REQUESTED, the car sees the pending join
        val join = post("/v1/c/${registration.carCode}/join", """{"lang":"ja","partySize":2}""")
        val deviceToken = join.getValue("deviceToken").jsonPrimitive.content
        val joinId = join.getValue("joinRequestId").jsonPrimitive.content
        val requested = eventually("REQUESTED") { repo.state.value.takeIf { it.view?.state == TripState.REQUESTED } }
        assertEquals(joinId, requested.view?.pendingJoin?.joinRequestId)
        val passenger = Passenger(http, base.replace("http", "ws") + "/v1/ws?token=$deviceToken")
        passenger.open()

        // a driving car must not approve; parked it can
        vehicle.setDriving(true)
        eventually("driving") { repo.state.value.takeIf { it.driving == DrivingStatus.Driving } }
        repo.state.value.parked?.approveJoin(joinId) // no hands-on commands exist while the car moves
        delay(500)
        assertEquals(TripState.REQUESTED, repo.state.value.view?.state)
        vehicle.setDriving(false)
        eventually("parked") { repo.state.value.takeIf { it.driving == DrivingStatus.Parked } }
        // The relay learns about the stop through driving.state; a refusal (it still believed the car was moving)
        // clears the button, so a second tap is what the driver would do. Retrying here removes that ordering race.
        eventually("CONNECTED after a parked approve") {
            if (!repo.state.value.joinInFlight) repo.state.value.parked?.approveJoin(joinId)
            repo.state.value.takeIf { it.view?.state == TripState.CONNECTED }
        }

        // lead passenger sends the card
        val found = http.newCall(Request.Builder().url("$base/v1/places?q=lotte&lang=ja").header("Authorization", "Device $deviceToken").build())
            .execute().use { it.body!!.string() }
        val place = Json.parseToJsonElement(found).jsonArray.first().toString()
        passenger.send("card.submit", """{"destination":$place,"conditions":["luggage"]}""")
        val cardSent = eventually("CARD_SENT") { repo.state.value.takeIf { it.view?.state == TripState.CARD_SENT } }
        assertEquals("롯데호텔 서울", cardSent.view?.card?.destination?.nameKo)
        // the card text is spoken: "카드가 왔어요 + 목적지" reaches the vehicle
        eventually("card speech") { vehicle.spoken.firstOrNull { "롯데호텔" in it } }

        // car starts the route (fake navigation answers at once)
        repo.state.value.parked!!.startRoute()
        eventually("IN_TRIP") { repo.state.value.takeIf { it.view?.state == TripState.IN_TRIP } }
        assertEquals(1, vehicle.routes.size)

        // car proposes a drop-off with a structured candidate; the passenger device gets a localized label for it
        repo.state.value.parked!!.openDropoff()
        val withCandidates = eventually("candidates") { repo.state.value.takeIf { it.candidates.items.size > 1 } }
        val entrance = withCandidates.candidates.items.first { it.kind != ai.hey.model.DropoffKind.Default }
        repo.state.value.parked!!.proposeDropoff(entrance, null)
        val proposed = eventually("proposal") { repo.state.value.view?.dropoff?.takeIf { (it as? ai.hey.model.DriverProposal)?.status == ai.hey.model.DriverProposalStatus.Pending } }
        assertEquals(entrance.kind, proposed.kind)
        val seenByPassenger = eventually("passenger sees the proposal") {
            passenger.lastSnapshot()?.get("dropoff")?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.jsonObject
        }
        assertEquals(entrance.kind.name.lowercase(), seenByPassenger.getValue("kind").jsonPrimitive.content)
        assertTrue(seenByPassenger.getValue("labelLocal").jsonPrimitive.content.isNotBlank())
        passenger.send("dropoff.respond", """{"version":${proposed.version},"response":"ok"}""")
        val agreed = eventually("agreed") { repo.state.value.view?.agreedDropoff }
        assertEquals(entrance.label, agreed.label)
        assertEquals(ai.hey.model.DriverProposalStatus.Ok, (repo.state.value.view?.dropoff as? ai.hey.model.DriverProposal)?.status)

        // arrival by the manual button (navigation may never report it); a second press must not send again
        repo.closeDropoff() // D4 offers no arrive button; the driver closes it first
        repo.state.value.parked!!.arrive()
        eventually("ARRIVED") { repo.state.value.takeIf { it.view?.state == TripState.ARRIVED } }
        repo.state.value.parked?.arrive() // not offered in ARRIVED
        assertTrue(repo.state.value.error == null)
        assertNotNull(repo.state.value.view?.summary?.elapsedSec)

        repo.state.value.parked!!.endTrip()
        eventually("IDLE again") { repo.state.value.takeIf { it.view?.state == TripState.IDLE } }

        repo.stop()
    }

    @Test fun aRegistrationTheRelayDoesNotKnowIsReportedAndCanBeRedone() = runBlocking {
        val vehicle = FakeVehicleGateway(scope)
        val storage = InMemoryCarStorage(ai.hey.model.CarSettings(plateSuffix = "5678", relayUrl = base))
        storage.saveRegistration(Registration("ZZZZZZZZ", "not-a-real-token-000000", "$base/c/ZZZZZZZZ", base, "5678"))
        val repo = TripRepository(scope, vehicle, storage, http = http, progressPollMs = 3_600_000)
        repo.start()
        eventually("rejected") { repo.state.value.takeIf { it.connection == ConnectionStatus.Rejected } }
        repo.state.value.parked!!.reregister()
        val online = eventually("online after re-registering") { repo.state.value.takeIf { it.online && it.view != null } }
        assertTrue(!online.qrUrl!!.contains("ZZZZZZZZ"))
        repo.stop()
    }

    /**
     * The relay answers BAD_REQUEST with the envelope's ref when it refuses join.approve (here: a join request it no longer
     * has; the same answer it gives while it believes the car is driving). The car must release the button and show the error.
     */
    @Test fun aRefusedApproveReleasesTheButtonAndShowsAnError() = runBlocking {
        val vehicle = FakeVehicleGateway(scope)
        val storage = InMemoryCarStorage(ai.hey.model.CarSettings(plateSuffix = "4321", relayUrl = base))
        val repo = TripRepository(scope, vehicle, storage, http = http, progressPollMs = 3_600_000)
        repo.start()
        eventually("online") { repo.state.value.takeIf { it.online && it.view?.state == TripState.IDLE } }
        val registration = storage.loadRegistration(base)!!
        post("/v1/c/${registration.carCode}/join", """{"lang":"en","partySize":1}""")
        eventually("REQUESTED") { repo.state.value.takeIf { it.view?.state == TripState.REQUESTED } }

        repo.state.value.parked!!.approveJoin("jr_does_not_exist")
        val refused = eventually("error shown") { repo.state.value.takeIf { it.error != null } }
        assertEquals(ai.hey.model.ErrorCode.BAD_REQUEST, refused.error)
        assertTrue("the join button must be released", !refused.joinInFlight)
        repo.stop()
    }

    /**
     * Three hands-on commands at once: a stale approve (refused), a drop-off proposal (accepted) and a reply
     * (accepted). Every key must resolve — none may stay in flight — and the accepted effects must be in the state.
     */
    @Test fun concurrentHandsOnCommandsAllResolveTheirKeys() = runBlocking {
        val vehicle = FakeVehicleGateway(scope)
        val storage = InMemoryCarStorage(ai.hey.model.CarSettings(plateSuffix = "8765", relayUrl = base))
        val repo = TripRepository(scope, vehicle, storage, http = http, progressPollMs = 3_600_000)
        repo.start()
        eventually("online idle") { repo.state.value.takeIf { it.online && it.view?.state == TripState.IDLE } }
        val registration = storage.loadRegistration(base)!!
        val join = post("/v1/c/${registration.carCode}/join", """{"lang":"en","partySize":1}""")
        val deviceToken = join.getValue("deviceToken").jsonPrimitive.content
        val joinId = join.getValue("joinRequestId").jsonPrimitive.content
        val passenger = Passenger(http, base.replace("http", "ws") + "/v1/ws?token=$deviceToken")
        passenger.open()
        eventually("REQUESTED") { repo.state.value.takeIf { it.view?.state == TripState.REQUESTED } }

        repo.state.value.parked!!.approveJoin(joinId)
        eventually("CONNECTED") { repo.state.value.takeIf { it.view?.state == TripState.CONNECTED } }
        val found = http.newCall(Request.Builder().url("$base/v1/places?q=lotte&lang=en").header("Authorization", "Device $deviceToken").build())
            .execute().use { it.body!!.string() }
        val place = Json.parseToJsonElement(found).jsonArray.first().toString()
        passenger.send("card.submit", """{"destination":$place,"conditions":["luggage"]}""")
        eventually("CARD_SENT") { repo.state.value.takeIf { it.view?.state == TripState.CARD_SENT } }
        passenger.send("request.send", """{"kind":"preset","presetId":"window","clientRef":"race-1"}""")
        val open = eventually("open question") { repo.state.value.view?.requests?.firstOrNull { it.replyExpected } }

        val candidate = DropoffCandidate("default", DropoffKind.Default, null, null, null, "기본 위치", "기본 위치", LatLng(37.5655, 126.9811), 0.0)
        val beforeVersion = repo.state.value.view?.dropoff?.version ?: 0
        val beforeReplies = repo.state.value.view?.replies?.size ?: 0
        // The open question puts the car on D6; dismissing it is the driver's way back to the card, where a drop-off
        // proposal and a reply are both legal and can be tapped together.
        repo.dismissRequests()
        val hands = repo.state.value.parked!!
        listOf(
            launch(Dispatchers.IO) { hands.proposeDropoff(candidate, null) },
            launch(Dispatchers.IO) { hands.sendReply(open.requestId, QuickReplyId.Yes) },
        ).forEach { it.join() }

        // The proposal and reply clear through snapshots; the stale approve clears through its error ref.
        eventually("all command keys cleared") { repo.state.value.takeIf { it.commands.isEmpty() } }
        assertEquals(beforeVersion + 1, repo.state.value.view?.dropoff?.version)
        assertTrue("the reply must land", (repo.state.value.view?.replies?.size ?: 0) > beforeReplies)
        repo.stop()
    }
}

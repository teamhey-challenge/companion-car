package ai.hey.session

import ai.hey.model.ConsentType
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/** PLEOS-141: a 200 that is not the expected JSON (captive portal, proxy page, wrong deploy) is an [IOException], never a crash. */
class BadBodyTest {
    private fun client(body: String, type: String = "text/html") = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(body.toResponseBody(type.toMediaType())).build()
    }).build()

    private suspend fun expectIo(block: suspend () -> Unit) {
        try {
            block()
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(e !is HttpFailure)
            assertEquals("bad body", e.message)
        }
    }

    private val html = "<html><body>Sign in to the network</body></html>"

    @Test fun `register and session turn an HTML 200 and a wrong shape into IOException`() = runBlocking {
        expectIo { HttpTalkApi(client(html)).register("https://x.test", "i", "s") }
        expectIo { HttpTalkApi(client(html)).session("https://x.test", "tok") }
        expectIo { HttpTalkApi(client("""{"other":1}""", "application/json")).session("https://x.test", "tok") }
        expectIo { HttpTalkApi(client("[1,2]", "application/json")).register("https://x.test", "i", "s") }
    }

    @Test fun `every companion call turns an HTML 200 into IOException`() = runBlocking {
        val storage = InMemoryCarStorage().also { it.saveCarToken(it.settings.value.serverUrl, "tok") }
        val api = HttpCompanionApi(storage, client(html))
        expectIo { api.consents() }
        expectIo { api.postConsent(ConsentType.Record, "v1", true) }
        expectIo { api.createPairing() }
        expectIo { api.currentPairing() }
        expectIo { api.memory(null, 20) }
        expectIo { api.settings() }
        expectIo { HttpCompanionApi(storage, client("""{"items":5}""", "application/json")).memory(null, 20) }
    }
}

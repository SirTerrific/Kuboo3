package com.sethchhim.kuboo_remote.client

import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
class UbooquitySessionInterceptorTest {

    private val server = MockWebServer()
    private val logins = AtomicInteger()
    @Volatile private var validSession = ""
    private val errorImage = ByteArray(11928) { 7 }
    private val page = ByteArray(500) { 1 }

    @Before
    fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path == "/" && request.method == "GET") return MockResponse().setBody(
                        """<input id="serversalt" value="salt"><input id="servertime" name="servertime" value="1">""")
                if (request.path == "/" && request.method == "POST") {
                    validSession = "S${logins.incrementAndGet()}"
                    return MockResponse().addHeader("Set-Cookie", "UbooquitySession=$validSession; Path=/")
                }
                val signedIn = request.getHeader("Cookie")?.contains("UbooquitySession=$validSession") == true
                return MockResponse().addHeader("Content-Type", "image/png").setBody(Buffer().write(if (signedIn) page else errorImage))
            }
        }
        server.start()
    }

    @After
    fun stop() = server.shutdown()

    private fun client(): OkHttpClient {
        val jar = HostCookieJar()
        lateinit var client: OkHttpClient
        client = OkHttpClient.Builder().cookieJar(jar).addInterceptor(UbooquitySessionInterceptor(jar) { client }).build()
        return client
    }

    private fun OkHttpClient.page(): Int {
        val request = Request.Builder().url(server.url("/pagereader/1?page=1"))
                .header("Authorization", "Basic " + Base64.encodeToString("kuboo:pw".toByteArray(), Base64.NO_WRAP))
                .build()
        return newCall(request).execute().use { it.body!!.bytes().size }
    }

    @Test
    fun signsInOnceThenReusesTheSession() {
        val client = client()

        assertEquals(500, client.page())
        assertEquals(500, client.page())
        assertEquals(1, logins.get())
    }

    @Test
    fun signsInAgainWhenTheServerForgotTheSession() {
        val client = client()
        assertEquals(500, client.page())

        validSession = "restarted" // the server no longer knows the cookie it handed out

        assertEquals(500, client.page())
        assertEquals(2, logins.get())
    }
}

package com.sethchhim.kuboo_remote.client

import android.util.Base64
import okhttp3.Call
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Signs in to Ubooquity the way its own web reader does, so that page images can be read.
 *
 * Ubooquity 3 answers /pagereader/ requests with an "Authentication error" image unless the
 * request carries a session cookie. It never looks at the basic authentication header there, and
 * fetching the opds feed does not hand a session out, so page streaming and covers came back as
 * that error image on every server with user management enabled -- with a 200 status, which is
 * why nothing looked like a failure.
 *
 * A session comes from the login form on the library root. The form does not send the password:
 *
 *     hash = hmac_sha256(hmac_sha256(password, serversalt), servertime)
 *
 * with the salt and the timestamp both taken from the form that was just served, and the hash
 * posted alongside the username. The reply sets UbooquitySession, which the cookie jar then
 * sends with every later request.
 *
 * Credentials come from the request's own basic authentication header, so this needs no wiring at
 * the call sites: every request already carries them, Glide's included.
 */
internal class UbooquitySessionInterceptor(private val cookieJar: HostCookieJar,
                                           private val callFactory: () -> Call.Factory) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.isPageReader()) return chain.proceed(request)

        synchronized(this) { if (!hasSession(request.url)) login(request) }
        val usedSession = session(request.url) ?: return chain.proceed(request)

        val response = chain.proceed(request)
        if (!response.isAuthenticationError()) return response

        // The server forgot the session (restart, timeout): it answers the error image, with a 200,
        // to a cookie it no longer knows. Sign in again once, unless another page already did.
        response.close()
        synchronized(this) {
            if (session(request.url) == usedSession) {
                cookieJar.forget(request.url, COOKIE_SESSION)
                login(request)
            }
        }
        return chain.proceed(request)
    }

    private fun Request.isPageReader() = url.encodedPath.startsWith(PATH_PAGE_READER)

    private fun session(url: HttpUrl) = cookieJar.loadForRequest(url).firstOrNull { it.name == COOKIE_SESSION }?.value

    private fun hasSession(url: HttpUrl) = session(url) != null

    // shortcut: the error image is recognised by its size, as Ubooquity 3.1.0 serves it; a server
    // version with a different image needs its size here, until then it just is not renewed.
    private fun Response.isAuthenticationError() =
            header("Content-Type") == "image/png" && peekBody(AUTH_ERROR_IMAGE_BYTES + 1).contentLength() == AUTH_ERROR_IMAGE_BYTES

    private fun login(request: Request) {
        val credentials = request.credentials() ?: return
        val loginUrl = request.url.newBuilder().encodedPath("/").query(null).build()

        try {
            val form = callFactory().newCall(Request.Builder().url(loginUrl).build()).execute().use {
                it.body?.string() ?: ""
            }
            val salt = SALT.find(form)?.groupValues?.get(1)
            val time = TIME.find(form)?.groupValues?.get(1)
            if (salt == null || time == null) {
                //no login form: this server does not use user management, nothing to sign in to
                return
            }

            val body = FormBody.Builder()
                    .add("login", credentials.first)
                    .add("servertime", time)
                    .add("hash", ubooquityLoginHash(credentials.second, salt, time))
                    .build()
            callFactory().newCall(Request.Builder().url(loginUrl).post(body).build()).execute().close()

            if (!hasSession(request.url)) Timber.w("Ubooquity did not grant a session to [${credentials.first}]")
        } catch (e: Exception) {
            Timber.e("Failed to open a Ubooquity session: ${e.message}")
        }
    }

    private fun Request.credentials(): Pair<String, String>? {
        val header = header("Authorization") ?: return null
        if (!header.startsWith(BASIC)) return null

        return try {
            val decoded = String(Base64.decode(header.removePrefix(BASIC), Base64.DEFAULT))
            val username = decoded.substringBefore(":")
            val password = decoded.substringAfter(":", "")
            if (username.isEmpty()) null else Pair(username, password)
        } catch (e: Exception) {
            Timber.e("Failed to read credentials from the request: ${e.message}")
            null
        }
    }

    private companion object {
        const val PATH_PAGE_READER = "/pagereader/"
        const val COOKIE_SESSION = "UbooquitySession"
        const val AUTH_ERROR_IMAGE_BYTES = 11928L
        const val BASIC = "Basic "

        val SALT = """id="serversalt" value="([^"]*)"""".toRegex()
        val TIME = """id="servertime" name="servertime" value="([^"]*)"""".toRegex()
    }

}

/**
 * What the login form sends instead of the password, as its own script computes it:
 *
 *     hex_hmac_sha256(hex_hmac_sha256(password, serversalt), servertime)
 *
 * The keys are the first argument in that library, so the password keys the inner hash and the
 * inner hash, as a hex string, keys the outer one.
 */
internal fun ubooquityLoginHash(password: String, salt: String, time: String) = hmac(hmac(password, salt), time)

private fun hmac(key: String, message: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(key.toByteArray(), "HmacSHA256"))
    return mac.doFinal(message.toByteArray()).joinToString("") { "%02x".format(it) }
}

package com.sethchhim.kuboo_remote.client

import android.content.Context
import com.sethchhim.kuboo_remote.util.Settings.ALLOW_SELF_SIGNED_CERTIFICATES
import com.sethchhim.kuboo_remote.util.Settings.CACHE_SIZE
import com.sethchhim.kuboo_remote.util.Settings.CONNECTION_TIMEOUT
import com.sethchhim.kuboo_remote.util.Settings.READ_TIMEOUT
import okhttp3.Cache
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber
import java.io.File
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Builds the one http client the app uses.
 *
 * This was a subclass of OkHttpClient overriding its getters, which okhttp 3 tolerated and okhttp
 * 4 forbids -- every one of those members is final now. Building through the builder is both the
 * supported shape and what unblocks moving off a 2019 release of the library.
 *
 * The client is rebuilt when the self-signed certificate setting changes, since that decides how
 * the socket factory is put together and a built client cannot be asked again.
 */
class OkHttpClientFactory(private val context: Context) {

    internal val sslHandshakeInterceptor = SSLHandshakeInterceptor()

    private val isHttpLoggingEnabled = false

    private var builtAllowingSelfSigned: Boolean? = null
    private var client: OkHttpClient? = null

    @Synchronized
    fun client(): OkHttpClient {
        val allowSelfSigned = ALLOW_SELF_SIGNED_CERTIFICATES
        val current = client
        if (current != null && builtAllowingSelfSigned == allowSelfSigned) return current

        return build(allowSelfSigned).also {
            client = it
            builtAllowingSelfSigned = allowSelfSigned
        }
    }

    private fun build(allowSelfSigned: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
                .cache(cache())
                .cookieJar(cookieJar)
                .connectTimeout(CONNECTION_TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
                .readTimeout(READ_TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
                .protocols(listOf(Protocol.HTTP_1_1))
                .addInterceptor(sslHandshakeInterceptor)
                .addInterceptor(UbooquitySessionInterceptor(cookieJar) { client() })

        if (isHttpLoggingEnabled) {
            builder.addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        }

        if (allowSelfSigned) {
            // Deliberately no validation: the user asked for a certificate that does not check
            // out, which is the ordinary case for a server at home behind one it signed itself.
            trustAllSslSocketFactory()?.let { builder.sslSocketFactory(it, trustAllManager) }
            builder.hostnameVerifier(HostnameVerifier { _, _ -> true })
        } else {
            builder.hostnameVerifier(HttpsURLConnection.getDefaultHostnameVerifier())
        }

        return builder.build()
    }

    private fun cache(): Cache {
        val cacheFile = File(context.cacheDir, "okhttp_cache")
        if (!cacheFile.exists()) cacheFile.mkdir()
        return Cache(cacheFile, (CACHE_SIZE * 1024 * 1024).toLong())
    }

    private fun trustAllSslSocketFactory(): SSLSocketFactory? = try {
        SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(trustAllManager), java.security.SecureRandom()) }.socketFactory
    } catch (e: KeyManagementException) {
        Timber.e("Failed to build an ssl context: ${e.message}")
        null
    } catch (e: NoSuchAlgorithmException) {
        Timber.e("Failed to build an ssl context: ${e.message}")
        null
    }

    private val cookieJar = HostCookieJar()

    private val trustAllManager = object : X509TrustManager {
        @Throws(CertificateException::class)
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
        }

        @Throws(CertificateException::class)
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

}

/**
 * Cookies are kept per host and port, which is what a server session needs.
 *
 * Keying them by the full url instead meant a session was only ever sent back to the exact
 * address that set it: Ubooquity hands out its session when the opds feed is fetched, and
 * that session is what /pagereader/ checks -- it ignores the basic authentication header
 * entirely and answers an "Authentication error" image without one.
 *
 * okhttp calls this from several threads at once, and a response that sets one cookie must not
 * wipe the others the host already set, so the store is concurrent and merges by cookie name.
 */
internal class HostCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, List<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        store.merge(key(url), cookies) { old, new -> old.filter { o -> new.none { it.name == o.name } } + new }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        return store[key(url)]?.filter { it.expiresAt > now } ?: emptyList()
    }

    fun forget(url: HttpUrl, name: String) {
        store.computeIfPresent(key(url)) { _, cookies -> cookies.filter { it.name != name } }
    }

    private fun key(url: HttpUrl) = "${url.host}:${url.port}"
}

package com.sethchhim.kuboo_remote.client

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HostCookieJarTest {

    private val url = "http://192.168.1.10:2202/".toHttpUrl()

    private fun cookie(name: String, value: String, extra: String = "") = Cookie.parse(url, "$name=$value$extra")!!

    @Test
    fun aResponseSettingOneCookieKeepsTheOthers() {
        val jar = HostCookieJar()
        jar.saveFromResponse(url, listOf(cookie("UbooquitySession", "A")))
        jar.saveFromResponse(url, listOf(cookie("other", "1")))

        assertEquals(setOf("UbooquitySession", "other"), jar.loadForRequest(url).map { it.name }.toSet())
    }

    @Test
    fun aNewValueReplacesTheOldOneOfTheSameName() {
        val jar = HostCookieJar()
        jar.saveFromResponse(url, listOf(cookie("UbooquitySession", "A")))
        jar.saveFromResponse(url, listOf(cookie("UbooquitySession", "B")))

        assertEquals(listOf("B"), jar.loadForRequest(url).map { it.value })
    }

    @Test
    fun twoPortsOnOneHostDoNotShareASession() {
        val jar = HostCookieJar()
        jar.saveFromResponse(url, listOf(cookie("UbooquitySession", "A")))

        assertTrue(jar.loadForRequest("http://192.168.1.10:2203/".toHttpUrl()).isEmpty())
    }

    @Test
    fun anExpiredCookieIsNotSent() {
        val jar = HostCookieJar()
        jar.saveFromResponse(url, listOf(cookie("UbooquitySession", "A", "; Max-Age=0")))

        assertTrue(jar.loadForRequest(url).isEmpty())
    }

    @Test
    fun forgetDropsOnlyTheNamedCookie() {
        val jar = HostCookieJar()
        jar.saveFromResponse(url, listOf(cookie("UbooquitySession", "A"), cookie("other", "1")))
        jar.forget(url, "UbooquitySession")

        assertEquals(listOf("other"), jar.loadForRequest(url).map { it.name })
    }
}

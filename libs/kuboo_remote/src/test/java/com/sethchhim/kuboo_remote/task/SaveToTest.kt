package com.sethchhim.kuboo_remote.task

import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class SaveToTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun body(data: ByteArray, announced: Long = data.size.toLong()) = object : ResponseBody() {
        override fun contentType(): MediaType? = null
        override fun contentLength() = announced
        override fun source(): BufferedSource = Buffer().write(data)
    }

    @Test
    fun aCompleteBodyEndsUpUnderItsRealName() {
        val file = File(folder.root, "book.cbz")

        assertEquals(file, body(byteArrayOf(1, 2, 3)).saveTo(file))
        assertArrayEquals(byteArrayOf(1, 2, 3), file.readBytes())
        assertEquals(listOf("book.cbz"), folder.root.list()!!.toList())
    }

    @Test
    fun aShortBodyLeavesNothingBehind() {
        val file = File(folder.root, "book.cbz")

        assertNull(body(byteArrayOf(1, 2, 3), announced = 10).saveTo(file))
        assertEquals(0, folder.root.list()!!.size)
    }

    @Test
    fun aFailureMidTransferLeavesNothingBehind() {
        val file = File(folder.root, "book.cbz")
        val failing = object : ResponseBody() {
            override fun contentType(): MediaType? = null
            override fun contentLength() = 10L
            override fun source(): BufferedSource {
                val data = Buffer().write(byteArrayOf(1, 2))
                return object : ForwardingSource(data) {
                    override fun read(sink: Buffer, byteCount: Long): Long {
                        if (data.size == 0L) throw IOException("connection reset")
                        return super.read(sink, byteCount)
                    }
                }.buffer()
            }
        }

        try {
            failing.saveTo(file)
        } catch (e: IOException) {
            // expected
        }
        assertEquals(0, folder.root.list()!!.size)
    }

    @Test
    fun aFinishedDownloadIsReplacedByANewOne() {
        val file = File(folder.root, "book.cbz").apply { writeBytes(byteArrayOf(9)) }

        assertEquals(file, body(byteArrayOf(1, 2)).saveTo(file))
        assertArrayEquals(byteArrayOf(1, 2), file.readBytes())
    }
}

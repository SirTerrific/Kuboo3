package com.sethchhim.kuboo_remote.task

import android.accounts.NetworkErrorException
import androidx.lifecycle.MutableLiveData
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import com.sethchhim.kuboo_remote.KubooRemote
import com.sethchhim.kuboo_remote.model.Login
import com.sethchhim.kuboo_remote.util.Settings.isDebugOkHttp
import okhttp3.ResponseBody
import timber.log.Timber
import java.io.File
import java.net.URL

class Task_RemoteDownloadFile(val kubooRemote: KubooRemote, val login: Login, val stringUrl: String, val saveDir: File) {

    private val okHttpHelper = kubooRemote.okHttpHelper
    internal val liveData = MutableLiveData<File?>()

    init {
        kubooRemote.networkIO.execute {
            try {
                val startTime = System.currentTimeMillis()
                // use{} closes the response on every path: an abandoned one keeps the connection
                // open and the server logs a broken pipe once it gives up on the transfer.
                okHttpHelper.getCall(login, stringUrl, javaClass.simpleName).execute().use { response ->
                    if (!response.isSuccessful) throw NetworkErrorException()
                    val responseBody = response.body ?: throw NetworkErrorException()
                    val file = File(saveDir, URL(stringUrl).guessFileName())
                    val result = when (file.exists() && file.length() == responseBody.contentLength()) {
                        true -> file
                        false -> responseBody.saveTo(file)
                    }
                    if (isDebugOkHttp) Timber.i("File download finished. result[$result] [${System.currentTimeMillis() - startTime}] [$stringUrl]")
                    kubooRemote.mainThread.execute { liveData.value = result }
                }
            } catch (e: Exception) {
                if (isDebugOkHttp) Timber.e(e)
                kubooRemote.mainThread.execute { liveData.value = null }
            } catch (e: OutOfMemoryError) {
                if (isDebugOkHttp) Timber.e(e)
                kubooRemote.mainThread.execute { liveData.value = null }
            }
        }
    }

    private fun URL.guessFileName(): String {
        val fileExtension = MimeTypeMap.getFileExtensionFromUrl(this.toString())
        return URLUtil.guessFileName(this.toString(), null, fileExtension)
    }

}

/**
 * Copies the body straight to disk, to a temporary file next to [file], and moves it into place
 * only once all of it arrived. A transfer cut short leaves nothing under the real name, so it is
 * never mistaken for a finished download, and two downloads of one file no longer write into the
 * same file. Reading the body into a ByteArray first held the whole comic in memory twice, which
 * runs a device out of heap on a large file.
 *
 * Returns null when the length received is not the length announced.
 */
internal fun ResponseBody.saveTo(file: File): File? {
    val expected = contentLength()
    val part = File.createTempFile("kuboo_", ".part", file.parentFile)
    try {
        byteStream().use { input -> part.outputStream().use { output -> input.copyTo(output) } }
        if (part.length() != expected) return null
        file.delete()
        return if (part.renameTo(file)) file else null
    } finally {
        part.delete()
    }
}

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
                val call = okHttpHelper.getCall(login, stringUrl, javaClass.simpleName)
                val response = call.execute()
                val responseBody = response.body
                if (responseBody != null) {
                    val contentLength = responseBody.contentLength()
                    val fileName = URL(stringUrl).guessFileName()
                    val saveFilePath = "$saveDir${File.separator}$fileName"
                    val file = File(saveFilePath)
                    when (file.exists()) {
                        true -> onFileExists(file, saveFilePath, responseBody, contentLength, startTime)
                        false -> onFileDoesNotExist(file, saveFilePath, responseBody, contentLength, startTime)
                    }
                    response.close()
                } else {
                    throw NetworkErrorException()
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

    private fun onFileExists(file: File, saveFilePath: String, responseBody: ResponseBody, contentLength: Long, startTime: Long) {
        if (file.length() == contentLength) {
            val stopTime = System.currentTimeMillis()
            val elapsedTime = stopTime - startTime
            if (isDebugOkHttp) Timber.i("File already exists. Download cancelled. [$saveFilePath] [$elapsedTime] [$stringUrl] ")
            kubooRemote.mainThread.execute { liveData.value = file }
        } else {
            if (isDebugOkHttp) Timber.i("File already exists but is incomplete. Deleting partial download and starting over. fileLength[${file.length()}] totalSize[$contentLength] [$saveFilePath] [$stringUrl] ")
            file.delete()
            file.createNewFile()
            onFileDoesNotExist(file, saveFilePath, responseBody, contentLength, startTime)
        }
    }

    /**
     * The body is copied straight to disk. Reading it into a ByteArray first held the whole
     * comic in memory and then a second copy while writing it, which runs a device out of
     * heap on a large file: the OutOfMemoryError below swallowed it, the app reported that
     * assets could not be loaded, and the abandoned response left the server logging
     * "EofException: Broken pipe" mid-transfer.
     */
    private fun onFileDoesNotExist(file: File, saveFilePath: String, responseBody: ResponseBody, contentLength: Long, startTime: Long) {
        responseBody.byteStream().use { input -> file.outputStream().use { output -> input.copyTo(output) } }
        kubooRemote.mainThread.execute { liveData.value = file.verifyLength(contentLength) }
        val stopTime = System.currentTimeMillis()
        val elapsedTime = stopTime - startTime
        if (isDebugOkHttp) Timber.i("File download complete! [$saveFilePath] [$elapsedTime] [$stringUrl]")
    }

    private fun URL.guessFileName(): String {
        val fileExtension = MimeTypeMap.getFileExtensionFromUrl(this.toString())
        return URLUtil.guessFileName(this.toString(), null, fileExtension)
    }

    private fun File.verifyLength(contentLength: Long) = when (length() == contentLength) {
        true -> this
        false -> null
    }

}
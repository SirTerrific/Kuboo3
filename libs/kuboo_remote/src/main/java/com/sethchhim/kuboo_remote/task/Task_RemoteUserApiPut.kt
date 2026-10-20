package com.sethchhim.kuboo_remote.task

import androidx.lifecycle.MutableLiveData
import com.sethchhim.kuboo_remote.KubooRemote
import com.sethchhim.kuboo_remote.model.Book
import com.sethchhim.kuboo_remote.model.Login
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber

class Task_RemoteUserApiPut(kubooRemote: KubooRemote, login: Login, book: Book) : Task_RemoteUserApiBase(kubooRemote, login, book) {

    internal val liveData = MutableLiveData<Boolean?>()

    init {
        kubooRemote.networkIO.execute {
            try {
                val call = okHttpHelper.putCall(login, stringUrl, getRequestBody())
                val response = call.execute()
                if (response.isSuccessful) {
                    Timber.d("UserApi put is successful. title[${book.title}] page[${book.currentPage} of ${book.totalPages}] bookMark[${book.bookMark}] isFinished[${book.isFinished}] stringUrl[$stringUrl]")
                    kubooRemote.mainThread.execute { liveData.value = true }
                } else {
                    when (response.code) {
                        401 -> handleAuthentication(call)
                        else -> {
                            Timber.e("code[${response.code}] message[${response.message}] title[${book.title}] stringUrl[$stringUrl]")
                            kubooRemote.mainThread.execute { liveData.value = false }
                        }
                    }
                }
                response.close()
            } catch (e: Exception) {
                Timber.e("message[${e.message}] stringUrl[$stringUrl]")
                kubooRemote.mainThread.execute { liveData.value = false }
            }
        }
    }

    private fun handleAuthentication(call: Call) = kubooRemote.mainThread.execute {
        Task_Authenticate(kubooRemote, login).liveData.observeForever { result ->
            if (result == true) {
                val secondCall = call.clone()
                secondCall.retry()
            }
        }
    }

    private fun Call.retry() = kubooRemote.networkIO.execute {
        try {
            val secondResponse = execute()
            if (secondResponse.isSuccessful) {
                Timber.i("UserApi put is successful. title[${book.title}] page[${book.currentPage} of ${book.totalPages}] bookMark[${book.bookMark}] isFinished[${book.isFinished}] stringUrl[$stringUrl]")
            } else {
                Timber.e("code[${secondResponse.code}] message[${secondResponse.message}] title[${book.title}] stringUrl[$stringUrl] secondAttempt[true]")
                kubooRemote.mainThread.execute { liveData.value = null }
            }
        } catch (e: Exception) {
            Timber.e("message[${e.message}] secondAttempt[true]")
        }
    }

    /**
     * Ubooquity 3 takes the bookmark as the plain request body and parses it itself: an
     * integer page for comics, the reader's "spineIndex#percentage" string for a book. It
     * rejects the json body Ubooquity 2 accepted with
     * "NumberFormatException: For input string: {...}", and a form content type makes the
     * servlet consume the body before the handler reads it ("IllegalStateException:
     * STREAMED"), so the type has to be text.
     *
     * The call carries no finished flag any more; isFinished stays local.
     */
    private fun getRequestBody(): RequestBody {
        val mark = when (book.isEpub()) {
            true -> book.bookMark
            false -> book.currentPage.toString()
        }
        return mark.toRequestBody(TEXT)
    }

    private companion object {
        val TEXT = "text/plain; charset=utf-8".toMediaTypeOrNull()
    }

}
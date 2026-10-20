package com.sethchhim.kuboo_client.ui.main.browser

import androidx.lifecycle.Observer
import android.os.Bundle
import android.view.View
import com.sethchhim.kuboo_client.Constants.ARG_BOOK
import com.sethchhim.kuboo_client.ui.main.browser.adapter.BrowserContentAdapter
import com.sethchhim.kuboo_remote.model.Book
import com.sethchhim.kuboo_remote.util.resolveLink

class BrowserSeriesFragment : BrowserBaseFragment() {

    init {
        isPathEnabled = false
    }

    private lateinit var seriesBook: Book

    override fun onBindViews(view: View) {
        super.onBindViews(view)
        contentSwipeRefreshLayout.setOnRefreshListener { onSwipeRefresh() }
        contentAdapter = BrowserContentAdapter(this, viewModel)
        contentRecyclerView.adapter = contentAdapter
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        seriesBook = arguments?.getParcelable(ARG_BOOK) ?: Book()
        populateSeries()
    }

    private fun populateSeries() {
        setStateLoading()
        resetRecyclerView()
        paginationHandler.reset()
        val stringUrl = viewModel.getActiveServer().resolveLink(seriesBook.linkXmlPath)
        viewModel.getListByUrl(stringUrl).observe(viewLifecycleOwner, Observer { result ->
            val book = Book().apply { linkSubsection = seriesBook.linkXmlPath }
            handleMediaResult(book, result)
        })
    }

    override fun onSwipeRefresh() {
        populateSeries()
    }

    companion object {
        fun newInstance(book: Book) = BrowserSeriesFragment().apply {
            arguments = Bundle().apply { putParcelable(ARG_BOOK, book) }
        }
    }

}
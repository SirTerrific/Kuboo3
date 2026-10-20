package com.sethchhim.kuboo_client.ui.reader.base

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.MenuItem
import com.sethchhim.kuboo_client.R
import com.sethchhim.kuboo_client.Settings
import com.sethchhim.kuboo_client.ui.main.MainActivity


@SuppressLint("Registered")
open class ReaderBaseActivity : ReaderBaseActivityImpl7_Hardware() {

    override fun onCreate(savedInstanceState: Bundle?) {
        supportPostponeEnterTransition()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.reader_layout_base)
        setKeepScreenOn()
        setSupportActionBar(toolbar)

        //a way out that can be seen and tapped: the toolbar was title and menu only
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        hideReaderToolbar()
        hideStatusBar()

        pipWidth = systemUtil.getSystemWidth()
        pipHeight = systemUtil.getSystemHeight()

        previewImageView.transitionName = transitionUrl

        overlayLayout.setOnClickListener { hideOverlay() }
        overlayTextView1.setOnClickListener { hideOverlay() }
        restoreOverlay()

        title = currentBook.title
        isLocal = currentBook.isLocal()
        populateNeighbors()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        title = currentBook.title
        isLocal = currentBook.isLocal()
        populateNeighbors()
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.clearReaderLists()
    }

    override fun finish() {
        if (isBackStackLost) {
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(0, 0)
        }
        super.finish()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.reader_info -> showDialogInfo(currentBook)
            //the arrow means leave the book, not just dismiss the overlay it is drawn on
            android.R.id.home -> exitActivity()
        }
        return super.onOptionsItemSelected(item)
    }

    // exitActivity() calls super.onBackPressed() itself once the overlay is hidden.
    @android.annotation.SuppressLint("MissingSuperCall")
    override fun onBackPressed() = onBackPressedReader()

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Settings.PIP_MODE) startPictureInPictureMode()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            isInPipMode = true
            hideOverlay(isFadeEnabled = false)
        } else {
            isInPipMode = false
            isBackStackLost = true
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setOverlayGuideLines()
    }

}
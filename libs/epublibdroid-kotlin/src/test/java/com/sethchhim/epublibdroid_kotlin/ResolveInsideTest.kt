package com.sethchhim.epublibdroid_kotlin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class ResolveInsideTest {

    private val root = File(System.getProperty("java.io.tmpdir"), "epub_temp_files")

    @Test
    fun keepsAHrefInsideTheFolder() {
        assertEquals(File(root, "OEBPS/images/a.png").canonicalPath, resolveInside(root, "OEBPS/images/a.png")?.canonicalPath)
    }

    @Test
    fun keepsAHrefThatGoesUpAndComesBackDown() {
        assertEquals(File(root, "images/a.png").canonicalPath, resolveInside(root, "OEBPS/../images/a.png")?.canonicalPath)
    }

    @Test
    fun refusesAHrefThatClimbsOut() {
        assertNull(resolveInside(root, "../shared_prefs/x.xml"))
        assertNull(resolveInside(root, "OEBPS/../../x.png"))
    }

    @Test
    fun refusesASiblingFolderWithTheSamePrefix() {
        assertNull(resolveInside(root, "../epub_temp_files_evil/x.png"))
    }
}

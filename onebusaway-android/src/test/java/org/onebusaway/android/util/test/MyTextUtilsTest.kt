package org.onebusaway.android.util.test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.util.MyTextUtils

class MyTextUtilsTest {
    @Test
    fun titleCase() {
        assertEquals(null, MyTextUtils.toTitleCase(null))
        assertEquals("E John St & 13th Ave E", MyTextUtils.toTitleCase("E JOHN ST & 13th AVE E"))
        assertEquals("Seattle", MyTextUtils.toTitleCase("SEATTLE"))
        assertEquals("Seattle", MyTextUtils.toTitleCase("Seattle"))
    }

    @Test
    fun sentenceCase() {
        assertEquals("Testing sentence case", MyTextUtils.toSentenceCase("Testing sentence case"))
        assertEquals("Testing sentence case again", MyTextUtils.toSentenceCase("TESTING SENTENCE CASE AGAIN"))
        assertEquals("Another test", MyTextUtils.toSentenceCase("Another Test"))
        assertEquals("Another test", MyTextUtils.toSentenceCase("Another TEST"))
    }

    @Test
    fun allCaps() {
        assertTrue(MyTextUtils.isAllCaps("THIS IS ALL CAPS"))
        assertFalse(MyTextUtils.isAllCaps("THIS IS not ALL CAPS"))
    }
}

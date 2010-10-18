package com.joulespersecond.seattlebusbot.test;

import com.joulespersecond.seattlebusbot.MyTextUtils;

import android.test.AndroidTestCase;

public class MyTextUtilsTest extends AndroidTestCase {

    public void testTitleCase() {
        assertEquals(null, MyTextUtils.toTitleCase(null));
        assertEquals("E John St & 13th Ave E", MyTextUtils.toTitleCase("E JOHN ST & 13th AVE E"));
        assertEquals("Seattle", MyTextUtils.toTitleCase("SEATTLE"));
        assertEquals("Seattle", MyTextUtils.toTitleCase("Seattle"));
    }
}

package com.cuvm.ui.create

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParseResTest {
    @Test fun parses() = assertEquals(Resolution(1080, 2340, 450), parseRes("1080x2340x450"))
    @Test fun anySeparator() = assertEquals(Resolution(720, 1280, 320), parseRes("720 × 1280 dpi 320"))
    @Test fun rejectsMissing() = assertNull(parseRes("1080x2340"))
    @Test fun rejectsOutOfRange() = assertNull(parseRes("10x2340x450"))
}

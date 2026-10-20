package com.cuvm.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class PatchTest {
    @Test fun replacesEveryHitSameLength() {
        val src = "x/data/data/io.twoyi/rootfs\u0000/data/data/io.twoyi/loader64".toByteArray()
        val b = src.copyOf()
        assertEquals(2, Patch.bytes(b))
        assertEquals(src.size, b.size)
        assertEquals("x/data/data/com.cuvm/rootfs\u0000/data/data/com.cuvm/loader64", String(b))
    }

    @Test fun leavesOtherBytes() {
        val b = "/data/data/io.twoyX".toByteArray()
        assertEquals(0, Patch.bytes(b))
    }
}

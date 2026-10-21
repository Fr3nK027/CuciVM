package com.cuvm.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WrappedFolderTest {
    @Test fun prootDistroTarballIsWrapped() = assertTrue(wrappedInFolder("debian-trixie-aarch64/"))
    @Test fun paxHeaderIsSkipped() {
        fun header(name: String, type: Char, size: Int) = ByteArray(512).also { b ->
            name.toByteArray().copyInto(b)
            "%011o".format(size).toByteArray().copyInto(b, 124)
            b[156] = type.code.toByte()
        }
        val tar = header("./PaxHeaders/fedora-aarch64", 'x', 30) + ByteArray(512) + header("fedora-aarch64/", '5', 0)
        val first = firstTarEntry(tar.inputStream())
        org.junit.Assert.assertEquals("fedora-aarch64/", first)
        assertTrue(wrappedInFolder(first))
    }

    @Test fun plainRootfsIsNot() {
        assertFalse(wrappedInFolder("./"))
        assertFalse(wrappedInFolder("./bin"))
        assertFalse(wrappedInFolder("bin/"))
        assertFalse(wrappedInFolder("etc/hosts"))
        assertFalse(wrappedInFolder("README"))
        assertFalse(wrappedInFolder(null))
    }
}

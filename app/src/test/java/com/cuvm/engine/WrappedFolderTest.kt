package com.cuvm.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WrappedFolderTest {
    @Test fun prootDistroTarballIsWrapped() = assertTrue(wrappedInFolder("debian-trixie-aarch64/"))
    @Test fun plainRootfsIsNot() {
        assertFalse(wrappedInFolder("./"))
        assertFalse(wrappedInFolder("./bin"))
        assertFalse(wrappedInFolder("bin/"))
        assertFalse(wrappedInFolder("etc/hosts"))
        assertFalse(wrappedInFolder("README"))
        assertFalse(wrappedInFolder(null))
    }
}

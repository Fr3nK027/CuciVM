package com.cuvm.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files

class SafeDeleteTest {
    @Test fun deletesTreeButNotSymlinkTargets() {
        val outside = Files.createTempDirectory("outside").toFile()
        val precious = outside.resolve("photo.jpg").apply { writeText("keep me") }
        val vm = Files.createTempDirectory("vm").toFile()
        vm.resolve("rootfs/usr/bin").mkdirs()
        vm.resolve("rootfs/usr/bin/sh").writeText("x")
        val link = vm.resolve("rootfs/root/dl")
        link.parentFile!!.mkdirs()
        val made = runCatching { Files.createSymbolicLink(link.toPath(), outside.toPath()) }.isSuccess
        assumeTrue("symlinks not permitted on this machine", made)

        assertTrue(vm.deleteTree())
        assertFalse(vm.exists())
        assertTrue("target of a guest symlink must survive", precious.exists())
        outside.deleteTree()
    }
}

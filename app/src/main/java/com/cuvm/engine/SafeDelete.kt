package com.cuvm.engine

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * Deletes a directory tree WITHOUT following symlinks. Guest filesystems are full of links to absolute
 * paths (/storage/emulated/0, /system...) that resolve on the phone: File.deleteRecursively() would walk
 * into them and delete the user's real files. Links are removed as links.
 */
fun File.deleteTree(): Boolean {
    val root = toPath()
    if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return true
    if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return runCatching { Files.delete(root) }.isSuccess
    var ok = true
    Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
            // Owner needs write+exec on a dir to empty it; ROM dirs can be read-only.
            runCatching { dir.toFile().setWritable(true, true); dir.toFile().setExecutable(true, true) }
            return FileVisitResult.CONTINUE
        }
        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            try { Files.delete(file) } catch (_: IOException) { ok = false }
            return FileVisitResult.CONTINUE
        }
        override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
            try { Files.delete(file) } catch (_: IOException) { ok = false }
            return FileVisitResult.CONTINUE
        }
        override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
            try { Files.delete(dir) } catch (_: IOException) { ok = false }
            return FileVisitResult.CONTINUE
        }
    })
    return ok
}

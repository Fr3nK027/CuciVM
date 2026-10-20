package com.cuvm.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guards catalog/catalog.json, which is edited by hand on GitHub. */
class CatalogueTest {
    private val json = File("../catalog/catalog.json").readText()

    @Test fun shippedCatalogueIsValid() {
        val p = Catalogue.parse(json, 0)
        assertTrue(p.images.isNotEmpty())
        assertEquals(p.images.size, p.images.map { it.id }.toSet().size)
        for (img in p.images) {
            assertTrue("${img.id}: engine", img.engine in setOf("proot", "qemu", "android_container"))
            assertTrue("${img.id}: url or parts", img.downloadUrl != null || Catalogue.parts(img).isNotEmpty())
            assertTrue("${img.id}: sha256", img.sha256?.matches(Regex("[0-9a-f]{64}")) == true)
            assertTrue("${img.id}: logo", img.logoUrl?.startsWith("https://") == true)
            if (img.engine == "proot") assertTrue("${img.id}: session", !img.session.isNullOrBlank())
        }
    }

    @Test fun partsAndMirrorsParse() {
        val p = Catalogue.parse(
            """{"version":1,"images":[{"id":"w","os":"windows_full","name":"W","engine":"qemu","sha256":"${"a".repeat(64)}",
               "parts":[{"url":"https://x/1","size":10},{"url":"https://x/2","size":5,"sha256":"${"b".repeat(64)}"}],
               "mirrors":["https://m/1"]}]}""", 0,
        )
        val img = p.images.single()
        assertEquals(listOf("https://x/1", "https://x/2"), Catalogue.parts(img).map { it.url })
        assertEquals(15L, Catalogue.parts(img).sumOf { it.size })
        assertEquals(listOf("https://m/1"), Catalogue.urls(img))
    }
}

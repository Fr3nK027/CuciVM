package com.cuvm.data

import org.json.JSONArray
import org.json.JSONObject

/** What a proot guest runs on first boot (setup) and on every boot (session). Shell, inside the guest. */
data class Profile(val setup: String, val session: String)

data class Part(val url: String, val size: Long, val sha256: String?)

/**
 * The system list lives in catalog/catalog.json: built into the APK (assets) and refreshed from GitHub.
 * Same file, same parser, so the offline copy and the online one never drift apart.
 */
object Catalogue {
    const val REMOTE_URL = "https://raw.githubusercontent.com/Fr3nK027/CuciVM/main/catalog/catalog.json"

    class Parsed(val images: List<GuestImage>, val osLogos: Map<String, String>)

    fun parse(json: String, now: Long): Parsed {
        val root = JSONObject(json)
        require(root.optInt("version") == 1) { "unsupported catalogue version" }
        val list = root.getJSONArray("images")
        val images = (0 until list.length()).map { i ->
            val o = list.getJSONObject(i)
            GuestImage(
                id = o.getString("id"), os = o.getString("os"), name = o.getString("name"),
                versionLabel = o.optString("version"), arch = o.optString("arch", "arm64"),
                engine = o.getString("engine"), source = "catalogue",
                downloadUrl = o.optStringOrNull("url"), sha256 = o.optStringOrNull("sha256"),
                sizeBytes = o.optLong("size"), localPath = null, status = "remote",
                createdAt = now, updatedAt = now,
                logoUrl = o.optStringOrNull("logo"),
                mirrors = o.optJSONArray("mirrors")?.toString(),
                parts = o.optJSONArray("parts")?.toString(),
                setup = o.optStringOrNull("setup"), session = o.optStringOrNull("session"),
            )
        }
        val logos = root.optJSONObject("logos")?.let { l -> l.keys().asSequence().associateWith { l.getString(it) } } ?: emptyMap()
        return Parsed(images, logos)
    }

    fun parts(image: GuestImage): List<Part> = image.parts?.let { s ->
        val a = JSONArray(s)
        (0 until a.length()).map { a.getJSONObject(it).let { p -> Part(p.getString("url"), p.optLong("size"), p.optStringOrNull("sha256")) } }
    } ?: emptyList()

    fun urls(image: GuestImage): List<String> =
        listOfNotNull(image.downloadUrl) + (image.mirrors?.let { s -> JSONArray(s).let { a -> (0 until a.length()).map(a::getString) } } ?: emptyList())

    // Custom rootfs: must already contain Xvnc; start the first desktop it has.
    private val custom = Profile(
        setup = "",
        session = "for s in startxfce4 openbox-session openbox xterm; do command -v \$s >/dev/null && exec \$s; done",
    )

    fun profile(image: GuestImage) = Profile(image.setup ?: custom.setup, image.session ?: custom.session)

    private fun JSONObject.optStringOrNull(k: String) = if (has(k) && !isNull(k)) getString(k) else null
}

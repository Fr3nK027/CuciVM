package app.vmbox.data

/** What the guest runs on first boot (setup) and on every boot (session). Shell, inside the guest. */
data class Profile(val setup: String, val session: String)

object Catalogue {
    private const val ALPINE_URL =
        "https://dl-cdn.alpinelinux.org/alpine/v3.24/releases/aarch64/alpine-minirootfs-3.24.2-aarch64.tar.gz"
    private const val ALPINE_SHA = "9bf70a7f18ea44094cbb5f70c58f9af129c8214745743db0e68e5502cc2ce773"
    private const val ALPINE_SIZE = 4_028_030L

    private fun alpine(id: String, name: String) = GuestImage(
        id = id, os = "linux", name = name, versionLabel = "3.24.2", arch = "arm64", engine = "proot",
        source = "catalogue", downloadUrl = ALPINE_URL, sha256 = ALPINE_SHA, sizeBytes = ALPINE_SIZE,
        localPath = null, status = "remote", createdAt = 0, updatedAt = 0,
    )

    val images = listOf(
        alpine("alpine-openbox", "Alpine Linux · Openbox"),
        alpine("alpine-xfce", "Alpine Linux · XFCE"),
    )

    private val profiles = mapOf(
        "alpine-openbox" to Profile(
            setup = "apk update && apk add tigervnc openbox xterm font-dejavu",
            session = "xterm -geometry 80x24+8+8 & exec openbox",
        ),
        "alpine-xfce" to Profile(
            setup = "apk update && apk add tigervnc xfce4 xfce4-terminal dbus-x11 font-dejavu adwaita-icon-theme",
            session = "exec dbus-launch --exit-with-session startxfce4",
        ),
    )

    // Custom rootfs: must already contain Xvnc; start the first desktop it has.
    private val custom = Profile(
        setup = "",
        session = "for s in startxfce4 openbox-session openbox xterm; do command -v \$s >/dev/null && exec \$s; done",
    )

    fun profile(imageId: String) = profiles[imageId] ?: custom
}

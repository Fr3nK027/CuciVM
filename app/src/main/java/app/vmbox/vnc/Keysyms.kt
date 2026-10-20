package app.vmbox.vnc

/** X11 keysyms (X11/keysymdef.h). */
object Keysyms {
    const val BACKSPACE = 0xff08
    const val TAB = 0xff09
    const val RETURN = 0xff0d
    const val ESCAPE = 0xff1b
    const val LEFT = 0xff51
    const val UP = 0xff52
    const val RIGHT = 0xff53
    const val DOWN = 0xff54
    const val CTRL = 0xffe3
    const val ALT = 0xffe9
    const val SUPER = 0xffeb
    const val DELETE = 0xffff

    /** Latin-1 maps 1:1, other Unicode code points use the 0x01000000 range. */
    fun forChar(c: Int): Int = when {
        c == '\n'.code -> RETURN
        c == '\t'.code -> TAB
        c in 0x20..0xff -> c
        else -> 0x01000000 or c
    }
}

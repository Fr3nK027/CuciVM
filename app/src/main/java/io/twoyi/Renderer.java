// JNI bridge for libtwoyi.so (Twoyi, MPL-2.0, https://github.com/twoyi/twoyi).
// The library registers these natives on "io/twoyi/Renderer" in JNI_OnLoad, so the class name must stay.
// Unlike upstream there is no static loadLibrary: CuciVM loads the patched libraries by absolute path.
package io.twoyi;

import android.view.MotionEvent;
import android.view.Surface;

public class Renderer {
    public static native void init(Surface surface, String loader, float xdpi, float ydpi, int fps);
    public static native void resetWindow(Surface surface, int top, int left, int width, int height);
    public static native void removeWindow(Surface surface);
    public static native void handleTouch(MotionEvent event);
    public static native void sendKeycode(int keycode);
}

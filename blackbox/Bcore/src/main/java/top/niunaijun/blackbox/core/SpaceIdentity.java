package top.niunaijun.blackbox.core;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Properties;

/**
 * CuciVM addition. The device identity of one space (one virtual user): what its apps see as
 * model, brand, Android ID and IMEI. Written by the host app to files/space_identity/&lt;user&gt;.properties
 * before it launches an app; read once per app process.
 */
public class SpaceIdentity {
    public static volatile String androidId;
    public static volatile String imei;

    public static File file(Context context, int userId) {
        return new File(context.getFilesDir(), "space_identity/" + userId + ".properties");
    }

    public static void load(Context context, int userId) {
        Properties p = new Properties();
        try (InputStream in = new FileInputStream(file(context, userId))) {
            p.load(in);
        } catch (Exception e) {
            return; // no identity set for this space: the phone's own values stay
        }
        androidId = blankToNull(p.getProperty("androidId"));
        imei = blankToNull(p.getProperty("imei"));
        setBuild("MODEL", p.getProperty("model"));
        setBuild("BRAND", p.getProperty("brand"));
        setBuild("MANUFACTURER", p.getProperty("manufacturer"));
        setBuild("DEVICE", p.getProperty("device"));
        setBuild("PRODUCT", p.getProperty("product"));
        setBuild("SERIAL", p.getProperty("serial"));
        android.util.Log.i("SpaceIdentity", "user " + userId + ": " + Build.MANUFACTURER + " " + Build.MODEL);
    }

    private static String blankToNull(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    private static void setBuild(String name, String value) {
        if (blankToNull(value) == null) return;
        try {
            Field f = Build.class.getDeclaredField(name);
            f.setAccessible(true);
            f.set(null, value.trim());
        } catch (Throwable ignored) {
        }
    }
}

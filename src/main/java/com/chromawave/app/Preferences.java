package com.chromawave.app;

import com.chromawave.visual.Palette;

import java.util.prefs.BackingStoreException;

/**
 * Persistencia de ajustes entre sesiones sobre {@link java.util.prefs.Preferences},
 * que en macOS acaba en un plist del usuario y en Windows en el registro.
 */
public final class Preferences {

    private static final java.util.prefs.Preferences NODE =
            java.util.prefs.Preferences.userRoot().node("com/chromawave");

    private Preferences() {
    }

    public static String getVisualizerName(String fallback) {
        return NODE.get("visualizer", fallback);
    }

    public static void setVisualizerName(String name) {
        NODE.put("visualizer", name);
    }

    public static Palette getPalette() {
        try {
            return Palette.valueOf(NODE.get("palette", Palette.NEON.name()));
        } catch (IllegalArgumentException e) {
            return Palette.NEON;
        }
    }

    public static void setPalette(Palette palette) {
        NODE.put("palette", palette.name());
    }

    public static double getDouble(String key, double fallback) {
        return NODE.getDouble(key, fallback);
    }

    public static void setDouble(String key, double value) {
        NODE.putDouble(key, value);
    }

    public static boolean getBoolean(String key, boolean fallback) {
        return NODE.getBoolean(key, fallback);
    }

    public static void setBoolean(String key, boolean value) {
        NODE.putBoolean(key, value);
    }

    public static String getString(String key, String fallback) {
        return NODE.get(key, fallback);
    }

    public static void setString(String key, String value) {
        if (value == null) {
            NODE.remove(key);
        } else {
            NODE.put(key, value);
        }
    }

    public static void flush() {
        try {
            NODE.flush();
        } catch (BackingStoreException e) {
            // Perder las preferencias no debe impedir cerrar la aplicación.
        }
    }
}

package org.kiibord.lcd77;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import android.content.SharedPreferences;
import android.content.res.Resources;
public final class GlobalKeyboardSettings {
    protected static final String TAG = "HK/Globals";
    public float topRowScale = 1.0f;
    public int keyboardModePortrait = 0;public int keyboardModeLandscape = 1; public int keyboardMode = 0;
    public int keyClickMethod = 0;
    public float labelScalePref = 1.0f;
    public int keyboardHeightPercent = 50; public static boolean is_multipointer ; public static boolean is_rect = true;
    public int renderMode = 1;
    public int longpressTimeout = 400;
    public boolean shiftLockModifiers = false;

    public String editorPackageName; public String editorFieldName; public int editorFieldId; public int editorInputType;
    public Locale inputLocale = Locale.getDefault(); private int mCurrentFlags = 0;
    private Map<String, BooleanPref> mBoolPrefs = new HashMap<String, BooleanPref>();   private Map<String, StringPref> mStringPrefs = new HashMap<String, StringPref>();
    public static final int FLAG_PREF_NONE = 0;
    public static final int FLAG_PREF_RECREATE_INPUT_VIEW = 0x4;
    public static final int FLAG_PREF_RESET_KEYBOARDS = 0x8;
    public static final int FLAG_PREF_RESET_MODE_OVERRIDE = 0x10;
    /////////////
    public static final int FLAG_PREF_NEED_RELOAD = 0x1;
    ///////////////
    private interface BooleanPref { void set(boolean val);boolean getDefault();int getFlags();}
    private interface StringPref { void set(String val);String getDefault();int getFlags();}
    public void initPrefs(SharedPreferences prefs, Resources resources) { final Resources res = resources;
        addStringPref("pref_keyboard_mode_portrait", new StringPref() {
            public void set(String val) { keyboardModePortrait = Integer.valueOf(val); }
            public String getDefault() { return res.getString(R.string.default_keyboard_mode_portrait); }
            public int getFlags() { return FLAG_PREF_RESET_KEYBOARDS | FLAG_PREF_RESET_MODE_OVERRIDE; }
        });
        addStringPref("pref_label_scale_v2", new StringPref() {
            public void set(String val) { labelScalePref = Float.valueOf(val); }
            public String getDefault() { return "1.0"; }
            public int getFlags() { return FLAG_PREF_RECREATE_INPUT_VIEW; }
        });
        addStringPref("pref_click_method", new StringPref() {
            public void set(String val) { keyClickMethod = Integer.valueOf(val); }
            public String getDefault() { return res.getString(R.string.default_click_method); }
            public int getFlags() { return FLAG_PREF_NONE; }
        });
        for (String key : mBoolPrefs.keySet()) {
            BooleanPref pref = mBoolPrefs.get(key);
            pref.set(prefs.getBoolean(key, pref.getDefault()));
        }
        for (String key : mStringPrefs.keySet()) {
            StringPref pref = mStringPrefs.get(key);
            pref.set(prefs.getString(key, pref.getDefault()));
        }
    }
    public void sharedPreferenceChanged(SharedPreferences prefs, String key) {
        BooleanPref bPref = mBoolPrefs.get(key);
        if (bPref != null) bPref.set(prefs.getBoolean(key, bPref.getDefault()));
        StringPref sPref = mStringPrefs.get(key);
        if (sPref != null) sPref.set(prefs.getString(key, sPref.getDefault()));
    }

    private void addStringPref(String key, StringPref setter) {
        mStringPrefs.put(key, setter);
    }
    public boolean hasFlag(int flag) {
        if ((mCurrentFlags & flag) != 0) {
            mCurrentFlags &= ~flag;
            return true;
        }
        return false;
    }

    public int unhandledFlags() {
        return mCurrentFlags;
    }

    private void addBooleanPref(String key, BooleanPref setter) {
        mBoolPrefs.put(key, setter);
    }

}

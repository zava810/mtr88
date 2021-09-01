package org.kiibord.lcd77;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Handler;
import android.view.InflateException;

import java.lang.ref.SoftReference;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;

public class KeyboardSwitcher {
    private static final int KEYBOARDMODE_SYMBOL = 1; public static final int KEYBOARDMODE_NORMAL = 1;
    public static LatinKeyboardView mInputView; private LatinIME mInputMethodService;
    public static final int MODE_NONE = 0;public static final int MODE_TEXT = 1; private int mMode = MODE_NONE;
    private int mImeOptions;
    private int mLayoutId;private static final int[] THEMES = new int[] {R.layout.input_ics,}; public static final String DEFAULT_LAYOUT_ID = "0";
    private int m_keyboard_mode;private KeyboardId m_mtr88_keyboard_id;private KeyboardId m_str88_keyboard_id;private boolean mHasVoice = false;
    public static final int KEYBOARDMODE_SYMBOLS_WITH_SETTINGS_KEY = 1;private static final int KBD_SYMBOLS = 1;private static final int KBD_SYMBOLS_SHIFT = 1;
    private static final int KBD_mtr88 = 1;private static final int KBD_str88 = 2; private static final int KBD_QWERTY = 1;
    private static final int KBD_FULL = 1;
    private KeyboardId mSymbolsId; private KeyboardId mSymbolsShiftedId; private KeyboardId mCurrentId;
    private final HashMap<KeyboardId, SoftReference<LatinKeyboard>> mKeyboards = new HashMap<KeyboardId, SoftReference<LatinKeyboard>>();
    private static final KeyboardSwitcher sInstance = new KeyboardSwitcher();public static KeyboardSwitcher getInstance() { return sInstance; }private KeyboardSwitcher() { }
    public static void init(LatinIME arg_lime) { sInstance.mInputMethodService = arg_lime; }

    public LatinKeyboardView getInputView() {
        return mInputView;
    }

    public void changeLatinKeyboardView() {
        if ( mInputView == null ) {
            LatinIMEUtil.GCUtils.getInstance().reset();
            boolean tryGC = true;
            for (int i = 0; i < LatinIMEUtil.GCUtils.GC_TRY_LOOP_MAX && tryGC; ++i) {
                try { mInputView = (LatinKeyboardView) mInputMethodService.getLayoutInflater().inflate(R.layout.input_ics, null);tryGC = false; }
                catch (OutOfMemoryError e) { tryGC = LatinIMEUtil.GCUtils.getInstance().tryGCOrWait(0 + "," + 0, e); }
            }
            mInputView.setOnKeyboardActionListener(mInputMethodService);
        }
        (new Handler()).post(new Runnable() {
            public void run() { if (mInputView != null) mInputMethodService.setInputView(mInputView);mInputMethodService.updateInputViewShown(); }
        });
    }
    public void recreateInputView() { changeLatinKeyboardView(); }
//    public void recreateInputView() { changeLatinKeyboardView(mLayoutId, true); }
    private void changeLatinKeyboardView(int newLayout, boolean forceReset) {
        if (mLayoutId != newLayout || mInputView == null || forceReset) {
            if (THEMES.length <= newLayout) newLayout = Integer.valueOf(DEFAULT_LAYOUT_ID);
            LatinIMEUtil.GCUtils.getInstance().reset();
            boolean tryGC = true;
            for (int i = 0; i < LatinIMEUtil.GCUtils.GC_TRY_LOOP_MAX && tryGC; ++i) {
                try { mInputView = (LatinKeyboardView) mInputMethodService.getLayoutInflater().inflate(THEMES[newLayout], null);tryGC = false; }
                catch (OutOfMemoryError e) { tryGC = LatinIMEUtil.GCUtils.getInstance().tryGCOrWait(mLayoutId + "," + newLayout, e); } catch (InflateException e) { tryGC = LatinIMEUtil.GCUtils.getInstance().tryGCOrWait(mLayoutId + "," + newLayout, e); }
            }
            mInputView.setPadding(0, 0, 0, 0);
            mLayoutId = newLayout;
        }
        mInputMethodService.mHandler.post(new Runnable() {
            public void run() { if (mInputView != null) mInputMethodService.setInputView(mInputView);mInputMethodService.updateInputViewShown(); }
        });
    }
    private static class KeyboardId {// TODO: should have locale and portrait/landscape orientation?
        public final int mKeyboardMode;public int mKeyboardHeightPercent;private final int mHashCode;
        public KeyboardId(int mode) {
            this.mKeyboardMode = mode;
            this.mKeyboardHeightPercent = LatinIME.sKeyboardSettings.keyboardHeightPercent;
            this.mHashCode = Arrays.hashCode(new Object[] { mode });
        }
        @Override public boolean equals(Object other) { return other instanceof KeyboardId && equals((KeyboardId) other); }
        private boolean equals(KeyboardId other) { return other != null &&  other.mKeyboardMode == this.mKeyboardMode; }
        @Override public int hashCode() { return mHashCode; }
    }
    private KeyboardId make_mtr88_keyboard_id() { if (m_keyboard_mode > 0) return null;return new KeyboardId( KEYBOARDMODE_SYMBOL); }
    public void makeKeyboards() {
        m_keyboard_mode = LatinIME.sKeyboardSettings.keyboardMode; m_mtr88_keyboard_id = make_mtr88_keyboard_id();
        m_mtr88_keyboard_id.mKeyboardHeightPercent = LatinIME.sKeyboardSettings.keyboardHeightPercent ;
        mInputView.requestLayout();mInputView.invalidate();
    }
    public void setKeyboardMode(int mode, int imeOptions) {
        if (mInputView == null) return;
        mMode = mode;
        mImeOptions = imeOptions;
        KeyboardId id = getKeyboardId(mode);
        LatinKeyboard keyboard = null;
        keyboard = getKeyboard(id);
        mCurrentId = id;
        mInputView.setKeyboard(keyboard);
        keyboard.setImeOptions(mInputMethodService.getResources(), mMode, imeOptions);
    }
    private KeyboardId getKeyboardId(int mode) {
        switch (mode) { case MODE_NONE: case MODE_TEXT: return new KeyboardId(KEYBOARDMODE_NORMAL); }
        return null;
    }
    private LatinKeyboard getKeyboard(KeyboardId id) {
        SoftReference<LatinKeyboard> ref = mKeyboards.get(id);
        LatinKeyboard keyboard = (ref == null) ? null : ref.get();
        if (keyboard == null) {
            Resources orig = mInputMethodService.getResources();Configuration conf = orig.getConfiguration();Locale saveLocale = conf.locale;
            conf.locale = LatinIME.sKeyboardSettings.inputLocale;
            orig.updateConfiguration(conf, null);
            id.mKeyboardHeightPercent = LatinIME.sKeyboardSettings.keyboardHeightPercent ;
            keyboard = new LatinKeyboard(mInputMethodService,  id.mKeyboardMode, id.mKeyboardHeightPercent);
            mKeyboards.put(id, new SoftReference<LatinKeyboard>(keyboard));
            conf.locale = saveLocale;
            orig.updateConfiguration(conf, null);
        }
        return keyboard;
    }
    public void toggle_nmlok(){ mInputView.is_nmlk_on =  !mInputView.is_nmlk_on; } public boolean is_nm_lok(){ return mInputView.is_nmlk_on ; }
    public void toggle_sft_lok(){ mInputView.is_sft_on =  !mInputView.is_sft_on; } public boolean is_sft_lok(){ return mInputView.is_sft_on; }
    public void toggle_go_lok(){ mInputView.is_go_on =  !mInputView.is_go_on; mInputView.is_muv_on = false ; } public boolean is_go_lok(){ return mInputView.is_go_on; }
    public void toggle_muv_lok(){ mInputView.is_muv_on =  !mInputView.is_muv_on; mInputView.is_go_on = false ; } public boolean is_muv_lok(){ return mInputView.is_muv_on; }
}
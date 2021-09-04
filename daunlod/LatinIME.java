package org.kiibord.lcd77;

import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class LatinIME extends InputMethodService implements LatinKeyboardView.OnKeyboardActionListener, SharedPreferences.OnSharedPreferenceChangeListener{
    public static final String PREF_SELECTED_LANGUAGES = "selected_languages"; public static final String PREF_INPUT_LANGUAGE = "input_language";
    private static final String NOTIFICATION_CHANNEL_ID = "mtr88_notification_channel";private static final int NOTIFICATION_ONGOING_ID = 1001;
    static final String PREF_KEYBOARD_NOTIFICATION = "keyboard_notification"; private boolean mKeyboardNotification;
    static final String PREF_RENDER_MODE = "pref_render_mode";
    static final String PREF_FORCE_KEYBOARD_ON = "force_keyboard_on";
    static final String PREF_multipointer_ON = "is_multipointer";
    static final String PREF_is_rect = "is_rect";
    private boolean mForceKeyboardOn;
    private boolean is_ptrup_ev; //private final int skey_flags = 0 ;

    private AlertDialog mOptionsDialog;
    KeyboardSwitcher mKeyboardSwitcher; private int mNumKeyboardModes = 2;
    public static final GlobalKeyboardSettings sKeyboardSettings = new GlobalKeyboardSettings();
    static LatinIME sInstance; private Resources mResources;
    private int mOrientation; private int mHeightPortrait;private int mHeightLandscape;
    private int mKeyboardModeOverridePortrait;private int mKeyboardModeOverrideLandscape;
    static final String PREF_HEIGHT_PORTRAIT = "settings_height_portrait";static final String PREF_HEIGHT_LANDSCAPE = "settings_height_landscape";
    private PluginManager mPluginManager; Handler mHandler = new Handler();
    private static InputConnection ic;
    private static int l88bytes;
    private static int kk = KeyEvent.KEYCODE_UNKNOWN; private static int prev_kk = KeyEvent.KEYCODE_UNKNOWN;
    private static CharSequence ksek = null ; private static CharSequence prev_ksek = null ;
    private static int meta; private static int prev_meta; private static  boolean prev_is_send_or_commit = true ;
    private static boolean isl88_up_pending = true; private static boolean send_y_commit_n = true ;
    @Override public void onCreate() { super.onCreate();sInstance = this;
        mResources = getResources();final Configuration conf = mResources.getConfiguration(); mOrientation = conf.orientation;
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        LanguageSwitcher mLanguageSwitcher = new LanguageSwitcher(this);
        mLanguageSwitcher.loadLocales(prefs);mLanguageSwitcher.setSystemLocale(conf.locale);
        String inputLanguage = mLanguageSwitcher.getInputLanguage();
        if (inputLanguage == null) inputLanguage = conf.locale.toString();
        KeyboardSwitcher.init(this);mKeyboardSwitcher = KeyboardSwitcher.getInstance();
        mHeightPortrait = getHeight(prefs, PREF_HEIGHT_PORTRAIT, mResources.getString(R.string.default_height_portrait));
        mHeightLandscape = getHeight(prefs, PREF_HEIGHT_LANDSCAPE, mResources.getString(R.string.default_height_landscape));
        LatinIME.sKeyboardSettings.renderMode = getPrefInt(prefs, PREF_RENDER_MODE, mResources.getString(R.string.default_render_mode));
        sKeyboardSettings.initPrefs(prefs, mResources);
        updateKeyboardOptions();

        mPluginManager = new PluginManager(this);final IntentFilter pFilter = new IntentFilter();
        pFilter.addDataScheme("package");pFilter.addAction("android.intent.action.PACKAGE_ADDED");pFilter.addAction("android.intent.action.PACKAGE_REPLACED");pFilter.addAction("android.intent.action.PACKAGE_REMOVED");
        registerReceiver(mPluginManager, pFilter);
        LatinIMEUtil.GCUtils.getInstance().reset();
        boolean tryGC = true;
        for (int i = 0; i < LatinIMEUtil.GCUtils.GC_TRY_LOOP_MAX && tryGC; ++i) {
            try { tryGC = false; } catch (OutOfMemoryError e) { tryGC = LatinIMEUtil.GCUtils.getInstance().tryGCOrWait(inputLanguage, e); }
        }
        prefs.registerOnSharedPreferenceChangeListener(this);
    }
    private void updateKeyboardOptions() {
        boolean isPortrait = isPortrait(); mNumKeyboardModes =  2;
        int screenHeightPercent = isPortrait ? mHeightPortrait : mHeightLandscape;
        if(GlobalKeyboardSettings.is_multipointer || !LatinIME.sKeyboardSettings.is_rect)
            {LatinIME.sKeyboardSettings.keyboardHeightPercent = screenHeightPercent;}
        else
            {LatinIME.sKeyboardSettings.keyboardHeightPercent =  20;}
    }
    private NotificationReceiver mNotificationReceiver;
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.notification_channel_name);
            String description = getString(R.string.notification_channel_description);
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    private void setNotification(boolean visible) {
        String ns = Context.NOTIFICATION_SERVICE; NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);

        if (visible && mNotificationReceiver == null) {
            createNotificationChannel();
            int icon = R.drawable.icon;
            CharSequence text = "Keyboard notification enabled.";
            long when = System.currentTimeMillis();

            // TODO: clean this up?
            mNotificationReceiver = new NotificationReceiver(this);
            final IntentFilter pFilter = new IntentFilter(NotificationReceiver.ACTION_SHOW);
            pFilter.addAction(NotificationReceiver.ACTION_SETTINGS);
            registerReceiver(mNotificationReceiver, pFilter);

            Intent notificationIntent = new Intent(NotificationReceiver.ACTION_SHOW);
            PendingIntent contentIntent = PendingIntent.getBroadcast(getApplicationContext(), 1, notificationIntent, 0);
            //PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

            Intent configIntent = new Intent(NotificationReceiver.ACTION_SETTINGS);
            PendingIntent configPendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 2, configIntent, 0);

            String title = "sho mtr88 keypad"; String body = "select this to open mtr88 keypad. disable in settings.";

            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.icon_hk_notification).setColor(0xff220044).setAutoCancel(false)
                    .setTicker(text).setContentTitle(title).setContentText(body).setContentIntent(contentIntent)
                    .setOngoing(true).addAction(R.drawable.icon_hk_notification, getString(R.string.notification_action_settings), configPendingIntent)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);


            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

            notificationManager.notify(NOTIFICATION_ONGOING_ID, mBuilder.build());

        } else if (mNotificationReceiver != null) {
            mNotificationManager.cancel(NOTIFICATION_ONGOING_ID);
            unregisterReceiver(mNotificationReceiver);
            mNotificationReceiver = null;
        }
    }
    private boolean isPortrait() { return (mOrientation == Configuration.ORIENTATION_PORTRAIT); }
    @Override public View onCreateInputView() {
//        setCandidatesViewShown(false);  // Workaround for "already has a parent" when reconfiguring
        mKeyboardSwitcher.recreateInputView();
        mKeyboardSwitcher.makeKeyboards();
        mKeyboardSwitcher.setKeyboardMode(1, 0);
        return mKeyboardSwitcher.getInputView();
    }
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        boolean needReload = false;Resources res = getResources(); sKeyboardSettings.sharedPreferenceChanged(sharedPreferences, key);
        if (sKeyboardSettings.hasFlag(GlobalKeyboardSettings.FLAG_PREF_NEED_RELOAD)) needReload = true;
        if (sKeyboardSettings.hasFlag(GlobalKeyboardSettings.FLAG_PREF_RESET_MODE_OVERRIDE)) {
            mKeyboardModeOverrideLandscape = 0;mKeyboardModeOverridePortrait = 0;
        }
        if (sKeyboardSettings.hasFlag(GlobalKeyboardSettings.FLAG_PREF_RESET_KEYBOARDS)) { }
        int unhandledFlags = sKeyboardSettings.unhandledFlags();
        if (unhandledFlags != GlobalKeyboardSettings.FLAG_PREF_NONE) { }
        if (PREF_FORCE_KEYBOARD_ON.equals(key)) {
            mForceKeyboardOn = sharedPreferences.getBoolean(PREF_FORCE_KEYBOARD_ON, res.getBoolean(R.bool.default_force_keyboard_on));needReload = true;
        } else if (PREF_multipointer_ON.equals(key)) {
            GlobalKeyboardSettings.is_multipointer = sharedPreferences.getBoolean(PREF_multipointer_ON, res.getBoolean(R.bool.is_multiponter));
            needReload = true;
        } else if (PREF_is_rect.equals(key)) {
            LatinIME.sKeyboardSettings.is_rect = sharedPreferences.getBoolean(PREF_is_rect, res.getBoolean(R.bool.is_rect));needReload = true;
        } else if (PREF_KEYBOARD_NOTIFICATION.equals(key)) {
            mKeyboardNotification = sharedPreferences.getBoolean(PREF_KEYBOARD_NOTIFICATION, res.getBoolean(R.bool.default_keyboard_notification));setNotification(mKeyboardNotification);
        } else if (PREF_HEIGHT_PORTRAIT.equals(key)) {
            mHeightPortrait = getHeight(sharedPreferences, PREF_HEIGHT_PORTRAIT, res.getString(R.string.default_height_portrait));needReload = true;
        } else if (PREF_HEIGHT_LANDSCAPE.equals(key)) {
            mHeightLandscape = getHeight(sharedPreferences, PREF_HEIGHT_LANDSCAPE, res.getString(R.string.default_height_landscape));needReload = true;
        } else if (PREF_RENDER_MODE.equals(key)) {
            LatinIME.sKeyboardSettings.renderMode = getPrefInt(sharedPreferences, PREF_RENDER_MODE, res.getString(R.string.default_render_mode));needReload = true;
        }
        updateKeyboardOptions();
        if (needReload) {
            mKeyboardSwitcher.makeKeyboards();
            mKeyboardSwitcher.setKeyboardMode(1, 0);
        }
    }
    @Override public AbstractInputMethodImpl onCreateInputMethodInterface() { return new MyInputMethodImpl(); }
    IBinder mToken;
    public class MyInputMethodImpl extends InputMethodImpl {
        @Override public void attachToken(IBinder token) { super.attachToken(token);if (mToken == null) mToken = token; }
    }
    @Override public void onStartInputView(EditorInfo attribute, boolean restarting) {
        sKeyboardSettings.editorPackageName = attribute.packageName;
        sKeyboardSettings.editorFieldName = attribute.fieldName;
        sKeyboardSettings.editorFieldId = attribute.fieldId;
        sKeyboardSettings.editorInputType = attribute.inputType;
        TextEntryState.newSession(this);
    }
    @Override public void hideWindow() {
        if (mOptionsDialog != null && mOptionsDialog.isShowing()) { mOptionsDialog.dismiss();mOptionsDialog = null; }
        super.hideWindow();
        TextEntryState.endSession();
    }
    public static void send_kk() {
        if( kk > KeyEvent.KEYCODE_UNKNOWN || ksek != null) {
            if (send_y_commit_n) {
                ic.sendKeyEvent(new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, kk,0, meta));
                ic.sendKeyEvent(new KeyEvent(0, 0, KeyEvent.ACTION_UP, kk,0, meta));
            } else {
                ic.commitText(ksek,1);
                final char temp = ksek.charAt(0);
                if(temp == '[' || temp == '(' || temp == '{' || temp == '"' || temp == '<') {
                    ic.sendKeyEvent(new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,0));
                    ic.sendKeyEvent(new KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT,0));
                }
            }
            meta = 0; prev_kk = kk ; prev_ksek = ksek ; kk = KeyEvent.KEYCODE_UNKNOWN ; ksek = null;
            isl88_up_pending = false ;
        }
    }
    // / handling segbytes
    public void toogle_handler(){
        if(isl88_up_pending) {
            isl88_up_pending = false;
            switch (l88bytes) {
                case 0xC07F: mKeyboardSwitcher.toggle_nmlok(); break;
                case 0x447F: mKeyboardSwitcher.toggle_sft_lok(); break;
                case 0x417F: mKeyboardSwitcher.toggle_go_lok(); break;
                case 0x427F: mKeyboardSwitcher.toggle_muv_lok(); break;
                default: isl88_up_pending = true ;
            }
        }
    }
    public boolean onText(int argl88bytes) { ic = getCurrentInputConnection(); if (ic == null) return true;
        ic.beginBatchEdit();isl88_up_pending = true; meta = 0; l88bytes = argl88bytes;  kk = KeyEvent.KEYCODE_UNKNOWN ; ksek = null;
        if (isl88_up_pending) toogle_handler(); //807F
        if (isl88_up_pending) muv_go_num_fn_minu_sft();
        if (isl88_up_pending) num_fn_minu_sft();
        if (isl88_up_pending) send_fn_on();
        if (isl88_up_pending) send_fn_oph();
        if (isl88_up_pending) send_minu();
        if (isl88_up_pending) send_06();
        if (isl88_up_pending) send_8E();
        if (isl88_up_pending) send_06_nsd23();
        if (isl88_up_pending) { send_special(); }
        if (isl88_up_pending) send_knt_alt_sft();
        ic.endBatchEdit();
        return isl88_up_pending;
    }
    public void muv_go_num_fn_minu_sft(){
        isl88_up_pending = false ; meta = 0 ; kk = KeyEvent.KEYCODE_UNKNOWN ; ksek = null ; send_y_commit_n = true;
        int num88bytes =  l88bytes;
        if ( mKeyboardSwitcher.is_nm_lok() ) {num88bytes =  num88bytes ^ 0x8000 ; }
        if ( mKeyboardSwitcher.is_sft_lok() ) {num88bytes =  num88bytes ^ 0x0400 ; }
        if ( mKeyboardSwitcher.is_go_lok() ) {num88bytes =  num88bytes ^ 0x0100 ; }
        if ( mKeyboardSwitcher.is_muv_lok() ) {num88bytes =  num88bytes ^ 0x0200 ; }
        switch (num88bytes) {
            case 0x007B: kk = KeyEvent.KEYCODE_C; break; // muv_go_num_fn_minu_sft
            case 0x047B: send_y_commit_n = false; ksek = "C"; break; // muv_go_num_fn_minu_sft : sft
            case 0x807B: kk = KeyEvent.KEYCODE_2; break;  // muv_go_num_fn_minu_sft : 123
            case 0x027B: kk = KeyEvent.KEYCODE_TAB; break; // muv_go_num_fn_minu_sft : tAb + muv
            case 0x017B: kk = KeyEvent.KEYCODE_TAB; break; // muv_go_num_fn_minu_sft : tAb + go
            case 0x0A7B: meta = meta | KeyEvent.META_ALT_ON ; kk = KeyEvent.KEYCODE_TAB; break; // muv_go_num_fn_minu_sft : alt + muv + tAb
            case 0x097B: meta = meta | KeyEvent.META_ALT_ON ;kk = KeyEvent.KEYCODE_TAB; break; // muv_go_num_fn_minu_sft : alt + go + tAb
            case 0x067B: meta = meta | KeyEvent.META_SHIFT_ON ; kk = KeyEvent.KEYCODE_TAB; break; // muv_go_num_fn_minu_sft : tAb + muv + sft
            case 0x057B: meta = meta | KeyEvent.META_SHIFT_ON ; kk = KeyEvent.KEYCODE_TAB; break; // muv_go_num_fn_minu_sft : tAb + go + sft
            // pnc cases
            case 0x847B: kk = KeyEvent.KEYCODE_2; break;  // muv_go_num_fn_minu_sft : sft+123 pnc
            case 0x827B: kk = KeyEvent.KEYCODE_TAB; break; // muv_go_num_fn_minu_sft : tAb + muv +123 pnc
            case 0x817B: kk = KeyEvent.KEYCODE_TAB; break; // muv_go_num_fn_minu_sft : tAb + go +123 pnc
            case 0x867B: meta = meta | KeyEvent.META_SHIFT_ON ; kk = KeyEvent.KEYCODE_TAB; break; // muv_go_num_fn_minu_sft : tAb + muv + sft + 123 pnc
            case 0x857B: meta = meta | KeyEvent.META_SHIFT_ON ; kk = KeyEvent.KEYCODE_TAB; break; // muv_go_num_fn_minu_sft : tAb + go + sft + 123 pnc
            case 0x0E7B: meta = meta | KeyEvent.META_ALT_ON ; kk = KeyEvent.KEYCODE_TAB; break; // muv_go_num_fn_minu_sft : alt + muv + tAb + sft
            case 0x0D7B: meta = meta | KeyEvent.META_ALT_ON ;kk = KeyEvent.KEYCODE_TAB; break; // muv_go_num_fn_minu_sft : alt + go + tAb + sft
            case 0x8A7B: meta = meta | KeyEvent.META_ALT_ON ; kk = KeyEvent.KEYCODE_TAB; break; // muv_go_num_fn_minu_sft : alt + muv + tAb + 123
            case 0x897B: meta = meta | KeyEvent.META_ALT_ON ;kk = KeyEvent.KEYCODE_TAB; break; // muv_go_num_fn_minu_sft : alt + go + tAb + 123
            case 0x8E7B: meta = meta | KeyEvent.META_ALT_ON ; kk = KeyEvent.KEYCODE_TAB; break; // muv_go_num_fn_minu_sft : alt + muv + tAb + sft + 123
            case 0x8D7B: meta = meta | KeyEvent.META_ALT_ON ;kk = KeyEvent.KEYCODE_TAB; break; // muv_go_num_fn_minu_sft : alt + go + tAb + sft + 123

            case 0x0077: kk = KeyEvent.KEYCODE_U; break; // muv_go_num_fn_minu_sft
            case 0x0477: send_y_commit_n = false; ksek = "U"; break; // muv_go_num_fn_minu_sft : sft
            case 0x8077: kk = KeyEvent.KEYCODE_3; break;  // muv_go_num_fn_minu_sft : 123
            case 0x0277: kk = KeyEvent.KEYCODE_PAGE_UP; break; // muv_go_num_fn_minu_sft : muv
            case 0x0177: kk = KeyEvent.KEYCODE_DPAD_UP; break; // muv_go_num_fn_minu_sft : go
            case 0x0677: meta = meta | KeyEvent.META_SHIFT_ON ; kk = KeyEvent.KEYCODE_PAGE_UP; break; // muv_go_num_fn_minu_sft : muv + sft
            case 0x0577: meta = meta | KeyEvent.META_SHIFT_ON ; kk = KeyEvent.KEYCODE_DPAD_UP; break; // muv_go_num_fn_minu_sft : go + sft
            // pnc cases
            case 0x8477: kk = KeyEvent.KEYCODE_3; break;  // muv_go_num_fn_minu_sft : sft+123 pnc
            case 0x8277: kk = KeyEvent.KEYCODE_PAGE_UP; break; // muv_go_num_fn_minu_sft : muv+123 pnc
            case 0x8177: kk = KeyEvent.KEYCODE_DPAD_UP; break; // muv_go_num_fn_minu_sft : go+123 pnc
            case 0x8677: meta = meta | KeyEvent.META_SHIFT_ON ; kk = KeyEvent.KEYCODE_PAGE_UP; break; // muv_go_num_fn_minu_sft : muv + sft + 123 pnc
            case 0x8577: meta = meta | KeyEvent.META_SHIFT_ON ; kk = KeyEvent.KEYCODE_DPAD_UP; break; // muv_go_num_fn_minu_sft : go + sft + 123 pnc

            case 0x007E: send_y_commit_n = false; ksek = "A"; break; // muv_go_num_fn_minu_sft
            case 0x047E: send_y_commit_n = false; ksek = "&"; break; // muv_go_num_fn_minu_sft : sft
            case 0x807E: kk = KeyEvent.KEYCODE_0; break; // muv_go_num_fn_minu_sft : 123
            case 0x027E: kk = KeyEvent.KEYCODE_PAGE_DOWN; break; // muv_go_num_fn_minu_sft : muv
            case 0x017E: kk = KeyEvent.KEYCODE_DPAD_DOWN; break; // muv_go_num_fn_minu_sft : go
            case 0x067E: meta = meta | KeyEvent.META_SHIFT_ON ;  kk = KeyEvent.KEYCODE_PAGE_DOWN; break; // muv_go_num_fn_minu_sft : muv + sft
            case 0x057E: meta = meta | KeyEvent.META_SHIFT_ON ;  kk = KeyEvent.KEYCODE_DPAD_DOWN; break; // muv_go_num_fn_minu_sft : go + sft
            // pnc cases
            case 0x847E: kk = KeyEvent.KEYCODE_0; break;  // muv_go_num_fn_minu_sft : sft+123 pnc
            case 0x827E: kk = KeyEvent.KEYCODE_PAGE_DOWN; break; // muv_go_num_fn_minu_sft : muv+123 pnc
            case 0x817E: kk = KeyEvent.KEYCODE_DPAD_DOWN; break; // muv_go_num_fn_minu_sft : go+123 pnc
            case 0x867E: meta = meta | KeyEvent.META_SHIFT_ON ;  kk = KeyEvent.KEYCODE_PAGE_DOWN; break; // muv_go_num_fn_minu_sft : muv + sft + 123 pnc
            case 0x857E: meta = meta | KeyEvent.META_SHIFT_ON ;  kk = KeyEvent.KEYCODE_DPAD_DOWN; break; // muv_go_num_fn_minu_sft : go + sft + 123 pnc

            case 0x003F: kk = KeyEvent.KEYCODE_I; break; // muv_go_num_fn_minu_sft
            case 0x043F: send_y_commit_n = false; ksek = "I"; break; // muv_go_num_fn_minu_sft : sft
            case 0x803F: kk = KeyEvent.KEYCODE_6; break; // muv_go_num_fn_minu_sft : 123
            case 0x023F: kk = KeyEvent.KEYCODE_MOVE_HOME; break; // muv_go_num_fn_minu_sft : muv
            case 0x013F: kk = KeyEvent.KEYCODE_DPAD_LEFT; break; // muv_go_num_fn_minu_sft : go
            case 0x063F: meta = meta | KeyEvent.META_SHIFT_ON ;  kk = KeyEvent.KEYCODE_MOVE_HOME; break; // muv_go_num_fn_minu_sft : muv + sft
            case 0x053F: meta = meta | KeyEvent.META_SHIFT_ON ;  kk = KeyEvent.KEYCODE_DPAD_LEFT; break; // muv_go_num_fn_minu_sft : go + sft
            case 0x123F: meta = meta | KeyEvent.META_CTRL_ON ; kk = KeyEvent.KEYCODE_MOVE_HOME; break; // muv_go_num_fn_minu_sft : muv + ktl
            case 0x163F: meta = meta | KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON ;  kk = KeyEvent.KEYCODE_MOVE_HOME; break; // muv_go_num_fn_minu_sft : muv + sft + ktl
            // pnc cases
            case 0x843F: kk = KeyEvent.KEYCODE_6; break;  // muv_go_num_fn_minu_sft : sft+123 pnc
            case 0x823F: kk = KeyEvent.KEYCODE_MOVE_HOME; break; // muv_go_num_fn_minu_sft : muv+123 pnc
            case 0x813F: kk = KeyEvent.KEYCODE_DPAD_LEFT; break; // muv_go_num_fn_minu_sft : go+123 pnc
            case 0x863F: meta = meta | KeyEvent.META_SHIFT_ON ;  kk = KeyEvent.KEYCODE_MOVE_HOME; break; // muv_go_num_fn_minu_sft : muv + sft + 123 pnc
            case 0x853F: meta = meta | KeyEvent.META_SHIFT_ON ;  kk = KeyEvent.KEYCODE_DPAD_LEFT; break; // muv_go_num_fn_minu_sft : go + sft + 123 pnc
            case 0x923F: meta = meta | KeyEvent.META_CTRL_ON ; kk = KeyEvent.KEYCODE_MOVE_HOME; break; // muv_go_num_fn_minu_sft : muv + ktl +123 pnc
            case 0x963F: meta = meta | KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON ;  kk = KeyEvent.KEYCODE_MOVE_HOME; break; // muv_go_num_fn_minu_sft : muv + sft + ktl +123 pnc

            case 0x007D: kk = KeyEvent.KEYCODE_E; break; // muv_go_num_fn_minu_sft
            case 0x047D: send_y_commit_n = false; ksek = "E"; break; // muv_go_num_fn_minu_sft : sft
            case 0x807D: kk = KeyEvent.KEYCODE_1; break; // muv_go_num_fn_minu_sft : 123
            case 0x027D: kk = KeyEvent.KEYCODE_MOVE_END; break; // muv_go_num_fn_minu_sft : muv
            case 0x017D: kk = KeyEvent.KEYCODE_DPAD_RIGHT; break; // muv_go_num_fn_minu_sft : go
            case 0x067D: meta = meta | KeyEvent.META_SHIFT_ON ;  kk = KeyEvent.KEYCODE_MOVE_END; break; // muv_go_num_fn_minu_sft : muv + sft
            case 0x057D: meta = meta | KeyEvent.META_SHIFT_ON ;  kk = KeyEvent.KEYCODE_DPAD_RIGHT; break; // muv_go_num_fn_minu_sft : go + sft
            case 0x127D: meta = meta | KeyEvent.META_CTRL_ON ; kk = KeyEvent.KEYCODE_MOVE_END; break; // muv_go_num_fn_minu_sft : muv + ktl
            case 0x167D: meta = meta | KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON ;  kk = KeyEvent.KEYCODE_MOVE_END; break; // muv_go_num_fn_minu_sft : muv + sft + ktl
            // pnc cases
            case 0x847D: kk = KeyEvent.KEYCODE_1; break;  // muv_go_num_fn_minu_sft : sft+123 pnc
            case 0x827D: kk = KeyEvent.KEYCODE_MOVE_HOME; break; // muv_go_num_fn_minu_sft : muv+123 pnc
            case 0x817D: kk = KeyEvent.KEYCODE_DPAD_LEFT; break; // muv_go_num_fn_minu_sft : go+123 pnc
            case 0x867D: meta = meta | KeyEvent.META_SHIFT_ON ;  kk = KeyEvent.KEYCODE_MOVE_END; break; // muv_go_num_fn_minu_sft : muv + sft + 123 pnc
            case 0x857D: meta = meta | KeyEvent.META_SHIFT_ON ;  kk = KeyEvent.KEYCODE_DPAD_RIGHT; break; // muv_go_num_fn_minu_sft : go + sft + 123 pnc
            case 0x927D: meta = meta | KeyEvent.META_CTRL_ON ; kk = KeyEvent.KEYCODE_MOVE_END; break; // muv_go_num_fn_minu_sft : muv + ktl +123 pnc
            case 0x967D: meta = meta | KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON ;  kk = KeyEvent.KEYCODE_MOVE_END; break; // muv_go_num_fn_minu_sft : muv + sft + ktl +123 pnc


            ///////from go
//            case 0x097B: kk = KeyEvent.KEYCODE_TAB; meta = meta | KeyEvent.META_ALT_ON ;break;
//            case 0x0A7B: kk = KeyEvent.KEYCODE_TAB; meta = meta | KeyEvent.META_ALT_ON ;break;
            ///////from go
            default: isl88_up_pending = true ; break ;
        }
        if(!isl88_up_pending) send_kk();
    }
    public void num_fn_minu_sft(){
        isl88_up_pending = false ; meta = 0 ; kk = KeyEvent.KEYCODE_UNKNOWN ; ksek = null ; send_y_commit_n = true;
        int num88bytes =  l88bytes;
        if ( mKeyboardSwitcher.is_nm_lok() ) {num88bytes =  num88bytes ^ 0x8000 ; }
        if ( mKeyboardSwitcher.is_sft_lok() ) {num88bytes =  num88bytes ^ 0x0400 ; }
        switch (num88bytes) {
            // ******************************************************* //
            // num_fn_minu_sft vhite key k2p = 0 keys from 0-6 + sft/dot/num
            // ******************************************************* //
            case 0x007F: send_y_commit_n = false; ksek = "F"; break; // send_y_commit_n = false; ksek = "F"; break; // num
            case 0x047F: kk = KeyEvent.KEYCODE_MINUS; break; // sft
            case 0x847F: send_y_commit_n = false; ksek = "L"; break; // num + stt
            case 0x807F: send_y_commit_n = false; ksek = "F"; break; // kk = KeyEvent.KEYCODE_F; break;

            // ******************************************************* //
            // num_fn_minu_sft yllo key k2p = 0 keys from 0-6 + sft/dot/num
            // ******************************************************* //
            case 0x00FF: kk = KeyEvent.KEYCODE_SPACE; break;
            case 0x04FF: send_y_commit_n = false; ksek = "()"; break; // sft
            case 0x80FF:  kk = KeyEvent.KEYCODE_7; break; //num
            case 0x84FF: kk = KeyEvent.KEYCODE_7; break;  // num + stt

            // ******************************************************* //
            // num_fn_minu_sft k2p = 1 keys from 0-6 bilo + sft/dot/num
            // ******************************************************* //
            case 0x006F:  kk = KeyEvent.KEYCODE_A; break;
            case 0x806F:  kk = KeyEvent.KEYCODE_4; break;  //num
            case 0x046F: send_y_commit_n = false; ksek = "@"; break; //sft
            // pnc case
            case 0x846F:  kk = KeyEvent.KEYCODE_4; break;  //num + sft ignore sft

            case 0x005F:  kk = KeyEvent.KEYCODE_O; break;
            case 0x805F:  kk = KeyEvent.KEYCODE_5; break;  //num
            case 0x045F: send_y_commit_n = false; ksek = "O"; break; //sft
            // pnc case
            case 0x845F:  kk = KeyEvent.KEYCODE_4; break; //num + sft ignore sft

            ////////////////////
            // 82F num keys bilo
            ////////////////////

            case 0x017F:  kk = KeyEvent.KEYCODE_PERIOD; break;
            case 0x817F:  kk = KeyEvent.KEYCODE_8; break; //num
            case 0x057F:  kk = KeyEvent.KEYCODE_PERIOD; break; //sft
            // pnc case
            case 0x857F: kk = KeyEvent.KEYCODE_8; break; //num + sft ignore sft

            case 0x027F:  send_y_commit_n = false; ksek = "_"; break;
            case 0x827F:  kk = KeyEvent.KEYCODE_9; break; //num
            case 0x067F:  send_y_commit_n = false; ksek = "K"; break; //sft
            // pnc case
            case 0x867F: kk = KeyEvent.KEYCODE_9; break; //num + sft ignore sft

            case 0x087F:  kk = KeyEvent.KEYCODE_DEL; break;
            case 0x887F:  send_y_commit_n = false;ksek = "J"; break; //num
            case 0x0C7F:  kk = KeyEvent.KEYCODE_DEL; break; //sft
            // pnc case
            case 0x8C7F: kk = KeyEvent.KEYCODE_DEL; break; //num + sft ignore num as decimal numbers are used more

            case 0x107F: kk = KeyEvent.KEYCODE_ENTER; break;
            case 0x907F: send_y_commit_n = false; ksek = "Q"; break; // num
            case 0x147F: kk = KeyEvent.KEYCODE_ENTER; break; // sft
            // pnc case
            case 0x947F: kk = KeyEvent.KEYCODE_ENTER; break; //num + sft ignore num as decimal numbers are used more

            case 0x207F:  kk = KeyEvent.KEYCODE_FORWARD_DEL; break;
            case 0xA07F: send_y_commit_n = false; ksek = "W" ; break; // num
            case 0x247F: kk = KeyEvent.KEYCODE_FORWARD_DEL; break; // sft
            // pnc case
            case 0xA47F: kk = KeyEvent.KEYCODE_FORWARD_DEL; break; //num + sft ignore num as decimal numbers are used more

            case 0x407F:  send_y_commit_n = false; ksek = "#"; break;
            case 0xC07F: send_y_commit_n = false; ksek = "X"; break; // num
            case 0x447F: kk = KeyEvent.KEYCODE_X; break; // sft
            // pnc case
            case 0xC47F: send_y_commit_n = false; ksek = "#"; break; //num + sft ignore num as decimal numbers are used more

            default: isl88_up_pending = true ; break ;
        }
        if(!isl88_up_pending) send_kk();
    }

    public void send_special(){
        isl88_up_pending = false ; meta = 0 ; kk = KeyEvent.KEYCODE_UNKNOWN ; ksek = null; send_y_commit_n = true;
        switch (l88bytes) {
            case 0x0006: send_y_commit_n = false; ksek = "()"; break;
            case 0x0050: send_y_commit_n = false; ksek = "{}"; break;
            case 0x0060: send_y_commit_n = false; ksek = "()"; break;
            case 0x0030: send_y_commit_n = false; ksek = "()"; break;
            case 0x02BF: send_y_commit_n = false; ksek = "_"; break;
            case 0x03FF: send_y_commit_n = false; ksek = "_"; break;
            case 0x437F: send_y_commit_n = false; ksek = "_"; break;
            case 0x8C6F: send_y_commit_n = false; ksek = "~"; break;
            case 0x9C7F: send_y_commit_n = false; ksek = "~"; break;
            default: isl88_up_pending = true ;
        }
        if(!isl88_up_pending) send_kk();
    }
    public void send_06(){
        isl88_up_pending = false ; meta = 0 ; kk = KeyEvent.KEYCODE_UNKNOWN ; ksek = null; send_y_commit_n = true;
        int num88bytes =  l88bytes;
        if (mKeyboardSwitcher.is_sft_lok() && ((num88bytes & 0x0080) == 0)) // if yllo key is also pressed ignore sft mode/on/key
            { num88bytes =  num88bytes ^ 0x0400 ; }
        switch (num88bytes) {
            // ******************************************************* //
            // send_06 k2p = yllo + 1 = 2 keys from 0-6 bilo + sft/dot
            // ******************************************************* //
            case 0x00FE: send_y_commit_n = false; ksek = "N" ; break; //yllo
            case 0x04FE: kk = KeyEvent.KEYCODE_N; break; //yllo + sft

            case 0x00FD: kk = KeyEvent.KEYCODE_ESCAPE; break; //yllo
            case 0x04FD: kk = KeyEvent.KEYCODE_ESCAPE; break; //yllo + sft

            case 0x00FB: kk = KeyEvent.KEYCODE_SEMICOLON; break; //yllo
            case 0x04FB: send_y_commit_n = false; ksek = ":" ; break; //yllo + sft

            case 0x00F7: kk = KeyEvent.KEYCODE_X; break; //yllo
            case 0x04F7: send_y_commit_n = false; ksek = "X"; break; //yllo + sft

            case 0x00EF: send_y_commit_n = false; ksek = "D"; break; //yllo
            case 0x04EF: kk = KeyEvent.KEYCODE_D; break; //yllo + sft

            case 0x00DF: kk = KeyEvent.KEYCODE_MINUS; break; //yllo
            case 0x04DF: kk = KeyEvent.KEYCODE_EQUALS; break; //yllo + sft

            case 0x00BF: send_y_commit_n = false; ksek = ":" ; break; //yllo
            case 0x04BF: kk = KeyEvent.KEYCODE_SEMICOLON ; break; //yllo + sft

            // ******************************************************* //
            // send_06 k2p = 2 keys from 0-6 bilo + sft/dot/num
            // ******************************************************* //
            case 0x007C: kk = KeyEvent.KEYCODE_P; break;
            case 0x00FC: send_y_commit_n = false; ksek = "+"; break;
            case 0x807C: send_y_commit_n = false; ksek = "P"; break;
            case 0x047C: send_y_commit_n = false; ksek = "P"; break;

            case 0x007A: kk = KeyEvent.KEYCODE_SEMICOLON; break;
            case 0x00FA: send_y_commit_n = false; ksek = "D"; break;
            case 0x807A: send_y_commit_n = false; ksek = "D"; break;
            case 0x047A: send_y_commit_n = false; ksek = "D"; break;

            case 0x0079: kk = KeyEvent.KEYCODE_F; break;
            case 0x00F9: send_y_commit_n = false; ksek = "|"; break;
            case 0x8079: send_y_commit_n = false; ksek = "L"; break;
            case 0x0479: send_y_commit_n = false; ksek = "L"; break;

            case 0x0076: kk = KeyEvent.KEYCODE_H; break;
            case 0x8076: send_y_commit_n = false; ksek = "H"; break;
            case 0x00F6: send_y_commit_n = false; ksek = "#"; break;
            case 0x0476: send_y_commit_n = false; ksek = "H"; break;

            case 0x0075: kk = KeyEvent.KEYCODE_Y; break;
            case 0x8075: send_y_commit_n = false; ksek = "Y"; break;
            case 0x0475: send_y_commit_n = false; ksek = "Y"; break;
            case 0x00F5: kk = KeyEvent.KEYCODE_SEMICOLON; break;

            case 0x0073: kk = KeyEvent.KEYCODE_B; break;
            case 0x8073: send_y_commit_n = false; ksek = "B"; break;
            case 0x0473: send_y_commit_n = false; ksek = "B"; break;
            case 0x00F3: kk = KeyEvent.KEYCODE_GRAVE; break;

            case 0x006E: send_y_commit_n = false; ksek = ":"; break;
            case 0x806E: kk = KeyEvent.KEYCODE_SEMICOLON; break;
            case 0x00EE: kk = KeyEvent.KEYCODE_SEMICOLON; break;
            case 0x046E: kk = KeyEvent.KEYCODE_SEMICOLON; break;

            case 0x006D: kk = KeyEvent.KEYCODE_Z; break;
            case 0x806D: send_y_commit_n = false; ksek = "Z"; break;
            case 0x00ED: kk = KeyEvent.KEYCODE_SEMICOLON; break;
            case 0x046D: send_y_commit_n = false; ksek = "Z"; break;

            case 0x006B: send_y_commit_n = false; ksek = "\"\""; break;
            case 0x806B: send_y_commit_n = false; ksek = "^"; break;
            case 0x00EB: send_y_commit_n = false; ksek = "^"; break;
            case 0x046B: send_y_commit_n = false; ksek = "^"; break;

            case 0x0067: kk = KeyEvent.KEYCODE_D; break;
            case 0x8067: send_y_commit_n = false; ksek = "^"; break;
            case 0x00E7: kk = KeyEvent.KEYCODE_APOSTROPHE; break;
            case 0x0467: send_y_commit_n = false; ksek = "D"; break;


            case 0x005E: kk = KeyEvent.KEYCODE_EQUALS; break;
            case 0x00DE: send_y_commit_n = false; ksek = "*"; break;
            case 0x805E: kk = KeyEvent.KEYCODE_MINUS; break;
            case 0x045E: send_y_commit_n = false; ksek = "M"; break;

            case 0x005D: kk = KeyEvent.KEYCODE_COMMA; break;
            case 0x805D: kk = KeyEvent.KEYCODE_SEMICOLON; break;
            case 0x045D: send_y_commit_n = false; ksek = "L"; break;
            case 0x00DD: kk = KeyEvent.KEYCODE_SEMICOLON; break;

            case 0x005B: kk = KeyEvent.KEYCODE_G; break;
            case 0x805B: send_y_commit_n = false; ksek = "G"; break;
            case 0x00DB: kk = KeyEvent.KEYCODE_COMMA; break;
            case 0x045B: send_y_commit_n = false; ksek = "G"; break;

            case 0x0057: kk = KeyEvent.KEYCODE_V; break;
            case 0x8057: send_y_commit_n = false; ksek = "V"; break;
            case 0x00D7: send_y_commit_n = false; ksek = "!"; break;
            case 0x0457: send_y_commit_n = false; ksek = "V"; break;

            case 0x004F: kk = KeyEvent.KEYCODE_J; break;
            case 0x804F: send_y_commit_n = false; ksek = "J"; break;
            case 0x00CF: kk = KeyEvent.KEYCODE_APOSTROPHE; break;
            case 0x044F: send_y_commit_n = false; ksek = "J"; break;

            case 0x003E: kk = KeyEvent.KEYCODE_Q; break;
            case 0x803E: send_y_commit_n = false; ksek = "Q"; break;
            case 0x00BE: send_y_commit_n = false; ksek = "_"; break;
            case 0x043E: send_y_commit_n = false; ksek = "Q"; break;

            case 0x003D: send_y_commit_n = false; ksek = "T"; break;
            case 0x803D: send_y_commit_n = false; ksek = ":"; break;
            case 0x00BD: send_y_commit_n = false; ksek = "_"; break;
            case 0x043D: send_y_commit_n = false; ksek = ":"; break;

            case 0x003B: kk = KeyEvent.KEYCODE_S; break;
            case 0x00BB: send_y_commit_n = false; ksek = "$"; break;
            case 0x803B: send_y_commit_n = false; ksek = "$"; break;
            case 0x043B: send_y_commit_n = false; ksek = "S"; break;

            case 0x0037: kk = KeyEvent.KEYCODE_Y; break;
            case 0x8037: send_y_commit_n = false; ksek = "Y"; break;
            case 0x00B7: send_y_commit_n = false; ksek = ":"; break;
            case 0x0437: send_y_commit_n = false; ksek = "Y"; break;

            case 0x002F: kk = KeyEvent.KEYCODE_T; break;
            case 0x00AF: kk = KeyEvent.KEYCODE_COMMA; break;
            case 0x802F: kk = KeyEvent.KEYCODE_SEMICOLON; break;
            case 0x042F: send_y_commit_n = false; ksek = "L"; break;

            case 0x001F: kk = KeyEvent.KEYCODE_R; break;
            case 0x009F: send_y_commit_n = false; ksek = "~"; break;
            case 0x801F: send_y_commit_n = false; ksek = "R"; break;
            case 0x041F: send_y_commit_n = false; ksek = "R"; break;

            // ******************************************************* //
            // send_06 k2p = 3 keys from 0-6 bilo + sft/dot/num
            // ******************************************************* //
            case 0x0078: kk = KeyEvent.KEYCODE_F; break;
            case 0x8078: send_y_commit_n = false; ksek = "F"; break;
            case 0x00F8: send_y_commit_n = false; ksek = "!"; break;
            case 0x0478: send_y_commit_n = false; ksek = "F"; break;

            case 0x0074: kk = KeyEvent.KEYCODE_SEMICOLON; break;
            case 0x8074: send_y_commit_n = false; ksek = ":"; break;
            case 0x00F4: send_y_commit_n = false; ksek = "_"; break;
            case 0x0474: send_y_commit_n = false; ksek = ":"; break;

            case 0x0072: kk = KeyEvent.KEYCODE_SEMICOLON; break;
            case 0x8072: send_y_commit_n = false; ksek = ":"; break;
            case 0x00F2: send_y_commit_n = false; ksek = "_"; break;
            case 0x0472: send_y_commit_n = false; ksek = ":"; break;

            case 0x0071: kk = KeyEvent.KEYCODE_T; break;
            case 0x8071: send_y_commit_n = false; ksek = "T"; break;
            case 0x00F1: send_y_commit_n = false; ksek = "[]"; break;
            case 0x0471: send_y_commit_n = false; ksek = "T"; break;

            case 0x006C: kk = KeyEvent.KEYCODE_SEMICOLON; break;
            case 0x806C: send_y_commit_n = false; ksek = ":"; break;
            case 0x00EC: kk = KeyEvent.KEYCODE_SEMICOLON; break;
            case 0x046C: send_y_commit_n = false; ksek = ":"; break;

            case 0x006A: send_y_commit_n = false; ksek = "V"; break;
            case 0x806A: send_y_commit_n = false; ksek = "~"; break;
            case 0x00EA: send_y_commit_n = false; ksek = "~"; break;
            case 0x046A: kk = KeyEvent.KEYCODE_V; break;

            case 0x0069: kk = KeyEvent.KEYCODE_SEMICOLON; break;
            case 0x8069: send_y_commit_n = false; ksek = "<>"; break;
            case 0x00E9: send_y_commit_n = false; ksek = "<>"; break;
            case 0x0469: send_y_commit_n = false; ksek = "<>"; break;

            case 0x0066: kk = KeyEvent.KEYCODE_EQUALS; break;
            case 0x8066: kk = KeyEvent.KEYCODE_EQUALS; break;
            // case 0x00E6: kk = KeyEvent.KEYCODE_EQUALS; break;

           	// case 0x00E5: isl88_up_pending = true; break;
            case 0x0065: kk = KeyEvent.KEYCODE_APOSTROPHE; break;
            case 0x8065: kk = KeyEvent.KEYCODE_SEMICOLON; break;
            // case 0x00E5: kk = KeyEvent.KEYCODE_APOSTROPHE; break;

            case 0x0063: kk = KeyEvent.KEYCODE_M; break;
            case 0x8063: send_y_commit_n = false; ksek = "~"; break;
            case 0x00E3: send_y_commit_n = false; ksek = "^"; break;
            case 0x0463: send_y_commit_n = false; ksek = "M"; break;

            case 0x005C: kk = KeyEvent.KEYCODE_COMMA; break;
            case 0x805C: kk = KeyEvent.KEYCODE_SEMICOLON; break;
            case 0x00DC: kk = KeyEvent.KEYCODE_SEMICOLON; break;
            case 0x045C: kk = KeyEvent.KEYCODE_SEMICOLON; break;

            case 0x005A: send_y_commit_n = false; ksek = "?"; break;
            case 0x805A: send_y_commit_n = false; ksek = "%"; break;
            case 0x00DA: send_y_commit_n = false; ksek = "%"; break;
            case 0x045A: send_y_commit_n = false; ksek = "%"; break;

            case 0x0059: kk = KeyEvent.KEYCODE_C; break;
            case 0x8059: send_y_commit_n = false; ksek = "C"; break; // not easy_to_guess
            case 0x00D9: kk = KeyEvent.KEYCODE_X; break;
            case 0x0459: send_y_commit_n = false; ksek = "C"; break; // not easy_to_guess

            case 0x0056: kk = KeyEvent.KEYCODE_L; break;
            case 0x8056: send_y_commit_n = false; ksek = "L"; break;
            case 0x00D6: send_y_commit_n = false; ksek = "!"; break;
            case 0x0456: send_y_commit_n = false; ksek = "L"; break;

            case 0x0055: send_y_commit_n = false; ksek = "!"; break;
            case 0x8055: send_y_commit_n = false; ksek = "%"; break;
            case 0x00D5: send_y_commit_n = false; ksek = "%"; break;
            case 0x0455: send_y_commit_n = false; ksek = "%"; break;

            case 0x0053: kk = KeyEvent.KEYCODE_APOSTROPHE; break;
            case 0x8053: send_y_commit_n = false; ksek = "{}"; break;
            case 0x00D3: send_y_commit_n = false; ksek = "{}"; break;
            case 0x0453: send_y_commit_n = false; ksek = "()"; break;

            case 0x004E: kk = KeyEvent.KEYCODE_EQUALS; break;
            case 0x00CE: send_y_commit_n = false; ksek = "%"; break;
            case 0x804E: send_y_commit_n = false; ksek = "%"; break;
            case 0x044E: send_y_commit_n = false; ksek = "!"; break;

            case 0x004D: kk = KeyEvent.KEYCODE_BACKSLASH; break;
            case 0x00CD: send_y_commit_n = false; ksek = "%"; break;
            case 0x804D: send_y_commit_n = false; ksek = "%"; break;
            case 0x044D: send_y_commit_n = false; ksek = "!"; break;

            case 0x004B: kk = KeyEvent.KEYCODE_W; break;
            case 0x00CB: send_y_commit_n = false; ksek = "^"; break;
            case 0x804B: send_y_commit_n = false; ksek = "~"; break;
            case 0x044B: send_y_commit_n = false; ksek = "W"; break;

            case 0x0047: kk = KeyEvent.KEYCODE_GRAVE; break;
            case 0x00C7: send_y_commit_n = false; ksek = "{}"; break;
            case 0x8047: send_y_commit_n = false; ksek = "[]"; break;
            case 0x0447: send_y_commit_n = false; ksek = "{}"; break;

            case 0x003C: kk = KeyEvent.KEYCODE_PERIOD; break;
            case 0x00BC: send_y_commit_n = false; ksek = ":"; break;
            case 0x803C: kk = KeyEvent.KEYCODE_PERIOD; break;
            case 0x043C: send_y_commit_n = false; ksek = ":"; break;

            case 0x003A: send_y_commit_n = false; ksek = "?"; break;
//            case 0x00BA: kk = KeyEvent.KEYCODE_SEMICOLON; break;
//            case 0x803A: kk = KeyEvent.KEYCODE_SEMICOLON; break;
//            case 0x043A: kk = KeyEvent.KEYCODE_SEMICOLON; break;

            case 0x0039: kk = KeyEvent.KEYCODE_SEMICOLON; break;
            // case 0x00B9: kk = KeyEvent.KEYCODE_SLASH; break;
            case 0x8039: send_y_commit_n = false; ksek = "C"; break;
            case 0x0439: send_y_commit_n = false; ksek = "C" ; break;

            case 0x0036: send_y_commit_n = false; ksek = "{}"; break;
            case 0x00B6: kk = KeyEvent.KEYCODE_SEMICOLON; break;
            case 0x8036: kk = KeyEvent.KEYCODE_SEMICOLON; break;
            case 0x0436: kk = KeyEvent.KEYCODE_SEMICOLON; break;

            case 0x0035: send_y_commit_n = false; ksek = "^"; break;
            case 0x00B5: kk = KeyEvent.KEYCODE_W; break;
            case 0x8035: send_y_commit_n = false; ksek = "|"; break;
            case 0x0435: kk = KeyEvent.KEYCODE_W; break;

            case 0x0033: kk = KeyEvent.KEYCODE_GRAVE; break;
            case 0x00B3: send_y_commit_n = false; ksek = "[]"; break;
            case 0x8033: send_y_commit_n = false; ksek = "[]"; break;
            case 0x0433: send_y_commit_n = false; ksek = "[]" ; break;

            case 0x002E: kk = KeyEvent.KEYCODE_L; break;
            case 0x00AE: send_y_commit_n = false; ksek = "L"; break;
            case 0x802E: send_y_commit_n = false; ksek = "L"; break;
            case 0x042E: send_y_commit_n = false; ksek = "L"; break;

            case 0x002D: kk = KeyEvent.KEYCODE_SEMICOLON; break;
            case 0x00AD: send_y_commit_n = false; ksek = "!"; break;
            case 0x802D: send_y_commit_n = false; ksek = "G"; break;
            case 0x042D: send_y_commit_n = false; ksek = "G" ; break;

            case 0x002B: kk = KeyEvent.KEYCODE_SEMICOLON; break;
            case 0x00AB: send_y_commit_n = false; ksek = "{}"; break;
            case 0x802B: send_y_commit_n = false; ksek = "G"; break;
            case 0x042B: send_y_commit_n = false; ksek = "G" ; break;

            case 0x0027: kk = KeyEvent.KEYCODE_R; break;
            case 0x00A7: send_y_commit_n = false; ksek = "~"; break;
            case 0x8027: kk = KeyEvent.KEYCODE_R; break;
            case 0x0427: kk = KeyEvent.KEYCODE_R; break;

            case 0x001E: send_y_commit_n = false; ksek = "<>"; break;
            case 0x009E: send_y_commit_n = false; ksek = "()"; break;
            case 0x801E: send_y_commit_n = false; ksek = "()"; break;
            case 0x041E: send_y_commit_n = false; ksek = "()" ; break;

            case 0x001D: kk = KeyEvent.KEYCODE_N; break;
            case 0x009D: send_y_commit_n = false; ksek = "*"; break;
            case 0x801D: send_y_commit_n = false; ksek = "N"; break;
            case 0x041D: send_y_commit_n = false; ksek = "N"; break;

            case 0x001B: kk = KeyEvent.KEYCODE_SLASH; break;
            case 0x009B: send_y_commit_n = false; ksek = "%"; break;
            case 0x801B: send_y_commit_n = false; ksek = "%"; break;
            case 0x041B: send_y_commit_n = false; ksek = "%"; break;

            case 0x0017: send_y_commit_n = false; ksek = "?"; break;
            case 0x0097: kk = KeyEvent.KEYCODE_COMMA; break;
            case 0x8017: kk = KeyEvent.KEYCODE_COMMA; break;
            case 0x0417: kk = KeyEvent.KEYCODE_COMMA; break;

            case 0x000F: kk = KeyEvent.KEYCODE_K; break;
            case 0x008F: kk = KeyEvent.KEYCODE_X; break;
            case 0x800F: send_y_commit_n = false; ksek = "K"; break;
            case 0x040F: send_y_commit_n = false; ksek = "K"; break;


            // ******************************************************* //
            // send_06  k2p = 4 keys from 0-6 bilo + sft/dot/num
            // ******************************************************* //

            case 0x0070: send_y_commit_n = false; ksek = "[]"; break;
            case 0x00F0: send_y_commit_n = false; ksek = "()"; break;
            case 0x8070: send_y_commit_n = false; ksek = "{}"; break;
            case 0x0470: send_y_commit_n = false; ksek = "{}"; break;

	        // case 0x8068: isl88_up_pending = true; break;	
            // case 0x00E8: kk = KeyEvent.KEYCODE_SEMICOLON; break;
	        // case 0x0468: isl88_up_pending = true; break;
            case 0x0068: kk = KeyEvent.KEYCODE_SEMICOLON; break;

            case 0x0064: send_y_commit_n = false; ksek = "{}"; break;
            case 0x00E4: send_y_commit_n = false; ksek = "[]"; break;
	        // case 0x8064: isl88_up_pending = true; break;	
	        // case 0x0464: isl88_up_pending = true; break;

            case 0x0062: send_y_commit_n = false; ksek = "M"; break;
            // case 0x00E2: send_y_commit_n = false; ksek = "*"; break;
            case 0x8062: send_y_commit_n = false; ksek = "Q"; break;
	        // case 0x0462: isl88_up_pending = true; break;

            case 0x0061: send_y_commit_n = false; ksek = "()"; break;
            // case 0x00E1: kk = KeyEvent.KEYCODE_COMMA; break;
	        // case 0x8061: isl88_up_pending = true; break;	
	        // case 0x0461: isl88_up_pending = true; break;

            case 0x0058: kk = KeyEvent.KEYCODE_APOSTROPHE; break;
            // case 0x00D8: kk = KeyEvent.KEYCODE_APOSTROPHE; break;
	        // case 0x8058: isl88_up_pending = true; break;	
	        // case 0x0458: isl88_up_pending = true; break;

            case 0x0054: kk = KeyEvent.KEYCODE_L; break;
            // case 0x00D4: send_y_commit_n = false; ksek = "!"; break;
            case 0x8054: send_y_commit_n = false; ksek = "L"; break;
            case 0x0454: send_y_commit_n = false; ksek = "L"; break;

            case 0x0052: send_y_commit_n = false; ksek = "?"; break;
            // case 0x00D2: kk = KeyEvent.KEYCODE_SLASH; break;
	        // case 0x8052: isl88_up_pending = true; break;
            case 0x0452: send_y_commit_n = false; ksek = "?"; break;

            case 0x0051: send_y_commit_n = false; ksek = "|"; break;
            // case 0x00D1: send_y_commit_n = false; ksek = "|"; break;
	        // case 0x8051: isl88_up_pending = true; break;	
	        // case 0x0451: isl88_up_pending = true; break;

            case 0x004C: send_y_commit_n = false; ksek = "!"; break;
            // case 0x00CC: send_y_commit_n = false; ksek = "%"; break;
	        // case 0x804C: isl88_up_pending = true; break;	
	        // case 0x044C: isl88_up_pending = true; break;

            case 0x004A: send_y_commit_n = false; ksek = "W"; break;
            // case 0x00CA: send_y_commit_n = false; ksek = "U"; break;
            // case 0x804A: isl88_up_pending = true; break;	
            // case 0x044A: isl88_up_pending = true; break;

            case 0x0049: send_y_commit_n = false; ksek = "%"; break;
            // case 0x00C9: send_y_commit_n = false; ksek = "%"; break;
            // case 0x8049: isl88_up_pending = true; break;	
            // case 0x0449: isl88_up_pending = true; break;

            case 0x0046: send_y_commit_n = false; ksek = "?"; break;
            // case 0x00C6: send_y_commit_n = false; ksek = "%"; break; // k2p=5
            // case 0x8046: isl88_up_pending = true; break;	
            // case 0x0446: isl88_up_pending = true; break;

            case 0x0045: send_y_commit_n = false; ksek = "%"; break;
            case 0x00C5: kk = KeyEvent.KEYCODE_PERIOD; break;
            // case 0x8045: isl88_up_pending = true; break;	
            // case 0x0445: isl88_up_pending = true; break;

            case 0x0043: send_y_commit_n = false; ksek = "*"; break;
            case 0x00C3: kk = KeyEvent.KEYCODE_PERIOD; break;
            // case 0x8043: isl88_up_pending = true; break;	
            // case 0x0443: isl88_up_pending = true; break;

            case 0x0038: send_y_commit_n = false; ksek = "()"; break;
            // case 0x00B8: send_y_commit_n = false; ksek = "()"; break;
            // case 0x8038: isl88_up_pending = true; break;	
            // case 0x0438: isl88_up_pending = true; break;

            case 0x0034: send_y_commit_n = false; ksek = ":"; break;
            // case 0x00B4: send_y_commit_n = false; ksek = ":"; break;
            // case 0x8034: isl88_up_pending = true; break;	
            // case 0x0434: isl88_up_pending = true; break;

            case 0x0032: send_y_commit_n = false; ksek = "{}"; break;
            case 0x00B2: send_y_commit_n = false; ksek = "[]"; break;
            // case 0x8032: isl88_up_pending = true; break;
           	// case 0x0432: isl88_up_pending = true; break;

            case 0x0031: kk = KeyEvent.KEYCODE_BACKSLASH; break;
            // case 0x00B1: kk = KeyEvent.KEYCODE_BACKSLASH; break;
            // case 0x8031: isl88_up_pending = true; break;
           	// case 0x0431: isl88_up_pending = true; break;

            case 0x002C: send_y_commit_n = false; ksek = "()"; break;
            // case 0x00AC: send_y_commit_n = false; ksek = "!"; break;
            // case 0x802C: isl88_up_pending = true; break;
           	// case 0x042C: isl88_up_pending = true; break;

            case 0x002A: kk = KeyEvent.KEYCODE_SEMICOLON; break;
            // case 0x00AA: kk = KeyEvent.KEYCODE_SEMICOLON; break;
            // case 0x802A: isl88_up_pending = true; break;
           	// case 0x042A: isl88_up_pending = true; break;

            case 0x0029: send_y_commit_n = false; ksek = "|"; break;
            // case 0x00A9: send_y_commit_n = false; ksek = "|"; break;
            // case 0x8029: kk = KeyEvent.KEYCODE_EQUALS; break;
            // case 0x0429: isl88_up_pending = true; break;

            case 0x0026: kk = KeyEvent.KEYCODE_C; break;
            case 0x00A6: send_y_commit_n = false; ksek = "()"; break;
            case 0x8026: send_y_commit_n = false; ksek = "<>"; break;
            case 0x0426: send_y_commit_n = false; ksek = "{}"; break;

            case 0x0025: send_y_commit_n = false; ksek = "()"; break;
            // case 0x00A5: send_y_commit_n = false; ksek = "()"; break;
            // case 0x8025: isl88_up_pending = true; break;
            // case 0x0425: isl88_up_pending = true; break;

            case 0x0023: send_y_commit_n = false; ksek = "()"; break;
            // case 0x00A3: kk = KeyEvent.KEYCODE_COMMA; break;
            // case 0x8023: isl88_up_pending = true; break;
           	// case 0x0423: isl88_up_pending = true; break;

            case 0x001C: send_y_commit_n = false; ksek = "*"; break;
            // case 0x009C: kk = KeyEvent.KEYCODE_MINUS; break;
            // case 0x801C: isl88_up_pending = true; break;
            // case 0x041C: isl88_up_pending = true; break;

            case 0x001A: send_y_commit_n = false; ksek = "!"; break;
            // case 0x009A: send_y_commit_n = false; ksek = "%"; break;
            // case 0x801A: isl88_up_pending = true; break;
            // case 0x041A: isl88_up_pending = true; break;

            case 0x0019: send_y_commit_n = false; ksek = "!"; break;
            // case 0x0099: send_y_commit_n = false; ksek = "%"; break;
            // case 0x8019: isl88_up_pending = true; break;
            // case 0x0419: isl88_up_pending = true; break;

            case 0x0016: send_y_commit_n = false; ksek = "!"; break;
            // case 0x0096: send_y_commit_n = false; ksek = "!"; break;
            // case 0x8016: isl88_up_pending = true; break;
            case 0x0416: kk = KeyEvent.KEYCODE_RIGHT_BRACKET; break;

            case 0x0015: send_y_commit_n = false; ksek = "N"; break;
            // case 0x0095: send_y_commit_n = false; ksek = "*"; break;
            // case 0x8015: isl88_up_pending = true; break;
            // case 0x0415: isl88_up_pending = true; break;

            case 0x0013: send_y_commit_n = false; ksek = "%"; break;
            // case 0x0093: send_y_commit_n = false; ksek = "%"; break;
            // case 0x8013: isl88_up_pending = true; break;
            // case 0x0413: isl88_up_pending = true; break;

            case 0x000E: kk = KeyEvent.KEYCODE_T; break;
            // case 0x008E: kk = KeyEvent.KEYCODE_T; break;
            // case 0x800E: isl88_up_pending = true; break;
            // case 0x040E: isl88_up_pending = true; break;


            case 0x000D: send_y_commit_n = false; ksek = "!"; break;
            // case 0x008D: send_y_commit_n = false; ksek = "%"; break;
            // case 0x800D: isl88_up_pending = true; break;
            // case 0x040D: isl88_up_pending = true; break;

            case 0x000B: send_y_commit_n = false; ksek = "%"; break;
            // case 0x008B: send_y_commit_n = false; ksek = "%"; break;
            // case 0x800B: isl88_up_pending = true; break;
            // case 0x040B: isl88_up_pending = true; break;

            case 0x0007: kk = KeyEvent.KEYCODE_F; break;
            // case 0x0087: kk = KeyEvent.KEYCODE_F; break;
            // case 0x8007: isl88_up_pending = true; break;
            case 0x0407: send_y_commit_n = false; ksek = "?"; break;

            default: isl88_up_pending = true; break;
        }
        if(!isl88_up_pending) send_kk();
    }
    public void send_06_nsd23(){
        isl88_up_pending = false ; meta = 0 ; kk = KeyEvent.KEYCODE_UNKNOWN ; ksek = null; send_y_commit_n = true;
        int num88bytes =  l88bytes;
        if ( mKeyboardSwitcher.is_nm_lok() && ((num88bytes & 0x0480) == 0)) {num88bytes =  num88bytes ^ 0x8000 ; }
        if (mKeyboardSwitcher.is_sft_lok() && ((num88bytes & 0x8080) == 0)) {num88bytes =  num88bytes ^ 0x0400 ; }
        if (mKeyboardSwitcher.is_go_lok() && ((num88bytes & 0x0200) == 0)) {num88bytes =  num88bytes ^ 0x0100 ; }
        switch (num88bytes) {
            // ******************************************************* //
            // send_06_nsd23  k2p = 0 keys from 0-6 bilo + sft/dot/num
            // ******************************************************* //

            case 0x04FF:  send_y_commit_n = false; ksek = "()"; break;
            // case 0x80FF: isl88_up_pending = true; break;
            // case 0x847F: isl88_up_pending = true; break;
            // case 0x84FF: isl88_up_pending = true; break;

            // ******************************************************* //
            // send_06_nsd23  k2p = 1 keys from 0-6 bilo + sft/dot/num
            // ******************************************************* //

            // case 0x04FE: isl88_up_pending = true; break;
            // case 0x80FE: isl88_up_pending = true; break;
            // case 0x847E: isl88_up_pending = true; break;
            // case 0x84FE: isl88_up_pending = true; break;
//
            // case 0x04FD: isl88_up_pending = true; break;
            // case 0x80FD: isl88_up_pending = true; break;
            // case 0x847D: isl88_up_pending = true; break;
            // case 0x84FD: isl88_up_pending = true; break;
//
            // case 0x04FB: isl88_up_pending = true; break;
            // case 0x80FB: isl88_up_pending = true; break;
            // case 0x847B: isl88_up_pending = true; break;
            // case 0x84FB: isl88_up_pending = true; break;

            // case 0x04F7: isl88_up_pending = true; break;
            // case 0x80F7: isl88_up_pending = true; break;
            // case 0x8477: isl88_up_pending = true; break;
            // case 0x84F7: isl88_up_pending = true; break;

            case 0x04EF: send_y_commit_n = false; ksek = "`"; break;
            // case 0x80EF: isl88_up_pending = true; break;
            // case 0x846F: isl88_up_pending = true; break;
            // case 0x84EF: isl88_up_pending = true; break;
//
            // case 0x04DF: isl88_up_pending = true; break;
            // case 0x80DF: isl88_up_pending = true; break;
            // case 0x845F: isl88_up_pending = true; break;
            // case 0x84DF: isl88_up_pending = true; break;

            case 0x04BF: send_y_commit_n = false; ksek = "C" ; break;
            // case 0x80BF: isl88_up_pending = true; break;
            // case 0x843F: isl88_up_pending = true; break;
            // case 0x84BF: isl88_up_pending = true; break;



            // ******************************************************* //
            // send_06_nsd23  k2p = 2 keys from 0-6 bilo + sft/dot/num
            // ******************************************************* //
            // case 0x04FC: isl88_up_pending = true; break;
            // case 0x80FC: isl88_up_pending = true; break;
            // case 0x847C: isl88_up_pending = true; break;
            // case 0x84FC: isl88_up_pending = true; break;
//
            // case 0x04FA: isl88_up_pending = true; break;
            // case 0x80FA: isl88_up_pending = true; break;
            // case 0x847A: isl88_up_pending = true; break;
            // case 0x84FA: isl88_up_pending = true; break;
//
            // case 0x04F9: isl88_up_pending = true; break;
            // case 0x80F9: isl88_up_pending = true; break;
            // case 0x8479: isl88_up_pending = true; break;
            // case 0x84F9: isl88_up_pending = true; break;

            case 0x04F6: send_y_commit_n = false; ksek = "#"; break;
            // case 0x80F6: isl88_up_pending = true; break;
            // case 0x8476: isl88_up_pending = true; break;
            // case 0x84F6: isl88_up_pending = true; break;
//
            // case 0x04F5: isl88_up_pending = true; break;
            // case 0x80F5: isl88_up_pending = true; break;
            // case 0x8475: isl88_up_pending = true; break;
            // case 0x84F5: isl88_up_pending = true; break;
//
            // case 0x04F3: isl88_up_pending = true; break;
            // case 0x80F3: isl88_up_pending = true; break;
            // case 0x8473: isl88_up_pending = true; break;
            // case 0x84F3: isl88_up_pending = true; break;
//
            // case 0x04EE: isl88_up_pending = true; break;
            // case 0x80EE: isl88_up_pending = true; break;
            // case 0x846E: isl88_up_pending = true; break;
            // case 0x84EE: isl88_up_pending = true; break;

            // case 0x04ED: isl88_up_pending = true; break;
            // case 0x80ED: isl88_up_pending = true; break;
            // case 0x846D: isl88_up_pending = true; break;
            // case 0x84ED: isl88_up_pending = true; break;
//
            // case 0x04EB: isl88_up_pending = true; break;
            // case 0x80EB: isl88_up_pending = true; break;
            // case 0x846B: isl88_up_pending = true; break;
            // case 0x84EB: isl88_up_pending = true; break;
//
            // case 0x04E7: isl88_up_pending = true; break;
            // case 0x80E7: isl88_up_pending = true; break;
            case 0x8467: send_y_commit_n = false; ksek = "~"; break;
            // case 0x84E7: isl88_up_pending = true; break;

            // case 0x04DE: isl88_up_pending = true; break;
            // case 0x80DE: isl88_up_pending = true; break;
            // case 0x845E: isl88_up_pending = true; break;
            // case 0x84DE: isl88_up_pending = true; break;
//
            // case 0x04DD: isl88_up_pending = true; break;
            // case 0x80DD: isl88_up_pending = true; break;
            // case 0x845D: isl88_up_pending = true; break;
            // case 0x84DD: isl88_up_pending = true; break;
//
            // case 0x04DB: isl88_up_pending = true; break;
            // case 0x80DB: isl88_up_pending = true; break;
            // case 0x845B: isl88_up_pending = true; break;
            // case 0x84DB: isl88_up_pending = true; break;
//
            // case 0x04D7: isl88_up_pending = true; break;
            // case 0x80D7: isl88_up_pending = true; break;
            // case 0x8457: isl88_up_pending = true; break;
            // case 0x84D7: isl88_up_pending = true; break;

            case 0x04CF: send_y_commit_n = false; ksek = "Z";
            // case 0x80CF: isl88_up_pending = true; break;
            // case 0x844F: isl88_up_pending = true; break;
            // case 0x84CF: isl88_up_pending = true; break;

            // case 0x04BE: isl88_up_pending = true; break;
            // case 0x80BE: isl88_up_pending = true; break;
            // case 0x843E: isl88_up_pending = true; break;
            // case 0x84BE: isl88_up_pending = true; break;
//
            // case 0x04BD: isl88_up_pending = true; break;
            // case 0x80BD: isl88_up_pending = true; break;
            // case 0x843D: isl88_up_pending = true; break;
            // case 0x84BD: isl88_up_pending = true; break;
//
            // case 0x04BB: isl88_up_pending = true; break;
            // case 0x80BB: isl88_up_pending = true; break;
            // case 0x843B: isl88_up_pending = true; break;
            // case 0x84BB: isl88_up_pending = true; break;

            // case 0x04B7: isl88_up_pending = true; break;
            // case 0x80B7: isl88_up_pending = true; break;
            // case 0x8437: isl88_up_pending = true; break;
            // case 0x84B7: isl88_up_pending = true; break;
//
            // case 0x04AF: isl88_up_pending = true; break;
            // case 0x80AF: isl88_up_pending = true; break;
            // case 0x842F: isl88_up_pending = true; break;
            // case 0x84AF: isl88_up_pending = true; break;
//
            // case 0x049F: isl88_up_pending = true; break;
            // case 0x809F: isl88_up_pending = true; break;
            // case 0x841F: isl88_up_pending = true; break;
            // case 0x849F: isl88_up_pending = true; break;


            // ******************************************************* //
            // send_06_nsd23  k2p = 3 keys from 0-6 bilo + sft/dot/num
            // ******************************************************* //

            // ******************************************************* //
            // send_06_nsd23  k2p = 4 keys from 0-6 bilo + sft/dot/num
            // ******************************************************* //

            // // //
            default: isl88_up_pending = true ; break ;
        }
        if(!isl88_up_pending) send_kk();
    }

    // /// 8E sending bilo
    public void send_8E(){
        isl88_up_pending = false ; meta = 0 ; kk = KeyEvent.KEYCODE_UNKNOWN ; ksek = null; send_y_commit_n = true;
        int num88bytes =  l88bytes;
//        if (mKeyboardSwitcher.is_sft_lok() && ((num88bytes & 0x0080) == 0)) // if yllo key is also pressed ignore sft mode/on/key
//            { num88bytes =  num88bytes ^ 0x0400 ; }
        switch (num88bytes) {
            // ******************************************************* //
            // send_8E k2p = 1 keys from 8-X bilo and dot
            // ******************************************************* //
            case 0x01FF: send_y_commit_n = false; ksek = "?"; break;
            case 0x02FF: send_y_commit_n = false; ksek = "+"; break;
            case 0x04FF: send_y_commit_n = false; ksek = "()"; break;
            case 0x08FF: send_y_commit_n = false; ksek = "[]"; break;
            case 0x10FF: send_y_commit_n = false; ksek = "*"; break;
            case 0x20FF: send_y_commit_n = false; ksek = "{}"; break;
            case 0x40FF: send_y_commit_n = false; ksek = "|"; break;
            // ******************************************************* //
            // send_8E k2p = yllo + 1 = 2 keys from 0-6 bilo + sft/dot/num
            // ******************************************************* //
            case 0x037F: kk = KeyEvent.KEYCODE_COMMA; break;
           	case 0x03FF: send_y_commit_n = false; ksek = "_"; break;
            // case 0x837F: isl88_up_pending = true; break;
           	// case 0x83FF: isl88_up_pending = true; break;

            // case 0x067F: isl88_up_pending = true; break;
           	case 0x06FF: kk = KeyEvent.KEYCODE_L; break;
            // case 0x867F: isl88_up_pending = true; break;
           	// case 0x86FF: isl88_up_pending = true; break;

            case 0x097F: send_y_commit_n = false; ksek = ":" ; break;
           	case 0x09FF: kk = KeyEvent.KEYCODE_EQUALS; break;
            // case 0x897F: isl88_up_pending = true; break;
           	// case 0x89FF: isl88_up_pending = true; break;

            // case 0x0A7F: isl88_up_pending = true; break;
           	case 0x0AFF: send_y_commit_n = false; ksek = "~"; break;
            // case 0x8A7F: isl88_up_pending = true; break;
           	// case 0x8AFF: isl88_up_pending = true; break;

            case 0x0C7F: kk = KeyEvent.KEYCODE_GRAVE; break;
           	// case 0x0CFF: isl88_up_pending = true; break;
            // case 0x8C7F: isl88_up_pending = true; break;
           	// case 0x8CFF: isl88_up_pending = true; break;

            // case 0x117F: isl88_up_pending = true; break;
           	case 0x11FF: send_y_commit_n = false; ksek = "#"; break;
            // case 0x917F: isl88_up_pending = true; break;
           	// case 0x91FF: isl88_up_pending = true; break;

            // case 0x127F: isl88_up_pending = true; break;
           	case 0x12FF: send_y_commit_n = false; ksek = "%"; break;
            // case 0x927F: isl88_up_pending = true; break;
           	// case 0x92FF: isl88_up_pending = true; break;

            case 0x187F: kk = KeyEvent.KEYCODE_APOSTROPHE; break;
           	case 0x18FF: kk = KeyEvent.KEYCODE_SEMICOLON; break;
            // case 0x987F: isl88_up_pending = true; break;
           	// case 0x98FF: isl88_up_pending = true; break;

            case 0x217F: send_y_commit_n = false; ksek = "*" ; break;
           	case 0x21FF: send_y_commit_n = false; ksek = "Z"; break;
            // case 0xA17F: isl88_up_pending = true; break;
           	// case 0xA1FF: isl88_up_pending = true; break;

            // case 0x227F: isl88_up_pending = true; break;
           	// case 0x22FF: isl88_up_pending = true; break;
            // case 0xA27F: isl88_up_pending = true; break;
           	// case 0xA2FF: isl88_up_pending = true; break;

            // case 0x247F: isl88_up_pending = true; break;
           	// case 0x24FF: isl88_up_pending = true; break;
            // case 0xA47F: isl88_up_pending = true; break;
           	// case 0xA4FF: isl88_up_pending = true; break;

            case 0x287F: kk = KeyEvent.KEYCODE_EQUALS; break;
           	// case 0x28FF: isl88_up_pending = true; break;
            // case 0xA87F: isl88_up_pending = true; break;
           	// case 0xA8FF: isl88_up_pending = true; break;

            // case 0x307F: isl88_up_pending = true; break;
           	// case 0x30FF: isl88_up_pending = true; break;
            // case 0xB07F: isl88_up_pending = true; break;
           	// case 0xB0FF: isl88_up_pending = true; break;

            // case 0x417F: isl88_up_pending = true; break;
           	// case 0x41FF: isl88_up_pending = true; break;
            // case 0xC17F: isl88_up_pending = true; break;
           	// case 0xC1FF: isl88_up_pending = true; break;

            // case 0x427F: isl88_up_pending = true; break;
           	// case 0x42FF: isl88_up_pending = true; break;
            // case 0xC27F: isl88_up_pending = true; break;
           	// case 0xC2FF: isl88_up_pending = true; break;



            // case 0x487F: isl88_up_pending = true; break;
           	// case 0x48FF: isl88_up_pending = true; break;
            // case 0xC87F: isl88_up_pending = true; break;
           	// case 0xC8FF: isl88_up_pending = true; break;

            // case 0x507F: isl88_up_pending = true; break;
           	// case 0x50FF: isl88_up_pending = true; break;
            // case 0xD07F: isl88_up_pending = true; break;
           	// case 0xD0FF: isl88_up_pending = true; break;

            case 0x607F: kk = KeyEvent.KEYCODE_R; break;
           	// case 0x60FF: isl88_up_pending = true; break;
            // case 0xE07F: isl88_up_pending = true; break;
           	// case 0xE0FF: isl88_up_pending = true; break;

            // send_8E : k2p=3
            case 0x077F: kk = KeyEvent.KEYCODE_SEMICOLON; break;
           	// case 0x07FF: isl88_up_pending = true; break;
            // case 0x877F: isl88_up_pending = true; break;
           	// case 0x87FF: isl88_up_pending = true; break;

            case 0x0B7F: kk = KeyEvent.KEYCODE_SEMICOLON; break;
           	// case 0x0BFF: isl88_up_pending = true; break;
            // case 0x8B7F: isl88_up_pending = true; break;
           	// case 0x8BFF: isl88_up_pending = true; break;

            // case 0x0D7F: isl88_up_pending = true; break;
           	// case 0x0DFF: isl88_up_pending = true; break;
            // case 0x8D7F: isl88_up_pending = true; break;
           	// case 0x8DFF: isl88_up_pending = true; break;

            // case 0x0E7F: isl88_up_pending = true; break;
           	// case 0x0EFF: isl88_up_pending = true; break;
            // case 0x8E7F: isl88_up_pending = true; break;
           	// case 0x8EFF: isl88_up_pending = true; break;

            // case 0x137F: isl88_up_pending = true; break;
           	// case 0x13FF: isl88_up_pending = true; break;
            // case 0x937F: isl88_up_pending = true; break;
           	// case 0x93FF: isl88_up_pending = true; break;

            // case 0x157F: isl88_up_pending = true; break;
           	// case 0x15FF: isl88_up_pending = true; break;
            // case 0x957F: isl88_up_pending = true; break;
           	// case 0x95FF: isl88_up_pending = true; break;

            // case 0x167F: isl88_up_pending = true; break;
           	// case 0x16FF: isl88_up_pending = true; break;
            // case 0x967F: isl88_up_pending = true; break;
           	// case 0x96FF: isl88_up_pending = true; break;

            // case 0x197F: isl88_up_pending = true; break;
           	// case 0x19FF: isl88_up_pending = true; break;
            // case 0x997F: isl88_up_pending = true; break;
           	// case 0x99FF: isl88_up_pending = true; break;

            // case 0x1A7F: isl88_up_pending = true; break;
           	// case 0x1AFF: isl88_up_pending = true; break;
            // case 0x9A7F: isl88_up_pending = true; break;
           	// case 0x9AFF: isl88_up_pending = true; break;

            case 0x1C7F: send_y_commit_n = false; ksek = "^" ; break;
           	// case 0x1CFF: isl88_up_pending = true; break;
            // case 0x9C7F: isl88_up_pending = true; break;
           	// case 0x9CFF: isl88_up_pending = true; break;

            case 0x237F: kk = KeyEvent.KEYCODE_COMMA; break;
           	// case 0x23FF: isl88_up_pending = true; break;
            // case 0xA37F: isl88_up_pending = true; break;
           	// case 0xA3FF: isl88_up_pending = true; break;

            // case 0x257F: isl88_up_pending = true; break;
           	// case 0x25FF: isl88_up_pending = true; break;
            // case 0xA57F: isl88_up_pending = true; break;
           	// case 0xA5FF: isl88_up_pending = true; break;

            case 0x267F: kk = KeyEvent.KEYCODE_LEFT_BRACKET; break;
           	// case 0x26FF: isl88_up_pending = true; break;
            // case 0xA67F: isl88_up_pending = true; break;
           	// case 0xA6FF: isl88_up_pending = true; break;

            case 0x297F: send_y_commit_n = false; ksek = "|" ; break;
           	// case 0x29FF: isl88_up_pending = true; break;
            // case 0xA97F: isl88_up_pending = true; break;
           	// case 0xA9FF: isl88_up_pending = true; break;

            // case 0x2A7F: isl88_up_pending = true; break;
           	// case 0x2AFF: isl88_up_pending = true; break;
            // case 0xAA7F: isl88_up_pending = true; break;
           	// case 0xAAFF: isl88_up_pending = true; break;

            case 0x2C7F: kk = KeyEvent.KEYCODE_RIGHT_BRACKET; break;
           	// case 0x2CFF: isl88_up_pending = true; break;
            // case 0xAC7F: isl88_up_pending = true; break;
           	// case 0xACFF: isl88_up_pending = true; break;

            // case 0x317F: isl88_up_pending = true; break;
           	// case 0x31FF: isl88_up_pending = true; break;
            // case 0xB17F: isl88_up_pending = true; break;
           	// case 0xB1FF: isl88_up_pending = true; break;

            case 0x327F: kk = KeyEvent.KEYCODE_BACKSLASH; break;
           	// case 0x32FF: isl88_up_pending = true; break;
            // case 0xB27F: isl88_up_pending = true; break;
           	// case 0xB2FF: isl88_up_pending = true; break;

            case 0x347F: send_y_commit_n = false; ksek = "~" ; break;
           	// case 0x34FF: isl88_up_pending = true; break;
            // case 0xB47F: isl88_up_pending = true; break;
           	// case 0xB4FF: isl88_up_pending = true; break;

            case 0x387F: kk = KeyEvent.KEYCODE_LEFT_BRACKET; break;
           	// case 0x38FF: isl88_up_pending = true; break;
            // case 0xB87F: isl88_up_pending = true; break;
           	// case 0xB8FF: isl88_up_pending = true; break;

            // case 0x437F: isl88_up_pending = true; break;
           	// case 0x43FF: isl88_up_pending = true; break;
            // case 0xC37F: isl88_up_pending = true; break;
           	// case 0xC3FF: isl88_up_pending = true; break;

            // case 0x457F: isl88_up_pending = true; break;
           	// case 0x45FF: isl88_up_pending = true; break;
            // case 0xC57F: isl88_up_pending = true; break;
           	// case 0xC5FF: isl88_up_pending = true; break;

            // case 0x467F: isl88_up_pending = true; break;
           	// case 0x46FF: isl88_up_pending = true; break;
            // case 0xC67F: isl88_up_pending = true; break;
           	// case 0xC6FF: isl88_up_pending = true; break;

            // case 0x497F: isl88_up_pending = true; break;
           	// case 0x49FF: isl88_up_pending = true; break;
            // case 0xC97F: isl88_up_pending = true; break;
           	// case 0xC9FF: isl88_up_pending = true; break;

            // case 0x4A7F: isl88_up_pending = true; break;
           	// case 0x4AFF: isl88_up_pending = true; break;
            // case 0xCA7F: isl88_up_pending = true; break;
           	// case 0xCAFF: isl88_up_pending = true; break;

            // case 0x4C7F: isl88_up_pending = true; break;
           	// case 0x4CFF: isl88_up_pending = true; break;
            // case 0xCC7F: isl88_up_pending = true; break;
           	// case 0xCCFF: isl88_up_pending = true; break;

            case 0x517F: kk = KeyEvent.KEYCODE_L; break;
           	// case 0x51FF: isl88_up_pending = true; break;
            // case 0xD17F: isl88_up_pending = true; break;
           	// case 0xD1FF: isl88_up_pending = true; break;

            // case 0x527F: isl88_up_pending = true; break;
           	// case 0x52FF: isl88_up_pending = true; break;
            // case 0xD27F: isl88_up_pending = true; break;
           	// case 0xD2FF: isl88_up_pending = true; break;

            // case 0x547F: isl88_up_pending = true; break;
           	// case 0x54FF: isl88_up_pending = true; break;
            // case 0xD47F: isl88_up_pending = true; break;
           	// case 0xD4FF: isl88_up_pending = true; break;

            // case 0x587F: isl88_up_pending = true; break;
           	// case 0x58FF: isl88_up_pending = true; break;
            // case 0xD87F: isl88_up_pending = true; break;
           	// case 0xD8FF: isl88_up_pending = true; break;

            case 0x617F: kk = KeyEvent.KEYCODE_COMMA; break;
           	// case 0x61FF: isl88_up_pending = true; break;
            // case 0xE17F: isl88_up_pending = true; break;
           	// case 0xE1FF: isl88_up_pending = true; break;

            case 0x627F: send_y_commit_n = false; ksek = "*" ; break;
           	// case 0x62FF: isl88_up_pending = true; break;
            // case 0xE27F: isl88_up_pending = true; break;
           	// case 0xE2FF: isl88_up_pending = true; break;

            case 0x647F: kk = KeyEvent.KEYCODE_SLASH; break;
           	// case 0x64FF: isl88_up_pending = true; break;
            case 0xE47F: send_y_commit_n = false; ksek = "%" ; break;
           	// case 0xE4FF: isl88_up_pending = true; break;

            // case 0x687F: isl88_up_pending = true; break;
           	// case 0x68FF: isl88_up_pending = true; break;
            // case 0xE87F: isl88_up_pending = true; break;
           	// case 0xE8FF: isl88_up_pending = true; break;

            // case 0x707F: isl88_up_pending = true; break;
           	// case 0x70FF: isl88_up_pending = true; break;
            // case 0xF07F: isl88_up_pending = true; break;
           	// case 0xF0FF: isl88_up_pending = true; break;

            // send_8E : k2p=4
            // case 0x0F7F: isl88_up_pending = true; break;
           	// case 0x0FFF: isl88_up_pending = true; break;
            // case 0x8F7F: isl88_up_pending = true; break;
           	// case 0x8FFF: isl88_up_pending = true; break;

            // case 0x177F: isl88_up_pending = true; break;
           	// case 0x17FF: isl88_up_pending = true; break;
            // case 0x977F: isl88_up_pending = true; break;
           	// case 0x97FF: isl88_up_pending = true; break;

            // case 0x1B7F: isl88_up_pending = true; break;
           	// case 0x1BFF: isl88_up_pending = true; break;
            // case 0x9B7F: isl88_up_pending = true; break;
           	// case 0x9BFF: isl88_up_pending = true; break;

            // case 0x1D7F: isl88_up_pending = true; break;
           	// case 0x1DFF: isl88_up_pending = true; break;
            // case 0x9D7F: isl88_up_pending = true; break;
           	// case 0x9DFF: isl88_up_pending = true; break;

            // case 0x1E7F: isl88_up_pending = true; break;
           	// case 0x1EFF: isl88_up_pending = true; break;
            // case 0x9E7F: isl88_up_pending = true; break;
           	// case 0x9EFF: isl88_up_pending = true; break;

            // case 0x277F: isl88_up_pending = true; break;
           	// case 0x27FF: isl88_up_pending = true; break;
            // case 0xA77F: isl88_up_pending = true; break;
           	// case 0xA7FF: isl88_up_pending = true; break;

            // case 0x2B7F: isl88_up_pending = true; break;
           	// case 0x2BFF: isl88_up_pending = true; break;
            // case 0xAB7F: isl88_up_pending = true; break;
           	// case 0xABFF: isl88_up_pending = true; break;

            case 0x2D7F: send_y_commit_n = false; ksek = "?"; break;
           	// case 0x2DFF: isl88_up_pending = true; break;
            // case 0xAD7F: isl88_up_pending = true; break;
           	// case 0xADFF: isl88_up_pending = true; break;

            // case 0x2E7F: isl88_up_pending = true; break;
           	// case 0x2EFF: isl88_up_pending = true; break;
            // case 0xAE7F: isl88_up_pending = true; break;
           	// case 0xAEFF: isl88_up_pending = true; break;

            // case 0x337F: isl88_up_pending = true; break;
           	// case 0x33FF: isl88_up_pending = true; break;
            // case 0xB37F: isl88_up_pending = true; break;
           	// case 0xB3FF: isl88_up_pending = true; break;

            // case 0x357F: isl88_up_pending = true; break;
           	// case 0x35FF: isl88_up_pending = true; break;
            // case 0xB57F: isl88_up_pending = true; break;
           	// case 0xB5FF: isl88_up_pending = true; break;

            // case 0x367F: isl88_up_pending = true; break;
           	// case 0x36FF: isl88_up_pending = true; break;
            // case 0xB67F: isl88_up_pending = true; break;
           	// case 0xB6FF: isl88_up_pending = true; break;

            // case 0x397F: isl88_up_pending = true; break;
           	// case 0x39FF: isl88_up_pending = true; break;
            // case 0xB97F: isl88_up_pending = true; break;
           	// case 0xB9FF: isl88_up_pending = true; break;

            // case 0x3A7F: isl88_up_pending = true; break;
           	// case 0x3AFF: isl88_up_pending = true; break;
            // case 0xBA7F: isl88_up_pending = true; break;
           	// case 0xBAFF: isl88_up_pending = true; break;

            // case 0x3C7F: isl88_up_pending = true; break;
           	// case 0x3CFF: isl88_up_pending = true; break;
            // case 0xBC7F: isl88_up_pending = true; break;
           	// case 0xBCFF: isl88_up_pending = true; break;

            // case 0x477F: isl88_up_pending = true; break;
           	// case 0x47FF: isl88_up_pending = true; break;
            // case 0xC77F: isl88_up_pending = true; break;
           	// case 0xC7FF: isl88_up_pending = true; break;

            // case 0x4B7F: isl88_up_pending = true; break;
           	// case 0x4BFF: isl88_up_pending = true; break;
            // case 0xCB7F: isl88_up_pending = true; break;
           	// case 0xCBFF: isl88_up_pending = true; break;

            // case 0x4D7F: isl88_up_pending = true; break;
           	// case 0x4DFF: isl88_up_pending = true; break;
            // case 0xCD7F: isl88_up_pending = true; break;
           	// case 0xCDFF: isl88_up_pending = true; break;

            // case 0x4E7F: isl88_up_pending = true; break;
           	// case 0x4EFF: isl88_up_pending = true; break;
            // case 0xCE7F: isl88_up_pending = true; break;
           	// case 0xCEFF: isl88_up_pending = true; break;

            // case 0x537F: isl88_up_pending = true; break;
           	// case 0x53FF: isl88_up_pending = true; break;
            // case 0xD37F: isl88_up_pending = true; break;
           	// case 0xD3FF: isl88_up_pending = true; break;

            // case 0x557F: isl88_up_pending = true; break;
           	// case 0x55FF: isl88_up_pending = true; break;
            // case 0xD57F: isl88_up_pending = true; break;
           	// case 0xD5FF: isl88_up_pending = true; break;

            // case 0x567F: isl88_up_pending = true; break;
           	// case 0x56FF: isl88_up_pending = true; break;
            // case 0xD67F: isl88_up_pending = true; break;
           	// case 0xD6FF: isl88_up_pending = true; break;

            // case 0x597F: isl88_up_pending = true; break;
           	// case 0x59FF: isl88_up_pending = true; break;
            // case 0xD97F: isl88_up_pending = true; break;
           	// case 0xD9FF: isl88_up_pending = true; break;

            // case 0x5A7F: isl88_up_pending = true; break;
           	// case 0x5AFF: isl88_up_pending = true; break;
            // case 0xDA7F: isl88_up_pending = true; break;
           	// case 0xDAFF: isl88_up_pending = true; break;

            // case 0x5C7F: isl88_up_pending = true; break;
           	// case 0x5CFF: isl88_up_pending = true; break;
            // case 0xDC7F: isl88_up_pending = true; break;
           	// case 0xDCFF: isl88_up_pending = true; break;

            case 0x637F: kk = KeyEvent.KEYCODE_O; break;
           	// case 0x63FF: isl88_up_pending = true; break;
            // case 0xE37F: isl88_up_pending = true; break;
           	// case 0xE3FF: isl88_up_pending = true; break;

            // case 0x657F: isl88_up_pending = true; break;
           	// case 0x65FF: isl88_up_pending = true; break;
            // case 0xE57F: isl88_up_pending = true; break;
           	// case 0xE5FF: isl88_up_pending = true; break;

            // case 0x667F: isl88_up_pending = true; break;
           	// case 0x66FF: isl88_up_pending = true; break;
            // case 0xE67F: isl88_up_pending = true; break;
           	// case 0xE6FF: isl88_up_pending = true; break;

            case 0x697F: send_y_commit_n = false; ksek = "!" ; break;
           	// case 0x69FF: isl88_up_pending = true; break;
            // case 0xE97F: isl88_up_pending = true; break;
           	// case 0xE9FF: isl88_up_pending = true; break;

            // case 0x6A7F: isl88_up_pending = true; break;
           	// case 0x6AFF: isl88_up_pending = true; break;
            // case 0xEA7F: isl88_up_pending = true; break;
           	// case 0xEAFF: isl88_up_pending = true; break;

            // case 0x6C7F: isl88_up_pending = true; break;
           	// case 0x6CFF: isl88_up_pending = true; break;
            // case 0xEC7F: isl88_up_pending = true; break;
           	// case 0xECFF: isl88_up_pending = true; break;

            // case 0x717F: isl88_up_pending = true; break;
           	// case 0x71FF: isl88_up_pending = true; break;
            // case 0xF17F: isl88_up_pending = true; break;
           	// case 0xF1FF: isl88_up_pending = true; break;

            // case 0x727F: isl88_up_pending = true; break;
           	// case 0x72FF: isl88_up_pending = true; break;
            // case 0xF27F: isl88_up_pending = true; break;
           	// case 0xF2FF: isl88_up_pending = true; break;

            // case 0x747F: isl88_up_pending = true; break;
           	// case 0x74FF: isl88_up_pending = true; break;
            // case 0xF47F: isl88_up_pending = true; break;
           	// case 0xF4FF: isl88_up_pending = true; break;

            case 0x787F: send_y_commit_n = false; ksek = "?"; break;
           	// case 0x78FF: isl88_up_pending = true; break;
            // case 0xF87F: isl88_up_pending = true; break;
           	// case 0xF8FF: isl88_up_pending = true; break;

            default: isl88_up_pending = true ;
        }
        if(!isl88_up_pending) send_kk();
    }

    //   menu minu go muv fn
    public void send_minu(){
        isl88_up_pending = false ; meta = 0 ; kk = KeyEvent.KEYCODE_UNKNOWN ; ksek = null ; send_y_commit_n = true;
        switch (l88bytes) {
            case 0x407E: kk = KeyEvent.KEYCODE_MENU; break;
            case 0x487F: kk = KeyEvent.KEYCODE_SOFT_LEFT; break;
            case 0x607F: kk = KeyEvent.KEYCODE_SOFT_RIGHT; break;
            case 0x407D: meta = meta | KeyEvent.META_ALT_ON ; kk = KeyEvent.KEYCODE_F; break;
            case 0x407B: meta = meta | KeyEvent.META_ALT_ON ; kk = KeyEvent.KEYCODE_E; break;
            case 0x4077: meta = meta | KeyEvent.META_ALT_ON ; kk = KeyEvent.KEYCODE_V; break;
//            case 0x607F: kk = KeyEvent.KEYCODE_FORWARD_DEL; break;
            default: isl88_up_pending = true ; break ;
        }
        if(!isl88_up_pending) send_kk();
    }
    public void send_fn_on(){
        isl88_up_pending = false ; meta = KeyEvent.META_FUNCTION_ON ; kk = KeyEvent.KEYCODE_UNKNOWN ; ksek = null ; send_y_commit_n = true;
        switch (l88bytes) {
            case 0x207C: kk = KeyEvent.KEYCODE_PLUS; break;
            case 0x207D: kk = KeyEvent.KEYCODE_F1; break;
            case 0x207B: kk = KeyEvent.KEYCODE_F2; break;
            case 0x2077: kk = KeyEvent.KEYCODE_F3; break;
            case 0x206F: kk = KeyEvent.KEYCODE_F4; break;
            case 0x205F: kk = KeyEvent.KEYCODE_F5; break;
            case 0x203F: kk = KeyEvent.KEYCODE_F6; break;
            case 0xA0FF: kk = KeyEvent.KEYCODE_F7; break;
            case 0x217F: kk = KeyEvent.KEYCODE_F8; break;
            case 0x227F: kk = KeyEvent.KEYCODE_F9; break;
            case 0x247F:  kk = KeyEvent.KEYCODE_F10; break;
            // case 0x207E:  kk = KeyEvent.KEYCODE_F10; break;
            case 0x287F: kk = KeyEvent.KEYCODE_F11; break;
            case 0x307F: kk = KeyEvent.KEYCODE_F12; break;
            // case 0x207F: meta = 0 ; kk = KeyEvent.KEYCODE_FORWARD_DEL; break;
            default: isl88_up_pending = true ; break ;
        }
        if(!isl88_up_pending) send_kk();
    }
    public void send_fn_oph(){
        isl88_up_pending = false ; meta = 0 ; kk = KeyEvent.KEYCODE_UNKNOWN ; ksek = null ; send_y_commit_n = true;
        switch (l88bytes) {
            // case 0x247F: meta = KeyEvent.META_SHIFT_ON ; kk = KeyEvent.KEYCODE_FORWARD_DEL; break;
            case 0xA07D: kk = KeyEvent.KEYCODE_F1; break;
            case 0xA07B: kk = KeyEvent.KEYCODE_F2; break;
            case 0xA077: kk = KeyEvent.KEYCODE_F3; break;
            case 0xA06F: kk = KeyEvent.KEYCODE_F4; break;
            case 0xA05F: kk = KeyEvent.KEYCODE_F5; break;
            case 0xA03F: kk = KeyEvent.KEYCODE_F6; break;
            case 0xA0FF: kk = KeyEvent.KEYCODE_F7; break;
            case 0xA17F: kk = KeyEvent.KEYCODE_F8; break;
            case 0xA27F: kk = KeyEvent.KEYCODE_F9; break;
            case 0xA47F:  kk = KeyEvent.KEYCODE_F10; break;
            case 0xA07E:  kk = KeyEvent.KEYCODE_F10; break;
            case 0xA87F: kk = KeyEvent.KEYCODE_F11; break;
            case 0xB07F: kk = KeyEvent.KEYCODE_F12; break;
            default: isl88_up_pending = true ; break ;
        }
        if(!isl88_up_pending) send_kk();
    }

    //   ctl alt must1 combinations +/or sft
    public void kas_muv_go_num_fn_minu_sft(int arg_kas88bytes){
        isl88_up_pending = false ; kk = KeyEvent.KEYCODE_UNKNOWN ; ksek = null ; send_y_commit_n = true;
        int num88bytes =  arg_kas88bytes;
        if ( mKeyboardSwitcher.is_nm_lok() ) {num88bytes =  num88bytes ^ 0x8000 ; }
//        if ( mKeyboardSwitcher.is_sft_lok() ) {num88bytes =  num88bytes ^ 0x0400 ; }
        if ( mKeyboardSwitcher.is_go_lok() ) {num88bytes =  num88bytes ^ 0x0100 ; }
        if ( mKeyboardSwitcher.is_muv_lok() ) {num88bytes =  num88bytes ^ 0x0200 ; }
        switch (num88bytes) {
            case 0x007B: kk = KeyEvent.KEYCODE_C; break; // muv_go_num_fn_minu_sft // kas
            case 0x047B: kk = KeyEvent.KEYCODE_C; break; // muv_go_num_fn_minu_sft : sft // kas
            case 0x807B: kk = KeyEvent.KEYCODE_2; break;  // muv_go_num_fn_minu_sft : 123 // kas
            case 0x027B: kk = KeyEvent.KEYCODE_TAB; break; // muv_go_num_fn_minu_sft : tAb + muv // kas
            case 0x017B: kk = KeyEvent.KEYCODE_TAB; break; // muv_go_num_fn_minu_sft : tAb + go // kas
            case 0x0A7B: meta = meta | KeyEvent.META_ALT_ON ; kk = KeyEvent.KEYCODE_TAB; break; // muv_go_num_fn_minu_sft : alt + muv + tAb // kas
            case 0x097B: meta = meta | KeyEvent.META_ALT_ON ;kk = KeyEvent.KEYCODE_TAB; break; // muv_go_num_fn_minu_sft : alt + go + tAb // kas
            case 0x067B: meta = meta | KeyEvent.META_SHIFT_ON ; kk = KeyEvent.KEYCODE_TAB; break; // muv_go_num_fn_minu_sft : tAb + muv + sft // kas
            case 0x057B: meta = meta | KeyEvent.META_SHIFT_ON ; kk = KeyEvent.KEYCODE_TAB; break; // muv_go_num_fn_minu_sft : tAb + go + sft // kas
            // pnc cases
            case 0x847B: kk = KeyEvent.KEYCODE_2; break;  // muv_go_num_fn_minu_sft : sft+123 pnc // kas
            case 0x827B: kk = KeyEvent.KEYCODE_TAB; break; // muv_go_num_fn_minu_sft : tAb + muv +123 pnc // kas
            case 0x817B: kk = KeyEvent.KEYCODE_TAB; break; // muv_go_num_fn_minu_sft : tAb + go +123 pnc // kas
            case 0x867B: meta = meta | KeyEvent.META_SHIFT_ON ; kk = KeyEvent.KEYCODE_TAB; break; // muv_go_num_fn_minu_sft : tAb + muv + sft + 123 pnc // kas
            case 0x857B: meta = meta | KeyEvent.META_SHIFT_ON ; kk = KeyEvent.KEYCODE_TAB; break; // muv_go_num_fn_minu_sft : tAb + go + sft + 123 pnc // kas
            case 0x0E7B: meta = meta | KeyEvent.META_ALT_ON ; kk = KeyEvent.KEYCODE_TAB; break; // muv_go_num_fn_minu_sft : alt + muv + tAb + sft // kas
            case 0x0D7B: meta = meta | KeyEvent.META_ALT_ON ;kk = KeyEvent.KEYCODE_TAB; break; // muv_go_num_fn_minu_sft : alt + go + tAb + sft // kas
            case 0x8A7B: meta = meta | KeyEvent.META_ALT_ON ; kk = KeyEvent.KEYCODE_TAB; break; // muv_go_num_fn_minu_sft : alt + muv + tAb + 123 // kas
            case 0x897B: meta = meta | KeyEvent.META_ALT_ON ;kk = KeyEvent.KEYCODE_TAB; break; // muv_go_num_fn_minu_sft : alt + go + tAb + 123 // kas
            case 0x8E7B: meta = meta | KeyEvent.META_ALT_ON ; kk = KeyEvent.KEYCODE_TAB; break; // muv_go_num_fn_minu_sft : alt + muv + tAb + sft + 123 // kas
            case 0x8D7B: meta = meta | KeyEvent.META_ALT_ON ;kk = KeyEvent.KEYCODE_TAB; break; // muv_go_num_fn_minu_sft : alt + go + tAb + sft + 123 // kas

            case 0x0077: kk = KeyEvent.KEYCODE_U; break; // muv_go_num_fn_minu_sft // kas
            case 0x0477: kk = KeyEvent.KEYCODE_U; break; // muv_go_num_fn_minu_sft : sft // kas
            case 0x8077: kk = KeyEvent.KEYCODE_3; break;  // muv_go_num_fn_minu_sft : 123 // kas
            case 0x0277: kk = KeyEvent.KEYCODE_PAGE_UP; break; // muv_go_num_fn_minu_sft : muv // kas
            case 0x0177: kk = KeyEvent.KEYCODE_DPAD_UP; break; // muv_go_num_fn_minu_sft : go // kas
            case 0x0677: meta = meta | KeyEvent.META_SHIFT_ON ; kk = KeyEvent.KEYCODE_PAGE_UP; break; // muv_go_num_fn_minu_sft : muv + sft // kas
            case 0x0577: meta = meta | KeyEvent.META_SHIFT_ON ; kk = KeyEvent.KEYCODE_DPAD_UP; break; // muv_go_num_fn_minu_sft : go + sft // kas
            // pnc cases
            case 0x8477: kk = KeyEvent.KEYCODE_3; break;  // muv_go_num_fn_minu_sft : sft+123 pnc // kas
            case 0x8277: kk = KeyEvent.KEYCODE_PAGE_UP; break; // muv_go_num_fn_minu_sft : muv+123 pnc // kas
            case 0x8177: kk = KeyEvent.KEYCODE_DPAD_UP; break; // muv_go_num_fn_minu_sft : go+123 pnc // kas
            case 0x8677: meta = meta | KeyEvent.META_SHIFT_ON ; kk = KeyEvent.KEYCODE_PAGE_UP; break; // muv_go_num_fn_minu_sft : muv + sft + 123 pnc // kas
            case 0x8577: meta = meta | KeyEvent.META_SHIFT_ON ; kk = KeyEvent.KEYCODE_DPAD_UP; break; // muv_go_num_fn_minu_sft : go + sft + 123 pnc // kas

            case 0x007E: kk = KeyEvent.KEYCODE_A; break; // muv_go_num_fn_minu_sft // kas
            case 0x047E: kk = KeyEvent.KEYCODE_A; break; // muv_go_num_fn_minu_sft : sft // kas
            case 0x807E: kk = KeyEvent.KEYCODE_0; break; // muv_go_num_fn_minu_sft : 123 // kas
            case 0x027E: kk = KeyEvent.KEYCODE_PAGE_DOWN; break; // muv_go_num_fn_minu_sft : muv // kas
            case 0x017E: kk = KeyEvent.KEYCODE_DPAD_DOWN; break; // muv_go_num_fn_minu_sft : go // kas
            case 0x067E: meta = meta | KeyEvent.META_SHIFT_ON ;  kk = KeyEvent.KEYCODE_PAGE_DOWN; break; // muv_go_num_fn_minu_sft : muv + sft // kas
            case 0x057E: meta = meta | KeyEvent.META_SHIFT_ON ;  kk = KeyEvent.KEYCODE_DPAD_DOWN; break; // muv_go_num_fn_minu_sft : go + sft // kas
            // pnc cases
            case 0x847E: kk = KeyEvent.KEYCODE_0; break;  // muv_go_num_fn_minu_sft : sft+123 pnc // kas
            case 0x827E: kk = KeyEvent.KEYCODE_PAGE_DOWN; break; // muv_go_num_fn_minu_sft : muv+123 pnc // kas
            case 0x817E: kk = KeyEvent.KEYCODE_DPAD_DOWN; break; // muv_go_num_fn_minu_sft : go+123 pnc // kas
            case 0x867E: meta = meta | KeyEvent.META_SHIFT_ON ;  kk = KeyEvent.KEYCODE_PAGE_DOWN; break; // muv_go_num_fn_minu_sft : muv + sft + 123 pnc // kas
            case 0x857E: meta = meta | KeyEvent.META_SHIFT_ON ;  kk = KeyEvent.KEYCODE_DPAD_DOWN; break; // muv_go_num_fn_minu_sft : go + sft + 123 pnc // kas

            case 0x003F: kk = KeyEvent.KEYCODE_I; break; // muv_go_num_fn_minu_sft // kas
            case 0x043F: kk = KeyEvent.KEYCODE_I; break; // muv_go_num_fn_minu_sft : sft // kas
            case 0x803F: kk = KeyEvent.KEYCODE_6; break; // muv_go_num_fn_minu_sft : 123 // kas
            case 0x023F: kk = KeyEvent.KEYCODE_MOVE_HOME; break; // muv_go_num_fn_minu_sft : muv // kas
            case 0x013F: kk = KeyEvent.KEYCODE_DPAD_LEFT; break; // muv_go_num_fn_minu_sft : go // kas
            case 0x063F: meta = meta | KeyEvent.META_SHIFT_ON ;  kk = KeyEvent.KEYCODE_MOVE_HOME; break; // muv_go_num_fn_minu_sft : muv + sft // kas
            case 0x053F: meta = meta | KeyEvent.META_SHIFT_ON ;  kk = KeyEvent.KEYCODE_DPAD_LEFT; break; // muv_go_num_fn_minu_sft : go + sft // kas
            case 0x123F: meta = meta | KeyEvent.META_CTRL_ON ; kk = KeyEvent.KEYCODE_MOVE_HOME; break; // muv_go_num_fn_minu_sft : muv + ktl // kas
            case 0x163F: meta = meta | KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON ;  kk = KeyEvent.KEYCODE_MOVE_HOME; break; // muv_go_num_fn_minu_sft : muv + sft + ktl // kas
            // pnc cases
            case 0x843F: kk = KeyEvent.KEYCODE_6; break;  // muv_go_num_fn_minu_sft : sft+123 pnc // kas
            case 0x823F: kk = KeyEvent.KEYCODE_MOVE_HOME; break; // muv_go_num_fn_minu_sft : muv+123 pnc // kas
            case 0x813F: kk = KeyEvent.KEYCODE_DPAD_LEFT; break; // muv_go_num_fn_minu_sft : go+123 pnc // kas
            case 0x863F: meta = meta | KeyEvent.META_SHIFT_ON ;  kk = KeyEvent.KEYCODE_MOVE_HOME; break; // muv_go_num_fn_minu_sft : muv + sft + 123 pnc // kas
            case 0x853F: meta = meta | KeyEvent.META_SHIFT_ON ;  kk = KeyEvent.KEYCODE_DPAD_LEFT; break; // muv_go_num_fn_minu_sft : go + sft + 123 pnc // kas
            case 0x923F: meta = meta | KeyEvent.META_CTRL_ON ; kk = KeyEvent.KEYCODE_MOVE_HOME; break; // muv_go_num_fn_minu_sft : muv + ktl +123 pnc // kas
            case 0x963F: meta = meta | KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON ;  kk = KeyEvent.KEYCODE_MOVE_HOME; break; // muv_go_num_fn_minu_sft : muv + sft + ktl +123 pnc // kas

            case 0x007D: kk = KeyEvent.KEYCODE_E; break; // muv_go_num_fn_minu_sft // kas
            case 0x047D: kk = KeyEvent.KEYCODE_E; break; // muv_go_num_fn_minu_sft : sft // kas
            case 0x807D: kk = KeyEvent.KEYCODE_1; break; // muv_go_num_fn_minu_sft : 123 // kas
            case 0x027D: kk = KeyEvent.KEYCODE_MOVE_END; break; // muv_go_num_fn_minu_sft : muv // kas
            case 0x017D: kk = KeyEvent.KEYCODE_DPAD_RIGHT; break; // muv_go_num_fn_minu_sft : go // kas
            case 0x067D: meta = meta | KeyEvent.META_SHIFT_ON ;  kk = KeyEvent.KEYCODE_MOVE_END; break; // muv_go_num_fn_minu_sft : muv + sft // kas
            case 0x057D: meta = meta | KeyEvent.META_SHIFT_ON ;  kk = KeyEvent.KEYCODE_DPAD_RIGHT; break; // muv_go_num_fn_minu_sft : go + sft // kas
            case 0x127D: meta = meta | KeyEvent.META_CTRL_ON ; kk = KeyEvent.KEYCODE_MOVE_END; break; // muv_go_num_fn_minu_sft : muv + ktl // kas
            case 0x167D: meta = meta | KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON ;  kk = KeyEvent.KEYCODE_MOVE_END; break; // muv_go_num_fn_minu_sft : muv + sft + ktl // kas
            // pnc cases
            case 0x847D: kk = KeyEvent.KEYCODE_1; break;  // muv_go_num_fn_minu_sft : sft+123 pnc // kas
            case 0x827D: kk = KeyEvent.KEYCODE_MOVE_HOME; break; // muv_go_num_fn_minu_sft : muv+123 pnc // kas
            case 0x817D: kk = KeyEvent.KEYCODE_DPAD_LEFT; break; // muv_go_num_fn_minu_sft : go+123 pnc // kas
            case 0x867D: meta = meta | KeyEvent.META_SHIFT_ON ;  kk = KeyEvent.KEYCODE_MOVE_END; break; // muv_go_num_fn_minu_sft : muv + sft + 123 pnc // kas
            case 0x857D: meta = meta | KeyEvent.META_SHIFT_ON ;  kk = KeyEvent.KEYCODE_DPAD_RIGHT; break; // muv_go_num_fn_minu_sft : go + sft + 123 pnc // kas
            case 0x927D: meta = meta | KeyEvent.META_CTRL_ON ; kk = KeyEvent.KEYCODE_MOVE_END; break; // muv_go_num_fn_minu_sft : muv + ktl +123 pnc // kas
            case 0x967D: meta = meta | KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON ;  kk = KeyEvent.KEYCODE_MOVE_END; break; // muv_go_num_fn_minu_sft : muv + sft + ktl +123 pnc // kas

            default: isl88_up_pending = true ; break; // kas
        }
        if(!isl88_up_pending) send_kk();
    }
    public void kas_num_fn_minu_sft(int arg_kas88bytes){
        isl88_up_pending = false ; kk = KeyEvent.KEYCODE_UNKNOWN ; ksek = null ; send_y_commit_n = true;
        int num88bytes =  arg_kas88bytes;
        if ( mKeyboardSwitcher.is_nm_lok() ) {num88bytes =  num88bytes ^ 0x8000 ; }
//        if ( mKeyboardSwitcher.is_sft_lok() ) {num88bytes =  num88bytes ^ 0x0400 ; }
        switch (num88bytes) {
            // ******************************************************* //
            // num_fn_minu_sft vhite key k2p = 0 keys from 0-6 + sft/dot/num
            // ******************************************************* //
            case 0x007F: kk = KeyEvent.KEYCODE_F; break; // num // kas
            case 0x047F: kk = KeyEvent.KEYCODE_L; break; // sft // kas
            case 0x847F: kk = KeyEvent.KEYCODE_L; break; // num + stt // kas
            case 0x807F: kk = KeyEvent.KEYCODE_F; break; // kas

            // ******************************************************* //
            // num_fn_minu_sft yllo key k2p = 0 keys from 0-6 + sft/dot/num
            // ******************************************************* //
            case 0x00FF: kk = KeyEvent.KEYCODE_SPACE; break; // kas
            case 0x04FF: kk = KeyEvent.KEYCODE_SPACE; break; // sft // kas
            case 0x80FF:  kk = KeyEvent.KEYCODE_7; break; //num // kas
            case 0x84FF: kk = KeyEvent.KEYCODE_SPACE; break;  // num + stt // kas

            // ******************************************************* //
            // num_fn_minu_sft k2p = 1 keys from 0-6 bilo + sft/dot/num
            // ******************************************************* //
            case 0x006F:  kk = KeyEvent.KEYCODE_A; break; // kas
            case 0x806F:  kk = KeyEvent.KEYCODE_4; break;  //num // kas
            case 0x046F: kk = KeyEvent.KEYCODE_A; break; //sft // kas
            // pnc case
            case 0x846F:  kk = KeyEvent.KEYCODE_A; break;  //num + sft ignore sft // kas

            case 0x005F:  kk = KeyEvent.KEYCODE_O; break; // kas
            case 0x805F:  kk = KeyEvent.KEYCODE_5; break;  //num // kas
            case 0x045F: kk = KeyEvent.KEYCODE_O; break; //sft // kas
            // pnc case
            case 0x845F:  kk = KeyEvent.KEYCODE_O; break; //num + sft ignore sft // kas

            ////////////////////
            // 82F num keys bilo
            ////////////////////

            case 0x017F:  kk = KeyEvent.KEYCODE_PERIOD; break; // kas
            case 0x817F:  kk = KeyEvent.KEYCODE_8; break; //num // kas
            case 0x057F:  kk = KeyEvent.KEYCODE_PERIOD; break; //sft // kas
            // pnc case
            case 0x857F: kk = KeyEvent.KEYCODE_PERIOD; break; //num + sft ignore sft // kas

            case 0x027F:  kk = KeyEvent.KEYCODE_K; break; // kas
            case 0x827F:  kk = KeyEvent.KEYCODE_9; break; //num // kas
            case 0x067F:  kk = KeyEvent.KEYCODE_K; break; //sft // kas
            // pnc case
            case 0x867F: kk = KeyEvent.KEYCODE_K; break; //num + sft ignore sft // kas

            case 0x087F:  kk = KeyEvent.KEYCODE_DEL; break; // kas
            case 0x887F:  kk = KeyEvent.KEYCODE_J; break; //num // kas
            case 0x0C7F:  kk = KeyEvent.KEYCODE_J; break; //sft // kas
            // pnc case
            case 0x8C7F: kk = KeyEvent.KEYCODE_J; break; //num + sft ignore num as decimal numbers are used more // kas

            case 0x107F: kk = KeyEvent.KEYCODE_ENTER; break; // kas
            case 0x907F: kk = KeyEvent.KEYCODE_Q; break; // num // kas
            case 0x147F: kk = KeyEvent.KEYCODE_Q; break; // sft // kas
            // pnc case
            case 0x947F: kk = KeyEvent.KEYCODE_Q; break; //num + sft ignore num as decimal numbers are used more // kas

            case 0x207F:  kk = KeyEvent.KEYCODE_FORWARD_DEL; break; // kas
            case 0xA07F: kk = KeyEvent.KEYCODE_W; break; // num // kas
            case 0x247F: kk = KeyEvent.KEYCODE_W; break; // sft // kas
            // pnc case
            case 0xA47F: kk = KeyEvent.KEYCODE_W; break; //num + sft ignore num as decimal numbers are used more // kas

            case 0x407F: kk = KeyEvent.KEYCODE_X; break; // kas
            case 0xC07F: kk = KeyEvent.KEYCODE_X; break; // num // kas
            case 0x447F: kk = KeyEvent.KEYCODE_X; break; // sft // kas
            // pnc case
            case 0xC47F: kk = KeyEvent.KEYCODE_X; break; //num + sft ignore num as decimal numbers are used more // kas

            default: isl88_up_pending = true ; break; // kas
        }
        if(!isl88_up_pending) send_kk();
    }
    public void kas_06(int arg_kas88bytes){
        isl88_up_pending = false ; kk = KeyEvent.KEYCODE_UNKNOWN ; ksek = null; send_y_commit_n = true;
        int num88bytes =  arg_kas88bytes;
//        if (mKeyboardSwitcher.is_sft_lok() && ((num88bytes & 0x0080) == 0)) // if yllo key is also pressed ignore sft mode/on/key
//            { num88bytes =  num88bytes ^ 0x0400 ; }
        switch (num88bytes) {
            // ******************************************************* //
            // send_06 k2p = yllo + 1 = 2 keys from 0-6 bilo + sft/dot/num
            // ******************************************************* //
            case 0x00FE: kk = KeyEvent.KEYCODE_N; break; //yllo // kas
            case 0x04FE: kk = KeyEvent.KEYCODE_PERIOD; break; //yllo + sft // kas
            case 0x00FD: kk = KeyEvent.KEYCODE_ESCAPE; break; //yllo // kas
            case 0x04FD: kk = KeyEvent.KEYCODE_ESCAPE; break; //yllo + sft // kas
            case 0x00FB: kk = KeyEvent.KEYCODE_SEMICOLON; break; //yllo // kas
            case 0x04FB: kk = KeyEvent.KEYCODE_SEMICOLON; break; //yllo + sft // kas
            case 0x00F7: kk = KeyEvent.KEYCODE_X; break; //yllo // kas
            case 0x04F7: kk = KeyEvent.KEYCODE_X; break; //yllo + sft // kas
            case 0x00EF: kk = KeyEvent.KEYCODE_D; break; //yllo // kas
            case 0x04EF: kk = KeyEvent.KEYCODE_D; break; //yllo + sft // kas
            case 0x00DF: kk = KeyEvent.KEYCODE_MINUS; break; //yllo // kas
            case 0x04DF: kk = KeyEvent.KEYCODE_MINUS; break; //yllo + sft // kas
            case 0x00BF: kk = KeyEvent.KEYCODE_SEMICOLON; break; //yllo // kas
            case 0x04BF: kk = KeyEvent.KEYCODE_SEMICOLON; break; //yllo + sft // kas

            // ******************************************************* //
            // send_06 k2p = 2 keys from 0-6 bilo + sft/dot/num
            // ******************************************************* //
            case 0x007C: kk = KeyEvent.KEYCODE_P; break; // kas
            case 0x00FC: kk = KeyEvent.KEYCODE_COMMA; break; // kas
            case 0x807C: kk = KeyEvent.KEYCODE_P; break; // kas
            case 0x047C: kk = KeyEvent.KEYCODE_P; break; // kas

            case 0x007A: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas
            case 0x00FA: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas
            case 0x807A: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas
            case 0x047A: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas

            case 0x0079: kk = KeyEvent.KEYCODE_F; break; // kas
            case 0x00F9: kk = KeyEvent.KEYCODE_F; break; // kas
            case 0x8079: kk = KeyEvent.KEYCODE_F; break; // kas
            case 0x0479: kk = KeyEvent.KEYCODE_F; break; // kas

            case 0x0076: kk = KeyEvent.KEYCODE_H; break; // kas
            case 0x8076: kk = KeyEvent.KEYCODE_EQUALS; break; // kas
            case 0x00F6: kk = KeyEvent.KEYCODE_H; break; // kas
            case 0x0476: kk = KeyEvent.KEYCODE_H; break; // kas

            case 0x0075: kk = KeyEvent.KEYCODE_Y; break; // kas
            case 0x8075: kk = KeyEvent.KEYCODE_Y; break; // kas
            case 0x0475: kk = KeyEvent.KEYCODE_Y; break; // kas
            case 0x00F5: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas

            case 0x0073: kk = KeyEvent.KEYCODE_B; break; // kas
            case 0x8073: kk = KeyEvent.KEYCODE_B; break; // kas
            case 0x0473: kk = KeyEvent.KEYCODE_B; break; // kas
            case 0x00F3: kk = KeyEvent.KEYCODE_GRAVE; break; // kas

            case 0x006E: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas
            case 0x806E: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas
            case 0x00EE: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas
            case 0x046E: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas

            case 0x006D: kk = KeyEvent.KEYCODE_Z; break; // kas
            case 0x806D: kk = KeyEvent.KEYCODE_Z; break; // kas
            case 0x00ED: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas
            case 0x046D: kk = KeyEvent.KEYCODE_Z; break; // kas

            case 0x006B: kk = KeyEvent.KEYCODE_APOSTROPHE; break; // kas
            case 0x806B: kk = KeyEvent.KEYCODE_APOSTROPHE; break; // kas
            case 0x00EB: kk = KeyEvent.KEYCODE_APOSTROPHE; break; // kas
            case 0x046B: kk = KeyEvent.KEYCODE_APOSTROPHE; break; // kas

            case 0x0067: kk = KeyEvent.KEYCODE_D; break; // kas
            case 0x8067: kk = KeyEvent.KEYCODE_D; break; // kas
            case 0x00E7: kk = KeyEvent.KEYCODE_D; break; // kas
            case 0x0467: kk = KeyEvent.KEYCODE_D; break; // kas


            case 0x005E: kk = KeyEvent.KEYCODE_EQUALS; break; // kas
            case 0x00DE: kk = KeyEvent.KEYCODE_EQUALS; break; // kas
            case 0x805E: kk = KeyEvent.KEYCODE_EQUALS; break; // kas
            case 0x045E: kk = KeyEvent.KEYCODE_EQUALS; break; // kas

            case 0x005D: kk = KeyEvent.KEYCODE_COMMA; break; // kas
            case 0x805D: kk = KeyEvent.KEYCODE_COMMA; break; // kas
            case 0x045D: kk = KeyEvent.KEYCODE_COMMA; break; // kas
            case 0x00DD: kk = KeyEvent.KEYCODE_COMMA; break; // kas

            case 0x005B: kk = KeyEvent.KEYCODE_G; break; // kas
            case 0x805B: kk = KeyEvent.KEYCODE_G; break; // kas
            case 0x00DB: kk = KeyEvent.KEYCODE_G; break; // kas
            case 0x045B: kk = KeyEvent.KEYCODE_G; break; // kas

            case 0x0057: kk = KeyEvent.KEYCODE_V; break; // kas
            case 0x8057: kk = KeyEvent.KEYCODE_V; break; // kas
            case 0x00D7: kk = KeyEvent.KEYCODE_V; break; // kas
            case 0x0457: kk = KeyEvent.KEYCODE_V; break; // kas

            case 0x004F: kk = KeyEvent.KEYCODE_J; break; // kas
            case 0x804F: kk = KeyEvent.KEYCODE_J; break; // kas
            case 0x00CF: kk = KeyEvent.KEYCODE_J; break; // kas
            case 0x044F: kk = KeyEvent.KEYCODE_J; break; // kas

            case 0x003E: kk = KeyEvent.KEYCODE_Q; break; // kas
            case 0x803E: kk = KeyEvent.KEYCODE_Q; break; // kas
            case 0x00BE: kk = KeyEvent.KEYCODE_Q; break; // kas
            case 0x043E: kk = KeyEvent.KEYCODE_Q; break; // kas

            case 0x003D: kk = KeyEvent.KEYCODE_T; break; // kas
            case 0x803D: kk = KeyEvent.KEYCODE_T; break; // kas
            case 0x00BD: kk = KeyEvent.KEYCODE_T; break; // kas
            case 0x043D: kk = KeyEvent.KEYCODE_T; break; // kas

            case 0x003B: kk = KeyEvent.KEYCODE_S; break; // kas
            case 0x00BB: kk = KeyEvent.KEYCODE_S; break; // kas
            case 0x803B: kk = KeyEvent.KEYCODE_S; break; // kas
            case 0x043B: kk = KeyEvent.KEYCODE_S; break; // kas

            case 0x0037: kk = KeyEvent.KEYCODE_Y; break; // kas
            case 0x8037: kk = KeyEvent.KEYCODE_Y; break; // kas
            case 0x00B7: kk = KeyEvent.KEYCODE_Y; break; // kas
            case 0x0437: kk = KeyEvent.KEYCODE_Y; break; // kas

            case 0x002F: kk = KeyEvent.KEYCODE_T; break; // kas
            case 0x00AF: kk = KeyEvent.KEYCODE_T; break; // kas
            case 0x802F: kk = KeyEvent.KEYCODE_T; break; // kas
            case 0x042F: kk = KeyEvent.KEYCODE_T; break; // kas

            case 0x001F: kk = KeyEvent.KEYCODE_R; break; // kas
            case 0x009F: kk = KeyEvent.KEYCODE_R; break; // kas
            case 0x801F: kk = KeyEvent.KEYCODE_R; break; // kas
            case 0x041F: kk = KeyEvent.KEYCODE_R; break; // kas

            // ******************************************************* //
            // send_06 k2p = 3 keys from 0-6 bilo + sft/dot/num
            // ******************************************************* //
            case 0x0078: kk = KeyEvent.KEYCODE_F; break; // kas
            case 0x8078: kk = KeyEvent.KEYCODE_F; break; // kas
            case 0x00F8: kk = KeyEvent.KEYCODE_F; break; // kas
            case 0x0478: kk = KeyEvent.KEYCODE_F; break; // kas

            case 0x0074: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas
            case 0x8074: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas
            case 0x00F4: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas
            case 0x0474: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas

            case 0x0072: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas
            case 0x8072: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas
            case 0x00F2: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas
            case 0x0472: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas

            case 0x0071: kk = KeyEvent.KEYCODE_T; break; // kas
            case 0x8071: kk = KeyEvent.KEYCODE_T; break; // kas
            case 0x00F1: kk = KeyEvent.KEYCODE_T; break; // kas
            case 0x0471: kk = KeyEvent.KEYCODE_T; break; // kas

            case 0x006C: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas
            case 0x806C: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas
            case 0x00EC: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas
            case 0x046C: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas

            case 0x006A: kk = KeyEvent.KEYCODE_W; break; // kas
            case 0x806A: kk = KeyEvent.KEYCODE_W; break; // kas
            case 0x00EA: kk = KeyEvent.KEYCODE_W; break; // kas
            case 0x046A: kk = KeyEvent.KEYCODE_W; break; // kas

            case 0x0069: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas
            case 0x8069: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas
            case 0x00E9: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas
            case 0x0469: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas

            case 0x0066: kk = KeyEvent.KEYCODE_APOSTROPHE; break; // kas
            case 0x8066: kk = KeyEvent.KEYCODE_APOSTROPHE; break; // kas
            case 0x00E6: kk = KeyEvent.KEYCODE_APOSTROPHE; break; // kas
            case 0x0466: kk = KeyEvent.KEYCODE_APOSTROPHE; break; // kas

            case 0x0065: kk = KeyEvent.KEYCODE_APOSTROPHE; break; // kas
            case 0x00E5: kk = KeyEvent.KEYCODE_APOSTROPHE; break; // kas
            case 0x8065: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas
            case 0x0465: kk = KeyEvent.KEYCODE_APOSTROPHE; break; // kas

            case 0x0063: kk = KeyEvent.KEYCODE_M; break; // kas
            case 0x8063: kk = KeyEvent.KEYCODE_M; break; // kas
            case 0x00E3: kk = KeyEvent.KEYCODE_M; break; // kas
            case 0x0463: kk = KeyEvent.KEYCODE_M; break; // kas

            case 0x005C: kk = KeyEvent.KEYCODE_COMMA; break; // kas
            case 0x805C: kk = KeyEvent.KEYCODE_COMMA; break; // kas
            case 0x00DC: kk = KeyEvent.KEYCODE_COMMA; break; // kas
            case 0x045C: kk = KeyEvent.KEYCODE_COMMA; break; // kas

            case 0x005A: kk = KeyEvent.KEYCODE_SLASH; break; // kas
            case 0x805A: kk = KeyEvent.KEYCODE_SLASH; break; // kas
            case 0x00DA: kk = KeyEvent.KEYCODE_SLASH; break; // kas
            case 0x045A: kk = KeyEvent.KEYCODE_SLASH; break; // kas

            case 0x0059: kk = KeyEvent.KEYCODE_C; break; // kas
            case 0x8059: kk = KeyEvent.KEYCODE_C; break; // not easy_to_guess // kas
            case 0x00D9: kk = KeyEvent.KEYCODE_C; break; // not easy_to_guess // kas
            case 0x0459: kk = KeyEvent.KEYCODE_C; break; // not easy_to_guess // kas

            case 0x0056: kk = KeyEvent.KEYCODE_L; break; // kas
            case 0x8056: kk = KeyEvent.KEYCODE_L; break; // kas
            case 0x00D6: kk = KeyEvent.KEYCODE_L; break; // kas
            case 0x0456: kk = KeyEvent.KEYCODE_L; break; // kas

            case 0x0055: kk = KeyEvent.KEYCODE_COMMA; break; // kas
            case 0x8055: kk = KeyEvent.KEYCODE_COMMA; break; // kas
            case 0x00D5: kk = KeyEvent.KEYCODE_COMMA; break; // kas
            case 0x0455: kk = KeyEvent.KEYCODE_COMMA; break; // kas

            case 0x0053: kk = KeyEvent.KEYCODE_APOSTROPHE; break; // kas
            case 0x8053: kk = KeyEvent.KEYCODE_APOSTROPHE; break; // kas
            case 0x00D3: kk = KeyEvent.KEYCODE_APOSTROPHE; break; // kas
            case 0x0453: kk = KeyEvent.KEYCODE_APOSTROPHE; break; // kas

            case 0x004E: kk = KeyEvent.KEYCODE_EQUALS; break; // kas
            case 0x00CE: kk = KeyEvent.KEYCODE_EQUALS; break; // kas
            case 0x804E: kk = KeyEvent.KEYCODE_EQUALS; break; // kas
            case 0x044E: kk = KeyEvent.KEYCODE_EQUALS; break; // kas

            case 0x004D: kk = KeyEvent.KEYCODE_BACKSLASH; break; // kas
            case 0x00CD: kk = KeyEvent.KEYCODE_BACKSLASH; break; // kas
            case 0x804D: kk = KeyEvent.KEYCODE_BACKSLASH; break; // kas
            case 0x044D: kk = KeyEvent.KEYCODE_BACKSLASH; break; // kas

            case 0x004B: kk = KeyEvent.KEYCODE_W; break; // kas
            case 0x00CB: kk = KeyEvent.KEYCODE_W; break; // kas
            case 0x804B: kk = KeyEvent.KEYCODE_W; break; // kas
            case 0x044B: kk = KeyEvent.KEYCODE_W; break; // kas

            case 0x0047: kk = KeyEvent.KEYCODE_GRAVE; break; // kas
            case 0x00C7: kk = KeyEvent.KEYCODE_GRAVE; break; // kas
            case 0x8047: kk = KeyEvent.KEYCODE_GRAVE; break; // kas
            case 0x0447: kk = KeyEvent.KEYCODE_GRAVE; break; // kas

            case 0x003C: kk = KeyEvent.KEYCODE_PERIOD; break; // kas
            case 0x00BC: kk = KeyEvent.KEYCODE_PERIOD; break; // kas
            case 0x803C: kk = KeyEvent.KEYCODE_PERIOD; break; // kas
            case 0x043C: kk = KeyEvent.KEYCODE_PERIOD; break; // kas

            case 0x003A: kk = KeyEvent.KEYCODE_COMMA; break; // kas
            case 0x00BA: kk = KeyEvent.KEYCODE_COMMA; break; // kas
            case 0x803A: kk = KeyEvent.KEYCODE_COMMA; break; // kas
            case 0x043A: kk = KeyEvent.KEYCODE_COMMA; break; // kas

            case 0x0039: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas
            case 0x00B9: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas
            case 0x8039: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas
            case 0x0439: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas

            case 0x0036: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas
            case 0x00B6: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas
            case 0x8036: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas
            case 0x0436: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas

            case 0x0035: kk = KeyEvent.KEYCODE_W; break; // kas
            case 0x00B5: kk = KeyEvent.KEYCODE_W; break; // kas
            case 0x8035: kk = KeyEvent.KEYCODE_W; break; // kas
            case 0x0435: kk = KeyEvent.KEYCODE_W; break; // kas

            case 0x0033: kk = KeyEvent.KEYCODE_GRAVE; break; // kas
            case 0x00B3: kk = KeyEvent.KEYCODE_GRAVE; break; // kas
            case 0x8033: kk = KeyEvent.KEYCODE_GRAVE; break; // kas
            case 0x0433: kk = KeyEvent.KEYCODE_GRAVE; break; // kas

            case 0x002E: kk = KeyEvent.KEYCODE_L; break; // kas
            case 0x00AE: kk = KeyEvent.KEYCODE_L; break; // kas
            case 0x802E: kk = KeyEvent.KEYCODE_L; break; // kas
            case 0x042E: kk = KeyEvent.KEYCODE_L; break; // kas

            case 0x002D: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas
            case 0x00AD: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas
            case 0x802D: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas
            case 0x042D: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas

            case 0x002B: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas
            case 0x00AB: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas
            case 0x802B: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas
            case 0x042B: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas

            case 0x0027: kk = KeyEvent.KEYCODE_APOSTROPHE; break; // kas
            case 0x00A7: kk = KeyEvent.KEYCODE_APOSTROPHE; break; // kas
            case 0x8027: kk = KeyEvent.KEYCODE_APOSTROPHE; break; // kas
            case 0x0427: kk = KeyEvent.KEYCODE_APOSTROPHE; break; // kas

            case 0x001E: kk = KeyEvent.KEYCODE_C; break; // kas
            case 0x009E: kk = KeyEvent.KEYCODE_C; break; // kas
            case 0x801E: kk = KeyEvent.KEYCODE_C; break; // kas
            case 0x041E: kk = KeyEvent.KEYCODE_C; break; // kas

            case 0x001D: kk = KeyEvent.KEYCODE_N; break; // kas
            case 0x009D: kk = KeyEvent.KEYCODE_N; break; // kas
            case 0x801D: kk = KeyEvent.KEYCODE_N; break; // kas
            case 0x041D: kk = KeyEvent.KEYCODE_N; break; // kas

            case 0x001B: kk = KeyEvent.KEYCODE_SLASH; break; // kas
            case 0x009B: kk = KeyEvent.KEYCODE_SLASH; break; // kas
            case 0x801B: kk = KeyEvent.KEYCODE_SLASH; break; // kas
            case 0x041B: kk = KeyEvent.KEYCODE_SLASH; break; // kas

            case 0x0017: kk = KeyEvent.KEYCODE_COMMA; break; // kas
            case 0x0097: kk = KeyEvent.KEYCODE_COMMA; break; // kas
            case 0x8017: kk = KeyEvent.KEYCODE_COMMA; break; // kas
            case 0x0417: kk = KeyEvent.KEYCODE_COMMA; break; // kas

            case 0x000F: kk = KeyEvent.KEYCODE_K; break; // kas
            case 0x008F: kk = KeyEvent.KEYCODE_K; break; // kas
            case 0x800F: kk = KeyEvent.KEYCODE_K; break; // kas
            case 0x040F: kk = KeyEvent.KEYCODE_K; break; // kas


            // ******************************************************* //
            // send_06  k2p = 4 keys from 0-6 bilo + sft/dot/num
            // ******************************************************* //

            case 0x0070: kk = KeyEvent.KEYCODE_LEFT_BRACKET; break; // kas
            case 0x00F0: kk = KeyEvent.KEYCODE_LEFT_BRACKET; break; // kas
            case 0x8070: kk = KeyEvent.KEYCODE_LEFT_BRACKET; break; // kas
            case 0x0470: kk = KeyEvent.KEYCODE_LEFT_BRACKET; break; // kas

            case 0x8068: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas
            case 0x00E8: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas
            case 0x0468: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas
            case 0x0068: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas

            case 0x0064: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas
//            case 0x00E4: send_y_commit_n = false; ksek = "[]"; break; // kas
            // case 0x8064: isl88_up_pending = true; break;	 // kas
            // case 0x0464: isl88_up_pending = true; break; // kas

            case 0x0062: kk = KeyEvent.KEYCODE_PERIOD; break; // kas
            // case 0x00E2: send_y_commit_n = false; ksek = "*"; break; // kas
            // case 0x8062: isl88_up_pending = true; break;	 // kas
            // case 0x0462: isl88_up_pending = true; break; // kas

            case 0x0061: kk = KeyEvent.KEYCODE_APOSTROPHE; break; // kas
            // case 0x00E1: kk = KeyEvent.KEYCODE_COMMA; break; // kas
            // case 0x8061: isl88_up_pending = true; break;	 // kas
            // case 0x0461: isl88_up_pending = true; break; // kas

            case 0x0058: kk = KeyEvent.KEYCODE_APOSTROPHE; break; // kas
            // case 0x00D8: kk = KeyEvent.KEYCODE_APOSTROPHE; break; // kas
            // case 0x8058: isl88_up_pending = true; break;	 // kas
            // case 0x0458: isl88_up_pending = true; break; // kas

            case 0x0054: kk = KeyEvent.KEYCODE_L; break; // kas
            // case 0x00D4: send_y_commit_n = false; ksek = "!"; break; // kas
//            case 0x8054: send_y_commit_n = false; ksek = "L"; break; // kas
//            case 0x0454: send_y_commit_n = false; ksek = "L"; break; // kas

            case 0x0052: kk = KeyEvent.KEYCODE_LEFT_BRACKET; break; // kas
            // case 0x00D2: kk = KeyEvent.KEYCODE_SLASH; break; // kas
            // case 0x8052: isl88_up_pending = true; break; // kas
//            case 0x0452: send_y_commit_n = false; ksek = "?"; break; // kas

            case 0x0051: kk = KeyEvent.KEYCODE_F; break; // kas
            // case 0x00D1: send_y_commit_n = false; ksek = "|"; break; // kas
            // case 0x8051: isl88_up_pending = true; break;	 // kas
            // case 0x0451: isl88_up_pending = true; break; // kas

            case 0x004C: kk = KeyEvent.KEYCODE_BACKSLASH; break;  //kas // kas
            // case 0x00CC: send_y_commit_n = false; ksek = "%"; break; // kas
            // case 0x804C: isl88_up_pending = true; break;	 // kas
            // case 0x044C: isl88_up_pending = true; break; // kas

            case 0x004A: kk = KeyEvent.KEYCODE_U; break; // kas
            // case 0x00CA: send_y_commit_n = false; ksek = "U"; break; // kas
            // case 0x804A: isl88_up_pending = true; break;	 // kas
            // case 0x044A: isl88_up_pending = true; break; // kas

            case 0x0049: kk = KeyEvent.KEYCODE_BACKSLASH; break; // kas
            // case 0x00C9: send_y_commit_n = false; ksek = "%"; break; // kas
            // case 0x8049: isl88_up_pending = true; break;	 // kas
            // case 0x0449: isl88_up_pending = true; break; // kas

            case 0x0046: kk = KeyEvent.KEYCODE_BACKSLASH; break; // kas
//            case 0x00C6: send_y_commit_n = false; ksek = "%"; break; // k2p=5 // kas
//            case 0x8046: isl88_up_pending = true; break; // kas
//            case 0x0446: isl88_up_pending = true; break; // kas

            case 0x0045: kk = KeyEvent.KEYCODE_BACKSLASH; break; // kas
            case 0x00C5: kk = KeyEvent.KEYCODE_BACKSLASH; break; // kas
            // case 0x8045: isl88_up_pending = true; break;	 // kas
            // case 0x0445: isl88_up_pending = true; break; // kas

            case 0x0043: kk = KeyEvent.KEYCODE_BACKSLASH; break; // kas
            case 0x00C3: kk = KeyEvent.KEYCODE_BACKSLASH; break; // kas
            // case 0x8043: isl88_up_pending = true; break;	 // kas
            // case 0x0443: isl88_up_pending = true; break; // kas

            case 0x0038: kk = KeyEvent.KEYCODE_RIGHT_BRACKET; break; // kas
            // case 0x00B8: send_y_commit_n = false; ksek = "()"; break; // kas
            // case 0x8038: isl88_up_pending = true; break;	 // kas
            // case 0x0438: isl88_up_pending = true; break; // kas

            case 0x0034: kk = KeyEvent.KEYCODE_W; break; // kas
            // case 0x00B4: send_y_commit_n = false; ksek = ":"; break; // kas
            // case 0x8034: isl88_up_pending = true; break;	 // kas
            // case 0x0434: isl88_up_pending = true; break; // kas

            case 0x0032: kk = KeyEvent.KEYCODE_LEFT_BRACKET; break; // kas
            case 0x00B2: kk = KeyEvent.KEYCODE_LEFT_BRACKET; break; // kas
            // case 0x8032: isl88_up_pending = true; break; // kas
            // case 0x0432: isl88_up_pending = true; break; // kas

            case 0x0031: kk = KeyEvent.KEYCODE_BACKSLASH; break; // kas
            // case 0x00B1: kk = KeyEvent.KEYCODE_BACKSLASH; break; // kas
            // case 0x8031: isl88_up_pending = true; break; // kas
            // case 0x0431: isl88_up_pending = true; break; // kas

//            case 0x002C: send_y_commit_n = false; ksek = "()"; break; // kas
            // case 0x00AC: send_y_commit_n = false; ksek = "!"; break; // kas
            // case 0x802C: isl88_up_pending = true; break; // kas
            // case 0x042C: isl88_up_pending = true; break; // kas

            case 0x002A: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas
            // case 0x00AA: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas
            // case 0x802A: isl88_up_pending = true; break; // kas
            // case 0x042A: isl88_up_pending = true; break; // kas

            case 0x0029: send_y_commit_n = false; ksek = "|"; break; // kas
            // case 0x00A9: send_y_commit_n = false; ksek = "|"; break; // kas
            // case 0x8029: kk = KeyEvent.KEYCODE_EQUALS; break; // kas
            // case 0x0429: isl88_up_pending = true; break; // kas

            case 0x0026: kk = KeyEvent.KEYCODE_C; break; // kas
            case 0x00A6: send_y_commit_n = false; ksek = "()"; break; // kas
            case 0x8026: send_y_commit_n = false; ksek = "<>"; break; // kas
            case 0x0426: send_y_commit_n = false; ksek = "{"; break; // kas

            case 0x0025: send_y_commit_n = false; ksek = "()"; break; // kas
            // case 0x00A5: send_y_commit_n = false; ksek = "()"; break; // kas
            // case 0x8025: isl88_up_pending = true; break; // kas
            // case 0x0425: isl88_up_pending = true; break; // kas

            case 0x0023: kk = KeyEvent.KEYCODE_LEFT_BRACKET; break; // kas
            case 0x00A3: kk = KeyEvent.KEYCODE_LEFT_BRACKET; break; // kas
            case 0x8023: kk = KeyEvent.KEYCODE_LEFT_BRACKET; break; // kas
            case 0x0423: kk = KeyEvent.KEYCODE_LEFT_BRACKET; break; // kas

            case 0x001C: kk = KeyEvent.KEYCODE_MINUS; break; // kas
            case 0x009C: kk = KeyEvent.KEYCODE_MINUS; break; // kas
            case 0x801C: kk = KeyEvent.KEYCODE_MINUS; break; // kas
            case 0x041C: kk = KeyEvent.KEYCODE_MINUS; break; // kas

            case 0x001A: kk = KeyEvent.KEYCODE_SLASH; break; // kas
            case 0x009A: kk = KeyEvent.KEYCODE_SLASH; break; // kas
            case 0x801A: kk = KeyEvent.KEYCODE_SLASH; break; // kas
            case 0x041A: kk = KeyEvent.KEYCODE_SLASH; break; // kas

            case 0x0019: kk = KeyEvent.KEYCODE_SLASH; break; // kas
            case 0x0099: kk = KeyEvent.KEYCODE_SLASH; break; // kas
            case 0x8019: kk = KeyEvent.KEYCODE_SLASH; break; // kas
            case 0x0419: kk = KeyEvent.KEYCODE_SLASH; break; // kas

            case 0x0016: kk = KeyEvent.KEYCODE_RIGHT_BRACKET; break; // kas
            case 0x0096: kk = KeyEvent.KEYCODE_RIGHT_BRACKET; break; // kas
            case 0x8016: kk = KeyEvent.KEYCODE_RIGHT_BRACKET; break; // kas
            case 0x0416: kk = KeyEvent.KEYCODE_RIGHT_BRACKET; break; // kas

            case 0x0015: kk = KeyEvent.KEYCODE_BACKSLASH; break; // kas
            case 0x0095: kk = KeyEvent.KEYCODE_BACKSLASH; break; // kas
            case 0x8015: kk = KeyEvent.KEYCODE_BACKSLASH; break; // kas
            case 0x0415: kk = KeyEvent.KEYCODE_BACKSLASH; break; // kas

            case 0x0013: kk = KeyEvent.KEYCODE_SLASH; break; // kas
            case 0x0093: kk = KeyEvent.KEYCODE_SLASH; break; // kas
            case 0x8013: kk = KeyEvent.KEYCODE_SLASH; break; // kas
            case 0x0413: kk = KeyEvent.KEYCODE_SLASH; break; // kas

            case 0x000E: kk = KeyEvent.KEYCODE_T; break; // kas
            case 0x008E: kk = KeyEvent.KEYCODE_T; break; // kas
            case 0x800E: kk = KeyEvent.KEYCODE_T; break; // kas
            case 0x040E: kk = KeyEvent.KEYCODE_T; break; // kas

            case 0x000D: kk = KeyEvent.KEYCODE_SLASH; break; // kas
            case 0x008D: kk = KeyEvent.KEYCODE_SLASH; break; // kas
            case 0x800D: kk = KeyEvent.KEYCODE_SLASH; break; // kas
            case 0x040D: kk = KeyEvent.KEYCODE_SLASH; break; // kas

            case 0x000B: kk = KeyEvent.KEYCODE_SLASH; break; // kas
            case 0x008B: kk = KeyEvent.KEYCODE_SLASH; break; // kas
            case 0x800B: kk = KeyEvent.KEYCODE_SLASH; break; // kas
            case 0x040B: kk = KeyEvent.KEYCODE_SLASH; break; // kas

            case 0x0007: kk = KeyEvent.KEYCODE_F; break; // kas
            case 0x0087: kk = KeyEvent.KEYCODE_F; break; // kas
            case 0x8007: kk = KeyEvent.KEYCODE_F; break; // kas
            case 0x0407: kk = KeyEvent.KEYCODE_F; break; // kas

            default: isl88_up_pending = true; break; // kas
        }
        if(!isl88_up_pending) send_kk();
    }
    public void kas_06_nsd23(int arg_kas88bytes){
        isl88_up_pending = false;
        switch (arg_kas88bytes) {
            // ******************************************************* //
            // send_06_nsd23  k2p = 0 keys from 0-6 bilo + sft/dot/num
            // ******************************************************* //

            case 0x04FF:  send_y_commit_n = false; ksek = "[]"; break; // kas
            // case 0x80FF: isl88_up_pending = true; break; // kas
            // case 0x847F: isl88_up_pending = true; break; // kas
            // case 0x84FF: isl88_up_pending = true; break; // kas

            // ******************************************************* //
            // send_06_nsd23  k2p = 1 keys from 0-6 bilo + sft/dot/num
            // ******************************************************* //

            // case 0x04FE: isl88_up_pending = true; break; // kas
            // case 0x80FE: isl88_up_pending = true; break; // kas
            // case 0x847E: isl88_up_pending = true; break; // kas
            // case 0x84FE: isl88_up_pending = true; break; // kas
//
            case 0x04FD: kk = KeyEvent.KEYCODE_ESCAPE; meta = meta | KeyEvent.META_SHIFT_ON ; break; // kas
            // case 0x80FD: isl88_up_pending = true; break; // kas
            // case 0x847D: isl88_up_pending = true; break; // kas
            // case 0x84FD: isl88_up_pending = true; break; // kas
//
            // case 0x04FB: isl88_up_pending = true; break; // kas
            // case 0x80FB: isl88_up_pending = true; break; // kas
            // case 0x847B: isl88_up_pending = true; break; // kas
            // case 0x84FB: isl88_up_pending = true; break; // kas

            // case 0x04F7: isl88_up_pending = true; break; // kas
            // case 0x80F7: isl88_up_pending = true; break; // kas
            // case 0x8477: isl88_up_pending = true; break; // kas
            // case 0x84F7: isl88_up_pending = true; break; // kas

            // case 0xS1: send_y_commit_n = false; ksek = "`"; break; // kas
            // case 0x80EF: isl88_up_pending = true; break; // kas
            // case 0x846F: isl88_up_pending = true; break; // kas
            // case 0x84EF: isl88_up_pending = true; break; // kas
//
            // case 0x04DF: isl88_up_pending = true; break; // kas
            // case 0x80DF: isl88_up_pending = true; break; // kas
            // case 0x845F: isl88_up_pending = true; break; // kas
            // case 0x84DF: isl88_up_pending = true; break; // kas

            // case 0xS1: send_y_commit_n = false; ksek = "C" ; break; // kas
            // case 0x80BF: isl88_up_pending = true; break; // kas
            // case 0x843F: isl88_up_pending = true; break; // kas
            // case 0x84BF: isl88_up_pending = true; break; // kas



            // ******************************************************* //
            // send_06_nsd23  k2p = 2 keys from 0-6 bilo + sft/dot/num
            // ******************************************************* //
            // case 0x04FC: isl88_up_pending = true; break; // kas
            // case 0x80FC: isl88_up_pending = true; break; // kas
            // case 0x847C: isl88_up_pending = true; break; // kas
            // case 0x84FC: isl88_up_pending = true; break; // kas
//
            // case 0x04FA: isl88_up_pending = true; break; // kas
            // case 0x80FA: isl88_up_pending = true; break; // kas
            // case 0x847A: isl88_up_pending = true; break; // kas
            // case 0x84FA: isl88_up_pending = true; break; // kas
//
            // case 0x04F9: isl88_up_pending = true; break; // kas
            // case 0x80F9: isl88_up_pending = true; break; // kas
            // case 0x8479: isl88_up_pending = true; break; // kas
            // case 0x84F9: isl88_up_pending = true; break; // kas

            // case 0xS1: send_y_commit_n = false; ksek = "#"; break; // kas
            // case 0x80F6: isl88_up_pending = true; break; // kas
            // case 0x8476: isl88_up_pending = true; break; // kas
            // case 0x84F6: isl88_up_pending = true; break; // kas
//
            // case 0x04F5: isl88_up_pending = true; break; // kas
            // case 0x80F5: isl88_up_pending = true; break; // kas
            // case 0x8475: isl88_up_pending = true; break; // kas
            // case 0x84F5: isl88_up_pending = true; break; // kas
//
            // case 0x04F3: isl88_up_pending = true; break; // kas
            // case 0x80F3: isl88_up_pending = true; break; // kas
            // case 0x8473: isl88_up_pending = true; break; // kas
            // case 0x84F3: isl88_up_pending = true; break; // kas
//
            // case 0x04EE: isl88_up_pending = true; break; // kas
            // case 0x80EE: isl88_up_pending = true; break; // kas
            // case 0x846E: isl88_up_pending = true; break; // kas
            // case 0x84EE: isl88_up_pending = true; break; // kas

            // case 0x04ED: isl88_up_pending = true; break; // kas
            // case 0x80ED: isl88_up_pending = true; break; // kas
            // case 0x846D: isl88_up_pending = true; break; // kas
            // case 0x84ED: isl88_up_pending = true; break; // kas
//
            // case 0x04EB: isl88_up_pending = true; break; // kas
            // case 0x80EB: isl88_up_pending = true; break; // kas
            // case 0x846B: isl88_up_pending = true; break; // kas
            // case 0x84EB: isl88_up_pending = true; break; // kas
//
            // case 0x04E7: isl88_up_pending = true; break; // kas
            // case 0x80E7: isl88_up_pending = true; break; // kas
            // case 0xS1: send_y_commit_n = false; ksek = "~"; break; // kas
            // case 0x84E7: isl88_up_pending = true; break; // kas

            // case 0x04DE: isl88_up_pending = true; break; // kas
            // case 0x80DE: isl88_up_pending = true; break; // kas
            // case 0x845E: isl88_up_pending = true; break; // kas
            // case 0x84DE: isl88_up_pending = true; break; // kas
//
            // case 0x04DD: isl88_up_pending = true; break; // kas
            // case 0x80DD: isl88_up_pending = true; break; // kas
            // case 0x845D: isl88_up_pending = true; break; // kas
            // case 0x84DD: isl88_up_pending = true; break; // kas
//
            // case 0x04DB: isl88_up_pending = true; break; // kas
            // case 0x80DB: isl88_up_pending = true; break; // kas
            // case 0x845B: isl88_up_pending = true; break; // kas
            // case 0x84DB: isl88_up_pending = true; break; // kas
//
            // case 0x04D7: isl88_up_pending = true; break; // kas
            // case 0x80D7: isl88_up_pending = true; break; // kas
            // case 0x8457: isl88_up_pending = true; break; // kas
            // case 0x84D7: isl88_up_pending = true; break; // kas

            // case 0xS1: send_y_commit_n = false; ksek = "Z";
            // case 0x80CF: isl88_up_pending = true; break; // kas
            // case 0x844F: isl88_up_pending = true; break; // kas
            // case 0x84CF: isl88_up_pending = true; break; // kas

            // case 0x04BE: isl88_up_pending = true; break; // kas
            // case 0x80BE: isl88_up_pending = true; break; // kas
            // case 0x843E: isl88_up_pending = true; break; // kas
            // case 0x84BE: isl88_up_pending = true; break; // kas
//
            // case 0x04BD: isl88_up_pending = true; break; // kas
            // case 0x80BD: isl88_up_pending = true; break; // kas
            // case 0x843D: isl88_up_pending = true; break; // kas
            // case 0x84BD: isl88_up_pending = true; break; // kas
//
            // case 0x04BB: isl88_up_pending = true; break; // kas
            // case 0x80BB: isl88_up_pending = true; break; // kas
            // case 0x843B: isl88_up_pending = true; break; // kas
            // case 0x84BB: isl88_up_pending = true; break; // kas

            // case 0x04B7: isl88_up_pending = true; break; // kas
            // case 0x80B7: isl88_up_pending = true; break; // kas
            // case 0x8437: isl88_up_pending = true; break; // kas
            // case 0x84B7: isl88_up_pending = true; break; // kas
//
            // case 0x04AF: isl88_up_pending = true; break; // kas
            // case 0x80AF: isl88_up_pending = true; break; // kas
            // case 0x842F: isl88_up_pending = true; break; // kas
            // case 0x84AF: isl88_up_pending = true; break; // kas
//
            // case 0x049F: isl88_up_pending = true; break; // kas
            // case 0x809F: isl88_up_pending = true; break; // kas
            // case 0x841F: isl88_up_pending = true; break; // kas
            // case 0x849F: isl88_up_pending = true; break; // kas


                // ******************************************************* //
                // send_06_nsd23  k2p = 3 keys from 0-6 bilo + sft/dot/num
                // ******************************************************* //

                // ******************************************************* //
                // send_06_nsd23  k2p = 4 keys from 0-6 bilo + sft/dot/num
                // ******************************************************* //

                // // //
            default: isl88_up_pending = true ; break; // kas
        }
        if(!isl88_up_pending) send_kk();
    }
    public void kas_8E(int arg_kas88bytes){
        isl88_up_pending = false;
        switch (arg_kas88bytes) {
            // ******************************************************* //
            // send_8E  k2p = 0 keys from 8-E bilo + dot/num
            // ******************************************************* //
            // send_8E : k2p=0,1
            // case 0x907F:  send_y_commit_n = false;ksek = "Q"; break; // kas

            // case 0x007F: isl88_up_pending = true; break; // kas
            // case 0x00FF: isl88_up_pending = true; break; // kas
            // case 0x807F: isl88_up_pending = true; break; // kas
            // case 0x80FF: isl88_up_pending = true; break; // kas

            case 0x017F:  kk = KeyEvent.KEYCODE_PERIOD; break; // kas
            case 0x817F:  kk = KeyEvent.KEYCODE_8; break; // kas
            // case 0xS1: send_y_commit_n = false; ksek = "?"; break; // kas
            // case 0x817F: isl88_up_pending = true; break; // kas
            // case 0x81FF: isl88_up_pending = true; break; // kas

            case 0x027F:  kk = KeyEvent.KEYCODE_TAB; break; // kas
            case 0x827F:  kk = KeyEvent.KEYCODE_9; break; // kas
            // case 0xS1: send_y_commit_n = false; ksek = "+"; break; // kas
            // case 0x827F: isl88_up_pending = true; break; // kas
            // case 0x82FF: isl88_up_pending = true; break; // kas

            // case 0xS1: send_y_commit_n = false; ksek = "_"; break; // kas
            // case 0xS1: send_y_commit_n = false; ksek = "[]"; break; // kas
            // case 0xS1: send_y_commit_n = false; ksek = "L"; break; // kas
            // case 0x84FF: isl88_up_pending = true; break; // kas

            case 0x087F:  kk = KeyEvent.KEYCODE_DEL; break; // kas
//            case 0x08FF: send_y_commit_n = false; ksek = "_"; break; // kas
            // case 0x887F:  send_y_commit_n = false;ksek = "J"; break; // kas
            // case 0x88FF: isl88_up_pending = true; break; // kas

             case 0x107F: kk = KeyEvent.KEYCODE_ENTER; break; // kas
            // case 0xS1: send_y_commit_n = false; ksek = "*"; break; // kas
            // case 0x907F: isl88_up_pending = true; break; // kas
            // case 0x90FF: isl88_up_pending = true; break; // kas

            case 0x207F:  kk = KeyEvent.KEYCODE_FORWARD_DEL; break; // kas
            // case 0xS1: send_y_commit_n = false; ksek = "F"; break; // kas
            // case 0xS1: send_y_commit_n = false; ksek = "W" ; break; // kas
            // case 0xA0FF: isl88_up_pending = true; break; // kas

//            case 0x407F:  send_y_commit_n = false;ksek = "#"; break; // kas
            case 0xC07F: kk = KeyEvent.KEYCODE_X; break; // kas
//            case 0x40FF: send_y_commit_n = false; ksek = "|"; break; // kas
            case 0xC0FF: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas

            // send_8E : k2p=2
            case 0x037F: kk = KeyEvent.KEYCODE_COMMA; break; // kas
            // case 0xS1: send_y_commit_n = false; ksek = "_"; break; // kas
            // case 0x837F: isl88_up_pending = true; break; // kas
            // case 0x83FF: isl88_up_pending = true; break; // kas

            // case 0x057F: isl88_up_pending = true; break; // kas
            // case 0xS1: send_y_commit_n = false; ksek = ":"; break; // kas
            // case 0x857F: isl88_up_pending = true; break; // kas
            // case 0x85FF: isl88_up_pending = true; break; // kas

            // case 0x067F: isl88_up_pending = true; break; // kas
            case 0x06FF: kk = KeyEvent.KEYCODE_L; break; // kas
            // case 0x867F: isl88_up_pending = true; break; // kas
            // case 0x86FF: isl88_up_pending = true; break; // kas

            // case 0xS1: send_y_commit_n = false; ksek = ":" ; break; // kas
            case 0x09FF: kk = KeyEvent.KEYCODE_EQUALS; break; // kas
            // case 0x897F: isl88_up_pending = true; break; // kas
            // case 0x89FF: isl88_up_pending = true; break; // kas

            // case 0x0A7F: isl88_up_pending = true; break; // kas
            // case 0xS1: send_y_commit_n = false; ksek = "~"; break; // kas
            // case 0x8A7F: isl88_up_pending = true; break; // kas
            // case 0x8AFF: isl88_up_pending = true; break; // kas

            case 0x0C7F: kk = KeyEvent.KEYCODE_GRAVE; break; // kas
            // case 0x0CFF: isl88_up_pending = true; break; // kas
            // case 0x8C7F: isl88_up_pending = true; break; // kas
            // case 0x8CFF: isl88_up_pending = true; break; // kas

            // case 0x117F: isl88_up_pending = true; break; // kas
            // case 0xS1: send_y_commit_n = false; ksek = "#"; break; // kas
            // case 0x917F: isl88_up_pending = true; break; // kas
            // case 0x91FF: isl88_up_pending = true; break; // kas

            // case 0x127F: isl88_up_pending = true; break; // kas
            // case 0xS1: send_y_commit_n = false; ksek = "%"; break; // kas
            // case 0x927F: isl88_up_pending = true; break; // kas
            // case 0x92FF: isl88_up_pending = true; break; // kas

            // case 0xS1: send_y_commit_n = false; ksek = "\"\"" ; break; // kas
            // case 0xS1: send_y_commit_n = false; ksek = "|"; break; // kas
            // case 0x947F: isl88_up_pending = true; break; // kas
            // case 0x94FF: isl88_up_pending = true; break; // kas

            case 0x187F: kk = KeyEvent.KEYCODE_APOSTROPHE; break; // kas
            case 0x18FF: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas
            // case 0x987F: isl88_up_pending = true; break; // kas
            // case 0x98FF: isl88_up_pending = true; break; // kas

            // case 0xS1: send_y_commit_n = false; ksek = "*" ; break; // kas
            // case 0xS1: send_y_commit_n = false; ksek = "Z"; break; // kas
            // case 0xA17F: isl88_up_pending = true; break; // kas
            // case 0xA1FF: isl88_up_pending = true; break; // kas

            // case 0x227F: isl88_up_pending = true; break; // kas
            // case 0x22FF: isl88_up_pending = true; break; // kas
            // case 0xA27F: isl88_up_pending = true; break; // kas
            // case 0xA2FF: isl88_up_pending = true; break; // kas

            // case 0x247F: isl88_up_pending = true; break; // kas
            // case 0x24FF: isl88_up_pending = true; break; // kas
            // case 0xA47F: isl88_up_pending = true; break; // kas
            // case 0xA4FF: isl88_up_pending = true; break; // kas

            case 0x287F: kk = KeyEvent.KEYCODE_EQUALS; break; // kas
            // case 0x28FF: isl88_up_pending = true; break; // kas
            // case 0xA87F: isl88_up_pending = true; break; // kas
            // case 0xA8FF: isl88_up_pending = true; break; // kas

            // case 0x307F: isl88_up_pending = true; break; // kas
            // case 0x30FF: isl88_up_pending = true; break; // kas
            // case 0xB07F: isl88_up_pending = true; break; // kas
            // case 0xB0FF: isl88_up_pending = true; break; // kas

            // case 0x417F: isl88_up_pending = true; break; // kas
            // case 0x41FF: isl88_up_pending = true; break; // kas
            // case 0xC17F: isl88_up_pending = true; break; // kas
            // case 0xC1FF: isl88_up_pending = true; break; // kas

            // case 0x427F: isl88_up_pending = true; break; // kas
            // case 0x42FF: isl88_up_pending = true; break; // kas
            // case 0xC27F: isl88_up_pending = true; break; // kas
            // case 0xC2FF: isl88_up_pending = true; break; // kas

            // case 0x447F: isl88_up_pending = true; break; // kas
            // case 0x44FF: isl88_up_pending = true; break; // kas
            // case 0xC47F: isl88_up_pending = true; break; // kas
            // case 0xC4FF: isl88_up_pending = true; break; // kas

            // case 0x487F: isl88_up_pending = true; break; // kas
            // case 0x48FF: isl88_up_pending = true; break; // kas
            // case 0xC87F: isl88_up_pending = true; break; // kas
            // case 0xC8FF: isl88_up_pending = true; break; // kas

            // case 0x507F: isl88_up_pending = true; break; // kas
            // case 0x50FF: isl88_up_pending = true; break; // kas
            // case 0xD07F: isl88_up_pending = true; break; // kas
            // case 0xD0FF: isl88_up_pending = true; break; // kas

            case 0x607F: kk = KeyEvent.KEYCODE_R; break; // kas
            // case 0x60FF: isl88_up_pending = true; break; // kas
            // case 0xE07F: isl88_up_pending = true; break; // kas
            // case 0xE0FF: isl88_up_pending = true; break; // kas

            // send_8E : k2p=3
            case 0x077F: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas
            // case 0x07FF: isl88_up_pending = true; break; // kas
            // case 0x877F: isl88_up_pending = true; break; // kas
            // case 0x87FF: isl88_up_pending = true; break; // kas

            case 0x0B7F: kk = KeyEvent.KEYCODE_SEMICOLON; break; // kas
            // case 0x0BFF: isl88_up_pending = true; break; // kas
            // case 0x8B7F: isl88_up_pending = true; break; // kas
            // case 0x8BFF: isl88_up_pending = true; break; // kas

            // case 0x0D7F: isl88_up_pending = true; break; // kas
            // case 0x0DFF: isl88_up_pending = true; break; // kas
            // case 0x8D7F: isl88_up_pending = true; break; // kas
            // case 0x8DFF: isl88_up_pending = true; break; // kas

            // case 0x0E7F: isl88_up_pending = true; break; // kas
            // case 0x0EFF: isl88_up_pending = true; break; // kas
            // case 0x8E7F: isl88_up_pending = true; break; // kas
            // case 0x8EFF: isl88_up_pending = true; break; // kas

            // case 0x137F: isl88_up_pending = true; break; // kas
            // case 0x13FF: isl88_up_pending = true; break; // kas
            // case 0x937F: isl88_up_pending = true; break; // kas
            // case 0x93FF: isl88_up_pending = true; break; // kas

            // case 0x157F: isl88_up_pending = true; break; // kas
            // case 0x15FF: isl88_up_pending = true; break; // kas
            // case 0x957F: isl88_up_pending = true; break; // kas
            // case 0x95FF: isl88_up_pending = true; break; // kas

            // case 0x167F: isl88_up_pending = true; break; // kas
            // case 0x16FF: isl88_up_pending = true; break; // kas
            // case 0x967F: isl88_up_pending = true; break; // kas
            // case 0x96FF: isl88_up_pending = true; break; // kas

            // case 0x197F: isl88_up_pending = true; break; // kas
            // case 0x19FF: isl88_up_pending = true; break; // kas
            // case 0x997F: isl88_up_pending = true; break; // kas
            // case 0x99FF: isl88_up_pending = true; break; // kas

            // case 0x1A7F: isl88_up_pending = true; break; // kas
            // case 0x1AFF: isl88_up_pending = true; break; // kas
            // case 0x9A7F: isl88_up_pending = true; break; // kas
            // case 0x9AFF: isl88_up_pending = true; break; // kas

            // case 0xS1: send_y_commit_n = false; ksek = "^" ; break; // kas
            // case 0x1CFF: isl88_up_pending = true; break; // kas
            // case 0x9C7F: isl88_up_pending = true; break; // kas
            // case 0x9CFF: isl88_up_pending = true; break; // kas

            case 0x237F: kk = KeyEvent.KEYCODE_COMMA; break; // kas
            // case 0x23FF: isl88_up_pending = true; break; // kas
            // case 0xA37F: isl88_up_pending = true; break; // kas
            // case 0xA3FF: isl88_up_pending = true; break; // kas

            // case 0x257F: isl88_up_pending = true; break; // kas
            // case 0x25FF: isl88_up_pending = true; break; // kas
            // case 0xA57F: isl88_up_pending = true; break; // kas
            // case 0xA5FF: isl88_up_pending = true; break; // kas

            case 0x267F: kk = KeyEvent.KEYCODE_LEFT_BRACKET; break; // kas
            // case 0x26FF: isl88_up_pending = true; break; // kas
            // case 0xA67F: isl88_up_pending = true; break; // kas
            // case 0xA6FF: isl88_up_pending = true; break; // kas

            // case 0xS1: send_y_commit_n = false; ksek = "|" ; break; // kas
            // case 0x29FF: isl88_up_pending = true; break; // kas
            // case 0xA97F: isl88_up_pending = true; break; // kas
            // case 0xA9FF: isl88_up_pending = true; break; // kas

            // case 0x2A7F: isl88_up_pending = true; break; // kas
            // case 0x2AFF: isl88_up_pending = true; break; // kas
            // case 0xAA7F: isl88_up_pending = true; break; // kas
            // case 0xAAFF: isl88_up_pending = true; break; // kas

            case 0x2C7F: kk = KeyEvent.KEYCODE_RIGHT_BRACKET; break; // kas
            // case 0x2CFF: isl88_up_pending = true; break; // kas
            // case 0xAC7F: isl88_up_pending = true; break; // kas
            // case 0xACFF: isl88_up_pending = true; break; // kas

            // case 0x317F: isl88_up_pending = true; break; // kas
            // case 0x31FF: isl88_up_pending = true; break; // kas
            // case 0xB17F: isl88_up_pending = true; break; // kas
            // case 0xB1FF: isl88_up_pending = true; break; // kas

            case 0x327F: kk = KeyEvent.KEYCODE_BACKSLASH; break; // kas
            // case 0x32FF: isl88_up_pending = true; break; // kas
            // case 0xB27F: isl88_up_pending = true; break; // kas
            // case 0xB2FF: isl88_up_pending = true; break; // kas

            // case 0xS1: send_y_commit_n = false; ksek = "~" ; break; // kas
            // case 0x34FF: isl88_up_pending = true; break; // kas
            // case 0xB47F: isl88_up_pending = true; break; // kas
            // case 0xB4FF: isl88_up_pending = true; break; // kas

            case 0x387F: kk = KeyEvent.KEYCODE_LEFT_BRACKET; break; // kas
            // case 0x38FF: isl88_up_pending = true; break; // kas
            // case 0xB87F: isl88_up_pending = true; break; // kas
            // case 0xB8FF: isl88_up_pending = true; break; // kas

            // case 0x437F: isl88_up_pending = true; break; // kas
            // case 0x43FF: isl88_up_pending = true; break; // kas
            // case 0xC37F: isl88_up_pending = true; break; // kas
            // case 0xC3FF: isl88_up_pending = true; break; // kas

            // case 0x457F: isl88_up_pending = true; break; // kas
            // case 0x45FF: isl88_up_pending = true; break; // kas
            // case 0xC57F: isl88_up_pending = true; break; // kas
            // case 0xC5FF: isl88_up_pending = true; break; // kas

            // case 0x467F: isl88_up_pending = true; break; // kas
            // case 0x46FF: isl88_up_pending = true; break; // kas
            // case 0xC67F: isl88_up_pending = true; break; // kas
            // case 0xC6FF: isl88_up_pending = true; break; // kas

            // case 0x497F: isl88_up_pending = true; break; // kas
            // case 0x49FF: isl88_up_pending = true; break; // kas
            // case 0xC97F: isl88_up_pending = true; break; // kas
            // case 0xC9FF: isl88_up_pending = true; break; // kas

            // case 0x4A7F: isl88_up_pending = true; break; // kas
            // case 0x4AFF: isl88_up_pending = true; break; // kas
            // case 0xCA7F: isl88_up_pending = true; break; // kas
            // case 0xCAFF: isl88_up_pending = true; break; // kas

            // case 0x4C7F: isl88_up_pending = true; break; // kas
            // case 0x4CFF: isl88_up_pending = true; break; // kas
            // case 0xCC7F: isl88_up_pending = true; break; // kas
            // case 0xCCFF: isl88_up_pending = true; break; // kas

            case 0x517F: kk = KeyEvent.KEYCODE_L; break; // kas
            // case 0x51FF: isl88_up_pending = true; break; // kas
            // case 0xD17F: isl88_up_pending = true; break; // kas
            // case 0xD1FF: isl88_up_pending = true; break; // kas

            // case 0x527F: isl88_up_pending = true; break; // kas
            // case 0x52FF: isl88_up_pending = true; break; // kas
            // case 0xD27F: isl88_up_pending = true; break; // kas
            // case 0xD2FF: isl88_up_pending = true; break; // kas

            // case 0x547F: isl88_up_pending = true; break; // kas
            // case 0x54FF: isl88_up_pending = true; break; // kas
            // case 0xD47F: isl88_up_pending = true; break; // kas
            // case 0xD4FF: isl88_up_pending = true; break; // kas

            // case 0x587F: isl88_up_pending = true; break; // kas
            // case 0x58FF: isl88_up_pending = true; break; // kas
            // case 0xD87F: isl88_up_pending = true; break; // kas
            // case 0xD8FF: isl88_up_pending = true; break; // kas

            case 0x617F: kk = KeyEvent.KEYCODE_COMMA; break; // kas
            // case 0x61FF: isl88_up_pending = true; break; // kas
            // case 0xE17F: isl88_up_pending = true; break; // kas
            // case 0xE1FF: isl88_up_pending = true; break; // kas

            // case 0xS1: send_y_commit_n = false; ksek = "*" ; break; // kas
            // case 0x62FF: isl88_up_pending = true; break; // kas
            // case 0xE27F: isl88_up_pending = true; break; // kas
            // case 0xE2FF: isl88_up_pending = true; break; // kas

            case 0x647F: kk = KeyEvent.KEYCODE_SLASH; break; // only 2nd slash this is this // kas
            // case 0x64FF: isl88_up_pending = true; break; // kas
            // case 0xS1: send_y_commit_n = false; ksek = "%" ; break; // kas
            // case 0xE4FF: isl88_up_pending = true; break; // kas

            // case 0x687F: isl88_up_pending = true; break; // kas
            // case 0x68FF: isl88_up_pending = true; break; // kas
            // case 0xE87F: isl88_up_pending = true; break; // kas
            // case 0xE8FF: isl88_up_pending = true; break; // kas

            // case 0x707F: isl88_up_pending = true; break; // kas
            // case 0x70FF: isl88_up_pending = true; break; // kas
            // case 0xF07F: isl88_up_pending = true; break; // kas
            // case 0xF0FF: isl88_up_pending = true; break; // kas

            // send_8E : k2p=4
            // case 0x0F7F: isl88_up_pending = true; break; // kas
            // case 0x0FFF: isl88_up_pending = true; break; // kas
            // case 0x8F7F: isl88_up_pending = true; break; // kas
            // case 0x8FFF: isl88_up_pending = true; break; // kas

            // case 0x177F: isl88_up_pending = true; break; // kas
            // case 0x17FF: isl88_up_pending = true; break; // kas
            // case 0x977F: isl88_up_pending = true; break; // kas
            // case 0x97FF: isl88_up_pending = true; break; // kas

            // case 0x1B7F: isl88_up_pending = true; break; // kas
            // case 0x1BFF: isl88_up_pending = true; break; // kas
            // case 0x9B7F: isl88_up_pending = true; break; // kas
            // case 0x9BFF: isl88_up_pending = true; break; // kas

            // case 0x1D7F: isl88_up_pending = true; break; // kas
            // case 0x1DFF: isl88_up_pending = true; break; // kas
            // case 0x9D7F: isl88_up_pending = true; break; // kas
            // case 0x9DFF: isl88_up_pending = true; break; // kas

            // case 0x1E7F: isl88_up_pending = true; break; // kas
            // case 0x1EFF: isl88_up_pending = true; break; // kas
            // case 0x9E7F: isl88_up_pending = true; break; // kas
            // case 0x9EFF: isl88_up_pending = true; break; // kas

            // case 0x277F: isl88_up_pending = true; break; // kas
            // case 0x27FF: isl88_up_pending = true; break; // kas
            // case 0xA77F: isl88_up_pending = true; break; // kas
            // case 0xA7FF: isl88_up_pending = true; break; // kas

            // case 0x2B7F: isl88_up_pending = true; break; // kas
            // case 0x2BFF: isl88_up_pending = true; break; // kas
            // case 0xAB7F: isl88_up_pending = true; break; // kas
            // case 0xABFF: isl88_up_pending = true; break; // kas

            // case 0xS1: send_y_commit_n = false; ksek = "?"; break; // kas
            // case 0x2DFF: isl88_up_pending = true; break; // kas
            // case 0xAD7F: isl88_up_pending = true; break; // kas
            // case 0xADFF: isl88_up_pending = true; break; // kas

            // case 0x2E7F: isl88_up_pending = true; break; // kas
            // case 0x2EFF: isl88_up_pending = true; break; // kas
            // case 0xAE7F: isl88_up_pending = true; break; // kas
            // case 0xAEFF: isl88_up_pending = true; break; // kas

            // case 0x337F: isl88_up_pending = true; break; // kas
            // case 0x33FF: isl88_up_pending = true; break; // kas
            // case 0xB37F: isl88_up_pending = true; break; // kas
            // case 0xB3FF: isl88_up_pending = true; break; // kas

            // case 0x357F: isl88_up_pending = true; break; // kas
            // case 0x35FF: isl88_up_pending = true; break; // kas
            // case 0xB57F: isl88_up_pending = true; break; // kas
            // case 0xB5FF: isl88_up_pending = true; break; // kas

            // case 0x367F: isl88_up_pending = true; break; // kas
            // case 0x36FF: isl88_up_pending = true; break; // kas
            // case 0xB67F: isl88_up_pending = true; break; // kas
            // case 0xB6FF: isl88_up_pending = true; break; // kas

            // case 0x397F: isl88_up_pending = true; break; // kas
            // case 0x39FF: isl88_up_pending = true; break; // kas
            // case 0xB97F: isl88_up_pending = true; break; // kas
            // case 0xB9FF: isl88_up_pending = true; break; // kas

            // case 0x3A7F: isl88_up_pending = true; break; // kas
            // case 0x3AFF: isl88_up_pending = true; break; // kas
            // case 0xBA7F: isl88_up_pending = true; break; // kas
            // case 0xBAFF: isl88_up_pending = true; break; // kas

            // case 0x3C7F: isl88_up_pending = true; break; // kas
            // case 0x3CFF: isl88_up_pending = true; break; // kas
            // case 0xBC7F: isl88_up_pending = true; break; // kas
            // case 0xBCFF: isl88_up_pending = true; break; // kas

            // case 0x477F: isl88_up_pending = true; break; // kas
            // case 0x47FF: isl88_up_pending = true; break; // kas
            // case 0xC77F: isl88_up_pending = true; break; // kas
            // case 0xC7FF: isl88_up_pending = true; break; // kas

            // case 0x4B7F: isl88_up_pending = true; break; // kas
            // case 0x4BFF: isl88_up_pending = true; break; // kas
            // case 0xCB7F: isl88_up_pending = true; break; // kas
            // case 0xCBFF: isl88_up_pending = true; break; // kas

            // case 0x4D7F: isl88_up_pending = true; break; // kas
            // case 0x4DFF: isl88_up_pending = true; break; // kas
            // case 0xCD7F: isl88_up_pending = true; break; // kas
            // case 0xCDFF: isl88_up_pending = true; break; // kas

            // case 0x4E7F: isl88_up_pending = true; break; // kas
            // case 0x4EFF: isl88_up_pending = true; break; // kas
            // case 0xCE7F: isl88_up_pending = true; break; // kas
            // case 0xCEFF: isl88_up_pending = true; break; // kas

            // case 0x537F: isl88_up_pending = true; break; // kas
            // case 0x53FF: isl88_up_pending = true; break; // kas
            // case 0xD37F: isl88_up_pending = true; break; // kas
            // case 0xD3FF: isl88_up_pending = true; break; // kas

            // case 0x557F: isl88_up_pending = true; break; // kas
            // case 0x55FF: isl88_up_pending = true; break; // kas
            // case 0xD57F: isl88_up_pending = true; break; // kas
            // case 0xD5FF: isl88_up_pending = true; break; // kas

            // case 0x567F: isl88_up_pending = true; break; // kas
            // case 0x56FF: isl88_up_pending = true; break; // kas
            // case 0xD67F: isl88_up_pending = true; break; // kas
            // case 0xD6FF: isl88_up_pending = true; break; // kas

            // case 0x597F: isl88_up_pending = true; break; // kas
            // case 0x59FF: isl88_up_pending = true; break; // kas
            // case 0xD97F: isl88_up_pending = true; break; // kas
            // case 0xD9FF: isl88_up_pending = true; break; // kas

            // case 0x5A7F: isl88_up_pending = true; break; // kas
            // case 0x5AFF: isl88_up_pending = true; break; // kas
            // case 0xDA7F: isl88_up_pending = true; break; // kas
            // case 0xDAFF: isl88_up_pending = true; break; // kas

            // case 0x5C7F: isl88_up_pending = true; break; // kas
            // case 0x5CFF: isl88_up_pending = true; break; // kas
            // case 0xDC7F: isl88_up_pending = true; break; // kas
            // case 0xDCFF: isl88_up_pending = true; break; // kas

            case 0x637F: kk = KeyEvent.KEYCODE_O; break; // kas
            // case 0x63FF: isl88_up_pending = true; break; // kas
            // case 0xE37F: isl88_up_pending = true; break; // kas
            // case 0xE3FF: isl88_up_pending = true; break; // kas

            // case 0x657F: isl88_up_pending = true; break; // kas
            // case 0x65FF: isl88_up_pending = true; break; // kas
            // case 0xE57F: isl88_up_pending = true; break; // kas
            // case 0xE5FF: isl88_up_pending = true; break; // kas

            // case 0x667F: isl88_up_pending = true; break; // kas
            // case 0x66FF: isl88_up_pending = true; break; // kas
            // case 0xE67F: isl88_up_pending = true; break; // kas
            // case 0xE6FF: isl88_up_pending = true; break; // kas

            // case 0xS1: send_y_commit_n = false; ksek = "!" ; break; // kas
            // case 0x69FF: isl88_up_pending = true; break; // kas
            // case 0xE97F: isl88_up_pending = true; break; // kas
            // case 0xE9FF: isl88_up_pending = true; break; // kas

            // case 0x6A7F: isl88_up_pending = true; break; // kas
            // case 0x6AFF: isl88_up_pending = true; break; // kas
            // case 0xEA7F: isl88_up_pending = true; break; // kas
            // case 0xEAFF: isl88_up_pending = true; break; // kas

            // case 0x6C7F: isl88_up_pending = true; break; // kas
            // case 0x6CFF: isl88_up_pending = true; break; // kas
            // case 0xEC7F: isl88_up_pending = true; break; // kas
            // case 0xECFF: isl88_up_pending = true; break; // kas

            // case 0x717F: isl88_up_pending = true; break; // kas
            // case 0x71FF: isl88_up_pending = true; break; // kas
            // case 0xF17F: isl88_up_pending = true; break; // kas
            // case 0xF1FF: isl88_up_pending = true; break; // kas

            // case 0x727F: isl88_up_pending = true; break; // kas
            // case 0x72FF: isl88_up_pending = true; break; // kas
            // case 0xF27F: isl88_up_pending = true; break; // kas
            // case 0xF2FF: isl88_up_pending = true; break; // kas

            // case 0x747F: isl88_up_pending = true; break; // kas
            // case 0x74FF: isl88_up_pending = true; break; // kas
            // case 0xF47F: isl88_up_pending = true; break; // kas
            // case 0xF4FF: isl88_up_pending = true; break; // kas

            // case 0xS1: send_y_commit_n = false; ksek = "?"; break; // kas
            // case 0x78FF: isl88_up_pending = true; break; // kas
            // case 0xF87F: isl88_up_pending = true; break; // kas
            // case 0xF8FF: isl88_up_pending = true; brea  k;

            default: isl88_up_pending = true ;
        }
        if(!isl88_up_pending) send_kk();
    }
    public void send_knt_alt_sft(){
        int num88bytes =  l88bytes;
        if ( mKeyboardSwitcher.is_nm_lok() && ((num88bytes & 0x0480) == 0)) {num88bytes =  num88bytes ^ 0x8000 ; }
        if (mKeyboardSwitcher.is_sft_lok() && ((num88bytes & 0x8080) == 0)) {num88bytes =  num88bytes ^ 0x0400 ; }
        if (mKeyboardSwitcher.is_go_lok() && ((num88bytes & 0x0200) == 0)) {num88bytes =  num88bytes ^ 0x0100 ; }
        if (isl88_up_pending && (num88bytes & 0x1800) > 0) {
            if ((l88bytes & 0x1000) == 0x1000) meta = meta | KeyEvent.META_CTRL_ON;
            if ((l88bytes & 0x0800) == 0x0800) meta = meta | KeyEvent.META_ALT_ON;
            if ((l88bytes & 0x0400) == 0x0400) meta = meta | KeyEvent.META_SHIFT_ON;
            kk = KeyEvent.KEYCODE_UNKNOWN ; ksek = null; send_y_commit_n = true;
            num88bytes = l88bytes & 0xE3FF;
            if(isl88_up_pending) kas_muv_go_num_fn_minu_sft(num88bytes);
            if(isl88_up_pending) kas_num_fn_minu_sft(num88bytes);
            if(isl88_up_pending) kas_06(num88bytes);
            if(isl88_up_pending) kas_06_nsd23(num88bytes);
            if(isl88_up_pending) kas_8E(num88bytes);
        } else { isl88_up_pending = true; }
    }

    private static final Pattern NUMBER_RE = Pattern.compile("(\\d+).*");
    static int getHeight(SharedPreferences prefs, String prefName, String defVal) {
        int val = getPrefInt(prefs, prefName, defVal); if (val < 15) val = 15;if (val > 75) val = 75;return val;
    }
    static int getPrefInt(SharedPreferences prefs, String prefName, String defStr) {
        int defVal = getIntFromString(defStr, 0);return getPrefInt(prefs, prefName, defVal);
    }
    static int getIntFromString(String val, int defVal) {
        Matcher num = NUMBER_RE.matcher(val);if (!num.matches()) return defVal;return Integer.parseInt(num.group(1));
    }
    static int getPrefInt(SharedPreferences prefs, String prefName, int defVal) {
        String prefVal = prefs.getString(prefName, Integer.toString(defVal));return getIntFromString(prefVal, defVal);
    }

}
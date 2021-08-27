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
//    private boolean m_multipointer_on = true;
    private boolean is_ptrup_ev; private int skey_flags = 0 ;
    private final int skey_flag_sft = 0 ;
    private final int skey_flag_go = 1 ;
//    private boolean m_is_rect = true;

    private AlertDialog mOptionsDialog;
    KeyboardSwitcher mKeyboardSwitcher; private int mNumKeyboardModes = 2;
    /////////
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
                if(temp == '[' || temp == '(' || temp == '{' || temp == '"') {
                    ic.sendKeyEvent(new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,0));
                    ic.sendKeyEvent(new KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT,0));
                }
            }
            prev_kk = kk ; prev_ksek = ksek ; kk = KeyEvent.KEYCODE_UNKNOWN ; ksek = null;
            isl88_up_pending = false ;
        }
    }
    ///// handling segbytes
    public void toogle_handler(){
        if(isl88_up_pending) {
            isl88_up_pending = false;
            switch (l88bytes) {
                case 0x807F: mKeyboardSwitcher.toggle_nmlok(); break;
                default: isl88_up_pending = true ;
            }
        }
    }
    public boolean onText(int argl88bytes) { ic = getCurrentInputConnection();if (ic == null) return true;
        ic.beginBatchEdit();isl88_up_pending = true;l88bytes = argl88bytes;  kk = KeyEvent.KEYCODE_UNKNOWN ; ksek = null;

        if (isl88_up_pending) toogle_handler(); //807F

        if (isl88_up_pending) send_num(); // k2p = 1

        if (isl88_up_pending) send_06();
//        if (isl88_up_pending) dot_06();
//        if (isl88_up_pending) sft_06();
        if (isl88_up_pending) send_8E();
        if (isl88_up_pending) dot_8E();
        if (isl88_up_pending) sft_dot_06();
        if (isl88_up_pending) send_special();
        if (isl88_up_pending && (l88bytes & 0x04FF) == 0x047F) send_sft8F();

        if (isl88_up_pending && (l88bytes & 0x1C00) == 0x1C00) send_knt_alt_sft();
        if (isl88_up_pending && (l88bytes & 0x1800) == 0x1800) send_knt_alt();
        if (isl88_up_pending && (l88bytes & 0x1400) == 0x1400) send_knt_sft();
        if (isl88_up_pending && (l88bytes & 0x0C00) == 0x0C00) send_alt_sft();
        if (isl88_up_pending && (l88bytes & 0x1000) == 0x1000) send_knt();
        if (isl88_up_pending && (l88bytes & 0x0800) == 0x0800) send_alt();

        if (isl88_up_pending && (l88bytes & 0x4000) == 0x4000) send_minu();
        if (isl88_up_pending && (l88bytes & 0x0100) == 0x0100) send_go();
        if (isl88_up_pending && (l88bytes & 0x0200) != 0) send_muv();
        if (isl88_up_pending) send_fn_on();
        if (isl88_up_pending) send_fn_oph();
        ic.endBatchEdit();
        return isl88_up_pending;
    }

    public void send_special(){
        isl88_up_pending = false ; meta = 0 ; kk = KeyEvent.KEYCODE_UNKNOWN ; ksek = null; send_y_commit_n = true;
        switch (l88bytes) {
            case 0x0277: kk = KeyEvent.KEYCODE_PAGE_UP; break;
            case 0x027E: kk = KeyEvent.KEYCODE_PAGE_DOWN; break;
            case 0x023F: kk = KeyEvent.KEYCODE_MOVE_HOME; break;
            case 0x027D: kk = KeyEvent.KEYCODE_MOVE_END; break;
            default: isl88_up_pending = true ;
        }
        if(!isl88_up_pending) send_kk();
    }
    public void send_num(){
        isl88_up_pending = false ; meta = 0 ; kk = KeyEvent.KEYCODE_UNKNOWN ; ksek = null; send_y_commit_n = true;
        final int num88bytes ;
        if (mKeyboardSwitcher.is_nm_lok() && ((l88bytes & 0x0480) == 0)) {num88bytes =  l88bytes ^ 0x8000 ; } else { num88bytes =  l88bytes ; }
        switch (num88bytes) {
            case 0x017F:  kk = KeyEvent.KEYCODE_ENTER; break; case 0x817F:  kk = KeyEvent.KEYCODE_8; break;
            case 0x027F:  kk = KeyEvent.KEYCODE_TAB; break; case 0x827F:  kk = KeyEvent.KEYCODE_9; break;
            case 0x047F: send_y_commit_n = false; ksek = "_"; break; case 0x847F: send_y_commit_n = false; ksek = "L"; break;
            case 0x087F:  kk = KeyEvent.KEYCODE_DEL; break; case 0x887F:  send_y_commit_n = false;ksek = "J"; break;
            case 0x107F:  kk = KeyEvent.KEYCODE_PERIOD; break; case 0x907F:  send_y_commit_n = false;ksek = "Q"; break;
            // special case for phen F , num is oph here
            case 0x207F:  kk = KeyEvent.KEYCODE_FORWARD_DEL; break; case 0xA07F: send_y_commit_n = false;ksek = "W"; break;
            case 0x407F:  send_y_commit_n = false;ksek = "#"; break; case 0xC07F: send_y_commit_n = false;ksek = "X"; break;
            default: isl88_up_pending = true; break;
        }
        if(!isl88_up_pending) send_kk();
    }
    public void send_06(){
        isl88_up_pending = false ; meta = 0 ; kk = KeyEvent.KEYCODE_UNKNOWN ; ksek = null; send_y_commit_n = true;
        final int num88bytes ;
        if (mKeyboardSwitcher.is_nm_lok() && ((l88bytes & 0x0480) == 0)) {num88bytes =  l88bytes ^ 0x8000 ; } else { num88bytes =  l88bytes ; }
        switch (num88bytes) {
            // send_06 : yllo key k2p = 0 keys from 0-6 + sft/dot/num
            case 0x00FF:  kk = KeyEvent.KEYCODE_SPACE; break;
            case 0x80FF:  kk = KeyEvent.KEYCODE_7; break; case 0x04FF:  send_y_commit_n = false; ksek = "[]"; break;

            // send_06 : k2p = 1 keys from 0-6 bilo + sft/dot/num
            ////////////////////////
            case 0x007E:  send_y_commit_n = false;ksek = "A"; break; case 0x00FE: kk = KeyEvent.KEYCODE_PERIOD; break;
            case 0x807E:  kk = KeyEvent.KEYCODE_0; break; case 0x047E:  send_y_commit_n = false; ksek = "&"; break;

            case 0x007D:  kk = KeyEvent.KEYCODE_E; break; case 0x00FD: kk = KeyEvent.KEYCODE_ESCAPE; break;
            case 0x807D:  kk = KeyEvent.KEYCODE_1; break; case 0x047D: send_y_commit_n = false; ksek = "E"; break;

            case 0x00FB: send_y_commit_n = false; ksek = "~"; break;
            case 0x007B:  kk = KeyEvent.KEYCODE_C; break; case 0x807B:  kk = KeyEvent.KEYCODE_2; break;

            case 0x00F7: kk = KeyEvent.KEYCODE_X; break;
            case 0x0077:  kk = KeyEvent.KEYCODE_U; break; case 0x8077:  kk = KeyEvent.KEYCODE_3; break;

            case 0x00EF: send_y_commit_n = false; ksek = "D"; break;
            case 0x006F:  kk = KeyEvent.KEYCODE_A; break; case 0x806F:  kk = KeyEvent.KEYCODE_4; break;

            case 0x00DF: kk = KeyEvent.KEYCODE_MINUS; break;
            case 0x005F:  kk = KeyEvent.KEYCODE_O; break; case 0x805F:  kk = KeyEvent.KEYCODE_5; break;

            case 0x003F:  kk = KeyEvent.KEYCODE_I; break; case 0x803F:  kk = KeyEvent.KEYCODE_6; break;
            case 0x00BF: send_y_commit_n = false; ksek = "T"; break;

            // send_06 : k2p = 2 keys from 0-6 bilo + sft/dot/num
            ////////////////////////
            case 0x007C: kk = KeyEvent.KEYCODE_P; break; case 0x807C: send_y_commit_n = false; ksek = "+"; break;
            case 0x00FC: kk = KeyEvent.KEYCODE_COMMA; break;
            
            case 0x007A: kk = KeyEvent.KEYCODE_SEMICOLON; break; case 0x807A: send_y_commit_n = false; ksek = ":"; break;
            case 0x00FA: send_y_commit_n = false; ksek = ":"; break;
            
            case 0x0079: send_y_commit_n = false; ksek = "{}"; break; case 0x8079: send_y_commit_n = false; ksek = "E"; break;
            case 0x00F9: send_y_commit_n = false; ksek = "|"; break;
            
            case 0x0076: kk = KeyEvent.KEYCODE_H; break; case 0x8076: kk = KeyEvent.KEYCODE_EQUALS; break;
            case 0x00F6: kk = KeyEvent.KEYCODE_EQUALS; break;
            
            case 0x0075: kk = KeyEvent.KEYCODE_Y; break; case 0x8075: send_y_commit_n = false; ksek = ":"; break;
            case 0x00F5: kk = KeyEvent.KEYCODE_SEMICOLON; break;
            
            case 0x0073: kk = KeyEvent.KEYCODE_B; break; case 0x8073: kk = KeyEvent.KEYCODE_GRAVE; break;
            case 0x00F3: kk = KeyEvent.KEYCODE_GRAVE; break;
            
            case 0x006E: send_y_commit_n = false; ksek = "T"; break; case 0x806E: send_y_commit_n = false; ksek = ":"; break;
            case 0x00EE: send_y_commit_n = false; ksek = ":"; break;
            
            case 0x006D: kk = KeyEvent.KEYCODE_Z; break; case 0x806D: send_y_commit_n = false; ksek = ":"; break;
            case 0x00ED: send_y_commit_n = false; ksek = ":"; break;
            
            case 0x006B: send_y_commit_n = false; ksek = "\"\""; break; case 0x806B: kk = KeyEvent.KEYCODE_PERIOD; break;
            case 0x00EB: send_y_commit_n = false; ksek = "_"; break;
            
            case 0x0067: kk = KeyEvent.KEYCODE_D; break; case 0x8067: kk = KeyEvent.KEYCODE_APOSTROPHE; break;
            case 0x00E7: kk = KeyEvent.KEYCODE_APOSTROPHE; break;
            
            case 0x005E: kk = KeyEvent.KEYCODE_M; break; case 0x00DE: send_y_commit_n = false; ksek = "*"; break;
            case 0x805E: kk = KeyEvent.KEYCODE_EQUALS; break; case 0x045E: send_y_commit_n = false; ksek = "M"; break;


            case 0x005D: kk = KeyEvent.KEYCODE_L; break; case 0x805D: send_y_commit_n = false; ksek = "[]"; break;
            case 0x00DD: kk = KeyEvent.KEYCODE_COMMA; break;

            case 0x005B: kk = KeyEvent.KEYCODE_G; break; case 0x805B: send_y_commit_n = false; ksek = ">"; break;
            case 0x00DB: kk = KeyEvent.KEYCODE_COMMA; break;

            case 0x0057: kk = KeyEvent.KEYCODE_V; break; case 0x8057: kk = KeyEvent.KEYCODE_EQUALS; break;
            case 0x00D7: kk = KeyEvent.KEYCODE_EQUALS; break;

            case 0x004F: kk = KeyEvent.KEYCODE_J; break; case 0x804F: send_y_commit_n = false; ksek = "J"; break;
            case 0x00CF: kk = KeyEvent.KEYCODE_APOSTROPHE; break;

            case 0x003E: kk = KeyEvent.KEYCODE_Q; break; case 0x803E: send_y_commit_n = false; ksek = "Q"; break;
            case 0x00BE: kk = KeyEvent.KEYCODE_COMMA; break;

            case 0x003D: send_y_commit_n = false; ksek = "T"; break; case 0x803D: kk = KeyEvent.KEYCODE_PERIOD; break;
            case 0x00BD: send_y_commit_n = false; ksek = "_"; break;

            case 0x003B: kk = KeyEvent.KEYCODE_S; break; case 0x803B: kk = KeyEvent.KEYCODE_MINUS; break;
            case 0x00BB: send_y_commit_n = false; ksek = "$"; break;

            case 0x0037: kk = KeyEvent.KEYCODE_Y; break; case 0x8037: send_y_commit_n = false; ksek = "Y"; break;
            case 0x00B7: send_y_commit_n = false; ksek = ":"; break;

            case 0x002F: send_y_commit_n = false; ksek = "()"; break; case 0x802F: send_y_commit_n = false; ksek = "|"; break;
            case 0x00AF: send_y_commit_n = false; ksek = "|"; break;

            case 0x001F: kk = KeyEvent.KEYCODE_R; break; case 0x801F: kk = KeyEvent.KEYCODE_COMMA; break;
            case 0x009F: send_y_commit_n = false; ksek = "~"; break;

            // send_06 : k2p = 3 keys from 0-6 bilo + sft/dot/num
            ////////////////////////
            case 0x0078: kk = KeyEvent.KEYCODE_F; break; case 0x8078: send_y_commit_n = false; ksek = "!"; break;
            case 0x00F8: send_y_commit_n = false; ksek = "!"; break;

            case 0x0074: kk = KeyEvent.KEYCODE_SEMICOLON; break; case 0x8074: send_y_commit_n = false; ksek = "()"; break;
            case 0x00F4: send_y_commit_n = false; ksek = "{}"; break;

            case 0x0072: kk = KeyEvent.KEYCODE_H; break; case 0x8072: send_y_commit_n = false; ksek = "{}"; break;
            case 0x00F2: send_y_commit_n = false; ksek = "()"; break;

            case 0x0071: kk = KeyEvent.KEYCODE_T; break; case 0x8071: send_y_commit_n = false; ksek = "[]"; break;
            case 0x00F1: send_y_commit_n = false; ksek = "[]"; break;

            case 0x006C: send_y_commit_n = false; ksek = "?"; break; case 0x806C: kk = KeyEvent.KEYCODE_SEMICOLON; break;
            case 0x00EC: kk = KeyEvent.KEYCODE_SEMICOLON; break;

            case 0x006A: kk = KeyEvent.KEYCODE_W; break; case 0x806A: send_y_commit_n = false; ksek = "~"; break;
            case 0x00EA: send_y_commit_n = false; ksek = "~"; break;

            case 0x0069: send_y_commit_n = false; ksek = "!"; break; case 0x8069: send_y_commit_n = false; ksek = "<"; break;
            case 0x00E9: send_y_commit_n = false; ksek = "<"; break;

            case 0x0066: kk = KeyEvent.KEYCODE_EQUALS; break; case 0x8066: kk = KeyEvent.KEYCODE_EQUALS; break;
            // case 0x00E6: kk = KeyEvent.KEYCODE_EQUALS; break;

            case 0x0065: kk = KeyEvent.KEYCODE_APOSTROPHE; break; case 0x8065: kk = KeyEvent.KEYCODE_SEMICOLON; break;
            // case 0x00E5: kk = KeyEvent.KEYCODE_APOSTROPHE; break;

            case 0x0063: kk = KeyEvent.KEYCODE_GRAVE; break; case 0x8063: send_y_commit_n = false; ksek = "~"; break;
            case 0x00E3: send_y_commit_n = false; ksek = "~"; break;

            case 0x005C: send_y_commit_n = false; ksek = ">"; break; case 0x805C: send_y_commit_n = false; ksek = "()"; break;
            case 0x00DC: send_y_commit_n = false; ksek = "()"; break;

            case 0x005A: kk = KeyEvent.KEYCODE_SLASH; break; case 0x805A: send_y_commit_n = false; ksek = "%"; break;
            case 0x00DA: send_y_commit_n = false; ksek = "%"; break;

            case 0x0059: send_y_commit_n = false; ksek = "[]"; break; case 0x8059: send_y_commit_n = false; ksek = "()"; break;
            case 0x00D9: send_y_commit_n = false; ksek = "()"; break;

            case 0x0056: kk = KeyEvent.KEYCODE_L; break; case 0x8056: kk = KeyEvent.KEYCODE_BACKSLASH; break;
            case 0x00D6: send_y_commit_n = false; ksek = "!"; break;

            case 0x0055: kk = KeyEvent.KEYCODE_BACKSLASH; break; case 0x8055: send_y_commit_n = false; ksek = "%"; break;
            case 0x00D5: send_y_commit_n = false; ksek = "%"; break;

            case 0x0053: send_y_commit_n = false; ksek = "[]"; break; case 0x8053: send_y_commit_n = false; ksek = "{}"; break;
            case 0x00D3: send_y_commit_n = false; ksek = "{}"; break;

            case 0x004E: kk = KeyEvent.KEYCODE_BACKSLASH; break; case 0x804E: send_y_commit_n = false; ksek = "%"; break;
            case 0x00CE: send_y_commit_n = false; ksek = "%"; break;

            case 0x004D: kk = KeyEvent.KEYCODE_BACKSLASH; break; case 0x804D: send_y_commit_n = false; ksek = "%"; break;
            case 0x00CD: send_y_commit_n = false; ksek = "%"; break;

            case 0x004B: kk = KeyEvent.KEYCODE_W; break; case 0x804B: send_y_commit_n = false; ksek = "~"; break;
            case 0x00CB: send_y_commit_n = false; ksek = "~"; break;

            case 0x0047: send_y_commit_n = false; ksek = "()"; break; case 0x8047: send_y_commit_n = false; ksek = "[]"; break;
            case 0x00C7: send_y_commit_n = false; ksek = "{}"; break;

            case 0x003C: kk = KeyEvent.KEYCODE_PERIOD; break; case 0x803C: kk = KeyEvent.KEYCODE_PERIOD; break;
            case 0x00BC: send_y_commit_n = false; ksek = ":"; break;

            case 0x003A: send_y_commit_n = false; ksek = "?"; break; case 0x803A: kk = KeyEvent.KEYCODE_SEMICOLON; break;
            case 0x00BA: kk = KeyEvent.KEYCODE_SEMICOLON; break;

            case 0x0039: send_y_commit_n = false; ksek = "!"; break; case 0x8039: send_y_commit_n = false; ksek = "C"; break;
            case 0x00B9: kk = KeyEvent.KEYCODE_SLASH; break;

            case 0x0036: send_y_commit_n = false; ksek = "{}"; break; case 0x8036: kk = KeyEvent.KEYCODE_SEMICOLON; break;
            case 0x00B6: kk = KeyEvent.KEYCODE_SEMICOLON; break;

            case 0x0035: send_y_commit_n = false; ksek = "!"; break; case 0x8035: send_y_commit_n = false; ksek = "|"; break;
            case 0x00B5: kk = KeyEvent.KEYCODE_W; break;

            case 0x0033: kk = KeyEvent.KEYCODE_GRAVE; break; case 0x8033: send_y_commit_n = false; ksek = "[]"; break;
            case 0x00B3: send_y_commit_n = false; ksek = "[]"; break;

            case 0x002E: kk = KeyEvent.KEYCODE_L; break; case 0x802E: send_y_commit_n = false; ksek = "{}"; break;
            case 0x00AE: send_y_commit_n = false; ksek = "{}"; break;

            case 0x002D: kk = KeyEvent.KEYCODE_BACKSLASH; break; case 0x802D: send_y_commit_n = false; ksek = "G"; break;
            case 0x00AD: send_y_commit_n = false; ksek = "!"; break;

            case 0x002B: kk = KeyEvent.KEYCODE_SLASH; break; case 0x802B: send_y_commit_n = false; ksek = "G"; break;
            case 0x00AB: send_y_commit_n = false; ksek = "{}"; break;

            case 0x0027: kk = KeyEvent.KEYCODE_SLASH; break; case 0x8027: send_y_commit_n = false; ksek = "[]"; break;
            case 0x00A7: send_y_commit_n = false; ksek = "[]"; break;

            case 0x001E: send_y_commit_n = false; ksek = "<"; break; case 0x801E: send_y_commit_n = false; ksek = "()"; break;
            case 0x009E: send_y_commit_n = false; ksek = "()"; break;

            case 0x001D: kk = KeyEvent.KEYCODE_N; break; case 0x801D: send_y_commit_n = false; ksek = "*"; break;
            case 0x009D: send_y_commit_n = false; ksek = "~"; break;

            case 0x001B: kk = KeyEvent.KEYCODE_SLASH; break; case 0x801B: send_y_commit_n = false; ksek = "%"; break;
            case 0x009B: send_y_commit_n = false; ksek = "%"; break;

            case 0x0017: send_y_commit_n = false; ksek = "?"; break; case 0x8017: kk = KeyEvent.KEYCODE_COMMA; break;
            case 0x0097: kk = KeyEvent.KEYCODE_COMMA; break;

            case 0x000F: kk = KeyEvent.KEYCODE_K; break; case 0x800F: send_y_commit_n = false; ksek = "[]"; break;
            case 0x008F: send_y_commit_n = false; ksek = "[]"; break;


            // send_06 : k2p = 4 keys from 0-6 bilo + sft/dot/num
            ////////////////////////
            case 0x0070: send_y_commit_n = false; ksek = "[]"; break; case 0x8070: send_y_commit_n = false; ksek = "{}"; break;
            case 0x00F0: send_y_commit_n = false; ksek = "()"; break;

            case 0x0068: kk = KeyEvent.KEYCODE_SEMICOLON; break;
            // case 0x00E8: kk = KeyEvent.KEYCODE_SEMICOLON; break;

            case 0x0064: send_y_commit_n = false; ksek = "{}"; break;
            case 0x00E4: send_y_commit_n = false; ksek = "[]"; break;

            case 0x0062: send_y_commit_n = false; ksek = "*"; break;
            // case 0x00E2: send_y_commit_n = false; ksek = "*"; break;

            case 0x0061: kk = KeyEvent.KEYCODE_COMMA; break;
            // case 0x00E1: kk = KeyEvent.KEYCODE_COMMA; break;

            case 0x0058: kk = KeyEvent.KEYCODE_APOSTROPHE; break;
            // case 0x00D8: kk = KeyEvent.KEYCODE_APOSTROPHE; break;

            case 0x0054: send_y_commit_n = false; ksek = "!"; break;
            // case 0x00D4: send_y_commit_n = false; ksek = "!"; break;

            case 0x0052: kk = KeyEvent.KEYCODE_SLASH; break;
            // case 0x00D2: kk = KeyEvent.KEYCODE_SLASH; break;

            case 0x0051: send_y_commit_n = false; ksek = "|"; break;
            // case 0x00D1: send_y_commit_n = false; ksek = "|"; break;

            case 0x004C: send_y_commit_n = false; ksek = "%"; break;
            // case 0x00CC: send_y_commit_n = false; ksek = "%"; break;

            case 0x004A: send_y_commit_n = false; ksek = "U"; break;
            // case 0x00CA: send_y_commit_n = false; ksek = "U"; break;

            case 0x0049: send_y_commit_n = false; ksek = "%"; break;
            // case 0x00C9: send_y_commit_n = false; ksek = "%"; break;

            case 0x0046: kk = KeyEvent.KEYCODE_BACKSLASH; break;
            case 0x00C6: send_y_commit_n = false; ksek = "%"; break;

            case 0x0045: send_y_commit_n = false; ksek = "%"; break;
            case 0x00C5: kk = KeyEvent.KEYCODE_PERIOD; break;

            case 0x0043: send_y_commit_n = false; ksek = "*"; break;
            case 0x00C3: kk = KeyEvent.KEYCODE_PERIOD; break;

            case 0x0038: send_y_commit_n = false; ksek = "()"; break;
            // case 0x00B8: send_y_commit_n = false; ksek = "()"; break;

            case 0x0034: send_y_commit_n = false; ksek = ":"; break;
            // case 0x00B4: send_y_commit_n = false; ksek = ":"; break;

            case 0x0032: send_y_commit_n = false; ksek = "{}"; break;
            case 0x00B2: send_y_commit_n = false; ksek = "[]"; break;

            case 0x0031: kk = KeyEvent.KEYCODE_BACKSLASH; break;
            // case 0x00B1: kk = KeyEvent.KEYCODE_BACKSLASH; break;

            case 0x002C: send_y_commit_n = false; ksek = "!"; break;
            // case 0x00AC: send_y_commit_n = false; ksek = "!"; break;

            case 0x002A: kk = KeyEvent.KEYCODE_SEMICOLON; break;
            // case 0x00AA: kk = KeyEvent.KEYCODE_SEMICOLON; break;

            case 0x0029: kk = KeyEvent.KEYCODE_L; break; case 0x8029: kk = KeyEvent.KEYCODE_EQUALS; break;
            case 0x00A9: send_y_commit_n = false; ksek = "|"; break;

            case 0x0026: kk = KeyEvent.KEYCODE_C; break; case 0x8026: send_y_commit_n = false; ksek = "<"; break;
            case 0x00A6: send_y_commit_n = false; ksek = "()"; break;

            case 0x0025: send_y_commit_n = false; ksek = "()"; break;
            // case 0x00A5: send_y_commit_n = false; ksek = "()"; break;

            case 0x0023: kk = KeyEvent.KEYCODE_COMMA; break;
            // case 0x00A3: kk = KeyEvent.KEYCODE_COMMA; break;

            case 0x001C: kk = KeyEvent.KEYCODE_MINUS; break;
            // case 0x009C: kk = KeyEvent.KEYCODE_MINUS; break;

            case 0x001A: send_y_commit_n = false; ksek = "%"; break;
            // case 0x009A: send_y_commit_n = false; ksek = "%"; break;

            case 0x0019: send_y_commit_n = false; ksek = "%"; break;
            // case 0x0099: send_y_commit_n = false; ksek = "%"; break;

            case 0x0016: send_y_commit_n = false; ksek = "!"; break;
            // case 0x0096: send_y_commit_n = false; ksek = "!"; break;

            case 0x0015: send_y_commit_n = false; ksek = "*"; break;
            // case 0x0095: send_y_commit_n = false; ksek = "*"; break;

            case 0x0013: send_y_commit_n = false; ksek = "%"; break;
            // case 0x0093: send_y_commit_n = false; ksek = "%"; break;

            case 0x000E: kk = KeyEvent.KEYCODE_T; break;
            // case 0x008E: kk = KeyEvent.KEYCODE_T; break;

            case 0x000D: send_y_commit_n = false; ksek = "%"; break;
            // case 0x008D: send_y_commit_n = false; ksek = "%"; break;

            case 0x000B: send_y_commit_n = false; ksek = "%"; break;
            // case 0x008B: send_y_commit_n = false; ksek = "%"; break;

            case 0x0007: kk = KeyEvent.KEYCODE_F; break;
            // case 0x0087: kk = KeyEvent.KEYCODE_F; break;
/////////////////////////////////////////////////////////////////////////////////
//            case 0x047D: send_y_commit_n = false; ksek = "E"; break;
            case 0x047B: send_y_commit_n = false; ksek = "C"; break;
            case 0x0477: send_y_commit_n = false; ksek = "U"; break;
            case 0x046F: send_y_commit_n = false; ksek = "@"; break;
            case 0x045F: send_y_commit_n = false; ksek = "O"; break;
            case 0x043F: send_y_commit_n = false; ksek = "I"; break;

            //k2p = 2
            case 0x047C: send_y_commit_n = false; ksek = "P"; break;
            case 0x047A: send_y_commit_n = false; ksek = "D"; break;
            case 0x0479: send_y_commit_n = false; ksek = "()"; break;
            case 0x0476: send_y_commit_n = false; ksek = "H"; break;
            case 0x0475: send_y_commit_n = false; ksek = "Y"; break;
            case 0x0473: send_y_commit_n = false; ksek = "B"; break;
            case 0x046E: send_y_commit_n = false; ksek = ":"; break;
            case 0x046D: send_y_commit_n = false; ksek = "Z"; break;
            case 0x046B: kk = KeyEvent.KEYCODE_PERIOD; break;
            case 0x0467: send_y_commit_n = false; ksek = "D"; break;
            case 0x045D: send_y_commit_n = false; ksek = "L"; break;
            case 0x045B: send_y_commit_n = false; ksek = "G"; break;
            case 0x0457: send_y_commit_n = false; ksek = "V"; break;
            case 0x044F: send_y_commit_n = false; ksek = "J"; break;
            case 0x043E: send_y_commit_n = false; ksek = "Q"; break;
            case 0x043D: send_y_commit_n = false; ksek = "_"; break;
            case 0x043B: send_y_commit_n = false; ksek = "S"; break;
            case 0x0437: send_y_commit_n = false; ksek = "Y"; break;
            case 0x042F: send_y_commit_n = false; ksek = "|"; break;
            case 0x041F: send_y_commit_n = false; ksek = "R"; break;


            //k2p = 3
            case 0x0478: send_y_commit_n = false; ksek = "F"; break;
            case 0x0474: send_y_commit_n = false; ksek = "{}"; break;
            case 0x0472: send_y_commit_n = false; ksek = "()"; break;
            case 0x0471: send_y_commit_n = false; ksek = "T"; break;
            case 0x046C: kk = KeyEvent.KEYCODE_SEMICOLON; break;
            case 0x046A: send_y_commit_n = false; ksek = "W"; break;
            case 0x0469: send_y_commit_n = false; ksek = "<"; break;
            case 0x0463: send_y_commit_n = false; ksek = "~"; break;
            case 0x045C: send_y_commit_n = false; ksek = "()"; break;
            case 0x045A: send_y_commit_n = false; ksek = "%"; break;
            case 0x0459: send_y_commit_n = false; ksek = "()"; break;
            case 0x0456: send_y_commit_n = false; ksek = "!"; break;
            case 0x0455: send_y_commit_n = false; ksek = "%"; break;
            case 0x0453: send_y_commit_n = false; ksek = "()"; break;
            case 0x044E: send_y_commit_n = false; ksek = "!"; break;
            case 0x044D: send_y_commit_n = false; ksek = "!"; break;
            case 0x044B: send_y_commit_n = false; ksek = "W"; break;
            case 0x0447: send_y_commit_n = false; ksek = "{}"; break;
            case 0x043C: send_y_commit_n = false; ksek = ":"; break;
            case 0x043A: kk = KeyEvent.KEYCODE_SEMICOLON; break;
            case 0x0439: send_y_commit_n = false; ksek = "C" ; break;
            case 0x0436: kk = KeyEvent.KEYCODE_SEMICOLON; break;
            case 0x0435: kk = KeyEvent.KEYCODE_W; break;
            case 0x0433: send_y_commit_n = false; ksek = "[]" ; break;
            case 0x042E: send_y_commit_n = false; ksek = "{}"; break;
            case 0x042D: send_y_commit_n = false; ksek = "G" ; break;
            case 0x042B: send_y_commit_n = false; ksek = "G" ; break;
            case 0x0427: send_y_commit_n = false; ksek = "[]" ; break;
            case 0x041E: send_y_commit_n = false; ksek = "()" ; break;
            case 0x041D: send_y_commit_n = false; ksek = "N"; break;
            case 0x041B: send_y_commit_n = false; ksek = "%"; break;
            case 0x0417: kk = KeyEvent.KEYCODE_COMMA; break;
            case 0x040F: send_y_commit_n = false; ksek = "K"; break;


            //k2p = 4
            case 0x0470: send_y_commit_n = false; ksek = "}"; break;
            case 0x0454: send_y_commit_n = false; ksek = "L"; break;
            case 0x0452: send_y_commit_n = false; ksek = "?"; break;
            case 0x0426: send_y_commit_n = false; ksek = "{"; break;
            case 0x0416: kk = KeyEvent.KEYCODE_RIGHT_BRACKET; break;
            case 0x0407: send_y_commit_n = false; ksek = "?"; break;
/////////////////////////////////////////////////////////////////////////////////
            /// default
            default: isl88_up_pending = true; break;
        }
        if(!isl88_up_pending) send_kk();

    }
    public void send_8E(){
        isl88_up_pending = false ; meta = 0 ; kk = KeyEvent.KEYCODE_UNKNOWN ; ksek = null; send_y_commit_n = true;
        final int num88bytes ;
        if (mKeyboardSwitcher.is_nm_lok() && ((l88bytes & 0x0480) == 0)) {num88bytes =  l88bytes ^ 0x8000 ; } else { num88bytes =  l88bytes ; }
        switch (num88bytes) {
            ///////
            // send_8E : k2p=0,1
            case 0x007F: isl88_up_pending = true; break;	case 0x00FF: isl88_up_pending = true; break;
            case 0x807F: isl88_up_pending = true; break;	case 0x80FF: isl88_up_pending = true; break;

            case 0x017F: kk = KeyEvent.KEYCODE_ENTER; break;	case 0x01FF: send_y_commit_n = false; ksek = "?"; break;
            case 0x817F: isl88_up_pending = true; break;	case 0x81FF: isl88_up_pending = true; break;

            case 0x027F: kk = KeyEvent.KEYCODE_TAB; break;	case 0x02FF: send_y_commit_n = false; ksek = "+"; break;
            case 0x827F: isl88_up_pending = true; break;	case 0x82FF: isl88_up_pending = true; break;

            case 0x047F: send_y_commit_n = false; ksek = "_" ; break;	case 0x04FF: send_y_commit_n = false; ksek = "[]"; break;
            case 0x847F: isl88_up_pending = true; break;	case 0x84FF: isl88_up_pending = true; break;

            case 0x087F: kk = KeyEvent.KEYCODE_DEL; break;	case 0x08FF: kk = KeyEvent.KEYCODE_EQUALS; break;
            case 0x887F: isl88_up_pending = true; break;	case 0x88FF: isl88_up_pending = true; break;

            case 0x107F: isl88_up_pending = true; break;	case 0x10FF: send_y_commit_n = false; ksek = "*"; break;
            case 0x907F: isl88_up_pending = true; break;	case 0x90FF: isl88_up_pending = true; break;

            case 0x207F: kk = KeyEvent.KEYCODE_SPACE; break;	case 0x20FF: send_y_commit_n = false; ksek = "F"; break;
            case 0xA07F: send_y_commit_n = false; ksek = "W" ; break;	case 0xA0FF: isl88_up_pending = true; break;

            case 0x407F: kk = KeyEvent.KEYCODE_PERIOD; break;	case 0x40FF: kk = KeyEvent.KEYCODE_SEMICOLON; break;
            case 0xC07F: isl88_up_pending = true; break;	case 0xC0FF: isl88_up_pending = true; break;

            // send_8E : k2p=2
            case 0x037F: kk = KeyEvent.KEYCODE_COMMA; break;	case 0x03FF: kk = KeyEvent.KEYCODE_SEMICOLON; break;
            case 0x837F: isl88_up_pending = true; break;	case 0x83FF: isl88_up_pending = true; break;
            
            case 0x057F: isl88_up_pending = true; break;	case 0x05FF: send_y_commit_n = false; ksek = ":"; break;
            case 0x857F: isl88_up_pending = true; break;	case 0x85FF: isl88_up_pending = true; break;
            
            case 0x067F: isl88_up_pending = true; break;	case 0x06FF: kk = KeyEvent.KEYCODE_L; break;
            case 0x867F: isl88_up_pending = true; break;	case 0x86FF: isl88_up_pending = true; break;
            
            case 0x097F: send_y_commit_n = false; ksek = ":" ; break;	case 0x09FF: kk = KeyEvent.KEYCODE_EQUALS; break;
            case 0x897F: isl88_up_pending = true; break;	case 0x89FF: isl88_up_pending = true; break;
            
            case 0x0A7F: isl88_up_pending = true; break;	case 0x0AFF: send_y_commit_n = false; ksek = "~"; break;
            case 0x8A7F: isl88_up_pending = true; break;	case 0x8AFF: isl88_up_pending = true; break;
            
            case 0x0C7F: kk = KeyEvent.KEYCODE_GRAVE; break;	case 0x0CFF: isl88_up_pending = true; break;
            case 0x8C7F: isl88_up_pending = true; break;	case 0x8CFF: isl88_up_pending = true; break;
            
            case 0x117F: isl88_up_pending = true; break;	case 0x11FF: send_y_commit_n = false; ksek = "#"; break;
            case 0x917F: isl88_up_pending = true; break;	case 0x91FF: isl88_up_pending = true; break;
            
            case 0x127F: isl88_up_pending = true; break;	case 0x12FF: send_y_commit_n = false; ksek = "%"; break;
            case 0x927F: isl88_up_pending = true; break;	case 0x92FF: isl88_up_pending = true; break;
            
            case 0x147F: send_y_commit_n = false; ksek = "\"\"" ; break;	case 0x14FF: send_y_commit_n = false; ksek = "|"; break;
            case 0x947F: isl88_up_pending = true; break;	case 0x94FF: isl88_up_pending = true; break;
            
            case 0x187F: kk = KeyEvent.KEYCODE_APOSTROPHE; break;	case 0x18FF: kk = KeyEvent.KEYCODE_SEMICOLON; break;
            case 0x987F: isl88_up_pending = true; break;	case 0x98FF: isl88_up_pending = true; break;
            
            case 0x217F: send_y_commit_n = false; ksek = "*" ; break;	case 0x21FF: send_y_commit_n = false; ksek = "Z"; break;
            case 0xA17F: isl88_up_pending = true; break;	case 0xA1FF: isl88_up_pending = true; break;
            
            case 0x227F: isl88_up_pending = true; break;	case 0x22FF: isl88_up_pending = true; break;
            case 0xA27F: isl88_up_pending = true; break;	case 0xA2FF: isl88_up_pending = true; break;
            
            case 0x247F: isl88_up_pending = true; break;	case 0x24FF: isl88_up_pending = true; break;
            case 0xA47F: isl88_up_pending = true; break;	case 0xA4FF: isl88_up_pending = true; break;
            
            case 0x287F: kk = KeyEvent.KEYCODE_EQUALS; break;	case 0x28FF: isl88_up_pending = true; break;
            case 0xA87F: isl88_up_pending = true; break;	case 0xA8FF: isl88_up_pending = true; break;
            
            case 0x307F: isl88_up_pending = true; break;	case 0x30FF: isl88_up_pending = true; break;
            case 0xB07F: isl88_up_pending = true; break;	case 0xB0FF: isl88_up_pending = true; break;
            
            case 0x417F: isl88_up_pending = true; break;	case 0x41FF: isl88_up_pending = true; break;
            case 0xC17F: isl88_up_pending = true; break;	case 0xC1FF: isl88_up_pending = true; break;
            
            case 0x427F: isl88_up_pending = true; break;	case 0x42FF: isl88_up_pending = true; break;
            case 0xC27F: isl88_up_pending = true; break;	case 0xC2FF: isl88_up_pending = true; break;
            
            case 0x447F: isl88_up_pending = true; break;	case 0x44FF: isl88_up_pending = true; break;
            case 0xC47F: isl88_up_pending = true; break;	case 0xC4FF: isl88_up_pending = true; break;
            
            case 0x487F: isl88_up_pending = true; break;	case 0x48FF: isl88_up_pending = true; break;
            case 0xC87F: isl88_up_pending = true; break;	case 0xC8FF: isl88_up_pending = true; break;
            
            case 0x507F: isl88_up_pending = true; break;	case 0x50FF: isl88_up_pending = true; break;
            case 0xD07F: isl88_up_pending = true; break;	case 0xD0FF: isl88_up_pending = true; break;
            
            case 0x607F: kk = KeyEvent.KEYCODE_R; break;	case 0x60FF: isl88_up_pending = true; break;
            case 0xE07F: isl88_up_pending = true; break;	case 0xE0FF: isl88_up_pending = true; break;

            // send_8E : k2p=3
            case 0x077F: kk = KeyEvent.KEYCODE_SEMICOLON; break;	case 0x07FF: isl88_up_pending = true; break;
            case 0x877F: isl88_up_pending = true; break;	case 0x87FF: isl88_up_pending = true; break;
            
            case 0x0B7F: kk = KeyEvent.KEYCODE_SEMICOLON; break;	case 0x0BFF: isl88_up_pending = true; break;
            case 0x8B7F: isl88_up_pending = true; break;	case 0x8BFF: isl88_up_pending = true; break;
            
            case 0x0D7F: isl88_up_pending = true; break;	case 0x0DFF: isl88_up_pending = true; break;
            case 0x8D7F: isl88_up_pending = true; break;	case 0x8DFF: isl88_up_pending = true; break;
            
            case 0x0E7F: isl88_up_pending = true; break;	case 0x0EFF: isl88_up_pending = true; break;
            case 0x8E7F: isl88_up_pending = true; break;	case 0x8EFF: isl88_up_pending = true; break;
            
            case 0x137F: isl88_up_pending = true; break;	case 0x13FF: isl88_up_pending = true; break;
            case 0x937F: isl88_up_pending = true; break;	case 0x93FF: isl88_up_pending = true; break;
            
            case 0x157F: isl88_up_pending = true; break;	case 0x15FF: isl88_up_pending = true; break;
            case 0x957F: isl88_up_pending = true; break;	case 0x95FF: isl88_up_pending = true; break;
            
            case 0x167F: isl88_up_pending = true; break;	case 0x16FF: isl88_up_pending = true; break;
            case 0x967F: isl88_up_pending = true; break;	case 0x96FF: isl88_up_pending = true; break;
            
            case 0x197F: isl88_up_pending = true; break;	case 0x19FF: isl88_up_pending = true; break;
            case 0x997F: isl88_up_pending = true; break;	case 0x99FF: isl88_up_pending = true; break;
            
            case 0x1A7F: isl88_up_pending = true; break;	case 0x1AFF: isl88_up_pending = true; break;
            case 0x9A7F: isl88_up_pending = true; break;	case 0x9AFF: isl88_up_pending = true; break;
            
            case 0x1C7F: send_y_commit_n = false; ksek = "^" ; break;	case 0x1CFF: isl88_up_pending = true; break;
            case 0x9C7F: isl88_up_pending = true; break;	case 0x9CFF: isl88_up_pending = true; break;
            
            case 0x237F: send_y_commit_n = false; ksek = ">" ; break;	case 0x23FF: isl88_up_pending = true; break;
            case 0xA37F: isl88_up_pending = true; break;	case 0xA3FF: isl88_up_pending = true; break;
            
            case 0x257F: isl88_up_pending = true; break;	case 0x25FF: isl88_up_pending = true; break;
            case 0xA57F: isl88_up_pending = true; break;	case 0xA5FF: isl88_up_pending = true; break;
            
            case 0x267F: kk = KeyEvent.KEYCODE_LEFT_BRACKET; break;	case 0x26FF: isl88_up_pending = true; break;
            case 0xA67F: isl88_up_pending = true; break;	case 0xA6FF: isl88_up_pending = true; break;
            
            case 0x297F: send_y_commit_n = false; ksek = "|" ; break;	case 0x29FF: isl88_up_pending = true; break;
            case 0xA97F: isl88_up_pending = true; break;	case 0xA9FF: isl88_up_pending = true; break;
            
            case 0x2A7F: isl88_up_pending = true; break;	case 0x2AFF: isl88_up_pending = true; break;
            case 0xAA7F: isl88_up_pending = true; break;	case 0xAAFF: isl88_up_pending = true; break;
            
            case 0x2C7F: kk = KeyEvent.KEYCODE_RIGHT_BRACKET; break;	case 0x2CFF: isl88_up_pending = true; break;
            case 0xAC7F: isl88_up_pending = true; break;	case 0xACFF: isl88_up_pending = true; break;
            
            case 0x317F: isl88_up_pending = true; break;	case 0x31FF: isl88_up_pending = true; break;
            case 0xB17F: isl88_up_pending = true; break;	case 0xB1FF: isl88_up_pending = true; break;
            
            case 0x327F: kk = KeyEvent.KEYCODE_BACKSLASH; break;	case 0x32FF: isl88_up_pending = true; break;
            case 0xB27F: isl88_up_pending = true; break;	case 0xB2FF: isl88_up_pending = true; break;
            
            case 0x347F: send_y_commit_n = false; ksek = "~" ; break;	case 0x34FF: isl88_up_pending = true; break;
            case 0xB47F: isl88_up_pending = true; break;	case 0xB4FF: isl88_up_pending = true; break;
            
            case 0x387F: kk = KeyEvent.KEYCODE_LEFT_BRACKET; break;	case 0x38FF: isl88_up_pending = true; break;
            case 0xB87F: isl88_up_pending = true; break;	case 0xB8FF: isl88_up_pending = true; break;
            
            case 0x437F: isl88_up_pending = true; break;	case 0x43FF: isl88_up_pending = true; break;
            case 0xC37F: isl88_up_pending = true; break;	case 0xC3FF: isl88_up_pending = true; break;
            
            case 0x457F: isl88_up_pending = true; break;	case 0x45FF: isl88_up_pending = true; break;
            case 0xC57F: isl88_up_pending = true; break;	case 0xC5FF: isl88_up_pending = true; break;
            
            case 0x467F: isl88_up_pending = true; break;	case 0x46FF: isl88_up_pending = true; break;
            case 0xC67F: isl88_up_pending = true; break;	case 0xC6FF: isl88_up_pending = true; break;
            
            case 0x497F: isl88_up_pending = true; break;	case 0x49FF: isl88_up_pending = true; break;
            case 0xC97F: isl88_up_pending = true; break;	case 0xC9FF: isl88_up_pending = true; break;
            
            case 0x4A7F: isl88_up_pending = true; break;	case 0x4AFF: isl88_up_pending = true; break;
            case 0xCA7F: isl88_up_pending = true; break;	case 0xCAFF: isl88_up_pending = true; break;
            
            case 0x4C7F: isl88_up_pending = true; break;	case 0x4CFF: isl88_up_pending = true; break;
            case 0xCC7F: isl88_up_pending = true; break;	case 0xCCFF: isl88_up_pending = true; break;
            
            case 0x517F: kk = KeyEvent.KEYCODE_L; break;	case 0x51FF: isl88_up_pending = true; break;
            case 0xD17F: isl88_up_pending = true; break;	case 0xD1FF: isl88_up_pending = true; break;
            
            case 0x527F: isl88_up_pending = true; break;	case 0x52FF: isl88_up_pending = true; break;
            case 0xD27F: isl88_up_pending = true; break;	case 0xD2FF: isl88_up_pending = true; break;
            
            case 0x547F: isl88_up_pending = true; break;	case 0x54FF: isl88_up_pending = true; break;
            case 0xD47F: isl88_up_pending = true; break;	case 0xD4FF: isl88_up_pending = true; break;
            
            case 0x587F: isl88_up_pending = true; break;	case 0x58FF: isl88_up_pending = true; break;
            case 0xD87F: isl88_up_pending = true; break;	case 0xD8FF: isl88_up_pending = true; break;
            
            case 0x617F: send_y_commit_n = false; ksek = "<" ; break;	case 0x61FF: isl88_up_pending = true; break;
            case 0xE17F: isl88_up_pending = true; break;	case 0xE1FF: isl88_up_pending = true; break;
            
            case 0x627F: send_y_commit_n = false; ksek = "*" ; break;	case 0x62FF: isl88_up_pending = true; break;
            case 0xE27F: isl88_up_pending = true; break;	case 0xE2FF: isl88_up_pending = true; break;
            
            case 0x647F: kk = KeyEvent.KEYCODE_SLASH; break;	case 0x64FF: isl88_up_pending = true; break;
            case 0xE47F: send_y_commit_n = false; ksek = "%" ; break;	case 0xE4FF: isl88_up_pending = true; break;
            
            case 0x687F: isl88_up_pending = true; break;	case 0x68FF: isl88_up_pending = true; break;
            case 0xE87F: isl88_up_pending = true; break;	case 0xE8FF: isl88_up_pending = true; break;
            
            case 0x707F: isl88_up_pending = true; break;	case 0x70FF: isl88_up_pending = true; break;
            case 0xF07F: isl88_up_pending = true; break;	case 0xF0FF: isl88_up_pending = true; break;

            // send_8E : k2p=4
            case 0x0F7F: isl88_up_pending = true; break;	case 0x0FFF: isl88_up_pending = true; break;
            case 0x8F7F: isl88_up_pending = true; break;	case 0x8FFF: isl88_up_pending = true; break;
            
            case 0x177F: isl88_up_pending = true; break;	case 0x17FF: isl88_up_pending = true; break;
            case 0x977F: isl88_up_pending = true; break;	case 0x97FF: isl88_up_pending = true; break;
            
            case 0x1B7F: isl88_up_pending = true; break;	case 0x1BFF: isl88_up_pending = true; break;
            case 0x9B7F: isl88_up_pending = true; break;	case 0x9BFF: isl88_up_pending = true; break;
            
            case 0x1D7F: isl88_up_pending = true; break;	case 0x1DFF: isl88_up_pending = true; break;
            case 0x9D7F: isl88_up_pending = true; break;	case 0x9DFF: isl88_up_pending = true; break;
            
            case 0x1E7F: isl88_up_pending = true; break;	case 0x1EFF: isl88_up_pending = true; break;
            case 0x9E7F: isl88_up_pending = true; break;	case 0x9EFF: isl88_up_pending = true; break;
            
            case 0x277F: isl88_up_pending = true; break;	case 0x27FF: isl88_up_pending = true; break;
            case 0xA77F: isl88_up_pending = true; break;	case 0xA7FF: isl88_up_pending = true; break;
            
            case 0x2B7F: isl88_up_pending = true; break;	case 0x2BFF: isl88_up_pending = true; break;
            case 0xAB7F: isl88_up_pending = true; break;	case 0xABFF: isl88_up_pending = true; break;
            
            case 0x2D7F: send_y_commit_n = false; ksek = "?"; break;	case 0x2DFF: isl88_up_pending = true; break;
            case 0xAD7F: isl88_up_pending = true; break;	case 0xADFF: isl88_up_pending = true; break;
            
            case 0x2E7F: isl88_up_pending = true; break;	case 0x2EFF: isl88_up_pending = true; break;
            case 0xAE7F: isl88_up_pending = true; break;	case 0xAEFF: isl88_up_pending = true; break;
            
            case 0x337F: isl88_up_pending = true; break;	case 0x33FF: isl88_up_pending = true; break;
            case 0xB37F: isl88_up_pending = true; break;	case 0xB3FF: isl88_up_pending = true; break;
            
            case 0x357F: isl88_up_pending = true; break;	case 0x35FF: isl88_up_pending = true; break;
            case 0xB57F: isl88_up_pending = true; break;	case 0xB5FF: isl88_up_pending = true; break;
            
            case 0x367F: isl88_up_pending = true; break;	case 0x36FF: isl88_up_pending = true; break;
            case 0xB67F: isl88_up_pending = true; break;	case 0xB6FF: isl88_up_pending = true; break;
            
            case 0x397F: isl88_up_pending = true; break;	case 0x39FF: isl88_up_pending = true; break;
            case 0xB97F: isl88_up_pending = true; break;	case 0xB9FF: isl88_up_pending = true; break;
            
            case 0x3A7F: isl88_up_pending = true; break;	case 0x3AFF: isl88_up_pending = true; break;
            case 0xBA7F: isl88_up_pending = true; break;	case 0xBAFF: isl88_up_pending = true; break;
            
            case 0x3C7F: isl88_up_pending = true; break;	case 0x3CFF: isl88_up_pending = true; break;
            case 0xBC7F: isl88_up_pending = true; break;	case 0xBCFF: isl88_up_pending = true; break;
            
            case 0x477F: isl88_up_pending = true; break;	case 0x47FF: isl88_up_pending = true; break;
            case 0xC77F: isl88_up_pending = true; break;	case 0xC7FF: isl88_up_pending = true; break;
            
            case 0x4B7F: isl88_up_pending = true; break;	case 0x4BFF: isl88_up_pending = true; break;
            case 0xCB7F: isl88_up_pending = true; break;	case 0xCBFF: isl88_up_pending = true; break;
            
            case 0x4D7F: isl88_up_pending = true; break;	case 0x4DFF: isl88_up_pending = true; break;
            case 0xCD7F: isl88_up_pending = true; break;	case 0xCDFF: isl88_up_pending = true; break;
            
            case 0x4E7F: isl88_up_pending = true; break;	case 0x4EFF: isl88_up_pending = true; break;
            case 0xCE7F: isl88_up_pending = true; break;	case 0xCEFF: isl88_up_pending = true; break;
            
            case 0x537F: isl88_up_pending = true; break;	case 0x53FF: isl88_up_pending = true; break;
            case 0xD37F: isl88_up_pending = true; break;	case 0xD3FF: isl88_up_pending = true; break;
            
            case 0x557F: isl88_up_pending = true; break;	case 0x55FF: isl88_up_pending = true; break;
            case 0xD57F: isl88_up_pending = true; break;	case 0xD5FF: isl88_up_pending = true; break;
            
            case 0x567F: isl88_up_pending = true; break;	case 0x56FF: isl88_up_pending = true; break;
            case 0xD67F: isl88_up_pending = true; break;	case 0xD6FF: isl88_up_pending = true; break;
            
            case 0x597F: isl88_up_pending = true; break;	case 0x59FF: isl88_up_pending = true; break;
            case 0xD97F: isl88_up_pending = true; break;	case 0xD9FF: isl88_up_pending = true; break;
            
            case 0x5A7F: isl88_up_pending = true; break;	case 0x5AFF: isl88_up_pending = true; break;
            case 0xDA7F: isl88_up_pending = true; break;	case 0xDAFF: isl88_up_pending = true; break;
            
            case 0x5C7F: isl88_up_pending = true; break;	case 0x5CFF: isl88_up_pending = true; break;
            case 0xDC7F: isl88_up_pending = true; break;	case 0xDCFF: isl88_up_pending = true; break;
            
            case 0x637F: kk = KeyEvent.KEYCODE_O; break;	case 0x63FF: isl88_up_pending = true; break;
            case 0xE37F: isl88_up_pending = true; break;	case 0xE3FF: isl88_up_pending = true; break;
            
            case 0x657F: isl88_up_pending = true; break;	case 0x65FF: isl88_up_pending = true; break;
            case 0xE57F: isl88_up_pending = true; break;	case 0xE5FF: isl88_up_pending = true; break;
            
            case 0x667F: isl88_up_pending = true; break;	case 0x66FF: isl88_up_pending = true; break;
            case 0xE67F: isl88_up_pending = true; break;	case 0xE6FF: isl88_up_pending = true; break;
            
            case 0x697F: send_y_commit_n = false; ksek = "!" ; break;	case 0x69FF: isl88_up_pending = true; break;
            case 0xE97F: isl88_up_pending = true; break;	case 0xE9FF: isl88_up_pending = true; break;
            
            case 0x6A7F: isl88_up_pending = true; break;	case 0x6AFF: isl88_up_pending = true; break;
            case 0xEA7F: isl88_up_pending = true; break;	case 0xEAFF: isl88_up_pending = true; break;
            
            case 0x6C7F: isl88_up_pending = true; break;	case 0x6CFF: isl88_up_pending = true; break;
            case 0xEC7F: isl88_up_pending = true; break;	case 0xECFF: isl88_up_pending = true; break;
            
            case 0x717F: isl88_up_pending = true; break;	case 0x71FF: isl88_up_pending = true; break;
            case 0xF17F: isl88_up_pending = true; break;	case 0xF1FF: isl88_up_pending = true; break;
            
            case 0x727F: isl88_up_pending = true; break;	case 0x72FF: isl88_up_pending = true; break;
            case 0xF27F: isl88_up_pending = true; break;	case 0xF2FF: isl88_up_pending = true; break;
            
            case 0x747F: isl88_up_pending = true; break;	case 0x74FF: isl88_up_pending = true; break;
            case 0xF47F: isl88_up_pending = true; break;	case 0xF4FF: isl88_up_pending = true; break;
            
            case 0x787F: send_y_commit_n = false; ksek = "?"; break;	case 0x78FF: isl88_up_pending = true; break;
            case 0xF87F: isl88_up_pending = true; break;	case 0xF8FF: isl88_up_pending = true; break;

            default: isl88_up_pending = true ;
        }
        if(!isl88_up_pending) send_kk();
    }
    public void dot_8E(){
        isl88_up_pending = false ; meta = 0 ; kk = KeyEvent.KEYCODE_UNKNOWN ; ksek = null; send_y_commit_n = true;
        switch (l88bytes) {

            ///////////////
//            case 0x01FF: send_y_commit_n = false; ksek = "?"; break;
//            case 0x02FF: send_y_commit_n = false; ksek = "+"; break;
//            case 0x04FF: send_y_commit_n = false; ksek = "()"; break;
//            case 0x08FF: kk = KeyEvent.KEYCODE_EQUALS; break;
//            case 0x10FF: send_y_commit_n = false; ksek = "*"; break;
//            case 0x20FF: send_y_commit_n = false; ksek = "F"; break;
//            case 0x40FF: kk = KeyEvent.KEYCODE_SEMICOLON; break;

            // k2p = 2  bilo
//            case 0x03FF: kk = KeyEvent.KEYCODE_SEMICOLON; break;
//            case 0x05FF: send_y_commit_n = false; ksek = ":"; break;
//            case 0x06FF: kk = KeyEvent.KEYCODE_L; break;
//            case 0x09FF: kk = KeyEvent.KEYCODE_EQUALS; break;
//            case 0x0AFF: send_y_commit_n = false; ksek = "~"; break;
// case 0x0CFF: break;
//            case 0x11FF: send_y_commit_n = false; ksek = "#"; break;
//            case 0x12FF: send_y_commit_n = false; ksek = "%"; break;
//            case 0x14FF: send_y_commit_n = false; ksek = "|"; break;
//            case 0x18FF: kk = KeyEvent.KEYCODE_SEMICOLON; break;
//            case 0x21FF: send_y_commit_n = false; ksek = "Z"; break;
            ///////////////
            // k2p = 1 bilo
//            case 0x01FF: send_y_commit_n = false; ksek = "?"; break;
//            case 0x02FF: send_y_commit_n = false; ksek = "+"; break;
////            case 0x04FF: send_y_commit_n = false; ksek = "()"; break;
//            case 0x08FF: kk = KeyEvent.KEYCODE_EQUALS; break;
//            case 0x10FF: send_y_commit_n = false; ksek = "*"; break;
//            case 0x20FF: send_y_commit_n = false; ksek = "F"; break;
//            case 0x40FF: kk = KeyEvent.KEYCODE_SEMICOLON; break;
//
//            // k2p = 2  bilo
//            case 0x03FF: kk = KeyEvent.KEYCODE_SEMICOLON; break;
//            case 0x05FF: send_y_commit_n = false; ksek = ":"; break;
//            case 0x06FF: kk = KeyEvent.KEYCODE_L; break;
//            case 0x09FF: kk = KeyEvent.KEYCODE_EQUALS; break;
//            case 0x0AFF: send_y_commit_n = false; ksek = "~"; break;
//// case 0x0CFF: break;
//            case 0x11FF: send_y_commit_n = false; ksek = "#"; break;
//            case 0x12FF: send_y_commit_n = false; ksek = "%"; break;
//            case 0x14FF: send_y_commit_n = false; ksek = "|"; break;
//            case 0x18FF: kk = KeyEvent.KEYCODE_SEMICOLON; break;
//            case 0x21FF: send_y_commit_n = false; ksek = "Z"; break;
//            case 0x22FF: break;
//            case 0x24FF: break;
//            case 0x28FF: break;
//            case 0x30FF: break;
//            case 0x41FF: break;
//            case 0x42FF: break;
//            case 0x44FF: break;
//            case 0x48FF: break;
//            case 0x50FF: break;
//            case 0x60FF: break;


            /// key2press = 3 bilo

            // k2p = 4 bilo

            default: isl88_up_pending = true ; break ;
        }
        if(!isl88_up_pending) send_kk();
    }
    public void sft_dot_06(){
        isl88_up_pending = false ; meta = 0 ; kk = KeyEvent.KEYCODE_UNKNOWN ; ksek = null; send_y_commit_n = true;
        switch (l88bytes) {
            case 0x04CF: send_y_commit_n = false; ksek = "Z";
            case 0x04EF: send_y_commit_n = false; ksek = "`"; break;
            case 0x04F6: send_y_commit_n = false; ksek = "#"; break;
//            case 0x04FF:  send_y_commit_n = false; ksek = "[]"; break;
            //////////
            case 0x04BF: send_y_commit_n = false; ksek = "C" ; break;
            default: isl88_up_pending = true ; break ;
        }
        if(!isl88_up_pending) send_kk();
    }

    /////// 8E sending bilo

    ////  ctl sft alt combinations
    public void send_knt(){
        meta = KeyEvent.META_CTRL_ON ;  kk = KeyEvent.KEYCODE_UNKNOWN ; ksek = null; send_y_commit_n = true;
        if(isl88_up_pending) { send_ka_s07(); }
        if(isl88_up_pending) { send_ka_s8f(); }
        if(isl88_up_pending) {
            isl88_up_pending = false; meta = KeyEvent.META_CTRL_ON ;
            switch (l88bytes) {
                case 0x107E: case 0x106F: kk = KeyEvent.KEYCODE_A; break;
                case 0x1059: case 0x101F: kk = KeyEvent.KEYCODE_C; break; case 0x1073: kk = KeyEvent.KEYCODE_B; break;
                case 0x107D: kk = KeyEvent.KEYCODE_E; break; case 0x907D: kk = KeyEvent.KEYCODE_1; break;
                case 0x1078: kk = KeyEvent.KEYCODE_F; break;
                case 0x105B: kk = KeyEvent.KEYCODE_G; break;
                case 0x1067: kk = KeyEvent.KEYCODE_D; break;
                case 0x507F: kk = KeyEvent.KEYCODE_X; break;
                case 0x1057: kk = KeyEvent.KEYCODE_V; break;
                case 0x104F: kk = KeyEvent.KEYCODE_J; break;
                case 0x105D: kk = KeyEvent.KEYCODE_L; break;
                case 0x106D: kk = KeyEvent.KEYCODE_Z; break;
                case 0x107B: kk = KeyEvent.KEYCODE_T; break;
                case 0x307F: kk = KeyEvent.KEYCODE_W; break;

                case 0x907B: kk = KeyEvent.KEYCODE_2; break;
                case 0x9077: kk = KeyEvent.KEYCODE_3; break;
                case 0x906F: kk = KeyEvent.KEYCODE_4; break;
                case 0x905F: kk = KeyEvent.KEYCODE_5; break;
                default: isl88_up_pending = true;
            }
            if(!isl88_up_pending) send_kk();
        }
    }
    public void send_alt(){
        meta = KeyEvent.META_ALT_ON ;  kk = KeyEvent.KEYCODE_UNKNOWN ; ksek = null; send_y_commit_n = true;
        if(isl88_up_pending) { send_ka_s07(); }
        if(isl88_up_pending) { send_ka_s8f(); }
        if(isl88_up_pending) {
            isl88_up_pending = false ; meta = KeyEvent.META_ALT_ON ;
            switch (l88bytes) {
                case 0x0873: kk = KeyEvent.KEYCODE_B; break;
                case 0x087E: case 0x086F: kk = KeyEvent.KEYCODE_A; break;
                case 0x084F: kk = KeyEvent.KEYCODE_J; break;
                case 0x0A7F: kk = KeyEvent.KEYCODE_TAB; break;
                default: isl88_up_pending = true;
            }
            if(!isl88_up_pending) send_kk();
        }
    }
    public void send_knt_alt(){
        meta = KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON ;  kk = KeyEvent.KEYCODE_UNKNOWN ; ksek = null; send_y_commit_n = true;
        if(isl88_up_pending) { send_ka_s07(); }
        if(isl88_up_pending) { send_ka_s8f(); }
        if(isl88_up_pending) {
            isl88_up_pending = false; meta = KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON ;
            switch (l88bytes) {
                default: isl88_up_pending = true;
            }
            if(!isl88_up_pending) send_kk();
        }
    }
    public void send_knt_sft(){
        meta = KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON ;  kk = KeyEvent.KEYCODE_UNKNOWN ; ksek = null; send_y_commit_n = true;
        if(isl88_up_pending) { send_ka_s07(); }
        if(isl88_up_pending) { send_ka_s8f(); }
        if(isl88_up_pending) {
            isl88_up_pending = false; meta = KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON ;
            switch (l88bytes) {
                default: isl88_up_pending = true;
            }
            if(!isl88_up_pending) send_kk();
        }
    }
    public void send_knt_alt_sft(){
        meta = KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON ;
        kk = KeyEvent.KEYCODE_UNKNOWN ; ksek = null; send_y_commit_n = true;
        if(isl88_up_pending) { send_ka_s07(); }
        if(isl88_up_pending) { send_ka_s8f(); }
        if(isl88_up_pending) {
            isl88_up_pending = false; meta = KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON ;
            switch (l88bytes) {
                default: isl88_up_pending = true;
            }
            if(!isl88_up_pending) send_kk();
        }
    }
    public void send_alt_sft(){
        meta = KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON ;
        kk = KeyEvent.KEYCODE_UNKNOWN ; ksek = null; send_y_commit_n = true;
        if(isl88_up_pending) { send_ka_s07(); }
        if(isl88_up_pending) { send_ka_s8f(); }
        if(isl88_up_pending) {
            isl88_up_pending = false; meta = KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON ;
            switch (l88bytes) {
                default: isl88_up_pending = true;
            }
            if(!isl88_up_pending) send_kk();
        }
    }
    public void send_sft8F(){
        isl88_up_pending = false ; meta = KeyEvent.META_SHIFT_ON ; kk = KeyEvent.KEYCODE_UNKNOWN ; ksek = null; send_y_commit_n = true;
        if(isl88_up_pending) { send_ka_s8f(); }
        if(isl88_up_pending) {
            isl88_up_pending = false;
            switch (l88bytes) {
                case 0x067F: kk = KeyEvent.KEYCODE_TAB; break;
                case 0x4477: kk = KeyEvent.KEYCODE_DPAD_UP; break;
                case 0x447E: kk = KeyEvent.KEYCODE_DPAD_DOWN; break;
                case 0x443F: kk = KeyEvent.KEYCODE_DPAD_LEFT; break;
                case 0x447D: kk = KeyEvent.KEYCODE_DPAD_RIGHT; break;
                case 0x0477: kk = KeyEvent.KEYCODE_PAGE_UP; break;
                case 0x047E: kk = KeyEvent.KEYCODE_PAGE_DOWN; break;
                case 0x043F: kk = KeyEvent.KEYCODE_MOVE_HOME; break;
                case 0x047D: kk = KeyEvent.KEYCODE_MOVE_END; break;
                case 0x143F: meta = KeyEvent.META_SHIFT_ON | KeyEvent.META_CTRL_ON ; kk = KeyEvent.KEYCODE_MOVE_HOME; break;
                case 0x147D: meta = KeyEvent.META_SHIFT_ON | KeyEvent.META_CTRL_ON ; kk = KeyEvent.KEYCODE_MOVE_HOME; break;
                default: isl88_up_pending = true;
            }
            if(!isl88_up_pending) send_kk();
        }
    }
    public void send_ka_s07(){
        isl88_up_pending = false ; kk = KeyEvent.KEYCODE_UNKNOWN ; ksek = null; send_y_commit_n = true;
        switch (l88bytes & 0x00FF) {
            case 0x007E: if (mKeyboardSwitcher.is_nm_lok()) { kk = KeyEvent.KEYCODE_0; } else { kk = KeyEvent.KEYCODE_A; } break;
            case 0x007B: if (mKeyboardSwitcher.is_nm_lok()) { kk = KeyEvent.KEYCODE_2; } else { kk = KeyEvent.KEYCODE_C; } break;
            case 0x0077: if (mKeyboardSwitcher.is_nm_lok()) { kk = KeyEvent.KEYCODE_3; } else { kk = KeyEvent.KEYCODE_U; } break;
            case 0x006F: if (mKeyboardSwitcher.is_nm_lok()) { kk = KeyEvent.KEYCODE_4; } else { kk = KeyEvent.KEYCODE_A; } break;
            case 0x005F: if (mKeyboardSwitcher.is_nm_lok()) { kk = KeyEvent.KEYCODE_5; } else { kk = KeyEvent.KEYCODE_O; } break;
            case 0x003F: if (mKeyboardSwitcher.is_nm_lok()) { kk = KeyEvent.KEYCODE_6; } else { kk = KeyEvent.KEYCODE_I; } break;
// case 0x047F: if (mKeyboardSwitcher.is_nm_lok()) { kk = KeyEvent.KEYCODE_L; } else { kk = KeyEvent.KEYCODE_MINUS; } break;
            case 0x0073: kk = KeyEvent.KEYCODE_B; break;
            case 0x0059: kk = KeyEvent.KEYCODE_C; break;
            case 0x0067: kk = KeyEvent.KEYCODE_D; break;
            case 0x0078: kk = KeyEvent.KEYCODE_F; break;
            case 0x005B: kk = KeyEvent.KEYCODE_G; break;
            case 0x004F: kk = KeyEvent.KEYCODE_J; break;
            case 0x000F: case 0x0070: kk = KeyEvent.KEYCODE_K; break;
            case 0x005E: kk = KeyEvent.KEYCODE_M; break;
            case 0x001D: kk = KeyEvent.KEYCODE_N; break;
            case 0x007C: kk = KeyEvent.KEYCODE_P; break;
            case 0x003E: kk = KeyEvent.KEYCODE_Q; break;
            case 0x001F: kk = KeyEvent.KEYCODE_R; break;
            case 0x003B: kk = KeyEvent.KEYCODE_S; break;
            case 0x0057: kk = KeyEvent.KEYCODE_V; break;
            case 0x003C: kk = KeyEvent.KEYCODE_W; break;
            case 0x0037: kk = KeyEvent.KEYCODE_Y; break;
            case 0x006D: kk = KeyEvent.KEYCODE_Z; break;
// case 0x001B: ic.commitText("/", 1); break;
// case 0x006E: case 0x00F1: ic.commitText("T", 1); break;
// case 0x00E7:  ic.commitText("D", 1); break;
// case 0x006A: case 0x0075: ic.commitText("n", 1); break;
// case 0x0062:case 0x00E3: case 0x00F5:case 0x009E: ic.commitText("N", 1); break;
// case 0x00BE: ic.commitText("K", 1); break;
// case 0x00ED: ic.commitText("R", 1); break;
// case 0x0043: ic.commitText("u", 1); break;
// case 0x0063: ic.commitText("o", 1); break;
// case 0x0071: ic.commitText("t", 1); break;
// case 0x00BB: ic.commitText("$", 1); break;
// case 0x005D: case 0x5100: ic.commitText("l", 1); break;
// case 0x0072: ic.commitText("h", 1); break;
// case 0x00FD: ic.commitText("\n", 1); break;
            default: isl88_up_pending = true ; break ;
        }
        if(isl88_up_pending)
        {
            isl88_up_pending = false ;
            switch (l88bytes & 0xFF00) {
                case 0x7000: kk = KeyEvent.KEYCODE_K; break;
                default: isl88_up_pending = true ; break ;
            }
        }
        if(!isl88_up_pending) send_kk();
    }
    public void send_ka_s8f(){
        isl88_up_pending = false ; kk = KeyEvent.KEYCODE_UNKNOWN ; ksek = null; send_y_commit_n = true;
        switch (l88bytes & 0xFF00) {
            default: isl88_up_pending = true ; break ;
        }
        if(isl88_up_pending)
        {
            isl88_up_pending = false ;
            switch (l88bytes & 0xFF00) {
                case 0x7000: kk = KeyEvent.KEYCODE_K; break;
                default: isl88_up_pending = true ; break ;
            }
        }
        if(!isl88_up_pending) send_kk();
    }

    ////  menu minu go muv fn
    public void send_minu(){
        isl88_up_pending = false ; meta = 0 ; kk = KeyEvent.KEYCODE_UNKNOWN ; ksek = null ; send_y_commit_n = true;
        switch (l88bytes) {
            case 0x407E: kk = KeyEvent.KEYCODE_BUTTON_THUMBR; break;
            case 0x607F: kk = KeyEvent.KEYCODE_FORWARD_DEL; break;
            default: isl88_up_pending = true ; break ;
        }
        if(!isl88_up_pending) send_kk();
    }
    public void send_go(){
        isl88_up_pending = false ; meta = 0 ; kk = KeyEvent.KEYCODE_UNKNOWN ; ksek = null ; send_y_commit_n = true;
        switch (l88bytes) {
            case 0x0177: kk = KeyEvent.KEYCODE_DPAD_UP; break;
            case 0x017E: kk = KeyEvent.KEYCODE_DPAD_DOWN; break;
            case 0x013F: kk = KeyEvent.KEYCODE_DPAD_LEFT; break;
            case 0x017D: kk = KeyEvent.KEYCODE_DPAD_RIGHT; break;
            default: isl88_up_pending = true ; break ;
        }
        if(isl88_up_pending) {
            isl88_up_pending = false ;
            meta = KeyEvent.META_SHIFT_ON ;
            switch (l88bytes) {
                case 0x0577: kk = KeyEvent.KEYCODE_DPAD_UP; break;
                case 0x057E: kk = KeyEvent.KEYCODE_DPAD_DOWN; break;
                case 0x053F: kk = KeyEvent.KEYCODE_DPAD_LEFT; break;
                case 0x057D: kk = KeyEvent.KEYCODE_DPAD_RIGHT; break;
                default: isl88_up_pending = true ; break ;
            }
        }
        if(!isl88_up_pending) send_kk();
        meta = 0 ; kk = KeyEvent.KEYCODE_UNKNOWN ; ksek = null ; send_y_commit_n = true;
    }
    public void send_muv(){
        isl88_up_pending = false ; meta = 0 ; kk = KeyEvent.KEYCODE_UNKNOWN ; ksek = null ; send_y_commit_n = true;
        switch (l88bytes) {
            case 0x0277: kk = KeyEvent.KEYCODE_PAGE_UP; break;
            case 0x027E: kk = KeyEvent.KEYCODE_PAGE_DOWN; break;
            case 0x023F: kk = KeyEvent.KEYCODE_MOVE_HOME; break; //96
            case 0x027D: kk = KeyEvent.KEYCODE_MOVE_END; break;
            case 0x123F: kk = KeyEvent.KEYCODE_MOVE_HOME; meta = meta | KeyEvent.META_CTRL_ON; break; // ktl
            case 0x127D: kk = KeyEvent.KEYCODE_MOVE_END; meta = meta | KeyEvent.META_CTRL_ON; break; // ktl
            default: isl88_up_pending = true ; break ;
        }
        if(isl88_up_pending) {
            isl88_up_pending = false ;
            meta = KeyEvent.META_SHIFT_ON ;
            switch (l88bytes) {
                case 0x0677: kk = KeyEvent.KEYCODE_PAGE_UP; break;
                case 0x067E: kk = KeyEvent.KEYCODE_PAGE_DOWN; break;
                case 0x063F: kk = KeyEvent.KEYCODE_MOVE_HOME; break; //96
                case 0x067D: kk = KeyEvent.KEYCODE_MOVE_END; break;
                case 0x163F: kk = KeyEvent.KEYCODE_MOVE_HOME; meta = meta | KeyEvent.META_CTRL_ON; break; // ktl
                case 0x167D: kk = KeyEvent.KEYCODE_MOVE_END; meta = meta | KeyEvent.META_CTRL_ON; break; // ktl
                default: isl88_up_pending = true ; break ;
            }            
        }
        if(!isl88_up_pending) send_kk();
    }
    public void send_fn_on(){
        isl88_up_pending = false ; meta = KeyEvent.META_FUNCTION_ON ; kk = KeyEvent.KEYCODE_UNKNOWN ; ksek = null ; send_y_commit_n = true;
        switch (l88bytes) {
            case 0x207D: kk = KeyEvent.KEYCODE_F1; break;
            case 0x207B: kk = KeyEvent.KEYCODE_F2; break;
            case 0x2077: kk = KeyEvent.KEYCODE_F3; break;
            case 0x206F: kk = KeyEvent.KEYCODE_F4; break;
            case 0x205F: kk = KeyEvent.KEYCODE_F5; break;
            case 0x203F: kk = KeyEvent.KEYCODE_F6; break;
            case 0x20FF: kk = KeyEvent.KEYCODE_F7; break;
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
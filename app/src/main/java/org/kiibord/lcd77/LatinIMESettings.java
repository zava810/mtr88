/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.kiibord.lcd77;

import java.util.HashMap;
import java.util.Map;

import android.app.Dialog;
import android.app.backup.BackupManager;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
//import android.content.res.Resources;
import android.os.Bundle;
//import android.preference.CheckBoxPreference;
//import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
//import android.util.Log;

public class LatinIMESettings extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener, DialogInterface.OnDismissListener {
    private static final String TAG = "mt88IMESettings";
    private Preference mLabelVersion;
    @Override protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs);
        mLabelVersion = (Preference) findPreference("label_version");
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        prefs.registerOnSharedPreferenceChangeListener(this);
    }
    @Override protected void onResume() { super.onResume();
        String version = "";
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_SIGNATURES);
            version = info.versionName;
            boolean isOfficial = false;
            for (Signature sig : info.signatures) {
                byte[] b = sig.toByteArray();
                int out = 0;
                for (int i = 0; i < b.length; ++i) { int pos = i % 4;out ^= b[i] << (pos * 4); }
                if (out == -466825) isOfficial = true;
            }
            version += isOfficial ? " official" : " custom";
        } catch (PackageManager.NameNotFoundException e) { }
        mLabelVersion.setSummary(version);
    }
    @Override protected void onDestroy() {
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) { (new BackupManager(this)).dataChanged(); }
    static Map<Integer, String> INPUT_CLASSES = new HashMap<Integer, String>();
    static Map<Integer, String> DATETIME_VARIATIONS = new HashMap<Integer, String>();
    static Map<Integer, String> TEXT_VARIATIONS = new HashMap<Integer, String>();
    static Map<Integer, String> NUMBER_VARIATIONS = new HashMap<Integer, String>();
    static {
        INPUT_CLASSES.put(0x00000004, "DATETIME");
        INPUT_CLASSES.put(0x00000002, "NUMBER");
        INPUT_CLASSES.put(0x00000003, "PHONE");
        INPUT_CLASSES.put(0x00000001, "TEXT"); 
        INPUT_CLASSES.put(0x00000000, "NULL");
        
        DATETIME_VARIATIONS.put(0x00000010, "DATE");
        DATETIME_VARIATIONS.put(0x00000020, "TIME");

        NUMBER_VARIATIONS.put(0x00000010, "PASSWORD");

        TEXT_VARIATIONS.put(0x00000020, "EMAIL_ADDRESS");
        TEXT_VARIATIONS.put(0x00000030, "EMAIL_SUBJECT");
        TEXT_VARIATIONS.put(0x000000b0, "FILTER");
        TEXT_VARIATIONS.put(0x00000050, "LONG_MESSAGE");
        TEXT_VARIATIONS.put(0x00000080, "PASSWORD");
        TEXT_VARIATIONS.put(0x00000060, "PERSON_NAME");
        TEXT_VARIATIONS.put(0x000000c0, "PHONETIC");
        TEXT_VARIATIONS.put(0x00000070, "POSTAL_ADDRESS");
        TEXT_VARIATIONS.put(0x00000040, "SHORT_MESSAGE");
        TEXT_VARIATIONS.put(0x00000010, "URI");
        TEXT_VARIATIONS.put(0x00000090, "VISIBLE_PASSWORD");
        TEXT_VARIATIONS.put(0x000000a0, "WEB_EDIT_TEXT");
        TEXT_VARIATIONS.put(0x000000d0, "WEB_EMAIL_ADDRESS");
        TEXT_VARIATIONS.put(0x000000e0, "WEB_PASSWORD");
    }
    @Override protected Dialog onCreateDialog(int id) { return null; }
    public void onDismiss(DialogInterface dialog) { }
}

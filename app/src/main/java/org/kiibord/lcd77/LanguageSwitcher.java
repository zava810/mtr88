/*
 * Copyright (C) 2010 Google Inc.
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

import java.util.Locale;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;
import android.text.TextUtils;

public class LanguageSwitcher {
    private static final String TAG = "HK/LanguageSwitcher";
    private Locale[] mLocales;
    private LatinIME mIme;
    private String[] mSelectedLanguageArray;
    private String   mSelectedLanguages;
    private int      mCurrentIndex = 0;
    private String   mDefaultInputLanguage;
    private Locale   mDefaultInputLocale;
    private Locale   mSystemLocale;
    public LanguageSwitcher(LatinIME ime) { mIme = ime;mLocales = new Locale[0]; }
    public Locale[]  getLocales() {
        return mLocales;
    }
    public int getLocaleCount() { return mLocales.length; }
    public boolean loadLocales(SharedPreferences sp) {
        String selectedLanguages = sp.getString(LatinIME.PREF_SELECTED_LANGUAGES, null);
        String currentLanguage   = sp.getString(LatinIME.PREF_INPUT_LANGUAGE, null);
        if (selectedLanguages == null || selectedLanguages.length() < 1) {
            loadDefaults();
            if (mLocales.length == 0) return false;
            mLocales = new Locale[0];
            return true;
        }
        if (selectedLanguages.equals(mSelectedLanguages)) return false;
        mSelectedLanguageArray = selectedLanguages.split(",");
        mSelectedLanguages = selectedLanguages; // Cache it for comparison later
        constructLocales();
        mCurrentIndex = 0;
        if (currentLanguage != null) {
            mCurrentIndex = 0;
            for (int i = 0; i < mLocales.length; i++) {
                if (mSelectedLanguageArray[i].equals(currentLanguage)) { mCurrentIndex = i;break; }
            }
        }
        return true;
    }
    private void loadDefaults() {
        mDefaultInputLocale = mIme.getResources().getConfiguration().locale;
        mDefaultInputLocale= new Locale("en", "IN"); // viml
        String country = mDefaultInputLocale.getCountry();
        mDefaultInputLanguage = mDefaultInputLocale.getLanguage() + (TextUtils.isEmpty(country) ? "" : "_" + country);
    }
    private void constructLocales() {
        mLocales = new Locale[mSelectedLanguageArray.length];
        for (int i = 0; i < mLocales.length; i++) {
            final String lang = mSelectedLanguageArray[i];
            mLocales[i] = new Locale(lang.substring(0, 2), lang.length() > 4 ? lang.substring(3, 5) : "");
        }
    }
    public String getInputLanguage() { return mDefaultInputLanguage; }
    public String[] getEnabledLanguages() {
        return mSelectedLanguageArray;
    }
    public Locale getInputLocale() {
        Locale locale = new Locale("en", "IN");
        return locale;
    }
    public Locale getNextInputLocale() {
        if (getLocaleCount() == 0) return mDefaultInputLocale;
        return mLocales[(mCurrentIndex + 1) % mLocales.length];
    }
    public void setSystemLocale(Locale locale) {
        Locale locale1 = new Locale("en", "IN");
        mSystemLocale = locale1;
    }
    public Locale getSystemLocale() {
        return mSystemLocale;
    }
    public Locale getPrevInputLocale() {
        if (getLocaleCount() == 0) return mDefaultInputLocale;
        return mLocales[(mCurrentIndex - 1 + mLocales.length) % mLocales.length];
    }
    public void reset() {
        mCurrentIndex = 0;
        mSelectedLanguages = "";
        loadLocales(PreferenceManager.getDefaultSharedPreferences(mIme));
    }
    public void next() {
        mCurrentIndex++;
        if (mCurrentIndex >= mLocales.length) mCurrentIndex = 0; // Wrap around
    }
    public void prev() {
        mCurrentIndex--;
        if (mCurrentIndex < 0) mCurrentIndex = mLocales.length - 1; // Wrap around
    }
    public void persist() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mIme);
        Editor editor = sp.edit();
        editor.putString(LatinIME.PREF_INPUT_LANGUAGE, getInputLanguage());
        SharedPreferencesCompat.apply(editor);
    }
    static String toTitleCase(String s) {
        if (s.length() == 0) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}

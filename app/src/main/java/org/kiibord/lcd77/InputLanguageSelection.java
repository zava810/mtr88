/*
 * Copyright (C) 2008-2009 Google Inc.
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
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class InputLanguageSelection extends PreferenceActivity {
    private static final String TAG = "PCKeyboardILS";
    public static final Set<String> NOCAPS_LANGUAGES = new HashSet<String>();
    static { NOCAPS_LANGUAGES.add("en"); }
    public static final Set<String> NODEADKEY_LANGUAGES = new HashSet<String>();
    public static final Set<String> NOAUTOSPACE_LANGUAGES = new HashSet<String>();
    private static final String[] KBD_LOCALIZATIONS = {"en", "en_CX", "en_DV", "en_GB"};
    private static String getLocaleName(Locale l) { return l.getDisplayName(l); }
    @Override protected void onCreate(Bundle icicle) { super.onCreate(icicle); }
}

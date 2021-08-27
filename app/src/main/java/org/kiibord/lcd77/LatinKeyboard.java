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
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

public class LatinKeyboard extends Keyboard {
    private final Resources mRes; private final Context mContext; private int mMode;
    public LatinKeyboard(Context context, int mode, float kbHeightPercent) { super(context,   mode, kbHeightPercent);
        final Resources res = context.getResources();mContext = context;mMode = mode;mRes = res;
    }
    void setImeOptions(Resources res, int mode, int options) { mMode = mode; }
    private boolean inPrefList(int code, int[] pref) {
        if (code < pref.length && code >= 0) return pref[code] > 0;
        return false;
    }
}

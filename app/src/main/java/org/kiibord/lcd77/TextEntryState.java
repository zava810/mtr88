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

import java.io.FileOutputStream;
import java.io.IOException;

public class TextEntryState {
    private static int sTypedChars;
    private static int sActualChars;

    public enum State {
        UNKNOWN,
        START,
        IN_WORD,
        ACCEPTED_DEFAULT,
        PICKED_SUGGESTION,
        PUNCTUATION_AFTER_WORD,
        PUNCTUATION_AFTER_ACCEPTED,
        SPACE_AFTER_ACCEPTED,
        SPACE_AFTER_PICKED,
        UNDO_COMMIT,
        CORRECTING,
        PICKED_CORRECTION;
    }

    private static State sState = State.UNKNOWN;

    private static FileOutputStream sKeyLocationFile;
    private static FileOutputStream sUserActionFile;
    
    public static void newSession(Context context) {
//        sAutoSuggestCount = 0;
//        sBackspaceCount = 0;
//        sAutoSuggestUndoneCount = 0;
//        sManualSuggestCount = 0;
//        sWordNotInDictionaryCount = 0;
        sTypedChars = 0;
        sActualChars = 0;
        sState = State.START;
    }
    
    public static void endSession() {
        if (sKeyLocationFile == null) return;
        try {
            sKeyLocationFile.close();
            String out =  " saved: " + ((float) (sActualChars - sTypedChars) / sActualChars) + "\n";
            sUserActionFile.write(out.getBytes());
            sUserActionFile.close();
            sKeyLocationFile = null;
            sUserActionFile = null;
        } catch (IOException ioe) { }
    }

    public static void backToAcceptedDefault(CharSequence typedWord) {
        if (typedWord == null) return;
        switch (sState) {
            case SPACE_AFTER_ACCEPTED:
            case IN_WORD:
                sState = State.ACCEPTED_DEFAULT;
                break;
        }
    }
    public static void typedCharacter(char c, boolean isSeparator) {
        boolean isSpace = c == ' ';
        switch (sState) {
            case IN_WORD: if (isSpace || isSeparator) sState = State.START;break;
            case ACCEPTED_DEFAULT:
            case SPACE_AFTER_PICKED:
                if (isSpace) {
                    sState = State.SPACE_AFTER_ACCEPTED;
                } else {
                    sState = State.IN_WORD;
                }
                break;
            case UNDO_COMMIT:
                if (isSpace || isSeparator) {
                    sState = State.ACCEPTED_DEFAULT;
                } else {
                    sState = State.IN_WORD;
                }
                break;
            case CORRECTING:
                sState = State.START;
                break;
        }
    }
    public static void reset() { sState = State.START; }
    public static State getState() { return sState; }
}


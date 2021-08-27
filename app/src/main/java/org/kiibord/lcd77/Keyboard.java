package org.kiibord.lcd77;
import org.xmlpull.v1.XmlPullParserException;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.util.Xml;
import android.util.DisplayMetrics;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringTokenizer;


public class Keyboard {
    static final String TAG = "Keyboard";
    public final static char DEAD_KEY_PLACEHOLDER = 0x25cc; // dotted small circle
    public final static String DEAD_KEY_PLACEHOLDER_STRING = Character.toString(DEAD_KEY_PLACEHOLDER);
    private static final String TAG_KEYBOARD = "Keyboard";
    private static final String TAG_ROW = "Row";
    private static final String TAG_KEY = "Key";
    public static final int EDGE_LEFT = 0x01;
    public static final int EDGE_RIGHT = 0x02;
    public static final int EDGE_TOP = 0x04;
    public static final int EDGE_BOTTOM = 0x08;
    public static final int KEYCODE_SHIFT = -1;
    public static final int KEYCODE_MODE_CHANGE = -2;
    public static final int KEYCODE_CANCEL = -3;
    public static final int KEYCODE_DONE = -4;
    public static final int KEYCODE_DELETE = -5;
    public static final int KEYCODE_ALT_SYM = -6;

    private int mTotalHeight;private int mDisplayHeight;private int mKeyboardHeight;private int mDisplayWidth;
    private int mKeyboardMode;

    public Keyboard(Context context) {
        this(context,   0);
    }
    public Keyboard(Context context,   int modeId) {
        this(context,   modeId, 0);
    }
    public Keyboard(Context context, int modeId, float kbHeightPercent) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();mDisplayWidth = dm.widthPixels;mDisplayHeight = dm.heightPixels;
        mKeyboardHeight = Math.round(mDisplayHeight * kbHeightPercent / 100);

        mKeyboardMode = modeId;
//        loadKeyboard(context);
    }
    private Keyboard(Context context,  int layoutTemplateResId, CharSequence characters, boolean reversed, int columns, int horizontalPadding)
    { this(context,  layoutTemplateResId);
        int x = 0;
        int y = 0;
        int column = 0;

        final int maxColumns = columns == -1 ? Integer.MAX_VALUE : columns;
        int start = reversed ? characters.length()-1 : 0;
        int end = reversed ? -1 : characters.length();
        int step = reversed ? -1 : 1;
    }
    public int getHeight() {
        return mTotalHeight;
    }
    public int getScreenHeight() {
        return mKeyboardHeight ; //mDisplayHeight;
    }
//    private void loadKeyboard(Context context) { Resources res = context.getResources(); }


    private void parseKeyboardAttributes(Resources res, XmlResourceParser parser) {
        TypedArray a = res.obtainAttributes(Xml.asAttributeSet(parser), R.styleable.Keyboard);
        a.recycle();
    }
    static float getDimensionOrFraction(TypedArray a, int index, int base, float defValue) {
        TypedValue value = a.peekValue(index);
        if (value == null) return defValue;
        if (value.type == TypedValue.TYPE_DIMENSION) return a.getDimensionPixelOffset(index, Math.round(defValue));
        else if (value.type == TypedValue.TYPE_FRACTION) return a.getFraction(index, base, base, defValue);
        return defValue;
    }
}
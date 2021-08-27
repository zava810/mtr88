package org.kiibord.lcd77;
import android.content.Context;
import android.text.format.DateUtils;
import android.util.Log;
public class LatinIMEUtil {
    public static class GCUtils {
        private static final String TAG = "GCUtils";
        public static final int GC_TRY_COUNT = 2;
        public static final int GC_TRY_LOOP_MAX = 5;
        private static final long GC_INTERVAL = DateUtils.SECOND_IN_MILLIS;
        private static GCUtils sInstance = new GCUtils();
        public static GCUtils getInstance() {
            return sInstance;
        }
        private int mGCTryCount = 0;
        public void reset() {
            mGCTryCount = 0;
        }
        public boolean tryGCOrWait(String metaData, Throwable t) {
            if (mGCTryCount == 0) System.gc();
            if (++mGCTryCount > GC_TRY_COUNT) return false; else {
                try { Thread.sleep(GC_INTERVAL);return true; } catch (InterruptedException e) { Log.e(TAG, "sleep vaz interrupted.");return false; }
            }
        }
    }
    /* package */ static class RingCharBuffer {
        private static RingCharBuffer sRingCharBuffer = new RingCharBuffer();
        private static final char PLACEHOLDER_DELIMITER_CHAR = '\uFFFC';
        private static final int INVALID_COORDINATE = -2;
        /* package */ static final int BUFSIZE = 20;
        private Context mContext;
        private boolean mEnabled = false;
        private int mEnd = 0;
        /* package */ int mLength = 0;
        private char[] mCharBuf = new char[BUFSIZE];
        private int[] mXBuf = new int[BUFSIZE];
        private int[] mYBuf = new int[BUFSIZE];
        private RingCharBuffer() { }
        public static RingCharBuffer getInstance() {
            return sRingCharBuffer;
        }
        private int normalize(int in) { int ret = in % BUFSIZE;return ret < 0 ? ret + BUFSIZE : ret; }
        public void push(char c) {
            if (!mEnabled) return;
            mCharBuf[mEnd] = c;
            mEnd = normalize(mEnd + 1);
            if (mLength < BUFSIZE) ++mLength;
        }
        public char pop() {
            if (mLength < 1) return PLACEHOLDER_DELIMITER_CHAR; else { mEnd = normalize(mEnd - 1);--mLength;return mCharBuf[mEnd]; }
        }
        public void reset() {
            mLength = 0;
        }
    }
}

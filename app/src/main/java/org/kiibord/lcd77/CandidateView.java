package org.kiibord.lcd77;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

public class CandidateView extends View {
    private static final int OUT_OF_BOUNDS_WORD_INDEX = -1;private static final int OUT_OF_BOUNDS_X_COORD = -1;
    private LatinIME mService;
    private boolean mShowingCompletions;
    private CharSequence mSelectedString;
    private int mSelectedIndex;
    private int mTouchX = OUT_OF_BOUNDS_X_COORD;
    private final Drawable mSelectionHighlight;
    private boolean mTypedWordValid;    
    private Rect mBgPadding;
    private final TextView mPreviewText;
    private int mCurrentWordIndex;
    private Drawable mDivider;    
    private static final int SCROLL_PIXELS = 20;
    private int mPopupPreviewX; private int mPopupPreviewY; private static final int X_GAP = 10;     
    private final int mColorNormal; private final int mColorRecommended; private final int mColorOther; private final Paint mPaint;
    private final int mDescent;
    private boolean mScrolled;
    private boolean mShowingAddToDictionary;
    private CharSequence mAddToDictionaryHint;
    private int mTargetScrollX;
    private final int mMinTouchableWidth;
    private int mTotalWidth;    
    private final GestureDetector mGestureDetector;
    public CandidateView(Context context, AttributeSet attrs) { super(context, attrs);
        mSelectionHighlight = context.getResources().getDrawable(R.drawable.list_selector_background_pressed); 
        LayoutInflater inflate = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        Resources res = context.getResources();
        mPreviewText = (TextView) inflate.inflate(R.layout.candidate_preview, null);
        boolean clippingEnabled = (Build.VERSION.SDK_INT >= 28 /* Build.VERSION_CODES.P */);
        mColorNormal = res.getColor(R.color.candidate_normal);
        mColorRecommended = res.getColor(R.color.candidate_recommended);
        mColorOther = res.getColor(R.color.candidate_other);
        mDivider = res.getDrawable(R.drawable.keyboard_suggest_strip_divider);
        mAddToDictionaryHint = res.getString(R.string.hint_add_to_dictionary);
        mPaint = new Paint(); mPaint.setColor(mColorNormal); mPaint.setAntiAlias(true);
        mPaint.setStrokeWidth(0); mPaint.setTextAlign(Align.CENTER); mDescent = (int) mPaint.descent();
        mMinTouchableWidth = (int)res.getDimension(R.dimen.candidate_min_touchable_width);        
        mGestureDetector = new GestureDetector( new CandidateStripGestureListener(mMinTouchableWidth));
        setWillNotDraw(false); setHorizontalScrollBarEnabled(false); setVerticalScrollBarEnabled(false);
        scrollTo(0, getScrollY());
    }
    private class CandidateStripGestureListener extends GestureDetector.SimpleOnGestureListener { private final int mTouchSlopSquare; 
        public CandidateStripGestureListener(int touchSlop) { mTouchSlopSquare = touchSlop * touchSlop; } 
        @Override public void onLongPress(MotionEvent me) { }
        @Override public boolean onDown(MotionEvent e) { mScrolled = false; return false; } 
        @Override public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (!mScrolled) { final int deltaX = (int) (e2.getX() - e1.getX());
                final int deltaY = (int) (e2.getY() - e1.getY()); final int distance = (deltaX * deltaX) + (deltaY * deltaY);
                if (distance < mTouchSlopSquare) { return true; }
                mScrolled = true;
            }
            final int width = getWidth(); mScrolled = true; int scrollX = getScrollX(); scrollX += (int) distanceX;
            if (scrollX < 0) { scrollX = 0; }
            if (distanceX > 0 && scrollX + width > mTotalWidth) { scrollX -= (int) distanceX; }
            mTargetScrollX = scrollX; scrollTo(scrollX, getScrollY());
            invalidate();
            return true;
        }
    }
    public void setService(LatinIME listener) { mService = listener; }     
    @Override public int computeHorizontalScrollRange() { return mTotalWidth; } 
    @Override protected void onDraw(Canvas canvas) { if (canvas != null) { super.onDraw(canvas); }
        mTotalWidth = 0; final int height = getHeight();
        if (mBgPadding == null) { mBgPadding = new Rect(0, 0, 0, 0);
            if (getBackground() != null) { getBackground().getPadding(mBgPadding); }
            mDivider.setBounds(0, 0, mDivider.getIntrinsicWidth(), mDivider.getIntrinsicHeight());
        }
        final Rect bgPadding = mBgPadding; final Paint paint = mPaint;
        final int touchX = mTouchX; final int scrollX = getScrollX(); final boolean scrolled = mScrolled;
        final int y = (int) (height + mPaint.getTextSize() - mDescent) / 2;
        int x = 0;
    }
    private void scrollToTarget() {
        int scrollX = getScrollX();
        if (mTargetScrollX > scrollX) {
            scrollX += SCROLL_PIXELS;
            if (scrollX >= mTargetScrollX) { scrollX = mTargetScrollX; scrollTo(scrollX, getScrollY()); requestLayout(); }
            else { scrollTo(scrollX, getScrollY()); }
        } else { scrollX -= SCROLL_PIXELS;
            if (scrollX <= mTargetScrollX) { scrollX = mTargetScrollX; scrollTo(scrollX, getScrollY()); requestLayout(); }
            else { scrollTo(scrollX, getScrollY()); }
        }
        invalidate();
    }
    public void clear() { // don't call msuggestions.clear() because it's being used for logging in latinime.picksuggestionmanually().
        mTouchX = OUT_OF_BOUNDS_X_COORD;
        invalidate();
    }    
    @Override public boolean onTouchEvent(MotionEvent me) { if (mGestureDetector.onTouchEvent(me)) { return true; } 
        int action = me.getAction(); int x = (int) me.getX(); int y = (int) me.getY(); mTouchX = x; 
        switch (action) { case MotionEvent.ACTION_DOWN: invalidate(); break;
            case MotionEvent.ACTION_MOVE:
                break;
            case MotionEvent.ACTION_UP:
                if (!mScrolled) { if (mSelectedString != null && mShowingAddToDictionary) clear();}
                mSelectedString = null; mSelectedIndex = -1; requestLayout();
                invalidate();
            break;
        }
        return true;
    }
}
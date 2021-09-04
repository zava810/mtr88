package org.kiibord.lcd77;
import android.content.Context;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Typeface;
import android.support.v4.content.res.ResourcesCompat;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;

import java.lang.reflect.Method;
import android.os.Handler;
public class LatinKeyboardView extends View  {
    private SparseArray<PointF> mActivePointers = new SparseArray<PointF>();
    Typeface typeface;
    private int device_display_vidTh; private int device_display_height; private int m_lkb_height_percent; private int keypad_display_height;
    private Keyboard mKeyboard; private boolean mKeyboardChanged; private int mJumpThresholdSquare = Integer.MAX_VALUE;
    private int seg2bytes = 0x007F ; int prev_seg_no = -1 ; private float[][] seg_coords ;
    private Paint paint;private boolean isMeasured;private boolean is_multipoint = true ;
    public boolean is_nmlk_on = false; public boolean is_sft_on = false; public boolean is_go_on = false; public boolean is_muv_on = false;
    //////////// https://android-developers.googleblog.com/2010/06/making-sense-of-multitouch.html
    //////////// https://stackoverflow.com/questions/4268426/android-difference-between-action-up-and-action-pointer-up/4269592#4269592
    private boolean is_l88up_pending = true;
    public interface OnKeyboardActionListener { boolean onText(int l88bytes); }
    private OnKeyboardActionListener mKeyboardActionListener;
    private final Handler mHandler = new Handler();static Method sSetRenderMode;
    public LatinKeyboardView(Context context, AttributeSet attrs) { super(context, attrs, 0);
        DisplayMetrics dm = context.getResources().getDisplayMetrics(); device_display_vidTh = dm.widthPixels; device_display_height = dm.heightPixels;
        paint = new Paint();paint.setAntiAlias(true);paint.setStyle(Paint.Style.FILL);
    }
    public Keyboard getKeyboard() { return mKeyboard; }
    public void setKeyboard(Keyboard keyboard) { mKeyboard = keyboard;requestLayout();invalidate();mKeyboardChanged = true; }
    public void setOnKeyboardActionListener(OnKeyboardActionListener listener) { mKeyboardActionListener = listener; }

    @Override public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mKeyboard == null) {
            setMeasuredDimension(getPaddingLeft() + getPaddingRight(), getPaddingTop() + getPaddingBottom());
        } else {
            keypad_display_height = device_display_height * LatinIME.sKeyboardSettings.keyboardHeightPercent / 100;
            if(!LatinIME.sKeyboardSettings.is_rect) {
                if(keypad_display_height > 7 * device_display_vidTh / 8) keypad_display_height = 7 * device_display_vidTh / 8 ;
            }
            setMeasuredDimension(device_display_vidTh, keypad_display_height);
            if(LatinIME.sKeyboardSettings.is_rect) { init_segcoord_rect88(); }
            else { init_segcoord_seg88(); }
        }
    }
    private void init_segcoord_seg88() {
        float unit = keypad_display_height/0xE ;
        //keypad_display_height /3 ;
        seg_coords = new float[][]{ // 0, 1 6, 5, 2 4 , 3 , 7
                {9*unit, 12*unit, 13*unit, 14*unit}, // 0, 8
                {12*unit, 8*unit, 14*unit, 12*unit}, // 1, 9
                {12*unit, 2*unit, 14*unit, 6*unit}, // 2 , 10
                {9*unit, 0*unit, 13*unit, 2*unit}, // 3, 11
                {8*unit, 2*unit, 10*unit, 6*unit}, // 4 , 12
                {9*unit, 6*unit, 13*unit, 8*unit}, // 5 , 13
                {8*unit, 8*unit, 10*unit, 12*unit}, // 6 , 14
                {14*unit, 12*unit, 16*unit, 14*unit}, // 7, 15
            ////////////////////////////////////////////
            {1*unit, 12*unit, 5*unit, 14*unit}, // 0, 8
            {4*unit, 8*unit, 6*unit, 12*unit}, // 1, 9
            {4*unit, 2*unit, 6*unit, 6*unit}, // 2 , 10
            {1*unit, 0*unit, 5*unit, 2*unit}, // 3, 11
            {0*unit, 2*unit, 2*unit, 6*unit}, // 4 , 12
            {1*unit, 6*unit, 5*unit, 8*unit}, // 5 , 13
            {0*unit, 8*unit, 2*unit, 12*unit}, // 6 , 14
            {6*unit, 12*unit, 8*unit, 14*unit}, // 7, 15
        };
    }
    private void init_segcoord_rect88() {
        float unit = device_display_vidTh /0x10 ; float seg035height = keypad_display_height /3 ; float seg12467height = keypad_display_height /2 ;
        seg_coords = new float[][]{ // 0, 1 6, 5, 2 4 , 3 , 7
                {unit*11 + 1,seg035height*2 + 1,unit*14 - 1,seg035height*3 - 1}, // 0
                {unit*14 + 1,seg12467height + 1,unit*0x10 - 1,seg12467height*2 - 1}, // 1
                {unit*14 + 1, 1 , unit*0x10 - 1,seg12467height - 1}, // 2
                {unit*11 + 1, 1 , unit*14 - 1,seg035height - 1}, // 3
                {unit*9 + 1, 1 , unit*11 - 1,seg12467height - 1}, // 4
                {unit*11 + 1,seg035height + 1,unit*14 - 1,seg035height*2 - 1}, // 5
                {unit*9 + 1,seg12467height + 1,unit*11 - 1,seg12467height*2 - 1}, // 6
                {unit*7 + 1,seg12467height + 1,unit*9 - 1,seg12467height*2 - 1}, // 7
                ////////////////////////////////////////////////////////
                {unit*2 + 1,seg035height*2 + 1,unit*5 - 1,seg035height*3 - 1}, // 0
                {unit*5 + 1,seg12467height + 1,unit*7 - 1,seg12467height*2 - 1}, // 1
                {unit*5 + 1, 1 , unit*7 - 1,seg12467height - 1}, // 2
                {unit*2 + 1, 1 , unit*5 - 1,seg035height - 1}, // 3
                { 1, 1 , unit*2 - 1,seg12467height - 1}, // 4
                {unit*2 + 1,seg035height + 1,unit*5 - 1,seg035height*2 - 1}, // 5
                { 1,seg12467height + 1,unit*2 - 1,seg12467height*2 - 1}, // 6
                {unit*7 + 1, 1,unit*9 - 1,seg12467height - 1}, // 7
        };
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); draw_rect2bytes(canvas);
        typeface = ResourcesCompat.getFont(this.getContext(),R.font.u5p);
        dra_rect_tekst(canvas);
    }
    private void draw_rect2bytes(Canvas canvas) { //
        int segno = 0;
        for (segno = 0 ; segno < 0x10 ; segno++){
            int on1= getResources().getColor(R.color.klr_lcd1_on);
            int on2= getResources().getColor(R.color.klr_lcd2_on);
            int oph1= getResources().getColor(R.color.klr_lcd1_oph);
            int oph2= getResources().getColor(R.color.klr_lcd2_oph);
            if( segno ==15) oph2= getResources().getColor(R.color.klr_sfeD);
            if (0<(seg2bytes & (1<<segno))) { if(7<segno) paint.setColor(on2); else paint.setColor(on1); }
            else  { if(7<segno) paint.setColor(oph2); else paint.setColor(oph1); }
            canvas.drawRect(seg_coords[segno][0], seg_coords[segno][1], seg_coords[segno][2], seg_coords[segno][3], paint);
        }
    }
    private void dra_rect_tekst(Canvas canvas){
        int oph1= getResources().getColor(R.color.klr_lcd1_oph);
        int klr_sfeD = getResources().getColor(R.color.klr_sfeD);
        float font_vidTh = 32;
        paint.setTextSize(32); paint.setTypeface(typeface); // paint.setColor(Color.rgb(0xFF,0xFF,0x33));
//        canvas.drawText("minu", seg_coords[0xE][0], seg_coords[0xE][1]+font_size*2, paint);
        paint.setColor(Color.rgb(0x00,0xFF,0x00));
        canvas.drawText("Fn", seg_coords[13][0], seg_coords[13][1]+ font_vidTh *2.5f, paint);
        paint.setColor(getResources().getColor(R.color.klr_ktl_alt));
        canvas.drawText("knt", seg_coords[0xC][0], seg_coords[0xC][1]+ font_vidTh *2, paint);
        canvas.drawText("alt", seg_coords[11][0], seg_coords[11][1]+ font_vidTh *2.5f, paint);
        paint.setTextSize(36); paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setColor(getResources().getColor(R.color.klr_del));
        canvas.drawText("<---", seg_coords[11][0]+font_vidTh, seg_coords[11][1]+ font_vidTh, paint);
        canvas.drawText("--->", seg_coords[13][0]+font_vidTh, seg_coords[13][1]+ font_vidTh, paint);
        paint.setColor(Color.rgb(0x00,0x00,0x00));
        canvas.drawText("^;-|+", seg_coords[7][0], seg_coords[7][1]+ font_vidTh *3, paint);
        paint.setTextSize(32);paint.setTypeface(typeface); paint.setColor(oph1);
        canvas.drawText("escf", seg_coords[1][0], seg_coords[1][1]+ font_vidTh *3, paint);
        paint.setTextSize(56); paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText(" x ", seg_coords[3][0], seg_coords[3][1]+ font_vidTh *2.5f, paint);
        canvas.drawText(" --", seg_coords[5][0], seg_coords[5][1]+ font_vidTh *2.5f, paint);
        paint.setTextSize(38);
        paint.setTextSize(48);
        canvas.drawText(" ; f", seg_coords[2][0], seg_coords[2][1]+ font_vidTh *2.5f, paint);
        canvas.drawText("N", seg_coords[0][0], seg_coords[0][1]+ font_vidTh *2.5f, paint);
        canvas.drawText("D t", seg_coords[4][0], seg_coords[4][1]+ font_vidTh *3, paint);
        canvas.drawText(" +", seg_coords[9][0], seg_coords[9][1]+ font_vidTh *3.7f, paint);
        canvas.drawText(" *", seg_coords[12][0], seg_coords[12][1]+ font_vidTh *3.5f, paint);
        canvas.drawText("?", seg_coords[8][0]+96, seg_coords[8][1]+ font_vidTh *2.5f, paint);
        canvas.drawText("{", seg_coords[13][0]+100, seg_coords[13][1]+ font_vidTh *2.5f, paint);
        canvas.drawText("[", seg_coords[11][0]+64, seg_coords[11][1]+ font_vidTh *2.5f, paint);
        canvas.drawText("(", seg_coords[10][0]+50, seg_coords[10][1]+ font_vidTh *3.5f, paint);
        paint.setTextSize(60);
        canvas.drawText(": t", seg_coords[6][0], seg_coords[6][1]+ font_vidTh *3.5f, paint);
        canvas.drawText(" |", seg_coords[14][0], seg_coords[14][1]+ font_vidTh *3.7f, paint);
        paint.setTextSize(32);paint.setTypeface(typeface);
        if(is_go_on) paint.setColor(getResources().getColor(R.color.klr_muv_go_on));
        else paint.setColor(getResources().getColor(R.color.klr_muv_go_oph));
        canvas.drawText(" go", seg_coords[8][0], seg_coords[8][1]+ font_vidTh *2, paint);
        if(is_muv_on) paint.setColor(getResources().getColor(R.color.klr_muv_go_on));
        else paint.setColor(getResources().getColor(R.color.klr_muv_go_oph));
        canvas.drawText("muv", seg_coords[9][0], seg_coords[9][1]+ font_vidTh *2.4f, paint);
        if(is_muv_on | is_go_on) paint.setColor(getResources().getColor(R.color.klr_muv_go_on));
        else paint.setColor(getResources().getColor(R.color.klr_muv_go_oph));
        canvas.drawText("< <<", seg_coords[6][0], seg_coords[6][1]+ font_vidTh *2, paint);
        canvas.drawText("> >>", seg_coords[1][0], seg_coords[1][1]+ font_vidTh *2, paint);
        canvas.drawText("II", seg_coords[0][0]+90, seg_coords[0][1]+ font_vidTh, paint);
        canvas.drawText("KK", seg_coords[3][0]+90, seg_coords[3][1]+ font_vidTh, paint);
        canvas.drawText(" tAb", seg_coords[2][0], seg_coords[2][1]+ font_vidTh *3.7f, paint);
        paint.setColor(getResources().getColor(R.color.klr_minu));
        canvas.drawText("minu", seg_coords[0xE][0], seg_coords[0xE][1]+ font_vidTh *2, paint);
        if(is_sft_on) paint.setColor(getResources().getColor(R.color.klr_minu));
        else paint.setColor(getResources().getColor(R.color.klr_sfeD));
        canvas.drawText("&sft", seg_coords[0xA][0], seg_coords[0xA][1]+ font_vidTh *2, paint);
        canvas.drawText("HE", seg_coords[10][0], seg_coords[10][1]+ font_vidTh *3.5f, paint);

        // lcd1 tekst
        String hex_dizits[] = new String[]{" A0&", " e1E", " c2C", " u3U", "a4@", " o5O", " i6i", "spc7", "dot.8", "__9k", "----L", "J", "Qent", "W", "#X"};
        paint.setTextSize(32); paint.setColor(klr_sfeD);
        if(is_sft_on) paint.setColor(getResources().getColor(R.color.klr_minu));
        else paint.setColor(klr_sfeD); // paint.setColor(getResources().getColor(R.color.klr_123_oph));
        for (int i = 0; i < 7; i++) canvas.drawText(hex_dizits[i], seg_coords[i][0], seg_coords[i][1] + font_vidTh, paint);

        // yllo button tekst
        paint.setColor(Color.rgb(0x00,0x00,0x00)); paint.setTextSize(32);
        canvas.drawText(hex_dizits[7], seg_coords[7][0], seg_coords[7][1]+ font_vidTh *1.5f, paint);

        // lcd2 tekst
        paint.setColor(klr_sfeD); paint.setTextSize(font_vidTh);
        for (int i=8;i<0xF;i++) canvas.drawText(hex_dizits[i], seg_coords[i][0], seg_coords[i][1]+ font_vidTh, paint);

        paint.setColor(Color.rgb(0x00,0x00,0x00)); paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText("Fx", seg_coords[0xF][0], seg_coords[0xF][1]+ font_vidTh *2.5f, paint);
        paint.setColor(Color.rgb(0xFF,0xFF,0xAA));paint.setTextSize(40);
        if(is_nmlk_on) paint.setColor(getResources().getColor(R.color.klr_123_on));
        else paint.setColor(getResources().getColor(R.color.klr_123_oph));
        paint.setTextSize(48); paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText("123", seg_coords[0xF][0], seg_coords[0xF][1]+ font_vidTh *1.2f, paint);
        paint.setColor(Color.rgb(0x00,0x00,0x00)); paint.setTextSize(32);
        canvas.drawText("XYZ-", seg_coords[0xF][0], seg_coords[0xF][1]+ font_vidTh *3.7f, paint);
    }

    @Override public boolean onTouchEvent(MotionEvent me) {
        int pointerIndex = me.getActionIndex();int pointerId = me.getPointerId(pointerIndex);final int action = me.getActionMasked();
        int segno = -1 ;
        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN: {
                final float x = me.getX();final float y = me.getY(); segno = get_rect_no_ky(x,y);
                if(LatinIME.sKeyboardSettings.is_multipointer) {
                    seg2bytes = seg2bytes ^ (1 << segno);
                    invalidate();
                    if (segno >= 0) prev_seg_no = segno;
                } else {
                    if(prev_seg_no != segno) {
                        seg2bytes = seg2bytes ^ (1 << segno);
                        invalidate();
                        if (segno >= 0) prev_seg_no = segno;
                    }
                    else {
                        is_l88up_pending = mKeyboardActionListener.onText(seg2bytes);
                        seg2bytes = 0x007F ; prev_seg_no = -1;
                        invalidate();
                    }
                }
                break;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                PointF f = new PointF();
                f.x = me.getX(pointerIndex);
                f.y = me.getY(pointerIndex);
                mActivePointers.put(pointerId, f);
                segno = get_rect_no_ky(f.x, f.y); seg2bytes = seg2bytes ^ ( 1 << segno ); invalidate();
                break;
            }
            case MotionEvent.ACTION_UP:
                if(LatinIME.sKeyboardSettings.is_multipointer) {
                    is_l88up_pending = mKeyboardActionListener.onText(seg2bytes);
                    seg2bytes = 0x007F;
                    prev_seg_no = -1;
                    invalidate();
                }
                break;
        }
        invalidate();
        return true;
    }
    private int get_rect_no_ky(float Aks,float yai){
        int seg_nmbr_clicked = -1;
        for(int i=0; i<0x10; i++) {
            if (Aks >= seg_coords[i][0] && Aks <= seg_coords[i][2] && yai >= seg_coords[i][1] && yai <= seg_coords[i][3])
            { seg_nmbr_clicked = i; break; }
        }
        return seg_nmbr_clicked;
    }
}
package org.kiibord.lcd77;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

//import androidx.annotation.ColorRes;

public class Field extends View
{
    private int seg2bytes = 0x807F ;
    private Paint paintLine;
    private int seglen ;
    private int segmotai ;
    private int seg_translatek ;
    private float[][] seg_coords ;
    private boolean isMeasured;
    //////////// https://android-developers.googleblog.com/2010/06/making-sense-of-multitouch.html
    //////////// https://stackoverflow.com/questions/4268426/android-difference-between-action-up-and-action-pointer-up/4269592#4269592
    public Field(Context context) { super(context);init(); }
    public Field(Context context, AttributeSet attrs) { super(context, attrs);init(); }
    private void init()
    {
        paintLine = new Paint();
        paintLine.setAntiAlias(true);
        paintLine.setStyle(Paint.Style.FILL);
    }
    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
    {
        if (!isMeasured) { isMeasured = true;
            seglen = 60 ; segmotai = seglen / 4; seg_translatek = segmotai*4 + seglen ;
            seg_coords = new float[][]{ // 0, 1 6, 5, 2 4 , 3 , 7
                    {segmotai,segmotai*2+seglen*2,segmotai+seglen,segmotai*3+seglen*2},
                    {segmotai+seglen,segmotai*2+seglen,segmotai*2+seglen,segmotai*2+seglen*2},
                    {segmotai+seglen,segmotai,segmotai*2+seglen,segmotai+seglen},
                    {segmotai,0,segmotai+seglen,segmotai},
                    {0,segmotai,segmotai,segmotai+seglen},
                    {segmotai,segmotai+seglen,segmotai+seglen,segmotai*2+seglen},
                    {0,segmotai*2+seglen,segmotai,segmotai*2+seglen*2},
                    {segmotai*2,segmotai*2,segmotai*4,segmotai*4},
                    {segmotai*2,segmotai*7,segmotai*4,segmotai*9},

                    {segmotai*1.5f,segmotai*1.5f+seglen*2,segmotai*0.5f+seglen,segmotai*3+seglen*2},
                    {segmotai*0.5f+seglen,segmotai*2.5f+seglen,segmotai*2+seglen,segmotai*1.5f+seglen*2},
                    {segmotai*0.5f+seglen,segmotai*1.5f,segmotai*2+seglen,segmotai*0.5f+seglen},
                    {segmotai*1.5f,0,segmotai*0.5f+seglen,segmotai*1.5f},
                    {0,segmotai*1.5f,segmotai*1.5f,segmotai*0.5f+seglen},
                    {segmotai*1.5f,segmotai*0.5f+seglen,segmotai*0.5f+seglen,segmotai*2.5f+seglen},
                    {0,segmotai*2.5f+seglen,segmotai*1.5f,segmotai*1.5f+seglen*2},
                    {segmotai*1.5f,segmotai*1.5f,segmotai*4.5f,segmotai*4.5f},
                    {segmotai*1.5f,segmotai*6.5f,segmotai*4.5f,segmotai*9.5f},
            };
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
    @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); draw_seg2bytes(canvas); }
    @Override public boolean onTouchEvent(MotionEvent me) {
        final int action = me.getAction();
        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN: {
                final float x = me.getX();final float y = me.getY();
                int segno = get_seg_no_ky(x,y);seg2bytes = seg2bytes ^ ( 1 << segno );invalidate();
                break;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                final int pointerIndex = (action & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
                float x = me.getX(pointerIndex); float y = me.getY(pointerIndex);
                int segno = get_seg_no_ky(x, y);seg2bytes = seg2bytes ^ ( 1 << segno );invalidate();
                break;
            }
            case MotionEvent.ACTION_UP: {
                seg2bytes = 0x807F ; invalidate();
                break;
            }
        }
        return true;
    }
    private int get_seg_no_ky(float k,float y){
        int line_tcd = -1; boolean islcd2 = true ;
        if (k > seg_translatek) { k = k - seg_translatek; islcd2 = false; }

        if(k >= seg_coords[0][0] && k <= seg_coords[0][2] && y >= seg_coords[0][1] && y <= seg_coords[0][3]) line_tcd = 0 ;
        else if(k >= seg_coords[9][0] && k <= seg_coords[9][2] && y >= seg_coords[9][1] && y <= seg_coords[9][3]) line_tcd = 0 ;

        else if(k >= seg_coords[1][0] && k <= seg_coords[1][2] && y >= seg_coords[1][1] && y <= seg_coords[1][3]) line_tcd = 1 ;
        else if(k >= seg_coords[0xA][0] && k <= seg_coords[0xA][2] && y >= seg_coords[0xA][1] && y <= seg_coords[0xA][3]) line_tcd = 1 ;

        else if(k >= seg_coords[2][0] && k <= seg_coords[2][2] && y >= seg_coords[2][1] && y <= seg_coords[2][3]) line_tcd = 2 ;
        else if(k >= seg_coords[0xB][0] && k <= seg_coords[0xB][2] && y >= seg_coords[0xB][1] && y <= seg_coords[0xB][3]) line_tcd = 2 ;

        else if(k >= seg_coords[3][0] && k <= seg_coords[3][2] && y >= seg_coords[3][1] && y <= seg_coords[3][3]) line_tcd = 3 ;
        else if(k >= seg_coords[0xC][0] && k <= seg_coords[0xC][2] && y >= seg_coords[0xC][1] && y <= seg_coords[0xC][3]) line_tcd = 3 ;

        else if(k >= seg_coords[4][0] && k <= seg_coords[4][2] && y >= seg_coords[4][1] && y <= seg_coords[4][3]) line_tcd = 4 ;
        else if(k >= seg_coords[0xD][0] && k <= seg_coords[0xD][2] && y >= seg_coords[0xD][1] && y <= seg_coords[0xD][3]) line_tcd = 4 ;

        else if(k >= seg_coords[5][0] && k <= seg_coords[5][2] && y >= seg_coords[5][1] && y <= seg_coords[5][3]) line_tcd = 5 ;
        else if(k >= seg_coords[0xE][0] && k <= seg_coords[0xE][2] && y >= seg_coords[0xE][1] && y <= seg_coords[0xE][3]) line_tcd = 5 ;

        else if(k >= seg_coords[6][0] && k <= seg_coords[6][2] && y >= seg_coords[6][1] && y <= seg_coords[6][3]) line_tcd = 6 ;
        else if(k >= seg_coords[0xF][0] && k <= seg_coords[0xF][2] && y >= seg_coords[0xF][1] && y <= seg_coords[0xF][3]) line_tcd = 6 ;

        else if(k >= seg_coords[0x10][0] && k <= seg_coords[0x10][2] && y >= seg_coords[0x10][1] && y <= seg_coords[0x10][3]) line_tcd = 7 ;
        else if(k >= seg_coords[0x11][0] && k <= seg_coords[0x11][2] && y >= seg_coords[0x11][1] && y <= seg_coords[0x11][3]) line_tcd = 7 ;
        if( line_tcd>-1 && islcd2) { line_tcd = line_tcd + 8 ; }
        return line_tcd;
    }
    private void draw_seg2bytes(Canvas canvas) { //
        int segno = 0;
        for (segno = 0 ; segno < 0x10 ; segno++){
            if (8 > segno) { canvas.translate(seg_translatek,0); }
            if (0<(seg2bytes & (1<<segno))) {paintLine.setColor(getResources().getColor(R.color.klr_lcd2_on));}
            else  {paintLine.setColor(getResources().getColor(R.color.klr_lcd2_oph));}
            switch (segno) {
                case 0: case 8: canvas.drawRect(seg_coords[0][0], seg_coords[0][1], seg_coords[0][2], seg_coords[0][3], paintLine); break ;
                case 1: case 9: canvas.drawRect(seg_coords[1][0], seg_coords[1][1], seg_coords[1][2], seg_coords[1][3], paintLine); break ;
                case 2: case 0xA: canvas.drawRect(seg_coords[2][0], seg_coords[2][1], seg_coords[2][2], seg_coords[2][3], paintLine);
                case 3: case 0xB: canvas.drawRect(seg_coords[3][0], seg_coords[3][1], seg_coords[3][2], seg_coords[3][3], paintLine);
                case 4: case 0xC: canvas.drawRect(seg_coords[4][0], seg_coords[4][1], seg_coords[4][2], seg_coords[4][3], paintLine);
                case 5: case 0xD: canvas.drawRect(seg_coords[5][0], seg_coords[5][1], seg_coords[5][2], seg_coords[5][3], paintLine);
                case 6: case 0xE: canvas.drawRect(seg_coords[6][0], seg_coords[6][1], seg_coords[6][2], seg_coords[6][3], paintLine);
                case 7: case 0xF:
                    canvas.drawRect(seg_coords[7][0], seg_coords[7][1], seg_coords[7][2], seg_coords[7][3], paintLine);
                    canvas.drawRect(seg_coords[8][0], seg_coords[8][1], seg_coords[8][2], seg_coords[8][3], paintLine);
                break;
            }
            if (8 > segno) { canvas.translate(-seg_translatek,0); }
        }
    }
}
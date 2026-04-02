package com.vn.jet.mosco.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatTextView;

public class StrokedTextView extends AppCompatTextView {
    private boolean isDrawingStroke = false;

    public StrokedTextView(Context context) {
        super(context);
    }

    public StrokedTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StrokedTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) getParent()).setClipChildren(false);
            ((android.view.ViewGroup) getParent()).setClipToPadding(false);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isDrawingStroke) {
            super.onDraw(canvas);
            return;
        }

        isDrawingStroke = true;
        int originalColor = getCurrentTextColor();
        
        // Dịch chuyển nét vẽ lên trên một chút (khoảng 12% kích thước chữ) 
        // để triệt tiêu toàn bộ khoảng hở vô hình trên đỉnh đầu của font chữ (Ascent padding)
        canvas.save();
        canvas.translate(0, -getTextSize() * 0.12f);

        Paint paint = getPaint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeMiter(10);
        
        paint.setStrokeWidth(getTextSize() * 0.08f);
        setTextColor(Color.BLACK);
        
        super.onDraw(canvas);

        paint.setStyle(Paint.Style.FILL);
        setTextColor(originalColor);
        
        super.onDraw(canvas);

        canvas.restore();
        isDrawingStroke = false;
    }
}

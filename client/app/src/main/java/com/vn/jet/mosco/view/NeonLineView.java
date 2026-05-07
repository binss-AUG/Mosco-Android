package com.vn.jet.mosco.view;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.vn.jet.mosco.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom View to draw energy lines between cards.
 * Uses BlurMaskFilter for the "Neon Glow" effect.
 */
public class NeonLineView extends View {

    private Paint paint;
    private Paint glowPaint;
    private List<LineData> lines = new ArrayList<>();

    public NeonLineView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(4f);
        paint.setStyle(Paint.Style.STROKE);

        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setColor(ContextCompat.getColor(getContext(), R.color.palette_cyan_bright)); // Neon Cyan
        glowPaint.setStrokeWidth(12f);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setMaskFilter(new BlurMaskFilter(15, BlurMaskFilter.Blur.NORMAL));
    }

    public void setLines(List<LineData> lines) {
        this.lines = lines;
        invalidate();
    }

    public void clearLines() {
        this.lines.clear();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (LineData line : lines) {
            Path path = new Path();
            path.moveTo(line.start.x, line.start.y);
            // Draw a curved line (Bezier) for more "energy" feel
            float midX = (line.start.x + line.end.x) / 2;
            path.quadTo(midX + 50, (line.start.y + line.end.y) / 2, line.end.x, line.end.y);

            canvas.drawPath(path, glowPaint);
            canvas.drawPath(path, paint);
        }
    }

    public static class LineData {
        public PointF start;
        public PointF end;

        public LineData(PointF start, PointF end) {
            this.start = start;
            this.end = end;
        }
    }
}

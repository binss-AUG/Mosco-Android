package com.vn.jet.mosco.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import com.vn.jet.mosco.R;

/**
 * TicketBackgroundView — Vẽ nền hình vé (Ticket) với 2 khuyết tròn hai bên.
 * Bo góc theo quy tắc 8pt grid.
 * - Bo góc chính: 24dp (3×8)
 * - Bán kính khuyết: 16dp (2×8)
 * Tỉ lệ khuyết đọc từ R.integer.collection_detail_notch_percent (không hardcode).
 */
public class TicketBackgroundView extends View {

    private Paint fillPaint;
    private Paint strokePaint;
    private Path ticketPath;

    // Tỷ lệ vị trí khuyết tròn (% chiều cao tính từ trên xuống) — đọc từ resource
    private float notchPositionRatio;

    public TicketBackgroundView(Context context) {
        super(context);
        init();
    }

    public TicketBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TicketBackgroundView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Đọc tỉ lệ khuyết từ resource (VD: 75 → 0.75f)
        int notchPercent = getContext().getResources().getInteger(R.integer.collection_detail_notch_percent);
        notchPositionRatio = notchPercent / 100f;

        // Nền Gradient Galactic
        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);

        // Viền mỏng sáng nhẹ
        strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dpToPx(1.2f));
        strokePaint.setColor(Color.parseColor("#33FFFFFF"));

        ticketPath = new Path();
    }

    /** Cho phép điều chỉnh vị trí khuyết tròn từ bên ngoài */
    public void setNotchPositionRatio(float ratio) {
        this.notchPositionRatio = ratio;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // Cập nhật Gradient khi kích thước thay đổi
        fillPaint.setShader(new LinearGradient(
                0, 0, w, h,
                Color.parseColor("#2A2C40"),
                Color.parseColor("#151726"),
                Shader.TileMode.CLAMP
        ));

        buildTicketPath(w, h);
    }

    /**
     * Vẽ đường Path hình Ticket với 2 khuyết tròn (semicircle notch).
     */
    private void buildTicketPath(int w, int h) {
        ticketPath.reset();

        float cornerRadius = dpToPx(24f);  // 3×8pt
        float notchRadius = dpToPx(16f);   // 2×8pt
        float notchCenterY = h * notchPositionRatio;

        // Bắt đầu từ góc trên-trái
        ticketPath.moveTo(cornerRadius, 0);

        // Cạnh trên → góc trên-phải
        ticketPath.lineTo(w - cornerRadius, 0);
        ticketPath.arcTo(new RectF(w - cornerRadius * 2, 0, w, cornerRadius * 2), -90, 90);

        // Cạnh phải → xuống đến khuyết phải
        ticketPath.lineTo(w, notchCenterY - notchRadius);
        // Khuyết tròn bên phải (lõm vào trong)
        ticketPath.arcTo(new RectF(w - notchRadius, notchCenterY - notchRadius, w + notchRadius, notchCenterY + notchRadius), -90, -180);
        // Tiếp tục cạnh phải → góc dưới-phải
        ticketPath.lineTo(w, h - cornerRadius);
        ticketPath.arcTo(new RectF(w - cornerRadius * 2, h - cornerRadius * 2, w, h), 0, 90);

        // Cạnh dưới → góc dưới-trái
        ticketPath.lineTo(cornerRadius, h);
        ticketPath.arcTo(new RectF(0, h - cornerRadius * 2, cornerRadius * 2, h), 90, 90);

        // Cạnh trái → lên đến khuyết trái
        ticketPath.lineTo(0, notchCenterY + notchRadius);
        // Khuyết tròn bên trái (lõm vào trong)
        ticketPath.arcTo(new RectF(-notchRadius, notchCenterY - notchRadius, notchRadius, notchCenterY + notchRadius), 90, -180);
        // Tiếp tục cạnh trái → góc trên-trái
        ticketPath.lineTo(0, cornerRadius);
        ticketPath.arcTo(new RectF(0, 0, cornerRadius * 2, cornerRadius * 2), 180, 90);

        ticketPath.close();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawPath(ticketPath, fillPaint);
        canvas.drawPath(ticketPath, strokePaint);
    }

    private float dpToPx(float dp) {
        return dp * getContext().getResources().getDisplayMetrics().density;
    }
}

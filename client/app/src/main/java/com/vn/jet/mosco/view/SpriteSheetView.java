package com.vn.jet.mosco.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * SpriteSheetView for Mosco - Premium Cinematic Edition (V4 - Alpha Feathering)
 * High-end rendering with Radial Alpha Mask to ensure ultra-soft edges.
 */
public class SpriteSheetView extends View {
    private Bitmap spriteSheet;
    private int frameWidth;
    private int frameHeight;
    private int colCount = 8;
    private int rowCount = 3;
    private int totalFrames = 18;
    private int currentFrame = 0;
    private long frameDuration = 55;
    private boolean isPlaying = false;
    private Runnable onAnimationEnd;

    private final RectF dstRectF = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Matrix matrix = new Matrix();
    private BitmapShader spriteShader;
    
    private float drawScale = 1.0f;
    private float drawOffsetX = 0f;
    private float drawOffsetY = 0f;

    public void setDrawSettings(float scale, float offsetX, float offsetY) {
        this.drawScale = scale;
        this.drawOffsetX = offsetX;
        this.drawOffsetY = offsetY;
    }

    public SpriteSheetView(Context context) {
        super(context);
    }

    public SpriteSheetView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void init(int drawableResId, int cols, int rows, int frames, long durationMs) {
        if (spriteSheet != null && !spriteSheet.isRecycled()) {
            spriteSheet.recycle();
        }
        spriteSheet = BitmapFactory.decodeResource(getResources(), drawableResId);
        spriteShader = new BitmapShader(spriteSheet, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        
        colCount = cols;
        rowCount = rows;
        totalFrames = frames;
        frameWidth = spriteSheet.getWidth() / cols;
        frameHeight = spriteSheet.getHeight() / rows;
        if (frames > 0) {
            frameDuration = durationMs / frames;
        }
    }

    public void play(Runnable onEnd) {
        this.onAnimationEnd = onEnd;
        currentFrame = 0;
        isPlaying = true;
        setVisibility(VISIBLE);
        postInvalidate();
        removeCallbacks(frameRunnable);
        postDelayed(frameRunnable, frameDuration);
    }

    private final Runnable frameRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isPlaying) return;
            
            if (currentFrame >= totalFrames - 1) {
                isPlaying = false;
                setVisibility(GONE);
                if (onAnimationEnd != null) onAnimationEnd.run();
            } else {
                currentFrame++;
                invalidate();
                postDelayed(this, frameDuration);
            }
        }
    };

    @Override
    protected void onDraw(Canvas canvas) {
        if (!isPlaying || spriteSheet == null || spriteSheet.isRecycled() || spriteShader == null) return;
        
        int row = currentFrame / colCount;
        int col = currentFrame % colCount;
        
        float viewWidth = getWidth();
        float viewHeight = getHeight();
        
        float frameAspect = (float) frameWidth / frameHeight;
        float viewAspect = viewWidth / viewHeight;
        
        float finalWidth, finalHeight;
        if (frameAspect > viewAspect) {
            finalWidth = viewWidth;
            finalHeight = viewWidth / frameAspect;
        } else {
            finalHeight = viewHeight;
            finalWidth = viewHeight * frameAspect;
        }
        
        finalWidth *= drawScale;
        finalHeight *= drawScale;
        
        float left = (viewWidth - finalWidth) / 2f + drawOffsetX;
        float top = (viewHeight - finalHeight) / 2f + drawOffsetY;
        dstRectF.set(left, top, left + finalWidth, top + finalHeight);

        // 1. Cấu hình Matrix cho Sprite
        matrix.reset();
        matrix.postTranslate(-col * frameWidth, -row * frameHeight);
        float scaleX = finalWidth / frameWidth;
        float scaleY = finalHeight / frameHeight;
        matrix.postScale(scaleX, scaleY);
        matrix.postTranslate(left, top);
        spriteShader.setLocalMatrix(matrix);
        
        // Bỏ Fade Viền (No Feathering) theo yêu cầu của user
        paint.setShader(spriteShader);
        
        // 4. Vẽ với hiệu ứng bo góc nhẹ bổ sung
        float cornerRadius = 4 * getResources().getDisplayMetrics().density;
        canvas.drawRoundRect(dstRectF, cornerRadius, cornerRadius, paint);
    }
}

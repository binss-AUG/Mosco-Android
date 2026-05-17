package com.vn.jet.mosco.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
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

    private final Rect srcRect = new Rect();
    private final RectF dstRectF = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    
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
        initPaint();
    }

    public SpriteSheetView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPaint();
    }

    private void initPaint() {
        paint.setFilterBitmap(true);
        paint.setDither(true);
    }

    public void init(int drawableResId, int cols, int rows, int frames, long durationMs) {
        if (spriteSheet != null && !spriteSheet.isRecycled()) {
            spriteSheet.recycle();
        }
        
        // High-Quality: Giữ nguyên độ phân giải gốc của SpriteSheet
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = false;
        options.inSampleSize = 1; // Không nén ảnh để giữ chất lượng cực cao
        options.inScaled = false; // Tắt tự động scale theo mật độ điểm ảnh
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        
        spriteSheet = BitmapFactory.decodeResource(getResources(), drawableResId, options);
        
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
        
        // Ép kiểu phần cứng để đạt 60 FPS
        setLayerType(LAYER_TYPE_HARDWARE, null);
        
        removeCallbacks(frameRunnable);
        postDelayed(frameRunnable, frameDuration);
        invalidate();
    }

    private final Runnable frameRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isPlaying) return;
            
            if (currentFrame >= totalFrames - 1) {
                isPlaying = false;
                setVisibility(GONE);
                setLayerType(LAYER_TYPE_NONE, null);
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
        if (!isPlaying || spriteSheet == null || spriteSheet.isRecycled()) return;
        
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

        // 1. Xác định vùng frame trong SpriteSheet (Source)
        int srcLeft = col * frameWidth;
        int srcTop = row * frameHeight;
        srcRect.set(srcLeft, srcTop, srcLeft + frameWidth, srcTop + frameHeight);
        
        // 2. Vẽ trực tiếp bằng drawBitmap (GPU optimized for Sprite Sheets)
        canvas.drawBitmap(spriteSheet, srcRect, dstRectF, paint);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (spriteSheet != null && !spriteSheet.isRecycled()) {
            spriteSheet.recycle();
            spriteSheet = null;
        }
    }
}

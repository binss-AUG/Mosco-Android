package com.vn.jet.mosco.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

/**
 * SpriteSheetView for Mosco
 * Used for rendering complex spritesheet animations like upgrade failure explosions.
 */
public class SpriteSheetView extends View {
    private Bitmap spriteSheet;
    private int frameWidth;
    private int frameHeight;
    private int colCount = 8;
    private int rowCount = 3;
    private int totalFrames = 18;
    private int currentFrame = 0;
    private long frameDuration = 55; // default
    private boolean isPlaying = false;
    private Runnable onAnimationEnd;

    private Rect srcRect = new Rect();
    private Rect dstRect = new Rect();
    
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
        spriteSheet = BitmapFactory.decodeResource(getResources(), drawableResId);
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
        postDelayed(frameRunnable, frameDuration);
    }

    private Runnable frameRunnable = new Runnable() {
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
        if (!isPlaying || spriteSheet == null) return;
        
        int row = currentFrame / colCount;
        int col = currentFrame % colCount;
        
        srcRect.left = col * frameWidth;
        srcRect.top = row * frameHeight;
        srcRect.right = srcRect.left + frameWidth;
        srcRect.bottom = srcRect.top + frameHeight;
        
        int viewWidth = getWidth();
        int viewHeight = getHeight();
        
        // Calculate the aspect ratio of the frame to fit within the view
        float frameAspect = (float) frameWidth / frameHeight;
        float viewAspect = (float) viewWidth / viewHeight;
        
        float finalWidth, finalHeight;
        
        if (frameAspect > viewAspect) {
            finalWidth = viewWidth;
            finalHeight = viewWidth / frameAspect;
        } else {
            finalHeight = viewHeight;
            finalWidth = viewHeight * frameAspect;
        }
        
        // Apply custom scale
        finalWidth *= drawScale;
        finalHeight *= drawScale;
        
        // Center the frame + apply custom offsets
        float left = (viewWidth - finalWidth) / 2f + drawOffsetX;
        float top = (viewHeight - finalHeight) / 2f + drawOffsetY;
        
        dstRect.left = (int) left;
        dstRect.top = (int) top;
        dstRect.right = (int) (left + finalWidth);
        dstRect.bottom = (int) (top + finalHeight);
        
        canvas.drawBitmap(spriteSheet, srcRect, dstRect, null);
    }
}

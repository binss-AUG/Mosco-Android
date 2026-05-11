package com.vn.jet.mosco.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.Log;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;

import java.security.MessageDigest;

/**
 * AvatarCropTransformation - Áp dụng crop thủ công từ metadata (Survive Reinstall).
 * Định dạng params: "xPercent,yPercent,sizePercent" (ví dụ: "0.1,0.2,0.5")
 */
public class AvatarCropTransformation extends BitmapTransformation {

    private static final String ID = "com.vn.jet.mosco.utils.AvatarCropTransformation";
    private final String cropParams;

    public AvatarCropTransformation(String cropParams) {
        this.cropParams = cropParams;
    }

    @Override
    protected Bitmap transform(@NonNull BitmapPool pool, @NonNull Bitmap toTransform, int outWidth, int outHeight) {
        if (cropParams == null || cropParams.isEmpty() || cropParams.equals("auto")) {
            return new SmartFaceCropTransformation().transform(pool, toTransform, outWidth, outHeight);
        }

        try {
            String[] parts = cropParams.split(",");
            if (parts.length != 3) return toTransform;

            float xP = Float.parseFloat(parts[0]);
            float yP = Float.parseFloat(parts[1]);
            float sP = Float.parseFloat(parts[2]);

            int sourceW = toTransform.getWidth();
            int sourceH = toTransform.getHeight();

            int size = (int) (sourceW * sP);
            int targetX = (int) (sourceW * xP);
            int targetY = (int) (sourceH * yP);

            // Clamp
            if (targetX < 0) targetX = 0;
            if (targetY < 0) targetY = 0;
            if (targetX + size > sourceW) size = sourceW - targetX;
            if (targetY + size > sourceH) size = sourceH - targetY;

            Bitmap squared = Bitmap.createBitmap(toTransform, targetX, targetY, size, size);
            Bitmap result = pool.get(size, size, Bitmap.Config.ARGB_8888);
            result.setHasAlpha(true);

            Canvas canvas = new Canvas(result);
            canvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

            Paint paint = new Paint();
            paint.setAntiAlias(true);
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            canvas.drawBitmap(squared, 0, 0, paint);

            if (squared != toTransform) {
                pool.put(squared);
            }

            return result;

        } catch (Exception e) {
            Log.e("AvatarCrop", "Failed to apply manual crop params: " + cropParams, e);
            return new SmartFaceCropTransformation().transform(pool, toTransform, outWidth, outHeight);
        }
    }

    @Override
    public void updateDiskCacheKey(@NonNull MessageDigest messageDigest) {
        messageDigest.update((ID + cropParams).getBytes(CHARSET));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AvatarCropTransformation && ((AvatarCropTransformation) o).cropParams.equals(cropParams);
    }

    @Override
    public int hashCode() {
        return ID.hashCode() + cropParams.hashCode();
    }
}

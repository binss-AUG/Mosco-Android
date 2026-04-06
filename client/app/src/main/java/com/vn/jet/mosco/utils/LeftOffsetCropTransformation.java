package com.vn.jet.mosco.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import androidx.annotation.NonNull;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;
import java.security.MessageDigest;

/**
 * Custom Glide Transformation:
 * 1. Crops image into a Circle.
 * 2. Offsets the center to the LEFT by ~15% to avoid vertical bars on the right of the Objet card.
 */
public class LeftOffsetCropTransformation extends BitmapTransformation {

    private static final String ID = "com.vn.jet.mosco.utils.LeftOffsetCropTransformation";
    private static final byte[] ID_BYTES = ID.getBytes(CHARSET);

    @Override
    protected Bitmap transform(@NonNull BitmapPool pool, @NonNull Bitmap toTransform, int outWidth, int outHeight) {
        int size = Math.min(toTransform.getWidth(), toTransform.getHeight());
        
        // Calculate crop origin
        // Default Center Crop would be: (width - size) / 2
        // We move it 30% further to the left (meaning 30% more space taken from the right)
        int x = (toTransform.getWidth() - size) / 2;
        int y = (toTransform.getHeight() - size) / 2;

        // Shift 'x' left if possible (to see more of the left-side content)
        // Adjust this factor (0.25f) to shift more or less.
        x = (int) (x - (toTransform.getWidth() * 0.15f));
        if (x < 0) x = 0;

        Bitmap squared = Bitmap.createBitmap(toTransform, x, y, size, size);
        Bitmap result = pool.get(size, size, Bitmap.Config.ARGB_8888);
        result.setHasAlpha(true);

        Canvas canvas = new Canvas(result);
        
        // Clear background to be fully transparent since we're reusing from pool
        canvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(squared, 0, 0, paint);

        // Crucial: Recycle the intermediate bitmap to prevent OutOfMemoryError in RecyclerViews
        if (squared != toTransform) {
            pool.put(squared);
        }

        return result;
    }

    @Override
    public void updateDiskCacheKey(@NonNull MessageDigest messageDigest) {
        messageDigest.update(ID_BYTES);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof LeftOffsetCropTransformation;
    }

    @Override
    public int hashCode() {
        return ID.hashCode();
    }
}

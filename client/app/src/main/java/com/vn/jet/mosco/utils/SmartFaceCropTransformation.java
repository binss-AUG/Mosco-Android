package com.vn.jet.mosco.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.util.Log;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.security.MessageDigest;
import java.util.List;

/**
 * Custom Glide Transformation:
 * 1. Uses Google ML Kit Face Detection to find the face in the Objet.
 * 2. If a face is found, crops a circle centered on the face.
 * 3. If NO face is found, falls back to the LeftOffset Crop logic.
 */
public class SmartFaceCropTransformation extends BitmapTransformation {

    private static final String ID = "com.vn.jet.mosco.utils.SmartFaceCropTransformation";
    private static final byte[] ID_BYTES = ID.getBytes(CHARSET);
    private static final String TAG = "SmartFaceCrop";

    @Override
    protected Bitmap transform(@NonNull BitmapPool pool, @NonNull Bitmap toTransform, int outWidth, int outHeight) {
        // --- 🛡️ BẢO VỆ CHÓT: LOẠI BỎ HOÀN TOÀN VIỀN MÀU BÊN PHẢI ---
        // Thẻ Objet có một vạch màu xếp hạng khá to ở lề phải (chiếm ~18% ảnh).
        // Ta tạo một "không gian hữu ích" (usableWidth) lờ đi 18% bên phải đó.
        int usableWidth = (int) (toTransform.getWidth() * 0.82f);
        int size = Math.min(usableWidth, toTransform.getHeight());

        // Default: Center trong vùng usableWidth (đã lệt trái tự nhiên)
        int targetX = (usableWidth - size) / 2;
        int targetY = (toTransform.getHeight() - size) / 2;

        try {
            // Set up ML Kit Face Detector (Fast Mode to prevent UI hangs)
            FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .build();
            FaceDetector detector = FaceDetection.getClient(options);

            InputImage image = InputImage.fromBitmap(toTransform, 0);

            // Block Glide's background thread until ML tasks finish
            List<Face> faces = Tasks.await(detector.process(image));
            
            if (faces != null && !faces.isEmpty()) {
                // Find largest face or first face
                Face mainFace = faces.get(0);
                Rect bounds = mainFace.getBoundingBox();

                // Center of the face
                int faceCenterX = bounds.centerX();
                int faceCenterY = bounds.centerY();

                // We want the circle to be centered around (faceCenterX, faceCenterY)
                targetX = faceCenterX - (size / 2);
                targetY = faceCenterY - (size / 2);

                // Prevent out-of-bounds mapping
                if (targetX < 0) targetX = 0;
                if (targetY < 0) targetY = 0;

                // Ép khung Crop không được vượt quá usableWidth
                if (targetX + size > usableWidth) {
                    targetX = usableWidth - size;
                }
                if (targetY + size > toTransform.getHeight()) {
                    targetY = toTransform.getHeight() - size;
                }

                Log.d(TAG, "Face detected! Custom crop centered at " + faceCenterX + "," + faceCenterY);
            } else {
                Log.d(TAG, "No face detected. Falling back to default offset.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Face detection failed: " + e.getMessage());
            // It will gracefully fall back to default offset logic
        }

        Bitmap squared = Bitmap.createBitmap(toTransform, targetX, targetY, size, size);
        Bitmap result = pool.get(size, size, Bitmap.Config.ARGB_8888);
        result.setHasAlpha(true);

        Canvas canvas = new Canvas(result);
        
        // Clear background to be fully transparent
        canvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(squared, 0, 0, paint);

        // Crucial: Recycle the intermediate bitmap to prevent OutOfMemoryError
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
        return o instanceof SmartFaceCropTransformation;
    }

    @Override
    public int hashCode() {
        return ID.hashCode();
    }
}

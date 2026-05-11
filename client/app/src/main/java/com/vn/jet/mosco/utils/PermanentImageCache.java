package com.vn.jet.mosco.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;

/**
 * [QUIET LUXURY] Permanent Cache for Artist/Objet processed images.
 * Saves cropped bitmaps to Internal Storage to avoid repeated ML Kit processing.
 */
public class PermanentImageCache {
    private static final String TAG = "PermanentImageCache";
    private static final String DIR_NAME = "mosco_artist_crops";

    public static Bitmap get(Context context, String key) {
        try {
            File dir = new File(context.getFilesDir(), DIR_NAME);
            if (!dir.exists()) return null;

            String fileName = md5(key) + ".png";
            File file = new File(dir, fileName);
            if (file.exists()) {
                Log.d(TAG, "Cache HIT: " + key);
                return BitmapFactory.decodeFile(file.getAbsolutePath());
            }
        } catch (Exception e) {
            Log.e(TAG, "Cache GET failed", e);
        }
        return null;
    }

    public static void put(Context context, String key, Bitmap bitmap) {
        new Thread(() -> {
            try {
                File dir = new File(context.getFilesDir(), DIR_NAME);
                if (!dir.exists()) dir.mkdirs();

                String fileName = md5(key) + ".png";
                File file = new File(dir, fileName);
                
                try (FileOutputStream out = new FileOutputStream(file)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                    Log.d(TAG, "Cache SAVED: " + key);
                }
            } catch (Exception e) {
                Log.e(TAG, "Cache PUT failed", e);
            }
        }).start();
    }

    private static String md5(String s) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(s.getBytes());
            byte[] messageDigest = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : messageDigest) {
                String h = Integer.toHexString(0xFF & b);
                while (h.length() < 2) h = "0" + h;
                hexString.append(h);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(s.hashCode());
        }
    }
}

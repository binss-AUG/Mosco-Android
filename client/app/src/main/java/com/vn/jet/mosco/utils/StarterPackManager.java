package com.vn.jet.mosco.utils;

import android.content.Context;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Manager xử lý việc tải và giải nén Starter Pack (SQLite file).
 */
public class StarterPackManager {
    private static final String TAG = "StarterPackManager";
    private static final String STARTER_PACK_URL = "https://mosco-assets.pages.dev/starter_pack_v1.zip";
    private static final String DB_NAME = "mosco_db";

    public interface ProgressListener {
        void onProgress(int percent);
        void onComplete();
        void onError(String error);
    }

    public static boolean isDbInitialized(Context context) {
        File dbFile = context.getDatabasePath(DB_NAME);
        return dbFile.exists();
    }

    public static void downloadAndInitDb(Context context, ProgressListener listener) {
        new Thread(() -> {
            try {
                URL url = new URL(STARTER_PACK_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    listener.onError("Server returned HTTP " + connection.getResponseCode());
                    return;
                }

                int fileLength = connection.getContentLength();
                InputStream input = new BufferedInputStream(url.openStream());
                
                // Giải nén trực tiếp từ stream
                ZipInputStream zis = new ZipInputStream(input);
                ZipEntry ze;
                byte[] buffer = new byte[8192];

                while ((ze = zis.getNextEntry()) != null) {
                    String fileName = ze.getName();
                    if (fileName.contains(DB_NAME)) {
                        File dbFile = context.getDatabasePath(DB_NAME);
                        // Đảm bảo thư mục database tồn tại
                        if (dbFile.getParentFile() != null && !dbFile.getParentFile().exists()) {
                            dbFile.getParentFile().mkdirs();
                        }

                        FileOutputStream fos = new FileOutputStream(dbFile);
                        long total = 0;
                        int count;
                        while ((count = zis.read(buffer)) != -1) {
                            total += count;
                            if (fileLength > 0) {
                                listener.onProgress((int) (total * 100 / fileLength));
                            }
                            fos.write(buffer, 0, count);
                        }
                        fos.close();
                    }
                    zis.closeEntry();
                }
                zis.close();
                listener.onComplete();

            } catch (Exception e) {
                Log.e(TAG, "Starter Pack error", e);
                listener.onError(e.getMessage());
            }
        }).start();
    }
}

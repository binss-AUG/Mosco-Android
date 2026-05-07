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
    private static final String STARTER_PACK_URL = "https://github.com/user-attachments/files/27490330/starter_pack_v1.zip";
    private static final String DB_NAME = "mosco_db";

    public interface ProgressListener {
        void onProgress(int percent);
        void onComplete();
        void onError(String error);
    }

    public static boolean isDbInitialized(Context context) {
        File dbFile = context.getDatabasePath(DB_NAME);
        // Kiểm tra file tồn tại và dung lượng phải > 100KB (tránh trường hợp file rỗng do Room tự tạo)
        return dbFile.exists() && dbFile.length() > 102400;
    }

    public static void downloadAndInitDb(Context context, ProgressListener listener) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                String currentUrl = STARTER_PACK_URL;
                int redirectCount = 0;
                while (redirectCount < 5) {
                    URL url = new URL(currentUrl);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(20000);
                    connection.setInstanceFollowRedirects(true);
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)");

                    int status = connection.getResponseCode();
                    Log.d(TAG, "URL: " + currentUrl + " -> Response: " + status);

                    if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
                        currentUrl = connection.getHeaderField("Location");
                        redirectCount++;
                        connection.disconnect();
                        continue;
                    }
                    break;
                }

                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    Log.e(TAG, "Server returned " + connection.getResponseCode() + ", skipping starter pack download.");
                    listener.onComplete();
                    return;
                }

                int fileLength = connection.getContentLength();
                Log.d(TAG, "Bắt đầu tải Starter Pack, kích thước: " + fileLength + " bytes");
                
                InputStream input = new BufferedInputStream(connection.getInputStream());
                ZipInputStream zis = new ZipInputStream(input);
                ZipEntry ze;
                byte[] buffer = new byte[8192];

                boolean foundDb = false;
                while ((ze = zis.getNextEntry()) != null) {
                    String fileName = ze.getName();
                    Log.d(TAG, "Đang giải nén: " + fileName);
                    if (fileName.contains(DB_NAME)) {
                        foundDb = true;
                        File dbFile = context.getDatabasePath(DB_NAME);
                        if (dbFile.getParentFile() != null && !dbFile.getParentFile().exists()) {
                            dbFile.getParentFile().mkdirs();
                        }

                        FileOutputStream fos = new FileOutputStream(dbFile);
                        long total = 0;
                        int count;
                        while ((count = zis.read(buffer)) != -1) {
                            total += count;
                            if (fileLength > 0) {
                                int percent = (int) (total * 100 / fileLength);
                                listener.onProgress(percent);
                            }
                            fos.write(buffer, 0, count);
                        }
                        fos.close();
                        Log.d(TAG, "Đã lưu database thành công: " + dbFile.getAbsolutePath() + " (" + total + " bytes)");
                    }
                    zis.closeEntry();
                }
                zis.close();
                
                if (!foundDb) {
                    Log.w(TAG, "Không tìm thấy file " + DB_NAME + " trong bản nén!");
                }
                
                listener.onComplete();

            } catch (Exception e) {
                Log.e(TAG, "Starter Pack download failed cực kỳ nghiêm trọng", e);
                listener.onComplete();
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }
}

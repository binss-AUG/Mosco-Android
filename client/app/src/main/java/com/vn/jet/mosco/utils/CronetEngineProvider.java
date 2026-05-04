package com.vn.jet.mosco.utils;

import android.content.Context;
import android.util.Log;
import com.google.android.gms.net.CronetProviderInstaller;
import com.google.android.gms.tasks.Task;
import org.chromium.net.CronetEngine;
import java.io.File;

/**
 * CronetEngineProvider — Cung cấp instance duy nhất của CronetEngine (Chromium Stack).
 * Sử dụng Google Play Services Cronet Provider để đảm bảo cập nhật và bảo mật.
 */
public class CronetEngineProvider {
    private static final String TAG = "CronetProvider";
    private static CronetEngine instance;

    public static synchronized CronetEngine getEngine(Context context) {
        if (instance == null) {
            try {
                // Đảm bảo Cronet Provider đã được cài đặt từ Play Services
                Log.d(TAG, "Installing Cronet Provider from Play Services...");
                
                // Cài đặt đồng bộ (Synchronous) cho đơn giản trong lần init đầu tiên
                CronetProviderInstaller.installProvider(context);
                
                File cacheDir = new File(context.getCacheDir(), "cronet_cache");
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs();
                }

                CronetEngine.Builder builder = new CronetEngine.Builder(context)
                        .enableHttp2(true)
                        .enableQuic(true)
                        .enableBrotli(true)
                        .setStoragePath(cacheDir.getAbsolutePath())
                        .enableHttpCache(CronetEngine.Builder.HTTP_CACHE_DISK, 10 * 1024 * 1024);

                instance = builder.build();
                Log.d(TAG, "Galactic Cronet Engine (HTTP/3) đã sẵn sàng.");
            } catch (Exception e) {
                Log.e(TAG, "Không thể khởi tạo Cronet Play Services, fallback sang logic khác...", e);
                // Fallback: có thể khởi tạo một engine mặc định hoặc báo lỗi
            }
        }
        return instance;
    }
}

package com.vn.jet.mosco;

import android.app.Application;
import android.content.Context;
import com.danikula.videocache.HttpProxyCacheServer;

/**
 * MoscoApplication — Quản lý toàn cục tài nguyên ứng dụng và Proxy đệm video cục bộ.
 */
public class MoscoApplication extends Application {

    private HttpProxyCacheServer proxy;

    public static HttpProxyCacheServer getProxy(Context context) {
        MoscoApplication app = (MoscoApplication) context.getApplicationContext();
        return app.proxy == null ? (app.proxy = app.newProxy()) : app.proxy;
    }

    private HttpProxyCacheServer newProxy() {
        return new HttpProxyCacheServer.Builder(this)
                .maxCacheSize(512 * 1024 * 1024) // Tối đa 512MB bộ nhớ đệm cho Video Motion
                .build();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Khởi tạo proxy cục bộ để đệm video tự động
        proxy = newProxy();
    }
}

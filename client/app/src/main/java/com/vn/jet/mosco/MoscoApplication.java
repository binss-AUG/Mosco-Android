package com.vn.jet.mosco;

import android.app.Application;
import android.content.Context;

import androidx.media3.database.DatabaseProvider;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;

import java.io.File;

/**
 * MoscoApplication — Quản lý toàn cục tài nguyên ứng dụng và Proxy đệm video cục bộ.
 */
public class MoscoApplication extends Application {

    private static SimpleCache simpleCache;
    private static DataSource.Factory cacheDataSourceFactory;

    public static DataSource.Factory getCacheDataSourceFactory(Context context) {
        if (cacheDataSourceFactory == null) {
            MoscoApplication app = (MoscoApplication) context.getApplicationContext();
            app.initCache();
        }
        return cacheDataSourceFactory;
    }

    private synchronized void initCache() {
        if (simpleCache == null) {
            File cacheDir = new File(getCacheDir(), "media_cache");
            // Tối đa 150MB bộ nhớ đệm cho Video Motion (khoảng 3-4 video) để tiết kiệm dung lượng
            LeastRecentlyUsedCacheEvictor evictor = new LeastRecentlyUsedCacheEvictor(150 * 1024 * 1024);
            DatabaseProvider databaseProvider = new StandaloneDatabaseProvider(this);
            simpleCache = new SimpleCache(cacheDir, evictor, databaseProvider);

            DataSource.Factory upstreamFactory = new DefaultHttpDataSource.Factory()
                    .setAllowCrossProtocolRedirects(true);
            
            cacheDataSourceFactory = new CacheDataSource.Factory()
                    .setCache(simpleCache)
                    .setUpstreamDataSourceFactory(upstreamFactory)
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Khởi tạo SimpleCache cho ExoPlayer
        initCache();
    }
}

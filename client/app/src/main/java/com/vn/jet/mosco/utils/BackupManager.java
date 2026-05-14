package com.vn.jet.mosco.utils;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.vn.jet.mosco.database.AppDatabase;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * BackupManager - Handles local database backup and restore in Internal Storage.
 */
public class BackupManager {
    private static final String TAG = "BackupManager";
    private static final String DB_NAME = "mosco_db";
    private static final String BACKUP_DIR = "backups";

    /**
     * Performs a backup to the App's Internal Storage (/files/backups/).
     * Returns the absolute path of the backup file if successful.
     */
    public static String performInternalBackup(Context context, long userId) {
        AppDatabase db = AppDatabase.getInstance(context);
        
        // 1. Force Checkpoint
        try {
            android.database.Cursor c = db.query("PRAGMA wal_checkpoint(TRUNCATE)", null);
            if (c != null) { c.moveToFirst(); c.close(); }
        } catch (Exception e) {
            Log.e(TAG, "Checkpoint failed", e);
        }

        // 2. Prepare Backup Directory
        File backupFolder = new File(context.getFilesDir(), BACKUP_DIR);
        if (!backupFolder.exists()) {
            backupFolder.mkdirs();
        }

        // 3. Create Backup File Name with Timestamp and UID
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String backupFileName = "mosco_backup_UID" + userId + "_" + timeStamp + ".db";
        File backupFile = new File(backupFolder, backupFileName);

        File dbFile = context.getDatabasePath(DB_NAME);

        // 4. Copy
        try (FileChannel source = new FileInputStream(dbFile).getChannel();
             FileChannel destination = new FileOutputStream(backupFile).getChannel()) {
            destination.transferFrom(source, 0, source.size());
            
            // 5. Retention Policy: Keep only latest 2 backups
            cleanOldBackups(backupFolder);
            
            Log.d(TAG, "Internal Backup created at: " + backupFile.getAbsolutePath());
            return backupFile.getAbsolutePath();
        } catch (IOException e) {
            Log.e(TAG, "Internal Backup failed", e);
            return null;
        }
    }

    /**
     * Deletes old backups, keeping only the 2 most recent files.
     */
    private static void cleanOldBackups(File backupFolder) {
        File[] files = backupFolder.listFiles((dir, name) -> name.endsWith(".db"));
        if (files == null || files.length <= 2) return;

        // Sort by last modified (descending)
        java.util.Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));

        // Delete from index 2 onwards
        for (int i = 2; i < files.length; i++) {
            if (files[i].delete()) {
                Log.d(TAG, "Deleted old backup: " + files[i].getName());
            }
        }
    }

    /**
     * Exports to a given Uri (kept for flexibility if needed).
     */
    public static boolean exportDatabase(Context context, Uri targetUri) {
        AppDatabase db = AppDatabase.getInstance(context);
        try {
            android.database.Cursor c = db.query("PRAGMA wal_checkpoint(TRUNCATE)", null);
            if (c != null) { c.moveToFirst(); c.close(); }
        } catch (Exception e) {
            Log.e(TAG, "Checkpoint failed", e);
        }
        File dbFile = context.getDatabasePath(DB_NAME);
        try (ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(targetUri, "w");
             FileChannel source = new FileInputStream(dbFile).getChannel();
             FileChannel destination = new FileOutputStream(pfd.getFileDescriptor()).getChannel()) {
            destination.transferFrom(source, 0, source.size());
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Restores from a Uri.
     */
    public static boolean restoreDatabase(Context context, Uri sourceUri) {
        AppDatabase.getInstance(context).close();
        File dbFile = context.getDatabasePath(DB_NAME);
        
        // Clear WAL and SHM to prevent corruption
        File walFile = new File(dbFile.getPath() + "-wal");
        File shmFile = new File(dbFile.getPath() + "-shm");
        if (walFile.exists()) walFile.delete();
        if (shmFile.exists()) shmFile.delete();

        try (ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(sourceUri, "r");
             FileChannel source = new FileInputStream(pfd.getFileDescriptor()).getChannel();
             FileChannel destination = new FileOutputStream(dbFile).getChannel()) {
            destination.transferFrom(source, 0, source.size());
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    /**
     * Uploads the latest backup of a user to the Cloud.
     */
    public static void syncToCloud(Context context, long userId, SyncCallback callback) {
        File backupFolder = new File(context.getFilesDir(), BACKUP_DIR);
        File[] files = backupFolder.listFiles((dir, name) -> name.contains("UID" + userId) && name.endsWith(".db"));
        
        if (files == null || files.length == 0) {
            callback.onFailure("No local backup found for this account.");
            return;
        }

        // Find latest
        java.util.Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        File latestBackup = files[0];

        // Prepare Multipart
        RequestBody requestFile = RequestBody.create(MediaType.parse("application/octet-stream"), latestBackup);
        MultipartBody.Part body = MultipartBody.Part.createFormData("file", latestBackup.getName(), requestFile);

        // API Call
        com.vn.jet.mosco.network.GameApiService apiService = com.vn.jet.mosco.network.ApiClient.getClient(context).create(com.vn.jet.mosco.network.GameApiService.class);
        apiService.uploadBackup(body).enqueue(new Callback<com.vn.jet.mosco.model.ApiResponse<String>>() {
            @Override
            public void onResponse(Call<com.vn.jet.mosco.model.ApiResponse<String>> call, Response<com.vn.jet.mosco.model.ApiResponse<String>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body().getMessage());
                } else {
                    callback.onFailure("Server Error: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<com.vn.jet.mosco.model.ApiResponse<String>> call, Throwable t) {
                callback.onFailure("Network Error: " + t.getMessage());
            }
        });
    }

    public interface SyncCallback {
        void onSuccess(String message);
        void onFailure(String error);
    }

    /**
     * Fetches the list of backups stored on the cloud for the current user.
     */
    public static void fetchCloudBackups(Context context, Callback<com.vn.jet.mosco.model.ApiResponse<java.util.List<String>>> callback) {
        com.vn.jet.mosco.network.GameApiService apiService = com.vn.jet.mosco.network.ApiClient.getClient(context).create(com.vn.jet.mosco.network.GameApiService.class);
        apiService.listCloudBackups().enqueue(callback);
    }

    /**
     * Downloads a backup from the cloud and restores it.
     */
    public static void downloadAndRestoreCloudBackup(Context context, String filename, SyncCallback callback) {
        com.vn.jet.mosco.network.GameApiService apiService = com.vn.jet.mosco.network.ApiClient.getClient(context).create(com.vn.jet.mosco.network.GameApiService.class);
        apiService.downloadCloudBackup(filename).enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    new Thread(() -> {
                        try {
                            // 1. Close current DB
                            AppDatabase.getInstance(context).close();
                            
                            // 2. Clear WAL and SHM to prevent corruption
                            File dbFile = context.getDatabasePath(DB_NAME);
                            File walFile = new File(dbFile.getPath() + "-wal");
                            File shmFile = new File(dbFile.getPath() + "-shm");
                            if (walFile.exists()) walFile.delete();
                            if (shmFile.exists()) shmFile.delete();

                            // 3. Write to DB file
                            try (java.io.InputStream is = response.body().byteStream();
                                 FileOutputStream fos = new FileOutputStream(dbFile)) {
                                byte[] buffer = new byte[8192];
                                int read;
                                while ((read = is.read(buffer)) != -1) {
                                    fos.write(buffer, 0, read);
                                }
                                fos.flush();
                                
                                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                    callback.onSuccess("✅ Cloud Restore Successful!");
                                });
                            }
                        } catch (IOException e) {
                            Log.e(TAG, "Failed to write cloud backup to disk", e);
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                callback.onFailure("Disk Error: " + e.getMessage());
                            });
                        }
                    }).start();
                } else {
                    callback.onFailure("Server Error: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                callback.onFailure("Network Error: " + t.getMessage());
            }
        });
    }
}

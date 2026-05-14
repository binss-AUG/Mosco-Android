package com.vn.jet.mosco.worker;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.vn.jet.mosco.utils.BackupManager;
import com.vn.jet.mosco.utils.SessionManager;

/**
 * BackupWorker - Automated background task for database backup.
 */
public class BackupWorker extends Worker {
    private static final String TAG = "BackupWorker";

    public BackupWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Executing scheduled backup task...");
        
        Context context = getApplicationContext();
        SessionManager sessionManager = new SessionManager(context);
        
        long userId = sessionManager.getUserId();
        if (userId == -1) {
            Log.w(TAG, "No user logged in. Skipping auto-backup.");
            return Result.success(); // Not a failure, just nothing to do
        }

        String resultPath = BackupManager.performInternalBackup(context, userId);
        
        if (resultPath != null) {
            Log.i(TAG, "Auto-backup completed successfully: " + resultPath);
            
            // Sync to cloud automatically if auto-backup is on
            BackupManager.syncToCloud(context, userId, new BackupManager.SyncCallback() {
                @Override
                public void onSuccess(String message) {
                    Log.i(TAG, "Auto-sync to cloud successful.");
                }

                @Override
                public void onFailure(String error) {
                    Log.e(TAG, "Auto-sync to cloud failed: " + error);
                }
            });
            
            return Result.success();
        } else {
            Log.e(TAG, "Auto-backup failed.");
            return Result.retry(); 
        }
    }
}

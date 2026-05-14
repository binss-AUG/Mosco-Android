package com.vn.jet.mosco.utils;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.vn.jet.mosco.worker.BackupWorker;

import java.util.concurrent.TimeUnit;

/**
 * WorkScheduler - Manages background task scheduling for the Mosco app.
 */
public class WorkScheduler {
    private static final String AUTO_BACKUP_WORK_NAME = "mosco_auto_backup";

    /**
     * Schedules the periodic backup task based on user settings.
     */
    public static void scheduleAutoBackup(Context context) {
        SessionManager sessionManager = new SessionManager(context);
        
        if (!sessionManager.isAutoBackupEnabled()) {
            cancelAutoBackup(context);
            return;
        }

        int intervalHours = sessionManager.getBackupInterval();

        // Constraints: Only run when battery is not low
        Constraints constraints = new Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build();

        // Periodic request: Dynamic interval
        PeriodicWorkRequest backupRequest = new PeriodicWorkRequest.Builder(
                BackupWorker.class, 
                intervalHours, TimeUnit.HOURS,
                15, TimeUnit.MINUTES // Flex interval
        )
        .setConstraints(constraints)
        .build();

        // Enqueue as Unique Periodic Work
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                AUTO_BACKUP_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE, // Update if interval changed
                backupRequest
        );
    }

    /**
     * Cancels the periodic backup task.
     */
    public static void cancelAutoBackup(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(AUTO_BACKUP_WORK_NAME);
    }
}

package com.vn.jet.mosco.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/**
 * NetworkMonitor - Premium Real-time Connectivity Guardian.
 * Monitors network state using modern ConnectivityManager.NetworkCallback.
 * Broadcasts status via LiveData for AAA-grade HUD synchronization.
 */
public class NetworkMonitor {
    private static NetworkMonitor instance;
    private final ConnectivityManager connectivityManager;
    private final MutableLiveData<Boolean> isConnected = new MutableLiveData<>();

    private NetworkMonitor(Context context) {
        connectivityManager = (ConnectivityManager) context.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        
        checkInitialStatus();
        registerCallback();
    }

    public static synchronized NetworkMonitor getInstance(Context context) {
        if (instance == null) {
            instance = new NetworkMonitor(context);
        }
        return instance;
    }

    public LiveData<Boolean> getIsConnected() {
        return isConnected;
    }

    private void checkInitialStatus() {
        try {
            Network activeNetwork = connectivityManager.getActiveNetwork();
            NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(activeNetwork);
            boolean connected = (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET));
            isConnected.postValue(connected);
        } catch (Exception e) {
            isConnected.postValue(false);
        }
    }

    private void registerCallback() {
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        connectivityManager.registerNetworkCallback(request, new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                // Connection acquired: Galactic link established.
                isConnected.postValue(true);
            }

            @Override
            public void onLost(@NonNull Network network) {
                // Connection lost: System anomaly detected.
                isConnected.postValue(false);
            }

            @Override
            public void onUnavailable() {
                isConnected.postValue(false);
            }
        });
    }
}

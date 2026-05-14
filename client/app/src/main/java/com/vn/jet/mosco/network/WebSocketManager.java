package com.vn.jet.mosco.network;

import android.util.Log;

import com.google.gson.Gson;
import com.vn.jet.mosco.model.WorldChatMessage;
import com.vn.jet.mosco.utils.AppConfig;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import ua.naiksoftware.stomp.Stomp;
import ua.naiksoftware.stomp.StompClient;

/**
 * Manager class for WebSocket STOMP connections.
 * Handles World Chat real-time messaging.
 */
public class WebSocketManager {
    private static final String TAG = "WebSocketManager";
    private static WebSocketManager instance;
    private StompClient stompClient;
    private Gson gson;

    private WebSocketManager() {
        gson = new Gson();
        stompClient = Stomp.over(Stomp.ConnectionProvider.OKHTTP, AppConfig.WS_URL);
    }

    public static synchronized WebSocketManager getInstance() {
        if (instance == null) {
            instance = new WebSocketManager();
        }
        return instance;
    }

    public void connect() {
        if (stompClient.isConnected()) {
            Log.d(TAG, "Stomp already connected");
            return;
        }

        Log.d(TAG, "Connecting to WebSocket at: " + AppConfig.WS_URL);
        stompClient.connect();

        stompClient.lifecycle()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(lifecycleEvent -> {
                    switch (lifecycleEvent.getType()) {
                        case OPENED:
                            Log.d(TAG, "✅ STOMP Connection Opened!");
                            break;
                        case ERROR:
                            Log.e(TAG, "❌ STOMP Connection Error", lifecycleEvent.getException());
                            // Có thể thêm logic reconnect ở đây nếu cần
                            break;
                        case CLOSED:
                            Log.d(TAG, "ℹ️ STOMP Connection Closed");
                            break;
                        case FAILED_SERVER_HEARTBEAT:
                            Log.e(TAG, "❌ STOMP Failed Server Heartbeat");
                            break;
                    }
                }, throwable -> Log.e(TAG, "Lifecycle error", throwable));
    }

    /**
     * Subscribe to World Chat topic.
     */
    public Disposable subscribeToWorldChat(OnMessageReceived listener) {
        Log.d(TAG, "Subscribing to /topic/world...");
        return stompClient.topic("/topic/world")
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(topicMessage -> {
                    Log.d(TAG, "Received message from topic: " + topicMessage.getPayload());
                    try {
                        WorldChatMessage message = gson.fromJson(topicMessage.getPayload(), WorldChatMessage.class);
                        if (message != null) {
                            listener.onReceived(message);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing message", e);
                    }
                }, throwable -> {
                    Log.e(TAG, "Error on subscribe topic /topic/world", throwable);
                });
    }

    /**
     * Send message to World Chat.
     */
    public void sendWorldMessage(WorldChatMessage message) {
        if (!stompClient.isConnected()) {
            Log.w(TAG, "Cannot send message: Stomp not connected");
            return;
        }
        String json = gson.toJson(message);
        stompClient.send("/app/chat.sendMessage", json).subscribe();
    }

    public void disconnect() {
        if (stompClient != null) stompClient.disconnect();
    }

    public interface OnMessageReceived {
        void onReceived(WorldChatMessage message);
    }
}

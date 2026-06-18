package com.vn.jet.mosco.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.adapter.AiChatAdapter;
import com.vn.jet.mosco.database.AppDatabase;
import com.vn.jet.mosco.model.AiChatMessage;
import com.vn.jet.mosco.model.ApiResponse;
import com.vn.jet.mosco.network.ApiClient;
import com.vn.jet.mosco.network.GameApiService;
import com.vn.jet.mosco.utils.AppExecutors;
import com.vn.jet.mosco.utils.MoscoDialogHelper;
import com.vn.jet.mosco.utils.SessionManager;
import com.vn.jet.mosco.utils.SmartFaceCropTransformation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AiChatBottomSheet extends BottomSheetDialogFragment {

    private RecyclerView rvMessages;
    private EditText etInput;
    private ImageView btnSend;
    private ImageView ivAiAvatar;
    private TextView tvAiName;
    
    private AiChatAdapter adapter;
    private final List<AiChatMessage> messageList = new ArrayList<>();
    
    private GameApiService gameApiService;
    private SessionManager sessionManager;
    private long lastSendTime = 0;
    private static final long COOLDOWN_MS = 3000;
    
    private List<String> ownedBiases = new ArrayList<>();
    private String currentAvatarUrl = null;
    private boolean isGenerating = false;
    private retrofit2.Call<com.vn.jet.mosco.model.ApiResponse<String>> currentApiCall;
    private okhttp3.sse.EventSource currentEventSource;
    private boolean isCancelledByUser = false;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext(), R.style.LiquidGlass_BottomSheetTheme);
        dialog.setOnShowListener(d -> {
            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                ViewGroup.LayoutParams layoutParams = bottomSheet.getLayoutParams();
                if (layoutParams != null) {
                    layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
                    bottomSheet.setLayoutParams(layoutParams);
                }

                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_ai_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        sessionManager = new SessionManager(requireContext());
        gameApiService = ApiClient.getClient(requireContext()).create(GameApiService.class);
        
        rvMessages = view.findViewById(R.id.rv_ai_messages);
        etInput = view.findViewById(R.id.et_ai_message);
        btnSend = view.findViewById(R.id.btn_ai_send);
        ivAiAvatar = view.findViewById(R.id.iv_ai_avatar);
        tvAiName = view.findViewById(R.id.tv_ai_name);
        
        view.findViewById(R.id.btn_close_chat).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.ll_ai_name_container).setOnClickListener(v -> showBiasSelectionDialog());
        view.findViewById(R.id.layout_ai_chat_header).setOnClickListener(v -> showBiasSelectionDialog());
        view.findViewById(R.id.layout_ai_avatar_group).setOnClickListener(v -> showBiasSelectionDialog());
        
        setupRecyclerView();
        loadOwnedBiasesAndInit();
        
        btnSend.setOnClickListener(v -> {
            if (isGenerating) {
                cancelGeneration();
            } else {
                sendMessage();
            }
        });

        etInput.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == android.view.KeyEvent.ACTION_DOWN && keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
                if (event.isShiftPressed()) {
                    return false; // Cho phép xuống dòng
                } else {
                    if (!isGenerating) {
                        sendMessage();
                    }
                    return true; // Ngăn chặn việc xuống dòng mặc định
                }
            }
            return false;
        });
    }

    private void setupRecyclerView() {
        adapter = new AiChatAdapter(messageList);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setStackFromEnd(true);
        rvMessages.setLayoutManager(layoutManager);
        rvMessages.setItemAnimator(null); // Fix RecyclerView SSE bounds animation stretching bug
        rvMessages.setAdapter(adapter);
    }

    private void loadOwnedBiasesAndInit() {
        AppExecutors.getInstance().diskIO().execute(() -> {
            Long userId = sessionManager.getUserId();
            if (com.vn.jet.mosco.utils.DatabaseLoader.cachedUserInventory == null) {
                com.vn.jet.mosco.utils.DatabaseLoader.loadInventoryFromLocal(requireContext(), userId);
            }
            
            java.util.Set<String> uniqueMembers = new java.util.HashSet<>();
            if (com.vn.jet.mosco.utils.DatabaseLoader.cachedUserInventory != null) {
                for (com.vn.jet.mosco.utils.DatabaseLoader.UserInventoryItem item : com.vn.jet.mosco.utils.DatabaseLoader.cachedUserInventory) {
                    if (item.member != null && !item.member.isEmpty() && com.vn.jet.mosco.utils.DatabaseLoader.isArtist(item.member)) {
                        uniqueMembers.add(item.member);
                    }
                }
            }
            
            ownedBiases = new ArrayList<>(uniqueMembers);
            
            // Nếu vẫn trống (user mới tinh ko có thẻ nào), cho full 24 artist luôn
            if (ownedBiases.isEmpty()) {
                ownedBiases.addAll(com.vn.jet.mosco.utils.AppConfig.OFFICIAL_ARTISTS);
            }
            
            java.util.Collections.sort(ownedBiases);
            
            String savedBias = sessionManager.getAiBiasId();
            if (savedBias == null || savedBias.isEmpty() || !ownedBiases.contains(savedBias)) {
                // If saved bias not in list, select first available
                savedBias = ownedBiases.get(0);
                sessionManager.setAiBiasId(savedBias);
            }
            
            final String finalSavedBias = savedBias;
            // Fetch Avatar
            currentAvatarUrl = AppDatabase.getInstance(requireContext())
                    .masterObjetDao().getLatestPremierImageByMember(finalSavedBias);

            new Handler(Looper.getMainLooper()).post(() -> {
                updateBiasUi();
                adapter.setAvatarUrl(currentAvatarUrl);
                loadLocalChatHistory();
            });
        });
    }

    private void updateBiasUi() {
        if (getView() == null) return;
        String biasId = sessionManager.getAiBiasId();
        tvAiName.setText(biasId + " AI");
        
        if (currentAvatarUrl != null && !currentAvatarUrl.isEmpty()) {
            Glide.with(requireContext())
                    .load(currentAvatarUrl)
                    .apply(RequestOptions.bitmapTransform(new SmartFaceCropTransformation(currentAvatarUrl)))
                    .placeholder(R.drawable.ic_star_twinkle)
                    .into(ivAiAvatar);
        } else {
            ivAiAvatar.setImageResource(R.drawable.ic_star_twinkle);
        }
    }

    private void showBiasSelectionDialog() {
        if (ownedBiases == null || ownedBiases.isEmpty()) {
            Toast.makeText(getContext(), "No bias available!", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] biasArray = new String[ownedBiases.size()];
        int selectedIndex = 0;
        String currentBiasId = sessionManager.getAiBiasId();
        
        for (int i = 0; i < ownedBiases.size(); i++) {
            biasArray[i] = ownedBiases.get(i) + " AI";
            if (ownedBiases.get(i).equals(currentBiasId)) {
                selectedIndex = i;
            }
        }

        MoscoDialogHelper.showSingleChoiceDialog(getActivity(), getString(R.string.ai_chat_title_choose_bias), biasArray, selectedIndex, index -> {
            String selectedBiasId = ownedBiases.get(index);
            sessionManager.setAiBiasId(selectedBiasId);
            
            // Cập nhật FAB ngoài MainActivity
            if (getActivity() instanceof com.vn.jet.mosco.MainActivity) {
                ((com.vn.jet.mosco.MainActivity) getActivity()).updateAiFabAvatar();
            }
            
            // Reload avatar and history
            AppExecutors.getInstance().diskIO().execute(() -> {
                currentAvatarUrl = AppDatabase.getInstance(requireContext())
                        .masterObjetDao().getLatestPremierImageByMember(selectedBiasId);
                        
                // Không xóa lịch sử chat nữa, tải theo biasId
                new Handler(Looper.getMainLooper()).post(() -> {
                    updateBiasUi();
                    adapter.setAvatarUrl(currentAvatarUrl);
                    messageList.clear();
                    adapter.notifyDataSetChanged();
                    loadLocalChatHistory();
                });
            });
        });
    }

    private void loadLocalChatHistory() {
        AppExecutors.getInstance().diskIO().execute(() -> {
            String biasId = sessionManager.getAiBiasId();
            List<AiChatMessage> history = AppDatabase.getInstance(requireContext())
                    .aiChatDao().getAllMessagesByBias(biasId);
            
            new Handler(Looper.getMainLooper()).post(() -> {
                messageList.clear();
                if (history == null || history.isEmpty()) {
                    String greetingText = getPersonalizedGreeting(biasId);
                    AiChatMessage greeting = new AiChatMessage(biasId, greetingText, true, System.currentTimeMillis());
                    messageList.add(greeting);
                    saveMessageToDb(greeting);
                } else {
                    messageList.addAll(history);
                }
                adapter.notifyDataSetChanged();
                scrollToBottom();
            });
        });
    }

    private String getPersonalizedGreeting(String biasId) {
        String displayName = sessionManager.getIngameName();
        if (displayName == null || displayName.isEmpty()) displayName = sessionManager.getUsername();
        
        boolean isVi = getResources().getConfiguration().locale.getLanguage().equals(new java.util.Locale("vi").getLanguage());
        String defaultName = isVi ? "bạn" : "you";
        if (displayName == null) displayName = defaultName;

        String resourceName = "ai_greeting_" + biasId.toLowerCase();
        int resId = getResources().getIdentifier(resourceName, "string", requireContext().getPackageName());

        if (resId != 0) {
            return getString(resId, displayName);
        } else {
            return getString(R.string.ai_chat_default_greeting);
        }
    }

    private void saveMessageToDb(AiChatMessage msg) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            AppDatabase.getInstance(requireContext()).aiChatDao().insert(msg);
        });
    }

    private void sendMessage() {
        String content = etInput.getText().toString().trim();
        if (content.isEmpty()) return;
        
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSendTime < COOLDOWN_MS) {
            Toast.makeText(getContext(), getString(R.string.ai_chat_err_cooldown), Toast.LENGTH_SHORT).show();
            return;
        }
        lastSendTime = currentTime;
        
        String biasId = sessionManager.getAiBiasId();
        AiChatMessage userMsg = new AiChatMessage(biasId, content, false, currentTime);
        
        messageList.add(userMsg);
        adapter.notifyItemInserted(messageList.size() - 1);
        scrollToBottom();
        saveMessageToDb(userMsg);
        
        etInput.setText("");
        isCancelledByUser = false;
        setLoading(true);
        
        okhttp3.OkHttpClient client = (okhttp3.OkHttpClient) ApiClient.getClient(requireContext()).callFactory();
        
        org.json.JSONObject jsonBody = new org.json.JSONObject();
        try {
            jsonBody.put("biasId", biasId);
            jsonBody.put("language", getResources().getConfiguration().locale.getLanguage());
            
            org.json.JSONArray messagesArray = new org.json.JSONArray();
            int startIdx = Math.max(0, messageList.size() - 20); // Gửi 20 tin nhắn gần nhất
            for (int i = startIdx; i < messageList.size(); i++) {
                AiChatMessage msg = messageList.get(i);
                if (msg.isThinking) continue;
                org.json.JSONObject msgObj = new org.json.JSONObject();
                msgObj.put("role", msg.isFromAi ? "model" : "user");
                msgObj.put("text", msg.message);
                messagesArray.put(msgObj);
            }
            jsonBody.put("messages", messagesArray);
        } catch (Exception e) {}
        
        okhttp3.RequestBody requestBody = okhttp3.RequestBody.create(
                jsonBody.toString(),
                okhttp3.MediaType.parse("application/json")
        );
        
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(com.vn.jet.mosco.utils.AppConfig.BASE_URL + "api/ai/chat/stream")
                .post(requestBody)
                .addHeader("Accept", "text/event-stream")
                .build();
                
        currentEventSource = okhttp3.sse.EventSources.createFactory(client)
                .newEventSource(request, new okhttp3.sse.EventSourceListener() {
                    
                    private StringBuilder responseBuilder = new StringBuilder();
                    private AiChatMessage aiMsg = null;
                    private boolean isAdded = false;

                    @Override
                    public void onOpen(okhttp3.sse.EventSource eventSource, okhttp3.Response response) {
                        aiMsg = new AiChatMessage(biasId, "", true, System.currentTimeMillis());
                        new Handler(Looper.getMainLooper()).post(() -> {
                            adapter.removeThinkingMessage();
                            messageList.add(aiMsg);
                            isAdded = true;
                            adapter.notifyItemInserted(messageList.size() - 1);
                            scrollToBottom();
                            // Also update text immediately in case onEvent fired before this UI task
                            if (responseBuilder.length() > 0) {
                                aiMsg.message = responseBuilder.toString();
                                adapter.notifyItemChanged(messageList.size() - 1);
                            }
                        });
                    }

                    @Override
                    public void onEvent(okhttp3.sse.EventSource eventSource, String id, String type, String data) {
                        try {
                            org.json.JSONObject obj = new org.json.JSONObject(data);
                            String text = obj.optString("text", "");
                            responseBuilder.append(text);
                        } catch (Exception e) {
                            // Fallback in case server sends raw text instead of JSON
                            responseBuilder.append(data);
                        }
                        if (aiMsg != null) {
                            aiMsg.message = responseBuilder.toString();
                        }
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (isAdded && aiMsg != null) {
                                int index = messageList.indexOf(aiMsg);
                                if (index != -1) {
                                    adapter.notifyItemChanged(index, AiChatAdapter.PAYLOAD_BUBBLE);
                                    scrollToBottom();
                                }
                            }
                        });
                    }

                    @Override
                    public void onClosed(okhttp3.sse.EventSource eventSource) {
                        new Handler(Looper.getMainLooper()).post(() -> {
                            setLoading(false);
                            if (aiMsg != null && aiMsg.message != null && !aiMsg.message.isEmpty()) {
                                saveMessageToDb(aiMsg);
                            }
                        });
                    }

                    @Override
                    public void onFailure(okhttp3.sse.EventSource eventSource, Throwable t, okhttp3.Response response) {
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (isCancelledByUser) {
                                isCancelledByUser = false;
                                return;
                            }
                            setLoading(false);
                            adapter.removeThinkingMessage();
                            if (response != null) {
                                handleErrorResponse(response);
                            } else {
                                Toast.makeText(getContext(), getString(R.string.ai_chat_err_disconnect), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
    }

    private void cancelGeneration() {
        isCancelledByUser = true;
        if (currentEventSource != null) {
            currentEventSource.cancel();
        }
        if (currentApiCall != null) {
            currentApiCall.cancel();
        }
        setLoading(false);
        // Save partial AI message if any
        int lastIdx = messageList.size() - 1;
        if (lastIdx >= 0) {
            AiChatMessage lastMsg = messageList.get(lastIdx);
            if (lastMsg.isFromAi && lastMsg.message != null && !lastMsg.message.isEmpty()) {
                saveMessageToDb(lastMsg);
            }
        }
    }

    private void handleErrorResponse(retrofit2.Response<?> response) {
        handleErrorCode(response.code());
    }

    private void handleErrorResponse(okhttp3.Response response) {
        handleErrorCode(response.code());
    }

    private void handleErrorCode(int code) {
        if (code == 403) {
            MoscoDialogHelper.showConfirmDialog(
                    getActivity(),
                    getString(R.string.ai_chat_err_restricted_title),
                    getString(R.string.ai_chat_err_restricted_msg),
                    getString(R.string.ai_chat_err_understood),
                    null,
                    null
            );
        } else {
            Toast.makeText(getContext(), getString(R.string.ai_chat_err_server), Toast.LENGTH_SHORT).show();
        }
    }

    private void setLoading(boolean isLoading) {
        this.isGenerating = isLoading;
        if (isLoading) {
            btnSend.setImageResource(android.R.drawable.ic_media_pause);
            AiChatMessage thinkingMsg = new AiChatMessage(true);
            adapter.addMessage(thinkingMsg);
            scrollToBottom();
        } else {
            btnSend.setImageResource(R.drawable.ic_send);
            adapter.removeThinkingMessage();
        }
        etInput.setEnabled(!isLoading);
    }

    private void scrollToBottom() {
        if (messageList.size() > 0) {
            if (isGenerating) {
                // Remove scrollBy 5000 as it can cause layout thrashing during rapid SSE
                rvMessages.scrollToPosition(messageList.size() - 1);
            } else {
                rvMessages.smoothScrollToPosition(messageList.size() - 1);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelGeneration();
    }
}

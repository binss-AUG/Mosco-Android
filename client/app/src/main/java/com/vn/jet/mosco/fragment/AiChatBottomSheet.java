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
    private Call<ApiResponse<String>> currentApiCall;

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
    }

    private void setupRecyclerView() {
        adapter = new AiChatAdapter(messageList);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setStackFromEnd(true);
        rvMessages.setLayoutManager(layoutManager);
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
        setLoading(true);
        
        Map<String, String> requestMap = new HashMap<>();
        requestMap.put("message", content);
        requestMap.put("biasId", biasId); // Send only the name, e.g., "HyeRin"
        requestMap.put("language", getResources().getConfiguration().locale.getLanguage()); // Pass app language
        
        currentApiCall = gameApiService.chatWithAi(requestMap);
        currentApiCall.enqueue(new Callback<ApiResponse<String>>() {
            @Override
            public void onResponse(Call<ApiResponse<String>> call, Response<ApiResponse<String>> response) {
                setLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    AiChatMessage aiMsg = new AiChatMessage(biasId, response.body().getData(), true, System.currentTimeMillis());
                    
                    messageList.add(aiMsg);
                    adapter.notifyItemInserted(messageList.size() - 1);
                    scrollToBottom();
                    saveMessageToDb(aiMsg);
                } else {
                    handleErrorResponse(response);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<String>> call, Throwable t) {
                setLoading(false);
                if (!call.isCanceled()) {
                    Toast.makeText(getContext(), getString(R.string.ai_chat_err_disconnect), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void cancelGeneration() {
        if (currentApiCall != null) {
            currentApiCall.cancel();
        }
        setLoading(false);
    }

    private void handleErrorResponse(Response<?> response) {
        if (response.code() == 403) {
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
            rvMessages.smoothScrollToPosition(messageList.size() - 1);
        }
    }
}

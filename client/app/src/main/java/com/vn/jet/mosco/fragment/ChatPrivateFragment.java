package com.vn.jet.mosco.fragment;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.view.MotionEvent;
import android.animation.ValueAnimator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieProperty;
import com.airbnb.lottie.model.KeyPath;
import com.airbnb.lottie.value.LottieValueCallback;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.adapter.WorldChatAdapter;
import com.vn.jet.mosco.database.AppDatabase;
import com.vn.jet.mosco.model.ApiResponse;
import com.vn.jet.mosco.model.CoupleStreakDto;
import com.vn.jet.mosco.model.PrivateChatMessage;
import com.vn.jet.mosco.model.WorldChatMessage;
import com.vn.jet.mosco.network.ApiClient;
import com.vn.jet.mosco.network.GameApiService;
import com.vn.jet.mosco.network.WebSocketManager;
import com.vn.jet.mosco.utils.AppExecutors;
import com.vn.jet.mosco.utils.AvatarUtils;
import com.vn.jet.mosco.utils.SessionManager;
import com.vn.jet.mosco.utils.StreakColorHelper;
import com.vn.jet.mosco.utils.MoscoDialogHelper;
import com.vn.jet.mosco.model.Objet;

import java.util.List;
import java.util.Objects;
import java.util.ArrayList;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * ChatPrivateFragment - Quản lý tin nhắn riêng giữa 2 người dùng.
 * V3.2: Quản lý trạng thái nút Request/Accept/Decline trong Dialog.
 */
public class ChatPrivateFragment extends Fragment {

    private static final String TAG = "ChatPrivateFragment";
    public static final String ARG_PARTNER_ID = "partner_id";
    public static final String ARG_PARTNER_NAME = "partner_name";
    public static final String ARG_PARTNER_AVATAR = "partner_avatar";

    private Long partnerId;
    private String partnerName;
    private String partnerAvatar;

    private RecyclerView rvChat;
    private WorldChatAdapter chatAdapter;
    private EditText etInput;
    private ImageView btnSend, btnBack;
    private ImageView ivHeaderAvatar;
    private LottieAnimationView lottieStreakIcon;
    private TextView tvHeaderName, tvHeaderStatus, tvStreakCount;
    private View btnStreakDetails;
    private View layoutAddFriendBanner;
    private TextView btnAddFriendChat;

    private SessionManager sessionManager;
    private GameApiService gameApiService;
    private io.reactivex.disposables.Disposable chatSubscription;
    private io.reactivex.disposables.Disposable streakSubscription;
    private android.animation.ValueAnimator rgbAnimator;
    private android.app.AlertDialog currentStreakDialog;

    private CoupleStreakDto currentStreakData;
    private int lastCount = -1;
    private boolean lastActive = false;

    public ChatPrivateFragment() {
    }

    public static ChatPrivateFragment newInstance(Long partnerId, String partnerName, String partnerAvatar) {
        ChatPrivateFragment fragment = new ChatPrivateFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_PARTNER_ID, partnerId);
        args.putString(ARG_PARTNER_NAME, partnerName);
        args.putString(ARG_PARTNER_AVATAR, partnerAvatar);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            partnerId = getArguments().getLong(ARG_PARTNER_ID);
            partnerName = getArguments().getString(ARG_PARTNER_NAME, "Unknown");
            partnerAvatar = getArguments().getString(ARG_PARTNER_AVATAR, "1");
        }
        sessionManager = new SessionManager(requireContext());
        gameApiService = ApiClient.getClient(requireContext()).create(GameApiService.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat_private, container, false);
        initViews(view);
        setupListeners();
        loadHistory();
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        syncWithServer();
        subscribeToUpdates();
    }

    private void initViews(View v) {
        rvChat = v.findViewById(R.id.rv_private_chat);
        etInput = v.findViewById(R.id.et_private_chat);
        btnSend = v.findViewById(R.id.btn_private_send);
        btnBack = v.findViewById(R.id.btn_close_private_chat);
        ivHeaderAvatar = v.findViewById(R.id.iv_private_header_avatar);
        tvHeaderName = v.findViewById(R.id.tv_private_header_name);
        tvHeaderStatus = v.findViewById(R.id.tv_private_status_text);
        tvStreakCount = v.findViewById(R.id.tv_streak_count);
        lottieStreakIcon = v.findViewById(R.id.lottie_streak_icon);
        btnStreakDetails = v.findViewById(R.id.btn_streak_details);
        layoutAddFriendBanner = v.findViewById(R.id.layout_add_friend_banner);
        btnAddFriendChat = v.findViewById(R.id.btn_add_friend_chat);

        tvHeaderName.setText(partnerName);
        AvatarUtils.loadAvatar(getContext(), ivHeaderAvatar, partnerId, partnerAvatar);

        chatAdapter = new WorldChatAdapter();
        chatAdapter.setCurrentUserId(String.valueOf(sessionManager.getUserId()));
        rvChat.setLayoutManager(new LinearLayoutManager(getContext()));
        rvChat.setAdapter(chatAdapter);

        if (lottieStreakIcon != null) {
            StreakColorHelper.setupStreakLottie(lottieStreakIcon, 0, false);
        }
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> {
            if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                getParentFragmentManager().popBackStack();
            }
        });
        btnSend.setOnClickListener(v -> sendMessage());
        if (btnStreakDetails != null) {
            btnStreakDetails.setOnClickListener(v -> showCoupleStreakDialog());
        }
        if (btnAddFriendChat != null) {
            btnAddFriendChat.setOnClickListener(v -> sendFriendRequestInChat());
        }
    }

    private void fetchStreakStatus() {
        if (getContext() == null) return;
        long myId = sessionManager.getUserId();
        gameApiService.checkCoupleStreak(myId, partnerId).enqueue(new Callback<ApiResponse<CoupleStreakDto>>() {
            @Override
            public void onResponse(Call<ApiResponse<CoupleStreakDto>> call, Response<ApiResponse<CoupleStreakDto>> response) {
                if (!isAdded() || getContext() == null) return;
                
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    currentStreakData = response.body().getData();
                    
                    // Ràng buộc bạn bè: Nếu không phải bạn bè thì ẩn sạch
                    if ("NOT_FRIENDS".equals(currentStreakData.getStatus())) {
                        lottieStreakIcon.setVisibility(View.GONE);
                        tvStreakCount.setVisibility(View.GONE);
                        btnStreakDetails.setVisibility(View.GONE);
                        if (layoutAddFriendBanner != null) {
                            layoutAddFriendBanner.setVisibility(View.VISIBLE);
                        }
                        return;
                    }

                    // Hiện lại nếu là bạn bè
                    lottieStreakIcon.setVisibility(View.VISIBLE);
                    tvStreakCount.setVisibility(View.VISIBLE);
                    btnStreakDetails.setVisibility(View.VISIBLE);
                    if (layoutAddFriendBanner != null) {
                        layoutAddFriendBanner.setVisibility(View.GONE);
                    }
                    
                    updateStreakUI(currentStreakData.getStreakCount(), "ACTIVE".equals(currentStreakData.getStatus()));
                } else {
                    // Nếu lỗi API hoặc không tìm thấy quan hệ
                    lottieStreakIcon.setVisibility(View.GONE);
                    tvStreakCount.setVisibility(View.GONE);
                    btnStreakDetails.setVisibility(View.GONE);
                    if (layoutAddFriendBanner != null) {
                        layoutAddFriendBanner.setVisibility(View.VISIBLE);
                    }
                }
            }
            @Override
            public void onFailure(Call<ApiResponse<CoupleStreakDto>> call, Throwable t) {
                if (isAdded()) {
                    lottieStreakIcon.setVisibility(View.GONE);
                    tvStreakCount.setVisibility(View.GONE);
                    btnStreakDetails.setVisibility(View.GONE);
                }
            }
        });
    }

    private void updateStreakUI(int count, boolean active) {
        boolean interactedToday = false;
        if (currentStreakData != null && currentStreakData.getLastInteractionDate() != null) {
            String lastDate = currentStreakData.getLastInteractionDate();
            String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date());
            if (lastDate.startsWith(today)) {
                interactedToday = true;
            }
        }

        // Nếu qua ngày mới mà chưa tương tác (interactedToday = false), coi như inactive để xám lại
        boolean visualActive = active && interactedToday;

        if (count == lastCount && visualActive == lastActive) return;
        
        lastCount = count;
        lastActive = visualActive;
        
        if (count == 0) {
            tvStreakCount.setVisibility(View.GONE);
        } else {
            tvStreakCount.setVisibility(View.VISIBLE);
            tvStreakCount.setText(String.valueOf(count));
        }

        if (lottieStreakIcon == null) return;

        StreakColorHelper.setupStreakLottie(lottieStreakIcon, count, visualActive);

        if (visualActive && count >= 1000) {
            startRGBStreakAnimation(lottieStreakIcon);
        } else {
            stopRGBStreakAnimation();
        }
        
        tvStreakCount.setTextColor(visualActive && count > 0 ? android.graphics.Color.WHITE : android.graphics.Color.GRAY);
    }

    private void startRGBStreakAnimation(LottieAnimationView lottie) {
        if (rgbAnimator != null && rgbAnimator.isRunning()) return;
        rgbAnimator = android.animation.ValueAnimator.ofFloat(0f, 360f);
        rgbAnimator.setDuration(3000);
        rgbAnimator.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        rgbAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
        rgbAnimator.addUpdateListener(animation -> {
            float hue = (float) animation.getAnimatedValue();
            StreakColorHelper.applyRGBEffect(lottie, hue);
        });
        rgbAnimator.start();
    }

    private void stopRGBStreakAnimation() {
        if (rgbAnimator != null) {
            rgbAnimator.cancel();
            rgbAnimator = null;
        }
    }

    private void showCoupleStreakDialog() {
        if (getContext() == null || !isAdded() || currentStreakData == null) return;

        // 1. Chuyển đổi trạng thái từ DTO
        MoscoDialogHelper.CoupleStatus status;
        String apiStatus = currentStreakData.getStatus();
        
        if ("PENDING".equals(apiStatus)) {
            if (Objects.equals(currentStreakData.getRequesterId(), sessionManager.getUserId())) {
                status = MoscoDialogHelper.CoupleStatus.WAITING;
            } else {
                status = MoscoDialogHelper.CoupleStatus.RECEIVED_REQUEST;
            }
        } else if ("ACTIVE".equals(apiStatus)) {
            status = MoscoDialogHelper.CoupleStatus.ACTIVE;
        } else {
            status = MoscoDialogHelper.CoupleStatus.INVITE;
        }

        // 2. Truy vấn Metadata thẻ từ Local DB để đồng bộ hiển thị (Local-First)
        AppExecutors.getInstance().diskIO().execute(() -> {
            com.vn.jet.mosco.database.MasterObjetDao dao = com.vn.jet.mosco.database.AppDatabase.getInstance(requireContext()).masterObjetDao();
            
            boolean isRequester = Objects.equals(currentStreakData.getRequesterId(), sessionManager.getUserId());
            String myObjetId = isRequester ? currentStreakData.getRequesterObjetId() : currentStreakData.getPartnerObjetId();
            String partnerObjetId = isRequester ? currentStreakData.getPartnerObjetId() : currentStreakData.getRequesterObjetId();
            
            int myGrade = isRequester ? currentStreakData.getRequesterGrade() : currentStreakData.getPartnerGrade();
            int partnerGrade = isRequester ? currentStreakData.getPartnerGrade() : currentStreakData.getRequesterGrade();

            org.json.JSONObject objA = com.vn.jet.mosco.utils.DatabaseLoader.findById(requireContext(), myObjetId);
            org.json.JSONObject objB = com.vn.jet.mosco.utils.DatabaseLoader.findById(requireContext(), partnerObjetId);

            if (!isAdded()) return;

            requireActivity().runOnUiThread(() -> {
                MoscoDialogHelper.CoupleData dialogData = new MoscoDialogHelper.CoupleData(
                        partnerName,
                        null, 
                        null,
                        currentStreakData.getStreakCount()
                );
                
                dialogData.streakId = currentStreakData.getId();

                // Map dữ liệu thẻ A (Chính chủ)
                if (objA != null) {
                    dialogData.cardAUrl = com.vn.jet.mosco.utils.GlideBindingAdapter.convertImageIdToUrl(objA.optString("frontImage"), false);
                    dialogData.cardABackUrl = com.vn.jet.mosco.utils.GlideBindingAdapter.convertImageIdToUrl(objA.optString("backImage"), false);
                    dialogData.cardAName = objA.optString("member");
                }
                // Cập nhật Badge (Grade)
                dialogData.cardAGrade = myGrade;

                // Map dữ liệu thẻ B (Partner)
                if (objB != null) {
                    dialogData.cardBUrl = com.vn.jet.mosco.utils.GlideBindingAdapter.convertImageIdToUrl(objB.optString("frontImage"), false);
                    dialogData.cardBBackUrl = com.vn.jet.mosco.utils.GlideBindingAdapter.convertImageIdToUrl(objB.optString("backImage"), false);
                    dialogData.cardBName = objB.optString("member");
                }
                // Cập nhật Badge (Grade)
                dialogData.cardBGrade = partnerGrade;

                if (currentStreakDialog != null && currentStreakDialog.isShowing()) {
                    MoscoDialogHelper.updateCoupleStreakDialog(currentStreakDialog, dialogData, requireActivity());
                } else {
                    currentStreakDialog = MoscoDialogHelper.showCoupleStreakDialog(requireActivity(), status, dialogData, new MoscoDialogHelper.DialogCallback() {
                        @Override
                        public void onPositive() {
                            currentStreakDialog = null;
                            if (status == MoscoDialogHelper.CoupleStatus.INVITE) {
                                requestStreak();
                            } else if (status == MoscoDialogHelper.CoupleStatus.RECEIVED_REQUEST) {
                                acceptStreak();
                            } else if (status == MoscoDialogHelper.CoupleStatus.WAITING) {
                                declineStreak(); // Reuse decline for cancellation
                            }
                        }

                        @Override
                        public void onNegative() {
                            currentStreakDialog = null;
                            if (status == MoscoDialogHelper.CoupleStatus.RECEIVED_REQUEST) {
                                declineStreak();
                            }
                        }
                    });
                }
                
                if (currentStreakDialog != null) {
                    currentStreakDialog.setOnDismissListener(d -> currentStreakDialog = null);
                }
            });
        });
    }

    private void requestStreak() {
        gameApiService.requestCoupleStreak(sessionManager.getUserId(), partnerId).enqueue(new Callback<ApiResponse<CoupleStreakDto>>() {
            @Override
            public void onResponse(Call<ApiResponse<CoupleStreakDto>> call, Response<ApiResponse<CoupleStreakDto>> response) {
                if (isAdded() && response.isSuccessful()) fetchStreakStatus();
            }
            @Override
            public void onFailure(Call<ApiResponse<CoupleStreakDto>> call, Throwable t) {}
        });
    }

    private void acceptStreak() {
        gameApiService.acceptCoupleStreak(sessionManager.getUserId(), currentStreakData.getRequesterId()).enqueue(new Callback<ApiResponse<CoupleStreakDto>>() {
            @Override
            public void onResponse(Call<ApiResponse<CoupleStreakDto>> call, Response<ApiResponse<CoupleStreakDto>> response) {
                if (isAdded() && response.isSuccessful()) fetchStreakStatus();
            }
            @Override
            public void onFailure(Call<ApiResponse<CoupleStreakDto>> call, Throwable t) {}
        });
    }

    private void declineStreak() {
        if (currentStreakData == null) return;
        Long targetId = Objects.equals(currentStreakData.getRequesterId(), sessionManager.getUserId()) 
                        ? partnerId : currentStreakData.getRequesterId();
        
        gameApiService.declineCoupleStreak(sessionManager.getUserId(), targetId).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                if (isAdded() && response.isSuccessful()) fetchStreakStatus();
            }
            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {}
        });
    }

    private void loadHistory() {
        String myId = String.valueOf(sessionManager.getUserId());
        String partnerIdStr = String.valueOf(partnerId);
        AppExecutors.getInstance().diskIO().execute(() -> {
            List<PrivateChatMessage> localMsgs = AppDatabase.getInstance(requireContext())
                    .messageDao().getChatHistory(myId, partnerIdStr);
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    chatAdapter.clear();
                    for (PrivateChatMessage pm : localMsgs) {
                        chatAdapter.addMessage(new WorldChatMessage(pm.getSenderId(), pm.getSenderName(), pm.getAvatarId(), pm.getContent()));
                    }
                    rvChat.scrollToPosition(chatAdapter.getItemCount() - 1);
                });
            }
        });
    }

    private void syncWithServer() {
        String myId = String.valueOf(sessionManager.getUserId());
        gameApiService.getChatHistory(Long.parseLong(myId), partnerId)
                .enqueue(new Callback<ApiResponse<List<PrivateChatMessage>>>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse<List<PrivateChatMessage>>> call, @NonNull Response<ApiResponse<List<PrivateChatMessage>>> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                            processServerMessages(response.body().getData());
                        }
                    }
                    @Override
                    public void onFailure(@NonNull Call<ApiResponse<List<PrivateChatMessage>>> call, @NonNull Throwable t) {
                    }
                });
    }

    private void processServerMessages(List<PrivateChatMessage> serverMsgs) {
        String myId = String.valueOf(sessionManager.getUserId());
        String partnerIdStr = String.valueOf(partnerId);
        List<Long> idsToAck = new ArrayList<>();
        for (PrivateChatMessage sMsg : serverMsgs) {
            if (sMsg.getId() > 0) {
                idsToAck.add(sMsg.getId());
            }
        }

        AppExecutors.getInstance().diskIO().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(requireContext());
            List<PrivateChatMessage> localMsgs = db.messageDao().getChatHistory(myId, partnerIdStr);
            boolean hasNew = false;
            for (PrivateChatMessage sMsg : serverMsgs) {
                boolean exists = false;
                for (PrivateChatMessage lMsg : localMsgs) {
                    if (lMsg.getTimestamp() == sMsg.getTimestamp() && Objects.equals(lMsg.getContent(), sMsg.getContent())) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    db.messageDao().insertMessage(sMsg);
                    hasNew = true;
                }
            }

            if (!idsToAck.isEmpty()) {
                gameApiService.ackMessages(idsToAck).enqueue(new retrofit2.Callback<okhttp3.ResponseBody>() {
                    @Override
                    public void onResponse(@NonNull retrofit2.Call<okhttp3.ResponseBody> call, @NonNull retrofit2.Response<okhttp3.ResponseBody> response) {
                        Log.d(TAG, "Successfully acknowledged " + idsToAck.size() + " messages on Server");
                    }

                    @Override
                    public void onFailure(@NonNull retrofit2.Call<okhttp3.ResponseBody> call, @NonNull Throwable t) {
                        Log.e(TAG, "Failed to ACK messages", t);
                    }
                });
            }

            if (hasNew && isAdded()) {
                requireActivity().runOnUiThread(this::loadHistory);
            }
        });
    }

    private void subscribeToUpdates() {
        if (chatSubscription != null && !chatSubscription.isDisposed()) {
            chatSubscription.dispose();
        }
        chatSubscription = WebSocketManager.getInstance().subscribeToPrivateChat(String.valueOf(sessionManager.getUserId()), message -> {
            // Chỉ thêm tin nhắn vào adapter nếu người gửi LÀ ĐỐI PHƯƠNG.
            // Tránh duplicate vì tin nhắn của chính mình đã được thêm ngay khi gọi sendMessage()
            if (message.getSenderId().equals(String.valueOf(partnerId))) {
                chatAdapter.addMessage(new com.vn.jet.mosco.model.WorldChatMessage(
                    message.getSenderId(), 
                    message.getSenderName(), 
                    message.getAvatarId(), 
                    message.getContent()
                ));
                rvChat.scrollToPosition(chatAdapter.getItemCount() - 1);
            }
        });

        subscribeToStreakUpdates();
        fetchStreakStatus();
    }

    private void subscribeToStreakUpdates() {
        if (streakSubscription != null && !streakSubscription.isDisposed()) {
            streakSubscription.dispose();
        }
        
        Long myId = sessionManager.getUserId();
        streakSubscription = WebSocketManager.getInstance().subscribeToStreakUpdates(String.valueOf(myId), data -> {
            if (data == null) return;
            
            boolean isRelated = (data.getRequesterId().equals(partnerId) && data.getPartnerId().equals(myId))
                             || (data.getRequesterId().equals(myId) && data.getPartnerId().equals(partnerId));
            
            if (isRelated) {
                // Chỉ xử lý nếu có sự thay đổi thực sự trong data
                if (currentStreakData != null && 
                    Objects.equals(currentStreakData.getStatus(), data.getStatus()) && 
                    currentStreakData.getStreakCount() == data.getStreakCount() &&
                    Objects.equals(currentStreakData.getRequesterObjetId(), data.getRequesterObjetId()) &&
                    Objects.equals(currentStreakData.getPartnerObjetId(), data.getPartnerObjetId())) {
                    return; 
                }

                currentStreakData = data;
                updateStreakUI(data.getStreakCount(), "ACTIVE".equals(data.getStatus()));
                
                if ("NONE".equals(data.getStatus()) || "DECLINED".equals(data.getStatus())) {
                    if (currentStreakDialog != null && currentStreakDialog.isShowing()) {
                        currentStreakDialog.dismiss();
                    }
                }
                else if ("PENDING".equals(data.getStatus())) {
                    if (currentStreakDialog == null || !currentStreakDialog.isShowing()) {
                        showCoupleStreakDialog();
                    } else {
                        showCoupleStreakDialog(); // showCoupleStreakDialog giờ đã biết tự update
                    }
                } else if ("ACTIVE".equals(data.getStatus())) {
                    if (currentStreakDialog != null && currentStreakDialog.isShowing()) {
                        showCoupleStreakDialog(); // tự update thay vì dismiss
                    }
                }
            }
        });
    }

    @Override
    public void onStop() {
        super.onStop();
        if (currentStreakDialog != null && currentStreakDialog.isShowing()) {
            currentStreakDialog.dismiss();
            currentStreakDialog = null;
        }
        if (chatSubscription != null) chatSubscription.dispose();
        if (streakSubscription != null) streakSubscription.dispose();
        if (rgbAnimator != null) rgbAnimator.cancel();
    }

    private void sendMessage() {
        String msgText = etInput.getText().toString().trim();
        if (msgText.isEmpty()) return;
        String myId = String.valueOf(sessionManager.getUserId());
        String myName = sessionManager.getIngameName();
        String myAvatar = sessionManager.getAvatarId();
        chatAdapter.addMessage(new WorldChatMessage(myId, myName, myAvatar, msgText));
        rvChat.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
        etInput.setText("");
        PrivateChatMessage pm = new PrivateChatMessage(myId, String.valueOf(partnerId), myName, myAvatar, msgText, System.currentTimeMillis());
        AppExecutors.getInstance().diskIO().execute(() -> {
            AppDatabase.getInstance(requireContext()).messageDao().insertMessage(pm);
        });
        WebSocketManager.getInstance().sendPrivateMessage(pm);
    }

    private void sendFriendRequestInChat() {
        if (getContext() == null || partnerId == null) return;
        
        java.util.Map<String, Long> body = new java.util.HashMap<>();
        body.put("addresseeId", partnerId);

        gameApiService.addFriend(body).enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                try {
                    String msg;
                    if (response.isSuccessful() && response.body() != null) {
                        org.json.JSONObject json = new org.json.JSONObject(response.body().string());
                        msg = json.optString("message", "Đã gửi lời mời kết bạn!");
                        if (btnAddFriendChat != null) {
                            btnAddFriendChat.setText("Pending");
                            btnAddFriendChat.setEnabled(false);
                            btnAddFriendChat.setAlpha(0.6f);
                        }
                    } else if (response.errorBody() != null) {
                        org.json.JSONObject json = new org.json.JSONObject(response.errorBody().string());
                        msg = json.optString("message", "Không thể gửi lời mời!");
                    } else {
                        msg = "Có lỗi xảy ra!";
                    }
                    Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e(TAG, "Lỗi gửi lời mời từ chat", e);
                }
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                Toast.makeText(getContext(), "Lỗi mạng, vui lòng thử lại!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopRGBStreakAnimation();
        if (chatSubscription != null && !chatSubscription.isDisposed()) {
            chatSubscription.dispose();
        }
    }
}

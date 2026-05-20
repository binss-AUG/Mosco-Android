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
import com.vn.jet.mosco.utils.ClickDebounce;
import com.vn.jet.mosco.utils.MoscoDialogHelper;
import com.vn.jet.mosco.model.Objet;
import com.vn.jet.mosco.model.UserStats;

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
    public static final String ARG_IS_ONLINE = "is_online";
    public static final String ARG_IS_STRANGER = "is_stranger";

    private Long partnerId;
    private String partnerName;
    private String partnerAvatar;
    private boolean isOnline;
    private boolean isStranger;

    private RecyclerView rvChat;
    private WorldChatAdapter chatAdapter;
    private EditText etInput;
    private ImageView btnSend, btnBack;
    private ImageView ivHeaderAvatar;
    private LottieAnimationView lottieStreakIcon;
    private TextView tvHeaderName, tvHeaderStatus, tvStreakCount;
    private View btnStreakDetails;
    private View viewStatusDot;

    private SessionManager sessionManager;
    private GameApiService gameApiService;
    private LinearLayoutManager layoutManager;
    private io.reactivex.disposables.Disposable chatSubscription;
    private io.reactivex.disposables.Disposable streakSubscription;
    private android.animation.ValueAnimator rgbAnimator;
    private android.app.AlertDialog currentStreakDialog;

    private CoupleStreakDto currentStreakData;
    private int lastCount = -1;
    private boolean lastActive = false;
    private boolean hasAnimatedStreak = false;

    public ChatPrivateFragment() {
    }

    public static ChatPrivateFragment newInstance(Long partnerId, String partnerName, String partnerAvatar, boolean isOnline, boolean isStranger) {
        ChatPrivateFragment fragment = new ChatPrivateFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_PARTNER_ID, partnerId);
        args.putString(ARG_PARTNER_NAME, partnerName);
        args.putString(ARG_PARTNER_AVATAR, partnerAvatar);
        args.putBoolean(ARG_IS_ONLINE, isOnline);
        args.putBoolean(ARG_IS_STRANGER, isStranger);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            partnerId = getArguments().getLong(ARG_PARTNER_ID);
            String name = getArguments().getString(ARG_PARTNER_NAME, "User");
            if (name == null || name.isEmpty() || name.startsWith("User #") || name.equals("Unknown")) {
                name = "User";
            }
            partnerName = name;
            partnerAvatar = getArguments().getString(ARG_PARTNER_AVATAR, "1");
            isOnline = getArguments().getBoolean(ARG_IS_ONLINE, false);
            isStranger = getArguments().getBoolean(ARG_IS_STRANGER, false);
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
        fetchPartnerOnlineStatus();
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
        viewStatusDot = v.findViewById(R.id.view_private_status_dot);

        tvHeaderName.setText(isStranger ? partnerName + " • Stranger" : partnerName);
        tvHeaderName.setSelected(true);
        AvatarUtils.loadAvatar(getContext(), ivHeaderAvatar, partnerId, partnerAvatar);

        // Preload UI to prevent jitter
        if (tvHeaderStatus != null) {
            tvHeaderStatus.setText(isOnline ? "Online" : "Offline");
            tvHeaderStatus.setTextColor(isOnline ? android.graphics.Color.parseColor("#10B981") : android.graphics.Color.parseColor("#94A3B8"));
        }
        if (viewStatusDot != null) {
            viewStatusDot.setVisibility(isOnline ? View.VISIBLE : View.GONE);
        }
        


        chatAdapter = new WorldChatAdapter();
        chatAdapter.setPrivateChat(true);
        chatAdapter.setCurrentUserId(String.valueOf(sessionManager.getUserId()));
        layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setStackFromEnd(true);
        rvChat.setLayoutManager(layoutManager);
        rvChat.setAdapter(chatAdapter);

        // Tại sao (WHY): Sử dụng AdapterDataObserver để lắng nghe sự thay đổi của Adapter,
        // giúp cuộn màn hình xuống dưới cùng một cách mượt mà và tự động khi có tin nhắn mới (do ta gửi hoặc đối phương nhắn).
        chatAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                super.onItemRangeInserted(positionStart, itemCount);
                int lastVisible = layoutManager.findLastCompletelyVisibleItemPosition();
                boolean isSelf = false;
                if (chatAdapter.getItemCount() > 0) {
                    WorldChatMessage lastMsg = chatAdapter.getMessageAt(chatAdapter.getItemCount() - 1);
                    if (lastMsg != null && lastMsg.getSenderId().equals(String.valueOf(sessionManager.getUserId()))) {
                        isSelf = true;
                    }
                }
                // Tự động cuộn nếu tin nhắn do chính mình gửi hoặc người dùng đang cuộn gần đáy
                if (isSelf || lastVisible >= chatAdapter.getItemCount() - 2) {
                    rvChat.post(() -> rvChat.scrollToPosition(chatAdapter.getItemCount() - 1));
                }
            }
        });

        // Tại sao (WHY): Lắng nghe sự co giãn kích thước của RecyclerView (khi bàn phím ảo mở lên),
        // tự động cuộn bám đáy tin nhắn mới nhất để không bị bàn phím che mất vùng trò chuyện phẳng.
        rvChat.addOnLayoutChangeListener((v1, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (bottom < oldBottom) {
                rvChat.postDelayed(() -> {
                    if (chatAdapter.getItemCount() > 0) {
                        rvChat.scrollToPosition(chatAdapter.getItemCount() - 1);
                    }
                }, 60);
            }
        });

        if (lottieStreakIcon != null) {
            StreakColorHelper.setupStreakLottie(lottieStreakIcon, 0, false);
        }

        // Tự động trích xuất màu chủ đạo từ hình nền của Root View để áp dụng đồng bộ cho Header phẳng (Tỉ lệ vàng)
        View layoutHeader = v.findViewById(R.id.layout_private_chat_header);
        if (layoutHeader != null) {
            int dominantColor = getDominantColor(v.getBackground());
            layoutHeader.setBackgroundColor(dominantColor);
        }

        View btnMore = v.findViewById(R.id.btn_private_chat_more);
        if (btnMore != null) {
            btnMore.setOnClickListener(new ClickDebounce(view -> {
                android.widget.Toast.makeText(getContext(), "Options coming soon!", android.widget.Toast.LENGTH_SHORT).show();
            }));
        }
    }

    /**
     * Trích xuất màu chủ đạo của Background bằng thuật toán downscale canvas 1x1 siêu nhanh và an toàn.
     * Tại sao (WHY): Không phụ thuộc vào thư viện ngoài, xử lý tốt mọi loại Drawable (Color, Gradient, Bitmap),
     * duy trì hiệu năng mượt mà 60fps khi mở màn hình chat.
     */
    private int getDominantColor(android.graphics.drawable.Drawable drawable) {
        if (drawable == null) return android.graphics.Color.parseColor("#0B0F19");
        if (drawable instanceof android.graphics.drawable.ColorDrawable) {
            return ((android.graphics.drawable.ColorDrawable) drawable).getColor();
        }
        try {
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
            drawable.setBounds(0, 0, 1, 1);
            drawable.draw(canvas);
            int color = bitmap.getPixel(0, 0);
            bitmap.recycle();
            return color;
        } catch (Exception e) {
            return android.graphics.Color.parseColor("#0B0F19");
        }
    }

    private void setupListeners() {
        btnBack.setOnClickListener(new ClickDebounce(v -> {
            if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                getParentFragmentManager().popBackStack();
            }
        }));
        btnSend.setOnClickListener(new ClickDebounce(300, v -> sendMessage()));
        if (btnStreakDetails != null) {
            btnStreakDetails.setOnClickListener(new ClickDebounce(v -> showCoupleStreakDialog()));
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
                        return;
                    }

                    // Hiện lại nếu là bạn bè
                    lottieStreakIcon.setVisibility(View.VISIBLE);
                    tvStreakCount.setVisibility(View.VISIBLE);
                    btnStreakDetails.setVisibility(View.VISIBLE);
                    
                    updateStreakUI(currentStreakData.getStreakCount(), "ACTIVE".equals(currentStreakData.getStatus()));
                } else {
                    // Nếu lỗi API hoặc không tìm thấy quan hệ
                    lottieStreakIcon.setVisibility(View.GONE);
                    tvStreakCount.setVisibility(View.GONE);
                    btnStreakDetails.setVisibility(View.GONE);
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

        // State 1: Chưa đồng ý chuỗi (hoặc status != ACTIVE) -> giữ nguyên xám, không động, số 999
        if (!active) {
            if (tvStreakCount != null) {
                tvStreakCount.setText("999");
                tvStreakCount.setTextColor(android.graphics.Color.GRAY);
            }
            if (lottieStreakIcon != null) {
                StreakColorHelper.setupStreakLottie(lottieStreakIcon, 0, false);
            }
            stopRGBStreakAnimation();
            return;
        }

        // State 2: Đồng ý với nhau nhưng hôm nay chưa nhắn tin -> thay số 999 thành số hiện tại, ngọn lửa giữ nguyên xám và không động
        if (!interactedToday) {
            if (tvStreakCount != null) {
                tvStreakCount.setText(String.valueOf(count));
                tvStreakCount.setTextColor(android.graphics.Color.GRAY);
            }
            if (lottieStreakIcon != null) {
                StreakColorHelper.setupStreakLottie(lottieStreakIcon, 0, false);
            }
            stopRGBStreakAnimation();
            return;
        }

        // State 3: Trường hợp còn lại (đã đồng ý VÀ đã nhắn tin hôm nay) -> ngọn lửa fade-in màu vốn có từ dưới + chuyển động
        if (tvStreakCount != null) {
            tvStreakCount.setText(String.valueOf(count));
            tvStreakCount.setTextColor(android.graphics.Color.WHITE);
        }

        if (lottieStreakIcon != null) {
            // Ràng buộc Lottie: Đảm bảo frame min/max đúng
            if (lottieStreakIcon.getMinFrame() != 0 || lottieStreakIcon.getMaxFrame() != 24) {
                lottieStreakIcon.setMinAndMaxFrame(0, 24);
            }
            if (!lottieStreakIcon.isAnimating()) {
                lottieStreakIcon.playAnimation();
            }

            if (!hasAnimatedStreak) {
                hasAnimatedStreak = true;

                // Lấy kích thước và offset từ Dimens hệ thống - Tuyệt đối không hardcode
                float size28 = getResources().getDimension(R.dimen.spacing_28dp);
                float offset15 = getResources().getDimension(R.dimen.spacing_15dp);

                // Pivot ở đáy trung tâm để ngọn lửa bùng cháy vươn lên từ dưới
                float pivotX = lottieStreakIcon.getWidth() > 0 ? lottieStreakIcon.getWidth() / 2f : size28 / 2f;
                float pivotY = lottieStreakIcon.getHeight() > 0 ? lottieStreakIcon.getHeight() : size28;
                lottieStreakIcon.setPivotX(pivotX);
                lottieStreakIcon.setPivotY(pivotY);

                // Khởi tạo các thuộc tính chuyển động & xám ban đầu trước hoạt họa
                lottieStreakIcon.setScaleX(0.5f);
                lottieStreakIcon.setScaleY(0.5f);
                lottieStreakIcon.setTranslationY(offset15);
                StreakColorHelper.applyStreakColorTransition(lottieStreakIcon, count, 0f);

                android.animation.ValueAnimator transitionAnim = android.animation.ValueAnimator.ofFloat(0f, 1f);
                transitionAnim.setDuration(1000); // 1 giây chuyển đổi mượt mà, luxury
                transitionAnim.setInterpolator(new android.view.animation.DecelerateInterpolator());

                transitionAnim.addUpdateListener(animation -> {
                    float f = (float) animation.getAnimatedValue();
                    
                    // 1. Smoothly interpolate color matrix from grayscale (0.0) to level color (1.0)
                    StreakColorHelper.applyStreakColorTransition(lottieStreakIcon, count, f);
                    
                    // 2. Smoothly scale and lift up from bottom
                    lottieStreakIcon.setScaleX(0.5f + 0.5f * f);
                    lottieStreakIcon.setScaleY(0.5f + 0.5f * f);
                    lottieStreakIcon.setTranslationY(offset15 * (1f - f));
                });

                transitionAnim.addListener(new android.animation.AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(android.animation.Animator animation) {
                        lottieStreakIcon.setScaleX(1f);
                        lottieStreakIcon.setScaleY(1f);
                        lottieStreakIcon.setTranslationY(0f);
                        StreakColorHelper.setupStreakLottie(lottieStreakIcon, count, true);
                        
                        if (count >= 1000) {
                            startRGBStreakAnimation(lottieStreakIcon);
                        } else {
                            stopRGBStreakAnimation();
                        }
                    }
                });
                transitionAnim.start();
            } else {
                StreakColorHelper.setupStreakLottie(lottieStreakIcon, count, true);
                if (count >= 1000) {
                    startRGBStreakAnimation(lottieStreakIcon);
                } else {
                    stopRGBStreakAnimation();
                }
            }
        }
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
            AppDatabase db = AppDatabase.getInstance(requireContext());
            db.messageDao().markAsRead(myId, partnerIdStr);
            List<PrivateChatMessage> localMsgs = db.messageDao().getChatHistory(myId, partnerIdStr);
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    List<WorldChatMessage> newList = new ArrayList<>();
                    for (PrivateChatMessage pm : localMsgs) {
                        newList.add(new WorldChatMessage(pm.getSenderId(), pm.getSenderName(), pm.getAvatarId(), pm.getContent(), pm.getTimestamp()));
                    }
                    chatAdapter.setMessages(newList);
                    rvChat.post(() -> {
                        if (chatAdapter.getItemCount() > 0) {
                            rvChat.scrollToPosition(chatAdapter.getItemCount() - 1);
                        }
                    });
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
                // Tại sao (WHY): Không lưu tin nhắn điều khiển [SEEN] hệ thống vào local DB
                if (sMsg.getContent() != null && sMsg.getContent().startsWith("[SEEN]:")) {
                    continue;
                }
                boolean exists = false;
                for (PrivateChatMessage lMsg : localMsgs) {
                    // Tại sao (WHY): So sánh khoảng cách thời gian (chênh lệch dưới 10 giây) thay vì bằng tuyệt đối
                    // để khắc phục triệt để hiện tượng trùng lặp tin nhắn do độ phân giải mili giây giữa Client và MySQL Server.
                    long diff = Math.abs(lMsg.getTimestamp() - sMsg.getTimestamp());
                    if (diff < 10000 && Objects.equals(lMsg.getContent(), sMsg.getContent()) && lMsg.getSenderId().equals(sMsg.getSenderId())) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    // Tại sao (WHY): Đánh dấu đã đọc vì người dùng hiện đang ở trong phòng chat và nhìn thấy tin nhắn này.
                    // Sửa triệt để lỗi quay ra Inbox vẫn báo 1 tin nhắn chưa đọc.
                    sMsg.setRead(true);
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
        WebSocketManager.getInstance().connect();
        chatSubscription = WebSocketManager.getInstance().subscribeToPrivateChat(String.valueOf(sessionManager.getUserId()), message -> {
            if (message == null || !isAdded()) return;

            // Nhận tin nhắn chat bình thường từ đối phương
            if (message.getSenderId().equals(String.valueOf(partnerId))) {
                // 1. Lưu ngay vào local Room DB để đảm bảo kiến trúc Local-First
                // Tại sao (WHY): Đánh dấu đã đọc ngay vì người dùng đang ở trong phòng chat này.
                // Tránh triệt để lỗi khi quay lại danh sách Inbox vẫn hiển thị vòng tròn chưa đọc.
                message.setRead(true);
                AppExecutors.getInstance().diskIO().execute(() -> {
                    AppDatabase db = AppDatabase.getInstance(requireContext());
                    db.messageDao().insertMessage(message);
                });

                // 2. Gửi lệnh ACK báo cho server biết đã nhận được tin nhắn
                List<Long> idsToAck = new ArrayList<>();
                if (message.getId() > 0) {
                    idsToAck.add(message.getId());
                }
                if (!idsToAck.isEmpty()) {
                    gameApiService.ackMessages(idsToAck).enqueue(new retrofit2.Callback<okhttp3.ResponseBody>() {
                        @Override
                        public void onResponse(@NonNull retrofit2.Call<okhttp3.ResponseBody> call, @NonNull retrofit2.Response<okhttp3.ResponseBody> response) {
                            Log.d(TAG, "Gửi ACK thành công cho tin nhắn thời gian thực");
                        }
                        @Override
                        public void onFailure(@NonNull retrofit2.Call<okhttp3.ResponseBody> call, @NonNull Throwable t) {
                            Log.e(TAG, "Gửi ACK thất bại cho tin nhắn thời gian thực", t);
                        }
                    });
                }

                // 3. Cập nhật giao diện UI bong bóng chat
                requireActivity().runOnUiThread(() -> {
                    chatAdapter.addMessage(new com.vn.jet.mosco.model.WorldChatMessage(
                        message.getSenderId(), 
                        message.getSenderName(), 
                        message.getAvatarId(), 
                        message.getContent(),
                        message.getTimestamp()
                    ));
                });
            }
        });

        subscribeToStreakUpdates();
        fetchStreakStatus();
        fetchPartnerRelationship();
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
        long now = System.currentTimeMillis();
        
        // Tại sao (WHY): Reset text input TRƯỚC KHI thêm tin nhắn vào adapter.
        // Điều này giúp bắt đầu quá trình resize (co lại) của EditText sớm hơn,
        // tránh xung đột layout pass gây nhảy khung cuộn khi scrollToPosition chạy.
        etInput.setText("");
        
        chatAdapter.addMessage(new WorldChatMessage(myId, myName, myAvatar, msgText, now));
        // AdapterDataObserver đã tự động gọi scrollToPosition khi có tin nhắn mới (isSelf == true).
        PrivateChatMessage pm = new PrivateChatMessage(myId, String.valueOf(partnerId), myName, myAvatar, msgText, now);
        AppExecutors.getInstance().diskIO().execute(() -> {
            AppDatabase.getInstance(requireContext()).messageDao().insertMessage(pm);
        });
        WebSocketManager.getInstance().sendPrivateMessage(pm);
    }

    private void fetchPartnerOnlineStatus() {
        if (getContext() == null || partnerId == null) return;
        gameApiService.getFriendList().enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        org.json.JSONObject json = new org.json.JSONObject(response.body().string());
                        org.json.JSONArray friendsArr = json.optJSONArray("data");
                        if (friendsArr != null) {
                            boolean isOnline = false;
                            for (int i = 0; i < friendsArr.length(); i++) {
                                org.json.JSONObject friendObj = friendsArr.getJSONObject(i);
                                if (friendObj.optLong("userId") == partnerId) {
                                    isOnline = friendObj.optBoolean("online", false);
                                    break;
                                }
                            }
                            // No status tick online update on adapter
                            if (tvHeaderStatus != null) {
                                tvHeaderStatus.setText(isOnline ? "Online" : "Offline");
                                tvHeaderStatus.setTextColor(isOnline ? android.graphics.Color.parseColor("#10B981") : android.graphics.Color.parseColor("#94A3B8"));
                            }
                            if (viewStatusDot != null) {
                                viewStatusDot.setVisibility(isOnline ? View.VISIBLE : View.GONE);
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error checking partner online status", e);
                }
            }
            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {}
        });
    }

    private void fetchPartnerRelationship() {
        if (getContext() == null || partnerId == null) return;
        gameApiService.getUserStats(partnerId).enqueue(new Callback<UserStats>() {
            @Override
            public void onResponse(Call<UserStats> call, Response<UserStats> response) {
                if (!isAdded() || getContext() == null) return;
                if (response.isSuccessful() && response.body() != null) {
                    UserStats stats = response.body();
                    updateRelationshipUI(stats);
                }
            }
            @Override
            public void onFailure(Call<UserStats> call, Throwable t) {
                Log.e(TAG, "Failed to fetch relationship for partner " + partnerId, t);
            }
        });
    }

    private void updateRelationshipUI(UserStats stats) {
        if (stats == null || !isAdded()) return;
        
        String fullName = stats.getIngameName();
        String username = stats.getUsername();
        String displayName = (fullName != null && !fullName.isEmpty()) ? fullName : username;
        if (displayName != null && !displayName.isEmpty()) {
            partnerName = displayName;
            if (tvHeaderName != null) {
                boolean isReallyStranger = stats.getFriendshipStatus() != 2;
                tvHeaderName.setText(isReallyStranger ? partnerName + " • Stranger" : partnerName);
                tvHeaderName.setSelected(true);
            }
        }
        if (stats.getAvatarId() != null && !stats.getAvatarId().isEmpty()) {
            partnerAvatar = stats.getAvatarId();
            if (ivHeaderAvatar != null) {
                AvatarUtils.loadAvatar(getContext(), ivHeaderAvatar, partnerId, partnerAvatar);
            }
        }

        int status = stats.getFriendshipStatus();
        if (status == 2) {
            // Friends
            if (lottieStreakIcon != null) lottieStreakIcon.setVisibility(View.VISIBLE);
            if (tvStreakCount != null) tvStreakCount.setVisibility(View.VISIBLE);
            if (btnStreakDetails != null) btnStreakDetails.setVisibility(View.VISIBLE);
        } else {
            // Stranger or Pending states
            if (lottieStreakIcon != null) lottieStreakIcon.setVisibility(View.GONE);
            if (tvStreakCount != null) tvStreakCount.setVisibility(View.GONE);
            if (btnStreakDetails != null) btnStreakDetails.setVisibility(View.GONE);
        }
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

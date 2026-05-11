package com.vn.jet.mosco.fragment;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.widget.AppCompatButton;
import androidx.cardview.widget.CardView;
import androidx.core.view.GestureDetectorCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.google.gson.JsonObject;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.network.GachaRepository;
import com.vn.jet.mosco.model.GachaSpinRequest;
import com.vn.jet.mosco.model.GachaSpinResponse;
import com.vn.jet.mosco.utils.SessionManager;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SpinFragment extends Fragment {

    private View btnAddObjet;
    private ImageView ivSelectedObjet;
    private com.vn.jet.mosco.widget.MoscoButton btnSpin;
    private FrameLayout bgCardsContainer;
    private com.google.android.material.card.MaterialCardView cardCenterSlot;
    private View layoutSelectedFront;
    private VideoView videoSpinEffect;
    private FrameLayout videoContainer;

    // UI Phases
    private View layoutSpinMain;
    private View layoutRevealGrid;
    private RecyclerView rvSecretGrid;
    private com.vn.jet.mosco.widget.MoscoButton btnConfirmSelect;
    private SecretCardAdapter secretAdapter;
    private int selectedPosition = -1;
    private int secretCardHeight = 0;
    private OnBackPressedCallback backPressedCallback;

    // Result UI
    private View layoutResultReveal;
    private CardView cardResultFinal;
    private ImageView ivResultImage;
    private ImageView ivResultBack;
    private View viewNeonGlow;
    private com.vn.jet.mosco.widget.MoscoButton btnCollect;
    private TextView tvRevealUnchosen;
    private TextView tvResultTitle;
    private TextView tvResultSubtitle;

    // Reveal Result Grid UI
    private View layoutRevealResultGrid;
    private RecyclerView rvRevealResultGrid;
    private com.vn.jet.mosco.widget.MoscoButton btnTryAgain;
    private ImageView ivRevealBack;
    private RevealResultAdapter revealResultAdapter;
    private final java.util.List<BackgroundCardState> bgCardStates = new java.util.ArrayList<>();
    private ValueAnimator masterBgAnimator;

    private static class BackgroundCardState {
        final ImageView view;
        final long duration, startTimeOffset;
        final float beamWidth, cardBaseScale, zMax;
        final int lightBase, lightRange;
        
        BackgroundCardState(ImageView view, long duration, long startTimeOffset, 
                            float beamWidth, float cardBaseScale, float zMax,
                            int lightBase, int lightRange) {
            this.view = view;
            this.duration = duration;
            this.startTimeOffset = startTimeOffset;
            this.beamWidth = beamWidth;
            this.cardBaseScale = cardBaseScale;
            this.zMax = zMax;
            this.lightBase = lightBase;
            this.lightRange = lightRange;
        }

        void update(long currentTimeMillis, int screenWidth) {
            long elapsed = (currentTimeMillis + startTimeOffset) % duration;
            float fraction = (float) elapsed / duration;
            
            // Di chuyển ngang từ trái sang phải (Linear Slideshow)
            float startX = -screenWidth * 0.8f;
            float endX = screenWidth * 0.8f;
            float tx = startX + (endX - startX) * fraction;
            
            view.setTranslationX(tx);
            view.setTranslationY(0); 
            
            // TƯƠNG TÁC ÁNH SÁNG (Light Interaction at Center)
            float distToCenter = Math.abs(tx);
            float lightIntensity = 0f;
            if (distToCenter < beamWidth) {
                lightIntensity = 1f - (distToCenter / beamWidth);
                lightIntensity = (float) Math.pow(lightIntensity, 0.6); 
            }

            // Điều chỉnh độ sáng qua ColorFilter (Multiply)
            int colorVal = (int) (lightBase + lightRange * lightIntensity); 
            view.setColorFilter(android.graphics.Color.rgb(colorVal, colorVal, colorVal), android.graphics.PorterDuff.Mode.MULTIPLY);
            
            view.setScaleX(cardBaseScale);
            view.setScaleY(cardBaseScale);
            view.setAlpha(1.0f);
            view.setTranslationZ(lightIntensity * zMax);
        }
    }
    private FrameLayout layoutDustContainer;
    private int revealCardHeight = 0;

    private GachaRepository gachaRepository;
    private GachaSpinResponse currentSpinResult;
    private String selectedSacrificeId;
    private List<Map<String, Object>> gridSessionCards = new ArrayList<>();
    private int sessionResultIndex = -1;

    private volatile boolean videoComplete = false;
    private volatile boolean preloadComplete = false;

    // ── 3D Flip cho thẻ hi sinh (Sacrifice Card) ──
    private static final int FLIP_HALF_DURATION = 250;
    private static final int SWIPE_THRESHOLD = 100;
    private static final int SWIPE_VELOCITY_THRESHOLD = 100;
    private ImageView ivSacrificeBack;
    private boolean isSacrificeFlipped = false;
    private boolean isSacrificeFlipAnimating = false;
    private String sacrificeBackImageUrl = "";
    private GestureDetectorCompat sacrificeGestureDetector;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Khởi tạo Repository để giao tiếp với Server
        gachaRepository = new GachaRepository(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_spin, container, false);

        layoutSpinMain = view.findViewById(R.id.layout_spin_main);
        layoutRevealGrid = view.findViewById(R.id.layout_reveal_grid);
        rvSecretGrid = view.findViewById(R.id.rv_secret_grid);
        btnConfirmSelect = view.findViewById(R.id.btn_confirm_select);

        layoutResultReveal = view.findViewById(R.id.layout_result_reveal);
        cardResultFinal = view.findViewById(R.id.card_result_final);
        ivResultImage = view.findViewById(R.id.iv_result_image);
        ivResultBack = view.findViewById(R.id.iv_result_back);
        viewNeonGlow = view.findViewById(R.id.view_neon_glow);
        btnCollect = view.findViewById(R.id.btn_collect);
        tvRevealUnchosen = view.findViewById(R.id.tv_reveal_unchosen);
        tvResultTitle = view.findViewById(R.id.tv_result_title);
        tvResultSubtitle = view.findViewById(R.id.tv_result_subtitle);
        btnCollect.setOnClickListener(v -> resetToSpinMain());

        // Reveal Result Grid UI
        layoutRevealResultGrid = view.findViewById(R.id.layout_reveal_result_grid);
        rvRevealResultGrid = view.findViewById(R.id.rv_reveal_result_grid);
        btnTryAgain = view.findViewById(R.id.btn_try_again);
        ivRevealBack = view.findViewById(R.id.iv_reveal_back);

        // Reveal unchosen Objekts click listener
        tvRevealUnchosen.setOnClickListener(v -> showRevealResultGrid());

        // Back arrow từ reveal result grid → result screen
        ivRevealBack.setOnClickListener(v -> {
            layoutRevealResultGrid.setVisibility(View.GONE);
            layoutResultReveal.setVisibility(View.VISIBLE);
        });

        // Header info button
        ImageView ivInfo = view.findViewById(R.id.iv_spin_info);
        if (ivInfo != null) {
            ivInfo.setOnClickListener(v -> showSpinInfoDialog());
        }

        // Try again → reset to spin main
        btnTryAgain.setOnClickListener(v -> {
            layoutRevealResultGrid.setVisibility(View.GONE);
            resetToSpinMain();
        });

        cardCenterSlot = view.findViewById(R.id.card_center_slot);
        btnAddObjet = view.findViewById(R.id.btn_add_objet);
        layoutSelectedFront = view.findViewById(R.id.layout_selected_front);
        ivSelectedObjet = view.findViewById(R.id.card_iv_image);
        ivSacrificeBack = view.findViewById(R.id.iv_sacrifice_back);
        btnSpin = view.findViewById(R.id.btn_spin);
        bgCardsContainer = view.findViewById(R.id.layout_bg_cards_container);
        videoSpinEffect = view.findViewById(R.id.video_spin_effect);
        videoContainer = view.findViewById(R.id.video_container);
        layoutDustContainer = view.findViewById(R.id.layout_dust_container);

        view.setBackgroundColor(Color.BLACK);
        view.post(() -> {
            startBackgroundAnimation();
            startDustEffect();
        });

        // Khởi tạo Gesture Detector cho cơ chế 3D Flip thẻ hi sinh
        initSacrificeFlipGesture();

        btnSpin.setOnClickListener(v -> showConfirmDialog());
        btnConfirmSelect.setOnClickListener(v -> {
            btnConfirmSelect.setEnabled(false);

            // QuanTum Swap Matrix: Giúp Card mà player bấm CHẮC CHẮN là result được bốc bởi Server
            if (selectedPosition != -1 && selectedPosition != sessionResultIndex && selectedPosition < gridSessionCards.size()) {
                Map<String, Object> temp = gridSessionCards.get(selectedPosition);
                gridSessionCards.set(selectedPosition, currentSpinResult.getCardData());
                gridSessionCards.set(sessionResultIndex, temp);
                sessionResultIndex = selectedPosition;
            }

            if (layoutRevealGrid != null) {
                // Screen fade before cutscene (opacity 100% -> 0% in 150ms)
                layoutRevealGrid.animate()
                        .alpha(0f)
                        .setDuration(150)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                playRewardVideoAnimation();
                            }
                        })
                        .start();
            } else {
                playRewardVideoAnimation();
            }
        });

        // Chặn nút back khi ở trạng thái 16 thẻ
        backPressedCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                // Không làm gì — chặn back
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backPressedCallback);

        return view;
    }

    private void toggleBottomNavigation(boolean show) {
        if (getActivity() != null) {
            View navBar = getActivity().findViewById(R.id.bottom_navigation);
            if (navBar != null) navBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void showConfirmDialog() {
        if (requireContext() == null) return;
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_spin_confirm, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            com.vn.jet.mosco.widget.MoscoButton btnC = (com.vn.jet.mosco.widget.MoscoButton) v;
            btnC.setEnabled(false);
            btnC.setText(getString(R.string.spin_action_charging));

            // BẮT ĐẦU LOAD DATA NGẦM NGAY KHI ẤN
            gachaRepository.spinCard(new GachaSpinRequest(selectedSacrificeId), new GachaRepository.GachaCallback<GachaSpinResponse>() {
                @Override
                public void onSuccess(GachaSpinResponse response) {
                    com.vn.jet.mosco.utils.DatabaseLoader.clearUserCache();
                    Long userId = new com.vn.jet.mosco.utils.SessionManager(requireContext()).getUserId();
                    com.vn.jet.mosco.network.GameApiService apiService = com.vn.jet.mosco.network.ApiClient.getClient(requireContext()).create(com.vn.jet.mosco.network.GameApiService.class);
                    com.vn.jet.mosco.utils.DatabaseLoader.reloadInventoryFromServer(requireContext(), userId, apiService);

                    currentSpinResult = response;
                    gridSessionCards.clear();
                    if (response.getCardData() != null) gridSessionCards.add(response.getCardData());
                    if (response.getRevealGrid() != null) gridSessionCards.addAll(response.getRevealGrid());
                    java.util.Collections.shuffle(gridSessionCards);
                    sessionResultIndex = -1;
                    for (int i = 0; i < gridSessionCards.size(); i++) {
                        String cid = String.valueOf(gridSessionCards.get(i).get("id"));
                        if (cid != null && cid.equals(response.getItemId())) {
                            sessionResultIndex = i;
                            break;
                        }
                    }
                    preloadAssets(gridSessionCards);
                }

                @Override
                public void onError(int httpCode, String errorMessage) {
                    android.util.Log.e("SpinFragment", "Lỗi Spin: " + errorMessage);
                    videoComplete = true;
                    android.widget.Toast.makeText(requireContext(), getString(R.string.common_msg_system_error) + ": " + errorMessage, android.widget.Toast.LENGTH_LONG).show();
                    resetToSpinMain();
                }
            });

            // ⏳ TRỄ 0.6s: Để luồng Preload Assets có thời gian bứt phá trước khi Video bắt đầu
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                dialog.dismiss();
                playSpinVideoEffect();
            }, 600);
        });
        dialog.show();
    }
    private void checkReadyToReveal() {
        if (videoComplete && preloadComplete) {
            if (getActivity() != null) {
                getActivity().runOnUiThread(this::spinGridSecret);
            }
        }
    }

    private void handleVideoComplete() {
        videoComplete = true;
        checkReadyToReveal();
    }

    private void playSpinVideoEffect() {
        playVideo(R.raw.spin_animation, 0, 0, this::handleVideoComplete);
    }

    private void playRewardVideoAnimation() {
        if (selectedPosition == -1) return;
        if (backPressedCallback != null) backPressedCallback.setEnabled(false);
        playVideo(R.raw.spin_reward_animation, 100, 100, this::showFinalResultWithNeonEffect);

        // 🚀 WARMUP PERFORMANCE: Nạp và vẽ nháp màn hình kết quả ngầm trong khi Video đang chạy
        warmupResultUI();
    }

    private void warmupResultUI() {
        if (currentSpinResult == null || selectedPosition == -1 || layoutResultReveal == null) return;

        // Hiện layout ở mức tàng hình (elevation 10dp vẫn dưới Video 100dp) để Android render sẵn
        layoutResultReveal.setAlpha(0.01f);
        layoutResultReveal.setVisibility(View.VISIBLE);

        String frontUrl = getCardImageUrl(selectedPosition, true);
        String backUrl = getCardImageUrl(selectedPosition, false);

        loadCardImageInto(frontUrl, ivResultImage);
        loadCardImageInto(backUrl, ivResultBack);

        if (currentSpinResult.getCardData() != null) {
            String name = String.valueOf(currentSpinResult.getCardData().get("name"));
            if (tvResultSubtitle != null) tvResultSubtitle.setText(getString(R.string.spin_format_received_objet, name));
        }
    }

    /**
     * @param fadeOutMs nếu > 0, fade out container trong N ms cuối video
     * @param earlyCompleteMs nếu > 0, gọi onComplete sớm hơn N ms trước khi video kết thúc
     */
    private void playVideo(int resId, int fadeOutMs, int earlyCompleteMs, Runnable onComplete) {
        if (videoSpinEffect == null || videoContainer == null || requireContext() == null) return;
        if (layoutSpinMain != null) layoutSpinMain.setVisibility(View.GONE);
        if (layoutRevealGrid != null) layoutRevealGrid.setVisibility(View.GONE);
        if (layoutResultReveal != null) layoutResultReveal.setVisibility(View.GONE);
        if (layoutRevealResultGrid != null) layoutRevealResultGrid.setVisibility(View.GONE);
        toggleBottomNavigation(false);
        videoContainer.setVisibility(View.VISIBLE);
        videoContainer.setAlpha(1f);
        videoContainer.setBackgroundColor(Color.BLACK);

        // Disable elevation which causes shadow/white frame artifacts
        if (videoSpinEffect != null) {
            videoSpinEffect.animate().cancel();
            videoSpinEffect.setZ(0f);
            videoSpinEffect.setAlpha(0f); // Hide the underlying SurfaceView flash until video generates its first frame
        }

        FrameLayout.LayoutParams resetLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        resetLp.gravity = Gravity.CENTER;
        videoSpinEffect.setLayoutParams(resetLp);
        Uri videoUri = Uri.parse("android.resource://" + requireActivity().getPackageName() + "/" + resId);
        videoSpinEffect.setVideoURI(videoUri);
        final boolean[] completeCalled = {false};
        videoSpinEffect.setOnPreparedListener(mp -> {
            float videoRatio = mp.getVideoWidth() / (float) mp.getVideoHeight();
            int targetWidth = videoContainer.getWidth();
            int targetHeight = (int) (targetWidth / videoRatio);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(targetWidth, targetHeight);
            lp.gravity = Gravity.CENTER;
            videoSpinEffect.setLayoutParams(lp);
            videoSpinEffect.setScaleX(1.0f);
            videoSpinEffect.setScaleY(1.0f);
            mp.setLooping(false);

            // Show video with smooth fade-in (0.2s)
            videoSpinEffect.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .start();

            videoSpinEffect.start();

            int duration = mp.getDuration();

            // Fade out trong N ms cuối video
            if (fadeOutMs > 0) {
                int fadeStartTime = Math.max(0, duration - fadeOutMs);
                videoContainer.postDelayed(() -> {
                    if (isAdded() && videoContainer != null) {
                        videoContainer.animate()
                                .alpha(0f)
                                .setDuration(fadeOutMs)
                                .setInterpolator(new LinearInterpolator())
                                .start();
                    }
                }, fadeStartTime);
            }

            // Gọi onComplete sớm hơn N ms trước khi video kết thúc
            if (earlyCompleteMs > 0) {
                int earlyTime = Math.max(0, duration - earlyCompleteMs);
                videoContainer.postDelayed(() -> {
                    if (isAdded() && !completeCalled[0]) {
                        completeCalled[0] = true;
                        onComplete.run();
                    }
                }, earlyTime);
            }
        });
        videoSpinEffect.setOnCompletionListener(mp -> {
            if (isAdded()) {
                videoContainer.animate().cancel();
                videoContainer.setAlpha(1f);
                videoContainer.setVisibility(View.GONE);
                if (!completeCalled[0]) {
                    completeCalled[0] = true;
                    onComplete.run();
                }
            }
        });
    }

    private void spinGridSecret() {
        toggleBottomNavigation(false);
        if (backPressedCallback != null) backPressedCallback.setEnabled(true);

        // Đợi 0.3s sau khi video tắt hẳn, rồi fade-in giao diện 16 thẻ
        if (layoutRevealGrid != null) {
            layoutRevealGrid.setAlpha(0f);
            layoutRevealGrid.setVisibility(View.VISIBLE);
        }

        if (rvSecretGrid != null) {
            rvSecretGrid.setLayoutManager(new GridLayoutManager(requireContext(), 4));

            // Đợi layout đo xong, rồi tính kích thước thẻ
            rvSecretGrid.post(() -> {
                if (!isAdded()) return;

                float density = getResources().getDisplayMetrics().density;
                int itemMarginPx = (int) (4 * density); // 4dp margin mỗi bên

                // Tính chiều cao theo ratio 1:1.54 từ chiều rộng
                int availableWidth = rvSecretGrid.getWidth();
                int totalHorizontalMargins = itemMarginPx * 2 * 4; // left+right * 4 cols
                int itemWidth = (availableWidth - totalHorizontalMargins) / 4;
                secretCardHeight = (int) (itemWidth * 1.54f);

                secretAdapter = new SecretCardAdapter();
                rvSecretGrid.setAdapter(secretAdapter);

                // Start shine effect loop
                startShineEffectLoop();

                // Fade-in nhanh
                if (layoutRevealGrid != null) {
                    layoutRevealGrid.animate()
                            .alpha(1f)
                            .setDuration(150)
                            .setInterpolator(new AccelerateDecelerateInterpolator())
                            .start();
                }
            });
        }
    }

    private String getCardImageUrl(int position, boolean isFront) {
        if (position >= 0 && position < gridSessionCards.size()) {
            Map<String, Object> obj = gridSessionCards.get(position);
            if (obj == null) return "";

            String key = isFront ? "frontImage" : "backImage";
            Object urlObj = obj.get(key);
            if (urlObj == null || String.valueOf(urlObj).equalsIgnoreCase("null") || String.valueOf(urlObj).isEmpty()) {
                // Trả về một URL đặc biệt hoặc để trống để load Card Back mặc định
                return isFront ? "dummy://trash_object" : "";
            }

            String url = String.valueOf(urlObj);
            if (isFront && position != sessionResultIndex) {
                if (url.endsWith("/4x"))
                    url = url.substring(0, url.length() - 3) + "/1x";
                else if (url.endsWith("/original"))
                    url = url.substring(0, url.length() - 9) + "/1x";
            }
            return url;
        }
        return "";
    }

    private void preloadAssets(List<Map<String, Object>> cards) {
        new Thread(() -> {
            java.util.List<String> priorityUrls = new ArrayList<>();
            java.util.List<String> normalUrls = new ArrayList<>();

            for (int i = 0; i < cards.size(); i++) {
                Map<String, Object> card = cards.get(i);
                if (card == null) continue;

                String frontUrl = String.valueOf(card.get("frontImage"));
                String backUrl = String.valueOf(card.get("backImage"));

                if (i == sessionResultIndex) {
                    // 🎯 KẾT QUẢ CƯỜNG HÓA: Phải tải bản 4x Xịn nhất (Cả 2 mặt)
                    if (frontUrl != null && !frontUrl.isEmpty() && !frontUrl.equals("null")) priorityUrls.add(frontUrl);
                    if (backUrl != null && !backUrl.isEmpty() && !backUrl.equals("null")) priorityUrls.add(backUrl);
                } else {
                    // 🗑️ THẺ RÁC: Chỉ cần bản 1x nhẹ nhàng để hiện Grid
                    if (frontUrl == null || frontUrl.equalsIgnoreCase("null") || frontUrl.isEmpty()) {
                        // Rác mặc định, không cần tải mạng
                    } else {
                        if (frontUrl.endsWith("/4x")) frontUrl = frontUrl.substring(0, frontUrl.length() - 3) + "/1x";
                        else if (frontUrl.endsWith("/original")) frontUrl = frontUrl.substring(0, frontUrl.length() - 9) + "/1x";
                        normalUrls.add(frontUrl);
                    }
                }
            }

            int totalPriority = priorityUrls.size();
            int totalNormal = normalUrls.size();
            java.util.concurrent.atomic.AtomicInteger finishedCount = new java.util.concurrent.atomic.AtomicInteger(0);
            int totalToLoad = totalPriority + totalNormal;

            if (totalToLoad == 0) {
                preloadComplete = true;
                checkReadyToReveal();
                return;
            }

            // --- BƯỚC 1: TẢI ẢNH ƯU TIÊN (HÀNG KHỦNG 4X) ---
            for (String u : priorityUrls) {
                Glide.with(requireContext().getApplicationContext())
                        .asBitmap()
                        .load(u)
                        .priority(Priority.IMMEDIATE) // Đẩy lên đầu hàng đợi nạp CPU/Network
                        .timeout(10000)
                        .submit();
                if (finishedCount.incrementAndGet() >= totalToLoad) {
                    preloadComplete = true;
                    checkReadyToReveal();
                }
            }

            // --- BƯỚC 2: TẢI ẢNH THẺ RÁC (1X) ---
            for (String u : normalUrls) {
                Glide.with(requireContext().getApplicationContext())
                        .asBitmap()
                        .load(u)
                        .priority(Priority.LOW)
                        .submit();
                if (finishedCount.incrementAndGet() >= totalToLoad) {
                    preloadComplete = true;
                    checkReadyToReveal();
                }
            }
        }).start();
    }

    private void loadCardImageInto(String url, ImageView imageView) {
        if (!isAdded() || url == null || url.isEmpty() || imageView == null) return;
        try {
            imageView.setAlpha(1.0f);
            if (url.equals("dummy://trash_object")) {
                // 🚀 TỐI ƯU CỰC ĐẠI: Dùng trực tiếp Resource để tránh Glide delay cho rác
                imageView.setImageResource(R.drawable.trash_objet);
                imageView.setAlpha(0.6f);
            } else {
                // 💎 LOCAL FIRST THUMBNAIL TRICK:
                // Dùng bản 2x từ máy làm ảnh chờ cho các loại hiển thị 4x/1x từ mạng tại màn Spin
                java.io.File localFile = com.vn.jet.mosco.utils.CardAssetManager.getLocalFile(requireContext(), url);
                com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> thumbRequest = null;
                if (localFile != null && localFile.exists()) {
                    thumbRequest = Glide.with(this).load(localFile);
                }

                Glide.with(this)
                        .load(url)
                        .thumbnail(thumbRequest)
                        .priority(Priority.IMMEDIATE)
                        .placeholder(R.drawable.objet_back_spin)
                        // LOẠI BỎ CROSSFADE ĐỂ HIỆN NGAY LẬP TỨC (TRÁNH KHỰNG KHI XOAY 3D)
                        .dontAnimate()
                        .into(imageView);
            }
        } catch (Exception e) {
            android.util.Log.e("SpinFragment", "Error loading card image: " + e.getMessage());
        }
    }

    private void showFinalResultWithNeonEffect() {
        if (!isAdded() || layoutResultReveal == null) return;

        // 🚀 TỐI ƯU JANK: Chuẩn bị RecyclerView ngầm trên GPU trước khi người dùng bấm Reveal
        prepareRevealResultGridOffscreen();

        // Kết thúc warmup: hiện thị hoàn toàn
        layoutResultReveal.setAlpha(1.0f);

        // Ẩn title/subtitle ban đầu để chuẩn bị fade-in sau
        if (tvResultTitle != null) tvResultTitle.setAlpha(0f);
        if (tvResultSubtitle != null) tvResultSubtitle.setAlpha(0f);

        // Setup card: hiện ngay, scale 0.8
        cardResultFinal.setAlpha(1f);
        cardResultFinal.setScaleX(0.8f);
        cardResultFinal.setScaleY(0.8f);
        cardResultFinal.setRotationY(0f);

        // Neon Glow: khởi tạo cùng scale, alpha 1.0 đè lên card (Chỉ khi Thắng)
        boolean isWin = currentSpinResult != null && currentSpinResult.isWin();
        if (isWin) {
            viewNeonGlow.setScaleX(0.8f);
            viewNeonGlow.setScaleY(0.8f);
            viewNeonGlow.setAlpha(1.0f);
            viewNeonGlow.setVisibility(View.VISIBLE);
            ivResultImage.clearColorFilter();
            ivResultBack.clearColorFilter();
        } else {
            viewNeonGlow.setVisibility(View.GONE);
            // Áp dụng Grayscale cho ảnh thẻ nếu thua
            android.graphics.ColorMatrix matrix = new android.graphics.ColorMatrix();
            matrix.setSaturation(0);
            android.graphics.ColorMatrixColorFilter filter = new android.graphics.ColorMatrixColorFilter(matrix);
            ivResultImage.setColorFilter(filter);
            ivResultBack.setColorFilter(filter);
            stopShineEffectLoop();
        }

        // Camera distance cho 3D rotation đẹp
        float scale = getResources().getDisplayMetrics().density;
        cardResultFinal.setCameraDistance(8000 * scale);

        // Hiện mặt trước, ẩn mặt sau
        ivResultImage.setVisibility(View.VISIBLE);
        ivResultImage.setScaleX(1f);
        ivResultBack.setVisibility(View.GONE);
        ivResultBack.setScaleX(-1f); // Lật ngang để bù mirror khi rotationY 180°

        // Load ảnh (đã được pre-load nên sẽ hiện tức thì)
        String frontUrl = getCardImageUrl(selectedPosition, true);
        loadCardImageInto(frontUrl, ivResultImage);
        String backUrl = getCardImageUrl(selectedPosition, false);
        loadCardImageInto(backUrl, ivResultBack);

        // === Phase 1: Neon Glow Fade 100% → 0% trong 1000ms ===
        if (isWin) {
            viewNeonGlow.animate()
                    .alpha(0f)
                    .setDuration(1000)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            viewNeonGlow.setVisibility(View.GONE);
                            startRevealZoomAnimation();
                        }
                    })
                    .start();
        } else {
            startRevealZoomAnimation();
        }
    }

    private void startRevealZoomAnimation() {
        if (!isAdded()) return;
        // Dừng 100ms trước Phase 2 (Theo nguồn cũ)
        cardResultFinal.postDelayed(() -> {
            if (!isAdded()) return;
            // === Phase 2: Zoom In → scale 1.4 trong 700ms ===
            cardResultFinal.animate()
                    .scaleX(1.4f)
                    .scaleY(1.4f)
                    .setDuration(700)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            // === Phase 3: Quick Zoom Out → scale 1.2 trong 200ms ===
                            cardResultFinal.animate()
                                    .scaleX(1.2f)
                                    .scaleY(1.2f)
                                    .setDuration(200)
                                    .setInterpolator(new AccelerateDecelerateInterpolator())
                                    .setListener(new AnimatorListenerAdapter() {
                                        @Override
                                        public void onAnimationEnd(Animator animation) {
                                            // Dừng 300ms trước Phase 4
                                            cardResultFinal.postDelayed(() -> {
                                                if (!isAdded()) return;
                                                startResultRotation();
                                            }, 300);
                                        }
                                    })
                                    .start();
                        }
                    })
                    .start();
        }, 100);
    }

    private void startResultRotation() {
        // === Phase 4.1: Rotation 0° to 180° ===
        ValueAnimator rot1 = ValueAnimator.ofFloat(0f, 180f);
        rot1.setDuration(750);
        rot1.setInterpolator(new AccelerateDecelerateInterpolator());
        final boolean[] showingBack = {false};
        rot1.addUpdateListener(anim -> {
            float val = (float) anim.getAnimatedValue();
            cardResultFinal.setRotationY(val);
            if (!showingBack[0] && val >= 90f) {
                showingBack[0] = true;
                ivResultImage.setVisibility(View.GONE);
                ivResultBack.setVisibility(View.VISIBLE);
            }
        });
        rot1.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // PAUSE 500ms at 180° (Mặt sau)
                cardResultFinal.postDelayed(() -> {
                    if (!isAdded()) return;
                    // === Phase 4.2: Rotation 180° to 360° ===
                    ValueAnimator rot2 = ValueAnimator.ofFloat(180f, 360f);
                    rot2.setDuration(750);
                    rot2.setInterpolator(new AccelerateDecelerateInterpolator());
                    rot2.addUpdateListener(anim2 -> {
                        float val = (float) anim2.getAnimatedValue();
                        cardResultFinal.setRotationY(val);
                        if (showingBack[0] && val >= 270f) {
                            showingBack[0] = false;
                            ivResultBack.setVisibility(View.GONE);
                            ivResultImage.setVisibility(View.VISIBLE);
                        }
                    });
                    rot2.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            cardResultFinal.setRotationY(0f);

                            // Show frame glowing effect (Theo nguồn cũ)
                            boolean isWin = currentSpinResult != null && currentSpinResult.isWin();
                            if (isWin && getView() != null) {
                                ImageView frameSpin = getView().findViewById(R.id.iv_frame_result_spin);
                                if (frameSpin != null) {
                                    frameSpin.setVisibility(View.VISIBLE);
                                    frameSpin.setAlpha(0f);
                                    frameSpin.setScaleX(0.95f);
                                    frameSpin.setScaleY(0.95f);
                                    frameSpin.animate()
                                            .alpha(1f)
                                            .scaleX(1.0f)
                                            .scaleY(1.0f)
                                            .setDuration(300)
                                            .setInterpolator(new AccelerateDecelerateInterpolator())
                                            .start();
                                }
                            }
                            // === Phase 5: Final Zoom Reset ===
                            cardResultFinal.animate()
                                    .scaleX(1.0f)
                                    .scaleY(1.0f)
                                    .setDuration(400)
                                    .setInterpolator(new AccelerateDecelerateInterpolator())
                                    .setListener(new AnimatorListenerAdapter() {
                                        @Override
                                        public void onAnimationEnd(Animator animation) {
                                            showResultInfoText();
                                        }
                                    })
                                    .start();
                        }
                    });
                    rot2.start();
                }, 500);
            }
        });
        rot1.start();
    }

    private void showResultInfoText() {
        boolean isWin = currentSpinResult != null && currentSpinResult.isWin();

        // Lấy tên thẻ bài từ dữ liệu thẻ (collectionId)
        String cardName = getString(R.string.spin_msg_nothing);
        if (currentSpinResult != null && currentSpinResult.getCardData() != null) {
            Object cid = currentSpinResult.getCardData().get("collectionId");
            if (cid != null) cardName = String.valueOf(cid);
        }

        // Fade-in title: Trạng thái (Thắng/Thua)
        if (tvResultTitle != null) {
            tvResultTitle.setText(isWin ? getString(R.string.spin_msg_success) : getString(R.string.spin_msg_failed));
            tvResultTitle.setTextColor(isWin ? Color.WHITE : ContextCompat.getColor(requireContext(), R.color.mosco_text_disabled));
            tvResultTitle.animate()
                    .alpha(1f)
                    .setDuration(400)
                    .start();
        }
        // Fade-in subtitle: Tên thẻ bài (Phần thưởng)
        if (tvResultSubtitle != null) {
            String subMsg = isWin
                    ? "You received: " + cardName
                    : "Unfortunately, you received: " + cardName + " (Trash)";
            tvResultSubtitle.setText(subMsg);
            tvResultSubtitle.setTextColor(isWin ? ContextCompat.getColor(requireContext(), R.color.palette_gray_300) : ContextCompat.getColor(requireContext(), R.color.mosco_text_dim));
            tvResultSubtitle.animate()
                    .alpha(1f)
                    .setDuration(400)
                    .setStartDelay(100)
                    .start();
        }
        // Fade-in "Reveal unchosen Objekts"
        if (tvRevealUnchosen != null) {
            tvRevealUnchosen.setVisibility(View.VISIBLE);
            tvRevealUnchosen.animate()
                    .alpha(1f)
                    .setDuration(350)
                    .setStartDelay(200)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        }
        // Fade-in nút Done
        if (btnCollect != null) {
            btnCollect.setVisibility(View.VISIBLE);
            btnCollect.animate()
                    .alpha(1f)
                    .setDuration(350)
                    .setStartDelay(200)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        }
    }

    // ============================================================
    // Tối ưu Jank: Vẽ trước 16 thẻ trên GPU ngầm
    // ============================================================
    private void prepareRevealResultGridOffscreen() {
        if (!isAdded() || rvRevealResultGrid == null) return;

        // Đặt layout thành INVISIBLE để nó được layout và vẽ ngầm trên GPU
        layoutRevealResultGrid.setVisibility(View.INVISIBLE);

        rvRevealResultGrid.setLayoutManager(new GridLayoutManager(requireContext(), 4));

        rvRevealResultGrid.post(() -> {
            if (!isAdded()) return;

            float density = getResources().getDisplayMetrics().density;
            int itemMarginPx = (int) (4 * density);

            int availableWidth = rvRevealResultGrid.getWidth();
            if (availableWidth <= 0) return;

            int totalHorizontalMargins = itemMarginPx * 2 * 4;
            int itemWidth = (availableWidth - totalHorizontalMargins) / 4;
            revealCardHeight = (int) (itemWidth * 1.54f);

            revealResultAdapter = new RevealResultAdapter(selectedPosition);
            rvRevealResultGrid.setAdapter(revealResultAdapter);
        });
    }

    // ============================================================
    // Reveal Result Grid — hiện khi ấn "Reveal unchosen Objekts >"
    // ============================================================
    private void showRevealResultGrid() {
        if (!isAdded()) return;

        // Ẩn result, hiện reveal grid (đã được vẽ sẵn)
        layoutResultReveal.setVisibility(View.GONE);
        layoutRevealResultGrid.setVisibility(View.VISIBLE);

        // Sau 0.6s, flip các thẻ còn lại
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isAdded() && revealResultAdapter != null) {
                revealResultAdapter.revealAllCards();
            }
        }, 600);
    }

    private void resetToSpinMain() {
        if (!isAdded()) return;
        // Ẩn result
        layoutResultReveal.setVisibility(View.GONE);
        // Ẩn reveal result grid
        if (layoutRevealResultGrid != null) layoutRevealResultGrid.setVisibility(View.GONE);
        // Reset btn_collect
        if (btnCollect != null) {
            btnCollect.setAlpha(0f);
            btnCollect.setVisibility(View.INVISIBLE);
        }
        // Reset reveal unchosen
        if (tvRevealUnchosen != null) {
            tvRevealUnchosen.setAlpha(0f);
            tvRevealUnchosen.setVisibility(View.INVISIBLE);
        }
        // Reset title/subtitle
        if (tvResultTitle != null) {
            tvResultTitle.animate().cancel();
            tvResultTitle.setAlpha(0f);
        }
        if (tvResultSubtitle != null) {
            tvResultSubtitle.animate().cancel();
            tvResultSubtitle.setAlpha(0f);
        }
        // Reset objet: rotation, scale, back image
        cardResultFinal.setScaleX(1f);
        cardResultFinal.setScaleY(1f);
        cardResultFinal.setRotationY(0f);
        if (ivResultBack != null) {
            ivResultBack.setVisibility(View.GONE);
        }
        if (ivResultImage != null) {
            ivResultImage.setVisibility(View.VISIBLE);
        }
        viewNeonGlow.setAlpha(0f);
        viewNeonGlow.setVisibility(View.GONE);

        if (getView() != null) {
            ImageView frameSpin = getView().findViewById(R.id.iv_frame_result_spin);
            if (frameSpin != null) {
                frameSpin.setVisibility(View.GONE);
                frameSpin.setAlpha(0f);
            }
        }

        stopShineEffectLoop(); // Reset shine loop

        // Reset trạng thái chọn thẻ
        selectedPosition = -1;
        secretCardHeight = 0;
        secretAdapter = null;
        revealResultAdapter = null;
        revealCardHeight = 0;
        if (rvSecretGrid != null) rvSecretGrid.setAdapter(null);
        if (rvRevealResultGrid != null) rvRevealResultGrid.setAdapter(null);
        // Reset nút Confirm
        if (btnConfirmSelect != null) {
            btnConfirmSelect.setEnabled(false);
            ViewCompat.setBackgroundTintList(btnConfirmSelect, ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.mosco_btn_disabled)));
            btnConfirmSelect.setTextColor(ContextCompat.getColor(requireContext(), R.color.mosco_text_disabled));
        }
        // Hiện lại màn Spin chính
        layoutSpinMain.setVisibility(View.VISIBLE);
        toggleBottomNavigation(true);

        // Reset slot nguyên liệu spin
        if (layoutSelectedFront != null) layoutSelectedFront.setVisibility(View.GONE);
        if (ivSelectedObjet != null) {
            ivSelectedObjet.setVisibility(View.GONE);
            ivSelectedObjet.setImageDrawable(null);
        }
        if (cardCenterSlot != null) {
            View shimmer = cardCenterSlot.findViewById(R.id.view_card_shimmer);
            com.vn.jet.mosco.utils.CardEffectHelper.remove(cardCenterSlot, shimmer);
        }
        // Reset trạng thái Flip 3D thẻ hi sinh
        resetSacrificeFlip();
        if (btnAddObjet != null) {
            btnAddObjet.setVisibility(View.VISIBLE);
        }
        if (btnSpin != null) {
            btnSpin.setEnabled(false);
            ViewCompat.setBackgroundTintList(btnSpin, ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.mosco_btn_disabled)));
            btnSpin.setTextColor(ContextCompat.getColor(requireContext(), R.color.mosco_text_disabled));
        }
    }

    private void showSpinInfoDialog() {
        if (getContext() == null) return;
        new androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.GalacticDialogTheme)
            .setTitle(R.string.spin_info_title)
            .setMessage(R.string.spin_info_msg)
            .setPositiveButton(R.string.action_confirm, null)
            .show();
    }

    // ========================
    // Shine Effect Logic
    // ========================
    private final Handler shineHandler = new Handler(Looper.getMainLooper());
    private final java.util.Random shineRandom = new java.util.Random();
    private boolean isShineActive = false;

    private void startShineEffectLoop() {
        if (isShineActive) return;
        isShineActive = true;
        // The loop triggers randomly. We set the first trigger fairly soon.
        shineHandler.postDelayed(shineRunnable, 1000);
    }

    private void stopShineEffectLoop() {
        isShineActive = false;
        shineHandler.removeCallbacks(shineRunnable);
    }

    private final Runnable shineRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isShineActive || !isAdded()) return;

            // Trigger for Secret Grid
            if (secretAdapter != null && rvSecretGrid != null && layoutRevealGrid.getVisibility() == View.VISIBLE) {
                int count = 1 + shineRandom.nextInt(2);
                for(int i = 0; i < count; i++) {
                    int randomPos = shineRandom.nextInt(gridSessionCards.size());
                    RecyclerView.ViewHolder vh = rvSecretGrid.findViewHolderForAdapterPosition(randomPos);
                    if (vh instanceof SecretCardAdapter.ViewHolder) {
                        ((SecretCardAdapter.ViewHolder) vh).playShineAnimation(600 + shineRandom.nextInt(401));
                    }
                }
            }

            // Trigger for Reveal Result Grid
            if (revealResultAdapter != null && rvRevealResultGrid != null && layoutRevealResultGrid.getVisibility() == View.VISIBLE) {
                int count = 1 + shineRandom.nextInt(2);
                for(int i = 0; i < count; i++) {
                    int randomPos = shineRandom.nextInt(gridSessionCards.size());
                    RecyclerView.ViewHolder vh = rvRevealResultGrid.findViewHolderForAdapterPosition(randomPos);
                    if (vh instanceof RevealResultAdapter.ViewHolder) {
                        ((RevealResultAdapter.ViewHolder) vh).playShineAnimation(3500); // 3.5s speed like ObjetDetailBinder
                    }
                }
            }

            long delayMs = 1000 + shineRandom.nextInt(1000);
            shineHandler.postDelayed(this, delayMs);
        }
    };

    // ========================
    // Sparkling Dust Effect
    // ========================
    private void startDustEffect() {
        if (layoutDustContainer == null) return;
        
        int particleCount = 25;
        java.util.Random random = new java.util.Random();
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;

        for (int i = 0; i < particleCount; i++) {
            ImageView particle = new ImageView(requireContext());
            int size = (int) (random.nextFloat() * 4 + 2) * (int) getResources().getDisplayMetrics().density;
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
            particle.setLayoutParams(lp);
            particle.setBackgroundResource(R.drawable.bg_circle_white);
            particle.setAlpha(random.nextFloat() * 0.4f + 0.1f);
            
            layoutDustContainer.addView(particle);

            // Random start position
            particle.setTranslationX(random.nextInt(screenWidth));
            particle.setTranslationY(random.nextInt(screenHeight));

            animateParticle(particle, random, screenWidth, screenHeight);
        }
    }

    private void animateParticle(View particle, java.util.Random random, int sw, int sh) {
        long duration = 4000 + random.nextInt(6000);
        float targetX = random.nextInt(sw);
        float targetY = random.nextInt(sh);

        particle.animate()
                .translationX(targetX)
                .translationY(targetY)
                .alpha(random.nextFloat() * 0.5f + 0.1f)
                .setDuration(duration)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (isAdded() && layoutDustContainer != null) {
                            animateParticle(particle, random, sw, sh);
                        }
                    }
                })
                .start();

        // Thêm hiệu ứng nhấp nháy (Twinkle)
        ObjectAnimator twinkle = ObjectAnimator.ofFloat(particle, "scaleX", 0.5f, 1.2f, 0.5f);
        twinkle.setDuration(2000 + random.nextInt(3000));
        twinkle.setRepeatCount(ValueAnimator.INFINITE);
        twinkle.setRepeatMode(ValueAnimator.REVERSE);
        twinkle.start();
        
        ObjectAnimator twinkleY = ObjectAnimator.ofFloat(particle, "scaleY", 0.5f, 1.2f, 0.5f);
        twinkleY.setDuration(twinkle.getDuration());
        twinkleY.setRepeatCount(ValueAnimator.INFINITE);
        twinkleY.setRepeatMode(ValueAnimator.REVERSE);
        twinkleY.start();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopShineEffectLoop();
    }

    // ========================
    // SecretCardAdapter (chọn thẻ ban đầu)
    // ========================
    private class SecretCardAdapter extends RecyclerView.Adapter<SecretCardAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_secret_card, parent, false);
            if (secretCardHeight > 0) {
                ViewGroup.LayoutParams params = v.getLayoutParams();
                params.height = secretCardHeight;
                v.setLayoutParams(params);
            }
            return new ViewHolder(v);
        }
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            float density = holder.itemView.getContext().getResources().getDisplayMetrics().density;
            int strokeWidth = selectedPosition == position ? (int) (3 * density) : 0;
            holder.cardRoot.setStrokeWidth(strokeWidth);

            if (isAdded()) {
                // 🚀 TỐI ƯU CỰC ĐẠI: Card Back là ảnh tĩnh, không được dùng Glide để tránh Jank khi hiện 16 thẻ cùng lúc
                holder.ivCardBack.setImageResource(R.drawable.objet_back_spin);

                // Set the shimmer background for metallic shine
                // Smoother gradient for a "blurred/dreamy" effect
                GradientDrawable shimmerBg = new GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        new int[]{0x00FFFFFF, 0x02FFFFFF, 0x0AFFFFFF, 0x22FFFFFF, 0x44FFFFFF, 0x22FFFFFF, 0x0AFFFFFF, 0x02FFFFFF, 0x00FFFFFF}
                );
                holder.viewShine.setBackground(shimmerBg);
                holder.viewShine.setRotation(130f); // Nghiêng 50 độ
                holder.viewShine.setScaleY(3.0f); // Tăng tỉ lệ để phủ kín khi nghiêng 50 độ
            }
            holder.ivCardBack.setVisibility(View.VISIBLE);
            holder.ivCardFront.setVisibility(View.INVISIBLE);

            holder.itemView.setOnClickListener(v -> {
                int oldPos = selectedPosition;
                selectedPosition = holder.getBindingAdapterPosition();
                if (selectedPosition != RecyclerView.NO_POSITION) {
                    if (oldPos != -1) notifyItemChanged(oldPos);
                    notifyItemChanged(selectedPosition);
                    if (btnConfirmSelect != null) {
                        btnConfirmSelect.setEnabled(true);
                        ViewCompat.setBackgroundTintList(btnConfirmSelect, ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.mosco_card_stroke)));
                        btnConfirmSelect.setTextColor(Color.WHITE);
                    }
                }
            });
        }
        @Override
        public int getItemCount() { return gridSessionCards.size(); }
        class ViewHolder extends RecyclerView.ViewHolder {
            MaterialCardView cardRoot;
            ImageView ivCardBack, ivCardFront;
            View viewShine;

            ViewHolder(View v) {
                super(v);
                cardRoot = v.findViewById(R.id.card_root);
                ivCardBack = v.findViewById(R.id.iv_card_back);
                ivCardFront = v.findViewById(R.id.iv_card_front);
                viewShine = v.findViewById(R.id.view_metallic_shine);
            }

            void playShineAnimation(long duration) {
                if (viewShine == null || cardRoot == null) return;
                int cw = cardRoot.getWidth();
                if (cw == 0) return;

                viewShine.setVisibility(View.VISIBLE);
                viewShine.setAlpha(1.0f);

                // Start from 2.5f width to -2.5f width to cover diagonal sweep
                ValueAnimator shimmerAnim = ValueAnimator.ofFloat(2.5f, -2.5f);
                shimmerAnim.setDuration(duration);
                shimmerAnim.setInterpolator(new android.view.animation.LinearInterpolator());
                shimmerAnim.addUpdateListener(animation -> {
                    float fraction = (float) animation.getAnimatedValue();
                    viewShine.setTranslationX(cw * fraction);
                });
                shimmerAnim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        viewShine.setVisibility(View.INVISIBLE);
                    }
                });
                shimmerAnim.start();
            }
        }
    }

    // ========================
    // RevealResultAdapter (hiện kết quả 16 thẻ)
    // ========================
    private class RevealResultAdapter extends RecyclerView.Adapter<RevealResultAdapter.ViewHolder> {
        private final int winPosition;
        private boolean revealed = false;
        private final List<ViewHolder> holders = new ArrayList<>();

        RevealResultAdapter(int winPosition) {
            this.winPosition = winPosition;
        }

        void revealAllCards() {
            revealed = true;
            for (ViewHolder holder : holders) {
                int pos = holder.getBindingAdapterPosition();
                if (pos != winPosition && pos != RecyclerView.NO_POSITION) {
                    flipCardToFront(holder, pos);
                }
            }
        }

        private void flipCardToFront(ViewHolder holder, int position) {
            // Animation lật thẻ: scale X từ 1 → 0, đổi ảnh, rồi 0 → 1
            holder.itemView.animate()
                    .scaleX(0f)
                    .setDuration(200)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            // Đổi sang mặt trước
                            holder.ivCardBack.setVisibility(View.GONE);
                            holder.ivCardFront.setVisibility(View.VISIBLE);

                            // Scale X từ 0 → 1
                            holder.itemView.animate()
                                    .scaleX(1f)
                                    .setDuration(200)
                                    .setInterpolator(new AccelerateDecelerateInterpolator())
                                    .setListener(null)
                                    .start();
                        }
                    })
                    .start();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_secret_card, parent, false);
            if (revealCardHeight > 0) {
                ViewGroup.LayoutParams params = v.getLayoutParams();
                params.height = revealCardHeight;
                v.setLayoutParams(params);
            }
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holders.add(holder);
            holder.cardRoot.setStrokeWidth(0);

            if (isAdded()) {
                // 🚀 TỐI ƯU: Nạp trực tiếp mặt sau thẻ tĩnh
                holder.ivCardBack.setImageResource(R.drawable.objet_back_spin);

                String frontUrl = getCardImageUrl(position, true);
                loadCardImageInto(frontUrl, holder.ivCardFront);

                // Set the shimmer background for metallic shine
                // Smoother gradient for a "blurred/dreamy" effect
                GradientDrawable shimmerBg = new GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        new int[]{0x00FFFFFF, 0x02FFFFFF, 0x0AFFFFFF, 0x22FFFFFF, 0x44FFFFFF, 0x22FFFFFF, 0x0AFFFFFF, 0x02FFFFFF, 0x00FFFFFF}
                );
                holder.viewShine.setBackground(shimmerBg);
                holder.viewShine.setRotation(-50f); // Nghiêng -50 độ
                holder.viewShine.setScaleY(3.0f); // Tăng tỉ lệ để phủ kín khi nghiêng -50 độ
            }

            if (position == winPosition) {
                // Thẻ đã chọn — hiện mặt trước + badge "Get"
                holder.ivCardBack.setVisibility(View.GONE);
                holder.ivCardFront.setVisibility(View.VISIBLE);

                float density = holder.itemView.getContext().getResources().getDisplayMetrics().density;
                holder.cardRoot.setStrokeWidth((int) (3 * density));

                // Thêm badge "Get" lên trên
                holder.addGetBadge();
            } else {
                // Các thẻ khác — hiện mặt sau (card back)
                holder.ivCardBack.setVisibility(View.VISIBLE);
                holder.ivCardFront.setVisibility(View.INVISIBLE);
            }

            // Disable click trong reveal grid
            holder.itemView.setOnClickListener(null);
            holder.itemView.setClickable(false);
        }

        @Override
        public int getItemCount() { return gridSessionCards.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            MaterialCardView cardRoot;
            ImageView ivCardBack, ivCardFront;
            TextView tvGetBadge;
            View viewShine;

            ViewHolder(View v) {
                super(v);
                cardRoot = v.findViewById(R.id.card_root);
                ivCardBack = v.findViewById(R.id.iv_card_back);
                ivCardFront = v.findViewById(R.id.iv_card_front);
                viewShine = v.findViewById(R.id.view_metallic_shine);
            }

            void playShineAnimation(long duration) {
                if (viewShine == null || cardRoot == null) return;
                int cw = cardRoot.getWidth();
                if (cw == 0) return;

                viewShine.setVisibility(View.VISIBLE);
                viewShine.setAlpha(1.0f);

                // Start from -2.5f width to 2.5f width to cover diagonal sweep
                ValueAnimator shimmerAnim = ValueAnimator.ofFloat(-2.5f, 2.5f);
                shimmerAnim.setDuration(duration);
                shimmerAnim.setInterpolator(new android.view.animation.LinearInterpolator());
                shimmerAnim.addUpdateListener(animation -> {
                    float fraction = (float) animation.getAnimatedValue();
                    viewShine.setTranslationX(cw * fraction);
                });
                shimmerAnim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        viewShine.setVisibility(View.INVISIBLE);
                    }
                });
                shimmerAnim.start();
            }

            void addGetBadge() {
                if (tvGetBadge != null) return; // Đã thêm rồi

                View parentView = (View) ivCardFront.getParent();
                if (!(parentView instanceof ViewGroup)) return;

                ViewGroup parent = (ViewGroup) parentView;
                tvGetBadge = new TextView(parent.getContext());
                tvGetBadge.setText("Get");
                tvGetBadge.setTextColor(Color.WHITE);
                tvGetBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                tvGetBadge.setTypeface(null, Typeface.BOLD);
                tvGetBadge.setGravity(Gravity.CENTER);
                tvGetBadge.setBackgroundResource(R.drawable.bg_badge_get);

                float density = parent.getResources().getDisplayMetrics().density;
                int paddingH = (int) (12 * density);
                int paddingV = (int) (4 * density);
                tvGetBadge.setPadding(paddingH, paddingV, paddingH, paddingV);

                // Use the correct LayoutParams for ConstraintLayout (the actual parent)
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp = new androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
                        androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT,
                        androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT);
                lp.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
                lp.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
                lp.topMargin = (int) (8 * density);
                lp.leftMargin = (int) (8 * density);

                parent.addView(tvGetBadge, lp);
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  3D FLIP CHO THẺ HI SINH — Interactive Drag-to-Flip (giống Showcase)
    // ════════════════════════════════════════════════════════════════

    /**
     * Khởi tạo cơ chế lật thẻ vật lý:
     * - Single Tap → mở InventoryBottomSheet (chọn thẻ mới)
     * - Kéo ngang → xoay thẻ real-time theo ngón tay
     * - Thả tay → snap về góc gần nhất (0° hoặc 180°) với Elastic Spring
     */
    private void initSacrificeFlipGesture() {
        // GestureDetector chỉ dùng cho Single Tap (chọn thẻ)
        GestureDetector.SimpleOnGestureListener gestureListener =
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapConfirmed(MotionEvent e) {
                        // Tap → mở chọn thẻ hi sinh mới bằng InventoryBottomSheet chung
                        InventoryBottomSheet bottomSheet = new InventoryBottomSheet();
                        bottomSheet.setOnObjetSelectedListener(selectedObj -> {
                            selectedSacrificeId = selectedObj.getIdString();
                            updateSelectedObjetUI(selectedObj);
                        });
                        bottomSheet.show(getParentFragmentManager(), "SelectObjet");
                        return true;
                    }

                    @Override
                    public boolean onDown(MotionEvent e) {
                        return true;
                    }
                };

        sacrificeGestureDetector = new GestureDetectorCompat(requireContext(), gestureListener);

        if (cardCenterSlot != null) {
            // Camera distance cho 3D rotation mượt
            float scale = getResources().getDisplayMetrics().density;
            cardCenterSlot.setCameraDistance(8000 * scale);

            // Đọc cấu hình độ nhạy xoay từ resource (không hardcode)
            final int flipSensitivity = getResources().getInteger(R.integer.card_flip_sensitivity);
            final float[] initialTouchX = {0f};
            final float[] startRotation = {0f};
            final ObjectAnimator[] snapAnim = {null};

            cardCenterSlot.setOnTouchListener((v, event) -> {
                // Relay sự kiện cho GestureDetector (Single Tap)
                sacrificeGestureDetector.onTouchEvent(event);

                // Chỉ xử lý flip khi đã có thẻ hiển thị
                if (layoutSelectedFront == null || layoutSelectedFront.getVisibility() != View.VISIBLE) {
                    return true;
                }

                View pseudoGlow = (View) cardCenterSlot.getTag(R.id.view_progress_fill);
                if (pseudoGlow != null) pseudoGlow.setCameraDistance(8000 * scale);

                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        if (snapAnim[0] != null && snapAnim[0].isRunning()) {
                            snapAnim[0].cancel();
                        }
                        initialTouchX[0] = event.getRawX();
                        startRotation[0] = cardCenterSlot.getRotationY();
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float diffX = event.getRawX() - initialTouchX[0];
                        float newRotation = startRotation[0] + (diffX / flipSensitivity);
                        cardCenterSlot.setRotationY(newRotation);
                        if (pseudoGlow != null) pseudoGlow.setRotationY(newRotation);

                        // Tính toán mặt hiện tại dựa trên góc xoay
                        float normalized = newRotation % 360;
                        if (normalized < 0) normalized += 360;
                        boolean shouldShowBack = (normalized > 90 && normalized < 270);

                        if (shouldShowBack != isSacrificeFlipped) {
                            setSacrificeFace(shouldShowBack);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.getParent().requestDisallowInterceptTouchEvent(false);
                        // Snap về góc gần nhất (0° hoặc 180°)
                        float curRot = cardCenterSlot.getRotationY();
                        float norm = curRot % 360;
                        if (norm < 0) norm += 360;

                        float nearestAngle;
                        if (norm <= 90 || norm >= 270) {
                            nearestAngle = Math.round(curRot / 360f) * 360f;
                        } else {
                            nearestAngle = Math.round((curRot - 180f) / 360f) * 360f + 180f;
                        }

                        snapAnim[0] = ObjectAnimator.ofFloat(cardCenterSlot, "rotationY", curRot, nearestAngle);
                        snapAnim[0].setDuration(250);
                        snapAnim[0].setInterpolator(new android.view.animation.OvershootInterpolator(1.2f));
                        if (pseudoGlow != null) {
                            snapAnim[0].addUpdateListener(animation ->
                                    pseudoGlow.setRotationY((float) animation.getAnimatedValue()));
                        }
                        snapAnim[0].addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                float finalNorm = cardCenterSlot.getRotationY() % 360;
                                if (finalNorm < 0) finalNorm += 360;
                                boolean finalBack = (finalNorm > 90 && finalNorm < 270);
                                if (finalBack != isSacrificeFlipped) {
                                    setSacrificeFace(finalBack);
                                }
                            }
                        });
                        snapAnim[0].start();
                        return true;
                }
                return true;
            });
        }
    }

    /**
     * Thiết lập mặt hiển thị (trước/sau) dựa trên trạng thái.
     * Không toggle — gán trực tiếp trạng thái mới.
     */
    private void setSacrificeFace(boolean showBack) {
        if (isSacrificeFlipped == showBack) return;
        if (ivSelectedObjet == null || ivSacrificeBack == null) return;

        isSacrificeFlipped = showBack;

        View shimmer = cardCenterSlot.findViewById(R.id.view_card_shimmer);
        android.widget.TextView tvOvr = cardCenterSlot.findViewById(R.id.card_tv_ovr);
        android.widget.ImageView ivLevelBadge = cardCenterSlot.findViewById(R.id.card_iv_level);

        if (!showBack) {
            // Hiện mặt trước
            ivSelectedObjet.setVisibility(View.VISIBLE);
            ivSacrificeBack.setVisibility(View.GONE);
            if (shimmer != null) shimmer.setVisibility(View.VISIBLE);
            if (tvOvr != null) tvOvr.setVisibility(View.GONE);
            if (ivLevelBadge != null && ivLevelBadge.getTag() != null) {
                ivLevelBadge.setVisibility(View.VISIBLE);
            }
        } else {
            // Hiện mặt sau
            ivSelectedObjet.setVisibility(View.GONE);
            ivSacrificeBack.setVisibility(View.VISIBLE);
            ivSacrificeBack.setScaleX(-1f); // Mirror fix cho rotation 180°
            ivSacrificeBack.setAlpha(1f);
            if (shimmer != null) shimmer.setVisibility(View.GONE);
            if (tvOvr != null) tvOvr.setVisibility(View.GONE);
            if (ivLevelBadge != null) ivLevelBadge.setVisibility(View.GONE);
        }
    }

    /**
     * Reset trạng thái flip về mặt trước (dùng khi đổi thẻ hoặc reset spin).
     */
    private void resetSacrificeFlip() {
        isSacrificeFlipped = false;
        isSacrificeFlipAnimating = false;
        sacrificeBackImageUrl = null;
        if (cardCenterSlot != null) cardCenterSlot.setRotationY(0f);
        if (ivSacrificeBack != null) {
            ivSacrificeBack.setVisibility(View.GONE);
            ivSacrificeBack.setScaleX(1f);
            ivSacrificeBack.setImageResource(R.drawable.objet_back_spin);
        }
        if (ivSelectedObjet != null) ivSelectedObjet.setVisibility(View.VISIBLE);
    }

    private void startBackgroundAnimation() {
        if (getView() == null || bgCardsContainer == null) return;
        
        if (masterBgAnimator != null) masterBgAnimator.cancel();
        bgCardsContainer.removeAllViews();
        bgCardStates.clear();

        int screenWidth = getResources().getDisplayMetrics().widthPixels;

        // Tải hằng số 1 lần duy nhất để tối ưu frame rate
        int numCards = getResources().getInteger(R.integer.spin_slideshow_card_count);
        long duration = getResources().getInteger(R.integer.spin_slideshow_duration_ms);
        int lightBase = getResources().getInteger(R.integer.spin_light_color_base);
        int lightRange = getResources().getInteger(R.integer.spin_light_color_range);
        float zMax = getResources().getDimension(R.dimen.spin_z_elevation_max);

        android.util.TypedValue beamRatioVal = new android.util.TypedValue();
        getResources().getValue(R.dimen.spin_beam_width_ratio, beamRatioVal, true);
        float beamWidth = screenWidth * beamRatioVal.getFloat();

        android.util.TypedValue scaleVal = new android.util.TypedValue();
        getResources().getValue(R.dimen.spin_card_base_scale, scaleVal, true);
        float cardBaseScale = scaleVal.getFloat();

        android.util.TypedValue widthRatioVal = new android.util.TypedValue();
        getResources().getValue(R.dimen.spin_card_width_ratio, widthRatioVal, true);
        float widthRatio = widthRatioVal.getFloat();

        for (int i = 0; i < numCards; i++) {
            ImageView iv = new ImageView(requireContext());
            iv.setImageResource(R.drawable.objet_back_spin);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            
            int cardW = (int) (screenWidth * widthRatio);
            int cardH = (int) (cardW * 1.54f);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(cardW, cardH);
            lp.gravity = Gravity.CENTER;
            iv.setLayoutParams(lp);
            iv.setAlpha(1.0f); 
            iv.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            
            bgCardsContainer.addView(iv);

            // Phân bổ thời gian bắt đầu đều nhau để tạo chuỗi slideshow lặp lại
            long startTimeOffset = (duration / numCards) * i;

            bgCardStates.add(new BackgroundCardState(iv, duration, startTimeOffset, 
                    beamWidth, cardBaseScale, zMax, lightBase, lightRange));
        }

        masterBgAnimator = ValueAnimator.ofFloat(0f, 1f);
        masterBgAnimator.setRepeatCount(ValueAnimator.INFINITE);
        masterBgAnimator.setDuration(1000);
        masterBgAnimator.addUpdateListener(animation -> {
            if (!isAdded()) return;
            long currentTime = System.currentTimeMillis();
            for (BackgroundCardState state : bgCardStates) {
                state.update(currentTime, screenWidth);
            }
        });
        masterBgAnimator.start();
    }

    private void animateOrbitalCard(View target, float rx, float ry, long duration, long initialPlayTime, float ox, float oy) {
        // Hàm này không còn dùng nữa vì đã gộp vào Master Animator
    }

    private void animateCard(View target, float startX, float endX, long duration, long initialPlayTime) {
        // Giữ lại hàm cũ để tránh lỗi compile nếu có chỗ khác gọi, nhưng logic orbital đã thay thế chính
        if (target == null) return;
        target.setVisibility(View.VISIBLE);
        ObjectAnimator animator = ObjectAnimator.ofFloat(target, "translationX", startX, endX);
        animator.setDuration(duration);
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.RESTART);
        animator.start();
        animator.setCurrentPlayTime(initialPlayTime);
    }

    private void updateSelectedObjetUI(com.vn.jet.mosco.model.Objet selectedObj) {
        String imageUrl = selectedObj.getImageUrl();
        if (btnAddObjet != null) btnAddObjet.setVisibility(View.GONE);
        View loader = getView() != null ? getView().findViewById(R.id.layout_spin_loading_skeleton) : null;

        // Reset trạng thái flip trước khi hiển thị thẻ mới
        resetSacrificeFlip();

        if (ivSelectedObjet != null) {
            ivSelectedObjet.setVisibility(View.GONE);
            if (layoutSelectedFront != null) layoutSelectedFront.setVisibility(View.GONE);

            if (loader != null) {
                loader.setVisibility(View.VISIBLE);
            }

            // ⏳ TRỄ 0.4s: Để load ảnh 4x và tạo cảm giác app đang "xác thực" thẻ
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (isAdded() && ivSelectedObjet != null) {
                    if (loader != null) {
                        loader.setVisibility(View.GONE);
                    }
                    if (layoutSelectedFront != null) layoutSelectedFront.setVisibility(View.VISIBLE);
                    ivSelectedObjet.setVisibility(View.VISIBLE);
                    ivSelectedObjet.setAlpha(0f);

                    Glide.with(this)
                            .load(imageUrl) // 4x nạp từ mạng
                            .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade(300))
                            .into(ivSelectedObjet);

                    ivSelectedObjet.animate().alpha(1f).setDuration(300).start();

                    // Áp dụng Full Hiệu Ứng (OVR, Level Badge, Shimmer)
                    android.widget.TextView tvOvr = cardCenterSlot.findViewById(R.id.card_tv_ovr);
                    if (tvOvr != null) {
                        tvOvr.setText(String.valueOf(selectedObj.getOvr()));
                        tvOvr.setVisibility(View.GONE);
                    }

                    android.widget.ImageView ivLevelBadge = cardCenterSlot.findViewById(R.id.card_iv_level);
                    if (ivLevelBadge != null) {
                        if (selectedObj.getUpgradeLevel() > 0) {
                            com.bumptech.glide.Glide.with(requireContext()).load("file:///android_asset/grade/" + selectedObj.getUpgradeLevel() + ".png").into(ivLevelBadge);
                            ivLevelBadge.setVisibility(View.VISIBLE);
                            ivLevelBadge.setTag("has_badge"); // Đánh dấu để swapSacrificeFaces biết nên hiện lại
                            com.vn.jet.mosco.utils.LevelBadgeEffectHelper.apply(ivLevelBadge, selectedObj.getUpgradeLevel());
                        } else {
                            ivLevelBadge.setVisibility(View.GONE);
                            ivLevelBadge.setTag(null);
                            com.vn.jet.mosco.utils.LevelBadgeEffectHelper.remove(ivLevelBadge);
                        }
                    }

                    View shimmer = cardCenterSlot.findViewById(R.id.view_card_shimmer);
                    if (shimmer != null) {
                        com.vn.jet.mosco.utils.CardEffectHelper.apply(cardCenterSlot, shimmer, selectedObj, true);
                    }

                    // 💎 Load ảnh mặt sau (backImage) từ database.json cho Flip 3D
                    if (requireContext() != null && selectedObj.getCollectionId() != null) {
                        org.json.JSONObject cardJson = com.vn.jet.mosco.utils.DatabaseLoader.findById(
                                requireContext(), selectedObj.getCollectionId());
                        if (cardJson != null) {
                            sacrificeBackImageUrl = cardJson.optString("backImage", "");
                            if (ivSacrificeBack != null && !sacrificeBackImageUrl.isEmpty()) {
                                java.io.File localBackThumb = com.vn.jet.mosco.utils.CardAssetManager.getLocalFile(
                                        requireContext(), sacrificeBackImageUrl);
                                com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> backThumb = null;
                                if (localBackThumb != null && localBackThumb.exists()) {
                                    backThumb = Glide.with(this).load(localBackThumb);
                                }
                                Glide.with(this)
                                        .load(sacrificeBackImageUrl)
                                        .thumbnail(backThumb)
                                        .placeholder(R.drawable.objet_back_spin)
                                        .error(R.drawable.objet_back_spin)
                                        .dontAnimate()
                                        .into(ivSacrificeBack);
                            }
                        }
                    }
                }
            }, 400);
        }

        if (btnSpin != null) {
            btnSpin.setEnabled(true);
            ViewCompat.setBackgroundTintList(btnSpin, ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.mosco_card_stroke)));
            btnSpin.setTextColor(Color.WHITE);
        }
    }
}
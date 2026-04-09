package com.vn.jet.mosco.fragment;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.model.ApiResponse;
import com.vn.jet.mosco.model.Objet;
import com.vn.jet.mosco.model.UpgradeRequest;
import com.vn.jet.mosco.model.UpgradeResponse;
import com.vn.jet.mosco.network.ApiClient;
import com.vn.jet.mosco.network.GameApiService;
import com.vn.jet.mosco.utils.CardEffectHelper;
import com.vn.jet.mosco.utils.DatabaseLoader;
import com.vn.jet.mosco.utils.LevelBadgeEffectHelper;
import com.vn.jet.mosco.utils.SessionManager;
import com.vn.jet.mosco.utils.UpgradeAlgorithm;
import com.vn.jet.mosco.view.SpriteSheetView;
import com.vn.jet.mosco.view.UpgradeSceneView;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * UpgradeFragment - Premium Galactic Edition (V2 - Fix Ghosting & White Card)
 * Chức năng rèn thẻ với hiệu ứng điện ảnh sấm sét, glitch và cháy nổ.
 * Đã tối ưu sequencing để tránh hiện tượng bóng ma và lóa sáng.
 */
public class UpgradeFragment extends Fragment {

    // Views
    private View rootView;
    private View layoutContentWrapper;
    private VideoView bgVideoView;
    private View frameMainCard;
    private FrameLayout btnAddMainCard;
    private ImageView ivMainCardImage;
    private View viewCardBg;
    private TextView tvCardOvr;
    private ImageView ivCardLevelBadge;

    private LinearLayout layoutRightStats;
    private TextView tvOvrAfter;
    private TextView tvOvrCurrentSmall;

    private LinearLayout layoutLevelIndicator;
    private ImageView ivLevelCurrent;
    private ImageView ivLevelNext;
    private TextView tvLevelCurrent;
    private TextView tvLevelNext;

    private View viewProgressFill;
    private TextView tvMaterialsCount;

    private View[] frameMaterials = new View[5];
    private ImageView[] ivMaterials = new ImageView[5];
    private TextView[] tvMaterialPlus = new TextView[5];
    private View[] viewMaterialBg = new View[5];
    private TextView[] tvMaterialOvr = new TextView[5];
    private ImageView[] ivMaterialLevel = new ImageView[5];

    private androidx.appcompat.widget.AppCompatButton btnUpgrade;

    // Data
    private Objet mainCard = null;
    private Objet[] materialCards = new Objet[5];
    private int currentMaterialSlot = -1;

    private UpgradeAlgorithm upgradeAlgorithm;

    public UpgradeFragment() {}

    public static UpgradeFragment newInstance() {
        return new UpgradeFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadUpgradeConfig();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_upgrade, container, false);
        bindViews(rootView);
        setupClickListeners();
        setupVideoBackground();
        updateUI();
        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        DatabaseLoader.registerInventoryChangeListener(inventoryChangeListener);
        if (bgVideoView != null && !bgVideoView.isPlaying()) {
            bgVideoView.start();
        }
        updateUI();
    }

    @Override
    public void onPause() {
        super.onPause();
        DatabaseLoader.unregisterInventoryChangeListener(inventoryChangeListener);
        if (bgVideoView != null && bgVideoView.isPlaying()) {
            bgVideoView.pause();
        }
    }

    public void setMainCard(Objet card) {
        this.mainCard = card;
        if (rootView != null) {
            updateUI();
        }
    }

    private void setupVideoBackground() {
        if (bgVideoView == null || getContext() == null) return;
        String path = "android.resource://" + getContext().getPackageName() + "/" + R.raw.thunderbackground;
        bgVideoView.setVideoURI(Uri.parse(path));
        bgVideoView.setOnPreparedListener(mp -> {
            mp.setLooping(true);
            float videoRatio = mp.getVideoWidth() / (float) mp.getVideoHeight();
            float screenRatio = bgVideoView.getWidth() / (float) bgVideoView.getHeight();
            float scale = videoRatio / screenRatio;
            if (scale >= 1f) {
                bgVideoView.setScaleX(scale);
            } else {
                bgVideoView.setScaleY(1f / scale);
            }
            bgVideoView.start();
        });
    }

    private final DatabaseLoader.OnInventoryChangeListener inventoryChangeListener = () -> {
        if (getActivity() != null && isAdded()) {
            getActivity().runOnUiThread(this::updateUI);
        }
    };

    private void loadUpgradeConfig() {
        Context ctx = getContext();
        if (ctx == null) return;
        Gson gson = new Gson();
        try {
            InputStream isRate = ctx.getAssets().open("upgradeRate.json");
            InputStreamReader readerRate = new InputStreamReader(isRate);
            Type rateType = new TypeToken<Map<String, Double>>() {}.getType();
            Map<String, Double> rawRates = gson.fromJson(readerRate, rateType);
            readerRate.close();

            Map<Integer, Double> upgradeRates = new HashMap<>();
            for (Map.Entry<String, Double> entry : rawRates.entrySet()) {
                upgradeRates.put(Integer.parseInt(entry.getKey()), entry.getValue());
            }

            InputStream isCustom = ctx.getAssets().open("customUpgrade.json");
            InputStreamReader readerCustom = new InputStreamReader(isCustom);
            JsonObject customJson = gson.fromJson(readerCustom, JsonObject.class);
            readerCustom.close();

            Map<Integer, Map<String, UpgradeAlgorithm.UpgradeConfig>> customUpgrades = new HashMap<>();
            for (Map.Entry<String, JsonElement> levelEntry : customJson.entrySet()) {
                int level = Integer.parseInt(levelEntry.getKey());
                JsonObject typeObj = levelEntry.getValue().getAsJsonObject();
                Map<String, UpgradeAlgorithm.UpgradeConfig> typeMap = new HashMap<>();
                for (Map.Entry<String, JsonElement> typeEntry : typeObj.entrySet()) {
                    UpgradeAlgorithm.UpgradeConfig config = gson.fromJson(typeEntry.getValue(), UpgradeAlgorithm.UpgradeConfig.class);
                    typeMap.put(typeEntry.getKey(), config);
                }
                customUpgrades.put(level, typeMap);
            }
            upgradeAlgorithm = new UpgradeAlgorithm(upgradeRates, customUpgrades);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void bindViews(View view) {
        layoutContentWrapper = view.findViewById(R.id.layout_content_wrapper);
        bgVideoView = view.findViewById(R.id.bg_video_view);
        frameMainCard = view.findViewById(R.id.frame_main_card);
        btnAddMainCard = view.findViewById(R.id.btn_add_main_card);
        ivMainCardImage = frameMainCard.findViewById(R.id.card_iv_image);
        viewCardBg = view.findViewById(R.id.view_card_bg);
        tvCardOvr = frameMainCard.findViewById(R.id.card_tv_ovr);
        ivCardLevelBadge = frameMainCard.findViewById(R.id.card_iv_level);

        layoutRightStats = view.findViewById(R.id.layout_right_stats);
        tvOvrAfter = view.findViewById(R.id.tv_ovr_after);
        tvOvrCurrentSmall = view.findViewById(R.id.tv_ovr_current_small);

        layoutLevelIndicator = view.findViewById(R.id.layout_level_indicator);
        ivLevelCurrent = view.findViewById(R.id.iv_level_current);
        ivLevelNext = view.findViewById(R.id.iv_level_next);
        tvLevelCurrent = view.findViewById(R.id.tv_level_current);
        tvLevelNext = view.findViewById(R.id.tv_level_next);

        viewProgressFill = view.findViewById(R.id.view_progress_fill);
        tvMaterialsCount = view.findViewById(R.id.tv_materials_count);
        btnUpgrade = view.findViewById(R.id.btn_upgrade);

        int[] materialFrameIds = {R.id.frame_material_1, R.id.frame_material_2, R.id.frame_material_3, R.id.frame_material_4, R.id.frame_material_5};
        int[] materialPlusIds = {R.id.tv_material_plus_1, R.id.tv_material_plus_2, R.id.tv_material_plus_3, R.id.tv_material_plus_4, R.id.tv_material_plus_5};
        int[] materialBgIds = {R.id.view_material_bg_1, R.id.view_material_bg_2, R.id.view_material_bg_3, R.id.view_material_bg_4, R.id.view_material_bg_5};

        for (int i = 0; i < 5; i++) {
            frameMaterials[i] = view.findViewById(materialFrameIds[i]);
            tvMaterialPlus[i] = view.findViewById(materialPlusIds[i]);
            viewMaterialBg[i] = view.findViewById(materialBgIds[i]);
            ivMaterials[i] = frameMaterials[i].findViewById(R.id.card_iv_image);
            tvMaterialOvr[i] = frameMaterials[i].findViewById(R.id.card_tv_ovr);
            ivMaterialLevel[i] = frameMaterials[i].findViewById(R.id.card_iv_level);
        }
    }

    private void setupClickListeners() {
        frameMainCard.setOnClickListener(v -> openCardSelector(-1));
        for (int i = 0; i < 5; i++) {
            final int slotIndex = i;
            frameMaterials[i].setOnClickListener(v -> openCardSelector(slotIndex));
        }
        btnUpgrade.setOnClickListener(v -> performUpgrade());
    }

    private void openCardSelector(int slotIndex) {
        if (slotIndex != -1 && mainCard == null) {
            Toast.makeText(getContext(), "Vui lòng chọn thẻ chính trước!", Toast.LENGTH_SHORT).show();
            return;
        }
        currentMaterialSlot = slotIndex;
        InventoryBottomSheet bottomSheet = new InventoryBottomSheet();
        if (slotIndex != -1) {
            List<Objet> currentSelected = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                if (materialCards[i] != null) currentSelected.add(materialCards[i]);
            }
            bottomSheet.setMultiSelectMode(mainCard, upgradeAlgorithm, currentSelected, materials -> {
                for (int i = 0; i < 5; i++) {
                    if (materials != null && i < materials.size()) materialCards[i] = materials.get(i);
                    else materialCards[i] = null;
                }
                updateUI();
            });
        } else {
            bottomSheet.setOnObjetSelectedListener(card -> {
                if (currentMaterialSlot == -1) {
                    mainCard = card;
                    for (int i = 0; i < 5; i++) materialCards[i] = null;
                }
                updateUI();
            });
        }
        bottomSheet.show(getParentFragmentManager(), "upgrade_card_selector");
    }

    // --- CONSTANTS cho Timing & Sync ---
    private static final int ANIM_DURATION_MATERIALS_GATHER = 600;
    private static final int VIDEO_FADE_DURATION = 150;
    private static final int VIDEO_CLIMAX_DELAY_MS = 2500; // Đoạn tia sét/sáng mạnh nhất
    private static final int CAMERA_SHAKE_DURATION_MS = 200;
    private static final int CARD_REVEAL_OVERSHOOT_DURATION = 500;
    private static final int RESULT_TEXT_SLIDE_DURATION = 300;

    // --- SFX HOOKS ---
    private void playSfx(String eventType) {
        // TODO: Gắn âm thanh tương ứng
        // "gather_materials" -> Nguyên liệu bay
        // "lightning_strike" -> Tia sét đánh
        // "glass_shatter" -> Kính vỡ
    }

    private void performUpgrade() {
        if (mainCard == null) return;
        List<Long> materialIds = new ArrayList<>();
        for (Objet mc : materialCards) {
            if (mc != null) materialIds.add((long) mc.getId());
        }
        if (materialIds.isEmpty()) return;

        // Disable button ngay lập tức để block click liên tục
        btnUpgrade.setEnabled(false);
        btnUpgrade.setText("UPGRADING...");
        btnUpgrade.setBackgroundResource(R.drawable.bg_upgrade_button_disabled);
        btnUpgrade.setTextColor(0xFFc2c6d1);

        Long userId = new SessionManager(requireContext()).getUserId();
        UpgradeRequest request = new UpgradeRequest(userId, (long) mainCard.getId(), materialIds);

        GameApiService apiService = ApiClient.getClient(requireContext()).create(GameApiService.class);
        apiService.upgradeCard(request).enqueue(new Callback<ApiResponse<UpgradeResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<UpgradeResponse>> call, Response<ApiResponse<UpgradeResponse>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getStatus() == 200) {
                    UpgradeResponse result = response.body().getData();
                    // Vào thẳng Cinematic, không có animation gathering
                    performUpgradeAnimation(result);
                } else {
                    resetUpgradeButton();
                    Toast.makeText(getContext(), "Lỗi hệ thống khi nâng cấp", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<UpgradeResponse>> call, Throwable t) {
                resetUpgradeButton();
                Toast.makeText(getContext(), "Lỗi mạng: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void resetUpgradeButton() {
        btnUpgrade.setEnabled(true);
        btnUpgrade.setText("UPGRADE");
        updateUpgradeButtonUI();
    }

    private void performUpgradeAnimation(UpgradeResponse result) {
        if (getContext() == null || rootView == null) return;

        ViewGroup parent = (ViewGroup) rootView;
        View oldOverlay = parent.findViewById(R.id.view_upgrade_overlay);
        if (oldOverlay != null) parent.removeView(oldOverlay);

        // Ẩn Navbar + UI tĩnh
        if (getActivity() != null) {
            View navBar = getActivity().findViewById(R.id.bottom_navigation);
            if (navBar != null) navBar.setVisibility(View.GONE);
        }
        if (layoutContentWrapper != null) {
            layoutContentWrapper.animate().alpha(0f).setDuration(150).start();
        }

        // === OVERLAY ===
        FrameLayout overlay = new FrameLayout(getContext());
        overlay.setId(R.id.view_upgrade_overlay);
        overlay.setAlpha(0f);
        overlay.setBackgroundColor(Color.TRANSPARENT);
        parent.addView(overlay, new ViewGroup.LayoutParams(-1, -1));

        // Video sấm chớp (thành công)
        VideoView successVideoView = new VideoView(getContext());
        successVideoView.setVisibility(View.GONE);
        successVideoView.setAlpha(1f);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        // Chiều cao video sẽ chiếm khoảng 80% màn hình để đảm bảo phủ từ đỉnh xuống qua thẻ bài
        int videoHeight = (int) (screenHeight * 0.85f); 
        FrameLayout.LayoutParams successParams = new FrameLayout.LayoutParams(screenWidth, videoHeight);
        successParams.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
        overlay.addView(successVideoView, successParams);
        String successVideoPath = "android.resource://" + getContext().getPackageName() + "/" + R.raw.successupgrade;
        successVideoView.setVideoURI(Uri.parse(successVideoPath));

        // UpgradeSceneView (Hiệu ứng glitch/streaks)
        UpgradeSceneView sceneView = new UpgradeSceneView(getContext());
        sceneView.setVisibility(View.GONE);
        overlay.addView(sceneView, new FrameLayout.LayoutParams(-1, -1));

        // === CARD WRAPPER ===
        FrameLayout cardWrapper = new FrameLayout(getContext());
        cardWrapper.setAlpha(0f);
        cardWrapper.setScaleX(0.8f);
        cardWrapper.setScaleY(0.8f);

        com.google.android.material.card.MaterialCardView resultCard = new com.google.android.material.card.MaterialCardView(getContext());
        resultCard.setId(View.generateViewId());
        resultCard.setCardBackgroundColor(Color.TRANSPARENT);
        resultCard.setRadius(getResources().getDisplayMetrics().density * 12f);
        resultCard.setCardElevation(0f);
        resultCard.setStrokeWidth(0);

        androidx.constraintlayout.widget.ConstraintLayout cardContainer = new androidx.constraintlayout.widget.ConstraintLayout(getContext());
        cardContainer.setBackgroundResource(R.drawable.bg_card_filled);
        LayoutInflater.from(getContext()).inflate(R.layout.layout_core_card, cardContainer, true);
        resultCard.addView(cardContainer, new ViewGroup.LayoutParams(-1, -1));

        int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.5f);
        int height = (int) (width * 1.54f);
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(width, height);
        cardParams.gravity = android.view.Gravity.CENTER;
        resultCard.setLayoutParams(cardParams);
        cardWrapper.addView(resultCard);

        View neonGlow = new View(getContext());
        neonGlow.setBackgroundResource(R.drawable.bg_neon_glow);
        neonGlow.setAlpha(0f);
        neonGlow.setScaleX(0.8f);
        neonGlow.setScaleY(0.8f);
        cardWrapper.addView(neonGlow, new FrameLayout.LayoutParams(width, height, android.view.Gravity.CENTER));

        SpriteSheetView spriteSheetView = new SpriteSheetView(getContext());
        spriteSheetView.setVisibility(View.GONE);
        spriteSheetView.init(R.drawable.failedupgrade, 8, 4, 18, 1000);
        spriteSheetView.setDrawSettings(1.7f, 0f, 0f);
        cardWrapper.addView(spriteSheetView, new FrameLayout.LayoutParams((int)(width * 1.5f), (int)(height * 1.5f), android.view.Gravity.CENTER));

        overlay.addView(cardWrapper, new FrameLayout.LayoutParams(-1, -1));

        // Flash trắng (Climax)
        View flashWhite = new View(getContext());
        flashWhite.setBackgroundColor(Color.WHITE);
        flashWhite.setAlpha(0f);
        flashWhite.setVisibility(View.GONE);
        overlay.addView(flashWhite, new FrameLayout.LayoutParams(-1, -1));

        // Bind card views
        ImageView ivResultImage = cardContainer.findViewById(R.id.card_iv_image);
        TextView tvResultOvr = cardContainer.findViewById(R.id.card_tv_ovr);
        ImageView ivResultLevel = cardContainer.findViewById(R.id.card_iv_level);
        View shimmer = cardContainer.findViewById(R.id.view_card_shimmer);

        // Load ảnh thẻ CŨ lên card (sẽ swap sang data mới sau)
        Glide.with(getContext()).load(mainCard.getImageUrl()).into(ivResultImage);
        tvResultOvr.setVisibility(View.VISIBLE);
        tvResultOvr.setText(String.valueOf(mainCard.getOvr()));
        if (mainCard.getCardLevel() > 0) {
            ivResultLevel.setVisibility(View.VISIBLE);
            String gradePath = "file:///android_asset/grade/" + Math.min(mainCard.getCardLevel(), 10) + ".png";
            Glide.with(getContext()).load(gradePath).into(ivResultLevel);
        }

        // Nút DONE
        androidx.appcompat.widget.AppCompatButton btnDone = new androidx.appcompat.widget.AppCompatButton(getContext());
        btnDone.setText("DONE");
        btnDone.setTextColor(Color.WHITE);
        btnDone.setBackgroundResource(R.drawable.bg_upgrade_button_active);
        btnDone.setVisibility(View.GONE);
        btnDone.setAlpha(0f);
        FrameLayout.LayoutParams btnParams = new FrameLayout.LayoutParams(
                (int)(120 * getResources().getDisplayMetrics().density),
                (int)(48 * getResources().getDisplayMetrics().density));
        btnParams.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL;
        btnParams.bottomMargin = (int)(64 * getResources().getDisplayMetrics().density);
        overlay.addView(btnDone, btnParams);

        // Title kết quả
        TextView tvTitle = new TextView(getContext());
        tvTitle.setText(result.isSuccess() ? "Upgrade Successful!" : "Upgrade Failed!");
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(24);
        tvTitle.setAlpha(0f);
        tvTitle.setVisibility(View.GONE);
        tvTitle.setTranslationY(80f);
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(-2, -2);
        titleParams.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
        titleParams.topMargin = (int)(80 * getResources().getDisplayMetrics().density);
        overlay.addView(tvTitle, titleParams);

        // ══════════════════════════════════════════════════
        // SEQUENCE BẮT ĐẦU
        // ══════════════════════════════════════════════════
        overlay.animate().alpha(1f).setDuration(150).start();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // Hiện card bay lên giữa
            cardWrapper.setAlpha(1f);
            float currentY = frameMainCard.getY() + frameMainCard.getHeight() / 2f;
            float centerY = parent.getHeight() / 2f;
            cardWrapper.setTranslationY(currentY - centerY);

            ObjectAnimator moveAnim = ObjectAnimator.ofFloat(cardWrapper, "translationY", currentY - centerY, 0f);
            ObjectAnimator sx = ObjectAnimator.ofFloat(cardWrapper, "scaleX", 0.8f, 1.0f);
            ObjectAnimator sy = ObjectAnimator.ofFloat(cardWrapper, "scaleY", 0.8f, 1.0f);
            moveAnim.setDuration(800);
            sx.setDuration(800);
            sy.setDuration(800);
            moveAnim.setInterpolator(new DecelerateInterpolator());

            moveAnim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    // Khi card đã đáp xuống tâm, cho lớp trắng (neonGlow) mờ dần hiện lên
                    neonGlow.setScaleX(1.0f);
                    neonGlow.setScaleY(1.0f);
                    neonGlow.animate().alpha(1f).setDuration(300).withEndAction(() -> {
                        
                        // TRANSITION INTO CUTSCENE: Cross-fade (Tiền Cutscene)
                        sceneView.setCoreBounds(
                                parent.getWidth() / 2f - width / 2f,
                                parent.getHeight() / 2f - height / 2f,
                                parent.getWidth() / 2f + width / 2f,
                                parent.getHeight() / 2f + height / 2f);
                        sceneView.setAlpha(0f);
                        sceneView.setVisibility(View.VISIBLE);
                        sceneView.startAnimation();
                        sceneView.animate().alpha(1f).setDuration(400).start();

                        // Sau khi lớp trắng hiện xong, cả card + layer cùng fade out
                        cardWrapper.animate().alpha(0f).setDuration(400).withEndAction(() -> {
                            // Khi đã mờ hẳn, dọn dẹp
                            neonGlow.setVisibility(View.GONE);
                            cardWrapper.setVisibility(View.GONE);

                            // Chuẩn bị data thẻ mới (ngầm)
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                tvResultOvr.setText(String.valueOf(result.getNewOvr()));
                                if (result.getNewLevel() > 0) {
                                    ivResultLevel.setVisibility(View.VISIBLE);
                                    String p = "file:///android_asset/grade/" + Math.min(result.getNewLevel(), 10) + ".png";
                                    Glide.with(getContext()).load(p).into(ivResultLevel);
                                    LevelBadgeEffectHelper.apply(ivResultLevel, result.getNewLevel());
                                } else {
                                    ivResultLevel.setVisibility(View.GONE);
                                }
                                Objet t = new Objet(mainCard.getId(), mainCard.getCollectionId(), mainCard.getImageUrl(), result.getNewLevel(), 0, result.getNewLevel());
                                t.setOvr(result.getNewOvr());
                                CardEffectHelper.apply(resultCard, shimmer, t, true);

                                if (!result.isSuccess()) {
                                    android.graphics.ColorMatrix matrix = new android.graphics.ColorMatrix();
                                    matrix.setSaturation(0);
                                    resultCard.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                                    android.graphics.Paint paint = new android.graphics.Paint();
                                    paint.setColorFilter(new android.graphics.ColorMatrixColorFilter(matrix));
                                    resultCard.setLayerPaint(paint);
                                }
                            }, VIDEO_CLIMAX_DELAY_MS - 400);

                            // ── CLIMAX: TRANSITION OUT OF CUTSCENE (Hậu Cutscene) ──
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                // Cross-fade mờ đi Cutscene
                                sceneView.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                                    sceneView.stopAnimation();
                                    sceneView.setVisibility(View.GONE);
                                    sceneView.setAlpha(1f); // Reset state
                                }).start();

                                float topY = -(parent.getHeight() / 6f); // Đặt ở vị trí 1/3 màn hình từ trên xuống
                                cardWrapper.setTranslationY(topY);
                                cardWrapper.setAlpha(0f);
                                cardWrapper.setVisibility(View.VISIBLE);
                                resultCard.setAlpha(1f);

                                if (result.isSuccess()) {
                                    successVideoView.setVisibility(View.VISIBLE);
                                    successVideoView.setAlpha(1f);
                                    
                                    // Đồng bộ tuyệt đối: Chờ VideoView báo cáo đã render frame đầu tiên
                                    successVideoView.setOnInfoListener((mp, what, extra) -> {
                                        if (what == android.media.MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                                            new Handler(Looper.getMainLooper()).post(() -> {
                                                // Object, FX, và Flash lập tức bung ra không cần delay
                                                cardWrapper.animate().alpha(1f).setDuration(300).setStartDelay(0).start();
                                                playSfx("lightning_strike");
                                                flashWhite.setVisibility(View.VISIBLE);
                                                flashWhite.animate().alpha(1f).setDuration(75).withEndAction(() ->
                                                        flashWhite.animate().alpha(0f).setDuration(75).start()
                                                ).start();
                                            });
                                            successVideoView.setOnInfoListener(null); // Giải phóng listener
                                            return true;
                                        }
                                        return false;
                                    });
                                    
                                    successVideoView.start();

                                } else {
                                    // Fallback thất bại thì không delay
                                    cardWrapper.animate().alpha(1f).setDuration(300).start();
                                    playSfx("glass_shatter");
                                    ObjectAnimator shake = ObjectAnimator.ofFloat(cardWrapper, "translationX", 0, 25, -25, 25, -25, 15, -15, 6, -6, 0);
                                    shake.setDuration(CAMERA_SHAKE_DURATION_MS);
                                    shake.start();
                                    spriteSheetView.setAlpha(1f);
                                    spriteSheetView.setVisibility(View.VISIBLE);
                                    spriteSheetView.play(null);
                                }

                                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                    if (result.isSuccess()) {
                                        successVideoView.animate().alpha(0f).setDuration(VIDEO_FADE_DURATION).withEndAction(() -> {
                                            successVideoView.setVisibility(View.GONE);
                                            successVideoView.stopPlayback();
                                        }).start();
                                    } else {
                                        spriteSheetView.animate().alpha(0f).setDuration(150).withEndAction(() -> {
                                            spriteSheetView.setVisibility(View.GONE);
                                        }).start();
                                    }

                                    cardWrapper.animate()
                                            .translationY(0f).scaleX(1.0f).scaleY(1.0f)
                                            .setDuration(800).setInterpolator(new AccelerateInterpolator())
                                            .withEndAction(() -> {
                                                finalizeAnimationUI(tvTitle, btnDone, cardWrapper, null);
                                            }).start();
                                }, 3000);
                            }, VIDEO_CLIMAX_DELAY_MS);
                        }).start();
                    }).start();
                }
            });
            moveAnim.start();
            sx.start();
            sy.start();
        }, 500);

        // Nút DONE: Khôi phục toàn bộ UI
        btnDone.setOnClickListener(v -> {
            overlay.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                parent.removeView(overlay);
                if (getActivity() != null) {
                    View navBar = getActivity().findViewById(R.id.bottom_navigation);
                    if (navBar != null) navBar.setVisibility(View.VISIBLE);
                }
                resultCard.setLayerPaint(null);
                if (layoutContentWrapper != null) {
                    layoutContentWrapper.setAlpha(1f);
                }
                resetUpgradeButton();
                finalizeData(result);
            }).start();
        });
    }

    private void finalizeAnimationUI(TextView title, View done, View card, View video) {
        if (video != null) {
            video.animate().alpha(0f).setDuration(VIDEO_FADE_DURATION).start();
        }
        title.setVisibility(View.VISIBLE);
        done.setVisibility(View.VISIBLE);
        
        // Anim Title (Slide Up + Fade In)
        ObjectAnimator slideUp = ObjectAnimator.ofFloat(title, "translationY", 80f, 0f);
        ObjectAnimator titleFade = ObjectAnimator.ofFloat(title, "alpha", 0f, 1f);
        slideUp.setDuration(RESULT_TEXT_SLIDE_DURATION);
        titleFade.setDuration(RESULT_TEXT_SLIDE_DURATION);
        slideUp.setInterpolator(new DecelerateInterpolator());
        slideUp.start(); titleFade.start();

        ObjectAnimator.ofFloat(done, "alpha", 0f, 1f).setDuration(RESULT_TEXT_SLIDE_DURATION).start();
        
        // Anim Card Reveal (Overshoot)
        card.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setInterpolator(new OvershootInterpolator(2.5f))
            .setDuration(CARD_REVEAL_OVERSHOOT_DURATION)
            .start();
    }

    private void finalizeData(UpgradeResponse result) {
        mainCard.setCardLevel(result.getNewLevel());
        mainCard.setOvr(result.getNewOvr());
        for (int i = 0; i < 5; i++) materialCards[i] = null;
        DatabaseLoader.clearUserCache();
        GameApiService apiService = ApiClient.getClient(requireContext()).create(GameApiService.class);
        DatabaseLoader.reloadInventoryFromServer(requireContext(), new SessionManager(requireContext()).getUserId(), apiService);
        updateUI();
    }

    private void updateUI() {
        if (rootView == null) return;
        updateMainCardUI();
        updateStatsUI();
        updateLevelIndicatorUI();
        updateProgressBarUI();
        updateMaterialsUI();
        updateUpgradeButtonUI();
    }

    private void updateMainCardUI() {
        if (rootView == null) return;
        com.google.android.material.card.MaterialCardView cardMain = rootView.findViewById(R.id.card_main);
        View shimmer = cardMain != null ? cardMain.findViewById(R.id.view_card_shimmer) : null;
        if (mainCard == null) {
            btnAddMainCard.setVisibility(View.VISIBLE);
            ivMainCardImage.setVisibility(View.GONE);
            tvCardOvr.setVisibility(View.GONE);
            ivCardLevelBadge.setVisibility(View.GONE);
            viewCardBg.setBackgroundResource(R.drawable.bg_card_placeholder);
            CardEffectHelper.remove(cardMain, shimmer);
            LevelBadgeEffectHelper.remove(ivCardLevelBadge);
        } else {
            btnAddMainCard.setVisibility(View.GONE);
            ivMainCardImage.setVisibility(View.VISIBLE);
            viewCardBg.setBackgroundResource(R.drawable.bg_card_filled);
            Glide.with(this).load(mainCard.getImageUrl()).into(ivMainCardImage);
            tvCardOvr.setVisibility(View.VISIBLE);
            tvCardOvr.setText(String.valueOf(mainCard.getOvr()));
            if (mainCard.getCardLevel() > 0) {
                ivCardLevelBadge.setVisibility(View.VISIBLE);
                String path = "file:///android_asset/grade/" + Math.min(mainCard.getCardLevel(), 10) + ".png";
                Glide.with(this).load(path).into(ivCardLevelBadge);
                LevelBadgeEffectHelper.apply(ivCardLevelBadge, mainCard.getCardLevel());
            } else {
                ivCardLevelBadge.setVisibility(View.GONE);
                LevelBadgeEffectHelper.remove(ivCardLevelBadge);
            }
            CardEffectHelper.apply(cardMain, shimmer, mainCard, true);
        }
    }

    private void updateStatsUI() {
        if (mainCard == null) {
            layoutRightStats.setAlpha(0.2f);
            tvOvrAfter.setText("--");
            tvOvrCurrentSmall.setVisibility(View.GONE);
        } else {
            layoutRightStats.setAlpha(1.0f);
            tvOvrAfter.setText("+" + Math.min(mainCard.getCardLevel() + 1, 10));
            tvOvrCurrentSmall.setVisibility(View.VISIBLE);
            tvOvrCurrentSmall.setText(String.valueOf(mainCard.getOvr()));
        }
    }

    private void updateLevelIndicatorUI() {
        if (mainCard == null) {
            layoutLevelIndicator.setAlpha(0.4f);
            tvLevelCurrent.setText("--");
            tvLevelNext.setText("--");
            ivLevelCurrent.setVisibility(View.GONE);
            ivLevelNext.setVisibility(View.GONE);
        } else {
            layoutLevelIndicator.setAlpha(1.0f);
            int currentLevel = mainCard.getCardLevel();
            int nextLevel = Math.min(currentLevel + 1, 10);
            if (currentLevel > 0) {
                tvLevelCurrent.setVisibility(View.GONE);
                ivLevelCurrent.setVisibility(View.VISIBLE);
                Glide.with(this).load("file:///android_asset/grade/" + currentLevel + ".png").into(ivLevelCurrent);
            } else {
                tvLevelCurrent.setVisibility(View.VISIBLE);
                tvLevelCurrent.setText("+0");
                ivLevelCurrent.setVisibility(View.GONE);
            }
            if (nextLevel > 0) {
                tvLevelNext.setVisibility(View.GONE);
                ivLevelNext.setVisibility(View.VISIBLE);
                Glide.with(this).load("file:///android_asset/grade/" + nextLevel + ".png").into(ivLevelNext);
            } else {
                tvLevelNext.setVisibility(View.VISIBLE);
                tvLevelNext.setText("+1");
                ivLevelNext.setVisibility(View.GONE);
            }
        }
    }

    private void updateProgressBarUI() {
        if (mainCard == null || upgradeAlgorithm == null) {
            setProgressWidth(0);
            return;
        }
        List<UpgradeAlgorithm.Card> materials = new ArrayList<>();
        for (Objet mc : materialCards) {
            if (mc != null) materials.add(createAlgoCard(mc));
        }
        if (materials.isEmpty()) {
            setProgressWidth(0);
            return;
        }
        double fillPercent = upgradeAlgorithm.calculateFillPercent(createAlgoCard(mainCard), materials);
        setProgressWidth(fillPercent);
    }

    private void setProgressWidth(double percent) {
        if (viewProgressFill == null) return;
        viewProgressFill.post(() -> {
            ViewGroup parent = (ViewGroup) viewProgressFill.getParent();
            int fillWidth = (int) (parent.getWidth() * (percent / 100.0));
            ViewGroup.LayoutParams params = viewProgressFill.getLayoutParams();
            params.width = fillWidth;
            viewProgressFill.setLayoutParams(params);
        });
    }

    private void updateMaterialsUI() {
        int selectedCount = 0;
        for (int i = 0; i < 5; i++) {
            com.google.android.material.card.MaterialCardView cardView = (com.google.android.material.card.MaterialCardView) frameMaterials[i];
            View shimmer = cardView.findViewById(R.id.view_card_shimmer);
            if (materialCards[i] != null) {
                selectedCount++;
                ivMaterials[i].setVisibility(View.VISIBLE);
                tvMaterialPlus[i].setVisibility(View.GONE);
                Glide.with(this).load(materialCards[i].getImageUrl()).into(ivMaterials[i]);
                tvMaterialOvr[i].setVisibility(View.VISIBLE);
                tvMaterialOvr[i].setText(String.valueOf(materialCards[i].getOvr()));
                if (materialCards[i].getCardLevel() > 0) {
                    ivMaterialLevel[i].setVisibility(View.VISIBLE);
                    Glide.with(this).load("file:///android_asset/grade/" + Math.min(materialCards[i].getCardLevel(), 10) + ".png").into(ivMaterialLevel[i]);
                    LevelBadgeEffectHelper.apply(ivMaterialLevel[i], materialCards[i].getCardLevel());
                } else {
                    ivMaterialLevel[i].setVisibility(View.GONE);
                    LevelBadgeEffectHelper.remove(ivMaterialLevel[i]);
                }
                viewMaterialBg[i].setBackgroundResource(R.drawable.bg_material_filled);
                CardEffectHelper.apply(cardView, shimmer, materialCards[i], false);
            } else {
                ivMaterials[i].setVisibility(View.GONE);
                tvMaterialPlus[i].setVisibility(View.VISIBLE);
                viewMaterialBg[i].setBackgroundResource(R.drawable.bg_material_slot);
                tvMaterialOvr[i].setVisibility(View.GONE);
                ivMaterialLevel[i].setVisibility(View.GONE);
                CardEffectHelper.remove(cardView, shimmer);
            }
        }
        tvMaterialsCount.setText(selectedCount + " / 5 Selected");
        tvMaterialsCount.setTextColor(selectedCount > 0 ? 0xFFa3c9ff : 0xFFc2c6d1);
    }

    private void updateUpgradeButtonUI() {
        boolean hasMaterials = false;
        for (Objet mc : materialCards) { if (mc != null) { hasMaterials = true; break; } }
        boolean canUpgrade = mainCard != null && hasMaterials && mainCard.getCardLevel() < 10;
        btnUpgrade.setEnabled(canUpgrade);
        btnUpgrade.setAlpha(canUpgrade ? 1.0f : 0.6f);
        btnUpgrade.setBackgroundResource(canUpgrade ? R.drawable.bg_upgrade_button_active : R.drawable.bg_upgrade_button_disabled);
        btnUpgrade.setTextColor(canUpgrade ? Color.WHITE : 0xFFc2c6d1);
    }

    private UpgradeAlgorithm.Card createAlgoCard(Objet card) {
        UpgradeAlgorithm.Card c = new UpgradeAlgorithm.Card();
        c.id = card.getIdString();
        c.typeKey = card.getTypeKey();
        c.level = card.getCardLevel();
        c.ovr = card.getOvr();
        return c;
    }
}

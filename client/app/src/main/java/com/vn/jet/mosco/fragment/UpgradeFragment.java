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
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import com.bumptech.glide.Glide;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.model.ApiResponse;
import com.vn.jet.mosco.model.CardDisplayItem;
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
import com.vn.jet.mosco.widget.MoscoButton;

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
 * UpgradeFragment - Phiên bản Thiên hà Cao cấp (V2 - Sửa lỗi Ghosting & Thẻ
 * trắng)
 * Chức năng rèn thẻ với hiệu ứng điện ảnh sấm sét, glitch và cháy nổ.
 * Đã tối ưu sequencing để tránh hiện tượng bóng ma và lóa sáng.
 */
public class UpgradeFragment extends Fragment {
    private static final String TAG = "UpgradeFragment";

    // Các View giao diện
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
    private View btnBack;

    // Dữ liệu logic
    private CardDisplayItem mainCard = null;
    private CardDisplayItem[] materialCards = new CardDisplayItem[5];
    private int currentMaterialSlot = -1;

    private UpgradeAlgorithm upgradeAlgorithm;

    public UpgradeFragment() {
    }

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
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
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

    public void setMainCard(CardDisplayItem card) {
        this.mainCard = card;
        if (rootView != null) {
            updateUI();
        }
    }

    private void setupVideoBackground() {
        if (bgVideoView == null || requireContext() == null)
            return;
        String path = "android.resource://" + requireContext().getPackageName() + "/" + R.raw.thunderbackground;
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
        Context ctx = requireContext();
        if (ctx == null)
            return;
        Gson gson = new Gson();
        try {
            InputStream isRate = ctx.getAssets().open("upgradeRate.json");
            InputStreamReader readerRate = new InputStreamReader(isRate);
            Type rateType = new TypeToken<Map<String, Double>>() {
            }.getType();
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
                    UpgradeAlgorithm.UpgradeConfig config = gson.fromJson(typeEntry.getValue(),
                            UpgradeAlgorithm.UpgradeConfig.class);
                    typeMap.put(typeEntry.getKey(), config);
                }
                customUpgrades.put(level, typeMap);
            }
            upgradeAlgorithm = new UpgradeAlgorithm(upgradeRates, customUpgrades);
        } catch (Exception e) {
            android.util.Log.e(TAG, "Lỗi khi tải cấu hình nâng cấp", e);
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
        btnBack = view.findViewById(R.id.btn_back_upgrade);

        int[] materialFrameIds = { R.id.frame_material_1, R.id.frame_material_2, R.id.frame_material_3,
                R.id.frame_material_4, R.id.frame_material_5 };
        int[] materialPlusIds = { R.id.tv_material_plus_1, R.id.tv_material_plus_2, R.id.tv_material_plus_3,
                R.id.tv_material_plus_4, R.id.tv_material_plus_5 };
        int[] materialBgIds = { R.id.view_material_bg_1, R.id.view_material_bg_2, R.id.view_material_bg_3,
                R.id.view_material_bg_4, R.id.view_material_bg_5 };

        for (int i = 0; i < 5; i++) {
            frameMaterials[i] = view.findViewById(materialFrameIds[i]);
            tvMaterialPlus[i] = view.findViewById(materialPlusIds[i]);
            viewMaterialBg[i] = view.findViewById(materialBgIds[i]);
            ivMaterials[i] = frameMaterials[i].findViewById(R.id.card_iv_image);
            tvMaterialOvr[i] = frameMaterials[i].findViewById(R.id.card_tv_ovr);
            ivMaterialLevel[i] = frameMaterials[i].findViewById(R.id.card_iv_level);

            // Đảm bảo ẩn OVR và Level mặc định trên card material
            if (tvMaterialOvr[i] != null)
                tvMaterialOvr[i].setVisibility(View.GONE);
            if (ivMaterialLevel[i] != null)
                ivMaterialLevel[i].setVisibility(View.GONE);
        }
    }

    private void setupClickListeners() {
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                    getParentFragmentManager().popBackStack();
                } else {
                    // Fallback: Quay về tab Home nếu không có backstack
                    navigateToTab(R.id.nav_home);
                }
            });
        }
        frameMainCard.setOnClickListener(v -> openCardSelector(-1));
        for (int i = 0; i < 5; i++) {
            final int slotIndex = i;
            frameMaterials[i].setOnClickListener(v -> openCardSelector(slotIndex));
        }
        btnUpgrade.setOnClickListener(v -> performUpgrade());
    }

    private void openCardSelector(int slotIndex) {
        if (slotIndex != -1 && mainCard == null) {
            Toast.makeText(requireContext(), getString(R.string.upgrade_msg_select_main), Toast.LENGTH_SHORT).show();
            return;
        }
        currentMaterialSlot = slotIndex;
        InventoryBottomSheet bottomSheet = new InventoryBottomSheet();
        if (slotIndex != -1) {
            List<CardDisplayItem> currentSelected = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                if (materialCards[i] != null)
                    currentSelected.add(materialCards[i]);
            }
            bottomSheet.setMultiSelectMode(mainCard, upgradeAlgorithm, currentSelected, materials -> {
                for (int i = 0; i < 5; i++) {
                    if (materials != null && i < materials.size())
                        materialCards[i] = materials.get(i);
                    else
                        materialCards[i] = null;
                }
                updateUI();
            });
        } else {
            bottomSheet.setOnCardSelectedListener(card -> {
                if (currentMaterialSlot == -1) {
                    mainCard = card;
                    for (int i = 0; i < 5; i++)
                        materialCards[i] = null;
                }
                updateUI();
            });
        }
        bottomSheet.show(getParentFragmentManager(), "upgrade_card_selector");
    }

    // --- CONSTANTS cho Timing & Sync ---
    private static final int RESULT_TEXT_SLIDE_DURATION = 300;
    private static final float TITLE_SLIDE_OFFSET = 80f;
    private static final float CARD_OVERSHOOT_TENSION = 2.5f;
    private static final int OVERLAY_FADE_DURATION = 150;
    private static final int VIDEO_FADE_DURATION = 150;
    private static final float CARD_INITIAL_SCALE = 0.8f;
    private static final int CARD_GATHER_DURATION = 800;
    private static final int NEON_GLOW_DURATION = 300;
    private static final int CUTSCENE_CROSSFADE_DURATION = 400;
    private static final int RESULT_REVEAL_DURATION = 300;
    private static final float SUCCESS_VIDEO_TOP_MARGIN_DP = 80f;
    private static final float DONE_BUTTON_BOTTOM_MARGIN_DP = 64f;
    private static final float RESULT_CARD_WIDTH_PERCENT = 0.5f;
    private static final float RESULT_CARD_RATIO = 1.54f;
    private static final int VIDEO_CLIMAX_DELAY_MS = 2500;
    private static final int CAMERA_SHAKE_DURATION_MS = 200;
    private static final int CARD_REVEAL_OVERSHOOT_DURATION = 500;

    // --- SFX HOOKS ---
    private void playSfx(String eventType) {
        // TODO: Gắn âm thanh tương ứng
        // "gather_materials" -> Nguyên liệu bay
        // "lightning_strike" -> Tia sét đánh
        // "glass_shatter" -> Kính vỡ
    }

    private void performUpgrade() {
        if (mainCard == null)
            return;
        List<Long> materialIds = new ArrayList<>();
        for (CardDisplayItem mc : materialCards) {
            if (mc != null)
                materialIds.add(mc.getId());
        }
        if (materialIds.isEmpty())
            return;

        // Disable button ngay lập tức để block click liên tục
        btnUpgrade.setEnabled(false);
        btnUpgrade.setText(getString(R.string.upgrade_action_upgrading));

        Long userId = new SessionManager(requireContext()).getUserId();
        UpgradeRequest request = new UpgradeRequest(userId, mainCard.getId(), materialIds);

        GameApiService apiService = ApiClient.getClient(requireContext()).create(GameApiService.class);
        apiService.upgradeCard(request).enqueue(new Callback<ApiResponse<UpgradeResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<UpgradeResponse>> call,
                    Response<ApiResponse<UpgradeResponse>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getStatus() == 200) {
                    UpgradeResponse result = response.body().getData();
                    // Vào thẳng Cinematic, không có animation gathering
                    performUpgradeAnimation(result);
                } else {
                    resetUpgradeButton();
                    Toast.makeText(requireContext(), getString(R.string.common_error_unknown), Toast.LENGTH_SHORT)
                            .show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<UpgradeResponse>> call, Throwable t) {
                resetUpgradeButton();
                Toast.makeText(requireContext(), getString(R.string.common_error_network), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void resetUpgradeButton() {
        btnUpgrade.setEnabled(true);
        btnUpgrade.setText(getString(R.string.upgrade_btn_text));
        updateUpgradeButtonUI();
    }

    private void performUpgradeAnimation(UpgradeResponse result) {
        if (requireContext() == null || rootView == null)
            return;

        ViewGroup parent = (ViewGroup) rootView;
        View oldOverlay = parent.findViewById(R.id.view_upgrade_overlay);
        if (oldOverlay != null)
            parent.removeView(oldOverlay);

        // Ẩn Navbar + UI tĩnh
        if (getActivity() != null) {
            View navBar = getActivity().findViewById(R.id.bottom_navigation);
            if (navBar != null)
                navBar.setVisibility(View.GONE);
        }
        if (layoutContentWrapper != null) {
            layoutContentWrapper.animate().alpha(0f).setDuration(OVERLAY_FADE_DURATION).start();
        }

        // === OVERLAY ===
        FrameLayout overlay = new FrameLayout(requireContext());
        overlay.setId(R.id.view_upgrade_overlay);
        overlay.setAlpha(0f);
        overlay.setBackgroundColor(Color.TRANSPARENT);
        parent.addView(overlay, new ViewGroup.LayoutParams(-1, -1));

        // Video sấm chớp (thành công)
        VideoView successVideoView = new VideoView(requireContext());
        successVideoView.setVisibility(View.GONE);
        successVideoView.setAlpha(1f);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;

        // Cấu hình ban đầu trước khi video kịp load
        FrameLayout.LayoutParams successParams = new FrameLayout.LayoutParams(screenWidth, screenWidth);
        successParams.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
        overlay.addView(successVideoView, successParams);
        String successVideoPath = "android.resource://" + requireContext().getPackageName() + "/"
                + R.raw.successupgrade;
        successVideoView.setVideoURI(Uri.parse(successVideoPath));

        successVideoView.setOnPreparedListener(mp -> {
            // Tính toán chiều cao chính xác theo tỷ lệ video, ép video full width (trái
            // phải)
            float videoRatio = mp.getVideoWidth() / (float) mp.getVideoHeight();
            int exactHeight = (int) (screenWidth / videoRatio);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) successVideoView.getLayoutParams();
            lp.width = screenWidth;
            lp.height = exactHeight;
            lp.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
            successVideoView.setLayoutParams(lp);
        });

        // UpgradeSceneView (Hiệu ứng glitch/streaks)
        UpgradeSceneView sceneView = new UpgradeSceneView(requireContext());
        sceneView.setVisibility(View.GONE);
        overlay.addView(sceneView, new FrameLayout.LayoutParams(-1, -1));

        // === CARD WRAPPER ===
        FrameLayout cardWrapper = new FrameLayout(requireContext());
        cardWrapper.setAlpha(0f);
        cardWrapper.setScaleX(CARD_INITIAL_SCALE);
        cardWrapper.setScaleY(CARD_INITIAL_SCALE);

        com.google.android.material.card.MaterialCardView resultCard = new com.google.android.material.card.MaterialCardView(
                requireContext());
        resultCard.setId(View.generateViewId());
        resultCard.setCardBackgroundColor(Color.TRANSPARENT);
        resultCard.setRadius(getResources().getDisplayMetrics().density * 12f);
        resultCard.setCardElevation(0f);
        resultCard.setStrokeWidth(0);

        androidx.constraintlayout.widget.ConstraintLayout cardContainer = new androidx.constraintlayout.widget.ConstraintLayout(
                requireContext());
        cardContainer.setBackgroundResource(R.drawable.bg_card_filled);
        LayoutInflater.from(requireContext()).inflate(R.layout.layout_core_card, cardContainer, true);
        resultCard.addView(cardContainer, new ViewGroup.LayoutParams(-1, -1));

        int width = (int) (getResources().getDisplayMetrics().widthPixels * RESULT_CARD_WIDTH_PERCENT);
        int height = (int) (width * RESULT_CARD_RATIO);
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(width, height);
        cardParams.gravity = android.view.Gravity.CENTER;
        resultCard.setLayoutParams(cardParams);
        cardWrapper.addView(resultCard);

        View neonGlow = new View(requireContext());
        neonGlow.setBackgroundResource(R.drawable.bg_neon_glow);
        neonGlow.setAlpha(0f);
        neonGlow.setScaleX(0.8f);
        neonGlow.setScaleY(0.8f);
        cardWrapper.addView(neonGlow, new FrameLayout.LayoutParams(width, height, android.view.Gravity.CENTER));

        SpriteSheetView spriteSheetView = new SpriteSheetView(requireContext());
        spriteSheetView.setVisibility(View.GONE);
        spriteSheetView.init(R.drawable.failedupgrade, 8, 4, 18, 1000);
        spriteSheetView.setDrawSettings(1.7f, 0f, 0f);
        cardWrapper.addView(spriteSheetView,
                new FrameLayout.LayoutParams((int) (width * 1.5f), (int) (height * 1.5f), android.view.Gravity.CENTER));

        overlay.addView(cardWrapper, new FrameLayout.LayoutParams(-1, -1));

        // Flash trắng (Climax)
        View flashWhite = new View(requireContext());
        flashWhite.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white));
        flashWhite.setAlpha(0f);
        flashWhite.setVisibility(View.GONE);
        overlay.addView(flashWhite, new FrameLayout.LayoutParams(-1, -1));

        // Bind card views
        ImageView ivResultImage = cardContainer.findViewById(R.id.card_iv_image);
        TextView tvResultOvr = cardContainer.findViewById(R.id.card_tv_ovr);
        ImageView ivResultLevel = cardContainer.findViewById(R.id.card_iv_level);
        View shimmer = cardContainer.findViewById(R.id.view_card_shimmer);

        // Load ảnh thẻ CŨ lên card (Sử dụng Priority Flow - Original)
        com.vn.jet.mosco.utils.GlideBindingAdapter.loadImage(ivResultImage, mainCard.getFrontImage(), false);
        tvResultOvr.setVisibility(View.GONE);
        tvResultOvr.setText(String.valueOf(mainCard.getOvr()));
        if (mainCard.getLevel() > 0) {
            ivResultLevel.setVisibility(View.VISIBLE);
            String gradePath = "file:///android_asset/grade/" + Math.min(mainCard.getLevel(), 10) + ".png";
            Glide.with(requireContext()).load(gradePath).into(ivResultLevel);
        }

        // Nút DONE
        MoscoButton btnDone = new MoscoButton(requireContext());
        btnDone.setText(getString(R.string.action_done));
        btnDone.setVisibility(View.GONE);
        btnDone.setAlpha(0f);

        int btnWidth = screenWidth - (int) (48 * getResources().getDisplayMetrics().density);
        int btnHeight = (int) getResources().getDimension(R.dimen.spacing_56dp);
        FrameLayout.LayoutParams btnParams = new FrameLayout.LayoutParams(btnWidth, btnHeight);
        btnParams.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL;
        btnParams.bottomMargin = (int) (DONE_BUTTON_BOTTOM_MARGIN_DP * getResources().getDisplayMetrics().density);
        overlay.addView(btnDone, btnParams);

        // Title kết quả
        TextView tvTitle = new TextView(requireContext());
        tvTitle.setText(
                result.isSuccess() ? getString(R.string.upgrade_msg_success) : getString(R.string.upgrade_msg_failed));
        tvTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
        tvTitle.setTextSize(24);
        tvTitle.setAlpha(0f);
        tvTitle.setVisibility(View.GONE);
        tvTitle.setTranslationY(TITLE_SLIDE_OFFSET);
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(-2, -2);
        titleParams.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
        titleParams.topMargin = (int) (SUCCESS_VIDEO_TOP_MARGIN_DP * getResources().getDisplayMetrics().density);
        overlay.addView(tvTitle, titleParams);

        // ══════════════════════════════════════════════════
        // SEQUENCE BẮT ĐẦU
        // ══════════════════════════════════════════════════
        overlay.animate().alpha(1f).setDuration(OVERLAY_FADE_DURATION).start();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // Hiện card bay lên giữa
            cardWrapper.setAlpha(1f);
            float currentY = frameMainCard.getY() + frameMainCard.getHeight() / 2f;
            float centerY = parent.getHeight() / 2f;
            cardWrapper.setTranslationY(currentY - centerY);

            ObjectAnimator moveAnim = ObjectAnimator.ofFloat(cardWrapper, "translationY", currentY - centerY, 0f);
            ObjectAnimator sx = ObjectAnimator.ofFloat(cardWrapper, "scaleX", CARD_INITIAL_SCALE, 1.0f);
            ObjectAnimator sy = ObjectAnimator.ofFloat(cardWrapper, "scaleY", CARD_INITIAL_SCALE, 1.0f);
            moveAnim.setDuration(CARD_GATHER_DURATION);
            sx.setDuration(CARD_GATHER_DURATION);
            sy.setDuration(CARD_GATHER_DURATION);
            moveAnim.setInterpolator(new DecelerateInterpolator());

            moveAnim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    // Khi card đã đáp xuống tâm, cho lớp trắng (neonGlow) mờ dần hiện lên
                    neonGlow.setScaleX(1.0f);
                    neonGlow.setScaleY(1.0f);
                    neonGlow.animate().alpha(1f).setDuration(NEON_GLOW_DURATION).withEndAction(() -> {

                        // TRANSITION INTO CUTSCENE: Cross-fade (Tiền Cutscene)
                        sceneView.setCoreBounds(
                                parent.getWidth() / 2f - width / 2f,
                                parent.getHeight() / 2f - height / 2f,
                                parent.getWidth() / 2f + width / 2f,
                                parent.getHeight() / 2f + height / 2f);
                        sceneView.setAlpha(0f);
                        sceneView.setVisibility(View.VISIBLE);
                        sceneView.startAnimation();
                        sceneView.animate().alpha(1f).setDuration(CUTSCENE_CROSSFADE_DURATION).start();

                        // Sau khi lớp trắng hiện xong, cả card + layer cùng fade out
                        cardWrapper.animate().alpha(0f).setDuration(CUTSCENE_CROSSFADE_DURATION).withEndAction(() -> {
                            // Khi đã mờ hẳn, dọn dẹp
                            neonGlow.setVisibility(View.GONE);
                            cardWrapper.setVisibility(View.GONE);

                            // Chuẩn bị data thẻ mới (ngầm)
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                tvResultOvr.setText(String.valueOf(result.getNewOvr()));
                                if (result.getNewLevel() > 0) {
                                    ivResultLevel.setVisibility(View.VISIBLE);
                                    String p = "file:///android_asset/grade/" + Math.min(result.getNewLevel(), 10)
                                            + ".png";
                                    Glide.with(requireContext()).load(p).into(ivResultLevel);
                                    LevelBadgeEffectHelper.apply(ivResultLevel, result.getNewLevel());
                                } else {
                                    ivResultLevel.setVisibility(View.GONE);
                                }
                                
                                // Tạo một CardDisplayItem giả để apply hiệu ứng
                                CardDisplayItem tempItem = new CardDisplayItem();
                                tempItem.setOvr(result.getNewOvr());
                                tempItem.setLevel(result.getNewLevel());
                                tempItem.setFrontImage(mainCard.getFrontImage());
                                tempItem.setOwned(true);
                                
                                CardEffectHelper.apply(resultCard, shimmer, tempItem, true, result.isSuccess());

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
                                sceneView.animate().alpha(0f).setDuration(RESULT_REVEAL_DURATION).withEndAction(() -> {
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
                                                cardWrapper.animate().alpha(1f).setDuration(RESULT_REVEAL_DURATION)
                                                        .setStartDelay(0).start();
                                                playSfx("lightning_strike");
                                                // Vô hiệu hóa Flash trắng theo yêu cầu
                                                /*
                                                 * flashWhite.setVisibility(View.VISIBLE);
                                                 * flashWhite.animate().alpha(1f).setDuration(75).withEndAction(() ->
                                                 * flashWhite.animate().alpha(0f).setDuration(75).start()
                                                 * ).start();
                                                 */
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
                                    ObjectAnimator shake = ObjectAnimator.ofFloat(cardWrapper, "translationX", 0, 25,
                                            -25, 25, -25, 15, -15, 6, -6, 0);
                                    shake.setDuration(CAMERA_SHAKE_DURATION_MS);
                                    shake.start();
                                    spriteSheetView.setAlpha(1f);
                                    spriteSheetView.setVisibility(View.VISIBLE);
                                    spriteSheetView.play(null);
                                }

                                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                    if (result.isSuccess()) {
                                        successVideoView.animate().alpha(0f).setDuration(VIDEO_FADE_DURATION)
                                                .withEndAction(() -> {
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
                                            .setDuration(CARD_GATHER_DURATION)
                                            .setInterpolator(new AccelerateInterpolator())
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
        }, CARD_REVEAL_OVERSHOOT_DURATION);

        // Nút DONE: Khôi phục toàn bộ UI
        btnDone.setOnClickListener(v -> {
            overlay.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                parent.removeView(overlay);
                if (getActivity() != null) {
                    View navBar = getActivity().findViewById(R.id.bottom_navigation);
                    if (navBar != null)
                        navBar.setVisibility(View.VISIBLE);
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
        slideUp.start();
        titleFade.start();

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
        mainCard.setLevel(result.getNewLevel());
        mainCard.setOvr(result.getNewOvr());
        for (int i = 0; i < 5; i++)
            materialCards[i] = null;
        DatabaseLoader.clearUserCache();
        GameApiService apiService = ApiClient.getClient(requireContext()).create(GameApiService.class);
        DatabaseLoader.reloadInventoryFromServer(requireContext(), new SessionManager(requireContext()).getUserId(),
                apiService);

        // [Phase 2] Phát tín hiệu Broadcast để CollectionFragment tự động Refresh
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(requireContext())
                .sendBroadcast(new android.content.Intent("ACTION_INVENTORY_UPDATED"));

        updateUI();
    }

    private void updateUI() {
        if (rootView == null)
            return;
        updateMainCardUI();
        updateStatsUI();
        updateLevelIndicatorUI();
        updateProgressBarUI();
        updateMaterialsUI();
        updateUpgradeButtonUI();
    }

    private void updateMainCardUI() {
        if (rootView == null)
            return;
        com.google.android.material.card.MaterialCardView cardMain = rootView.findViewById(R.id.card_main);
        View shimmer = cardMain != null ? cardMain.findViewById(R.id.view_card_shimmer) : null;
        View skeleton = cardMain != null ? cardMain.findViewById(R.id.layout_card_skeleton) : null;
        if (mainCard == null) {
            btnAddMainCard.setVisibility(View.VISIBLE);
            ivMainCardImage.setVisibility(View.GONE);
            tvCardOvr.setVisibility(View.GONE);
            ivCardLevelBadge.setVisibility(View.GONE);
            viewCardBg.setBackgroundResource(R.drawable.bg_card_placeholder);
            if (skeleton != null) skeleton.setVisibility(View.GONE);
            CardEffectHelper.remove(cardMain, shimmer);
            LevelBadgeEffectHelper.remove(ivCardLevelBadge);
        } else {
            btnAddMainCard.setVisibility(View.GONE);
            ivMainCardImage.setVisibility(View.VISIBLE);
            viewCardBg.setBackgroundResource(R.drawable.bg_card_filled);
            
            // Luồng tải ưu tiên: Thẻ chính dùng bản Original
            com.vn.jet.mosco.utils.GlideBindingAdapter.loadImage(ivMainCardImage, mainCard.getFrontImage(), false);
            tvCardOvr.setVisibility(View.GONE);
            tvCardOvr.setText(String.valueOf(mainCard.getOvr()));
            if (mainCard.getLevel() > 0) {
                ivCardLevelBadge.setVisibility(View.VISIBLE);
                String path = "file:///android_asset/grade/" + Math.min(mainCard.getLevel(), 10) + ".png";
                Glide.with(this).load(path).into(ivCardLevelBadge);
                LevelBadgeEffectHelper.apply(ivCardLevelBadge, mainCard.getLevel());
            } else {
                ivCardLevelBadge.setVisibility(View.GONE);
                LevelBadgeEffectHelper.remove(ivCardLevelBadge);
            }
            CardEffectHelper.apply(cardMain, shimmer, mainCard, true);
        }

        // Luôn ẩn OVR trên card chính (đã có info ở panel bên phải)
        if (tvCardOvr != null)
            tvCardOvr.setVisibility(View.GONE);
    }

    private void updateStatsUI() {
        if (mainCard == null) {
            layoutRightStats.setAlpha(0.2f);
            tvOvrAfter.setText("--");
            tvOvrCurrentSmall.setVisibility(View.GONE);
        } else {
            layoutRightStats.setAlpha(1.0f);
            tvOvrAfter.setText("+" + Math.min(mainCard.getLevel() + 1, 10));
            tvOvrCurrentSmall.setVisibility(View.GONE); // Hidden per OVR requirement
        }
    }

    private void updateLevelIndicatorUI() {
        if (mainCard == null) {
            layoutLevelIndicator.setAlpha(0.4f);
            tvLevelCurrent.setText(getString(R.string.placeholder_empty));
            tvLevelNext.setText(getString(R.string.placeholder_empty));
            ivLevelCurrent.setVisibility(View.GONE);
            ivLevelNext.setVisibility(View.GONE);
        } else {
            layoutLevelIndicator.setAlpha(1.0f);
            int currentLevel = mainCard.getLevel();
            int nextLevel = Math.min(currentLevel + 1, 10);
            if (currentLevel > 0) {
                tvLevelCurrent.setVisibility(View.GONE);
                ivLevelCurrent.setVisibility(View.VISIBLE);
                Glide.with(this).load("file:///android_asset/grade/" + currentLevel + ".png").into(ivLevelCurrent);
            } else {
                tvLevelCurrent.setVisibility(View.VISIBLE);
                tvLevelCurrent.setText(getString(R.string.format_level_plus, 0));
                ivLevelCurrent.setVisibility(View.GONE);
            }
            if (nextLevel > 0) {
                tvLevelNext.setVisibility(View.GONE);
                ivLevelNext.setVisibility(View.VISIBLE);
                Glide.with(this).load("file:///android_asset/grade/" + nextLevel + ".png").into(ivLevelNext);
            } else {
                tvLevelNext.setVisibility(View.VISIBLE);
                tvLevelNext.setText(getString(R.string.format_level_plus, 1));
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
        for (CardDisplayItem mc : materialCards) {
            if (mc != null)
                materials.add(createAlgoCard(mc));
        }
        if (materials.isEmpty()) {
            setProgressWidth(0);
            return;
        }
        double fillPercent = upgradeAlgorithm.calculateFillPercent(createAlgoCard(mainCard), materials);
        setProgressWidth(fillPercent);
    }

    private void setProgressWidth(double percent) {
        if (viewProgressFill == null)
            return;
        viewProgressFill.post(() -> {
            ViewGroup parent = (ViewGroup) viewProgressFill.getParent();
            if (parent.getWidth() == 0) {
                parent.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        parent.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        int fillWidth = (int) (parent.getWidth() * (percent / 100.0));
                        ViewGroup.LayoutParams params = viewProgressFill.getLayoutParams();
                        params.width = fillWidth;
                        viewProgressFill.setLayoutParams(params);
                    }
                });
            } else {
                int fillWidth = (int) (parent.getWidth() * (percent / 100.0));
                ViewGroup.LayoutParams params = viewProgressFill.getLayoutParams();
                params.width = fillWidth;
                viewProgressFill.setLayoutParams(params);
            }
        });
    }

    private void updateMaterialsUI() {
        int selectedCount = 0;
        for (int i = 0; i < 5; i++) {
            com.google.android.material.card.MaterialCardView cardView = (com.google.android.material.card.MaterialCardView) frameMaterials[i];
            View shimmer = cardView.findViewById(R.id.view_card_shimmer);
            View skeleton = cardView.findViewById(R.id.layout_card_skeleton);
            if (materialCards[i] != null) {
                selectedCount++;
                ivMaterials[i].setVisibility(View.VISIBLE);
                tvMaterialPlus[i].setVisibility(View.GONE);
                
                // Luồng tải ưu tiên: Card nguyên liệu dùng bản Thumbnail
                com.vn.jet.mosco.utils.GlideBindingAdapter.loadImage(ivMaterials[i], materialCards[i].getFrontImage(), true);
                tvMaterialOvr[i].setVisibility(View.GONE); // Luôn ẩn OVR trên card trong màn hình này
                if (materialCards[i].getLevel() > 0) {
                    ivMaterialLevel[i].setVisibility(View.VISIBLE);
                    Glide.with(this).load(
                            "file:///android_asset/grade/" + Math.min(materialCards[i].getLevel(), 10) + ".png")
                            .into(ivMaterialLevel[i]);
                    LevelBadgeEffectHelper.apply(ivMaterialLevel[i], materialCards[i].getLevel());
                } else {
                    ivMaterialLevel[i].setVisibility(View.GONE);
                    LevelBadgeEffectHelper.remove(ivMaterialLevel[i]);
                }
                viewMaterialBg[i].setBackgroundResource(R.drawable.bg_material_filled);
                // Loại bỏ hiệu ứng Shimmer/Glow ở đây theo yêu cầu người dùng
                CardEffectHelper.remove(cardView, shimmer);
            } else {
                ivMaterials[i].setVisibility(View.GONE);
                tvMaterialPlus[i].setVisibility(View.VISIBLE);
                viewMaterialBg[i].setBackgroundResource(R.drawable.bg_material_slot);
                tvMaterialOvr[i].setVisibility(View.GONE);
                ivMaterialLevel[i].setVisibility(View.GONE);
                CardEffectHelper.remove(cardView, shimmer);
            }
        }
        tvMaterialsCount.setText(getString(R.string.upgrade_format_materials_count_limit, selectedCount));
        int primaryColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.mosco_primary);
        int disabledColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.mosco_text_disabled);
        tvMaterialsCount.setTextColor(selectedCount > 0 ? primaryColor : disabledColor);
    }

    private void updateUpgradeButtonUI() {
        boolean hasMaterials = false;
        for (CardDisplayItem mc : materialCards) {
            if (mc != null) {
                hasMaterials = true;
                break;
            }
        }
        boolean canUpgrade = mainCard != null && hasMaterials && mainCard.getLevel() < 10;
        btnUpgrade.setEnabled(canUpgrade);
    }

    private UpgradeAlgorithm.Card createAlgoCard(CardDisplayItem card) {
        UpgradeAlgorithm.Card c = new UpgradeAlgorithm.Card();
        c.id = String.valueOf(card.getId());
        c.typeKey = card.getCardClass();
        c.level = card.getLevel();
        c.ovr = card.getOvr();
        return c;
    }

    /**
     * Navigates to a specific bottom navigation tab by its menu item ID.
     */
    private void navigateToTab(int navItemId) {
        if (getActivity() instanceof com.vn.jet.mosco.MainActivity) {
            BottomNavigationView nav = getActivity().findViewById(R.id.bottom_navigation);
            if (nav != null) {
                nav.setSelectedItemId(navItemId);
            }
        }
    }
}

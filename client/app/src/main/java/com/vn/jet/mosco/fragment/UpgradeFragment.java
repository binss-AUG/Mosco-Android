package com.vn.jet.mosco.fragment;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.TextureView;
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
import com.vn.jet.mosco.utils.ErrorTranslator;
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

    // Trình phát video ExoPlayer dành cho các thẻ Motion chính và kết quả nâng cấp (DRY)
    private androidx.media3.exoplayer.ExoPlayer mainVideoPlayer;
    private androidx.media3.exoplayer.ExoPlayer resultVideoPlayer;

    private void releaseUpgradePlayers() {
        if (mainVideoPlayer != null) {
            com.vn.jet.mosco.utils.MotionVideoHelper.releasePlayer(mainVideoPlayer);
            mainVideoPlayer = null;
        }
        if (resultVideoPlayer != null) {
            com.vn.jet.mosco.utils.MotionVideoHelper.releasePlayer(resultVideoPlayer);
            resultVideoPlayer = null;
        }
    }


    private UpgradeAlgorithm upgradeAlgorithm;
    private boolean isUpgrading = false;

    public UpgradeFragment() {
    }

    public static UpgradeFragment newInstance() {
        return new UpgradeFragment();
    }

    public static UpgradeFragment newInstance(CardDisplayItem mainCard) {
        UpgradeFragment fragment = new UpgradeFragment();
        Bundle args = new Bundle();
        args.putString("main_card_json", new Gson().toJson(mainCard));
        fragment.setArguments(args);
        return fragment;
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

        // Load card from arguments if present
        if (getArguments() != null && getArguments().containsKey("main_card_json")) {
            String json = getArguments().getString("main_card_json");
            this.mainCard = new Gson().fromJson(json, CardDisplayItem.class);
        }

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
        if (mainVideoPlayer != null) {
            mainVideoPlayer.play();
        }
        if (resultVideoPlayer != null) {
            resultVideoPlayer.play();
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
        if (mainVideoPlayer != null) {
            mainVideoPlayer.pause();
        }
        if (resultVideoPlayer != null) {
            resultVideoPlayer.pause();
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
        
        // [QUIET LUXURY] Tìm nút quay lại và đặt tiêu đề cho Header dùng chung
        View headerView = view.findViewById(R.id.layout_header_upgrade);
        if (headerView != null) {
            btnBack = headerView.findViewById(R.id.btn_back_common);
            TextView tvTitle = headerView.findViewById(R.id.tv_header_title);
            if (tvTitle != null) {
                tvTitle.setText(R.string.upgrade_header_title);
            }
        }

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
                if (isUpgrading) return;
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
        if (isUpgrading) return;
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

    // --- Timing & Layout Config (Nạp từ Resources) ---
    private int animOverlayFadeDuration;
    private int animVideoFadeDuration;
    private int animGatherDuration;
    private int animNeonGlowDuration;
    private int animCutsceneCrossfadeDuration;
    private int animResultRevealDuration;
    private int animVideoClimaxDelay;
    private int animCameraShakeDuration;
    private int animRevealOvershootDuration;
    private int animResultTextSlideDuration;

    private float dimenTitleSlideOffset;
    private float dimenSuccessVideoTopMargin;
    private float dimenDoneButtonBottomMargin;
    private float dimenResultCardWidth;
    private float dimenResultCardHeight;
    private float dimenCardInitialScale;

    private void initAnimationConstants() {
        Context ctx = requireContext();
        animOverlayFadeDuration = ctx.getResources().getInteger(R.integer.upgrade_anim_overlay_fade_duration);
        animVideoFadeDuration = ctx.getResources().getInteger(R.integer.upgrade_anim_video_fade_duration);
        animGatherDuration = ctx.getResources().getInteger(R.integer.upgrade_anim_gather_duration);
        animNeonGlowDuration = ctx.getResources().getInteger(R.integer.upgrade_anim_neon_glow_duration);
        animCutsceneCrossfadeDuration = ctx.getResources().getInteger(R.integer.upgrade_anim_cutscene_crossfade_duration);
        animResultRevealDuration = ctx.getResources().getInteger(R.integer.upgrade_anim_result_reveal_duration);
        animVideoClimaxDelay = ctx.getResources().getInteger(R.integer.upgrade_anim_video_climax_delay);
        animCameraShakeDuration = ctx.getResources().getInteger(R.integer.upgrade_anim_camera_shake_duration);
        animRevealOvershootDuration = ctx.getResources().getInteger(R.integer.upgrade_anim_reveal_overshoot_duration);
        animResultTextSlideDuration = ctx.getResources().getInteger(R.integer.upgrade_anim_result_text_slide_duration);

        dimenTitleSlideOffset = ctx.getResources().getDimension(R.dimen.upgrade_title_slide_offset);
        dimenSuccessVideoTopMargin = ctx.getResources().getDimension(R.dimen.upgrade_success_video_top_margin);
        dimenDoneButtonBottomMargin = ctx.getResources().getDimension(R.dimen.upgrade_done_button_bottom_margin);
        dimenResultCardWidth = ctx.getResources().getDimension(R.dimen.upgrade_result_card_width);
        dimenResultCardHeight = ctx.getResources().getDimension(R.dimen.upgrade_result_card_height);
        
        TypedValue outValue = new TypedValue();
        ctx.getResources().getValue(R.dimen.upgrade_card_initial_scale, outValue, true);
        dimenCardInitialScale = outValue.getFloat();
    }

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
        isUpgrading = true;
        btnUpgrade.setEnabled(false);
        btnUpgrade.setText(getString(R.string.upgrade_action_upgrading));

        final android.content.Context appContext = requireContext().getApplicationContext();
        Long userId = new SessionManager(appContext).getUserId();
        UpgradeRequest request = new UpgradeRequest(userId, mainCard.getId(), materialIds);

        // Pre-load constants & SpriteSheet (Local-First optimization)
        initAnimationConstants();
        preloadFailureSpriteSheet();

        GameApiService apiService = ApiClient.getClient(appContext).create(GameApiService.class);
        apiService.upgradeCard(request).enqueue(new Callback<ApiResponse<UpgradeResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<UpgradeResponse>> call,
                    Response<ApiResponse<UpgradeResponse>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getStatus() == 200) {
                    UpgradeResponse result = response.body().getData();
                    
                    // TẠI SAO: Đồng bộ và kiểm tra Huy hiệu mới mở khóa tức thời sau khi nâng cấp thành công
                    com.vn.jet.mosco.utils.BadgeSyncHelper.syncAndCheckBadges(getActivity(), userId);

                    performUpgradeAnimation(result);
                } else {
                    resetUpgradeButton();
                    String msg = getString(R.string.common_error_unknown);
                    if (response.body() != null && response.body().getMessage() != null) {
                        msg = response.body().getMessage();
                    }
                    Toast.makeText(requireContext(), ErrorTranslator.translate(requireContext(), msg), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<UpgradeResponse>> call, Throwable t) {
                resetUpgradeButton();
                Toast.makeText(requireContext(), getString(R.string.common_error_network), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private SpriteSheetView globalSpriteSheetView; 

    private void preloadFailureSpriteSheet() {
        if (globalSpriteSheetView == null) {
            globalSpriteSheetView = new SpriteSheetView(requireContext());
        }
        // Pre-decode Bitmap bất đồng bộ ngầm định (trong SpriteSheetView.init)
        globalSpriteSheetView.init(R.drawable.failedupgrade, 8, 4, 18, 1000);
    }

    private void resetUpgradeButton() {
        isUpgrading = false;
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
            View customNavBar = getActivity().findViewById(R.id.cl_custom_bottom_navigation);
            if (customNavBar != null)
                customNavBar.setVisibility(View.GONE);
        }
        if (layoutContentWrapper != null) {
            layoutContentWrapper.animate().alpha(0f).setDuration(animOverlayFadeDuration).withLayer().start();
        }
        View headerView = rootView.findViewById(R.id.layout_header_upgrade);
        if (headerView != null) {
            headerView.animate().alpha(0f).setDuration(animOverlayFadeDuration).withLayer().start();
        }

        // === OVERLAY ===
        FrameLayout overlay = new FrameLayout(requireContext());
        overlay.setId(R.id.view_upgrade_overlay);
        overlay.setAlpha(0f);
        overlay.setBackgroundColor(Color.TRANSPARENT);
        overlay.setLayerType(View.LAYER_TYPE_HARDWARE, null); // Bắt buộc Hardware Layer
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
        cardWrapper.setScaleX(dimenCardInitialScale);
        cardWrapper.setScaleY(dimenCardInitialScale);
        cardWrapper.setLayerType(View.LAYER_TYPE_HARDWARE, null); // Bắt buộc Hardware Layer cho wrapper card

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

        int width = (int) dimenResultCardWidth;
        int height = (int) dimenResultCardHeight;
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

        // Đảm bảo SpriteSheetView không có parent cũ (Tránh crash IllegalStateException)
        SpriteSheetView spriteSheetView = globalSpriteSheetView != null ? globalSpriteSheetView : new SpriteSheetView(requireContext());
        if (spriteSheetView.getParent() != null) {
            ((ViewGroup) spriteSheetView.getParent()).removeView(spriteSheetView);
        }
        
        if (globalSpriteSheetView == null) {
            spriteSheetView.init(R.drawable.failedupgrade, 8, 4, 18, 1000);
        }
        spriteSheetView.setDrawSettings(1.7f, 0f, 0f);
        spriteSheetView.setAlpha(0f);
        spriteSheetView.setVisibility(View.GONE);
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

        TextureView vvResultVideo = cardContainer.findViewById(R.id.card_vv_video);
        if (vvResultVideo != null) {
            vvResultVideo.setVisibility(View.GONE);
        }

        tvResultOvr.setVisibility(View.GONE); // Ẩn OVR theo yêu cầu
        tvResultOvr.setText(String.valueOf(mainCard.getOvr()));
        if (mainCard.getUpgradeLevel() > 0) {
            ivResultLevel.setVisibility(View.VISIBLE);
            String gradePath = "file:///android_asset/grade/" + Math.min(mainCard.getUpgradeLevel(), 10) + ".png";
            Glide.with(requireContext()).load(gradePath).dontAnimate().into(ivResultLevel);
            LevelBadgeEffectHelper.apply(ivResultLevel, mainCard.getUpgradeLevel());
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
        btnParams.bottomMargin = (int) dimenDoneButtonBottomMargin;
        overlay.addView(btnDone, btnParams);

        // Title kết quả
        TextView tvTitle = new TextView(requireContext());
        tvTitle.setText(
                result.isSuccess() ? getString(R.string.upgrade_msg_success) : getString(R.string.upgrade_msg_failed));
        tvTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
        tvTitle.setTextSize(24);
        tvTitle.setAlpha(0f);
        tvTitle.setVisibility(View.GONE);
        tvTitle.setTranslationY(dimenTitleSlideOffset);
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(-2, -2);
        titleParams.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
        titleParams.topMargin = (int) dimenSuccessVideoTopMargin;
        overlay.addView(tvTitle, titleParams);

        // ══════════════════════════════════════════════════
        // SEQUENCE ĐIỀU PHỐI BẰNG ANIMATORSET (ZERO HANDLER)
        // ══════════════════════════════════════════════════
        
        // 1. Overlay fade-in
        overlay.animate().alpha(1f).setDuration(animOverlayFadeDuration).withLayer().start();

        // 2. Gather Animation (Card bay lên)
        float currentY = frameMainCard.getY() + frameMainCard.getHeight() / 2f;
        float centerY = parent.getHeight() / 2f;
        cardWrapper.setAlpha(1f);
        cardWrapper.setTranslationY(currentY - centerY);

        ObjectAnimator gatherY = ObjectAnimator.ofFloat(cardWrapper, "translationY", currentY - centerY, 0f);
        ObjectAnimator gatherScaleX = ObjectAnimator.ofFloat(cardWrapper, "scaleX", dimenCardInitialScale, 1.0f);
        ObjectAnimator gatherScaleY = ObjectAnimator.ofFloat(cardWrapper, "scaleY", dimenCardInitialScale, 1.0f);
        
        android.animation.AnimatorSet gatherSet = new android.animation.AnimatorSet();
        gatherSet.playTogether(gatherY, gatherScaleX, gatherScaleY);
        gatherSet.setDuration(animGatherDuration);
        gatherSet.setInterpolator(new DecelerateInterpolator());
        gatherSet.setStartDelay(animRevealOvershootDuration); // Thay thế postDelayed đầu tiên

        gatherSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // 3. Neon Glow Animation
                neonGlow.setScaleX(1.0f);
                neonGlow.setScaleY(1.0f);
                neonGlow.animate().alpha(1f).setDuration(animNeonGlowDuration).withLayer().withEndAction(() -> {
                    
                    // 4. Transition into Cutscene
                    sceneView.setCoreBounds(
                            parent.getWidth() / 2f - width / 2f,
                            parent.getHeight() / 2f - height / 2f,
                            parent.getWidth() / 2f + width / 2f,
                            parent.getHeight() / 2f + height / 2f);
                    sceneView.setAlpha(0f);
                    sceneView.setVisibility(View.VISIBLE);
                    sceneView.startAnimation();
                    
                    // Cross-fade (Card mờ đi, SceneView hiện lên)
                    sceneView.animate().alpha(1f).setDuration(animCutsceneCrossfadeDuration).withLayer().start();
                    cardWrapper.animate().alpha(0f).setDuration(animCutsceneCrossfadeDuration).withLayer().withEndAction(() -> {
                        
                        // Khi đã mờ hẳn, chuẩn bị card kết quả (Pre-bind data)
                        neonGlow.setVisibility(View.GONE);
                        cardWrapper.setVisibility(View.GONE);

                        // Cập nhật data ngầm trong lúc cutscene đang chạy (Backend-Driven)
                        tvResultOvr.setVisibility(View.GONE); // Giữ ẩn OVR
                        tvResultOvr.setText(String.valueOf(result.getNewOvr()));
                        if (result.getNewLevel() > 0) {
                            ivResultLevel.setVisibility(View.VISIBLE);
                            String p = "file:///android_asset/grade/" + Math.min(result.getNewLevel(), 10) + ".png";
                            Glide.with(requireContext()).load(p).dontAnimate().into(ivResultLevel);
                            LevelBadgeEffectHelper.apply(ivResultLevel, result.getNewLevel());
                        } else {
                            ivResultLevel.setVisibility(View.GONE);
                        }
                        
                        CardDisplayItem tempItem = new CardDisplayItem();
                        tempItem.setOvr(result.getNewOvr());
                        tempItem.setLevel(result.getNewLevel());
                        // Thiết lập upgradeLevel để CardEffectHelper hiển thị huy hiệu cấp cộng (+ level) chính xác thay vì mặc định về 0
                        tempItem.setUpgradeLevel(result.getNewLevel());
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

                        // 5. CLIMAX REVEAL (Sử dụng AnimatorSet để timing chính xác)
                        android.animation.AnimatorSet climaxSet = new android.animation.AnimatorSet();
                        
                        // Fake delay để khớp với climax video
                        ValueAnimator climaxTimer = ValueAnimator.ofFloat(0, 1);
                        climaxTimer.setDuration(animVideoClimaxDelay);
                        
                        climaxTimer.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                // Kết thúc Cutscene
                                sceneView.animate().alpha(0f).setDuration(animResultRevealDuration).withLayer().withEndAction(() -> {
                                    sceneView.stopAnimation();
                                    sceneView.setVisibility(View.GONE);
                                }).start();

                                // Hiển thị Card Wrapper kết quả
                                float topY = -(parent.getHeight() / 6f);
                                cardWrapper.setTranslationY(topY);
                                cardWrapper.setAlpha(0f);
                                cardWrapper.setVisibility(View.VISIBLE);
                                resultCard.setAlpha(1f);

                                if (result.isSuccess()) {
                                    successVideoView.setVisibility(View.VISIBLE);
                                    successVideoView.setAlpha(1f);
                                    successVideoView.setOnInfoListener((mp, what, extra) -> {
                                        if (what == android.media.MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                                            // THÀNH CÔNG TỨC THÌ (Zero Latency)
                                            cardWrapper.setVisibility(View.VISIBLE);
                                            cardWrapper.setAlpha(1f);
                                            playSfx("lightning_strike");
                                            successVideoView.setOnInfoListener(null);
                                            return true;
                                        }
                                        return false;
                                    });
                                    successVideoView.start();
                                } else {
                                    // THẤT BẠI
                                    cardWrapper.setAlpha(1f);
                                    playSfx("glass_shatter");
                                    ObjectAnimator shake = ObjectAnimator.ofFloat(cardWrapper, "translationX", 0, 25, -25, 25, -25, 15, -15, 6, -6, 0);
                                    shake.setDuration(animCameraShakeDuration);
                                    shake.start();
                                    spriteSheetView.setAlpha(1f);
                                    spriteSheetView.setVisibility(View.VISIBLE);
                                    spriteSheetView.play(null);
                                }

                                // 6. FINALIZING ANIMATION (Dọn dẹp và hiện nút Done)
                                android.animation.AnimatorSet finalizeSet = new android.animation.AnimatorSet();
                                ValueAnimator finalizeTimer = ValueAnimator.ofFloat(0, 1);
                                finalizeTimer.setDuration(3000); // 3s chiêm ngưỡng kết quả
                                
                                finalizeTimer.addListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(Animator animation) {
                                        if (result.isSuccess()) {
                                            successVideoView.animate().alpha(0f).setDuration(animVideoFadeDuration).withLayer().withEndAction(() -> {
                                                successVideoView.setVisibility(View.GONE);
                                                successVideoView.stopPlayback();
                                            }).start();
                                        } else {
                                            spriteSheetView.animate().alpha(0f).setDuration(150).withLayer().withEndAction(() -> {
                                                spriteSheetView.setVisibility(View.GONE);
                                            }).start();
                                        }

                                        cardWrapper.animate()
                                                .translationY(0f).scaleX(1.0f).scaleY(1.0f)
                                                .setDuration(animGatherDuration)
                                                .setInterpolator(new AccelerateInterpolator())
                                                .withLayer()
                                                .withEndAction(() -> {
                                                    finalizeAnimationUI(tvTitle, btnDone, cardWrapper, vvResultVideo, result.isSuccess());
                                                }).start();
                                    }
                                });
                                finalizeTimer.start();
                            }
                        });
                        climaxTimer.start();
                    }).start();
                }).start();
            }
        });
        gatherSet.start();

        // Nút DONE: Khôi phục toàn bộ UI
        btnDone.setOnClickListener(v -> {
            isUpgrading = false;
            if (resultVideoPlayer != null) {
                com.vn.jet.mosco.utils.MotionVideoHelper.releasePlayer(resultVideoPlayer);
                resultVideoPlayer = null;
            }
            overlay.animate().alpha(0f).setDuration(300).withLayer().withEndAction(() -> {
                parent.removeView(overlay);
                resultCard.setLayerPaint(null);
                if (layoutContentWrapper != null) {
                    layoutContentWrapper.setAlpha(1f);
                }

                View doneHeaderView = rootView.findViewById(R.id.layout_header_upgrade);
                if (doneHeaderView != null) {
                    doneHeaderView.setAlpha(1f);
                }
                resetUpgradeButton();
                
                // Clear dữ liệu ngay lập tức trên UI thread để tránh gởi ID cũ lên server lần 2
                for (int i = 0; i < 5; i++) materialCards[i] = null;
                updateUI(); 

                finalizeData(result);
            }).start();
        });
    }



    private void finalizeAnimationUI(TextView title, View done, View card, TextureView vvResultVideo, boolean isSuccess) {
        title.setVisibility(View.VISIBLE);
        done.setVisibility(View.VISIBLE);

        // Anim Title (Slide Up + Fade In)
        ObjectAnimator slideUp = ObjectAnimator.ofFloat(title, "translationY", dimenTitleSlideOffset, 0f);
        ObjectAnimator titleFade = ObjectAnimator.ofFloat(title, "alpha", 0f, 1f);
        slideUp.setDuration(animResultTextSlideDuration);
        titleFade.setDuration(animResultTextSlideDuration);
        slideUp.setInterpolator(new DecelerateInterpolator());
        slideUp.start();
        titleFade.start();

        ObjectAnimator.ofFloat(done, "alpha", 0f, 1f).setDuration(animResultTextSlideDuration).start();

        // Anim Card Reveal (Overshoot)
        card.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setInterpolator(new OvershootInterpolator(2.5f))
                .setDuration(animRevealOvershootDuration)
                .withLayer()
                .withEndAction(() -> {
                    // Video MP4 playback cho thẻ kết quả nâng cấp
                    if (vvResultVideo != null) {
                        if (isSuccess && mainCard != null && mainCard.getFrontVideoUrl() != null && !mainCard.getFrontVideoUrl().isEmpty()) {
                            if (resultVideoPlayer != null) {
                                com.vn.jet.mosco.utils.MotionVideoHelper.releasePlayer(resultVideoPlayer);
                                resultVideoPlayer = null;
                            }
                            ImageView ivResultImage = card.findViewById(R.id.card_iv_image);
                            resultVideoPlayer = com.vn.jet.mosco.utils.MotionVideoHelper.playMotionVideo(requireContext(), vvResultVideo, mainCard.getFrontVideoUrl(), ivResultImage);
                        } else {
                            vvResultVideo.setVisibility(View.GONE);
                        }
                    }
                })
                .start();
    }

    private void finalizeData(UpgradeResponse result) {
        // Cập nhật Main Card data
        if (mainCard != null) {
            mainCard.setUpgradeLevel(result.getNewLevel());
            mainCard.setOvr(result.getNewOvr());
        }

        com.vn.jet.mosco.utils.AppExecutors.getInstance().diskIO().execute(() -> {
            GameApiService apiService = ApiClient.getClient(requireContext()).create(GameApiService.class);
            DatabaseLoader.reloadInventoryFromServer(requireContext(), new SessionManager(requireContext()).getUserId(),
                    apiService);
            
            // [Phase 2] Phát tín hiệu Broadcast
            androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(requireContext())
                    .sendBroadcast(new android.content.Intent("ACTION_INVENTORY_UPDATED"));

            if (getActivity() != null) {
                getActivity().runOnUiThread(this::updateUI);
            }
        });
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
            if (skeleton != null)
                skeleton.setVisibility(View.GONE);
            CardEffectHelper.remove(cardMain, shimmer);
            LevelBadgeEffectHelper.remove(ivCardLevelBadge);
            if (cardMain != null) {
                TextureView vvMainVideo = cardMain.findViewById(R.id.card_vv_video);
                if (vvMainVideo != null) vvMainVideo.setVisibility(View.GONE);
            }
            if (mainVideoPlayer != null) {
                com.vn.jet.mosco.utils.MotionVideoHelper.releasePlayer(mainVideoPlayer);
                mainVideoPlayer = null;
            }
        } else {
            btnAddMainCard.setVisibility(View.GONE);
            ivMainCardImage.setVisibility(View.VISIBLE);
            viewCardBg.setBackgroundResource(R.drawable.bg_card_filled);

            // Luồng tải ưu tiên: Thẻ chính dùng bản Original
            com.vn.jet.mosco.utils.GlideBindingAdapter.loadImage(ivMainCardImage, mainCard.getFrontImage(), false);

            // Video MP4 playback cho Motion Cards
            TextureView vvMainVideo = cardMain != null ? cardMain.findViewById(R.id.card_vv_video) : null;
            if (vvMainVideo != null) {
                if (mainVideoPlayer != null) {
                    com.vn.jet.mosco.utils.MotionVideoHelper.releasePlayer(mainVideoPlayer);
                    mainVideoPlayer = null;
                }
                if (mainCard.getFrontVideoUrl() != null && !mainCard.getFrontVideoUrl().isEmpty()) {
                    mainVideoPlayer = com.vn.jet.mosco.utils.MotionVideoHelper.playMotionVideo(requireContext(), vvMainVideo, mainCard.getFrontVideoUrl(), ivMainCardImage);
                } else {
                    vvMainVideo.setVisibility(View.GONE);
                }
            }

            tvCardOvr.setVisibility(View.GONE);
            tvCardOvr.setText(String.valueOf(mainCard.getOvr()));
            if (mainCard.getUpgradeLevel() > 0) {
                ivCardLevelBadge.setVisibility(View.VISIBLE);
                String path = "file:///android_asset/grade/" + Math.min(mainCard.getUpgradeLevel(), 10) + ".png";
                Glide.with(this).load(path).into(ivCardLevelBadge);
                LevelBadgeEffectHelper.apply(ivCardLevelBadge, mainCard.getUpgradeLevel());
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
            tvOvrAfter.setText("+" + Math.min(mainCard.getUpgradeLevel() + 1, 10));
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
            int currentLevel = mainCard.getUpgradeLevel();
            int nextLevel = Math.min(currentLevel + 1, 10);
            if (currentLevel > 0) {
                tvLevelCurrent.setVisibility(View.GONE);
                ivLevelCurrent.setVisibility(View.VISIBLE);
                Glide.with(this).load("file:///android_asset/grade/" + currentLevel + ".png").dontAnimate().into(ivLevelCurrent);
            } else {
                tvLevelCurrent.setVisibility(View.VISIBLE);
                tvLevelCurrent.setText(getString(R.string.format_level_plus, 0));
                ivLevelCurrent.setVisibility(View.GONE);
            }
            if (nextLevel > 0) {
                tvLevelNext.setVisibility(View.GONE);
                ivLevelNext.setVisibility(View.VISIBLE);
                Glide.with(this).load("file:///android_asset/grade/" + nextLevel + ".png").dontAnimate().into(ivLevelNext);
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
                parent.getViewTreeObserver()
                        .addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
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
                com.vn.jet.mosco.utils.GlideBindingAdapter.loadImage(ivMaterials[i], materialCards[i].getFrontImage(),
                        true);
                tvMaterialOvr[i].setVisibility(View.GONE); // Luôn ẩn OVR trên card trong màn hình này
                if (materialCards[i].getUpgradeLevel() > 0) {
                    ivMaterialLevel[i].setVisibility(View.VISIBLE);
                    Glide.with(this).load(
                            "file:///android_asset/grade/" + Math.min(materialCards[i].getUpgradeLevel(), 10) + ".png")
                            .into(ivMaterialLevel[i]);
                    LevelBadgeEffectHelper.apply(ivMaterialLevel[i], materialCards[i].getUpgradeLevel());
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
        int primaryColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.lg_accent_primary);
        int disabledColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.lg_text_disabled);
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
        c.level = card.getUpgradeLevel();
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        releaseUpgradePlayers();
    }
}



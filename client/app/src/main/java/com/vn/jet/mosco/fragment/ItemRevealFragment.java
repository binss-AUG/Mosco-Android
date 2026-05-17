package com.vn.jet.mosco.fragment;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;

import com.airbnb.lottie.LottieAnimationView;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.network.GachaRepository;
import com.vn.jet.mosco.utils.NumberUtils;
import com.vn.jet.mosco.utils.SessionManager;
import com.vn.jet.mosco.model.Objet;
import com.vn.jet.mosco.utils.CardEffectHelper;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ItemRevealFragment extends Fragment {

    private static final String ARG_NAME = "item_name";
    private static final String ARG_DESC = "item_desc";
    private static final String ARG_IMAGE = "item_image";
    private static final String ARG_QTY = "item_qty";
    private static final String ARG_CODE = "item_code";
    private static final String KEY_CARDS = "cards";
    private static final String KEY_CARD_DATA = "cardData";
    private static final String KEY_CARD_CLASS = "class";
    private static final String KEY_FRONT_IMAGE = "frontImage";
    private static final String KEY_BACK_IMAGE = "backImage";
    private static final String KEY_COLLECTION_ID = "collectionId";
    private static final String KEY_LEVEL = "level";
    private static final String KEY_UPGRADE_LEVEL = "upgradeLevel";
    private static final String KEY_MEMBER = "member";
    private static final String KEY_SEASON = "season";
    private static final String KEY_BACKGROUND_COLOR = "backgroundColor";
    private static final String KEY_TEXT_COLOR = "textColor";

    private String itemName;
    private String itemDesc;
    private String itemImage;
    private int itemQty;
    private String itemCode;

    private View packRevealRoot;
    private View packFlashOverlay;
    private GachaRepository gachaRepository;
    private ObjectAnimator floatingAnim;
    private ValueAnimator parallaxIdleAnimator;
    private float summaryCardBaseTranslationY = 0f;
    private LottieAnimationView loadingAnimView;
    private View skeletonLoadingView; // "Quiet Luxury" Objet Skeleton
    private ChaosParticleView activeParticleView;

    // Components dynamic for Flip
    private ImageView backImageView;
    private View shimmerView;
    private View currentGlowView;

    public static ItemRevealFragment newInstance(String name, String desc,
            String imageUri, int qty, String code) {
        ItemRevealFragment f = new ItemRevealFragment();
        Bundle args = new Bundle();
        args.putString(ARG_NAME, name);
        args.putString(ARG_DESC, desc);
        args.putString(ARG_IMAGE, imageUri);
        args.putInt(ARG_QTY, qty);
        args.putString(ARG_CODE, code);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            itemName = getArguments().getString(ARG_NAME, getString(R.string.reveal_default_item_name));
            itemDesc = getArguments().getString(ARG_DESC, "");
            itemImage = getArguments().getString(ARG_IMAGE, "");
            itemQty = getArguments().getInt(ARG_QTY, 1);
            itemCode = getArguments().getString(ARG_CODE, "");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_item_reveal, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        gachaRepository = new GachaRepository(requireContext());
        packRevealRoot = view.findViewById(R.id.root_item_reveal);
        if (packRevealRoot != null) {
            packRevealRoot.setBackgroundColor(
                    androidx.core.content.ContextCompat.getColor(requireContext(), R.color.lg_background));
        }

        if (getActivity() != null) {
            View navBar = getActivity().findViewById(R.id.bottom_navigation);
            if (navBar != null)
                navBar.setVisibility(View.GONE);
        }

        bindInitialUI(view);
    }

    private void bindInitialUI(View view) {
        TextView tvItemName = view.findViewById(R.id.tv_item_name);
        TextView tvItemInfo = view.findViewById(R.id.tv_item_info);
        TextView tvItemQty = view.findViewById(R.id.tv_item_qty);
        ImageView ivItemImage = view.findViewById(R.id.iv_item_image);
        MaterialCardView cardItem = view.findViewById(R.id.card_item);
        com.vn.jet.mosco.widget.MoscoButton btnOpenOne = view.findViewById(R.id.btn_open_one);
        com.vn.jet.mosco.widget.MoscoButton btnOpenAll = view.findViewById(R.id.btn_open_all);
        com.vn.jet.mosco.widget.MoscoButton btnDone = view.findViewById(R.id.btn_done);

        packFlashOverlay = view.findViewById(R.id.view_pack_flash_overlay);

        tvItemName.setText(
                itemName != null && !itemName.isEmpty() ? itemName : getString(R.string.reveal_default_item_name));
        tvItemInfo
                .setText(itemDesc != null && !itemDesc.isEmpty() ? itemDesc : getString(R.string.reveal_default_info));
        tvItemQty.setText(getString(R.string.format_qty, NumberUtils.format(requireContext(), itemQty)));

        if (itemImage != null && !itemImage.isEmpty()) {
            Glide.with(this).load(itemImage).placeholder(R.drawable.item_shop_demo).into(ivItemImage);
        } else {
            ivItemImage.setImageResource(R.drawable.item_shop_demo);
        }

        if (itemQty > 1) {
            btnOpenAll.setVisibility(View.VISIBLE);
            final int maxOpenQuantity = getResources().getInteger(R.integer.reveal_open_pack_max_quantity);
            int openAllDisplayQty = Math.min(itemQty, maxOpenQuantity);
            btnOpenAll.setText(getString(R.string.reveal_action_open_all, openAllDisplayQty));
            String capHint = getString(R.string.reveal_msg_limit_hint, maxOpenQuantity);
            String baseInfo = itemDesc != null && !itemDesc.isEmpty() ? itemDesc
                    : getString(R.string.reveal_default_info);
            tvItemInfo.setText(baseInfo + "\n" + capHint);
        } else {
            btnOpenAll.setVisibility(View.GONE);
        }

        if (itemQty <= 0) {
            btnOpenOne.setVisibility(View.GONE);
            btnOpenAll.setVisibility(View.GONE);
            btnDone.setVisibility(View.VISIBLE);
        } else {
            btnOpenOne.setVisibility(View.VISIBLE);
            btnDone.setVisibility(View.GONE);
        }

        view.findViewById(R.id.btn_back).setOnClickListener(v -> goBack());
        btnOpenOne.setOnClickListener(v -> startPackOpening(false));
        btnOpenAll.setOnClickListener(v -> startPackOpening(true));
        btnDone.setOnClickListener(v -> goBack());

        applyVisualEffects(cardItem, view);
    }

    private void startPackOpening(boolean openAll) {
        if (itemQty <= 0)
            return;
        final int maxOpenQuantity = getResources().getInteger(R.integer.reveal_open_pack_max_quantity);
        final int quantity = openAll ? Math.min(itemQty, maxOpenQuantity) : 1;
        showLoadingOverlay();
        setActionButtonsEnabled(false);

        Long userId = new SessionManager(requireContext()).getUserId();

        gachaRepository.openPack(userId, itemCode, quantity, new GachaRepository.GachaCallback<Map<String, Object>>() {
            @Override
            public void onSuccess(Map<String, Object> response) {
                try {
                    // PROACTIVE BACKGROUND REFRESH: Thay vì xóa sạch cache (gây load lâu sau này),
                    // ta kích hoạt nạp lại ngầm ngay bây giờ để lúc user quay lại Album là có sẵn
                    // data.
                    com.vn.jet.mosco.network.GameApiService apiService = com.vn.jet.mosco.network.ApiClient
                            .getClient(requireContext()).create(com.vn.jet.mosco.network.GameApiService.class);
                    com.vn.jet.mosco.utils.DatabaseLoader.reloadInventoryFromServer(requireContext(), userId,
                            apiService);

                    itemQty -= quantity;

                    if (getActivity() != null) {
                        processPackResponse(response, quantity);
                    }
                } catch (Exception e) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            hideLoadingOverlay(true);
                            goBack();
                        });
                    }
                }
            }

            @Override
            public void onError(int httpCode, String errorMessage) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        hideLoadingOverlay(true);
                        Toast.makeText(requireContext(), getString(R.string.reveal_error_format, errorMessage),
                                Toast.LENGTH_LONG).show();
                        goBack();
                    });
                }
            }
        });
    }

    private void processPackResponse(Map<String, Object> response, int quantity) {
        List<Map<String, Object>> cards = (List<Map<String, Object>>) response.get(KEY_CARDS);
        if (cards == null || cards.isEmpty()) {
            hideLoadingOverlay(true);
            goBack();
            return;
        }

        JSONObject topCardJsonObj = null;
        List<Map<String, Object>> bulkCards = cards;

        if (quantity == 1) {
            Map<String, Object> firstRoll = cards.get(0);
            Map<String, Object> cardData = (Map<String, Object>) firstRoll.get(KEY_CARD_DATA);
            if (cardData != null)
                topCardJsonObj = new JSONObject(cardData);
        } else {
            Map<String, Object> bestCard = cards.get(0);
            int maxTier = -1;
            for (Map<String, Object> roll : cards) {
                Map<String, Object> data = (Map<String, Object>) roll.get(KEY_CARD_DATA);
                if (data != null) {
                    int tier = getCardTier(String.valueOf(data.get(KEY_CARD_CLASS)));
                    if (tier > maxTier) {
                        maxTier = tier;
                        bestCard = roll;
                    }
                }
            }
            Map<String, Object> bestData = (Map<String, Object>) bestCard.get(KEY_CARD_DATA);
            if (bestData != null)
                topCardJsonObj = new JSONObject(bestData);
        }

        final JSONObject topCardJson = topCardJsonObj;
        if (topCardJson == null) {
            hideLoadingOverlay(true);
            goBack();
            return;
        }

        // PRELOAD IMAGE STRICTLY BEFORE PHASE 2
        String frontImageUrl = topCardJson.optString(KEY_FRONT_IMAGE, "");
        String backImageUrl = topCardJson.optString(KEY_BACK_IMAGE, "");

        if (!backImageUrl.isEmpty()) {
            Glide.with(this).load(backImageUrl).preload();
        }

        final boolean[] frontLoaded = { false };
        final boolean[] backLoaded = { backImageUrl.isEmpty() };
        Runnable tryStartPhase2 = () -> {
            if (frontLoaded[0] && backLoaded[0]) {
                View rootView = getView();
                if (rootView == null)
                    return;
                MaterialCardView cardItem = rootView.findViewById(R.id.card_item);
                // Đảm bảo Android đã layout xong trước khi chạy reveal nặng.
                cardItem.post(() -> hideLoadingOverlay(false, () -> startRevealAnimation(topCardJson, bulkCards)));
            }
        };

        Glide.with(this).asBitmap().load(frontImageUrl)
                .into(new com.bumptech.glide.request.target.CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource,
                            @Nullable com.bumptech.glide.request.transition.Transition<? super Bitmap> transition) {
                        frontLoaded[0] = true;
                        tryStartPhase2.run();
                    }

                    @Override
                    public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) {
                    }

                    @Override
                    public void onLoadFailed(@Nullable android.graphics.drawable.Drawable errorDrawable) {
                        frontLoaded[0] = true; // fallback
                        tryStartPhase2.run();
                    }
                });

        if (!backImageUrl.isEmpty()) {
            Glide.with(this).asBitmap().load(backImageUrl)
                    .into(new com.bumptech.glide.request.target.CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource,
                                @Nullable com.bumptech.glide.request.transition.Transition<? super Bitmap> transition) {
                            backLoaded[0] = true;
                            tryStartPhase2.run();
                        }

                        @Override
                        public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) {
                        }

                        @Override
                        public void onLoadFailed(@Nullable android.graphics.drawable.Drawable errorDrawable) {
                            backLoaded[0] = true;
                            tryStartPhase2.run();
                        }
                    });
        }
    }

    private void startRevealAnimation(JSONObject topCardJson, List<Map<String, Object>> bulkCards) {
        View rootView = getView();
        if (rootView == null)
            return;

        rootView.findViewById(R.id.tv_item_name).animate().alpha(0f)
                .setDuration(getResources().getInteger(R.integer.reveal_ui_fade_ms)).start();
        rootView.findViewById(R.id.tv_item_info).animate().alpha(0f)
                .setDuration(getResources().getInteger(R.integer.reveal_ui_fade_ms)).start();
        rootView.findViewById(R.id.tv_item_qty).animate().alpha(0f)
                .setDuration(getResources().getInteger(R.integer.reveal_ui_fade_ms)).start();
        rootView.findViewById(R.id.ll_buttons).animate().alpha(0f)
                .setDuration(getResources().getInteger(R.integer.reveal_ui_fade_ms)).start();
        rootView.findViewById(R.id.btn_back).animate().alpha(0f)
                .setDuration(getResources().getInteger(R.integer.reveal_ui_fade_ms)).start();

        MaterialCardView cardItem = rootView.findViewById(R.id.card_item);
        if (floatingAnim != null)
            floatingAnim.cancel();
        float introScale = getPercent(R.integer.reveal_card_intro_scale_percent);
        cardItem.animate()
                .scaleX(introScale).scaleY(introScale).translationY(0f)
                .setDuration(getResources().getInteger(R.integer.reveal_card_scale_ms))
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> runPhase2Animation(topCardJson, bulkCards))
                .start();
    }

    private void showLoadingOverlay() {
        View rootView = getView();
        if (!(rootView instanceof ViewGroup) || requireContext() == null)
            return;
        ViewGroup root = (ViewGroup) rootView;
        hideLoadingOverlay(false);

        loadingAnimView = new com.airbnb.lottie.LottieAnimationView(requireContext());
        loadingAnimView.setAnimation(R.raw.loading);
        loadingAnimView.setRepeatCount(com.airbnb.lottie.LottieDrawable.INFINITE);
        loadingAnimView.playAnimation();
        loadingAnimView.setAlpha(0f);

        int loadingSize = (int) getResources().getDimension(R.dimen.spacing_120dp);
        ConstraintLayout.LayoutParams lp = new ConstraintLayout.LayoutParams(loadingSize, loadingSize);
        lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
        lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
        lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        lp.verticalBias = 0.45f;

        loadingAnimView.setElevation(getResources().getDimension(R.dimen.reveal_loading_overlay_elevation));
        root.addView(loadingAnimView, lp);
        loadingAnimView.animate().alpha(1f).setDuration(getResources().getInteger(R.integer.reveal_loading_fade_ms))
                .start();
    }

    private void hideLoadingOverlay(boolean immediate) {
        hideLoadingOverlay(immediate, null);
    }

    private void hideLoadingOverlay(boolean immediate, @Nullable Runnable endAction) {
        if (loadingAnimView == null) {
            if (endAction != null)
                endAction.run();
            return;
        }
        if (immediate) {
            ViewGroup parent = (ViewGroup) loadingAnimView.getParent();
            if (parent != null)
                parent.removeView(loadingAnimView);
            loadingAnimView = null;
            if (endAction != null)
                endAction.run();
            return;
        }
        loadingAnimView.animate()
                .alpha(0f)
                .setDuration(getResources().getInteger(R.integer.reveal_loading_fade_ms))
                .withEndAction(() -> {
                    if (loadingAnimView != null) {
                        ViewGroup parent = (ViewGroup) loadingAnimView.getParent();
                        if (parent != null)
                            parent.removeView(loadingAnimView);
                        loadingAnimView = null;
                    }
                    if (endAction != null)
                        endAction.run();
                }).start();
    }

    private void setActionButtonsEnabled(boolean enabled) {
        View rootView = getView();
        if (rootView == null)
            return;
        rootView.findViewById(R.id.btn_open_one).setEnabled(enabled);
        rootView.findViewById(R.id.btn_open_all).setEnabled(enabled);
        rootView.findViewById(R.id.btn_back).setEnabled(enabled);
    }

    private void runPhase2Animation(JSONObject topCardJson, List<Map<String, Object>> bulkCards) {
        MaterialCardView cardItem = getView().findViewById(R.id.card_item);
        View lightLayer = getView().findViewById(R.id.view_pack_flash_overlay);
        ImageView ivItemImage = getView().findViewById(R.id.iv_item_image);

        int tier = getCardTier(topCardJson.optString(KEY_CARD_CLASS, "Welcome"));
        int tierColor = getAuraColorForTier(tier);

        // 1. Rung nhẹ Pack
        float shakeMild = getResources().getDimension(R.dimen.reveal_shake_mild);
        float shakeMildRepeat1 = shakeMild * getPercent(R.integer.reveal_phase2_shake_dampen_60_percent);
        float shakeMildRepeat2 = shakeMild * getPercent(R.integer.reveal_phase2_shake_dampen_30_percent);
        ObjectAnimator shake1Clean = ObjectAnimator.ofFloat(cardItem, "translationX", 0f, shakeMild, -shakeMild,
                shakeMildRepeat1, -shakeMildRepeat1, shakeMildRepeat2, -shakeMildRepeat2, 0f);
        shake1Clean.setDuration(getResources().getInteger(R.integer.reveal_phase2_shake_mild_ms));
        shake1Clean.setRepeatCount(getResources().getInteger(R.integer.reveal_phase2_shake_mild_repeat));
        shake1Clean.start();

        // 2. Tụ sáng ngầm (Blurry Metallic Effect)
        GradientDrawable metallicBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {
                androidx.core.content.ContextCompat.getColor(requireContext(), R.color.reveal_metallic_1),
                androidx.core.content.ContextCompat.getColor(requireContext(), R.color.reveal_metallic_2),
                androidx.core.content.ContextCompat.getColor(requireContext(), R.color.reveal_metallic_3),
                androidx.core.content.ContextCompat.getColor(requireContext(), R.color.reveal_metallic_4)
        });
        lightLayer.setBackground(metallicBg);
        lightLayer.animate().alpha(getPercent(R.integer.reveal_phase2_overlay_alpha_percent))
                .setDuration(getResources().getInteger(R.integer.reveal_phase2_metallic_ms)).withEndAction(() -> {

                    // 3. Flash mạnh và Rung dữ dội
                    ObjectAnimator colorAnim = ObjectAnimator.ofArgb(lightLayer, "backgroundColor", Color.WHITE,
                            tierColor);
                    colorAnim.setDuration(getResources().getInteger(R.integer.reveal_phase2_flash_ms));
                    colorAnim.start();

                    float shakeIntense = getResources().getDimension(R.dimen.reveal_shake_intense);
                    float shakeIntenseRepeat1 = shakeIntense
                            * getPercent(R.integer.reveal_phase2_shake_dampen_60_percent);
                    float shakeIntenseRepeat2 = shakeIntense
                            * getPercent(R.integer.reveal_phase2_shake_dampen_30_percent);
                    ObjectAnimator shake2 = ObjectAnimator.ofFloat(cardItem, "translationX", 0f, -shakeIntense,
                            shakeIntense, -shakeIntenseRepeat1, shakeIntenseRepeat1, -shakeIntenseRepeat2,
                            shakeIntenseRepeat2, 0f);
                    shake2.setDuration(getResources().getInteger(R.integer.reveal_phase2_shake_intense_ms));
                    shake2.setRepeatCount(getResources().getInteger(R.integer.reveal_phase2_shake_intense_repeat));
                    shake2.start();

                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        // THAY ĐỔI RUỘT CARD THÀNH OBJET CARD
                        String imageUrl = topCardJson.optString(KEY_FRONT_IMAGE, "");
                        if (!imageUrl.isEmpty())
                            Glide.with(this).load(imageUrl).into(ivItemImage);

                        // Tạo mặt sau (Back Image) cho FLIP 3D
                        String backImageUrl = topCardJson.optString(KEY_BACK_IMAGE, "");
                        if (backImageView == null) {
                            backImageView = new ImageView(requireContext());
                            backImageView.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
                            backImageView.setScaleType(ImageView.ScaleType.FIT_XY);
                            backImageView.setScaleX(-1f);
                            backImageView.setVisibility(View.GONE);
                            cardItem.addView(backImageView);
                        }
                        if (!backImageUrl.isEmpty() && requireContext() != null) {
                            Glide.with(this).load(backImageUrl).into(backImageView);
                        }

                        buildPremiumRevealEffects(cardItem, topCardJson, tierColor);

                        // Xóa bỏ lớp flash overlay sau khi đã lật thẻ để tránh ám màu (Fix lỗi ảnh 1)
                        if (lightLayer != null) {
                            lightLayer.animate().alpha(0f).setDuration(400)
                                    .withEndAction(() -> lightLayer.setVisibility(View.GONE)).start();
                        }

                        // SYNC GLOW STATE IMMEDIATELY (Đồng bộ ngay lập tức để tránh 'trễ' hiệu ứng)
                        syncGlowToCard(cardItem);

                        ivItemImage.setAlpha(0f);
                        ivItemImage.setScaleX(0.8f);
                        ivItemImage.setScaleY(0.8f);
                        ivItemImage.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(500)
                                .setInterpolator(new OvershootInterpolator())
                                .setUpdateListener(animation -> syncGlowToCard(cardItem))
                                .start();

                        // Kích hoạt Thiên Hà Hạt
                        createChaosParticles(tierColor, cardItem);
                        setupFlipGesture(cardItem);

                        long summaryDelayMs = getSummaryRevealDelayMs();
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            showBulkSummaryResult(bulkCards, topCardJson);
                        }, summaryDelayMs);
                    }, getResources().getInteger(R.integer.reveal_phase2_explosion_delay_ms));
                }).start();
    }

    private void buildPremiumRevealEffects(MaterialCardView cardItem, JSONObject topCardJson, int forcedGlowColor) {
        if (requireContext() == null || topCardJson == null)
            return;

        Objet heroObjet = new Objet(0,
                topCardJson.optString(KEY_COLLECTION_ID),
                topCardJson.optString(KEY_FRONT_IMAGE),
                topCardJson.optInt(KEY_LEVEL, 1),
                0,
                topCardJson.optInt(KEY_UPGRADE_LEVEL, 1));
        heroObjet.setMember(topCardJson.optString(KEY_MEMBER));
        heroObjet.setSeason(topCardJson.optString(KEY_SEASON));
        heroObjet.setBackgroundColor(topCardJson.optString(KEY_BACKGROUND_COLOR));
        heroObjet.setTextColor(topCardJson.optString(KEY_TEXT_COLOR));

        this.shimmerView = getView().findViewById(R.id.view_card_shimmer);
        Integer glowColorArg = (forcedGlowColor == 0) ? null : forcedGlowColor;
        com.vn.jet.mosco.utils.CardEffectHelper.apply(cardItem, this.shimmerView, heroObjet, true, true, glowColorArg);

        // SYNC CAMERA DISTANCE & STATE IMMEDIATELY
        cardItem.post(() -> {
            float density = getResources().getDisplayMetrics().density;
            cardItem.setCameraDistance(8000 * density);
            syncGlowToCard(cardItem);
            if (currentGlowView != null) {
                currentGlowView.setCameraDistance(8000 * density);
            }
        });
    }

    private void setupFlipGesture(MaterialCardView cardItem) {
        float scale = getResources().getDisplayMetrics().density;
        cardItem.setCameraDistance(8000 * scale);
        if (currentGlowView != null)
            currentGlowView.setCameraDistance(8000 * scale);

        final float[] initialTouchX = { 0f };
        final float[] startRotation = { 0f };
        final boolean[] isFlipped = { false };
        final ValueAnimator[] snapAnim = { null };
        ImageView ivFront = getView().findViewById(R.id.iv_item_image);

        cardItem.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (snapAnim[0] != null && snapAnim[0].isRunning())
                        snapAnim[0].cancel();
                    initialTouchX[0] = event.getRawX();
                    startRotation[0] = cardItem.getRotationY();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float diffX = event.getRawX() - initialTouchX[0];
                    float newRot = startRotation[0] + (diffX / 5f); // Sensitivity fixed

                    cardItem.setRotationY(newRot);
                    syncGlowToCard(cardItem);

                    float norm = Math.abs(newRot % 360);
                    boolean shouldShowBack = (norm > 90 && norm < 270);
                    if (shouldShowBack != isFlipped[0]) {
                        isFlipped[0] = shouldShowBack;
                        updateVisibilitySync(ivFront, shouldShowBack);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    float currentRot = cardItem.getRotationY();
                    float currentNorm = Math.abs(currentRot % 360);
                    float nearestAngle = (currentNorm <= 90 || currentNorm >= 270)
                            ? Math.round(currentRot / 360f) * 360f
                            : Math.round((currentRot - 180f) / 360f) * 360f + 180f;

                    snapAnim[0] = ValueAnimator.ofFloat(currentRot, nearestAngle);
                    snapAnim[0].setDuration(400);
                    snapAnim[0].setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
                    snapAnim[0].addUpdateListener(anim -> {
                        float val = (float) anim.getAnimatedValue();
                        updateRotationSync(cardItem, val);

                        float snapNorm = Math.abs(val % 360);
                        boolean snapShouldShowBack = (snapNorm > 90 && snapNorm < 270);
                        if (snapShouldShowBack != isFlipped[0]) {
                            isFlipped[0] = snapShouldShowBack;
                            updateVisibilitySync(ivFront, snapShouldShowBack);
                        }
                    });
                    snapAnim[0].start();
                    return true;
            }
            return false;
        });
    }

    private void updateRotationSync(MaterialCardView card, float rot) {
        card.setRotationY(rot);
        syncGlowToCard(card);
    }

    private void syncGlowToCard(MaterialCardView card) {
        if (card == null)
            return;
        View glow = (View) card.getTag(R.id.view_progress_fill);
        if (glow != null) {
            glow.setRotationY(card.getRotationY());
            glow.setRotationX(card.getRotationX());
            glow.setTranslationX(card.getTranslationX());
            glow.setTranslationY(card.getTranslationY());
            glow.setScaleX(card.getScaleX());
            glow.setScaleY(card.getScaleY());
            currentGlowView = glow;
        }
    }

    private void updateVisibilitySync(ImageView ivFront, boolean showBack) {
        if (!showBack) {
            ivFront.setVisibility(View.VISIBLE);
            if (backImageView != null)
                backImageView.setVisibility(View.GONE);
            if (shimmerView != null)
                shimmerView.setVisibility(View.VISIBLE);
        } else {
            ivFront.setVisibility(View.GONE);
            if (backImageView != null)
                backImageView.setVisibility(View.VISIBLE);
            if (shimmerView != null)
                shimmerView.setVisibility(View.GONE);
        }
    }

    private void createChaosParticles(int color, View cardItem) {
        ViewGroup root = (ViewGroup) getView();
        if (root == null)
            return;

        if (activeParticleView != null) {
            activeParticleView.stopAndRemove();
            activeParticleView = null;
        }

        ChaosParticleView particleView = new ChaosParticleView(requireContext(), buildParticleConfig());
        activeParticleView = particleView;
        // Fix z-index & layouts
        ConstraintLayout.LayoutParams lp = new ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.MATCH_PARENT);
        // Đặt particle phía sau object để đúng cảm giác nổ hậu cảnh.
        int cardIndex = root.indexOfChild(cardItem);
        if (cardIndex > 0) {
            root.addView(particleView, cardIndex, lp);
        } else {
            root.addView(particleView, 0, lp);
        }
        particleView.setElevation(Math.max(0f, cardItem.getElevation() - dpToPx(2)));

        // Đợi layout ổn định rồi mới map toạ độ card -> particle layer để tránh lệch
        // tâm nổ.
        particleView.post(() -> {
            if (particleView.getParent() == null || cardItem.getParent() == null)
                return;
            int[] cardLoc = new int[2];
            int[] rootLoc = new int[2];
            cardItem.getLocationOnScreen(cardLoc);
            root.getLocationOnScreen(rootLoc);
            float cx = (cardLoc[0] - rootLoc[0]) + (cardItem.getWidth() / 2f);
            float cy = (cardLoc[1] - rootLoc[1]) + (cardItem.getHeight() / 2f);
            particleView.startExplosion(color, cx, cy);
        });
    }

    private ParticleConfig buildParticleConfig() {
        ParticleConfig cfg = new ParticleConfig();
        final float slowDownFactor = 1.6f; // Slow particle explosion by 60%
        cfg.particleCount = getResources().getInteger(R.integer.reveal_particle_count);
        cfg.explosionPhaseMs = (long) (getResources().getInteger(R.integer.reveal_particle_explosion_phase_ms)
                * slowDownFactor);
        cfg.ovalBurstX = getResources().getInteger(R.integer.reveal_particle_oval_burst_x_percent) / 100f;
        cfg.ovalBurstY = getResources().getInteger(R.integer.reveal_particle_oval_burst_y_percent) / 100f;
        cfg.regionMarginXRatio = getResources().getInteger(R.integer.reveal_particle_region_margin_x_percent) / 100f;
        cfg.regionMarginYRatio = getResources().getInteger(R.integer.reveal_particle_region_margin_y_percent) / 100f;
        cfg.touchRadius = getResources().getInteger(R.integer.reveal_particle_touch_radius);
        cfg.maxTouchPush = getResources().getInteger(R.integer.reveal_particle_max_touch_push_percent) / 100f;
        cfg.homePull = getResources().getInteger(R.integer.reveal_particle_home_pull_permille) / 1000f;
        cfg.targetPull = getResources().getInteger(R.integer.reveal_particle_target_pull_permille) / 1000f;
        cfg.damping = getResources().getInteger(R.integer.reveal_particle_damping_permille) / 1000f;
        cfg.edgeBounce = getResources().getInteger(R.integer.reveal_particle_edge_bounce_percent) / 100f;
        cfg.maxSpeed = getResources().getInteger(R.integer.reveal_particle_max_speed_percent) / 100f;
        cfg.initialSpeedScale = getResources().getInteger(R.integer.reveal_particle_initial_speed_percent) / 100f;
        cfg.spreadPull = getResources().getInteger(R.integer.reveal_particle_spread_pull_permille) / 1000f;
        cfg.spreadSwirl = getResources().getInteger(R.integer.reveal_particle_spread_swirl_percent) / 100f;
        return cfg;
    }

    private long getSummaryRevealDelayMs() {
        long fallbackDelay = getResources().getInteger(R.integer.reveal_phase2_summary_delay_ms);
        long particlePhaseMs = buildParticleConfig().explosionPhaseMs;
        long settleMs = 120L;
        return Math.max(fallbackDelay, particlePhaseMs + settleMs);
    }

    private static class ParticleConfig {
        int particleCount;
        long explosionPhaseMs;
        float ovalBurstX;
        float ovalBurstY;
        float regionMarginXRatio;
        float regionMarginYRatio;
        float touchRadius;
        float maxTouchPush;
        float homePull;
        float targetPull;
        float damping;
        float edgeBounce;
        float maxSpeed;
        float initialSpeedScale;
        float spreadPull;
        float spreadSwirl;
    }

    private void showBulkSummaryResult(List<Map<String, Object>> bulkCards, JSONObject topCardJson) {
        MaterialCardView cardItem = getView().findViewById(R.id.card_item);
        TextView tvTitle = getView().findViewById(R.id.tv_item_name);
        com.vn.jet.mosco.widget.MoscoButton btnDone = getView().findViewById(R.id.btn_done);
        LinearLayout llButtons = getView().findViewById(R.id.ll_buttons);

        // --- 1. Micro-lift card: giữ near-center, tránh jump theo trục Y ---
        int moveUpDistance = (int) getResources().getDimension(R.dimen.reveal_summary_micro_lift);
        summaryCardBaseTranslationY = -moveUpDistance;
        cardItem.animate()
                .translationY(summaryCardBaseTranslationY)
                .scaleX(getPercent(R.integer.reveal_card_summary_scale_percent))
                .scaleY(getPercent(R.integer.reveal_card_summary_scale_percent))
                .setDuration(getResources().getInteger(R.integer.reveal_summary_card_move_ms))
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setUpdateListener(animation -> syncGlowToCard(cardItem))
                .start();
        if (currentGlowView != null) {
            currentGlowView.animate()
                    .translationY(summaryCardBaseTranslationY)
                    .scaleX(getPercent(R.integer.reveal_card_summary_scale_percent))
                    .scaleY(getPercent(R.integer.reveal_card_summary_scale_percent))
                    .setDuration(getResources().getInteger(R.integer.reveal_summary_card_move_ms))
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        }
        if (activeParticleView != null) {
            activeParticleView.animate()
                    .translationY(summaryCardBaseTranslationY
                            * getPercent(R.integer.reveal_parallax_particle_y_follow_percent))
                    .setDuration(getResources().getInteger(R.integer.reveal_summary_card_move_ms))
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        }

        // --- 2. Premium title: cleaner typography + safe top spacing ---
        String headline = getString(R.string.reveal_summary_headline);
        String subtitle = getString(R.string.reveal_summary_subtitle, bulkCards.size());
        SpannableStringBuilder titleBuilder = new SpannableStringBuilder(headline + "\n" + subtitle);
        titleBuilder.setSpan(new StyleSpan(Typeface.BOLD), 0, headline.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        titleBuilder.setSpan(new RelativeSizeSpan(getPercent(R.integer.reveal_title_headline_size_percent)), 0,
                headline.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        titleBuilder.setSpan(
                new ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.palette_gold_medium)), 0,
                headline.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int subtitleStart = headline.length() + 1;
        titleBuilder.setSpan(new RelativeSizeSpan(getPercent(R.integer.reveal_title_subtitle_size_percent)),
                subtitleStart, titleBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        titleBuilder.setSpan(
                new ForegroundColorSpan(androidx.core.content.ContextCompat.getColor(requireContext(),
                        R.color.lg_text_secondary)),
                subtitleStart, titleBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tvTitle.setText(titleBuilder);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setLineSpacing(getResources().getDimension(R.dimen.reveal_title_line_spacing_extra), 1.0f);
        tvTitle.setShadowLayer(getResources().getDimension(R.dimen.reveal_title_shadow_radius), 0f,
                getResources().getDimension(R.dimen.reveal_title_shadow_dy),
                ContextCompat.getColor(requireContext(), R.color.mosco_card_stroke_alpha_70));
        tvTitle.setPadding(tvTitle.getPaddingLeft(),
                (int) getResources().getDimension(R.dimen.reveal_title_top_padding), tvTitle.getPaddingRight(),
                tvTitle.getPaddingBottom());
        tvTitle.animate().alpha(getPercent(R.integer.reveal_alpha_visible_percent))
                .translationY(getResources().getDimension(R.dimen.reveal_title_translation_y))
                .setDuration(getResources().getInteger(R.integer.reveal_summary_title_fade_ms)).start();
        tvTitle.setScaleX(getPercent(R.integer.reveal_title_initial_scale_percent));
        tvTitle.setScaleY(getPercent(R.integer.reveal_title_initial_scale_percent));
        tvTitle.animate()
                .scaleX(getPercent(R.integer.reveal_title_peak_scale_percent))
                .scaleY(getPercent(R.integer.reveal_title_peak_scale_percent))
                .setDuration(getResources().getInteger(R.integer.reveal_summary_title_pop_up_ms))
                .setInterpolator(new OvershootInterpolator(1.5f))
                .withEndAction(() -> tvTitle.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(getResources().getInteger(R.integer.reveal_summary_title_pop_down_ms))
                        .start())
                .start();

        // --- 3. Summary Glass Container ---
        LinearLayout summaryContainer = new LinearLayout(requireContext());
        summaryContainer.setOrientation(LinearLayout.VERTICAL);
        summaryContainer.setAlpha(0f);

        GradientDrawable glassBg = new GradientDrawable();
        glassBg.setColor(ContextCompat.getColor(requireContext(), R.color.mosco_overlay_dark));
        glassBg.setCornerRadius(getResources().getDimension(R.dimen.reveal_summary_corner_radius));
        glassBg.setStroke((int) getResources().getDimension(R.dimen.reveal_summary_stroke_width),
                ContextCompat.getColor(requireContext(), R.color.mosco_white_20));
        summaryContainer.setBackground(glassBg);

        // Header
        TextView tvHeader = new TextView(requireContext());
        tvHeader.setText(getString(R.string.reveal_summary_title_collapse));
        tvHeader.setTextColor(Color.WHITE);
        tvHeader.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.reveal_summary_header_text_size));
        tvHeader.setGravity(Gravity.CENTER);
        int headerPaddingHorizontal = (int) getResources()
                .getDimension(R.dimen.reveal_summary_header_padding_horizontal);
        tvHeader.setPadding(
                headerPaddingHorizontal,
                (int) getResources().getDimension(R.dimen.reveal_summary_header_padding_top),
                headerPaddingHorizontal,
                (int) getResources().getDimension(R.dimen.reveal_summary_header_padding_bottom));
        tvHeader.setTypeface(Typeface.DEFAULT_BOLD);
        tvHeader.setCompoundDrawablePadding((int) getResources().getDimension(R.dimen.spacing_8dp));
        tvHeader.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_down, 0);
        summaryContainer.addView(tvHeader);

        View div = new View(requireContext());
        div.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.mosco_white_15));
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                (int) getResources().getDimension(R.dimen.reveal_summary_divider_width),
                (int) getResources().getDimension(R.dimen.reveal_summary_divider_height));
        divLp.gravity = Gravity.CENTER;
        div.setLayoutParams(divLp);
        summaryContainer.addView(div);

        // Scrollable List
        android.widget.ScrollView scrollView = new android.widget.ScrollView(requireContext());
        scrollView.setVerticalScrollBarEnabled(false);
        int expandedHeight = (int) getResources().getDimension(R.dimen.reveal_summary_list_height);
        int collapsedHeight = (int) getResources().getDimension(R.dimen.reveal_summary_list_collapsed_height);
        summaryContainer.addView(scrollView, new LinearLayout.LayoutParams(-1, expandedHeight));

        LinearLayout listLayout = new LinearLayout(requireContext());
        listLayout.setOrientation(LinearLayout.VERTICAL);
        listLayout.setPadding(
                (int) getResources().getDimension(R.dimen.reveal_summary_list_padding_horizontal),
                (int) getResources().getDimension(R.dimen.reveal_summary_list_padding_top),
                (int) getResources().getDimension(R.dimen.reveal_summary_list_padding_horizontal),
                (int) getResources().getDimension(R.dimen.reveal_summary_list_padding_bottom));
        scrollView.addView(listLayout);

        // Data processing for list
        Map<String, Integer> cardCounts = new HashMap<>();
        Map<String, Integer> cardTiers = new HashMap<>();
        for (Map<String, Object> roll : bulkCards) {
            Map<String, Object> data = (Map<String, Object>) roll.get("cardData");
            if (data != null) {
                String name = String.valueOf(data.get("collectionId"));
                String cardClass = String.valueOf(data.get("class"));
                cardCounts.put(name, cardCounts.getOrDefault(name, 0) + 1);
                cardTiers.put(name, getCardTier(cardClass));
            }
        }

        for (Map.Entry<String, Integer> entry : cardCounts.entrySet()) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, (int) getResources().getDimension(R.dimen.reveal_summary_row_padding_vertical), 0,
                    (int) getResources().getDimension(R.dimen.reveal_summary_row_padding_vertical));
            row.setGravity(Gravity.CENTER_VERTICAL);

            View colorIndicator = new View(requireContext());
            int targetTier = cardTiers.getOrDefault(entry.getKey(), 1);
            GradientDrawable indicatorBg = new GradientDrawable();
            indicatorBg.setShape(GradientDrawable.OVAL);
            if (targetTier == 4) {
                indicatorBg.setColors(new int[] {
                        androidx.core.content.ContextCompat.getColor(requireContext(), R.color.reveal_tier_4_primary),
                        androidx.core.content.ContextCompat.getColor(requireContext(), R.color.reveal_tier_4_secondary)
                });
            } else if (targetTier == 3) {
                indicatorBg.setColors(new int[] {
                        androidx.core.content.ContextCompat.getColor(requireContext(), R.color.white),
                        androidx.core.content.ContextCompat.getColor(requireContext(), R.color.reveal_tier_3_secondary)
                });
            } else if (targetTier == 2) {
                indicatorBg.setColor(
                        androidx.core.content.ContextCompat.getColor(requireContext(), R.color.reveal_tier_2));
            } else {
                indicatorBg.setColor(
                        androidx.core.content.ContextCompat.getColor(requireContext(), R.color.reveal_tier_1));
            }

            colorIndicator.setBackground(indicatorBg);
            colorIndicator.setLayoutParams(new LinearLayout.LayoutParams(
                    (int) getResources().getDimension(R.dimen.reveal_summary_indicator_size),
                    (int) getResources().getDimension(R.dimen.reveal_summary_indicator_size)));

            TextView tvName = new TextView(requireContext());
            tvName.setText(entry.getKey());
            tvName.setTextColor(Color.WHITE);
            tvName.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    getResources().getDimension(R.dimen.reveal_summary_name_text_size));
            tvName.setPadding((int) getResources().getDimension(R.dimen.reveal_summary_name_padding_start), 0,
                    (int) getResources().getDimension(R.dimen.reveal_summary_name_padding_end), 0);
            tvName.setTypeface(Typeface.SANS_SERIF, Typeface.NORMAL);
            tvName.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.0f));

            TextView tvQty = new TextView(requireContext());
            tvQty.setText(getString(R.string.format_qty_int, entry.getValue()));
            tvQty.setTextColor(
                    androidx.core.content.ContextCompat.getColor(requireContext(), R.color.reveal_summary_qty_text));
            tvQty.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    getResources().getDimension(R.dimen.reveal_summary_qty_text_size));
            tvQty.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);

            row.addView(colorIndicator);
            row.addView(tvName);
            row.addView(tvQty);
            listLayout.addView(row);
        }

        // --- 4. Style the "Collect All" button ---
        llButtons.setAlpha(1f); // Đảm bảo hiện lại layout nút đã bị ẩn lúc nãy
        View btnOpenOne = getView().findViewById(R.id.btn_open_one);
        View btnOpenAll = getView().findViewById(R.id.btn_open_all);
        if (btnOpenOne != null)
            btnOpenOne.setVisibility(View.GONE);
        if (btnOpenAll != null)
            btnOpenAll.setVisibility(View.GONE);
        btnDone.setVisibility(View.VISIBLE);
        btnDone.setText(getString(R.string.reveal_action_collect_all, bulkCards.size()));
        // MoscoButton tự quản lý style/radius qua XML attrs (moscoStyle="primary")
        btnDone.setAlpha(getPercent(R.integer.reveal_overlay_alpha_hidden_percent));
        btnDone.animate().alpha(getPercent(R.integer.reveal_alpha_visible_percent))
                .setDuration(getResources().getInteger(R.integer.reveal_summary_button_fade_ms)).start();

        llButtons.bringToFront();
        btnDone.setOnClickListener(v -> goBack());

        // Add container to root
        ViewGroup root = (ViewGroup) getView();
        ConstraintLayout.LayoutParams lp = new ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.WRAP_CONTENT);
        lp.topToBottom = cardItem.getId();
        // Cho list chạy "dưới" FAB theo trục Z để tiết kiệm diện tích hiển thị card
        // trung tâm.
        lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
        lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
        int negMargin = (int) getResources().getDimension(R.dimen.reveal_summary_negative_margin);
        int summarySideMargin = (int) getResources().getDimension(R.dimen.reveal_summary_margin_side);
        lp.setMargins(summarySideMargin, -negMargin, summarySideMargin, 0);

        root.addView(summaryContainer, lp);
        summaryContainer.animate()
                .alpha(getPercent(R.integer.reveal_alpha_visible_percent))
                .translationY(-getResources().getDimension(R.dimen.reveal_summary_translation_y))
                .setDuration(getResources().getInteger(R.integer.reveal_summary_container_fade_ms))
                .start();

        // Footer spacer lớn để item cuối không bị FAB che khi cuộn tới cuối danh sách.
        View listFooterSpacer = new View(requireContext());
        listFooterSpacer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) getResources().getDimension(R.dimen.reveal_summary_footer_spacer_height)));
        TextView footerMarker = new TextView(requireContext());
        footerMarker.setText(getString(R.string.reveal_summary_footer_marker));
        footerMarker.setGravity(Gravity.CENTER);
        footerMarker.setTextColor(
                androidx.core.content.ContextCompat.getColor(requireContext(), R.color.lg_text_secondary));
        footerMarker.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.reveal_summary_footer_marker_text_size));
        footerMarker.setPadding(0, (int) getResources().getDimension(R.dimen.reveal_summary_footer_marker_padding_top),
                0, 0);
        listLayout.addView(footerMarker);
        listLayout.addView(listFooterSpacer);

        setupSummaryPanelInteractions(tvHeader, scrollView, expandedHeight, collapsedHeight);

        llButtons.bringToFront(); // Đảm bảo nút Collect All luôn nằm trên màng mờ Summary
    }

    private int getCardTier(String cardClass) {
        if (cardClass == null)
            return 1;
        String lower = cardClass.toLowerCase();
        if (lower.contains("unit"))
            return 4;
        if (lower.contains("premier") || lower.contains("legendary"))
            return 3;
        if (lower.contains("double") || lower.contains("special"))
            return 2;
        return 1;
    }

    private int getAuraColorForTier(int tier) {
        switch (tier) {
            case 4:
                return androidx.core.content.ContextCompat.getColor(requireContext(), R.color.reveal_tier_4_secondary);
            case 3:
                return androidx.core.content.ContextCompat.getColor(requireContext(), R.color.reveal_tier_3_secondary);
            case 2:
                return androidx.core.content.ContextCompat.getColor(requireContext(), R.color.reveal_tier_2);
            case 1:
            default:
                return androidx.core.content.ContextCompat.getColor(requireContext(), R.color.reveal_tier_1);
        }
    }

    private void applyVisualEffects(MaterialCardView cardItem, View view) {
        int primaryColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.lg_accent_primary);
        cardItem.setStrokeWidth(dpToPx(1));
        cardItem.setStrokeColor(androidx.core.graphics.ColorUtils.setAlphaComponent(primaryColor, 128));
        float cardFloatY = getResources().getDimension(R.dimen.reveal_card_float_y);
        floatingAnim = ObjectAnimator.ofFloat(cardItem, "translationY", 0f, -cardFloatY, 0f);
        floatingAnim.setDuration(getResources().getInteger(R.integer.reveal_card_float_ms));
        floatingAnim.setRepeatCount(-1);
        floatingAnim.setRepeatMode(ValueAnimator.REVERSE);
        floatingAnim.start();
    }

    private void setupParallaxDepth(MaterialCardView cardItem) {
        View rootView = getView();
        if (rootView == null)
            return;

        final float maxTilt = getResources().getInteger(R.integer.reveal_parallax_max_tilt_deg);
        final float maxCardShift = getResources().getDimension(R.dimen.reveal_parallax_card_shift);
        final float maxGlowShift = getResources().getDimension(R.dimen.reveal_parallax_glow_shift);
        final float maxParticleShift = getResources().getDimension(R.dimen.reveal_parallax_particle_shift);
        final float tiltDampenPercent = getPercent(R.integer.reveal_parallax_glow_tilt_dampen_percent);
        final float yDampenPercent = getPercent(R.integer.reveal_parallax_y_dampen_percent);
        final int settleDuration = getResources().getInteger(R.integer.reveal_parallax_settle_ms);

        if (parallaxIdleAnimator != null)
            parallaxIdleAnimator.cancel();
        parallaxIdleAnimator = ValueAnimator.ofFloat(-1f, 1f);
        parallaxIdleAnimator.setDuration(getResources().getInteger(R.integer.reveal_parallax_idle_cycle_ms));
        parallaxIdleAnimator.setInterpolator(new LinearInterpolator());
        parallaxIdleAnimator.setRepeatMode(ValueAnimator.REVERSE);
        parallaxIdleAnimator.setRepeatCount(ValueAnimator.INFINITE);
        parallaxIdleAnimator.addUpdateListener(animation -> {
            float phase = (float) animation.getAnimatedValue();
            float cardMicroX = phase * getResources().getDimension(R.dimen.reveal_parallax_idle_card_x);
            float cardMicroY = phase * getResources().getDimension(R.dimen.reveal_parallax_idle_card_y);
            cardItem.setTranslationX(cardMicroX);
            cardItem.setTranslationY(summaryCardBaseTranslationY + cardMicroY);

            if (currentGlowView != null) {
                currentGlowView.setTranslationX(cardMicroX * getPercent(R.integer.reveal_parallax_glow_follow_percent));
                currentGlowView.setTranslationY(summaryCardBaseTranslationY
                        + (cardMicroY * getPercent(R.integer.reveal_parallax_glow_follow_percent)));
            }
            if (activeParticleView != null) {
                activeParticleView
                        .setTranslationX(-cardMicroX * getPercent(R.integer.reveal_parallax_particle_follow_percent));
            }
        });
        parallaxIdleAnimator.start();

        cardItem.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (parallaxIdleAnimator != null)
                        parallaxIdleAnimator.cancel();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float centerX = cardItem.getWidth() / 2f;
                    float centerY = cardItem.getHeight() / 2f;
                    if (centerX <= 0f || centerY <= 0f)
                        return true;
                    float nx = (event.getX() - centerX) / centerX;
                    float ny = (event.getY() - centerY) / centerY;
                    nx = clampFloat(nx, -1f, 1f);
                    ny = clampFloat(ny, -1f, 1f);

                    float rotY = nx * maxTilt;
                    float rotX = -ny * maxTilt;
                    cardItem.setRotationY(rotY);
                    cardItem.setRotationX(rotX * yDampenPercent);
                    cardItem.setTranslationX(nx * maxCardShift);
                    cardItem.setTranslationY(summaryCardBaseTranslationY + (ny * maxCardShift * yDampenPercent));

                    // SYNC GLOW 1:1 WITH CARD
                    syncGlowToCard(cardItem);

                    if (activeParticleView != null) {
                        activeParticleView.setTranslationX(-nx * maxParticleShift);
                        activeParticleView.setTranslationY((summaryCardBaseTranslationY
                                * getPercent(R.integer.reveal_parallax_particle_y_follow_percent))
                                + (-ny * maxParticleShift * yDampenPercent));
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    cardItem.animate()
                            .rotationX(0f).rotationY(0f)
                            .translationX(0f).translationY(summaryCardBaseTranslationY)
                            .setDuration(settleDuration)
                            .setInterpolator(new AccelerateDecelerateInterpolator())
                            .start();
                    if (currentGlowView != null) {
                        currentGlowView.animate()
                                .rotationX(0f).rotationY(0f)
                                .translationX(0f).translationY(summaryCardBaseTranslationY)
                                .setDuration(settleDuration)
                                .setInterpolator(new AccelerateDecelerateInterpolator())
                                .start();
                    }
                    if (activeParticleView != null) {
                        activeParticleView.animate()
                                .translationX(0f)
                                .translationY(summaryCardBaseTranslationY
                                        * getPercent(R.integer.reveal_parallax_particle_y_follow_percent))
                                .setDuration(settleDuration)
                                .setInterpolator(new AccelerateDecelerateInterpolator())
                                .start();
                    }
                    if (parallaxIdleAnimator != null)
                        parallaxIdleAnimator.start();
                    return true;
            }
            return false;
        });
    }

    private void goBack() {
        if (getActivity() != null) {
            View navBar = getActivity().findViewById(R.id.bottom_navigation);
            if (navBar != null)
                navBar.setVisibility(View.VISIBLE);
            getActivity().getSupportFragmentManager().popBackStack();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (floatingAnim != null)
            floatingAnim.cancel();
        if (parallaxIdleAnimator != null)
            parallaxIdleAnimator.cancel();
        if (activeParticleView != null) {
            activeParticleView.stopAndRemove();
            activeParticleView = null;
        }
        hideLoadingOverlay(true);
    }

    private int dpToPx(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private float getPercent(int integerResId) {
        return getResources().getInteger(integerResId) / 100f;
    }

    private void setupSummaryPanelInteractions(TextView header, View panel, int expandedHeight, int collapsedHeight) {
        if (header == null || panel == null)
            return;
        final int[] currentHeight = { expandedHeight };
        final boolean[] isCollapsed = { false };
        final float[] downY = { 0f };
        final int[] startHeight = { expandedHeight };
        final boolean[] isDragging = { false };
        final int dragThresholdPx = dpToPx(getResources().getInteger(R.integer.reveal_summary_panel_drag_threshold_dp));

        header.setOnClickListener(v -> {
            int targetHeight = isCollapsed[0] ? expandedHeight : collapsedHeight;
            ValueAnimator animator = ValueAnimator.ofInt(currentHeight[0], targetHeight);
            animator.setDuration(getResources().getInteger(R.integer.reveal_summary_panel_resize_ms));
            animator.addUpdateListener(animation -> {
                int value = (int) animation.getAnimatedValue();
                ViewGroup.LayoutParams lp = panel.getLayoutParams();
                lp.height = value;
                panel.setLayoutParams(lp);
                currentHeight[0] = value;
            });
            animator.start();
            isCollapsed[0] = !isCollapsed[0];
            header.setText(getString(
                    isCollapsed[0] ? R.string.reveal_summary_title_expand : R.string.reveal_summary_title_collapse));
            header.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    0, 0, isCollapsed[0] ? R.drawable.ic_arrow_up : R.drawable.ic_arrow_down, 0);
        });

        header.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downY[0] = event.getRawY();
                    startHeight[0] = currentHeight[0];
                    isDragging[0] = false;
                    return false;
                case MotionEvent.ACTION_MOVE:
                    float deltaY = downY[0] - event.getRawY();
                    if (Math.abs(deltaY) > dragThresholdPx) {
                        isDragging[0] = true;
                    }
                    if (isDragging[0]) {
                        int target = clamp(startHeight[0] + Math.round(deltaY), collapsedHeight, expandedHeight);
                        ViewGroup.LayoutParams lp = panel.getLayoutParams();
                        lp.height = target;
                        panel.setLayoutParams(lp);
                        currentHeight[0] = target;
                        return true;
                    }
                    return false;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isDragging[0]) {
                        int middle = collapsedHeight + ((expandedHeight - collapsedHeight) / 2);
                        int snapTarget = currentHeight[0] >= middle ? expandedHeight : collapsedHeight;
                        ValueAnimator settle = ValueAnimator.ofInt(currentHeight[0], snapTarget);
                        settle.setDuration(getResources().getInteger(R.integer.reveal_summary_panel_resize_ms));
                        settle.addUpdateListener(animation -> {
                            int value = (int) animation.getAnimatedValue();
                            ViewGroup.LayoutParams lp = panel.getLayoutParams();
                            lp.height = value;
                            panel.setLayoutParams(lp);
                            currentHeight[0] = value;
                        });
                        settle.start();
                        isCollapsed[0] = (snapTarget == collapsedHeight);
                        header.setText(getString(isCollapsed[0] ? R.string.reveal_summary_title_expand
                                : R.string.reveal_summary_title_collapse));
                        header.setCompoundDrawablesRelativeWithIntrinsicBounds(
                                0, 0, isCollapsed[0] ? R.drawable.ic_arrow_up : R.drawable.ic_arrow_down, 0);
                        return true;
                    }
                    return false;
                default:
                    return false;
            }
        });
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    // =========================================================================
    // INNER CLASSES (GLOW & PARTICLES)
    // =========================================================================

    public static class ChaosParticleView extends View {
        private final ParticleConfig config;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<Particle> particles = new ArrayList<>();
        private int explodeColor = Color.WHITE;
        private boolean isExploding = false;
        private float centerX, centerY;

        private float touchX = -1000, touchY = -1000;
        private boolean isTouching = false;
        private long startedAtMs = 0L;
        private final float[] touchImpulseX = new float[] { 0f };
        private final float[] touchImpulseY = new float[] { 0f };
        private float regionLeft = 0f;
        private float regionTop = 0f;
        private float regionRight = 0f;
        private float regionBottom = 0f;
        private int lastWidth = -1;
        private int lastHeight = -1;
        private long frameTick = 0L;

        public ChaosParticleView(Context context, ParticleConfig config) {
            super(context);
            this.config = config;
        }

        public void startExplosion(int color, float cx, float cy) {
            this.explodeColor = color;
            this.centerX = cx;
            this.centerY = cy;
            this.startedAtMs = android.os.SystemClock.uptimeMillis();
            setAlpha(1f);
            setBackgroundColor(Color.TRANSPARENT);

            paint.setColor(color);
            paint.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT);

            particles.clear();
            for (int i = 0; i < config.particleCount; i++) {
                particles.add(new Particle(i, config.particleCount, cx, cy));
            }
            isExploding = true;
            invalidate();
        }

        public void stopAndRemove() {
            isExploding = false;
            ViewGroup parent = (ViewGroup) getParent();
            if (parent != null)
                parent.removeView(this);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (!isExploding)
                return false;

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    touchX = event.getX();
                    touchY = event.getY();
                    isTouching = true;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    touchX = event.getX();
                    touchY = event.getY();
                    isTouching = true;
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isTouching = false;
                    ripplePushFields();
                    return true;
            }
            return false;
        }

        private void ripplePushFields() {
            for (Particle p : particles) {
                float dx = p.x - touchX;
                float dy = p.y - touchY;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist > 0) {
                    p.interactionOffsetX += (dx / dist) * 16f;
                    p.interactionOffsetY += (dy / dist) * 16f;
                }
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (!isExploding)
                return;
            long elapsed = android.os.SystemClock.uptimeMillis() - startedAtMs;
            frameTick++;
            touchImpulseX[0] = isTouching ? touchX : -1000f;
            touchImpulseY[0] = isTouching ? touchY : -1000f;
            final int w = getWidth();
            final int h = getHeight();
            ensureRegionBounds(w, h);

            for (Particle p : particles) {
                p.update(
                        centerX, centerY, elapsed, touchImpulseX[0], touchImpulseY[0], isTouching,
                        regionLeft, regionTop, regionRight, regionBottom, frameTick);
                paint.setAlpha(Math.max(72, p.alpha));
                canvas.drawCircle(p.x, p.y, p.radius, paint);
            }

            postInvalidateOnAnimation();
        }

        private void ensureRegionBounds(int w, int h) {
            if (w == lastWidth && h == lastHeight)
                return;
            lastWidth = w;
            lastHeight = h;
            regionLeft = w * config.regionMarginXRatio;
            regionRight = w * (1f - config.regionMarginXRatio);
            regionTop = h * config.regionMarginYRatio;
            regionBottom = h * (1f - config.regionMarginYRatio);
        }

        class Particle {
            float x, y, vx, vy;
            float radius;
            int alpha = 255;
            float interactionOffsetX = 0f;
            float interactionOffsetY = 0f;
            float wanderSeedX;
            float wanderSeedY;
            float driftX;
            float driftY;
            float homeX;
            float homeY;
            float targetX;
            float targetY;
            float launchAngle;
            float launchRadius;
            int retargetStep;
            long nextRetargetTick = 0L;

            Particle(int index, int total, float startX, float startY) {
                this.x = startX;
                this.y = startY;
                float angle = (float) (Math.random() * 2 * Math.PI);
                launchAngle = angle;
                // sqrt(random) => phân bố đều diện tích bên trong ellipse (đặc ruột), không bị
                // dồn tâm.
                launchRadius = (float) Math.sqrt(Math.random());
                float speed = (float) (Math.random() * 35 + 15) * config.initialSpeedScale;
                vx = (float) Math.cos(angle) * speed * config.ovalBurstX;
                vy = (float) Math.sin(angle) * speed * config.ovalBurstY;
                radius = (float) (Math.random() * 1.4f + 0.8f);
                wanderSeedX = (float) (Math.random() * Math.PI * 2);
                wanderSeedY = (float) (Math.random() * Math.PI * 2);
                driftX = (float) (Math.random() * 1.6f - 0.8f);
                driftY = (float) (Math.random() * 1.6f - 0.8f);
                int cols = (int) Math.sqrt(total);
                cols = Math.max(cols, 1);
                int rows = (int) Math.ceil(total / (float) cols);
                int col = index % cols;
                int row = index / cols;
                float u = (col + 0.5f) / cols;
                float v = (row + 0.5f) / rows;
                homeX = u;
                homeY = v;
                targetX = u;
                targetY = v;
                retargetStep = 10 + (int) (Math.random() * 18);
            }

            void update(
                    float cx, float cy, long elapsed, float tx, float ty, boolean touching,
                    float left, float top, float right, float bottom, long tick) {
                float homeWorldX = left + (right - left) * homeX;
                float homeWorldY = top + (bottom - top) * homeY;
                if (elapsed < config.explosionPhaseMs) {
                    // Pha 1: giữ quỹ đạo oval trước, chỉ chuyển dần sang lưới chữ nhật ở 40% cuối.
                    float progress = Math.max(0f, Math.min(1f, elapsed / (float) config.explosionPhaseMs));
                    float ease = progress * progress * (3f - 2f * progress); // smoothstep
                    final float ovalHoldUntil = 0.60f;
                    float localOvalProgress = Math.min(1f, progress / ovalHoldUntil);
                    float ovalEase = localOvalProgress * localOvalProgress * (3f - 2f * localOvalProgress);
                    float ovalRadiusX = (right - left) * 0.5f * config.ovalBurstX;
                    float ovalRadiusY = (bottom - top) * 0.5f * config.ovalBurstY;
                    float ovalX = cx + (float) Math.cos(launchAngle) * ovalRadiusX * launchRadius * ovalEase;
                    float ovalY = cy + (float) Math.sin(launchAngle) * ovalRadiusY * launchRadius * ovalEase;
                    float rectBlend = (progress <= ovalHoldUntil) ? 0f
                            : ((progress - ovalHoldUntil) / (1f - ovalHoldUntil));
                    float rectBlendEase = rectBlend * rectBlend * (3f - 2f * rectBlend);
                    float desiredX = ovalX + (homeWorldX - ovalX) * rectBlendEase;
                    float desiredY = ovalY + (homeWorldY - ovalY) * rectBlendEase;
                    float swirlForce = (1f - progress) * config.spreadSwirl;
                    vx += (desiredX - x) * config.spreadPull
                            + (float) Math.sin(progress * 12f + wanderSeedX) * swirlForce * 0.08f;
                    vy += (desiredY - y) * config.spreadPull
                            + (float) Math.cos(progress * 12f + wanderSeedY) * swirlForce * 0.08f;
                    vx *= (config.damping + 0.01f);
                    vy *= (config.damping + 0.01f);
                    x += vx;
                    y += vy;
                    alpha = 255;
                    return;
                }

                // Pha 2: roaming bất quy tắc toàn màn hình.
                float t = (elapsed - config.explosionPhaseMs) * 0.001f;
                vx += (float) Math.sin(t * 1.9f + wanderSeedX) * 0.08f + driftX * 0.01f;
                vy += (float) Math.cos(t * 2.1f + wanderSeedY) * 0.08f + driftY * 0.01f;
                if (tick >= nextRetargetTick) {
                    float jitterX = (float) (Math.random() * 0.18f - 0.09f);
                    float jitterY = (float) (Math.random() * 0.18f - 0.09f);
                    targetX = clamp01(homeX + jitterX);
                    targetY = clamp01(homeY + jitterY);
                    nextRetargetTick = tick + retargetStep;
                }
                float targetWorldX = left + (right - left) * targetX;
                float targetWorldY = top + (bottom - top) * targetY;
                vx += (targetWorldX - x) * config.targetPull;
                vy += (targetWorldY - y) * config.targetPull;
                vx += (homeWorldX - x) * config.homePull;
                vy += (homeWorldY - y) * config.homePull;
                vx *= config.damping;
                vy *= config.damping;

                if (touching) {
                    float dxTouch = x - tx;
                    float dyTouch = y - ty;
                    float distSq = dxTouch * dxTouch + dyTouch * dyTouch;
                    float touchRadiusSq = config.touchRadius * config.touchRadius;
                    if (distSq > 1f && distSq < touchRadiusSq) {
                        float touchDist = (float) Math.sqrt(distSq);
                        float edgeDist = Math.min(Math.min(x - left, right - x), Math.min(y - top, bottom - y));
                        float edgeFactor = Math.max(0.18f, Math.min(1f, edgeDist / 48f));
                        float push = (1f - (distSq / touchRadiusSq)) * config.maxTouchPush * edgeFactor;
                        interactionOffsetX += (dxTouch / touchDist) * push;
                        interactionOffsetY += (dyTouch / touchDist) * push;
                    }
                }

                interactionOffsetX *= 0.92f;
                interactionOffsetY *= 0.92f;
                x += vx + interactionOffsetX;
                y += vy + interactionOffsetY;

                // Bounce trong biên màn để roaming liên tục.
                if (x < left) {
                    x = left;
                    vx = Math.abs(vx) * config.edgeBounce;
                    interactionOffsetX *= 0.65f;
                } else if (x > right) {
                    x = right;
                    vx = -Math.abs(vx) * config.edgeBounce;
                    interactionOffsetX *= 0.65f;
                }
                if (y < top) {
                    y = top;
                    vy = Math.abs(vy) * config.edgeBounce;
                    interactionOffsetY *= 0.65f;
                } else if (y > bottom) {
                    y = bottom;
                    vy = -Math.abs(vy) * config.edgeBounce;
                    interactionOffsetY *= 0.65f;
                }

                float speedSq = vx * vx + vy * vy;
                if (speedSq > config.maxSpeed * config.maxSpeed) {
                    float ratio = config.maxSpeed / (float) Math.sqrt(speedSq);
                    vx *= ratio;
                    vy *= ratio;
                }

                alpha = 210;
            }

            private float clamp01(float value) {
                return Math.max(0f, Math.min(1f, value));
            }
        }
    }
}


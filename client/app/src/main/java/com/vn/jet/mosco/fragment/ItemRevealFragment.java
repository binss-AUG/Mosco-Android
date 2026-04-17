package com.vn.jet.mosco.fragment;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.net.Uri;
import android.view.Gravity;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import android.content.Context;
import android.util.AttributeSet;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonObject;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.utils.NumberUtils;
import com.vn.jet.mosco.utils.ObjetDetailBinder;
import com.vn.jet.mosco.utils.SessionManager;
import com.vn.jet.mosco.network.GachaRepository;
import com.vn.jet.mosco.model.GachaRollRequest;
import com.vn.jet.mosco.model.GachaRollResponse;

import java.util.Map;

/**
 * Premium item reveal fragment for opening Pack / Objet items.
 * Uses the same visual effects as the Objet Detail dialog:
 * - Metallic surface background gradient
 * - Shimmer overlay sweep animation
 * - Idle floating animation (translationY)
 * - Video cutscene and result reveal from SpinFragment
 */
public class ItemRevealFragment extends Fragment {

    private static final String ARG_NAME = "item_name";
    private static final String ARG_DESC = "item_desc";
    private static final String ARG_IMAGE = "item_image";
    private static final String ARG_QTY = "item_qty";
    private static final String ARG_CODE = "item_code";

    private String itemName;
    private String itemDesc;
    private String itemImage;
    private int itemQty;
    private String itemCode;

    // UI Elements
    private View packRevealRoot;
    private View packFlashOverlay;
    private GachaRepository gachaRepository;

    // Dynamic Overlay (UpgradeFragment Style)
    private FrameLayout cinematicOverlay;
    
    // Animators
    private ObjectAnimator floatingAnim;
    private ValueAnimator shimmerAnim;
    
    // Data Loading
    private org.json.JSONObject pendingCardResult = null;

    // Timing Constants
    private static final int VIDEO_FAILURE_FAILSAFE_MS = 8000;
    private static final int OVERLAY_FADE_DURATION = 300;

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
            itemName = getArguments().getString(ARG_NAME, "Item");
            itemDesc = getArguments().getString(ARG_DESC, "");
            itemImage = getArguments().getString(ARG_IMAGE, "");
            itemQty = getArguments().getInt(ARG_QTY, 1);
            itemCode = getArguments().getString(ARG_CODE, "");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_item_reveal, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Khởi tạo Repository để quản lý gọi API Gacha
        gachaRepository = new GachaRepository(requireContext());
        
        packRevealRoot = view.findViewById(R.id.root_item_reveal);
        if (packRevealRoot != null) packRevealRoot.setBackgroundColor(Color.parseColor("#0e0e0e"));

        // Hide bottom navigation
        if (getActivity() != null) {
            View navBar = getActivity().findViewById(R.id.bottom_navigation);
            if (navBar != null) navBar.setVisibility(View.GONE);
        }

        // Bind views
        TextView tvItemName = view.findViewById(R.id.tv_item_name);
        TextView tvItemInfo = view.findViewById(R.id.tv_item_info);
        TextView tvItemQty = view.findViewById(R.id.tv_item_qty);
        ImageView ivItemImage = view.findViewById(R.id.iv_item_image);
        MaterialCardView cardItem = view.findViewById(R.id.card_item);
        MaterialButton btnOpenOne = view.findViewById(R.id.btn_open_one);
        MaterialButton btnOpenAll = view.findViewById(R.id.btn_open_all);
        MaterialButton btnDone = view.findViewById(R.id.btn_done);
        packFlashOverlay = view.findViewById(R.id.view_pack_flash_overlay);

        // Set data
        tvItemName.setText(itemName);
        tvItemInfo.setText(itemDesc != null && !itemDesc.isEmpty() ? itemDesc : "Open to reveal a surprise!");
        tvItemQty.setText("x" + NumberUtils.format(getContext(), itemQty));

        // Load image
        if (itemImage != null && !itemImage.isEmpty()) {
            Glide.with(this).load(itemImage)
                    .placeholder(R.drawable.item_shop_demo)
                    .into(ivItemImage);
        } else {
            ivItemImage.setImageResource(R.drawable.item_shop_demo);
        }

        // Show Open All button only when qty > 1
        if (itemQty > 1) {
            btnOpenAll.setVisibility(View.VISIBLE);
            btnOpenAll.setText("Open All (x" + itemQty + ")");
        }

        // Back button
        view.findViewById(R.id.btn_back).setOnClickListener(v -> goBack());

        // Open x1 button
        btnOpenOne.setOnClickListener(v -> startPackOpening(false));

        // Open All button
        btnOpenAll.setOnClickListener(v -> startPackOpening(true));
 
        // Done button
        btnDone.setOnClickListener(v -> goBack());

        // Apply effects
        applyVisualEffects(cardItem, view);
    }

    private void startPackOpening(boolean openAll) {
        if (itemQty <= 0) {
            Toast.makeText(getContext(), "Không còn Pack để mở", Toast.LENGTH_SHORT).show();
            return;
        }

        final int quantity = openAll ? Math.min(itemQty, 36) : 1;
        
        pendingCardResult = null;

        // 1. CHẠY HIỆU ỨNG CHUẨN BỊ: Pack to lên và đứng im + Fade out UI
        final View cardItem = getView().findViewById(R.id.card_item);
        if (cardItem != null) {
            if (floatingAnim != null) floatingAnim.cancel();
            
            cardItem.animate()
                    .scaleX(1.15f)
                    .scaleY(1.15f)
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(400)
                    .setInterpolator(new AccelerateInterpolator())
                    .start();
        }
        
        // Mờ dần các thành phần UI
        getView().findViewById(R.id.tv_item_name).animate().alpha(0f).setDuration(400).start();
        getView().findViewById(R.id.tv_item_info).animate().alpha(0f).setDuration(400).start();
        getView().findViewById(R.id.tv_item_qty).animate().alpha(0f).setDuration(400).start();
        getView().findViewById(R.id.ll_buttons).animate().alpha(0f).setDuration(400).start();
        getView().findViewById(R.id.btn_back).animate().alpha(0f).setDuration(400).start();

        // 2. GỌI API MỞ PACK
        Long userId = new SessionManager(requireContext()).getUserId();
        gachaRepository.openPack(userId, itemCode, quantity, new GachaRepository.GachaCallback<Map<String, Object>>() {
            @Override
            public void onSuccess(Map<String, Object> response) {
                try {
                    com.vn.jet.mosco.utils.DatabaseLoader.clearUserCache();
                    itemQty -= quantity;
                    
                    if (quantity == 1) {
                        java.util.List<Map<String, Object>> cards = (java.util.List<Map<String, Object>>) response.get("cards");
                        if (cards != null && !cards.isEmpty()) {
                            Map<String, Object> firstRollResult = cards.get(0);
                            Map<String, Object> cardData = (Map<String, Object>) firstRollResult.get("cardData");
                            
                            String jsonStr = new com.google.gson.Gson().toJson(cardData);
                            pendingCardResult = new org.json.JSONObject(jsonStr);
                            
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> performTieredCinematic(pendingCardResult));
                            }
                        } else {
                            throw new RuntimeException("Phản hồi thành công nhưng không có dữ liệu thẻ");
                        }
                    } else {
                        java.util.List<Map<String, Object>> cardList = (java.util.List<Map<String, Object>>) response.get("cards");
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> showBulkResults(cardList));
                        }
                    }
                } catch (Exception e) {
                    android.util.Log.e("ItemReveal", "Lỗi dữ liệu", e);
                    if (getActivity() != null) getActivity().runOnUiThread(() -> refreshUI());
                }
            }

            @Override
            public void onError(int httpCode, String errorMessage) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Lỗi: " + errorMessage, Toast.LENGTH_LONG).show();
                        refreshUI();
                    });
                }
            }
        });
    }

    private void showBulkResults(java.util.List<Map<String, Object>> cards) {
        if (cards == null || cards.isEmpty()) {
            refreshUI();
            return;
        }

        StringBuilder sb = new StringBuilder("Bạn đã nhận được:\n");
        for (Map<String, Object> roll : cards) {
            Map<String, Object> data = (Map<String, Object>) roll.get("cardData");
            if (data != null) {
                sb.append("- ").append(data.get("collectionId")).append("\n");
            }
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Mở Hàng Loạt (" + cards.size() + " Packs)")
                .setMessage(sb.toString())
                .setPositiveButton("TUYỆT VỜI", (d, w) -> {
                    if (itemQty > 0) refreshUI();
                    else goBack();
                })
                .setCancelable(false)
                .show();
    }

    private void performTieredCinematic(org.json.JSONObject cardJson) {
        if (!isAdded() || getContext() == null) return;
        
        String cardClass = cardJson.optString("class", "Welcome");
        final FrameLayout overlay = createCinematicOverlay();
        if (overlay == null) {
            showFullscreenResult(cardJson);
            return;
        }

        // Logic check class linh hoạt hơn để mapping đúng Tier
        boolean isTier1 = cardClass.equalsIgnoreCase("Welcome") 
                || cardClass.equalsIgnoreCase("First") 
                || cardClass.equalsIgnoreCase("FirstWelcome");
        
        boolean isTier2 = cardClass.equalsIgnoreCase("Double");

        if (isTier1) {
            runTier1Cinematic(overlay, cardJson);
        } else if (isTier2) {
            runTier2Cinematic(overlay, cardJson);
        } else {
            runTier3Cinematic(overlay, cardJson);
        }
    }

    /**
     * Custom VideoView to handle Center Crop aspect ratio scaling.
     * Fixing the "black bars" and "laggy" feel of default VideoView.
     */
    public static class FullSizeVideoView extends VideoView {
        public FullSizeVideoView(Context context) { super(context); }
        public FullSizeVideoView(Context context, AttributeSet attrs) { super(context, attrs); }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = getDefaultSize(0, widthMeasureSpec);
            int height = getDefaultSize(0, heightMeasureSpec);
            setMeasuredDimension(width, height);
        }
    }

    private FrameLayout createCinematicOverlay() {
        if (getActivity() == null) return null;
        ViewGroup root = getActivity().findViewById(android.R.id.content);
        if (root == null) return null;

        View old = root.findViewById(R.id.vip_overlay_container);
        if (old != null) root.removeView(old);

        FrameLayout overlay = new FrameLayout(requireContext());
        overlay.setId(R.id.vip_overlay_container);
        overlay.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        overlay.setBackgroundColor(Color.BLACK); // Nền đen sâu để chuyển cảnh mượt
        overlay.setElevation(2000f);
        overlay.setAlpha(0f);

        // --- FOOTER AREA ---
        LinearLayout footer = new LinearLayout(getContext());
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, 0, 0, dpToPx(48));
        FrameLayout.LayoutParams footerParams = new FrameLayout.LayoutParams(-1, dpToPx(150));
        footerParams.gravity = Gravity.BOTTOM;
        
        // Ambient Label
        TextView tvFooter = new TextView(getContext());
        tvFooter.setText("MOSCO CINEMATIC EXPERIENCE");
        tvFooter.setTextColor(Color.parseColor("#44FFFFFF"));
        tvFooter.setTextSize(10);
        tvFooter.setLetterSpacing(0.5f);
        tvFooter.setAllCaps(true);
        footer.addView(tvFooter);

        overlay.addView(footer, footerParams);

        // --- SKIP BUTTON ---
        TextView btnSkip = new TextView(getContext());
        btnSkip.setText("SKIP ›");
        btnSkip.setTextColor(Color.WHITE);
        btnSkip.setTextSize(14);
        btnSkip.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
        btnSkip.setBackgroundResource(R.drawable.bg_glass_qty); // Reuse glass effect
        btnSkip.setAlpha(0.6f);
        
        FrameLayout.LayoutParams skipParams = new FrameLayout.LayoutParams(-2, -2);
        skipParams.gravity = Gravity.TOP | Gravity.END;
        skipParams.topMargin = dpToPx(60);
        skipParams.setMarginEnd(dpToPx(20));
        overlay.addView(btnSkip, skipParams);

        root.addView(overlay);
        cinematicOverlay = overlay;
        return overlay;
    }

    private void runTier1Cinematic(FrameLayout overlay, org.json.JSONObject cardJson) {
        View flash = new View(getContext());
        flash.setBackgroundColor(Color.WHITE);
        flash.setAlpha(0f);
        overlay.addView(flash, new FrameLayout.LayoutParams(-1, -1));

        overlay.animate().alpha(1f).setDuration(150).start();
        flash.animate().alpha(1f).setDuration(400).withEndAction(() -> {
            if (packRevealRoot != null) packRevealRoot.setVisibility(View.GONE);
            flash.animate().alpha(0f).setDuration(600).setStartDelay(100).withEndAction(() -> {
                showFullscreenResult(cardJson);
            }).start();
        }).start();
    }

    private void runTier2Cinematic(FrameLayout overlay, org.json.JSONObject cardJson) {
        FullSizeVideoView videoView = new FullSizeVideoView(getContext());
        videoView.setAlpha(0f);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            videoView.setZOrderMediaOverlay(true);
        }
        overlay.addView(videoView, 0, new FrameLayout.LayoutParams(-1, -1));

        final Handler failsafeHandler = new Handler(Looper.getMainLooper());
        final Runnable failsafe = () -> {
            overlay.animate().alpha(0f).setDuration(OVERLAY_FADE_DURATION).withEndAction(() -> {
                if (overlay.getParent() != null) ((ViewGroup)overlay.getParent()).removeView(overlay);
                showFullscreenResult(cardJson);
            }).start();
        };

        // Skip logic
        View skip = overlay.getChildAt(overlay.getChildCount() - 1);
        if (skip != null) skip.setOnClickListener(v -> failsafe.run());

        overlay.animate().alpha(1f).setDuration(250).start();
        Uri videoUri = Uri.parse("android.resource://" + requireActivity().getPackageName() + "/" + R.raw.cut_scense_tier_2);
        videoView.setVideoURI(videoUri);

        videoView.setOnPreparedListener(mp -> {
            if (packRevealRoot != null) packRevealRoot.setVisibility(View.GONE);
            videoView.animate().alpha(1f).setDuration(400).start();
            videoView.start();
        });
        videoView.setOnCompletionListener(mp -> failsafe.run());
        videoView.setOnErrorListener((mp, what, extra) -> { 
            android.util.Log.e("ItemReveal", "Video Tier 2 Error: " + what + "," + extra);
            failsafe.run(); 
            return true; 
        });
        failsafeHandler.postDelayed(failsafe, VIDEO_FAILURE_FAILSAFE_MS);
    }

    private void runTier3Cinematic(FrameLayout overlay, org.json.JSONObject cardJson) {
        FullSizeVideoView videoView = new FullSizeVideoView(getContext());
        videoView.setAlpha(0f);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            videoView.setZOrderMediaOverlay(true);
        }
        overlay.addView(videoView, 0, new FrameLayout.LayoutParams(-1, -1));

        TextView tvInfo = new TextView(getContext());
        tvInfo.setAlpha(0f);
        tvInfo.setTextColor(Color.WHITE);
        tvInfo.setTextSize(44);
        tvInfo.setGravity(Gravity.CENTER);
        tvInfo.setTypeface(android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD));
        tvInfo.setShadowLayer(30, 0, 0, androidx.core.content.ContextCompat.getColor(requireContext(), R.color.mosco_primary));
        
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2);
        lp.gravity = Gravity.CENTER;
        overlay.addView(tvInfo, lp);

        final Handler failsafeHandler = new Handler(Looper.getMainLooper());
        final Runnable failsafe = () -> {
            overlay.animate().alpha(0f).setDuration(OVERLAY_FADE_DURATION).withEndAction(() -> {
                if (overlay.getParent() != null) ((ViewGroup)overlay.getParent()).removeView(overlay);
                showFullscreenResult(cardJson);
            }).start();
        };

        // Skip logic (Skip is the 3rd child now: 0-Video, 1-Footer, 2-Skip)
        View skip = overlay.getChildAt(overlay.getChildCount() - 1);
        if (skip != null) skip.setOnClickListener(v -> failsafe.run());

        overlay.animate().alpha(1f).setDuration(250).start();
        Uri videoUri = Uri.parse("android.resource://" + requireActivity().getPackageName() + "/" + R.raw.cut_scense_tier_3);
        videoView.setVideoURI(videoUri);

        videoView.setOnPreparedListener(mp -> {
            if (packRevealRoot != null) packRevealRoot.setVisibility(View.GONE);
            videoView.animate().alpha(1f).setDuration(400).start();
            videoView.start();
            startVipOverlaySequenceV2(tvInfo, cardJson);
        });
        videoView.setOnCompletionListener(mp -> failsafe.run());
        videoView.setOnErrorListener((mp, what, extra) -> { 
            android.util.Log.e("ItemReveal", "Video Tier 3 Error: " + what + "," + extra);
            failsafe.run(); 
            return true; 
        });
        failsafeHandler.postDelayed(failsafe, VIDEO_FAILURE_FAILSAFE_MS);
    }

    private void startVipOverlaySequenceV2(TextView tvInfo, org.json.JSONObject cardJson) {
        String cardClass = cardJson.optString("class", "Special");
        String season = cardJson.optString("season", "");
        String memberName = cardJson.optString("member", "");
        String sId = getMemberSId(memberName);

        Handler h = new Handler(Looper.getMainLooper());
        h.postDelayed(() -> showOverlayTextV2(tvInfo, cardClass.toUpperCase()), 1000);
        h.postDelayed(() -> hideOverlayTextV2(tvInfo), 2500);
        h.postDelayed(() -> showOverlayTextV2(tvInfo, season), 3000);
        h.postDelayed(() -> hideOverlayTextV2(tvInfo), 4500);
        h.postDelayed(() -> showOverlayTextV2(tvInfo, sId), 5000);
        h.postDelayed(() -> hideOverlayTextV2(tvInfo), 6500);
    }

    private void showOverlayTextV2(TextView tv, String text) {
        tv.setText(text);
        tv.animate().alpha(1f).scaleX(1.1f).scaleY(1.1f).setDuration(400).start();
    }

    private void hideOverlayTextV2(TextView tv) {
        tv.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f).setDuration(400).start();
    }

    private String getMemberSId(String member) {
        switch (member) {
            case "SeoYeon": return "S1";
            case "HyeRin": return "S2";
            case "JiWoo": return "S3";
            case "ChaeYeon": return "S4";
            case "YooYeon": return "S5";
            case "SooMin": return "S6";
            case "NaKyoung": return "S7";
            case "YuBin": return "S8";
            case "Kaede": return "S9";
            case "DaHyun": return "S10";
            case "Kotone": return "S11";
            case "YeonJi": return "S12";
            case "Nien": return "S13";
            case "SoHyun": return "S14";
            case "Xinyu": return "S15";
            case "Mayu": return "S16";
            case "Lynn": return "S17";
            case "JooBin": return "S18";
            case "HaYeon": return "S19";
            case "ShiOn": return "S20";
            case "ChaeWon": return "S21";
            case "Sullin": return "S22";
            case "SeoAh": return "S23";
            case "JiYeon": return "S24";
            default: return "S0";
        }
    }

    private void showFullscreenResult(org.json.JSONObject cardJson) {
        String collectionId = cardJson.optString("collectionId", "Unknown Objet");
        String imageUrl = cardJson.optString("frontImage", "");

        if (getActivity() == null) return;
        ViewGroup root = getActivity().findViewById(android.R.id.content);
        if (root == null) return;

        FrameLayout resultContainer = new FrameLayout(requireContext());
        resultContainer.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        resultContainer.setBackgroundColor(Color.parseColor("#0e0e0e"));
        resultContainer.setElevation(3000f);
        
        View resultView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_item_detail, resultContainer, false);
        ((CardView)resultView).setCardBackgroundColor(Color.TRANSPARENT);
        ((CardView)resultView).setCardElevation(0f);
        
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, -2);
        params.gravity = Gravity.CENTER;
        resultView.setLayoutParams(params);
        resultContainer.addView(resultView);
        root.addView(resultContainer);

        TextView tvTitle = resultView.findViewById(R.id.dialogItemName);
        TextView tvDesc = resultView.findViewById(R.id.dialogItemDesc);
        ImageView ivImage = resultView.findViewById(R.id.dialogItemImage);
        MaterialButton btnOk = resultView.findViewById(R.id.btnUseItem);
        
        tvTitle.setText("Pack Opened!");
        tvTitle.setTextColor(Color.WHITE);
        tvDesc.setText("You received: " + collectionId);
        tvDesc.setTextColor(Color.parseColor("#adaaaa"));
        btnOk.setText("AWESOME");
        
        ViewGroup.LayoutParams imgParams = ivImage.getLayoutParams();
        imgParams.width = dpToPx(280);
        imgParams.height = dpToPx(400);
        
        if (!imageUrl.isEmpty()) {
            Glide.with(this).load(imageUrl).placeholder(R.drawable.item_shop_demo).into(ivImage);
        }

        tvTitle.setAlpha(0f); tvDesc.setAlpha(0f); btnOk.setAlpha(0f);
        ivImage.setScaleX(0.2f); ivImage.setScaleY(0.2f); ivImage.setAlpha(0f);
        
        ivImage.animate().alpha(1f).scaleX(1.0f).scaleY(1.0f).setDuration(600).setInterpolator(new OvershootInterpolator()).withEndAction(() -> {
            tvTitle.animate().alpha(1f).setDuration(400).start();
            tvDesc.animate().alpha(1f).setDuration(400).start();
            btnOk.animate().alpha(1f).setDuration(400).start();
        }).start();

        ObjectAnimator floatAnim = ObjectAnimator.ofFloat(ivImage, "translationY", 0f, -30f, 0f);
        floatAnim.setDuration(4000); floatAnim.setRepeatCount(-1); floatAnim.setRepeatMode(ValueAnimator.REVERSE); floatAnim.start();

        btnOk.setOnClickListener(v -> {
            floatAnim.cancel();
            if (cinematicOverlay != null && cinematicOverlay.getParent() != null) ((ViewGroup)cinematicOverlay.getParent()).removeView(cinematicOverlay);
            resultContainer.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                root.removeView(resultContainer);
                if (itemQty > 0) refreshUI();
                else goBack();
            }).start();
        });
    }

    private void refreshUI() {
        if (getView() == null) return;
        packRevealRoot.setVisibility(View.VISIBLE);
        packRevealRoot.setAlpha(0f);
        packRevealRoot.animate().alpha(1f).setDuration(300).start();
        
        View cardItem = getView().findViewById(R.id.card_item);
        if (cardItem != null) {
            cardItem.setAlpha(1f); cardItem.setScaleX(1.0f); cardItem.setScaleY(1.0f); cardItem.setVisibility(View.VISIBLE);
        }
 
        getView().findViewById(R.id.tv_item_name).animate().alpha(1f).setDuration(300).start();
        getView().findViewById(R.id.tv_item_info).animate().alpha(1f).setDuration(300).start();
        getView().findViewById(R.id.tv_item_qty).animate().alpha(1f).setDuration(300).start();
        getView().findViewById(R.id.ll_buttons).animate().alpha(1f).setDuration(300).start();
        getView().findViewById(R.id.btn_back).animate().alpha(1f).setDuration(300).start();
 
        TextView tvItemQty = getView().findViewById(R.id.tv_item_qty);
        MaterialButton btnOpenOne = getView().findViewById(R.id.btn_open_one);
        MaterialButton btnOpenAll = getView().findViewById(R.id.btn_open_all);
        MaterialButton btnDone = getView().findViewById(R.id.btn_done);
        
        if (tvItemQty != null) tvItemQty.setText("x" + NumberUtils.format(getContext(), itemQty));
        
        if (itemQty > 0) {
            btnOpenOne.setVisibility(View.VISIBLE); btnDone.setVisibility(View.GONE);
            if (btnOpenAll != null) {
                if (itemQty > 1) {
                    btnOpenAll.setVisibility(View.VISIBLE);
                    btnOpenAll.setText("Open All (x" + NumberUtils.format(getContext(), itemQty) + ")");
                } else btnOpenAll.setVisibility(View.GONE);
            }
        } else {
            btnOpenOne.setVisibility(View.GONE);
            if (btnOpenAll != null) btnOpenAll.setVisibility(View.GONE);
            btnDone.setVisibility(View.VISIBLE);
        }
    }

    private void applyVisualEffects(MaterialCardView cardItem, View view) {
        int primaryColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.mosco_primary);
        cardItem.setStrokeWidth(dpToPx(1));
        cardItem.setStrokeColor(androidx.core.graphics.ColorUtils.setAlphaComponent(primaryColor, 128));

        View metallicBg = view.findViewById(R.id.view_card_metallic_bg);
        if (metallicBg != null) {
            metallicBg.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFFF5F5F5, 0xFFFFFFFF, 0xFFDBDBDB}));
        }

        View shimmer = view.findViewById(R.id.view_card_shimmer);
        if (shimmer != null) {
            shimmer.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0x00FFFFFF, 0x00FFFFFF, 0x66FFFFFF, 0x00FFFFFF, 0x00FFFFFF}));
            shimmerAnim = ValueAnimator.ofFloat(-1.5f, 2.5f);
            shimmerAnim.setDuration(3500); shimmerAnim.setRepeatCount(-1);
            shimmerAnim.addUpdateListener(animation -> shimmer.setTranslationX(shimmer.getWidth() * (float)animation.getAnimatedValue()));
            shimmerAnim.start();
        }

        floatingAnim = ObjectAnimator.ofFloat(cardItem, "translationY", 0f, -12f, 0f);
        floatingAnim.setDuration(3000); floatingAnim.setRepeatCount(-1); floatingAnim.setRepeatMode(ValueAnimator.REVERSE); floatingAnim.start();
    }

    private void goBack() {
        if (getActivity() != null) {
            View navBar = getActivity().findViewById(R.id.bottom_navigation);
            if (navBar != null) navBar.setVisibility(View.VISIBLE);
            getActivity().getSupportFragmentManager().popBackStack();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (floatingAnim != null) floatingAnim.cancel();
        if (shimmerAnim != null) shimmerAnim.cancel();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}

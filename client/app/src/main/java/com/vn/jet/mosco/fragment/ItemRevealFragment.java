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
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import android.net.Uri;

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
    private VideoView videoSpinEffect;
    private FrameLayout videoContainer;
    private View rootLayout;
    private GachaRepository gachaRepository;

    // Animators
    private ObjectAnimator floatingAnim;
    private ValueAnimator shimmerAnim;
    
    // Sync Flags for Background Loading
    private volatile boolean packVideoComplete = false;
    private volatile boolean packDataReady = false;
    private org.json.JSONObject pendingCardData = null;

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
        
        rootLayout = view.findViewById(R.id.root_item_reveal);
        if (rootLayout != null) rootLayout.setBackgroundColor(Color.parseColor("#0e0e0e"));

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

        videoContainer = new FrameLayout(requireContext());
        videoContainer.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        videoContainer.setVisibility(View.GONE);
        videoContainer.setBackgroundColor(Color.parseColor("#0e0e0e")); // Level 0: The Void
        
        // Cực kỳ quan trọng: Thêm videoContainer vào Activity Root thay vì Fragment View
        // để đảm bảo nó đè lên tất cả mọi thứ (bao gồm cả Bottom Navigation)
        if (getActivity() != null) {
            ViewGroup root = getActivity().findViewById(android.R.id.content);
            if (root != null) {
                // Đặt Z-index cực cao
                videoContainer.setElevation(1000f);
                root.addView(videoContainer);
            } else {
                ((ViewGroup) view).addView(videoContainer);
            }
        } else {
            ((ViewGroup) view).addView(videoContainer);
        }

        videoSpinEffect = new VideoView(requireContext());
        videoSpinEffect.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        videoContainer.addView(videoSpinEffect);

        // Set data
        tvItemName.setText(itemName);
        tvItemInfo.setText(itemDesc != null && !itemDesc.isEmpty() ? itemDesc : "Open to reveal a surprise!");
        tvItemQty.setText("x" + itemQty);

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
        btnOpenOne.setOnClickListener(v -> openPack(false));

        // Open All button
        btnOpenAll.setOnClickListener(v -> openPack(true));

        // Apply effects
        applyVisualEffects(cardItem, view);
    }

    private void openPack(boolean openAll) {
        if (itemQty <= 0) {
            Toast.makeText(getContext(), "Không còn Pack để mở", Toast.LENGTH_SHORT).show();
            return;
        }

        packVideoComplete = false;
        packDataReady = false;
        pendingCardData = null;

        // 1. CHẠY CUTSCENE NGAY LẬP TỨC
        playCutsceneAndReveal();

        // 2. GỌI API NGẦM TRONG KHI VIDEO ĐANG CHẠY
        GachaRollRequest request = new GachaRollRequest(itemCode, 1);
        gachaRepository.rollGacha(request, new GachaRepository.GachaCallback<GachaRollResponse>() {
            @Override
            public void onSuccess(GachaRollResponse response) {
                try {
                    // Cực kỳ quan trọng: Xóa Cache cũ để khi quay sang thẻ Túi Đồ, App tự update lấy thẻ mới bù vào!
                    com.vn.jet.mosco.utils.DatabaseLoader.clearUserCache();
                    
                    String jsonStr = new com.google.gson.Gson().toJson(response.getCardData());
                    pendingCardData = new org.json.JSONObject(jsonStr);
                    itemQty--; // Giảm số lượng
                    
                    // Preload ảnh kết quả ngay
                    String imgUrl = pendingCardData.optString("frontImage");
                    if (!imgUrl.isEmpty()) {
                        Glide.with(requireContext().getApplicationContext()).asBitmap().load(imgUrl).submit();
                    }
                    
                    packDataReady = true;
                    checkReadyToShowPackResult();
                } catch (Exception e) {
                    android.util.Log.e("ItemReveal", "Lỗi dữ liệu", e);
                    packDataReady = true; // Vẫn cho phép hiện để thoát kẹt
                }
            }

            @Override
            public void onError(int httpCode, String errorMessage) {
                android.util.Log.e("ItemReveal", "Lỗi Server: " + errorMessage);
                Toast.makeText(getContext(), "Lỗi: " + errorMessage, Toast.LENGTH_LONG).show();
                packDataReady = true;
                checkReadyToShowPackResult();
            }
        });
    }

    private void checkReadyToShowPackResult() {
        if (packVideoComplete && packDataReady) {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (pendingCardData != null) {
                        showFullscreenResult(pendingCardData);
                    } else {
                        refreshUI();
                    }
                });
            }
        }
    }

    private void playCutsceneAndReveal() {
        // Fade out current UI completely
        rootLayout.animate().alpha(0f).setDuration(300).withEndAction(() -> {
            rootLayout.setVisibility(View.GONE);
            
            // Bring video container to front and make it visible
            videoContainer.bringToFront();
            videoContainer.setVisibility(View.VISIBLE);
            videoContainer.setAlpha(0f);
            
            // Use R.raw.spin_reward_animation as requested
            playVideo(R.raw.spin_reward_animation, () -> {
                packVideoComplete = true;
                checkReadyToShowPackResult();
            });
        }).start();
    }

    private void playVideo(int resId, Runnable onComplete) {
        if (videoSpinEffect == null || videoContainer == null || getContext() == null) return;
        
        videoContainer.bringToFront();
        videoContainer.setVisibility(View.VISIBLE);
        videoContainer.setAlpha(1f);
        videoContainer.setBackgroundColor(Color.BLACK);

        // Disable elevation which causes shadow/white frame artifacts
        if (videoSpinEffect != null) {
            videoSpinEffect.animate().cancel();
            videoSpinEffect.setZ(0f);
            videoSpinEffect.setAlpha(0f); 
        }

        FrameLayout.LayoutParams resetLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        resetLp.gravity = android.view.Gravity.CENTER;
        videoSpinEffect.setLayoutParams(resetLp);
        
        Uri videoUri = Uri.parse("android.resource://" + requireActivity().getPackageName() + "/" + resId);
        videoSpinEffect.setVideoURI(videoUri);
        
        final boolean[] completeCalled = {false};
        
        Runnable safeComplete = () -> {
            if (!completeCalled[0]) {
                completeCalled[0] = true;
                if (videoContainer != null) {
                    videoContainer.animate().cancel();
                    videoContainer.setAlpha(1f);
                    videoContainer.setVisibility(View.GONE);
                }
                onComplete.run();
            }
        };

        // Fail-safe TỐI THƯỢNG: Đảm bảo DÙ CÓ CHUYỆN GÌ XẢY RA, video cũng chỉ chạy tối đa 6 giây
        // Bộ đếm này bắt đầu ngay khi gọi playVideo, không chờ onPrepared
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            if (!completeCalled[0]) {
                android.util.Log.w("ItemReveal", "Absolute fail-safe triggered after 6s");
                safeComplete.run();
            }
        }, 6000);

        videoSpinEffect.setOnPreparedListener(mp -> {
            float videoRatio = mp.getVideoWidth() / (float) mp.getVideoHeight();
            int targetWidth = videoContainer.getWidth();
            if (targetWidth == 0) targetWidth = getResources().getDisplayMetrics().widthPixels;
            
            int targetHeight = (int) (targetWidth / videoRatio);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(targetWidth, targetHeight);
            lp.gravity = android.view.Gravity.CENTER;
            videoSpinEffect.setLayoutParams(lp);
            videoSpinEffect.setScaleX(1.0f);
            videoSpinEffect.setScaleY(1.0f);
            mp.setLooping(false);
            
            // Bỏ qua 0.2s đầu (phần chờ/đen)
            mp.seekTo(200);

            // Show video instantly once hardware decodes first frame
            videoSpinEffect.animate()
                    .alpha(1f)
                    .setDuration(150)
                    .start();

            videoSpinEffect.start();
        });

        videoSpinEffect.setOnCompletionListener(mp -> {
            if (isAdded()) {
                safeComplete.run();
            }
        });

        videoSpinEffect.setOnErrorListener((mp, what, extra) -> {
            android.util.Log.e("ItemReveal", "Video Error: what=" + what + ", extra=" + extra);
            safeComplete.run();
            return true;
        });
    }

    private void showFullscreenResult(org.json.JSONObject cardJson) {
        String collectionId = cardJson.optString("collectionId", "Unknown Objet");
        String imageUrl = cardJson.optString("frontImage", "");

        // Cực kỳ quan trọng: Gắn resultContainer vào Root Activity để đè lên mọi thứ
        FrameLayout resultContainer = new FrameLayout(requireContext());
        resultContainer.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        resultContainer.setBackgroundColor(Color.parseColor("#0e0e0e")); // Level 0: The Void
        resultContainer.setElevation(1000f); // Đặt Z-index rất cao
        
        // Add content view
        View resultView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_item_detail, resultContainer, false);
        
        // Make the card background transparent since we are full screen now
        CardView rootCard = (CardView) resultView;
        rootCard.setCardBackgroundColor(Color.TRANSPARENT);
        rootCard.setCardElevation(0f);
        
        // Center the content in the fullscreen container
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = android.view.Gravity.CENTER;
        resultView.setLayoutParams(params);
        
        resultContainer.addView(resultView);
        
        // Cực kỳ quan trọng: Tắt clipping để thẻ không bị khuyết khi di chuyển lên xuống
        if (resultView instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) resultView;
            vg.setClipChildren(false);
            vg.setClipToPadding(false);
            // LinearLayout bên trong
            if (vg.getChildCount() > 0 && vg.getChildAt(0) instanceof ViewGroup) {
                ViewGroup innerVg = (ViewGroup) vg.getChildAt(0);
                innerVg.setClipChildren(false);
                innerVg.setClipToPadding(false);
                // Thiết lập Padding đều nhau cho khung kết quả
                int p = dpToPx(32);
                innerVg.setPadding(p, p, p, p);
            }
        }
        
        // Gắn vào Activity Root thay vì Fragment View
        if (getActivity() != null) {
            ViewGroup root = getActivity().findViewById(android.R.id.content);
            if (root != null) {
                // Đảm bảo không bị clipping khi thực hiện hiệu ứng floating/scale lớn
                root.setClipChildren(false);
                root.setClipToPadding(false);
                root.addView(resultContainer);
            } else {
                ((ViewGroup) getView()).setClipChildren(false);
                ((ViewGroup) getView()).addView(resultContainer);
            }
        } else {
            ((ViewGroup) getView()).setClipChildren(false);
            ((ViewGroup) getView()).addView(resultContainer);
        }

        resultContainer.setClipChildren(false);
        resultContainer.setClipToPadding(false);

        // Apply Color.md Design System to elements
        TextView tvTitle = resultView.findViewById(R.id.dialogItemName);
        TextView tvDesc = resultView.findViewById(R.id.dialogItemDesc);
        TextView tvQty = resultView.findViewById(R.id.dialogItemQuantity);
        ImageView ivImage = resultView.findViewById(R.id.dialogItemImage);
        MaterialButton btnOk = resultView.findViewById(R.id.btnUseItem);
        View btnClose = resultView.findViewById(R.id.btnCloseDialog);

        tvTitle.setText("Pack Opened!");
        tvTitle.setTextColor(Color.WHITE); // Secondary: Starlight Neutral
        tvTitle.setTextSize(24f);
        // Tăng khoảng cách để không bị thẻ bài đè khi floating
        LinearLayout.LayoutParams titleLp = (LinearLayout.LayoutParams) tvTitle.getLayoutParams();
        titleLp.topMargin = dpToPx(32);
        tvTitle.setLayoutParams(titleLp);
        
        tvDesc.setText("You received: " + collectionId);
        tvDesc.setTextColor(Color.parseColor("#adaaaa")); // Body: on_surface_variant
        
        if (tvQty != null) tvQty.setVisibility(View.GONE);
        if (btnClose != null) btnClose.setVisibility(View.GONE);
        
        // Increase image size for fullscreen feel
        ViewGroup.LayoutParams imgParams = ivImage.getLayoutParams();
        imgParams.width = dpToPx(280);
        imgParams.height = dpToPx(400);
        ivImage.setLayoutParams(imgParams);
        
        // Primary Button: Linear Gradient (The Pulse to metallic sheen)
        btnOk.setText("AWESOME");
        btnOk.setLetterSpacing(0.1f);
        btnOk.setTextColor(Color.WHITE);
        btnOk.setBackgroundTintList(null); // Remove default tint
        
        GradientDrawable gradient = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[] {Color.parseColor("#6c29fd"), Color.parseColor("#9547f7")}
        );
        gradient.setCornerRadius(dpToPx(8));
        btnOk.setBackground(gradient);
        
        if (!imageUrl.isEmpty()) {
            java.io.File localThumb = com.vn.jet.mosco.utils.CardAssetManager.getLocalFile(requireContext(), imageUrl);
            com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> thumbRequest = null;
            if (localThumb != null && localThumb.exists()) {
                thumbRequest = Glide.with(this).load(localThumb);
            }

            Glide.with(this)
                    .load(imageUrl)
                    .thumbnail(thumbRequest)
                    .placeholder(R.drawable.item_shop_demo)
                    .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade(500))
                    .into(ivImage);
        }

        // Ẩn Text và Button để hiện sau
        tvTitle.setAlpha(0f);
        tvDesc.setAlpha(0f);
        btnOk.setAlpha(0f);

        // --- HIỆU ỨNG BẤT NGỜ (Overshoot + Burst) dành riêng cho Thẻ bài ---
        ivImage.setScaleX(0.2f);
        ivImage.setScaleY(0.2f);
        ivImage.setAlpha(0f);
        
        ivImage.animate()
                .alpha(1f)
                .scaleX(1.3f)
                .scaleY(1.3f)
                .setDuration(450)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    // Nảy về kích thước chuẩn
                    ivImage.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(250)
                            .setInterpolator(new android.view.animation.OvershootInterpolator())
                            .withEndAction(() -> {
                                // SAU KHI THẺ XONG -> Hiện Text và Button (Fade dần)
                                tvTitle.animate().alpha(1f).setDuration(400).start();
                                tvDesc.animate().alpha(1f).setDuration(400).start();
                                btnOk.animate().alpha(1f).setDuration(400).start();
                            })
                            .start();
                })
                .start();

        // Floating animation for the card
        ObjectAnimator floatAnim = ObjectAnimator.ofFloat(ivImage, "translationY", 0f, -30f, 0f);
        floatAnim.setDuration(4000);
        floatAnim.setRepeatCount(ObjectAnimator.INFINITE);
        floatAnim.setRepeatMode(ObjectAnimator.REVERSE);
        floatAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        floatAnim.start();

        btnOk.setOnClickListener(v -> {
            floatAnim.cancel();
            resultContainer.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                if (resultContainer.getParent() != null) {
                    ((ViewGroup) resultContainer.getParent()).removeView(resultContainer);
                }
                if (itemQty > 0) {
                    refreshUI();
                } else {
                    goBack();
                }
            }).start();
        });
    }

    private void showResultDialog(org.json.JSONObject cardJson) {
        // Obsolete: Replaced by showFullscreenResult
    }

    private void refreshUI() {
        rootLayout.setVisibility(View.VISIBLE);
        rootLayout.setAlpha(0f);
        rootLayout.animate().alpha(1f).setDuration(300).start();

        TextView tvItemQty = getView().findViewById(R.id.tv_item_qty);
        MaterialButton btnOpenAll = getView().findViewById(R.id.btn_open_all);
        
        if (tvItemQty != null) tvItemQty.setText("x" + itemQty);
        if (btnOpenAll != null) {
            if (itemQty > 1) {
                btnOpenAll.setVisibility(View.VISIBLE);
                btnOpenAll.setText("Open All (x" + itemQty + ")");
            } else {
                btnOpenAll.setVisibility(View.GONE);
            }
        }
    }

    private void applyVisualEffects(MaterialCardView cardItem, View view) {
        int primaryColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.mosco_primary);
        int blurredBorder = androidx.core.graphics.ColorUtils.setAlphaComponent(primaryColor, 128);

        cardItem.setStrokeWidth(dpToPx(1));
        cardItem.setStrokeColor(blurredBorder);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            cardItem.setCardElevation(dpToPx(16));
            cardItem.setOutlineAmbientShadowColor(primaryColor);
            cardItem.setOutlineSpotShadowColor(primaryColor);
        }

        View metallicBg = view.findViewById(R.id.view_card_metallic_bg);
        if (metallicBg != null) {
            int[] colors = {Color.parseColor("#F5F5F5"), Color.parseColor("#FFFFFF"), Color.parseColor("#DBDBDB")};
            GradientDrawable mBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
            metallicBg.setBackground(mBg);
        }

        View shimmer = view.findViewById(R.id.view_card_shimmer);
        if (shimmer != null) {
            GradientDrawable shimmerBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{0x00FFFFFF, 0x00FFFFFF, 0x66FFFFFF, 0x00FFFFFF, 0x00FFFFFF});
            shimmer.setBackground(shimmerBg);
            shimmer.setRotation(10f);
            shimmer.setScaleX(1.0f);
            shimmer.setScaleY(1.5f);

            shimmerAnim = ValueAnimator.ofFloat(-1.5f, 2.5f);
            shimmerAnim.setDuration(3500);
            shimmerAnim.setInterpolator(new LinearInterpolator());
            shimmerAnim.setRepeatCount(ValueAnimator.INFINITE);
            shimmerAnim.setRepeatMode(ValueAnimator.RESTART);
            shimmerAnim.addUpdateListener(animation -> {
                float fraction = (float) animation.getAnimatedValue();
                shimmer.setTranslationX(shimmer.getWidth() * fraction);
            });
            shimmerAnim.start();
        }

        floatingAnim = ObjectAnimator.ofFloat(cardItem, "translationY", 0f, -12f, 0f);
        floatingAnim.setDuration(3000);
        floatingAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        floatingAnim.setRepeatCount(ValueAnimator.INFINITE);
        floatingAnim.setRepeatMode(ValueAnimator.REVERSE);
        floatingAnim.start();
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
        if (floatingAnim != null) { floatingAnim.cancel(); floatingAnim = null; }
        if (shimmerAnim != null) { shimmerAnim.cancel(); shimmerAnim = null; }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}

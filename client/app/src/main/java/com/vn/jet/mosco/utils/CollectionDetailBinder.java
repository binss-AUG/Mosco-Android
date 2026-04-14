package com.vn.jet.mosco.utils;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.model.CollectionEntry;
import com.vn.jet.mosco.MainActivity;
import com.vn.jet.mosco.fragment.ShopFragment;
import com.vn.jet.mosco.GiftActivity;
import android.content.Intent;
import android.widget.Toast;
import android.view.ViewGroup;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

/**
 * CollectionDetailBinder — Xử lý giao diện chi tiết Thẻ bài trong Album.
 * Thiết kế phong cách Galactic Premium kèm hiệu ứng Showcase
 * (Glow + Shimmer + Floating) giống HomeFragment.
 * Hỗ trợ lật thẻ vật lý (Interactive Drag-to-Flip) với cơ chế Elastic Snap.
 */
public class CollectionDetailBinder {

    /**
     * Hiển thị hộp thoại chi tiết Thẻ bài trong Collection Book (Album).
     * Tích hợp đầy đủ hiệu ứng Showcase từ HomeFragment.
     */
    public static void showDetail(Context context, CollectionEntry entry) {
        if (context == null || entry == null) return;

        Dialog dialog = new Dialog(context);
        dialog.setContentView(R.layout.dialog_collection_detail);
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        // 1. Bind Hình ảnh thẻ bài (Sử dụng Local First)
        ImageView ivCard = dialog.findViewById(R.id.card_iv_image);
        if (ivCard != null) {
            String imageUrl = entry.getFrontImage();
            java.io.File localFile = CardAssetManager.getLocalFile(context, imageUrl);
            Glide.with(context)
                    .load(localFile != null && localFile.exists() ? localFile : imageUrl)
                    .placeholder(R.drawable.item_shop_demo)
                    .into(ivCard);
        }

        // 2. Bind OVR & Grade Badge
        TextView tvOvr = dialog.findViewById(R.id.card_tv_ovr);
        if (tvOvr != null) {
            tvOvr.setText(String.valueOf(entry.getOvr()));
            tvOvr.setVisibility(View.GONE);
        }

        ImageView ivLevel = dialog.findViewById(R.id.card_iv_level);
        int entryLevel = entry.getLevel();
        if (ivLevel != null) {
            if (entryLevel > 0) {
                String assetPath = "file:///android_asset/grade/" + entryLevel + ".png";
                Glide.with(context).load(assetPath).into(ivLevel);
                ivLevel.setVisibility(View.VISIBLE);
            } else {
                ivLevel.setVisibility(View.GONE);
            }
        }

        // 3. Bind Metadata (Đã loại bỏ hoàn toàn theo yêu cầu minimalism)
        
        // 4. Trạng thái chưa sở hữu (Locked Overlay vs Unlock UI)
        ImageView ivLock = dialog.findViewById(R.id.iv_detail_lock);
        View lockedOverlay = dialog.findViewById(R.id.layout_locked_overlay);
        
        if (ivLock != null) ivLock.setVisibility(entry.isOwned() ? View.GONE : View.VISIBLE);
        if (lockedOverlay != null) lockedOverlay.setVisibility(entry.isOwned() ? View.GONE : View.VISIBLE);

        // 5. Hệ thống Ghost Buttons (Title + Description + Logic)
        View layoutLocked = dialog.findViewById(R.id.layout_buttons_locked);
        View layoutOwned = dialog.findViewById(R.id.layout_buttons_owned);

        if (!entry.isOwned()) {
            if (layoutOwned != null) layoutOwned.setVisibility(View.GONE);
            if (layoutLocked != null) {
                layoutLocked.setVisibility(View.VISIBLE);

                // Button 1: Gacha Spin
                View btnSpin = dialog.findViewById(R.id.item_btn_spin);
                setupGhostButton(btnSpin, 
                    context.getString(R.string.btn_spin_title),
                    context.getString(R.string.btn_spin_desc),
                    v -> {
                        if (context instanceof MainActivity) ((MainActivity) context).selectTab(R.id.nav_spin);
                        dialog.dismiss();
                    });

                // Button 2: Open Shop
                View btnShop = dialog.findViewById(R.id.item_btn_shop);
                setupGhostButton(btnShop,
                    context.getString(R.string.btn_shop_title),
                    context.getString(R.string.btn_shop_desc),
                    v -> {
                        if (context instanceof AppCompatActivity) {
                            ((AppCompatActivity) context).getSupportFragmentManager().beginTransaction()
                                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                                    .replace(R.id.frame_layout, new ShopFragment())
                                    .addToBackStack(null).commit();
                        }
                        dialog.dismiss();
                    });
            }
        } else {
            if (layoutLocked != null) layoutLocked.setVisibility(View.GONE);
            if (layoutOwned != null) {
                layoutOwned.setVisibility(View.VISIBLE);

                // Kiểm tra xem thực tế sếp có đang cầm thẻ này không (userCardId != null && > 0)
                boolean isInInventory = entry.getUserCardId() != null && entry.getUserCardId() > 0;

                // Button 1: Capture Photo (Luôn cho phép vì đã từng sở hữu)
                View btnPhoto = dialog.findViewById(R.id.item_btn_take_photo);
                setupGhostButton(btnPhoto,
                    context.getString(R.string.btn_photo_title),
                    context.getString(R.string.btn_photo_desc),
                    v -> Toast.makeText(context, "Coming soon!", Toast.LENGTH_SHORT).show());

                // Logic cho "Get more" (Chỉ hiện khi ĐÃ TỪNG CÓ nhưng HIỆN KHÔNG CÒN)
                View layoutGetMore = dialog.findViewById(R.id.layout_get_more_expandable);
                View btnSend = dialog.findViewById(R.id.item_btn_send);
                View btnUpgrade = dialog.findViewById(R.id.item_btn_upgrade);
                View spacer1 = dialog.findViewById(R.id.spacer_owned_1);
                View spacer2 = dialog.findViewById(R.id.spacer_owned_2);

                if (!isInInventory) {
                    // TRẠNG THÁI: TỪNG CÓ NHƯNG ĐÃ BAY MÀU
                    if (layoutGetMore != null) {
                        layoutGetMore.setVisibility(View.VISIBLE);
                        setupGetMoreInteraction(dialog, context);
                    }
                    if (btnSend != null) btnSend.setVisibility(View.GONE);
                    if (btnUpgrade != null) btnUpgrade.setVisibility(View.GONE);
                    if (spacer1 != null) spacer1.setVisibility(View.GONE);
                    if (spacer2 != null) spacer2.setVisibility(View.GONE);
                } else {
                    // TRẠNG THÁI: ĐANG CẦM TRÊN TAY
                    if (layoutGetMore != null) layoutGetMore.setVisibility(View.GONE);
                    if (btnSend != null) {
                        btnSend.setVisibility(View.VISIBLE);
                        setupGhostButton(btnSend,
                            context.getString(R.string.btn_send_title),
                            context.getString(R.string.btn_send_desc),
                            v -> {
                                Intent intent = new Intent(context, GiftActivity.class);
                                intent.putExtra("target_collection_id", entry.getCollectionId());
                                context.startActivity(intent);
                                dialog.dismiss();
                            });
                    }
                    if (btnUpgrade != null) {
                        btnUpgrade.setVisibility(View.VISIBLE);
                        setupGhostButton(btnUpgrade,
                            context.getString(R.string.btn_upgrade_title),
                            context.getString(R.string.btn_upgrade_desc),
                            v -> {
                                if (context instanceof MainActivity) ((MainActivity) context).selectTab(R.id.nav_stage);
                                dialog.dismiss();
                            });
                    }
                    if (spacer1 != null) spacer1.setVisibility(View.VISIBLE);
                    if (spacer2 != null) spacer2.setVisibility(View.VISIBLE);
                }
            }
        }

        // 6. Áp dụng hiệu ứng Showcase & Lật thẻ (Chỉ khi ĐÃ sở hữu)
        MaterialCardView cvCard = dialog.findViewById(R.id.cv_album_card_container);
        View viewShimmer = dialog.findViewById(R.id.view_card_shimmer);
        
        if (entry.isOwned()) {
            // Hiệu ứng Showcase (Glow + Shimmer + Floating)
            if (cvCard != null) {
                CardEffectHelper.apply(cvCard, viewShimmer, entry, true);
            }

            // Hiệu ứng lật thẻ vật lý
            ImageView ivBack = dialog.findViewById(R.id.iv_collection_back);
            String backImageUrl = "";
            if (entry.getCollectionId() != null) {
                org.json.JSONObject cardJson = DatabaseLoader.findById(context, entry.getCollectionId());
                if (cardJson != null) {
                    backImageUrl = cardJson.optString("backImage", "");
                }
            }
            if (ivBack != null && !backImageUrl.isEmpty()) {
                java.io.File localBack = CardAssetManager.getLocalFile(context, backImageUrl);
                Glide.with(context)
                        .load(localBack != null && localBack.exists() ? localBack : backImageUrl)
                        .placeholder(R.drawable.objet_back_spin)
                        .into(ivBack);
            }

            if (cvCard != null) {
                setupInteractiveFlip(context, dialog, cvCard, ivCard, ivBack, ivLevel, entry.getLevel());
            }
        } else {
            // Nếu chưa sở hữu: Ẩn Shimmer để hiện diện lớp xám Sad
            if (viewShimmer != null) viewShimmer.setVisibility(View.GONE);
        }

        // 8. Đóng Dialog
        View btnClose = dialog.findViewById(R.id.btn_close_collection_detail);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> {
                if (cvCard != null) CardEffectHelper.remove(cvCard, viewShimmer);
                dialog.dismiss();
            });
        }

        dialog.show();
    }

    /**
     * Cấu hình Ghost Button chuyên nghiệp với Tiêu đề và Mô tả.
     */
    private static void setupGhostButton(View itemView, String title, String desc, View.OnClickListener listener) {
        if (itemView == null) return;
        TextView tvTitle = itemView.findViewById(R.id.tv_ghost_title);
        TextView tvDesc = itemView.findViewById(R.id.tv_ghost_desc);
        if (tvTitle != null) tvTitle.setText(title);
        if (tvDesc != null) tvDesc.setText(desc);
        itemView.setOnClickListener(listener);
    }

    /**
     * Thiết lập cơ chế lật thẻ vật lý — đồng bộ góc xoay theo điểm chạm.
     * Đọc cấu hình độ nhạy từ resource (R.integer.card_flip_sensitivity).
     * Sử dụng OvershootInterpolator cho hiệu ứng Elastic Snap khi buông tay.
     */
    private static void setupInteractiveFlip(Context context, Dialog dialog,
                                              MaterialCardView cvCard,
                                              ImageView ivFront, ImageView ivBack,
                                              ImageView ivLevel, int upgradeLevel) {
        float density = context.getResources().getDisplayMetrics().density;
        cvCard.setCameraDistance(8000 * density);

        // Đọc cấu hình độ nhạy xoay từ resource (không hardcode)
        final int flipSensitivity = context.getResources().getInteger(R.integer.card_flip_sensitivity);
        final boolean[] isFlipped = {false};
        final float[] initialTouchX = {0f};
        final float[] startRotation = {0f};
        final ObjectAnimator[] snapAnim = {null};

        // Tham chiếu shimmer (đã nằm trong card, xoay tự nhiên)
        View shimmerContainer = dialog.findViewById(R.id.layout_shimmer_container);

        cvCard.setOnTouchListener((v, event) -> {
            // Đọc glow ĐỘNG trên mỗi touch (vì CardEffectHelper tạo async qua Glide callback)
            View glow = (View) cvCard.getTag(R.id.view_progress_fill);
            if (glow != null) glow.setCameraDistance(8000 * density);

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    // Hủy animation snap đang chạy nếu có
                    if (snapAnim[0] != null && snapAnim[0].isRunning()) {
                        snapAnim[0].cancel();
                    }
                    initialTouchX[0] = event.getRawX();
                    startRotation[0] = cvCard.getRotationY();
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float diffX = event.getRawX() - initialTouchX[0];
                    float newRotation = startRotation[0] + (diffX / flipSensitivity);
                    cvCard.setRotationY(newRotation);
                    // Đồng bộ glow xoay cùng thẻ
                    if (glow != null) glow.setRotationY(newRotation);

                    // Tính toán mặt hiện tại dựa trên góc xoay
                    float normalized = newRotation % 360;
                    if (normalized < 0) normalized += 360;
                    boolean shouldShowBack = (normalized > 90 && normalized < 270);

                    if (shouldShowBack != isFlipped[0]) {
                        isFlipped[0] = shouldShowBack;
                        if (!shouldShowBack) {
                            // Hiện mặt trước
                            if (ivFront != null) ivFront.setVisibility(View.VISIBLE);
                            if (ivBack != null) ivBack.setVisibility(View.GONE);
                            if (shimmerContainer != null) shimmerContainer.setVisibility(View.VISIBLE);
                            if (ivLevel != null && upgradeLevel > 0) ivLevel.setVisibility(View.VISIBLE);
                        } else {
                            // Hiện mặt sau
                            if (ivFront != null) ivFront.setVisibility(View.GONE);
                            if (ivBack != null) {
                                ivBack.setVisibility(View.VISIBLE);
                                ivBack.setScaleX(-1f);
                                ivBack.setAlpha(1f);
                            }
                            if (shimmerContainer != null) shimmerContainer.setVisibility(View.GONE);
                            if (ivLevel != null) ivLevel.setVisibility(View.GONE);
                        }
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    // Snap về góc gần nhất (0° hoặc 180°) với hiệu ứng lò xo
                    float curRot = cvCard.getRotationY();
                    float norm = curRot % 360;
                    if (norm < 0) norm += 360;

                    float nearestAngle;
                    if (norm <= 90 || norm >= 270) {
                        nearestAngle = Math.round(curRot / 360f) * 360f;
                    } else {
                        nearestAngle = Math.round((curRot - 180f) / 360f) * 360f + 180f;
                    }

                    snapAnim[0] = ObjectAnimator.ofFloat(cvCard, "rotationY", curRot, nearestAngle);
                    snapAnim[0].setDuration(250);
                    snapAnim[0].setInterpolator(new OvershootInterpolator(1.2f));
                    // Đồng bộ glow xoay theo snap animation (đọc lại vì có thể đã tạo)
                    View snapGlow = (View) cvCard.getTag(R.id.view_progress_fill);
                    if (snapGlow != null) {
                        snapGlow.setCameraDistance(8000 * density);
                        snapAnim[0].addUpdateListener(animation -> 
                            snapGlow.setRotationY((float) animation.getAnimatedValue()));
                    }
                    snapAnim[0].addListener(new android.animation.AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(android.animation.Animator animation) {
                            // Kiểm tra lại mặt sau khi snap xong
                            float finalNorm = cvCard.getRotationY() % 360;
                            if (finalNorm < 0) finalNorm += 360;
                            boolean finalBack = (finalNorm > 90 && finalNorm < 270);
                            if (finalBack != isFlipped[0]) {
                                isFlipped[0] = finalBack;
                                if (!finalBack) {
                                    if (ivFront != null) ivFront.setVisibility(View.VISIBLE);
                                    if (ivBack != null) ivBack.setVisibility(View.GONE);
                                    if (shimmerContainer != null) shimmerContainer.setVisibility(View.VISIBLE);
                                } else {
                                    if (ivFront != null) ivFront.setVisibility(View.GONE);
                                    if (ivBack != null) {
                                        ivBack.setVisibility(View.VISIBLE);
                                        ivBack.setScaleX(-1f);
                                    }
                                    if (shimmerContainer != null) shimmerContainer.setVisibility(View.GONE);
                                }
                            }
                        }
                    });
                    snapAnim[0].start();
                    return true;
            }
            return false;
        });
    }
    /**
     * Thiết lập tương tác mở rộng cho cụm "Get more".
     * Xoay mũi tên 180 độ và Fade-in các nút bổ trợ Shop/Spin.
     */
    private static void setupGetMoreInteraction(Dialog dialog, Context context) {
        View header = dialog.findViewById(R.id.btn_get_more_header);
        View content = dialog.findViewById(R.id.layout_get_more_content);
        ImageView arrow = dialog.findViewById(R.id.iv_get_more_arrow);

        if (header == null || content == null || arrow == null) return;

        final boolean[] isExpanded = {false};

        header.setOnClickListener(v -> {
            isExpanded[0] = !isExpanded[0];

            // 1. Xoay mũi tên (0 -> 180)
            ObjectAnimator.ofFloat(arrow, "rotation", isExpanded[0] ? 180f : 0f)
                    .setDuration(300)
                    .start();

            // 2. Hiệu ứng Fade + Visibility cho Content
            if (isExpanded[0]) {
                content.setVisibility(View.VISIBLE);
                content.animate()
                        .alpha(1f)
                        .setDuration(300)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .start();
            } else {
                content.animate()
                        .alpha(0f)
                        .setDuration(200)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .withEndAction(() -> content.setVisibility(View.GONE))
                        .start();
            }
        });

        // Thiết lập sự kiện cho các nút Shop/Spin thứ cấp
        View btnShopSec = dialog.findViewById(R.id.item_btn_shop_secondary);
        setupGhostButton(btnShopSec,
            context.getString(R.string.btn_shop_title),
            context.getString(R.string.btn_shop_desc),
            v -> {
                if (context instanceof AppCompatActivity) {
                    ((AppCompatActivity) context).getSupportFragmentManager().beginTransaction()
                            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                            .replace(R.id.frame_layout, new ShopFragment())
                            .addToBackStack(null).commit();
                }
                dialog.dismiss();
            });

        View btnSpinSec = dialog.findViewById(R.id.item_btn_spin_secondary);
        setupGhostButton(btnSpinSec,
            context.getString(R.string.btn_spin_title),
            context.getString(R.string.btn_spin_desc),
            v -> {
                if (context instanceof MainActivity) ((MainActivity) context).selectTab(R.id.nav_spin);
                dialog.dismiss();
            });
    }
}

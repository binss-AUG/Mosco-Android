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
import com.vn.jet.mosco.model.CardDisplayItem;
import com.vn.jet.mosco.MainActivity;
import com.vn.jet.mosco.fragment.ShopFragment;
import com.vn.jet.mosco.GiftActivity;
import android.content.Intent;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

/**
 * CollectionDetailBinder — Xử lý giao diện chi tiết Thẻ bài trong Album.
 * Thiết kế phong cách Galactic Premium kèm hiệu ứng Showcase
 * (Glow + Shimmer + Floating) giống HomeFragment.
 * Hỗ trợ lật thẻ vật lý (Interactive Drag-to-Flip) với cơ chế Elastic Snap.
 */
public class CollectionDetailBinder {

    /**
     * Overload: Nhận CardDisplayItem (Unified Model) và chuyển sang CollectionEntry
     * để tương thích với logic hiện tại.
     */
    public static void showDetail(Context context, CardDisplayItem item) {
        showDetail(context, item, false, false, null);
    }

    public static void showDetail(Context context, CardDisplayItem item, Runnable onDismiss) {
        showDetail(context, item, false, false, onDismiss);
    }

    public static void showDetail(Context context, CardDisplayItem item, boolean isAlbumMode, Runnable onDismiss) {
        showDetail(context, item, null, isAlbumMode, false, onDismiss);
    }

    public static void showDetail(Context context, CardDisplayItem item, boolean isAlbumMode, boolean isFromExhibit, Runnable onDismiss) {
        showDetail(context, item, null, isAlbumMode, isFromExhibit, onDismiss);
    }

    public static void showDetail(Context context, CollectionEntry entry) {
        showDetail(context, entry, false, false, null);
    }

    public static void showDetail(Context context, CollectionEntry entry, Runnable onDismiss) {
        showDetail(context, entry, false, false, onDismiss);
    }

    public static void showDetail(Context context, CollectionEntry entry, boolean isAlbumMode, Runnable onDismiss) {
        CardDisplayItem item = CardDisplayItem.fromCollectionEntry(entry);
        showDetail(context, item, entry, isAlbumMode, false, onDismiss);
    }

    public static void showDetail(Context context, CollectionEntry entry, boolean isAlbumMode, boolean isFromExhibit, Runnable onDismiss) {
        CardDisplayItem item = CardDisplayItem.fromCollectionEntry(entry);
        showDetail(context, item, entry, isAlbumMode, isFromExhibit, onDismiss);
    }

    private static void showDetail(Context context, CardDisplayItem item, CollectionEntry legacyEntry, boolean isAlbumMode, boolean isFromExhibit, Runnable onDismiss) {
        if (context == null || (item == null && legacyEntry == null)) return;
        
        final boolean[] hasChanged = {false};

        BottomSheetDialog dialog = new BottomSheetDialog(context, R.style.TransparentBottomSheetDialog);
        dialog.setContentView(R.layout.dialog_collection_detail);
        
        dialog.setOnDismissListener(d -> {
            try {
                if (dialog.getWindow() != null && dialog.getWindow().getDecorView() != null) {
                    Object tag = dialog.getWindow().getDecorView().getTag();
                    if (tag instanceof androidx.media3.exoplayer.ExoPlayer) {
                        com.vn.jet.mosco.utils.MotionVideoHelper.releasePlayer((androidx.media3.exoplayer.ExoPlayer) tag);
                    }
                }
            } catch (Exception e) {}
            if (hasChanged[0] && onDismiss != null) onDismiss.run();
        });
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            dialog.getWindow().setDimAmount(0.0f);
        }

        // Configure BottomSheet to be full screen and expanded
        View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) {
            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
            behavior.setPeekHeight(context.getResources().getDisplayMetrics().heightPixels);
            
            // Adjust height to match parent precisely
            ViewGroup.LayoutParams layoutParams = bottomSheet.getLayoutParams();
            if (layoutParams != null) {
                layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
            }
            bottomSheet.setLayoutParams(layoutParams);
        }

        // Use legacyEntry if available (for fields that might be missing in item)
        final CollectionEntry entry = (legacyEntry != null) ? legacyEntry : CardDisplayItem.toCollectionEntry(item);

        // 1. Bind Hình ảnh thẻ bài
        ImageView ivCard = dialog.findViewById(R.id.card_iv_image);
        if (ivCard != null) {
            String imageUrl = entry.getFrontImage();
            // Load bản Original chất lượng cao cho màn hình chi tiết
            GlideBindingAdapter.loadImage(ivCard, imageUrl, false);

            if (!entry.isOwned()) {
                android.graphics.ColorMatrix matrix = new android.graphics.ColorMatrix();
                matrix.setSaturation(0f);
                ivCard.setColorFilter(new android.graphics.ColorMatrixColorFilter(matrix));
            } else {
                ivCard.clearColorFilter();
            }
        }

        // 1.5. Xử lý TextureView cho thẻ Motion (Apollo MP4s)
        final android.view.TextureView vvObjetVideo = dialog.findViewById(R.id.vv_objet_detail_video);
        final boolean isMotion = entry.getFrontVideoUrl() != null && !entry.getFrontVideoUrl().isEmpty() && entry.isOwned();
        final boolean[] isFlipped = {false};

        if (vvObjetVideo != null) {
            if (isMotion) {
                // Ẩn hoàn toàn (GONE) để ExoPlayer không render black frame
                // lên mặt TextureView trước khi video sẵn sàng
                vvObjetVideo.setVisibility(View.GONE);
                
                try {
                    androidx.media3.exoplayer.ExoPlayer player = com.vn.jet.mosco.utils.MotionVideoHelper.playMotionVideo(context, vvObjetVideo, entry.getFrontVideoUrl(), ivCard);
                    dialog.getWindow().getDecorView().setTag(player);

                    // Giải phóng tài nguyên ExoPlayer triệt để khi đóng dialog
                    // (Đã xử lý ở setOnDismissListener phía trên)
                    
                } catch (Exception e) {
                    e.printStackTrace();
                    if (ivCard != null) {
                        ivCard.setVisibility(View.VISIBLE);
                    }
                    vvObjetVideo.setVisibility(View.GONE);
                }
            } else {
                vvObjetVideo.setVisibility(View.GONE);
            }
        }

        // 2. Bind OVR & Grade Badge
        TextView tvOvr = dialog.findViewById(R.id.card_tv_ovr);
        if (tvOvr != null) {
            tvOvr.setText(String.valueOf(entry.getOvr()));
            tvOvr.setVisibility(View.GONE);
        }

        ImageView ivLevel = dialog.findViewById(R.id.card_iv_level);
        int upgradeGrade = entry.getUpgradeLevel();
        if (ivLevel != null) {
            if (isFromExhibit) {
                // Hiển thị cấp thẻ chính xác của Exhibit
                if (upgradeGrade > 0) {
                    String assetPath = context.getString(R.string.asset_grade_path) + upgradeGrade + ".png";
                    Glide.with(context).load(assetPath).into(ivLevel);
                    ivLevel.setVisibility(View.VISIBLE);
                    com.vn.jet.mosco.utils.LevelBadgeEffectHelper.apply(ivLevel, upgradeGrade);
                } else {
                    ivLevel.setVisibility(View.GONE);
                    com.vn.jet.mosco.utils.LevelBadgeEffectHelper.remove(ivLevel);
                }
            } else if (isAlbumMode) {
                ivLevel.setVisibility(View.GONE);
                com.vn.jet.mosco.utils.LevelBadgeEffectHelper.remove(ivLevel);
            } else if (upgradeGrade > 0) {
                String assetPath = context.getString(R.string.asset_grade_path) + upgradeGrade + ".png";
                Glide.with(context).load(assetPath).into(ivLevel);
                ivLevel.setVisibility(View.VISIBLE);
                com.vn.jet.mosco.utils.LevelBadgeEffectHelper.apply(ivLevel, upgradeGrade);
            } else {
                ivLevel.setVisibility(View.GONE);
                com.vn.jet.mosco.utils.LevelBadgeEffectHelper.remove(ivLevel);
            }
        }

        // 3. Bind Metadata (Title Center)
        TextView tvDetailName = dialog.findViewById(R.id.tv_detail_card_name);
        if (tvDetailName != null) {
            // Format mới: [Tên] [SeasonPrefix] [Số] — Ví dụ: Yooyeon B2 501Z
            String seasonPrefix = com.vn.jet.mosco.utils.NumberUtils.formatSeasonPrefix(entry.getSeason());
            String nameTag = seasonPrefix.isEmpty()
                    ? (entry.getMember() + " " + entry.getCollectionNo()).trim()
                    : (entry.getMember() + " " + seasonPrefix + " " + entry.getCollectionNo()).trim();
            tvDetailName.setText(nameTag);
        }
        
        View lockedOverlay = dialog.findViewById(R.id.layout_locked_overlay);
        
        if (lockedOverlay != null) lockedOverlay.setVisibility(entry.isOwned() ? View.GONE : View.VISIBLE);

        // 5. Liquid Glass Interactive Controls
        setupLiquidGlassControls(dialog, context, item, isAlbumMode, isFromExhibit, hasChanged);

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
            // Sử dụng backImage trực tiếp từ model thống nhất — không cần gọi DatabaseLoader.findById() nữa
            String backImageUrl = entry.getBackImage() != null ? entry.getBackImage() : "";
            // Fallback: Nếu server chưa trả backImage, tìm từ local JSON cache
            if (backImageUrl.isEmpty() && entry.getCollectionId() != null) {
                org.json.JSONObject cardJson = DatabaseLoader.findById(context, entry.getCollectionId());
                if (cardJson != null) {
                    backImageUrl = cardJson.optString("backImage", "");
                }
            }
            if (ivBack != null && !backImageUrl.isEmpty()) {
                // Load bản Original mặt sau cho hiệu ứng lật 3D
                GlideBindingAdapter.loadImage(ivBack, backImageUrl, false);
            }

            if (cvCard != null) {
                setupInteractiveFlip(context, dialog, cvCard, ivCard, ivBack, ivLevel, entry.getUpgradeLevel(), vvObjetVideo, isMotion, isFlipped);
            }
        } else {
            // Nếu chưa sở hữu: Ẩn Shimmer để hiện diện lớp xám Sad
            if (viewShimmer != null) viewShimmer.setVisibility(View.GONE);
        }

        dialog.show();
    }

    /**
     * [LIQUID GLASS] Setup new interactive controls for the redesigned detail UI.
     */
    private static void setupLiquidGlassControls(Dialog dialog, Context context, CardDisplayItem item, boolean isAlbumMode, boolean isFromExhibit, boolean[] hasChanged) {
        // --- TOP-LEFT: CLOSE ---
        View btnClose = dialog.findViewById(R.id.btn_close_detail);
        if (btnClose != null) {
            if (isFromExhibit) {
                // Xóa (ẩn) dấu X đóng khi xem chi tiết từ Exhibit để đảm bảo vẻ đẹp sang trọng và thao tác kéo thả tự nhiên
                btnClose.setVisibility(View.GONE);
            } else {
                btnClose.setVisibility(View.VISIBLE);
                // Đóng hộp thoại chi tiết để giải phóng tài nguyên giao diện và quay về danh sách chính
                btnClose.setOnClickListener(v -> dialog.dismiss());
            }
        }
        // --- TOP-RIGHT: GIFT (SEND) ---
        View btnSend = dialog.findViewById(R.id.btn_send_gift);
        if (btnSend != null) {
            btnSend.setVisibility((item.isOwned() && !isAlbumMode) ? View.VISIBLE : View.GONE);
            btnSend.setOnClickListener(v -> {
                Intent intent = new Intent(context, GiftActivity.class);
                intent.putExtra("target_collection_id", item.getCollectionId());
                context.startActivity(intent);
                dialog.dismiss();
            });
        }

        // --- BOTTOM-LEFT: PIN ---
        View btnPin = dialog.findViewById(R.id.btn_pin_card);
        ImageView ivPin = dialog.findViewById(R.id.iv_pin_icon);
        
        // Pin hidden in Album tab
        if (btnPin != null) {
            btnPin.setVisibility(isAlbumMode ? View.GONE : View.VISIBLE);
            if (!isAlbumMode) {
                String uniqueId = String.valueOf(item.getId());
                updatePinUI(context, uniqueId, ivPin);
                btnPin.setOnClickListener(v -> {
                    PinManager.togglePin(context, uniqueId);
                    updatePinUI(context, uniqueId, ivPin);
                    hasChanged[0] = true;
                });
            }
        }

        // --- BOTTOM-RIGHT: CAPTURE & UPGRADE ---
        View btnCapture = dialog.findViewById(R.id.btn_capture_photo);
        if (btnCapture != null) {
            btnCapture.setVisibility(isAlbumMode ? View.GONE : View.VISIBLE);
            btnCapture.setOnClickListener(v -> {
                Intent intent = new Intent(context, com.vn.jet.mosco.ObjetCameraActivity.class);
                intent.putExtra(com.vn.jet.mosco.ObjetCameraActivity.EXTRA_IMAGE_URL, item.getFrontImage());
                intent.putExtra("extra_back_image_url", item.getBackImage());
                intent.putExtra("extra_collection_id", item.getCollectionId());
                intent.putExtra("extra_bg_color", item.getBackgroundColor());
                intent.putExtra("extra_upgrade_level", item.getUpgradeLevel());
                context.startActivity(intent);
            });
        }

        View btnUpgrade = dialog.findViewById(R.id.btn_upgrade_detail);
        if (btnUpgrade != null) {
            btnUpgrade.setVisibility((item.isOwned() && !isAlbumMode) ? View.VISIBLE : View.GONE);
            btnUpgrade.setOnClickListener(v -> {
                if (context instanceof androidx.appcompat.app.AppCompatActivity) {
                    ((androidx.appcompat.app.AppCompatActivity) context).getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                        .add(R.id.frame_layout, com.vn.jet.mosco.fragment.UpgradeFragment.newInstance(item))
                        .addToBackStack(null)
                        .commit();
                }
                dialog.dismiss();
            });
        }
    }

    private static void updatePinUI(Context context, String cardId, ImageView ivPin) {
        boolean isPinned = PinManager.isPinned(context, cardId);
        int pinGoldColor = androidx.core.content.ContextCompat.getColor(context, R.color.collection_pin_gold);
        int pinUnselectedColor = androidx.core.content.ContextCompat.getColor(context, R.color.collection_pin_unselected);
        ivPin.setColorFilter(isPinned ? pinGoldColor : pinUnselectedColor); // Gold if pinned
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
                                              ImageView ivLevel, int upgradeLevel,
                                              android.view.TextureView vvObjetVideo, boolean isMotion, boolean[] isFlipped) {
        float density = context.getResources().getDisplayMetrics().density;
        android.util.TypedValue distVal = new android.util.TypedValue();
        context.getResources().getValue(R.dimen.flip_camera_distance_factor, distVal, true);
        cvCard.setCameraDistance(distVal.getFloat() * density);

        // Đọc cấu hình độ nhạy xoay từ resource (không hardcode)
        final int flipSensitivity = context.getResources().getInteger(R.integer.card_flip_sensitivity);
        final float[] initialTouchX = {0f};
        final float[] startRotation = {0f};
        final ObjectAnimator[] snapAnim = {null};

        // Tham chiếu shimmer (đã nằm trong card, xoay tự nhiên)
        View shimmerContainer = dialog.findViewById(R.id.layout_shimmer_container);

        cvCard.setOnTouchListener((v, event) -> {
            // Đọc glow ĐỘNG trên mỗi touch (vì CardEffectHelper tạo async qua Glide callback)
            View glow = (View) cvCard.getTag(R.id.view_progress_fill);
            if (glow != null) {
                android.util.TypedValue gDistVal = new android.util.TypedValue();
                context.getResources().getValue(R.dimen.flip_camera_distance_factor, gDistVal, true);
                glow.setCameraDistance(gDistVal.getFloat() * density);
            }

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
                            // MOTION VIDEO SUPPORT
                            if (vvObjetVideo != null && isMotion) {
                                        vvObjetVideo.setVisibility(View.VISIBLE);
                                        try {
                                            androidx.media3.exoplayer.ExoPlayer mp = (androidx.media3.exoplayer.ExoPlayer) dialog.getWindow().getDecorView().getTag();
                                            if (mp != null) mp.play();
                                        } catch (Exception e) {}
                                        
                                        // CHỈ Ẩn ảnh tĩnh nếu video đã thực sự tải xong
                                        if (Boolean.TRUE.equals(vvObjetVideo.getTag(R.id.vv_objet_detail_video))) {
                                            if (ivFront != null) ivFront.setVisibility(View.INVISIBLE);
                                        }
                            }
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
                            // MOTION VIDEO SUPPORT
                            if (vvObjetVideo != null) {
                                vvObjetVideo.setVisibility(View.INVISIBLE);
                                try {
                                    androidx.media3.exoplayer.ExoPlayer mp = (androidx.media3.exoplayer.ExoPlayer) dialog.getWindow().getDecorView().getTag();
                                    if (mp != null) mp.pause();
                                } catch (Exception e) {}
                            }
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

                    int flipDuration = context.getResources().getInteger(R.integer.flip_duration_ms);
                    snapAnim[0] = ObjectAnimator.ofFloat(cvCard, "rotationY", curRot, nearestAngle);
                    snapAnim[0].setDuration(flipDuration);
                    snapAnim[0].setInterpolator(new OvershootInterpolator(1.2f));
                    // Đồng bộ glow xoay theo snap animation (đọc lại vì có thể đã tạo)
                    View snapGlow = (View) cvCard.getTag(R.id.view_progress_fill);
                    if (snapGlow != null) {
                        android.util.TypedValue sgDistVal = new android.util.TypedValue();
                        context.getResources().getValue(R.dimen.flip_camera_distance_factor, sgDistVal, true);
                        snapGlow.setCameraDistance(sgDistVal.getFloat() * density);
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
                                    // MOTION VIDEO SUPPORT
                                    if (vvObjetVideo != null && isMotion) {
                                        vvObjetVideo.setVisibility(View.VISIBLE);
                                        try {
                                            androidx.media3.exoplayer.ExoPlayer mp = (androidx.media3.exoplayer.ExoPlayer) dialog.getWindow().getDecorView().getTag();
                                            if (mp != null) mp.play();
                                        } catch (Exception e) {}
                                        
                                        // CHỈ Ẩn ảnh tĩnh nếu video đã thực sự tải xong
                                        if (Boolean.TRUE.equals(vvObjetVideo.getTag(R.id.vv_objet_detail_video))) {
                                            if (ivFront != null) ivFront.setVisibility(View.INVISIBLE);
                                        }
                                    }
                                } else {
                                    if (ivFront != null) ivFront.setVisibility(View.GONE);
                                    if (ivBack != null) {
                                        ivBack.setVisibility(View.VISIBLE);
                                        ivBack.setScaleX(-1f);
                                    }
                                    if (shimmerContainer != null) shimmerContainer.setVisibility(View.GONE);
                                    // MOTION VIDEO SUPPORT
                                    if (vvObjetVideo != null) {
                                        vvObjetVideo.setVisibility(View.INVISIBLE);
                                        try {
                                            androidx.media3.exoplayer.ExoPlayer mp = (androidx.media3.exoplayer.ExoPlayer) dialog.getWindow().getDecorView().getTag();
                                            if (mp != null) mp.pause();
                                        } catch (Exception e) {}
                                    }
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
}

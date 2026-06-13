package com.vn.jet.mosco.utils;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.animation.ObjectAnimator;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.os.Build;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.model.CardDisplayItem;

/**
 * ObjetDetailBinder — Xử lý giao diện động cho hộp thoại chi tiết Thẻ bài (Objet).
 */
public class ObjetDetailBinder {
    
    /**
     * Hiển thị hộp thoại chi tiết Thẻ bài.
     */
    public static void showObjetDetail(android.content.Context context, com.vn.jet.mosco.model.Objet objet) {
        if (objet == null || context == null) return;
        
        android.app.Dialog dialog = new android.app.Dialog(context);
        dialog.setContentView(R.layout.dialog_objet_detail);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            dialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        bind(dialog, context, objet);

        dialog.findViewById(R.id.btn_close_detail).setOnClickListener(v -> dialog.dismiss());
        
        ImageView btnCamera = dialog.findViewById(R.id.btn_camera_detail);
        if (btnCamera != null) {
            btnCamera.setOnClickListener(v -> {
                android.content.Intent intent = new android.content.Intent(context, com.vn.jet.mosco.ObjetCameraActivity.class);
                intent.putExtra(com.vn.jet.mosco.ObjetCameraActivity.EXTRA_IMAGE_URL, objet.getImageUrl());
                intent.putExtra("extra_back_image_url", objet.getBackImageUrl());
                intent.putExtra("extra_collection_id", objet.getCollectionId());
                intent.putExtra("extra_bg_color", objet.getBackgroundColor());
                intent.putExtra("extra_upgrade_level", objet.getCardLevel());
                context.startActivity(intent);
            });
        }
        dialog.setOnDismissListener(d -> {
            try {
                if (dialog.getWindow() != null && dialog.getWindow().getDecorView() != null) {
                    Object tag = dialog.getWindow().getDecorView().getTag();
                    if (tag instanceof androidx.media3.exoplayer.ExoPlayer) {
                        com.vn.jet.mosco.utils.MotionVideoHelper.releasePlayer((androidx.media3.exoplayer.ExoPlayer) tag);
                    }
                }
            } catch (Exception e) {}
        });
        dialog.show();
    }

    /**
     * Liên kết dữ liệu từ JSON vào giao diện (thường dùng cho Collection/Inventory).
     */
    public static void bind(Dialog dialog, Context context, org.json.JSONObject cardJson, int level, int exp, int upgradeLevel) {
        try {
            com.vn.jet.mosco.model.Objet objet = new com.vn.jet.mosco.model.Objet(0, cardJson.optString("id"), cardJson.optString("frontImage"), level, exp, upgradeLevel);
            objet.setMember(cardJson.optString("member"));
            objet.setCollectionNo(cardJson.optString("collectionNo"));
            objet.setTypeKey(cardJson.optString("class"));
            objet.setSeason(cardJson.optString("season"));
            objet.setBackgroundColor(cardJson.optString("backgroundColor"));
            objet.setTextColor(cardJson.optString("textColor"));
            objet.setOvr(cardJson.optInt("ovr", 80));
            objet.setBackImageUrl(cardJson.optString("backImage", ""));
            bind(dialog, context, objet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Hàm bind chính — Xử lý logic hiển thị, màu sắc và hiệu ứng 3D.
     */
    public static void bind(Dialog dialog, Context context, com.vn.jet.mosco.model.Objet objet) {
        final boolean[] isFlipped = {false};
        try {
            // ── 1. Xử lý màu sắc chủ đạo ──────────────────────────────
            int bgColor;
            if (objet.getBackgroundColor() != null && objet.getBackgroundColor().startsWith("#")) {
                bgColor = Color.parseColor(objet.getBackgroundColor());
            } else {
                bgColor = androidx.core.content.ContextCompat.getColor(context, R.color.lg_accent_primary);
            }

            int txtColor;
            if (objet.getTextColor() != null && objet.getTextColor().startsWith("#")) {
                txtColor = Color.parseColor(objet.getTextColor());
            } else {
                txtColor = Color.WHITE;
            }
            
            // Màu sắc đại diện cho Class của thẻ
            int classColor = androidx.core.content.ContextCompat.getColor(context, R.color.mosco_btn_disabled);

            // ── 2. Trích xuất thông tin ────────────────────────────────
            String frontImageUrl = objet.getImageUrl();
            String member = objet.getMember() != null ? objet.getMember() : "Unknown";
            String collectionNo = objet.getCollectionNo() != null ? objet.getCollectionNo() : "";
            String cardClass = objet.getTypeKey() != null ? objet.getTypeKey() : "";
            String season = objet.getSeason() != null ? objet.getSeason() : "";
            int level = objet.getLevel();
            int exp = objet.getExp();
            int upgradeLevel = objet.getUpgradeLevel();

            // ── 3. Tải hình ảnh mặt trước ──────────────────────────────
            ImageView ivObjet = dialog.findViewById(R.id.card_iv_image);
            if (ivObjet != null && frontImageUrl != null && !frontImageUrl.isEmpty()) {
                // Su dung GlideBindingAdapter thong qua code Java (manual binding)
                com.vn.jet.mosco.utils.GlideBindingAdapter.loadImage(ivObjet, frontImageUrl, false);
            }

            // ── 3.5. Xử lý TextureView cho thẻ Motion (Apollo MP4s) ──────
            final android.view.TextureView vvObjetVideo = dialog.findViewById(R.id.vv_objet_detail_video);
            final boolean isMotion = objet.getFrontVideoUrl() != null && !objet.getFrontVideoUrl().isEmpty();

            if (vvObjetVideo != null) {
                if (isMotion) {
                    // Ẩn hoàn toàn (GONE) để ExoPlayer không render black frame
                    // lên mặt TextureView trước khi video sẵn sàng
                    vvObjetVideo.setVisibility(View.GONE);
                    
                    try {
                        androidx.media3.exoplayer.ExoPlayer player = com.vn.jet.mosco.utils.MotionVideoHelper.playMotionVideo(context, vvObjetVideo, objet.getFrontVideoUrl(), ivObjet);
                        dialog.getWindow().getDecorView().setTag(player);

                        // Giải phóng tài nguyên ExoPlayer triệt để khi đóng dialog
                        // (Đã xử lý ở setOnDismissListener phía trên, nhưng cẩn thận có thể đính kèm lại)
                    } catch (Exception e) {
                        e.printStackTrace();
                        if (ivObjet != null) {
                            ivObjet.setVisibility(View.VISIBLE);
                        }
                        vvObjetVideo.setVisibility(View.GONE);
                    }
                } else {
                    vvObjetVideo.setVisibility(View.GONE);
                }
            }

            // ── 3. Tính toán chỉ số (Stats) ──────────────────────────
            int hp = 100, atk = 10, def = 10, spd = 10;
            
            double levelBonus = (level - 1) * 0.05;
            double upgradeBonus = (upgradeLevel - 1) * 0.10;
            double multiplier = 1.0 + levelBonus + upgradeBonus;

            int finalHp = (int) (hp * multiplier);
            int finalAtk = (int) (atk * multiplier);
            int finalDef = (int) (def * multiplier);
            int finalSpd = (int) (spd * multiplier);
            
            int overall = objet.getOvr() > 0 ? objet.getOvr() : 80;

            TextView tvHp = dialog.findViewById(R.id.tv_stat_hp);
            if (tvHp != null) tvHp.setText(String.valueOf(finalHp));
            
            TextView tvAtk = dialog.findViewById(R.id.tv_stat_atk);
            if (tvAtk != null) tvAtk.setText(String.valueOf(finalAtk));
            
            TextView tvDef = dialog.findViewById(R.id.tv_stat_def);
            if (tvDef != null) tvDef.setText(String.valueOf(finalDef));
            
            TextView tvSpd = dialog.findViewById(R.id.tv_stat_spd);
            if (tvSpd != null) tvSpd.setText(String.valueOf(finalSpd));

            TextView tvCritRate = dialog.findViewById(R.id.tv_stat_crit_rate);
            if (tvCritRate != null) tvCritRate.setText(context.getString(R.string.format_qty, "10%"));
            
            TextView tvCritDmg = dialog.findViewById(R.id.tv_stat_crit_dmg);
            if (tvCritDmg != null) tvCritDmg.setText(context.getString(R.string.format_qty, "150%"));

            // ── 4. Hiển thị Tiêu đề, Badge & Dual-tone Chip ─────────────
            TextView tvTitle = dialog.findViewById(R.id.tv_objet_title);
            if (tvTitle != null) {
                // Format mới: [Tên] [SeasonPrefix] [Số] — Ví dụ: Yooyeon B2 501Z
                String seasonPrefix = com.vn.jet.mosco.utils.NumberUtils.formatSeasonPrefix(season);
                String titleText = seasonPrefix.isEmpty()
                        ? (member + " " + collectionNo).trim()
                        : (member + " " + seasonPrefix + " " + collectionNo).trim();
                tvTitle.setText(titleText);
                tvTitle.setTextColor(Color.WHITE);
            }

            TextView tvOvr = dialog.findViewById(R.id.card_tv_ovr);
            if (tvOvr != null) {
                tvOvr.setText(String.valueOf(overall));
                tvOvr.setVisibility(View.GONE);
            }

            ImageView ivLevel = dialog.findViewById(R.id.card_iv_level);
            if (ivLevel != null) {
                if (upgradeLevel > 0) {
                    String assetPath = "file:///android_asset/grade/" + upgradeLevel + ".png";
                    Glide.with(context).load(assetPath).into(ivLevel);
                    ivLevel.setVisibility(View.VISIBLE);
                    com.vn.jet.mosco.utils.LevelBadgeEffectHelper.apply(ivLevel, upgradeLevel);
                } else {
                    ivLevel.setVisibility(View.GONE);
                    com.vn.jet.mosco.utils.LevelBadgeEffectHelper.remove(ivLevel);
                }
            }

            TextView tvBadge = dialog.findViewById(R.id.tv_badge_class);
            if (tvBadge != null) {
                tvBadge.setText(cardClass);
                tvBadge.setTextColor(txtColor);
            }

            // Hiệu ứng dải màu kép cho Chip thông tin
            View llDualToneChip = dialog.findViewById(R.id.ll_dual_tone_chip);
            if (llDualToneChip != null) {
                int[] leftColors;
                if ("Special".equalsIgnoreCase(cardClass)) {
                    leftColors = new int[]{
                            androidx.core.content.ContextCompat.getColor(context, R.color.palette_pink_soft_alt),
                            androidx.core.content.ContextCompat.getColor(context, R.color.palette_blue_powder),
                            androidx.core.content.ContextCompat.getColor(context, R.color.palette_pink_lavender),
                            Color.WHITE
                    };
                } else {
                    leftColors = new int[]{bgColor};
                }
                llDualToneChip.setBackground(new HardStopGradientDrawable(leftColors, classColor, 0.3f));
            }

            // ── 5. Thiết lập màu nền & bo góc cho Dialog ─────────────────
            MaterialCardView cvRoot = dialog.findViewById(R.id.cv_dialog_root);
            int strokeWidth = dpToPx(context, 1);
            int blurredBorderColor = androidx.core.graphics.ColorUtils.setAlphaComponent(bgColor, 128);

            int topColor = androidx.core.content.ContextCompat.getColor(context, R.color.lg_background);
            int bottomColor = androidx.core.graphics.ColorUtils.blendARGB(topColor, bgColor, 0.5f);
            GradientDrawable rootBg = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{topColor, bottomColor}
            );
            rootBg.setCornerRadius(dpToPx(context, 16));

            if (cvRoot != null) {
                cvRoot.setCardBackgroundColor(Color.TRANSPARENT);
                cvRoot.setStrokeWidth(strokeWidth);
                cvRoot.setStrokeColor(blurredBorderColor);
                View inner = cvRoot.getChildAt(0);
                if (inner != null) {
                    inner.setBackground(rootBg);
                }
            }

            // ── 6. Xử lý Container hình ảnh & Hiệu ứng ──────────────────
            MaterialCardView cvImageContainer = dialog.findViewById(R.id.cv_objet_image_container);
            ImageView ivDetailBack = dialog.findViewById(R.id.iv_objet_detail_back);
            View shimmer = dialog.findViewById(R.id.view_card_shimmer);

            if (cvImageContainer != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    cvImageContainer.setCardElevation(dpToPx(context, 16));
                    cvImageContainer.setOutlineAmbientShadowColor(classColor);
                    cvImageContainer.setOutlineSpotShadowColor(classColor);
                }
                cvImageContainer.setStrokeWidth(strokeWidth);
                cvImageContainer.setStrokeColor(blurredBorderColor);

                // Áp dụng hiệu ứng Showcase (Glow + Shimmer + Floating)
                CardEffectHelper.apply(cvImageContainer, shimmer, objet, true);

                // ── 7. Lật thẻ 3D (3D FLIP) ──────────────────────────────
                String backImageUrl = objet.getBackImageUrl();
                if (ivDetailBack != null && backImageUrl != null && !backImageUrl.isEmpty()) {
                    // Load mat sau (Original) nhung khong hien ngay
                    com.vn.jet.mosco.utils.GlideBindingAdapter.loadImage(ivDetailBack, backImageUrl, false);
                }

                float density = context.getResources().getDisplayMetrics().density;
                cvImageContainer.setCameraDistance(8000 * density);

                // Đọc cấu hình độ nhạy xoay từ resource (không hardcode)
                final int flipSensitivity = context.getResources().getInteger(R.integer.card_flip_sensitivity);
                final float[] initialTouchX = {0f};
                final float[] startRotation = {0f};
                final ObjectAnimator[] snapAnim = {null};

                cvImageContainer.setOnTouchListener((v, event) -> {
                    switch (event.getActionMasked()) {
                        case android.view.MotionEvent.ACTION_DOWN:
                            // Hủy animation snap đang chạy nếu có
                            if (snapAnim[0] != null && snapAnim[0].isRunning()) {
                                snapAnim[0].cancel();
                            }
                            initialTouchX[0] = event.getRawX();
                            startRotation[0] = cvImageContainer.getRotationY();
                            v.getParent().requestDisallowInterceptTouchEvent(true);
                            return true;

                        case android.view.MotionEvent.ACTION_MOVE:
                            float diffX = event.getRawX() - initialTouchX[0];
                            float newRotation = startRotation[0] + (diffX / flipSensitivity);
                            
                            cvImageContainer.setRotationY(newRotation);

                            // Tính toán mặt hiện tại dựa trên góc xoay
                            float normalized = newRotation % 360;
                            if (normalized < 0) normalized += 360;
                            boolean shouldShowBack = (normalized > 90 && normalized < 270);

                            if (shouldShowBack != isFlipped[0]) {
                                isFlipped[0] = shouldShowBack;
                                if (!shouldShowBack) {
                                    if (isMotion && vvObjetVideo != null) {
                                        vvObjetVideo.setVisibility(View.VISIBLE);
                                        try {
                                            androidx.media3.exoplayer.ExoPlayer mp = (androidx.media3.exoplayer.ExoPlayer) dialog.getWindow().getDecorView().getTag();
                                            if (mp != null) mp.play();
                                        } catch (Exception e) {}
                                        
                                        // CHỈ Ẩn ảnh tĩnh nếu video đã thực sự tải xong
                                        if (Boolean.TRUE.equals(vvObjetVideo.getTag(R.id.vv_objet_detail_video))) {
                                            if (ivObjet != null) ivObjet.setVisibility(View.INVISIBLE);
                                        }
                                    } else {
                                        if (ivObjet != null) ivObjet.setVisibility(View.VISIBLE);
                                    }
                                    if (ivDetailBack != null) ivDetailBack.setVisibility(View.GONE);
                                    View shimmerContainer = dialog.findViewById(R.id.layout_shimmer_container);
                                    if (shimmerContainer != null) shimmerContainer.setVisibility(View.VISIBLE);
                                    ImageView ivLevelFlip = dialog.findViewById(R.id.card_iv_level);
                                    if (ivLevelFlip != null && upgradeLevel > 0) ivLevelFlip.setVisibility(View.VISIBLE);
                                } else {
                                    if (isMotion && vvObjetVideo != null) {
                                        vvObjetVideo.setVisibility(View.INVISIBLE);
                                        try {
                                            androidx.media3.exoplayer.ExoPlayer mp = (androidx.media3.exoplayer.ExoPlayer) dialog.getWindow().getDecorView().getTag();
                                            if (mp != null) mp.pause();
                                        } catch (Exception e) {}
                                    }
                                    if (ivObjet != null) ivObjet.setVisibility(View.GONE);
                                    if (ivDetailBack != null) {
                                        ivDetailBack.setVisibility(View.VISIBLE);
                                        ivDetailBack.setScaleX(-1f);
                                        ivDetailBack.setAlpha(1f);
                                    }
                                    View shimmerContainer = dialog.findViewById(R.id.layout_shimmer_container);
                                    if (shimmerContainer != null) shimmerContainer.setVisibility(View.GONE);
                                    ImageView ivLevelFlip = dialog.findViewById(R.id.card_iv_level);
                                    if (ivLevelFlip != null) ivLevelFlip.setVisibility(View.GONE);
                                }
                            }
                            return true;

                        case android.view.MotionEvent.ACTION_UP:
                        case android.view.MotionEvent.ACTION_CANCEL:
                            v.getParent().requestDisallowInterceptTouchEvent(false);
                            // Snap về góc gần nhất (0° hoặc 180°)
                            float curRot = cvImageContainer.getRotationY();
                            float norm = curRot % 360;
                            if (norm < 0) norm += 360;

                            float nearestAngle;
                            if (norm <= 90 || norm >= 270) {
                                nearestAngle = Math.round(curRot / 360f) * 360f;
                            } else {
                                nearestAngle = Math.round((curRot - 180f) / 360f) * 360f + 180f;
                            }

                            snapAnim[0] = ObjectAnimator.ofFloat(cvImageContainer, "rotationY", curRot, nearestAngle);
                            snapAnim[0].setDuration(250);
                            snapAnim[0].setInterpolator(new android.view.animation.OvershootInterpolator(1.2f));
                            snapAnim[0].addListener(new android.animation.AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationStart(android.animation.Animator animation) {
                                    cvImageContainer.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                                }
                                @Override
                                public void onAnimationEnd(android.animation.Animator animation) {
                                    // Tat Hardware Layer de giai phong tai nguyen GPU
                                    cvImageContainer.setLayerType(View.LAYER_TYPE_NONE, null);
                                    
                                    // Kiểm tra lại mặt sau khi snap xong
                                    float finalNorm = cvImageContainer.getRotationY() % 360;
                                    if (finalNorm < 0) finalNorm += 360;
                                    boolean finalBack = (finalNorm > 90 && finalNorm < 270);
                                    if (finalBack != isFlipped[0]) {
                                        isFlipped[0] = finalBack;
                                        if (!finalBack) {
                                            if (isMotion && vvObjetVideo != null) {
                                                vvObjetVideo.setVisibility(View.VISIBLE);
                                                try {
                                                    androidx.media3.exoplayer.ExoPlayer mp = (androidx.media3.exoplayer.ExoPlayer) dialog.getWindow().getDecorView().getTag();
                                                    if (mp != null) mp.play();
                                                } catch (Exception e) {}
                                                
                                                // CHỈ Ẩn ảnh tĩnh nếu video đã thực sự tải xong
                                                if (Boolean.TRUE.equals(vvObjetVideo.getTag(R.id.vv_objet_detail_video))) {
                                                    if (ivObjet != null) ivObjet.setVisibility(View.INVISIBLE);
                                                }
                                            } else {
                                                if (ivObjet != null) ivObjet.setVisibility(View.VISIBLE);
                                            }
                                            if (ivDetailBack != null) ivDetailBack.setVisibility(View.GONE);
                                            View shimmerContainer = dialog.findViewById(R.id.layout_shimmer_container);
                                            if (shimmerContainer != null) shimmerContainer.setVisibility(View.VISIBLE);
                                        } else {
                                            if (isMotion && vvObjetVideo != null) {
                                                try {
                                                    androidx.media3.exoplayer.ExoPlayer mp = (androidx.media3.exoplayer.ExoPlayer) dialog.getWindow().getDecorView().getTag();
                                                    if (mp != null) mp.pause();
                                                } catch (Exception e) {}
                                            }
                                            if (ivObjet != null) ivObjet.setVisibility(View.GONE);
                                            if (ivDetailBack != null) {
                                                ivDetailBack.setVisibility(View.VISIBLE);
                                                ivDetailBack.setScaleX(-1f);
                                            }
                                            View shimmerContainer = dialog.findViewById(R.id.layout_shimmer_container);
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

            // ── 8. Chức năng Nâng cấp (Upgrade) ──────────────────────────
            MaterialButton btnUpgrade = dialog.findViewById(R.id.btn_upgrade_detail);
            if (btnUpgrade != null) {
                btnUpgrade.setOnClickListener(v -> {
                    dialog.dismiss();
                    if (context instanceof androidx.appcompat.app.AppCompatActivity) {
                        androidx.appcompat.app.AppCompatActivity activity = (androidx.appcompat.app.AppCompatActivity) context;
                        com.vn.jet.mosco.fragment.UpgradeFragment upgradeFragment = com.vn.jet.mosco.fragment.UpgradeFragment.newInstance();
                        if (objet != null) {
                            upgradeFragment.setMainCard(CardDisplayItem.fromObjet(objet));
                            activity.getSupportFragmentManager().beginTransaction()
                                    .replace(R.id.frame_layout, upgradeFragment)
                                    .addToBackStack(null)
                                    .commit();
                            com.google.android.material.bottomnavigation.BottomNavigationView bottomNav = activity.findViewById(R.id.bottom_navigation);
                            if (bottomNav != null) {
                                bottomNav.setSelectedItemId(R.id.nav_stage);
                            }
                        }
                    }
                });
            }
            
            // ── 9. Phần hiển thị Level & EXP ────────────────────────────
            View clLevelSection = dialog.findViewById(R.id.cl_level_section);
            if (clLevelSection != null) {
                GradientDrawable levelBg = new GradientDrawable();
                levelBg.setColor(Color.TRANSPARENT);
                levelBg.setCornerRadius(dpToPx(context, 12));
                levelBg.setStroke(strokeWidth, blurredBorderColor);
                clLevelSection.setBackground(levelBg);
            }

            TextView tvLevelLabel = dialog.findViewById(R.id.tv_level_label);
            if (tvLevelLabel != null) {
                tvLevelLabel.setText(context.getString(R.string.format_level, level));
                tvLevelLabel.setTextColor(txtColor);
            }
 
            int maxExp = level * 100;
            TextView tvLevelValue = dialog.findViewById(R.id.tv_level_value);
            if (tvLevelValue != null) {
                tvLevelValue.setText(context.getString(R.string.format_fraction, String.valueOf(exp), String.valueOf(maxExp)));
                tvLevelValue.setTextColor(txtColor);
            }

            ProgressBar pbLevel = dialog.findViewById(R.id.pb_level);
            if (pbLevel != null) {
                pbLevel.setMax(maxExp);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    pbLevel.setProgress(exp, true);
                } else {
                    pbLevel.setProgress(exp);
                }
            }

            MaterialCardView cvInfoBox = dialog.findViewById(R.id.cv_info_box);
            if (cvInfoBox != null) {
                cvInfoBox.setStrokeWidth(strokeWidth);
                cvInfoBox.setStrokeColor(blurredBorderColor);
                cvInfoBox.setAlpha(0.8f);
            }

            // ── 10. Chức năng Rã thẻ (Recycle) ───────────────────────────
            ImageView btnRecycle = dialog.findViewById(R.id.btn_recycle_detail);
            if (btnRecycle != null) {
                int disabledColor = androidx.core.content.ContextCompat.getColor(context, R.color.lg_text_disabled);
                btnRecycle.setImageTintList(ColorStateList.valueOf(disabledColor));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static int dpToPx(Context ctx, int dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }

    /**
     * Lớp vẽ Drawable hỗ trợ dải màu gradient dừng cứng (Hard Stop).
     */
    private static class HardStopGradientDrawable extends android.graphics.drawable.Drawable {
        private final android.graphics.Paint paint;
        private final int[] leftColors;
        private final int rightColor;
        private final float stopPoint;
        private final android.graphics.Path clipPath = new android.graphics.Path();
        private final android.graphics.RectF rectF = new android.graphics.RectF();

        public HardStopGradientDrawable(int[] leftColors, int rightColor, float stopPoint) {
            this.paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            this.leftColors = leftColors;
            this.rightColor = rightColor;
            this.stopPoint = stopPoint;
        }

        @Override
        protected void onBoundsChange(android.graphics.Rect bounds) {
            super.onBoundsChange(bounds);
            float width = bounds.width();
            float height = bounds.height();
            float cornerRadius = height / 2.0f;
            
            rectF.set(0, 0, width, height);
            clipPath.reset();
            clipPath.addRoundRect(rectF, cornerRadius, cornerRadius, android.graphics.Path.Direction.CW);
        }

        @Override
        public void draw(android.graphics.Canvas canvas) {
            android.graphics.Rect bounds = getBounds();
            float width = bounds.width();
            float height = bounds.height();
            float splitX = width * stopPoint;

            canvas.save();
            canvas.clipPath(clipPath);

            if (leftColors.length > 1) {
                android.graphics.LinearGradient gradient = new android.graphics.LinearGradient(
                        0, 0, splitX, 0, leftColors, null, android.graphics.Shader.TileMode.CLAMP);
                paint.setShader(gradient);
            } else {
                paint.setColor(leftColors[0]);
                paint.setShader(null);
            }
            canvas.drawRect(0, 0, splitX, height, paint);

            paint.setShader(null);
            paint.setColor(rightColor);
            canvas.drawRect(splitX, 0, width, height, paint);
            
            canvas.restore();
        }

        @Override
        public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override
        public void setColorFilter(android.graphics.ColorFilter colorFilter) { paint.setColorFilter(colorFilter); }
        @Override
        public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }
}


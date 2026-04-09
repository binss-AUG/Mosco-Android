package com.vn.jet.mosco.utils;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
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
import com.vn.jet.mosco.model.Objet;

import org.json.JSONObject;

/**
 * ObjetDetailBinder — Data-driven dynamic theming for the Objet Detail dialog.
 */
public class ObjetDetailBinder {
    
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
        dialog.show();
    }

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
            bind(dialog, context, objet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void bind(Dialog dialog, Context context, com.vn.jet.mosco.model.Objet objet) {
        try {
            // ── 1. Parse colors ────────────────────────────────────────
            String bgColorHex = objet.getBackgroundColor() != null ? objet.getBackgroundColor() : "#6c29fd";
            String txtColorHex = objet.getTextColor() != null ? objet.getTextColor() : "#ffffff";

            int bgColor = Color.parseColor(bgColorHex);
            int txtColor = Color.parseColor(txtColorHex);
            
            // Derive class color from bgColor (logic previously in cardJson)
            int classColor = androidx.core.content.ContextCompat.getColor(context, R.color.mosco_btn_disabled);

            // ── 2. Parse metadata ──────────────────────────────────────
            String frontImageUrl = objet.getImageUrl();
            String member = objet.getMember() != null ? objet.getMember() : "Unknown";
            String collectionNo = objet.getCollectionNo() != null ? objet.getCollectionNo() : "";
            String cardClass = objet.getTypeKey() != null ? objet.getTypeKey() : "";
            String season = objet.getSeason() != null ? objet.getSeason() : "";
            int level = objet.getLevel();
            int exp = objet.getExp();
            int upgradeLevel = objet.getUpgradeLevel();

            // ── 3. Load front image via Glide ──────────────────────────
            ImageView ivObjet = dialog.findViewById(R.id.iv_objet_detail_image);
            if (ivObjet != null && frontImageUrl != null && !frontImageUrl.isEmpty()) {
                java.io.File localThumb = com.vn.jet.mosco.utils.CardAssetManager.getLocalFile(context, frontImageUrl);
                
                com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> thumbRequest = null;
                if (localThumb != null && localThumb.exists()) {
                    thumbRequest = Glide.with(context).load(localThumb);
                }

                Glide.with(context)
                        .load(frontImageUrl)
                        .thumbnail(thumbRequest)
                        .placeholder(R.drawable.item_shop_demo)
                        .error(R.drawable.item_shop_demo)
                        .transition(DrawableTransitionOptions.withCrossFade(500))
                        .into(ivObjet);
            }

            // ── 3. Calculate Stats ───────────
            int hp = 100;
            int atk = 10;
            int def = 10;
            int spd = 10;
            
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
            if (tvCritRate != null) tvCritRate.setText("10%");
            
            TextView tvCritDmg = dialog.findViewById(R.id.tv_stat_crit_dmg);
            if (tvCritDmg != null) tvCritDmg.setText("150%");

            // ── 4. Bind title, badge & Dual-tone Chip ──────────────────
            TextView tvTitle = dialog.findViewById(R.id.tv_objet_title);
            if (tvTitle != null) {
                tvTitle.setText(member + " " + collectionNo);
                tvTitle.setTextColor(Color.WHITE);
            }

            TextView tvOvr = dialog.findViewById(R.id.card_tv_ovr);
            if (tvOvr != null) {
                tvOvr.setText(String.valueOf(overall));
                tvOvr.setVisibility(View.VISIBLE);
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

            View llDualToneChip = dialog.findViewById(R.id.ll_dual_tone_chip);
            if (llDualToneChip != null) {
                int[] leftColors;
                if ("Special".equalsIgnoreCase(cardClass)) {
                    leftColors = new int[]{
                            Color.parseColor("#FFC0CB"),
                            Color.parseColor("#B0E0E6"),
                            Color.parseColor("#E6E6FA"),
                            Color.parseColor("#FFFFFF")
                    };
                } else {
                    leftColors = new int[]{bgColor};
                }
                llDualToneChip.setBackground(new HardStopGradientDrawable(leftColors, classColor, 0.3f));
            }

            // ── 5. Setup layout tints & backgrounds ──────────
            MaterialCardView cvRoot = dialog.findViewById(R.id.cv_dialog_root);
            int strokeWidth = dpToPx(context, 1);
            int blurredBorderColor = androidx.core.graphics.ColorUtils.setAlphaComponent(bgColor, 128);

            int topColor = androidx.core.content.ContextCompat.getColor(context, R.color.mosco_surface);
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

            // ── 6. Image container ────────────────
            MaterialCardView cvImageContainer = dialog.findViewById(R.id.cv_objet_image_container);
            ImageView ivDetailBack = dialog.findViewById(R.id.iv_objet_detail_back);
            if (cvImageContainer != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    cvImageContainer.setCardElevation(dpToPx(context, 16));
                    cvImageContainer.setOutlineAmbientShadowColor(classColor);
                    cvImageContainer.setOutlineSpotShadowColor(classColor);
                }
                cvImageContainer.setStrokeWidth(strokeWidth);
                cvImageContainer.setStrokeColor(blurredBorderColor);

                View metallicBg = dialog.findViewById(R.id.view_card_metallic_bg);
                if (metallicBg != null) {
                    int[] colors;
                    if ("Special".equalsIgnoreCase(cardClass)) {
                        colors = new int[]{Color.parseColor("#FFC0CB"), Color.parseColor("#B0E0E6"), Color.parseColor("#E6E6FA"), Color.parseColor("#FFFFFF")};
                    } else {
                        colors = new int[]{Color.parseColor("#F5F5F5"), Color.parseColor("#FFFFFF"), Color.parseColor("#DBDBDB")};
                    }
                    GradientDrawable mBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
                    metallicBg.setBackground(mBg);
                }

                View shimmer = dialog.findViewById(R.id.view_card_shimmer);
                if (shimmer != null) {
                    GradientDrawable shimmerBg = new GradientDrawable(
                            GradientDrawable.Orientation.LEFT_RIGHT,
                            new int[]{0x00FFFFFF, 0x00FFFFFF, 0x66FFFFFF, 0x00FFFFFF, 0x00FFFFFF}
                    );
                    shimmer.setBackground(shimmerBg);
                    shimmer.setRotation(10f);
                    shimmer.setScaleX(1.0f);
                    shimmer.setScaleY(1.5f);

                    ValueAnimator shimmerAnim = ValueAnimator.ofFloat(-1.5f, 2.5f); 
                    shimmerAnim.setDuration(3500); 
                    shimmerAnim.setInterpolator(new android.view.animation.LinearInterpolator());
                    shimmerAnim.setRepeatCount(ValueAnimator.INFINITE);
                    shimmerAnim.setRepeatMode(ValueAnimator.RESTART);
                    shimmerAnim.addUpdateListener(animation -> {
                        float fraction = (float) animation.getAnimatedValue();
                        shimmer.setTranslationX(shimmer.getWidth() * fraction);
                    });
                    shimmerAnim.start();
                }

                ObjectAnimator floatingAnim = ObjectAnimator.ofFloat(cvImageContainer, "translationY", 0f, -12f, 0f);
                floatingAnim.setDuration(3000);
                floatingAnim.setInterpolator(new AccelerateDecelerateInterpolator());
                floatingAnim.setRepeatCount(ValueAnimator.INFINITE);
                floatingAnim.setRepeatMode(ValueAnimator.REVERSE);
                floatingAnim.start();

                // ══════════════════════════════════════════════════════════
                //  e) 3D FLIP
                // ══════════════════════════════════════════════════════════
                String backImageUrl = objet.getBackImageUrl();
                if (ivDetailBack != null && backImageUrl != null && !backImageUrl.isEmpty()) {
                    java.io.File localBackThumb = com.vn.jet.mosco.utils.CardAssetManager.getLocalFile(context, backImageUrl);
                    com.bumptech.glide.RequestBuilder<Drawable> backThumb = null;
                    if (localBackThumb != null && localBackThumb.exists()) {
                        backThumb = Glide.with(context).load(localBackThumb);
                    }
                    Glide.with(context)
                            .load(backImageUrl)
                            .thumbnail(backThumb)
                            .placeholder(android.R.color.transparent)
                            .error(android.R.color.transparent)
                            .dontAnimate()
                            .into(ivDetailBack);
                }

                float density = context.getResources().getDisplayMetrics().density;
                cvImageContainer.setCameraDistance(8000 * density);

                final boolean[] isFlipped = {false};
                final boolean[] isFlipAnimating = {false};
                final int FLIP_HALF = 250;
                final int SWIPE_T = 100;
                final int VELOCITY_T = 100;

                android.view.GestureDetector flipGesture = new android.view.GestureDetector(context,
                        new android.view.GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onFling(android.view.MotionEvent e1, android.view.MotionEvent e2,
                                           float velocityX, float velocityY) {
                        if (e1 == null || e2 == null) return false;
                        float diffX = e2.getX() - e1.getX();
                        if (Math.abs(diffX) > SWIPE_T && Math.abs(velocityX) > VELOCITY_T && !isFlipAnimating[0]) {
                            isFlipAnimating[0] = true;
                            float startA = isFlipped[0] ? 180f : 0f;
                            float midA = isFlipped[0] ? 270f : 90f;
                            float endA = isFlipped[0] ? 360f : 180f;

                            ObjectAnimator p1 = ObjectAnimator.ofFloat(cvImageContainer, "rotationY", startA, midA);
                            p1.setDuration(FLIP_HALF);
                            p1.setInterpolator(new AccelerateDecelerateInterpolator());

                            ObjectAnimator p2 = ObjectAnimator.ofFloat(cvImageContainer, "rotationY", midA, endA);
                            p2.setDuration(FLIP_HALF);
                            p2.setInterpolator(new android.view.animation.DecelerateInterpolator());

                            p1.addListener(new android.animation.AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(android.animation.Animator animation) {
                                    if (isFlipped[0]) {
                                        if (ivObjet != null) ivObjet.setVisibility(View.VISIBLE);
                                        if (ivDetailBack != null) ivDetailBack.setVisibility(View.GONE);
                                        if (shimmer != null) shimmer.setVisibility(View.VISIBLE);
                                        if (metallicBg != null) metallicBg.setVisibility(View.VISIBLE);
                                        TextView tvOvrFlip = dialog.findViewById(R.id.card_tv_ovr);
                                        if (tvOvrFlip != null) tvOvrFlip.setVisibility(View.VISIBLE);
                                        ImageView ivLevelFlip = dialog.findViewById(R.id.card_iv_level);
                                        if (ivLevelFlip != null && upgradeLevel > 0) ivLevelFlip.setVisibility(View.VISIBLE);
                                    } else {
                                        if (ivObjet != null) ivObjet.setVisibility(View.GONE);
                                        if (ivDetailBack != null) {
                                            ivDetailBack.setVisibility(View.VISIBLE);
                                            ivDetailBack.setScaleX(-1f);
                                            ivDetailBack.setAlpha(1f);
                                        }
                                        if (shimmer != null) shimmer.setVisibility(View.GONE);
                                        if (metallicBg != null) metallicBg.setVisibility(View.GONE);
                                        TextView tvOvrFlip = dialog.findViewById(R.id.card_tv_ovr);
                                        if (tvOvrFlip != null) tvOvrFlip.setVisibility(View.GONE);
                                        ImageView ivLevelFlip = dialog.findViewById(R.id.card_iv_level);
                                        if (ivLevelFlip != null) ivLevelFlip.setVisibility(View.GONE);
                                    }
                                    isFlipped[0] = !isFlipped[0];
                                }
                            });

                            AnimatorSet flipSet = new AnimatorSet();
                            flipSet.playSequentially(p1, p2);
                            flipSet.addListener(new android.animation.AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(android.animation.Animator animation) {
                                    isFlipAnimating[0] = false;
                                    if (cvImageContainer.getRotationY() >= 360f) {
                                        cvImageContainer.setRotationY(0f);
                                    }
                                }
                            });
                            flipSet.start();
                            return true;
                        }
                        return false;
                    }

                    @Override
                    public boolean onDown(android.view.MotionEvent e) {
                        return true;
                    }
                });

                cvImageContainer.setOnTouchListener((v, event) -> {
                    flipGesture.onTouchEvent(event);
                    return true;
                });
            }

            MaterialButton btnUpgrade = dialog.findViewById(R.id.btn_upgrade_detail);
            if (btnUpgrade != null) {
                btnUpgrade.setOnClickListener(v -> {
                    dialog.dismiss();
                    if (context instanceof androidx.appcompat.app.AppCompatActivity) {
                        androidx.appcompat.app.AppCompatActivity activity = (androidx.appcompat.app.AppCompatActivity) context;
                        com.vn.jet.mosco.fragment.UpgradeFragment upgradeFragment = com.vn.jet.mosco.fragment.UpgradeFragment.newInstance();
                        if (objet != null) {
                            upgradeFragment.setMainCard(objet);
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
                tvLevelLabel.setText("Level " + level);
                tvLevelLabel.setTextColor(txtColor);
            }
 
            int maxExp = level * 100;
            TextView tvLevelValue = dialog.findViewById(R.id.tv_level_value);
            if (tvLevelValue != null) {
                tvLevelValue.setText(exp + " / " + maxExp);
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

            ImageView btnRecycle = dialog.findViewById(R.id.btn_recycle_detail);
            if (btnRecycle != null) {
                int disabledColor = androidx.core.content.ContextCompat.getColor(context, R.color.mosco_text_disabled);
                btnRecycle.setImageTintList(ColorStateList.valueOf(disabledColor));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static int dpToPx(Context ctx, int dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }

    private static class HardStopGradientDrawable extends Drawable {
        private final android.graphics.Paint paint;
        private final int[] leftColors;
        private final int rightColor;
        private final float stopPoint;

        public HardStopGradientDrawable(int[] leftColors, int rightColor, float stopPoint) {
            this.paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            this.leftColors = leftColors;
            this.rightColor = rightColor;
            this.stopPoint = stopPoint;
        }

        @Override
        public void draw(android.graphics.Canvas canvas) {
            android.graphics.Rect bounds = getBounds();
            float width = bounds.width();
            float height = bounds.height();
            float splitX = width * stopPoint;
            float cornerRadius = height / 2.0f; // pill shape

            android.graphics.Path clipPath = new android.graphics.Path();
            clipPath.addRoundRect(new android.graphics.RectF(0, 0, width, height), cornerRadius, cornerRadius, android.graphics.Path.Direction.CW);

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

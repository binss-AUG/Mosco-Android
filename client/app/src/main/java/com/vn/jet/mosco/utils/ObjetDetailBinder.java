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

import org.json.JSONObject;

/**
 * ObjetDetailBinder — Data-driven dynamic theming for the Objet Detail dialog.
 *
 * Each card in database.json has unique `backgroundColor` and `textColor`.
 * This binder applies those colors across the entire dialog to create
 * a visually distinct identity per card:
 *
 * - Progress bar tint → backgroundColor
 * - Stats card stroke → backgroundColor @40%
 * - Outer dialog card stroke → backgroundColor @60%
 * - Level badge background stroke → backgroundColor
 * - Title text → textColor
 * - Level EXP text → textColor
 * - Front image → loaded via Glide from frontImage URL
 */
public class ObjetDetailBinder {
    
    /**
     * Hiển thị Dialog chi tiết thẻ bài từ một đối tượng Objet.
     * Senior Logic: Tự động tìm kiếm Metadata từ DatabaseLoader dựa trên ID của thẻ.
     */
    public static void showObjetDetail(android.content.Context context, com.vn.jet.mosco.model.Objet objet) {
        if (objet == null || context == null) return;
        
        android.app.Dialog dialog = new android.app.Dialog(context);
        dialog.setContentView(R.layout.dialog_objet_detail);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            dialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        // Tìm Metadata từ Database ngầm dựa trên CollectionID (ví dụ: binary02_yeonji_333a)
        org.json.JSONObject cardJson = com.vn.jet.mosco.utils.DatabaseLoader.findById(context, objet.getCollectionId());
        if (cardJson != null) {
            bind(dialog, context, cardJson, objet);
        }

        dialog.findViewById(R.id.btn_close_detail).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    /**
     * Binds all dynamic data from a card JSON to the detail dialog.
     */
    public static void bind(Dialog dialog, Context context, JSONObject cardJson, com.vn.jet.mosco.model.Objet objet) {
        bind(dialog, context, cardJson, objet.getLevel(), objet.getExp(), objet.getUpgradeLevel(), objet);
    }

    /**
     * Binds all dynamic data from a card JSON to the detail dialog. (Legacy support)
     */
    public static void bind(Dialog dialog, Context context, JSONObject cardJson, int level, int exp, int upgradeLevel) {
        bind(dialog, context, cardJson, level, exp, upgradeLevel, null);
    }

    private static void bind(Dialog dialog, Context context, JSONObject cardJson, int level, int exp, int upgradeLevel, com.vn.jet.mosco.model.Objet objet) {
        try {
            // ── 1. Parse colors ────────────────────────────────────────
            String bgColorHex = cardJson.optString("backgroundColor", "#6c29fd");
            String txtColorHex = cardJson.optString("textColor", "#ffffff");
            String classColorStr = cardJson.optString("classColor", "");

            int bgColor = Color.parseColor(bgColorHex);
            int txtColor = Color.parseColor(txtColorHex);
            int classColor = classColorStr.isEmpty() 
                    ? androidx.core.content.ContextCompat.getColor(context, R.color.mosco_btn_disabled) 
                    : Color.parseColor(classColorStr);

            // ── 2. Parse metadata ──────────────────────────────────────
            String frontImageUrl = cardJson.optString("frontImage", "");
            String member = cardJson.optString("member", "Unknown");
            String collectionNo = cardJson.optString("collectionNo", "");
            String cardClass = cardJson.optString("class", "");
            String season = cardJson.optString("season", "");

            // ── 3. Load front image via Glide (Optimal 4x Loading Strategy) ──────────
            ImageView ivObjet = dialog.findViewById(R.id.iv_objet_detail_image);
            if (ivObjet != null && !frontImageUrl.isEmpty()) {
                // 💎 LOCAL FIRST THUMBNAIL TRICK:
                // Load ngay bản 2x từ bộ nhớ máy (siêu nhanh) để lấp đầy khoảng trống 
                // trong lúc đợi bản 4x (original) từ server Cloudflare nạp xong.
                java.io.File localThumb = com.vn.jet.mosco.utils.CardAssetManager.getLocalFile(context, frontImageUrl);
                
                com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> thumbRequest = null;
                if (localThumb != null && localThumb.exists()) {
                    thumbRequest = Glide.with(context).load(localThumb);
                }

                Glide.with(context)
                        .load(frontImageUrl) // Bản 4x (Network)
                        .thumbnail(thumbRequest) // Bản 2x (Local) - hiện tức thì
                        .placeholder(R.drawable.item_shop_demo)
                        .error(R.drawable.item_shop_demo)
                        .transition(DrawableTransitionOptions.withCrossFade(500))
                        .into(ivObjet);
            }

            // ── 3. Calculate Overall (Power Rating) & Bind Stats ───────────
            int hp = cardJson.optInt("hp", 100);
            int atk = cardJson.optInt("atk", 10);
            int def = cardJson.optInt("def", 10);
            int spd = cardJson.optInt("speed", 10);
            
            // Công thức nhân chỉ số: (1 + Bonus Level + Bonus Upgrade)
            double levelBonus = (level - 1) * 0.05; // 5% mỗi level
            double upgradeBonus = (upgradeLevel - 1) * 0.10; // 10% mỗi bậc +
            double multiplier = 1.0 + levelBonus + upgradeBonus;

            int finalHp = (int) (hp * multiplier);
            int finalAtk = (int) (atk * multiplier);
            int finalDef = (int) (def * multiplier);
            int finalSpd = (int) (spd * multiplier);
            
            // ĐỒNG BỘ OVR THEO cardOvr.json (Source of truth mới)
            String ovrClass = cardJson.optString("class", "FirstWelcome");
            int overall = (objet != null && objet.getOvr() > 0) 
                    ? objet.getOvr() 
                    : com.vn.jet.mosco.utils.DatabaseLoader.getOvrFromCardOvr(context, ovrClass, upgradeLevel);

            // Hiển thị chỉ số cá nhân động lên giao diện
            TextView tvHp = dialog.findViewById(R.id.tv_stat_hp);
            if (tvHp != null) tvHp.setText(String.valueOf(finalHp));
            
            TextView tvAtk = dialog.findViewById(R.id.tv_stat_atk);
            if (tvAtk != null) tvAtk.setText(String.valueOf(finalAtk));
            
            TextView tvDef = dialog.findViewById(R.id.tv_stat_def);
            if (tvDef != null) tvDef.setText(String.valueOf(finalDef));
            
            TextView tvSpd = dialog.findViewById(R.id.tv_stat_spd);
            if (tvSpd != null) tvSpd.setText(String.valueOf(finalSpd));

            // CRIT Rate & DMG: Giữ base hoặc tăng nhẹ tùy thiết kế balance (hiện tại lấy base)
            int baseCritRate = cardJson.optInt("critRate", 10);
            int baseCritDmg = cardJson.optInt("critDmg", 150);
            TextView tvCritRate = dialog.findViewById(R.id.tv_stat_crit_rate);
            if (tvCritRate != null) tvCritRate.setText(baseCritRate + "%");
            
            TextView tvCritDmg = dialog.findViewById(R.id.tv_stat_crit_dmg);
            if (tvCritDmg != null) tvCritDmg.setText(baseCritDmg + "%");

            // ── 4. Bind title, badge & Dual-tone Chip ──────────────────
            TextView tvTitle = dialog.findViewById(R.id.tv_objet_title);
            if (tvTitle != null) {
                // Hiển thị tên thành viên và mã sưu tầm (e.g. "Yeonji 333A")
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
                    // Class Special: Dải Holographic (Hồng phấn, Xanh Lạnh, Tím Pastel, Trắng)
                    leftColors = new int[]{
                            Color.parseColor("#FFC0CB"),
                            Color.parseColor("#B0E0E6"),
                            Color.parseColor("#E6E6FA"),
                            Color.parseColor("#FFFFFF")
                    };
                } else {
                    leftColors = new int[]{bgColor};
                }
                
                // Swap the colors: Base color/Gradient on the left (30%), Class color on the right (70%)
                llDualToneChip.setBackground(new HardStopGradientDrawable(leftColors, classColor, 0.3f));
            }

            // ── 5. Setup layout tints & backgrounds ──────────
            MaterialCardView cvRoot = dialog.findViewById(R.id.cv_dialog_root);
            
            int strokeWidth = dpToPx(context, 1);
            int blurredBorderColor = androidx.core.graphics.ColorUtils.setAlphaComponent(bgColor, 128);

            // Nền của dialog gradient surface (trên) với màu bgColor (dưới)
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
            if (cvImageContainer != null) {
                // a) Glowing Border (Outer Bloom)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    cvImageContainer.setCardElevation(dpToPx(context, 16));
                    cvImageContainer.setOutlineAmbientShadowColor(classColor);
                    cvImageContainer.setOutlineSpotShadowColor(classColor);
                }
                cvImageContainer.setStrokeWidth(strokeWidth);
                cvImageContainer.setStrokeColor(blurredBorderColor);

                // b) Metallic Surface Background
                View metallicBg = dialog.findViewById(R.id.view_card_metallic_bg);
                if (metallicBg != null) {
                    int[] colors;
                    if ("Special".equalsIgnoreCase(cardClass)) {
                        // Class Special: Holographic gradient
                        colors = new int[]{Color.parseColor("#FFC0CB"), Color.parseColor("#B0E0E6"), Color.parseColor("#E6E6FA"), Color.parseColor("#FFFFFF")};
                    } else {
                        // Class thường: Trắng Ánh kim
                        colors = new int[]{Color.parseColor("#F5F5F5"), Color.parseColor("#FFFFFF"), Color.parseColor("#DBDBDB")};
                    }
                    GradientDrawable mBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
                    metallicBg.setBackground(mBg);
                }

                // c) Shimmer Overlay (Reflective animation)
                View shimmer = dialog.findViewById(R.id.view_card_shimmer);
                if (shimmer != null) {
                    GradientDrawable shimmerBg = new GradientDrawable(
                            GradientDrawable.Orientation.LEFT_RIGHT,
                            new int[]{0x00FFFFFF, 0x00FFFFFF, 0x66FFFFFF, 0x00FFFFFF, 0x00FFFFFF} // Tạo vệt sáng sắc nét ở giữa
                    );
                    shimmer.setBackground(shimmerBg);
                    shimmer.setRotation(10f); // Xéo 80 độ so với trục X = nghiêng 10 độ so với trục Y
                    shimmer.setScaleX(1.0f);
                    shimmer.setScaleY(1.5f);

                    // Chạy thẳng từ trái sang phải, tạo delay vòng lặp mượt mà
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

                // d) Idle Floating Animation
                ObjectAnimator floatingAnim = ObjectAnimator.ofFloat(cvImageContainer, "translationY", 0f, -12f, 0f);
                floatingAnim.setDuration(3000);
                floatingAnim.setInterpolator(new AccelerateDecelerateInterpolator());
                floatingAnim.setRepeatCount(ValueAnimator.INFINITE);
                floatingAnim.setRepeatMode(ValueAnimator.REVERSE);
                floatingAnim.start();
            }

            MaterialButton btnUpgrade = dialog.findViewById(R.id.btn_upgrade_detail);
        if (btnUpgrade != null) {
            btnUpgrade.setOnClickListener(v -> {
                dialog.dismiss();
                if (context instanceof androidx.appcompat.app.AppCompatActivity) {
                    androidx.appcompat.app.AppCompatActivity activity = (androidx.appcompat.app.AppCompatActivity) context;
                    com.vn.jet.mosco.fragment.UpgradeFragment upgradeFragment = com.vn.jet.mosco.fragment.UpgradeFragment.newInstance();
                    
                    if (objet != null) {
                        // Truyền thẻ hiện tại vào làm thẻ chính
                        upgradeFragment.setMainCard(objet);
                        
                        // Mở UpgradeFragment
                        activity.getSupportFragmentManager().beginTransaction()
                                .replace(R.id.frame_layout, upgradeFragment) // frame_layout là container chính trong MainActivity
                                .addToBackStack(null)
                                .commit();
                                
                        // Đồng bộ tab dưới bottom navigation (nếu có)
                        com.google.android.material.bottomnavigation.BottomNavigationView bottomNav = activity.findViewById(R.id.bottom_navigation);
                        if (bottomNav != null) {
                            bottomNav.setSelectedItemId(R.id.nav_stage); // ID của tab Upgrade
                        }
                    } else {
                        android.widget.Toast.makeText(context, "Không thể nâng cấp từ màn hình này", android.widget.Toast.LENGTH_SHORT).show();
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

            // Level label (e.g. "Level 10") — dynamic
            TextView tvLevelLabel = dialog.findViewById(R.id.tv_level_label);
            if (tvLevelLabel != null) {
                tvLevelLabel.setText("Level " + level);
                tvLevelLabel.setTextColor(txtColor);
            }
 
            // EXP text (e.g. "9 / 1000") — dynamic
            int maxExp = level * 100; // Giả định Max EXP tăng theo Level
            TextView tvLevelValue = dialog.findViewById(R.id.tv_level_value);
            if (tvLevelValue != null) {
                tvLevelValue.setText(exp + " / " + maxExp);
                tvLevelValue.setTextColor(txtColor);
            }

            // ProgressBar Level
            ProgressBar pbLevel = dialog.findViewById(R.id.pb_level);
            if (pbLevel != null) {
                pbLevel.setMax(maxExp);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    pbLevel.setProgress(exp, true);
                } else {
                    pbLevel.setProgress(exp);
                }
            }

            // ── 8. Progress bar tint ───────────────────────────────────
            // [Removed dynamic tint, using static progressDrawable]

            // ── 9. Stats card — viền mờ ─────────────────────────────────
            MaterialCardView cvInfoBox = dialog.findViewById(R.id.cv_info_box);
            if (cvInfoBox != null) {
                cvInfoBox.setStrokeWidth(strokeWidth);
                cvInfoBox.setStrokeColor(blurredBorderColor);
                cvInfoBox.setAlpha(0.8f); // 80% opacity
            }

            // ── 10. Level Up button ─────────────
            // [Reverted to keep original background]

            // ── 11. Recycle button ─────────────────────────
            ImageView btnRecycle = dialog.findViewById(R.id.btn_recycle_detail);
            if (btnRecycle != null) {
                int disabledColor = androidx.core.content.ContextCompat.getColor(context, R.color.mosco_text_disabled);
                btnRecycle.setImageTintList(ColorStateList.valueOf(disabledColor));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────

    /**
     * Creates a color with modified alpha.
     */
    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int dpToPx(Context ctx, int dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }

    /**
     * Custom drawable to draw a linear gradient with a strict hard stop at a defined percentage.
     */
    private static class HardStopGradientDrawable extends Drawable {
        private final android.graphics.Paint paint;
        private final int[] colorsLeft;
        private final int colorRight;
        private final float stop;

        public HardStopGradientDrawable(int[] colorsLeft, int colorRight, float stop) {
            if (colorsLeft.length == 1) {
                this.colorsLeft = new int[]{colorsLeft[0], colorsLeft[0]};
            } else {
                this.colorsLeft = colorsLeft;
            }
            this.colorRight = colorRight;
            this.stop = stop;
            this.paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        }

        @Override
        public void setBounds(int left, int top, int right, int bottom) {
            super.setBounds(left, top, right, bottom);
            float width = right - left;

            int leftLen = colorsLeft.length;
            int totalColors = leftLen + 2; 
            int[] shaderColors = new int[totalColors];
            float[] shaderPositions = new float[totalColors];

            for (int i = 0; i < leftLen; i++) {
                shaderColors[i] = colorsLeft[i];
                shaderPositions[i] = i * (stop / Math.max(1, leftLen - 1));
            }
            shaderPositions[leftLen - 1] = stop; 

            shaderColors[leftLen] = colorRight;
            shaderPositions[leftLen] = stop;

            shaderColors[leftLen + 1] = colorRight;
            shaderPositions[leftLen + 1] = 1f;

            android.graphics.Shader shader = new android.graphics.LinearGradient(
                    0, 0, width, 0,
                    shaderColors,
                    shaderPositions,
                    android.graphics.Shader.TileMode.CLAMP
            );
            paint.setShader(shader);
        }

        @Override
        public void draw(android.graphics.Canvas canvas) {
            android.graphics.RectF rect = new android.graphics.RectF(getBounds());
            float cornerRadius = rect.height() / 2f;
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);
        }

        @Override
        public void setAlpha(int alpha) {}

        @Override
        public void setColorFilter(android.graphics.ColorFilter colorFilter) {}

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }
}

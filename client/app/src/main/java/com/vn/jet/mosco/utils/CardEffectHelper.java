package com.vn.jet.mosco.utils;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.TypeEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.card.MaterialCardView;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.model.Objet;

/**
 * Trình Xử Lý Hiệu Ứng Cao Cấp Cho Thẻ (Core Card).
 */
public class CardEffectHelper {

    private static int dpToPx(Context context, float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    // Helper class tạo Outer Glow liền mạch, không kẽ hở viền, tỷ lệ tán sắc mượt mà
    private static class OuterGlowView extends View {
        private Paint paint;
        private RectF rect;
        private float cornerRadius, glowRadius, extraPadding;
        private int color;

        public OuterGlowView(Context context, int color, float cornerRadius, float glowRadius, float extraPadding) {
            super(context);
            this.cornerRadius = cornerRadius;
            this.glowRadius = glowRadius;
            this.extraPadding = extraPadding;
            this.color = color;
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            setLayerType(LAYER_TYPE_SOFTWARE, null); // Bắt buộc dùng trên Android để hiển thị bóng Gaussian cực mảnh
            rect = new RectF();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.save();
            
            // Ép viền khoét rỗng LẤN VÀO BÊN TRONG thẻ 1.5dp -> Khóa chết không có kẽ hở đen lọt ra
            float inset = dpToPx(getContext(), 1.5f);
            
            Path path = new Path();
            float leftStrut = extraPadding + inset;
            float topStrut = extraPadding + inset;
            float rightStrut = getWidth() - extraPadding - inset;
            float bottomStrut = getHeight() - extraPadding - inset;
            rect.set(leftStrut, topStrut, rightStrut, bottomStrut);
            path.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW);
            
            // Khoét lõi tàng hình
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                canvas.clipOutPath(path);
            } else {
                canvas.clipPath(path, android.graphics.Region.Op.DIFFERENCE);
            }

            paint.setColor(color);
            
            // Lớp 1 (Lõi chói 100% -> 30%): Rất gắt nên dùng nửa bán kính glowRadius
            paint.setShadowLayer(glowRadius * 0.45f, 0, 0, color);
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);
            
            // Lớp 2 (Sương mờ 30% -> 0%): Tỏa dài ra toàn bộ ranh giới
            int mAlpha = Color.alpha(color);
            int fadedColor = Color.argb((int)(mAlpha * 0.35f), Color.red(color), Color.green(color), Color.blue(color));
            paint.setShadowLayer(glowRadius, 0, 0, fadedColor);
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);

            canvas.restore();
        }
    }

    public static void apply(MaterialCardView cardView, View shimmer, Objet card, boolean applyFloating) {
        if (cardView == null || card == null) return;
        Context context = cardView.getContext();

        String currentCardId = (String) cardView.getTag(R.id.card_main);
        if (card.getIdString().equals(currentCardId)) return;

        remove(cardView, shimmer);

        // THIẾT LẬP MẶT NẠ CHỐNG XUYÊN THẤU CHO SHIMMER ==========================================
        // Dùng PorterDuff.Mode.DST_OUT để tạo hiệu ứng "Lỗ Hổng Khôn Ngoan"
        View shimmerContainer = cardView.findViewById(R.id.layout_shimmer_container);
        android.widget.ImageView triplesBorder = cardView.findViewById(R.id.card_iv_triplesborder);
        if (shimmerContainer != null && triplesBorder != null) {
            // Bật bộ đệm phần cứng (Tạo ranh giới vô hình bao gọn cả Shimmer và Mask)
            shimmerContainer.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            
            Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            // Lệnh đục lỗ (DST_OUT): Dùng chính cái màu Alpha đặc 100% của triplesborder để khoét sạch Shimmer
            maskPaint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT));
            
            triplesBorder.setLayerType(View.LAYER_TYPE_HARDWARE, maskPaint);
            triplesBorder.setVisibility(View.VISIBLE); // Bật lên khi Card xài hiệu ứng
        }
        // =========================================================================================

        cardView.setTag(R.id.card_main, card.getIdString());

        cardView.post(() -> {
            int w = cardView.getWidth();
            int h = cardView.getHeight();
            if (w <= 0 || h <= 0) return;

            // BẠN CÓ THỂ ĐỔI ĐỘ DÀY VIỀN TRỰC TIẾP TRỞ LẠI NHỎ HƠN Ở ĐÂY (VD: 0.035f = 3.5%)
            int dynamicStrokeWidth = (int) (w * 0.00f); 
            cardView.setStrokeWidth(dynamicStrokeWidth);
            float cornerRadius = cardView.getRadius();

            Glide.with(context)
                    .asBitmap()
                    .load(card.getImageUrl())
                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                            int bw = resource.getWidth();
                            int bh = resource.getHeight();
                            if (bw > 0 && bh > 0) {
                                int pixelX = Math.max(0, bw - 2);
                                int pixelY = bh / 2;
                                int extractedColor = resource.getPixel(pixelX, pixelY);
                                
                                float[] hsv = new float[3];
                                Color.colorToHSV(extractedColor, hsv);
                                hsv[2] = Math.min(1.0f, hsv[2] + 0.3f);
                                int glowColor = Color.HSVToColor(hsv);

                                cardView.setStrokeColor(extractedColor);

                                ViewGroup parent = (ViewGroup) cardView.getParent();
                                if (parent != null) {
                                    parent.setClipChildren(false);
                                    parent.setClipToPadding(false);

                                    // ĐỘ DÀI CỦA TIA BÓNG (Tạo fade mượt)
                                    float glowRadius = w * 0.20f; 
                                    
                                    // Bổ sung Canvas Padding (Cực rộng để bóng không bao giờ đập hộp vuông gắt)
                                    float extraPadding = glowRadius * 2.5f;

                                    View pseudoGlow = new OuterGlowView(context, glowColor, cornerRadius, glowRadius, extraPadding);
                                    parent.addView(pseudoGlow, parent.indexOfChild(cardView));
                                    cardView.setTag(R.id.view_progress_fill, pseudoGlow);

                                    ViewGroup.LayoutParams rawParams = cardView.getLayoutParams();
                                    if (rawParams instanceof ConstraintLayout.LayoutParams) {
                                        ConstraintLayout.LayoutParams glowParams = new ConstraintLayout.LayoutParams(
                                                (int)(w + extraPadding * 2), (int)(h + extraPadding * 2));
                                        
                                        glowParams.topToTop = cardView.getId();
                                        glowParams.bottomToBottom = cardView.getId();
                                        glowParams.startToStart = cardView.getId();
                                        glowParams.endToEnd = cardView.getId();
                                        
                                        pseudoGlow.setLayoutParams(glowParams);
                                    }
                                }
                            }
                        }
                        @Override
                        public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) {}
                    });
        });



        // c) Shimmer Overlay (Reflective animation)
        if (shimmer != null) {
            shimmer.setVisibility(View.VISIBLE);

            // 1. Tạo "Vệt sáng" bằng Gradient
            android.graphics.drawable.GradientDrawable shimmerBg = new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{0x00FFFFFF, 0x00FFFFFF, 0x66FFFFFF, 0x00FFFFFF, 0x00FFFFFF}
            );
            shimmer.setBackground(shimmerBg);

            // 2. Thiết lập hình dáng ban đầu
            shimmer.setRotation(10f); // Nghiêng vệt sáng 10 độ
            shimmer.setScaleX(1.0f);
            shimmer.setScaleY(1.5f);  // Kéo dài theo chiều dọc để đảm bảo phủ hết thẻ khi nghiêng

            // [FIX LỖI KHỰNG]: Ép vệt sáng văng ra ngoài mép trái ngay lập tức chờ tới lượt
            shimmer.post(() -> {
                shimmer.setTranslationX(shimmer.getWidth() * -1.5f);
            });

            // 3. Khởi tạo Animation di chuyển (Chạy 1 lần)
            ValueAnimator shimmerAnim = ValueAnimator.ofFloat(-1.5f, 2.5f);
            shimmerAnim.setDuration(3500); // Tốc độ lướt (3.5 giây)
            shimmerAnim.setInterpolator(new android.view.animation.LinearInterpolator());

            // Biểu tượng an toàn: Cờ đánh dấu Animation bị hủy để không lặp thừa
            final boolean[] isCancelled = {false};

            Runnable delayRunnable = new Runnable() {
                @Override
                public void run() {
                    if (shimmer.isAttachedToWindow() && !isCancelled[0]) {
                        shimmerAnim.start();
                    }
                }
            };

            // 4. Cập nhật vị trí liên tục
            shimmerAnim.addUpdateListener(animation -> {
                float fraction = (float) animation.getAnimatedValue();
                shimmer.setTranslationX(shimmer.getWidth() * fraction);
            });

            // 5. Lắng nghe khi kết thúc để tạo delay ngẫu nhiên (1-3s)
            shimmerAnim.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationCancel(Animator animation) {
                    isCancelled[0] = true;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (isCancelled[0]) return;

                    // Random từ 1000ms (1s) đến 3000ms (3s)
                    long randomDelay = 1000 + new java.util.Random().nextInt(2001);

                    // Dùng View.postDelayed để đếm ngược thời gian chờ trực tiếp trên Shimmer View
                    shimmer.postDelayed(delayRunnable, randomDelay);
                }
            });

            // Kích hoạt lần chạy đầu tiên (Ngẫu nhiên chênh lệch để các thẻ không giống hệt nhau)
            shimmer.postDelayed(delayRunnable, new java.util.Random().nextInt(1500));

            shimmer.setTag(R.id.view_card_shimmer, shimmerAnim);
            shimmer.setTag(R.id.tv_materials_label, delayRunnable);
        }

        if (applyFloating) {
            ObjectAnimator floatingAnim = ObjectAnimator.ofFloat(cardView, "translationY", 0f, -dpToPx(context, 8f), 0f);
            
            floatingAnim.setEvaluator(new TypeEvaluator<Float>() {
                @Override
                public Float evaluate(float fraction, Float startValue, Float endValue) {
                    float val = startValue + fraction * (endValue - startValue);
                    return (float) Math.round(val); 
                }
            });

            floatingAnim.addUpdateListener(animation -> {
                View pseudoGlow = (View) cardView.getTag(R.id.view_progress_fill);
                if (pseudoGlow != null) {
                    pseudoGlow.setTranslationY((float) Math.round((float) animation.getAnimatedValue()));
                }
            });

            floatingAnim.setDuration(2800 + (long)(Math.random() * 500));
            floatingAnim.setInterpolator(new AccelerateDecelerateInterpolator());
            floatingAnim.setRepeatCount(ValueAnimator.INFINITE);
            floatingAnim.setRepeatMode(ValueAnimator.REVERSE);
            
            cardView.setTag(floatingAnim);
            floatingAnim.start();
        }
    }

    public static void remove(MaterialCardView cardView, View shimmer) {
        if (cardView == null) return;
        cardView.setStrokeWidth(0);
        cardView.setCardElevation(0);
        cardView.setTranslationY(0f);
        cardView.setTag(R.id.card_main, null);
        
        View pseudoGlow = (View) cardView.getTag(R.id.view_progress_fill);
        if (pseudoGlow != null && pseudoGlow.getParent() != null) {
            ((ViewGroup) pseudoGlow.getParent()).removeView(pseudoGlow);
            cardView.setTag(R.id.view_progress_fill, null);
        }
        
        Object tag = cardView.getTag();
        if (tag instanceof Animator) {
            ((Animator) tag).cancel();
            cardView.setTag(null);
        }
        

        if (shimmer != null) {
            shimmer.setVisibility(View.GONE);
            Object oldTag = shimmer.getTag(R.id.view_card_shimmer);
            if (oldTag instanceof Animator) {
                ((Animator) oldTag).cancel();
                shimmer.setTag(R.id.view_card_shimmer, null);
            }
            Object oldRunnable = shimmer.getTag(R.id.tv_materials_label);
            if (oldRunnable instanceof Runnable) {
                shimmer.removeCallbacks((Runnable) oldRunnable);
                shimmer.setTag(R.id.tv_materials_label, null);
            }
        }
        
        android.widget.ImageView triplesBorder = cardView.findViewById(R.id.card_iv_triplesborder);
        if (triplesBorder != null) {
            triplesBorder.setVisibility(View.INVISIBLE);
        }
    }
}

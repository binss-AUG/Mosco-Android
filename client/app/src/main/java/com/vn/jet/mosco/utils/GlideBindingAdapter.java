package com.vn.jet.mosco.utils;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.Nullable;
import androidx.databinding.BindingAdapter;
import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.vn.jet.mosco.BuildConfig;
import com.vn.jet.mosco.R;

/**
 * BindingAdapter chuyên biệt để quản lý việc tải ảnh từ Cloudflare Image Delivery.
 * Tự động nối chuỗi URL và chọn variant (thumbnail/original) dựa trên yêu cầu.
 */
public class GlideBindingAdapter {

    private static final String BASE_URL = "https://imagedelivery.net/";

    @BindingAdapter(value = {"imageUrl", "isThumbnail", "isHighQuality"}, requireAll = false)
    public static void loadImage(ImageView view, String imageIdOrUrl, boolean isThumbnail, boolean isHighQuality) {
        if (view == null) return;

        // Tìm Skeleton View trong cùng cấp với ImageView (Standard Layout)
        // Chỉ xử lý skeleton cho ảnh mặt trước (card_iv_image) để tránh dính lỗi loading chéo với ivBack
        final View skeleton = (view.getParent() instanceof ViewGroup && view.getId() == R.id.card_iv_image) 
                ? ((ViewGroup) view.getParent()).findViewById(R.id.layout_card_skeleton) 
                : null;

        if (imageIdOrUrl == null || imageIdOrUrl.isEmpty()) {
            if (skeleton != null) skeleton.setVisibility(View.GONE);
            view.setImageResource(R.drawable.ic_error_placeholder);
            return;
        }

        Context context = view.getContext();
        if (context == null) return;

        if (skeleton != null) {
            Object oldRunnable = skeleton.getTag(R.id.tag_skeleton_runnable);
            if (oldRunnable instanceof Runnable) {
                skeleton.removeCallbacks((Runnable) oldRunnable);
            }
            Runnable showSkeleton = () -> skeleton.setVisibility(View.VISIBLE);
            skeleton.setTag(R.id.tag_skeleton_runnable, showSkeleton);
            skeleton.setVisibility(View.GONE);
            skeleton.postDelayed(showSkeleton, 50);
        }

        // Ưu tiên isHighQuality nếu được set
        boolean effectiveThumbnail = isThumbnail && !isHighQuality;
        String finalUrl = convertImageIdToUrl(imageIdOrUrl, effectiveThumbnail);

        // 🚀 LOCAL FIRST: Check if the asset exists locally (2x or original)
        java.io.File localFile = CardAssetManager.getLocalFile(context, finalUrl);
        Object loadSource = (localFile != null && localFile.exists()) ? localFile : finalUrl;

        com.bumptech.glide.request.RequestOptions options = new com.bumptech.glide.request.RequestOptions()
                .diskCacheStrategy(loadSource instanceof java.io.File ? DiskCacheStrategy.NONE : DiskCacheStrategy.ALL)
                .priority(isHighQuality ? Priority.IMMEDIATE : Priority.NORMAL)
                .error(R.drawable.ic_error_placeholder);
                
        // Chỉ dùng placeholder cho thumbnail. High quality dùng chính thumbnail đè lên nên không cần placeholder xám
        if (!isHighQuality) {
            options = options.placeholder(R.drawable.bg_skeleton_card);
        }

        // Toi uu RAM: Dung RGB_565 cho thumbnail (giam 50% RAM so voi ARGB_8888)
        if (effectiveThumbnail) {
            options = options.format(com.bumptech.glide.load.DecodeFormat.PREFER_RGB_565);
            // Scale down for grid to save memory
            options = options.override(150, 231);
        } else {
            // ARGB_8888 cho bản sắc nét
            options = options.format(com.bumptech.glide.load.DecodeFormat.PREFER_ARGB_8888);
            // Kích hoạt load ngay cả khi View đang GONE (không đợi measure)
            options = options.override(com.bumptech.glide.request.target.Target.SIZE_ORIGINAL);
        }

        com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> request = Glide.with(context)
                .load(loadSource)
                .apply(options);

        // Nếu đang load high quality, dùng chính thumbnail (đã cache) làm ảnh đệm để không bao giờ bị trong suốt
        if (isHighQuality && !effectiveThumbnail) {
            String thumbUrl = convertImageIdToUrl(imageIdOrUrl, true);
            java.io.File localThumb = CardAssetManager.getLocalFile(context, thumbUrl);
            Object thumbSource = (localThumb != null && localThumb.exists()) ? localThumb : thumbUrl;
            
            request = request.thumbnail(
                    Glide.with(context).load(thumbSource)
                        .apply(new com.bumptech.glide.request.RequestOptions()
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .format(com.bumptech.glide.load.DecodeFormat.PREFER_RGB_565)
                                .override(150, 231))
                        .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                            @Override
                            public boolean onLoadFailed(@Nullable com.bumptech.glide.load.engine.GlideException e, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                                return false;
                            }

                            @Override
                            public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                                if (skeleton != null) {
                                    Object r = skeleton.getTag(R.id.tag_skeleton_runnable);
                                    if (r instanceof Runnable) skeleton.removeCallbacks((Runnable) r);
                                    skeleton.setVisibility(View.GONE);
                                }
                                return false;
                            }
                        })
            );
        }

        request.transition(loadSource instanceof java.io.File ? DrawableTransitionOptions.withCrossFade(0) : DrawableTransitionOptions.withCrossFade())
                .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable com.bumptech.glide.load.engine.GlideException e, Object model, 
                                              com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                        if (skeleton != null) {
                            Object r = skeleton.getTag(R.id.tag_skeleton_runnable);
                            if (r instanceof Runnable) skeleton.removeCallbacks((Runnable) r);
                            skeleton.setVisibility(View.GONE);
                        }
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, 
                                                 com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, 
                                                 com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                        if (skeleton != null) {
                            Object r = skeleton.getTag(R.id.tag_skeleton_runnable);
                            if (r instanceof Runnable) skeleton.removeCallbacks((Runnable) r);
                            skeleton.setVisibility(View.GONE);
                        }
                        return false;
                    }
                })
                .into(view);
    }

    /**
     * Overload cho Java code để duy trì tính tương thích.
     */
    public static void loadImage(ImageView view, String imageIdOrUrl, boolean isThumbnail) {
        loadImage(view, imageIdOrUrl, isThumbnail, !isThumbnail);
    }

    @BindingAdapter("imageUrlOriginal")
    public static void loadImageOriginal(ImageView view, String imageId) {
        loadImage(view, imageId, false, true);
    }

    @BindingAdapter("imageUrlThumbnail")
    public static void loadImageThumbnail(ImageView view, String imageId) {
        loadImage(view, imageId, true, false);
    }

    /**
     * Helper to convert an image ID (from Cloudflare) into a full URL.
     */
    public static String convertImageIdToUrl(String imageIdOrUrl, boolean isThumbnail) {
        if (imageIdOrUrl == null || imageIdOrUrl.isEmpty()) return "";
        String variant = isThumbnail ? "thumbnail" : "2x";
        if (imageIdOrUrl.startsWith("http")) {
            return CardAssetManager.convertToVariant(imageIdOrUrl, variant);
        }
        
        return BASE_URL + com.vn.jet.mosco.BuildConfig.CLOUDFLARE_ACCOUNT_ID + "/" + imageIdOrUrl + "/" + variant;
    }
}

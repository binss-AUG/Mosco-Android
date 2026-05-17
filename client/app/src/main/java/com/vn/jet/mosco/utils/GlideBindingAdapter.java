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
        if (view == null || imageIdOrUrl == null || imageIdOrUrl.isEmpty()) {
            return;
        }

        Context context = view.getContext();
        if (context == null) return;

        // Tìm Skeleton View trong cùng cấp với ImageView (Standard Layout)
        final View skeleton = (view.getParent() instanceof ViewGroup) 
                ? ((ViewGroup) view.getParent()).findViewById(R.id.layout_card_skeleton) 
                : null;

        if (skeleton != null) {
            skeleton.setVisibility(View.VISIBLE);
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
                .placeholder(R.drawable.bg_skeleton_card)
                .error(R.drawable.ic_error_placeholder);

        // Toi uu RAM: Dung RGB_565 cho thumbnail (giam 50% RAM so voi ARGB_8888)
        if (effectiveThumbnail) {
            options = options.format(com.bumptech.glide.load.DecodeFormat.PREFER_RGB_565);
            // Scale down for grid to save memory
            options = options.override(150, 231);
        } else {
            // ARGB_8888 cho bản sắc nét
            options = options.format(com.bumptech.glide.load.DecodeFormat.PREFER_ARGB_8888);
        }

        Glide.with(context)
                .load(loadSource)
                .apply(options)
                .transition(loadSource instanceof java.io.File ? DrawableTransitionOptions.withCrossFade(0) : DrawableTransitionOptions.withCrossFade())
                .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable com.bumptech.glide.load.engine.GlideException e, Object model, 
                                              com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                        if (skeleton != null) skeleton.setVisibility(View.GONE);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, 
                                                 com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, 
                                                 com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                        if (skeleton != null) skeleton.setVisibility(View.GONE);
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
        if (imageIdOrUrl.startsWith("http")) return imageIdOrUrl;
        
        String variant = isThumbnail ? "thumbnail" : "original";
        return BASE_URL + com.vn.jet.mosco.BuildConfig.CLOUDFLARE_ACCOUNT_ID + "/" + imageIdOrUrl + "/" + variant;
    }
}

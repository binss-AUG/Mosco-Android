package com.vn.jet.mosco.utils;

import android.content.Context;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.vn.jet.mosco.R;

import org.json.JSONObject;

import java.io.File;

/**
 * AvatarUtils - Tiện ích nạp Avatar cho toàn hệ thống Mosco.
 * Ưu tiên nạp ảnh đã Crop thủ công nếu là chính chủ (Current User).
 * Fallback về nạp ảnh gốc (Circle Crop) cho người dùng khác hoặc khi chưa có ảnh Crop.
 */
public class AvatarUtils {

    /**
     * Nạp Avatar cho một ImageView bất kỳ.
     * 
     * @param context      Context (nên dùng Activity hoặc Fragment context)
     * @param imageView    ImageView đích
     * @param targetUserId ID của người dùng sở hữu Avatar này (để check chính chủ)
     * @param avatarId     ID của thẻ bài đang được chọn làm Avatar
     */
    public static void loadAvatar(Context context, ImageView imageView, Long targetUserId, String avatarId) {
        loadAvatar(context, imageView, targetUserId, avatarId, null, false);
    }

    public static void loadAvatar(Context context, ImageView imageView, Long targetUserId, String avatarId, boolean isThumbnail) {
        loadAvatar(context, imageView, targetUserId, avatarId, null, isThumbnail);
    }

    public static void loadAvatar(Context context, ImageView imageView, Long targetUserId, String avatarId, String cropParams) {
        loadAvatar(context, imageView, targetUserId, avatarId, cropParams, false);
    }

    public static void loadAvatar(Context context, ImageView imageView, Long targetUserId, String avatarId, String cropParams, boolean isThumbnail) {
        if (context == null || imageView == null) return;

        // 1. Kiểm tra xem targetUserId có phải là người dùng hiện tại không
        SessionManager sessionManager = new SessionManager(context);
        Long currentUserId = sessionManager.getUserId();
        boolean isOwner = targetUserId != null && targetUserId.equals(currentUserId);

        if (isOwner) {
            // [PHASE 1] Nếu là chính chủ, ưu tiên dùng ảnh đã crop thủ công trong bộ nhớ trong (Survive Cache Clear)
            File croppedFile = new File(context.getFilesDir(), context.getString(R.string.avatar_crop_cache_name));
            if (croppedFile.exists()) {
                Glide.with(context)
                        .load(croppedFile)
                        .skipMemoryCache(true)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .circleCrop()
                        .placeholder(R.drawable.ic_user)
                        .error(R.drawable.ic_user)
                        .into(imageView);
                return;
            }
        }

        // [PHASE 2] Fallback: Load từ AvatarId (Circle Crop) cho người khác hoặc khi chưa crop
        loadAvatarById(context, imageView, avatarId, cropParams, isThumbnail);
    }

    public static void loadAvatarById(Context context, ImageView imageView, String avatarId) {
        loadAvatarById(context, imageView, avatarId, null, false);
    }

    public static void loadAvatarById(Context context, ImageView imageView, String avatarId, String cropParams, boolean isThumbnail) {
        if (context == null || imageView == null) return;

        // Lấy cropParams từ Session nếu không có cropParams truyền vào
        if (cropParams == null || cropParams.isEmpty()) {
            SessionManager sessionManager = new SessionManager(context);
            cropParams = sessionManager.getAvatarCropParams();
        }
        boolean hasCropParams = cropParams != null && !cropParams.isEmpty() && !cropParams.equals("auto");

        if (avatarId == null || avatarId.isEmpty() || avatarId.equals("null")) {
            imageView.setImageResource(R.drawable.ic_user);
            return;
        }

        JSONObject card = DatabaseLoader.findByCollectionId(context, avatarId);
        if (card != null) {
            String imgUrl = card.optString("frontImage", "");
            if (imgUrl.isEmpty()) {
                imageView.setImageResource(R.drawable.ic_user);
                return;
            }

            // Đảm bảo URL đầy đủ (Cloudflare / Firebase)
            if (!imgUrl.contains("/original") && !imgUrl.contains("/thumbnail") && !imgUrl.startsWith("http")) {
                imgUrl = GlideBindingAdapter.convertImageIdToUrl(imgUrl, isThumbnail);
            }

            com.bumptech.glide.request.RequestOptions options = new com.bumptech.glide.request.RequestOptions()
                    .placeholder(R.drawable.ic_user)
                    .error(R.drawable.ic_user);

            if (hasCropParams) {
                options = options.transform(new AvatarCropTransformation(cropParams));
            } else {
                options = options.transform(new SmartFaceCropTransformation(imgUrl));
            }

            Glide.with(context)
                    .load(imgUrl)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                    .apply(options)
                    .into(imageView);
        } else {
            imageView.setImageResource(R.drawable.ic_user);
        }
    }
}

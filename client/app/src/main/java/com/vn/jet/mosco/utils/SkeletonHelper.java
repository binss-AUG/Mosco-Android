package com.vn.jet.mosco.utils;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.LayoutRes;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.vn.jet.mosco.R;

/**
 * Lớp tiện ích hỗ trợ tạo hiệu ứng Skeleton động từ bất kỳ cấu trúc View nào.
 * Giúp giao diện đồng bộ tự động mà không cần duy trì các XML skeleton riêng biệt.
 */
public class SkeletonHelper {

    // Màu mặc định cho skeleton khối xám trong Dark Mode của Mosco
    private static final String DEFAULT_SKELETON_COLOR = "#2C2C35";
    private static final int DEFAULT_CORNER_RADIUS_DP = 4;

    /**
     * Chuyển đổi toàn bộ cây View thành dạng skeleton sử dụng màu mặc định.
     */
    public static void skeletonize(@Nullable View view) {
        if (view == null) return;
        int defaultColor = Color.parseColor(DEFAULT_SKELETON_COLOR);
        try {
            // Cố gắng đọc màu từ resources hệ thống để đồng bộ giao diện Dark Mode của Mosco
            defaultColor = ContextCompat.getColor(view.getContext(), R.color.mosco_surface_container_high);
        } catch (Exception e) {
            // Fallback về mã màu hex mặc định nếu context hoặc resource chưa sẵn sàng
        }
        skeletonize(view, defaultColor);
    }

    /**
     * Chuyển đổi toàn bộ cây View thành dạng skeleton với màu chỉ định.
     */
    public static void skeletonize(@Nullable View view, @ColorInt int skeletonColor) {
        if (view == null) return;

        // Cho phép bỏ qua toàn bộ một cụm View lớn nếu lập trình viên gắn tag "skeleton_ignore"
        Object tag = view.getTag();
        if (tag instanceof String && "skeleton_ignore".equals(tag)) {
            return;
        }

        // Biến cả ViewGroup phức tạp thành một khối xám duy nhất và ẩn các View con
        if (tag instanceof String && "skeleton_leaf".equals(tag)) {
            Object isSkeletonized = view.getTag(R.id.tag_is_skeletonized);
            if (!Boolean.TRUE.equals(isSkeletonized)) {
                view.setTag(R.id.tag_original_background, view.getBackground());
                view.setTag(R.id.tag_is_skeletonized, true);
                view.setBackground(createSkeletonDrawable(view.getContext(), skeletonColor));

                if (view instanceof ViewGroup) {
                    ViewGroup group = (ViewGroup) view;
                    for (int i = 0; i < group.getChildCount(); i++) {
                        View child = group.getChildAt(i);
                        child.setTag(R.id.tag_original_visibility, child.getVisibility());
                        child.setVisibility(View.INVISIBLE);
                    }
                }
            }
            return;
        }

        // Tránh xử lý lặp lại nếu View này đã được skeleton hóa rồi
        Object isSkeletonized = view.getTag(R.id.tag_is_skeletonized);
        if (Boolean.TRUE.equals(isSkeletonized)) {
            return;
        }

        if (view instanceof TextView) {
            TextView tv = (TextView) view;

            // Lưu giữ trạng thái nguyên bản để phục hồi sau này, tránh rò rỉ bộ nhớ
            tv.setTag(R.id.tag_original_text_color, tv.getCurrentTextColor());
            tv.setTag(R.id.tag_original_background, tv.getBackground());
            tv.setTag(R.id.tag_is_skeletonized, true);

            // Ẩn nội dung chữ thật bằng màu trong suốt nhưng vẫn giữ nguyên diện tích layout cũ
            tv.setTextColor(Color.TRANSPARENT);

            // Đối với TextView trống có thuộc tính wrap_content, ta điền ký tự giả định
            // ("  00  ") thay vì khoảng trắng để ép buộc công cụ vẽ của Android đo chiều rộng dương,
            // giúp khối xám hiển thị rõ nét trên màn hình và không bị co rút về 0px.
            CharSequence currentText = tv.getText();
            if (currentText == null || currentText.length() == 0) {
                // TẠI SAO: Đánh dấu text gốc rỗng bằng cách sử dụng tag có sẵn R.id.tag_original_text
                // để khôi phục chính xác về rỗng sau khi restore, tránh ghi đè dữ liệu mới của API.
                tv.setTag(R.id.tag_original_text, Boolean.TRUE);
                tv.setText("  00  ");
            } else {
                tv.setTag(R.id.tag_original_text, Boolean.FALSE);
            }

            // Gán background xám bo tròn giả lập thanh text đang tải
            tv.setBackground(createSkeletonDrawable(tv.getContext(), skeletonColor));

        } else if (view instanceof ImageView) {
            ImageView iv = (ImageView) view;

            // TẠI SAO: Kiểm tra xem có phải LottieAnimationView hay không để tránh gọi setImageDrawable(null)
            // làm mất vĩnh viễn composition của Lottie. Ta chỉ ẩn nó đi bằng View.INVISIBLE.
            if (view.getClass().getName().contains("LottieAnimationView")) {
                iv.setTag(R.id.tag_original_visibility, iv.getVisibility());
                iv.setTag(R.id.tag_is_skeletonized, true);
                iv.setVisibility(View.INVISIBLE);
                return;
            }

            iv.setTag(R.id.tag_original_image_drawable, iv.getDrawable());
            iv.setTag(R.id.tag_original_background, iv.getBackground());
            iv.setTag(R.id.tag_is_skeletonized, true);

            // Xóa ảnh thật để tránh lộ ảnh cũ khi đang tải dữ liệu mới
            iv.setImageDrawable(null);
            iv.setBackground(createSkeletonDrawable(iv.getContext(), skeletonColor));

        } else if (view instanceof ViewGroup) {
            // Duyệt đệ quy tất cả View con để skeleton hóa đồng bộ
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                skeletonize(group.getChildAt(i), skeletonColor);
            }
        }
    }

    /**
     * Khôi phục toàn bộ cây View về trạng thái nguyên bản trước khi skeletonize.
     */
    public static void restore(@Nullable View view) {
        if (view == null) return;

        Object tag = view.getTag();
        if (tag instanceof String && "skeleton_ignore".equals(tag)) {
            return;
        }

        // Phục hồi lại ViewGroup có tag "skeleton_leaf" về nguyên bản
        if (tag instanceof String && "skeleton_leaf".equals(tag)) {
            Object isSkeletonized = view.getTag(R.id.tag_is_skeletonized);
            if (Boolean.TRUE.equals(isSkeletonized)) {
                Object origBgObj = view.getTag(R.id.tag_original_background);
                view.setBackground(origBgObj instanceof Drawable ? (Drawable) origBgObj : null);
                view.setTag(R.id.tag_is_skeletonized, null);
                view.setTag(R.id.tag_original_background, null);

                if (view instanceof ViewGroup) {
                    ViewGroup group = (ViewGroup) view;
                    for (int i = 0; i < group.getChildCount(); i++) {
                        View child = group.getChildAt(i);
                        Object origVis = child.getTag(R.id.tag_original_visibility);
                        if (origVis instanceof Integer) {
                            child.setVisibility((Integer) origVis);
                        }
                        child.setTag(R.id.tag_original_visibility, null);
                    }
                }
            }
            return; // Dừng, không duyệt sâu hơn
        }

        Object isSkeletonized = view.getTag(R.id.tag_is_skeletonized);
        if (Boolean.TRUE.equals(isSkeletonized)) {
            if (view instanceof TextView) {
                TextView tv = (TextView) view;

                // Phục hồi lại các thuộc tính cũ từ tag được lưu trữ
                Object origColorObj = tv.getTag(R.id.tag_original_text_color);
                if (origColorObj instanceof Integer) {
                    tv.setTextColor((Integer) origColorObj);
                }

                // Restore background gốc (có thể là null)
                Object origBgObj = tv.getTag(R.id.tag_original_background);
                tv.setBackground(origBgObj instanceof Drawable ? (Drawable) origBgObj : null);

                // Restore text gốc chỉ khi ban đầu TextView trống rỗng (ta đã set "  00  ")
                // TẠI SAO: Nếu ban đầu TextView không rỗng, ta không restore text cũ để tránh ghi đè
                // lên dữ liệu mới tải về từ API/Database vừa được bind trước đó.
                Object wasEmptyObj = tv.getTag(R.id.tag_original_text);
                if (Boolean.TRUE.equals(wasEmptyObj)) {
                    tv.setText("");
                }

            } else if (view instanceof ImageView) {
                ImageView iv = (ImageView) view;

                // TẠI SAO: Khôi phục lại visibility gốc cho LottieAnimationView thay vì khôi phục drawable.
                if (view.getClass().getName().contains("LottieAnimationView")) {
                    Object origVis = iv.getTag(R.id.tag_original_visibility);
                    if (origVis instanceof Integer) {
                        iv.setVisibility((Integer) origVis);
                    }
                    iv.setTag(R.id.tag_is_skeletonized, null);
                    iv.setTag(R.id.tag_original_visibility, null);
                    return;
                }

                Object origDrawableObj = iv.getTag(R.id.tag_original_image_drawable);
                iv.setImageDrawable(origDrawableObj instanceof Drawable ? (Drawable) origDrawableObj : null);

                Object origBgObj = iv.getTag(R.id.tag_original_background);
                iv.setBackground(origBgObj instanceof Drawable ? (Drawable) origBgObj : null);
            }

            // Gỡ bỏ tag đánh dấu để sẵn sàng cho lần loading tiếp theo
            view.setTag(R.id.tag_is_skeletonized, null);
            view.setTag(R.id.tag_original_text_color, null);
            view.setTag(R.id.tag_original_background, null);
            view.setTag(R.id.tag_original_text, null);
            view.setTag(R.id.tag_original_image_drawable, null);
        }

        // Tiếp tục đệ quy phục hồi cho các View con
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                restore(group.getChildAt(i));
            }
        }
    }

    /**
     * Tạo Drawable màu xám tối bo tròn góc làm giả lập Skeleton.
     */
    @NonNull
    private static Drawable createSkeletonDrawable(@NonNull Context context, @ColorInt int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setColor(color);
        // Thiết lập bo góc mặc định để skeleton trông cao cấp và mềm mại hơn
        float density = context.getResources().getDisplayMetrics().density;
        shape.setCornerRadius(DEFAULT_CORNER_RADIUS_DP * density);
        return shape;
    }

    /**
     * Tự động tạo danh sách item skeleton bằng cách inflate layout thật và áp dụng hiệu ứng.
     * Tại sao (WHY): Loại bỏ việc phải viết lại các file XML skeleton, đảm bảo khi layout thật thay đổi
     * thì hiệu ứng shimmer skeleton luôn khớp 100% với giao diện mới.
     */
    public static void populateShimmerContainer(@Nullable ViewGroup container, @LayoutRes int itemLayoutId, int itemCount) {
        if (container == null) return;
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(container.getContext());
        for (int i = 0; i < itemCount; i++) {
            View itemView = inflater.inflate(itemLayoutId, container, false);
            skeletonize(itemView);
            container.addView(itemView);
        }
    }
}

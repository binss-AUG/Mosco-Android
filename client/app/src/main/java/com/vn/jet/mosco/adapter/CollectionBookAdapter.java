package com.vn.jet.mosco.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.model.CollectionEntry;

import java.util.List;

/**
 * Adapter cho Bộ Sưu Tập (Collection Book).
 * Hiển thị grid thẻ bài với 2 trạng thái:
 * - Đã sở hữu: Hình rõ nét, có OVR badge + Level badge
 * - Chưa sở hữu: Hình mờ grayscale, overlay tối, icon khóa
 * Không áp dụng hiệu ứng Glow/Shimmer/Floating cho Album.
 */
public class CollectionBookAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_ITEM = 0;
    private static final int VIEW_TYPE_SKELETON = 1;

    private List<CollectionEntry> list;
    private final OnBookCardClickListener listener;
    private boolean isLoading = false;
    private boolean isPagingLoading = false;

    /** Callback khi user click vào 1 thẻ trong Album */
    public interface OnBookCardClickListener {
        void onCardClick(CollectionEntry entry);
    }

    public CollectionBookAdapter(List<CollectionEntry> list, OnBookCardClickListener listener) {
        this.list = list;
        this.listener = listener;
    }

    public void setLoading(boolean loading) {
        this.isLoading = loading;
        notifyDataSetChanged();
    }

    public void updateData(List<CollectionEntry> newList) {
        this.list = newList;
        this.isLoading = false;
        this.isPagingLoading = false;
        notifyDataSetChanged();
    }

    public void setPagingLoading(boolean loading) {
        if (this.isPagingLoading == loading) return;
        this.isPagingLoading = loading;
        if (loading) {
            notifyItemRangeInserted(list == null ? 0 : list.size(), 3);
        } else {
            notifyItemRangeRemoved(list == null ? 0 : list.size(), 3);
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (isLoading) return VIEW_TYPE_SKELETON;
        if (list != null && position >= list.size()) return VIEW_TYPE_SKELETON;
        return VIEW_TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_SKELETON) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_objet_skeleton, parent, false);
            return new SkeletonViewHolder(v);
        }
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_collection_book_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ViewHolder) {
            ViewHolder vh = (ViewHolder) holder;
            CollectionEntry entry = list.get(position);
            if (entry == null) return;
            Context ctx = vh.itemView.getContext();

            // Load hình ảnh thẻ bài (Sử dụng GlideBindingAdapter đã chuẩn hóa)
            String imageUrl = entry.getFrontImage();
            com.vn.jet.mosco.utils.GlideBindingAdapter.loadImage(vh.ivCardImage, imageUrl, true);

            if (entry.isOwned()) {
                // === TRẠNG THÁI: ĐÃ SỞ HỮU ===
                vh.ivCardImage.setColorFilter(null);
                vh.ivCardImage.setAlpha(1.0f);
                vh.viewLockedOverlay.setVisibility(View.GONE);
                vh.ivLockIcon.setVisibility(View.GONE);

                // Hiện OVR badge
                if (vh.tvOvr != null) {
                    vh.tvOvr.setText(String.valueOf(entry.getOvr()));
                    vh.tvOvr.setVisibility(View.GONE);
                }

                // Ẩn Level badge (Album không cần hiển thị cấp độ)
                if (vh.ivLevel != null) {
                    vh.ivLevel.setVisibility(View.GONE);
                }

                if (vh.cvCard != null) {
                    vh.cvCard.setCardBackgroundColor(0xFF1A1C29);
                }

            } else {
                // === TRẠNG THÁI: CHƯA SỞ HỮU ===
                android.graphics.ColorMatrix matrix = new android.graphics.ColorMatrix();
                matrix.setSaturation(0f);
                vh.ivCardImage.setColorFilter(new android.graphics.ColorMatrixColorFilter(matrix));
                vh.ivCardImage.setAlpha(0.2f);

                vh.viewLockedOverlay.setVisibility(View.VISIBLE);
                vh.ivLockIcon.setVisibility(View.VISIBLE);

                if (vh.tvOvr != null) vh.tvOvr.setVisibility(View.GONE);
                if (vh.ivLevel != null) vh.ivLevel.setVisibility(View.GONE);

                if (vh.cvCard != null) {
                    vh.cvCard.setCardBackgroundColor(0xFF0D0F1A);
                }
            }

            vh.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onCardClick(entry);
            });
        }
    }

    @Override
    public int getItemCount() {
        if (isLoading) return 12;
        int count = list == null ? 0 : list.size();
        if (isPagingLoading) count += 3;
        return count;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        androidx.cardview.widget.CardView cvCard;
        ImageView ivCardImage, ivLockIcon, ivLevel;
        View viewLockedOverlay;
        TextView tvOvr;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cvCard = itemView.findViewById(R.id.cv_book_card);
            ivCardImage = itemView.findViewById(R.id.card_iv_image);
            ivLockIcon = itemView.findViewById(R.id.iv_lock_icon);
            ivLevel = itemView.findViewById(R.id.card_iv_level);
            viewLockedOverlay = itemView.findViewById(R.id.view_locked_overlay);
            tvOvr = itemView.findViewById(R.id.card_tv_ovr);
        }
    }

    static class SkeletonViewHolder extends RecyclerView.ViewHolder {
        public SkeletonViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}

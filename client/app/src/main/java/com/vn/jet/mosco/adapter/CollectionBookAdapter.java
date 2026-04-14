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
public class CollectionBookAdapter extends RecyclerView.Adapter<CollectionBookAdapter.ViewHolder> {

    private List<CollectionEntry> list;
    private final OnBookCardClickListener listener;

    /** Callback khi user click vào 1 thẻ trong Album */
    public interface OnBookCardClickListener {
        void onCardClick(CollectionEntry entry);
    }

    public CollectionBookAdapter(List<CollectionEntry> list, OnBookCardClickListener listener) {
        this.list = list;
        this.listener = listener;
    }

    /** Cập nhật dữ liệu mới (sau khi filter hoặc API trả về) */
    public void updateData(List<CollectionEntry> newList) {
        this.list = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_collection_book_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CollectionEntry entry = list.get(position);
        if (entry == null) return;
        Context ctx = holder.itemView.getContext();

        // Load hình ảnh thẻ bài (Ưu tiên bộ nhớ Local)
        String imageUrl = entry.getFrontImage();
        java.io.File localFile = com.vn.jet.mosco.utils.CardAssetManager.getLocalFile(ctx, imageUrl);

        Glide.with(ctx)
                .load(localFile != null && localFile.exists() ? localFile : imageUrl)
                .placeholder(R.drawable.item_shop_demo)
                .into(holder.ivCardImage);

        if (entry.isOwned()) {
            // === TRẠNG THÁI: ĐÃ SỞ HỮU ===
            holder.ivCardImage.setColorFilter(null);
            holder.ivCardImage.setAlpha(1.0f);
            holder.viewLockedOverlay.setVisibility(View.GONE);
            holder.ivLockIcon.setVisibility(View.GONE);

            // Hiện OVR badge
            if (holder.tvOvr != null) {
                holder.tvOvr.setText(String.valueOf(entry.getOvr()));
                holder.tvOvr.setVisibility(View.GONE);
            }

            // Hiện Level badge
            if (holder.ivLevel != null) {
                if (entry.getLevel() > 0) {
                    String assetPath = "file:///android_asset/grade/" + entry.getLevel() + ".png";
                    Glide.with(ctx).load(assetPath).into(holder.ivLevel);
                    holder.ivLevel.setVisibility(View.VISIBLE);
                } else {
                    holder.ivLevel.setVisibility(View.GONE);
                }
            }

            if (holder.cvCard != null) {
                holder.cvCard.setCardBackgroundColor(0xFF1A1C29);
            }

        } else {
            // === TRẠNG THÁI: CHƯA SỞ HỮU ===
            android.graphics.ColorMatrix matrix = new android.graphics.ColorMatrix();
            matrix.setSaturation(0f);
            holder.ivCardImage.setColorFilter(new android.graphics.ColorMatrixColorFilter(matrix));
            holder.ivCardImage.setAlpha(0.2f);

            holder.viewLockedOverlay.setVisibility(View.VISIBLE);
            holder.ivLockIcon.setVisibility(View.VISIBLE);

            if (holder.tvOvr != null) holder.tvOvr.setVisibility(View.GONE);
            if (holder.ivLevel != null) holder.ivLevel.setVisibility(View.GONE);

            if (holder.cvCard != null) {
                holder.cvCard.setCardBackgroundColor(0xFF0D0F1A);
            }
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onCardClick(entry);
        });
    }

    @Override
    public int getItemCount() { return list != null ? list.size() : 0; }

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
}

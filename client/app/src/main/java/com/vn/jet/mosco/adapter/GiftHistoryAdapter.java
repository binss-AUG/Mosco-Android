package com.vn.jet.mosco.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.vn.jet.mosco.R;

import org.json.JSONObject;

import java.util.List;

/**
 * Adapter cho danh sách lịch sử Gift (tab Nhận).
 * Hiển thị: thumbnail thẻ, tên người gửi, thời gian, badge "NEW".
 */
public class GiftHistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_ITEM = 0;
    private static final int VIEW_TYPE_SKELETON = 1;

    private List<JSONObject> data;
    private final boolean isReceivedTab; // true = tab Nhận, false = tab Gửi
    private boolean isLoading = false;

    public GiftHistoryAdapter(List<JSONObject> data, boolean isReceivedTab) {
        this.data = data;
        this.isReceivedTab = isReceivedTab;
    }

    public void setLoading(boolean loading) {
        this.isLoading = loading;
        notifyDataSetChanged();
    }

    public void updateData(List<JSONObject> newData) {
        this.data = newData;
        this.isLoading = false;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        if (isLoading) return VIEW_TYPE_SKELETON;
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
                .inflate(R.layout.item_gift_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ViewHolder) {
            ViewHolder vh = (ViewHolder) holder;
            try {
                JSONObject entry = data.get(position);

                // Tên người liên quan
                if (isReceivedTab) {
                    vh.tvUserName.setText(entry.optString("senderName", "Unknown"));
                    vh.tvDirection.setText(vh.itemView.getContext().getString(R.string.gift_label_received_from));
                } else {
                    vh.tvUserName.setText(entry.optString("receiverName", "Unknown"));
                    vh.tvDirection.setText(vh.itemView.getContext().getString(R.string.gift_label_sent_to));
                }

                // Thời gian — format đơn giản
                String createdAt = entry.optString("createdAt", "");
                if (createdAt.length() > 16) {
                    // Cắt lấy "yyyy-MM-dd HH:mm"
                    vh.tvTime.setText(createdAt.substring(0, 16).replace("T", " "));
                } else {
                    vh.tvTime.setText(createdAt);
                }

                // Thumbnail thẻ
                String frontImage = entry.optString("cardFrontImage", "");
                if (!frontImage.isEmpty()) {
                    Glide.with(vh.itemView.getContext())
                            .load(frontImage)
                            .placeholder(R.drawable.objet_back_spin)
                            .into(vh.ivCardThumb);
                }

                // Badge "NEW"
                vh.tvBadgeNew.setVisibility(View.GONE);

            } catch (Exception ignored) {}
        }
    }

    @Override
    public int getItemCount() {
        if (isLoading) return 5;
        return data != null ? data.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivCardThumb;
        TextView tvUserName, tvDirection, tvTime, tvBadgeNew;

        ViewHolder(View itemView) {
            super(itemView);
            ivCardThumb = itemView.findViewById(R.id.iv_gift_card_thumb);
            tvUserName = itemView.findViewById(R.id.tv_gift_user_name);
            tvDirection = itemView.findViewById(R.id.tv_gift_direction);
            tvTime = itemView.findViewById(R.id.tv_gift_time);
            tvBadgeNew = itemView.findViewById(R.id.tv_gift_badge_new);
        }
    }

    static class SkeletonViewHolder extends RecyclerView.ViewHolder {
        public SkeletonViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}

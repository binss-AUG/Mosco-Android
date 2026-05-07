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
public class GiftHistoryAdapter extends RecyclerView.Adapter<GiftHistoryAdapter.ViewHolder> {

    private List<JSONObject> data;
    private final boolean isReceivedTab; // true = tab Nhận, false = tab Gửi

    public GiftHistoryAdapter(List<JSONObject> data, boolean isReceivedTab) {
        this.data = data;
        this.isReceivedTab = isReceivedTab;
    }

    public void updateData(List<JSONObject> newData) {
        this.data = newData;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_gift_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        try {
            JSONObject entry = data.get(position);

            // Tên người liên quan
            if (isReceivedTab) {
                holder.tvUserName.setText(entry.optString("senderName", "Unknown"));
                holder.tvDirection.setText(holder.itemView.getContext().getString(R.string.gift_label_received_from));
            } else {
                holder.tvUserName.setText(entry.optString("receiverName", "Unknown"));
                holder.tvDirection.setText(holder.itemView.getContext().getString(R.string.gift_label_sent_to));
            }

            // Thời gian — format đơn giản
            String createdAt = entry.optString("createdAt", "");
            if (createdAt.length() > 16) {
                // Cắt lấy "yyyy-MM-dd HH:mm"
                holder.tvTime.setText(createdAt.substring(0, 16).replace("T", " "));
            } else {
                holder.tvTime.setText(createdAt);
            }

            // Thumbnail thẻ
            String frontImage = entry.optString("cardFrontImage", "");
            if (!frontImage.isEmpty()) {
                Glide.with(holder.itemView.getContext())
                        .load(frontImage)
                        .placeholder(R.drawable.objet_back_spin)
                        .into(holder.ivCardThumb);
            }

            // Badge "NEW" — chỉ hiện ở tab Nhận khi chưa đọc
            // Server không trả field này trực tiếp trong history, 
            // nên ẩn mặc định. GiftActivity sẽ xử lý mark-read khi mở tab.
            holder.tvBadgeNew.setVisibility(View.GONE);

        } catch (Exception ignored) {}
    }

    @Override
    public int getItemCount() { return data != null ? data.size() : 0; }

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
}

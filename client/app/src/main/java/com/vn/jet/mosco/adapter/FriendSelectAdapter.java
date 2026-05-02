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
import com.vn.jet.mosco.utils.DatabaseLoader;
import com.vn.jet.mosco.utils.SmartFaceCropTransformation;

import org.json.JSONObject;

import java.util.List;

/**
 * Adapter chọn bạn bè khi gửi tặng Objet — dạng radio single-select.
 * Reuse logic avatar load từ FriendAdapter.
 */
public class FriendSelectAdapter extends RecyclerView.Adapter<FriendSelectAdapter.ViewHolder> {

    private List<JSONObject> data;
    private int selectedPosition = -1;
    private OnFriendSelectedListener listener;

    public interface OnFriendSelectedListener {
        void onFriendSelected(JSONObject friend, int position);
    }

    public FriendSelectAdapter(List<JSONObject> data, OnFriendSelectedListener listener) {
        this.data = data;
        this.listener = listener;
    }

    public void updateData(List<JSONObject> newData) {
        this.data = newData;
        this.selectedPosition = -1;
        notifyDataSetChanged();
    }

    /**
     * Lấy bạn bè đang được chọn — trả null nếu chưa chọn.
     */
    public JSONObject getSelectedFriend() {
        if (selectedPosition >= 0 && selectedPosition < data.size()) {
            return data.get(selectedPosition);
        }
        return null;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_friend_select, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        try {
            JSONObject entry = data.get(position);
            holder.tvName.setText(entry.optString("ingameName", "Unknown"));
            holder.tvLevel.setText("LV " + entry.optInt("level", 1));

            // Avatar — load từ avatarId giống FriendAdapter
            String avatarId = entry.optString("avatarId", "1");
            JSONObject card = DatabaseLoader.findByCollectionId(holder.itemView.getContext(), avatarId);
            if (card != null) {
                String imgUrl = card.optString("frontImage", "");
                Glide.with(holder.itemView.getContext())
                        .load(imgUrl)
                        .transform(new SmartFaceCropTransformation())
                        .placeholder(R.drawable.ic_user)
                        .into(holder.ivAvatar);
            } else {
                holder.ivAvatar.setImageResource(R.drawable.ic_user);
            }

            // Hiệu ứng chọn: Highlight toàn bộ card thay vì dùng chấm tròn
            boolean isSelected = (position == selectedPosition);
            if (isSelected) {
                holder.itemView.setBackgroundResource(R.drawable.bg_friend_selected_luxury);
            } else {
                holder.itemView.setBackgroundResource(R.drawable.bg_header_glass_v2);
            }

            // Click → select
            holder.itemView.setOnClickListener(v -> {
                int prev = selectedPosition;
                selectedPosition = holder.getAdapterPosition();
                if (prev >= 0) notifyItemChanged(prev);
                notifyItemChanged(selectedPosition);
                if (listener != null) listener.onFriendSelected(entry, selectedPosition);
            });
        } catch (Exception ignored) {}
    }

    @Override
    public int getItemCount() { return data != null ? data.size() : 0; }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvLevel;
        ImageView ivAvatar;

        ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_friend_select_name);
            tvLevel = itemView.findViewById(R.id.tv_friend_select_level);
            ivAvatar = itemView.findViewById(R.id.iv_friend_select_avatar);
        }
    }
}

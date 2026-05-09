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
 * Adapter for friend list — shows avatar, name, level.
 * Avatar uses default ic_user since other users' avatars are stored locally on their devices.
 */
public class FriendAdapter extends RecyclerView.Adapter<FriendAdapter.FriendViewHolder> {

    private List<JSONObject> data;
    private List<JSONObject> fullData;

    public FriendAdapter(List<JSONObject> data) {
        this.data = new java.util.ArrayList<>(data);
        this.fullData = new java.util.ArrayList<>(data);
    }

    public void updateData(List<JSONObject> newData) {
        this.data = new java.util.ArrayList<>(newData);
        this.fullData = new java.util.ArrayList<>(newData);
        notifyDataSetChanged();
    }

    /**
     * Lọc danh sách bạn bè theo tên hoặc ID ngay lập tức (Real-time).
     * Hỗ trợ tìm kiếm theo ID định dạng (1000000x) và tên hiển thị.
     */
    public void filter(String query) {
        if (query == null || query.isEmpty()) {
            data = new java.util.ArrayList<>(fullData);
        } else {
            List<JSONObject> filtered = new java.util.ArrayList<>();
            String lowerQuery = query.toLowerCase().trim();
            
            for (JSONObject obj : fullData) {
                String name = obj.optString("ingameName", "").toLowerCase();
                String username = obj.optString("username", "").toLowerCase();
                long rawId = obj.optLong("userId", -1);
                String formattedId = String.valueOf(10000000 + rawId);
                
                // Lọc theo: Tên chứa query HOẶC ID định dạng chứa query (Không cho phép tìm theo ID gốc)
                if (name.contains(lowerQuery) || 
                    username.contains(lowerQuery) ||
                    formattedId.contains(lowerQuery)) {
                    filtered.add(obj);
                }
            }
            data = filtered;
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FriendViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_friend_entry, parent, false);
        return new FriendViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FriendViewHolder holder, int position) {
        try {
            JSONObject entry = data.get(position);
            holder.tvName.setText(entry.optString("ingameName", "Unknown"));
            holder.tvLevel.setText(holder.itemView.getContext().getString(R.string.format_level_short, entry.optInt("level", 1)));

            // --- 🟢 STATUS GLOW LOGIC (Sử dụng data thực từ server) ---
            boolean isOnline = entry.optBoolean("online", false); 
            holder.viewStatus.setVisibility(isOnline ? View.VISIBLE : View.GONE);

            // --- 🎭 SYNC AVATAR LOGIC ---
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

            // Bridge Bridge: Nhấn vào item mở profile thay vì popup cũ
            holder.itemView.setOnClickListener(v -> {
                long userId = entry.optLong("userId", -1L);
                if (userId != -1L) {
                    com.vn.jet.mosco.utils.NavigationUtils.openProfile((androidx.fragment.app.FragmentActivity) holder.itemView.getContext(), userId);
                }
            });

        } catch (Exception ignored) {}
    }

    @Override
    public int getItemCount() { return data != null ? data.size() : 0; }

    static class FriendViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvLevel;
        ImageView ivAvatar;
        View viewStatus;

        FriendViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_friend_name);
            tvLevel = itemView.findViewById(R.id.tv_friend_level);
            ivAvatar = itemView.findViewById(R.id.iv_friend_avatar);
            viewStatus = itemView.findViewById(R.id.view_online_status);
        }
    }
}

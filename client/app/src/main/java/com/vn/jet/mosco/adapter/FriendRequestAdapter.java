package com.vn.jet.mosco.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.vn.jet.mosco.R;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for friend requests — shows avatar, name, level, Accept + Reject buttons.
 * Nâng cấp bổ sung cơ chế lọc thời gian thực (Real-time Filtering) đồng bộ luồng tra cứu trung tâm.
 */
public class FriendRequestAdapter extends RecyclerView.Adapter<FriendRequestAdapter.RequestViewHolder> {

    private List<JSONObject> data;
    private List<JSONObject> fullData;
    private final OnRequestActionListener listener;

    /**
     * Callback interface for Accept/Reject actions.
     */
    public interface OnRequestActionListener {
        void onAccept(Long friendshipId);
        void onReject(Long friendshipId);
    }

    public FriendRequestAdapter(List<JSONObject> data, OnRequestActionListener listener) {
        this.data = new ArrayList<>(data);
        this.fullData = new ArrayList<>(data);
        this.listener = listener;
    }

    public void updateData(List<JSONObject> newData) {
        this.data = new ArrayList<>(newData);
        this.fullData = new ArrayList<>(newData);
        notifyDataSetChanged();
    }

    /**
     * Lọc danh sách lời mời kết bạn cục bộ theo từ khóa (Tên ingame hoặc ID định dạng).
     * Lý do (WHY): Hỗ trợ tìm kiếm chớp nhoáng mà không cần gửi truy vấn lên máy chủ, tiết kiệm trọn vẹn băng thông.
     */
    public void filter(String query) {
        if (query == null || query.trim().isEmpty()) {
            data = new ArrayList<>(fullData);
        } else {
            List<JSONObject> filtered = new ArrayList<>();
            String lowerQuery = query.toLowerCase().trim();

            for (JSONObject obj : fullData) {
                String name = obj.optString("ingameName", "").toLowerCase();
                String username = obj.optString("username", "").toLowerCase();
                long rawId = obj.optLong("userId", -1);
                String formattedId = String.valueOf(10000000 + rawId);

                // Đối soát toàn diện trên Tên hiển thị, tài khoản hoặc định dạng ID tiêu chuẩn
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
    public RequestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_friend_request, parent, false);
        return new RequestViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RequestViewHolder holder, int position) {
        try {
            JSONObject entry = data.get(position);
            Long friendshipId = entry.optLong("friendshipId");

            holder.tvName.setText(entry.optString("ingameName", "Unknown"));
            holder.tvLevel.setText(holder.itemView.getContext().getString(R.string.format_level_short, entry.optInt("level", 1)));

            long userId = entry.optLong("userId", -1L);
            String avatarId = entry.optString("avatarId", "1");
            com.vn.jet.mosco.utils.AvatarUtils.loadAvatar(holder.itemView.getContext(), holder.ivAvatar, userId, avatarId);

            // Nút đồng ý lời mời (Accept)
            holder.btnAccept.setOnClickListener(v -> {
                if (listener != null) listener.onAccept(friendshipId);
            });

            // Nhấn vào avatar mở trang cá nhân xem thông tin chi tiết trước khi quyết định
            holder.ivAvatar.setOnClickListener(v -> {
                if (userId != -1L) {
                    com.vn.jet.mosco.utils.NavigationUtils.openProfile((androidx.fragment.app.FragmentActivity) holder.itemView.getContext(), userId);
                }
            });

            // Nút từ chối lời mời (Reject)
            holder.btnReject.setOnClickListener(v -> {
                if (listener != null) listener.onReject(friendshipId);
            });
        } catch (Exception ignored) {}
    }

    @Override
    public int getItemCount() { return data != null ? data.size() : 0; }

    static class RequestViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvLevel;
        Button btnAccept;
        ImageView btnReject, ivAvatar;

        RequestViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_friend_name);
            tvLevel = itemView.findViewById(R.id.tv_friend_level);
            btnAccept = itemView.findViewById(R.id.btn_accept);
            btnReject = itemView.findViewById(R.id.btn_reject);
            ivAvatar = itemView.findViewById(R.id.iv_friend_avatar);
        }
    }
}

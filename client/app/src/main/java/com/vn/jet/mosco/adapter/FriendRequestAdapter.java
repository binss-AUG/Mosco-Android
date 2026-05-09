package com.vn.jet.mosco.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
 * Adapter for friend requests — shows avatar, name, level, Accept + Reject buttons.
 */
public class FriendRequestAdapter extends RecyclerView.Adapter<FriendRequestAdapter.RequestViewHolder> {

    private List<JSONObject> data;
    private final OnRequestActionListener listener;

    /**
     * Callback interface for Accept/Reject actions.
     */
    public interface OnRequestActionListener {
        void onAccept(Long friendshipId);
        void onReject(Long friendshipId);
    }

    public FriendRequestAdapter(List<JSONObject> data, OnRequestActionListener listener) {
        this.data = data;
        this.listener = listener;
    }

    public void updateData(List<JSONObject> newData) {
        this.data = newData;
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

            // Accept button
            holder.btnAccept.setOnClickListener(v -> {
                if (listener != null) listener.onAccept(friendshipId);
            });

            // Bridge Bridge: Nhấn vào avatar mở profile xem info trước khi accept
            holder.ivAvatar.setOnClickListener(v -> {
                long userId = entry.optLong("userId", -1L);
                if (userId != -1L) {
                    com.vn.jet.mosco.utils.NavigationUtils.openProfile((androidx.fragment.app.FragmentActivity) holder.itemView.getContext(), userId);
                }
            });

            // Reject button
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

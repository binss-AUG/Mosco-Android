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

    public FriendAdapter(List<JSONObject> data) {
        this.data = data;
    }

    public void updateData(List<JSONObject> newData) {
        this.data = newData;
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
            holder.tvLevel.setText("LV " + entry.optInt("level", 1));

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
        } catch (Exception ignored) {}
    }

    @Override
    public int getItemCount() { return data != null ? data.size() : 0; }

    static class FriendViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvLevel;
        ImageView ivAvatar;

        FriendViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_friend_name);
            tvLevel = itemView.findViewById(R.id.tv_friend_level);
            ivAvatar = itemView.findViewById(R.id.iv_friend_avatar);
        }
    }
}

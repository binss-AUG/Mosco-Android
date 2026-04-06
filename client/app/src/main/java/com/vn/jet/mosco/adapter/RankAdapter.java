package com.vn.jet.mosco.adapter;

import android.graphics.Color;
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
 * Adapter hiển thị danh sách xếp hạng.
 * Top 1/2/3 highlight màu vàng/bạc/đồng (Gold/Silver/Bronze).
 */
public class RankAdapter extends RecyclerView.Adapter<RankAdapter.RankViewHolder> {

    private List<JSONObject> data;
    private final String rankType;

    // Màu highlight cho Top 3
    private static final int COLOR_GOLD = Color.parseColor("#FFD700");
    private static final int COLOR_SILVER = Color.parseColor("#C0C0C0");
    private static final int COLOR_BRONZE = Color.parseColor("#CD7F32");

    public RankAdapter(List<JSONObject> data, String rankType) {
        this.data = data;
        this.rankType = rankType;
    }

    /**
     * Cập nhật dữ liệu và refresh UI.
     */
    public void updateData(List<JSONObject> newData) {
        this.data = newData;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RankViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_rank_entry, parent, false);
        return new RankViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RankViewHolder holder, int position) {
        try {
            JSONObject entry = data.get(position);
            int rank = position + 1;

            // Hạng
            holder.tvPosition.setText(String.valueOf(rank));

            // Tên người chơi
            holder.tvName.setText(entry.optString("ingameName", "Unknown"));

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

            // Chỉ số — định dạng tiền tố/hậu tố dể rõ ràng
            int value = entry.optInt("value", 0);
            switch (rankType) {
                case "level": 
                    holder.tvValue.setText("LV " + value);
                    break;
                case "ovr": 
                    holder.tvValue.setText("OVR " + value);
                    break;
                case "collection": 
                    holder.tvValue.setText(value + " Objet");
                    break;
            }

            // Highlight Top 3 (Gold / Silver / Bronze)
            if (rank == 1) {
                holder.tvPosition.setTextColor(COLOR_GOLD);
                holder.tvValue.setTextColor(COLOR_GOLD);
            } else if (rank == 2) {
                holder.tvPosition.setTextColor(COLOR_SILVER);
                holder.tvValue.setTextColor(COLOR_SILVER);
            } else if (rank == 3) {
                holder.tvPosition.setTextColor(COLOR_BRONZE);
                holder.tvValue.setTextColor(COLOR_BRONZE);
            } else {
                holder.tvPosition.setTextColor(Color.WHITE);
                holder.tvValue.setTextColor(Color.parseColor("#00E5FF")); // Neon Cyan
            }

        } catch (Exception e) {
            // Null-safety: không crash nếu data bị lỗi
        }
    }

    @Override
    public int getItemCount() {
        return data != null ? data.size() : 0;
    }

    static class RankViewHolder extends RecyclerView.ViewHolder {
        TextView tvPosition, tvName, tvValue;
        ImageView ivAvatar;

        RankViewHolder(View itemView) {
            super(itemView);
            tvPosition = itemView.findViewById(R.id.tv_rank_position);
            tvName = itemView.findViewById(R.id.tv_rank_name);
            tvValue = itemView.findViewById(R.id.tv_rank_value);
            ivAvatar = itemView.findViewById(R.id.iv_rank_avatar);
        }
    }
}

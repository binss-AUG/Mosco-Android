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
    private Long currentUserId;

    public RankAdapter(List<JSONObject> data, String rankType) {
        this.data = data;
        this.rankType = rankType;
    }

    public RankAdapter(List<JSONObject> data, String rankType, Long currentUserId) {
        this.data = data;
        this.rankType = rankType;
        this.currentUserId = currentUserId;
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
            int rank = entry.optInt("rank", position + 4);

            holder.tvPosition.setText(String.valueOf(rank));
            holder.tvName.setText(entry.optString("ingameName", "Unknown"));

            String avatarId = entry.optString("avatarId", "1");
            long userId = entry.optLong("userId", -1L);
            // Luồng tải ưu tiên: Avatar trong danh sách Rank dùng bản thumbnail
            com.vn.jet.mosco.utils.AvatarUtils.loadAvatar(holder.itemView.getContext(), holder.ivAvatar, userId, avatarId, true);

            // Xử lý Duo Streak (Avatar lồng)
            if (rankType.equals("duo-streak") && entry.has("partnerAvatarId") && holder.cvPartnerAvatar != null && holder.ivPartnerAvatar != null) {
                holder.cvPartnerAvatar.setVisibility(View.VISIBLE);
                String partnerAvatarId = entry.optString("partnerAvatarId", "1");
                long partnerId = entry.optLong("partnerId", -1L);
                com.vn.jet.mosco.utils.AvatarUtils.loadAvatar(holder.itemView.getContext(), holder.ivPartnerAvatar, partnerId, partnerAvatarId, true);
            } else if (holder.cvPartnerAvatar != null) {
                holder.cvPartnerAvatar.setVisibility(View.GONE);
            }

            int value = entry.optInt("value", 0);
            android.content.Context context = holder.itemView.getContext();
            switch (rankType) {
                case "level": 
                    holder.tvValue.setText(String.format("Lv. %d", value));
                    break;
                case "social": 
                    holder.tvValue.setText(String.format("%d Friends", value));
                    break;
                case "collection": 
                    holder.tvValue.setText(String.format("%d Objets", value));
                    break;
                case "fame":
                    holder.tvValue.setText(String.format("%d Likes", value));
                    break;
                case "duo-streak":
                case "streak":
                    holder.tvValue.setText(String.format("%d Days", value));
                    break;
                case "wealth":
                    holder.tvValue.setText(com.vn.jet.mosco.utils.NumberUtils.format(context, (long)value));
                    break;
            }

            if (currentUserId != null && userId == currentUserId) {
                holder.itemView.setBackgroundResource(R.drawable.bg_rank_item_highlight);
            } else {
                holder.itemView.setBackgroundResource(R.drawable.bg_rank_item);
            }

            // Click Avatar hoặc Tên để mở Profile (Bridge Bridge)
            View.OnClickListener profileClick = v -> {
                if (userId != -1L) {
                    com.vn.jet.mosco.utils.NavigationUtils.openProfile((androidx.fragment.app.FragmentActivity) context, userId);
                }
            };
            holder.ivAvatar.setOnClickListener(profileClick);
            holder.tvName.setOnClickListener(profileClick);
            
            if (holder.ivPartnerAvatar != null) {
                holder.ivPartnerAvatar.setOnClickListener(v -> {
                    long partnerId = entry.optLong("partnerId", -1L);
                    if (partnerId != -1L) {
                        com.vn.jet.mosco.utils.NavigationUtils.openProfile((androidx.fragment.app.FragmentActivity) context, partnerId);
                    }
                });
            }

        } catch (Exception e) {
            // Null-safety
        }
    }

    private void showStreakDetail(android.content.Context context, JSONObject entry) {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = 
            new com.google.android.material.bottomsheet.BottomSheetDialog(context, R.style.CustomBottomSheetDialogTheme);
        View view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_streak_detail, null);
        
        TextView tvCurrent = view.findViewById(R.id.tv_current_streak);
        TextView tvBest = view.findViewById(R.id.tv_best_streak);
        com.airbnb.lottie.LottieAnimationView ivIcon = view.findViewById(R.id.iv_streak_icon);
        ivIcon.setMinAndMaxFrame(0, 24);
        ivIcon.playAnimation();
        int currentStreak = entry.optInt("value", 0);
        com.vn.jet.mosco.utils.StreakColorHelper.applyStreakColor(ivIcon, currentStreak);
        android.widget.Button btnRestore = view.findViewById(R.id.btn_restore_streak);
        tvCurrent.setText(context.getString(R.string.rank_format_streak, currentStreak));
        
        // Vẽ vời: Record giả lập hoặc lấy từ data nếu có
        int bestStreak = entry.optInt("bestStreak", currentStreak + 5); 
        tvBest.setText(context.getString(R.string.rank_format_streak, bestStreak));

        // Animation cho ngọn lửa (Dùng ObjectAnimator để lặp lại)
        android.animation.ObjectAnimator pulse = android.animation.ObjectAnimator.ofPropertyValuesHolder(
            ivIcon,
            android.animation.PropertyValuesHolder.ofFloat("scaleX", 1.2f),
            android.animation.PropertyValuesHolder.ofFloat("scaleY", 1.2f)
        );
        pulse.setDuration(800);
        pulse.setRepeatCount(android.animation.ObjectAnimator.INFINITE);
        pulse.setRepeatMode(android.animation.ObjectAnimator.REVERSE);
        pulse.start();

        btnRestore.setOnClickListener(v -> {
            android.widget.Toast.makeText(context, context.getString(R.string.common_msg_coming_soon), android.widget.Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.setContentView(view);
        dialog.show();
    }

    @Override
    public int getItemCount() {
        return data != null ? data.size() : 0;
    }

    static class RankViewHolder extends RecyclerView.ViewHolder {
        TextView tvPosition, tvName, tvValue;
        ImageView ivAvatar, ivPartnerAvatar;
        com.google.android.material.card.MaterialCardView cvPartnerAvatar;

        public RankViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPosition = itemView.findViewById(R.id.tv_rank_position);
            tvName = itemView.findViewById(R.id.tv_rank_name);
            tvValue = itemView.findViewById(R.id.tv_rank_value);
            ivAvatar = itemView.findViewById(R.id.iv_rank_avatar);
            ivPartnerAvatar = itemView.findViewById(R.id.iv_partner_avatar);
            cvPartnerAvatar = itemView.findViewById(R.id.cv_partner_avatar);
        }
    }
}

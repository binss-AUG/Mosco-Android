package com.vn.jet.mosco.adapter;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import com.airbnb.lottie.LottieAnimationView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.utils.DatabaseLoader;
import com.vn.jet.mosco.utils.SmartFaceCropTransformation;

import org.json.JSONObject;

import java.util.List;

/**
 * Adapter quản lý khối Podium 3D (Top 1, 2, 3) trên đầu danh sách Rank.
 * Tích hợp hiệu ứng trượt và nảy tuần tự (Cascading Animation) giống các game AAA.
 */
public class PodiumAdapter extends RecyclerView.Adapter<PodiumAdapter.PodiumViewHolder> {

    private List<JSONObject> top3List;
    private final String rankType;
    private boolean isAnimated = false; // Chỉ chạy animation 1 lần khi load

    public PodiumAdapter(List<JSONObject> top3List, String rankType) {
        this.top3List = top3List;
        this.rankType = rankType;
    }

    public void updateData(List<JSONObject> newData) {
        this.top3List = newData;
        this.isAnimated = false; // Reset animation when data changes (e.g. switch tab)
        notifyDataSetChanged();
    }

    public void resetAnimation() {
        this.isAnimated = false;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PodiumViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_rank_podium, parent, false);
        return new PodiumViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PodiumViewHolder holder, int position) {
        if (top3List == null || top3List.isEmpty()) {
            holder.itemView.setVisibility(View.GONE);
            return;
        }
        
        holder.itemView.setVisibility(View.VISIBLE);

        // Bind Top 1 (Index 0)
        if (top3List.size() > 0) {
            bindPillar(holder.itemView.getContext(), top3List.get(0), 
                holder.ivAvatarGold, holder.tvNameGold, holder.tvValueGold, holder.ivTypeGold);
            holder.layoutGold.setVisibility(View.VISIBLE);
        } else {
            holder.layoutGold.setVisibility(View.INVISIBLE);
        }

        // Bind Top 2 (Index 1)
        if (top3List.size() > 1) {
            bindPillar(holder.itemView.getContext(), top3List.get(1), 
                holder.ivAvatarSilver, holder.tvNameSilver, holder.tvValueSilver, holder.ivTypeSilver);
            holder.layoutSilver.setVisibility(View.VISIBLE);
        } else {
            holder.layoutSilver.setVisibility(View.INVISIBLE);
        }

        // Bind Top 3 (Index 2)
        if (top3List.size() > 2) {
            bindPillar(holder.itemView.getContext(), top3List.get(2), 
                holder.ivAvatarBronze, holder.tvNameBronze, holder.tvValueBronze, holder.ivTypeBronze);
            holder.layoutBronze.setVisibility(View.VISIBLE);
        } else {
            holder.layoutBronze.setVisibility(View.INVISIBLE);
        }

        // Chạy animation tuần tự (chỉ chạy 1 lần để tránh giật khi cuộn)
        if (!isAnimated) {
            runEntranceAnimation(holder);
            isAnimated = true;
        }
    }

    private void bindPillar(Context context, JSONObject user, ImageView ivAvatar, TextView tvName, TextView tvValue, LottieAnimationView ivType) {
        tvName.setText(user.optString("ingameName", "Unknown"));
        
        int value = user.optInt("value", 0);
        ivType.cancelAnimation();
        
        switch (rankType) {
            case "level": 
                tvValue.setText(context.getString(R.string.rank_format_level, value)); 
                ivType.setVisibility(View.GONE);
                break;
            case "wealth": 
                tvValue.setText(com.vn.jet.mosco.utils.NumberUtils.format(context, (long)value)); 
                ivType.setImageResource(R.drawable.ic_item_diamond);
                ivType.setVisibility(View.VISIBLE);
                break;
            case "collection": 
                tvValue.setText(context.getString(R.string.rank_format_album, value)); 
                ivType.setImageResource(R.drawable.ic_objets);
                ivType.setVisibility(View.VISIBLE);
                break;
            case "streak":
                tvValue.setText(context.getString(R.string.rank_format_streak, value));
                ivType.setAnimation(R.raw.streak_animation);
                ivType.setProgress(0.5f);
                ivType.pauseAnimation();
                ivType.setVisibility(View.VISIBLE);
                break;
        }

        String avatarId = user.optString("avatarId", "1");
        long userId = user.optLong("userId", -1L);
        com.vn.jet.mosco.utils.AvatarUtils.loadAvatar(context, ivAvatar, userId, avatarId);

        // Bridge Bridge: Nhấn avatar mở profile
        ivAvatar.setOnClickListener(v -> {
            if (userId != -1L) {
                com.vn.jet.mosco.utils.NavigationUtils.openProfile((androidx.fragment.app.FragmentActivity) context, userId);
            }
        });
    }

    private void runEntranceAnimation(PodiumViewHolder holder) {
        // Chuẩn bị trạng thái ban đầu: Kéo tất cả xuống dưới và ẩn đi
        prepareViewForAnimation(holder.layoutSilver);
        prepareViewForAnimation(holder.layoutBronze);
        prepareViewForAnimation(holder.layoutGold);
        prepareAvatarForAnimation(holder.flAvatarSilver);
        prepareAvatarForAnimation(holder.flAvatarBronze);
        prepareAvatarForAnimation(holder.flAvatarGold);

        // Sequence: Silver (0ms) -> Bronze (150ms) -> Gold (300ms)
        animatePillar(holder.layoutSilver, holder.flAvatarSilver, 0);
        animatePillar(holder.layoutBronze, holder.flAvatarBronze, 150);
        animatePillar(holder.layoutGold, holder.flAvatarGold, 300);
    }

    private void prepareViewForAnimation(View view) {
        view.animate().cancel();
        view.setTranslationY(200f);
        view.setAlpha(0f);
    }

    private void prepareAvatarForAnimation(View view) {
        view.animate().cancel();
        // Cancel old hover if exists
        Object oldHover = view.getTag();
        if (oldHover instanceof ObjectAnimator) {
            ((ObjectAnimator) oldHover).cancel();
        }
        view.setTag(null);
        
        view.setScaleX(0f);
        view.setScaleY(0f);
        view.setTranslationY(0f);
    }

    private void animatePillar(View pillar, View avatar, long startDelay) {
        pillar.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(400)
            .setStartDelay(startDelay)
            .setInterpolator(new DecelerateInterpolator())
            .withEndAction(() -> {
                // Avatar pop up with bouncy effect
                avatar.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(500)
                    .setInterpolator(new OvershootInterpolator(1.5f))
                    .withEndAction(() -> {
                        // Floating effect (Dập dềnh) after pop-up
                        ObjectAnimator hover = ObjectAnimator.ofFloat(avatar, "translationY", 0f, -15f);
                        hover.setRepeatCount(ObjectAnimator.INFINITE);
                        hover.setRepeatMode(ObjectAnimator.REVERSE);
                        hover.setDuration(1200);
                        hover.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
                        avatar.setTag(hover);
                        hover.start();
                    })
                    .start();
            })
            .start();
    }

    @Override
    public int getItemCount() {
        return 1; // Chỉ render 1 block layout_rank_podium chứa cả 3
    }

    static class PodiumViewHolder extends RecyclerView.ViewHolder {
        View layoutGold, layoutSilver, layoutBronze;
        ImageView ivAvatarGold, ivAvatarSilver, ivAvatarBronze;
        LottieAnimationView ivTypeGold, ivTypeSilver, ivTypeBronze;
        TextView tvNameGold, tvNameSilver, tvNameBronze;
        TextView tvValueGold, tvValueSilver, tvValueBronze;
        View flAvatarGold, flAvatarSilver, flAvatarBronze;

        PodiumViewHolder(View itemView) {
            super(itemView);
            layoutGold = itemView.findViewById(R.id.layout_podium_gold);
            layoutSilver = itemView.findViewById(R.id.layout_podium_silver);
            layoutBronze = itemView.findViewById(R.id.layout_podium_bronze);

            ivAvatarGold = itemView.findViewById(R.id.iv_avatar_gold);
            ivAvatarSilver = itemView.findViewById(R.id.iv_avatar_silver);
            ivAvatarBronze = itemView.findViewById(R.id.iv_avatar_bronze);

            ivTypeGold = itemView.findViewById(R.id.iv_type_gold);
            ivTypeSilver = itemView.findViewById(R.id.iv_type_silver);
            ivTypeBronze = itemView.findViewById(R.id.iv_type_bronze);

            tvNameGold = itemView.findViewById(R.id.tv_name_gold);
            tvNameSilver = itemView.findViewById(R.id.tv_name_silver);
            tvNameBronze = itemView.findViewById(R.id.tv_name_bronze);

            tvValueGold = itemView.findViewById(R.id.tv_value_gold);
            tvValueSilver = itemView.findViewById(R.id.tv_value_silver);
            tvValueBronze = itemView.findViewById(R.id.tv_value_bronze);

            flAvatarGold = itemView.findViewById(R.id.fl_avatar_gold);
            flAvatarSilver = itemView.findViewById(R.id.fl_avatar_silver);
            flAvatarBronze = itemView.findViewById(R.id.fl_avatar_bronze);
        }
    }
}

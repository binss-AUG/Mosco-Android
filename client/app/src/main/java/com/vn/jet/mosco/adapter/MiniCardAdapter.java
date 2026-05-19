package com.vn.jet.mosco.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.model.Objet;
import com.vn.jet.mosco.utils.CardEffectHelper;
import java.util.List;
import java.util.Map;

/**
 * Adapter hiển thị danh sách các card đã mở dưới dạng mini card trong hàng ngang.
 * Phong cách premium: hỗ trợ đầy đủ hiệu ứng (glow, shimmer, float) qua CardEffectHelper.
 */
public class MiniCardAdapter extends RecyclerView.Adapter<MiniCardAdapter.ViewHolder> {

    public interface OnCardClickListener {
        void onCardClick(Map<String, Object> card, int position);
    }

    private final Context context;
    private final List<Map<String, Object>> cards;
    private final OnCardClickListener listener;
    private int selectedPosition = -1;

    public MiniCardAdapter(Context context, List<Map<String, Object>> cards, OnCardClickListener listener) {
        this.context = context;
        this.cards = cards;
        this.listener = listener;
    }

    public void setSelectedPosition(int position) {
        int prev = this.selectedPosition;
        this.selectedPosition = position;
        notifyItemChanged(prev);
        notifyItemChanged(selectedPosition);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_mini_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Map<String, Object> roll = cards.get(position);
        @SuppressWarnings("unchecked")
        Map<String, Object> cardData = (Map<String, Object>) roll.get("cardData");
        if (cardData == null) return;

        String frontImage = String.valueOf(cardData.get("frontImage"));
        Glide.with(context).load(frontImage).into(holder.ivCardImage);

        // Tạo model Objet để gọi CardEffectHelper.apply
        int level = 1;
        if (cardData.get("level") != null) {
            try {
                level = ((Number) cardData.get("level")).intValue();
            } catch (Exception ignored) {}
        }
        int upgradeLevel = 1;
        if (cardData.get("upgradeLevel") != null) {
            try {
                upgradeLevel = ((Number) cardData.get("upgradeLevel")).intValue();
            } catch (Exception ignored) {}
        }

        Objet objet = new Objet(0,
                String.valueOf(cardData.get("collectionId")),
                frontImage,
                level,
                0,
                upgradeLevel);
        objet.setMember(String.valueOf(cardData.get("member")));
        objet.setSeason(String.valueOf(cardData.get("season")));
        objet.setBackgroundColor(String.valueOf(cardData.get("backgroundColor")));
        objet.setTextColor(String.valueOf(cardData.get("textColor")));

        // Áp dụng hiệu ứng premium (glow, shimmer, floating)
        CardEffectHelper.apply(holder.cardItem, holder.viewShimmer, objet, true, true);

        // Hiệu ứng scale nhẹ cho card đang được focus
        if (position == selectedPosition) {
            holder.itemView.setScaleX(1.15f);
            holder.itemView.setScaleY(1.15f);
            holder.itemView.setAlpha(1.0f);
        } else {
            holder.itemView.setScaleX(1.0f);
            holder.itemView.setScaleY(1.0f);
            holder.itemView.setAlpha(0.7f);
        }

        holder.itemView.setOnClickListener(v -> {
            int currentPos = holder.getAdapterPosition();
            if (currentPos != RecyclerView.NO_POSITION && listener != null) {
                listener.onCardClick(roll, currentPos);
            }
        });
    }

    @Override
    public int getItemCount() {
        return cards.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardItem;
        ImageView ivCardImage;
        View viewShimmer;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardItem = itemView.findViewById(R.id.card_item_mini);
            ivCardImage = itemView.findViewById(R.id.iv_card_image_mini);
            viewShimmer = itemView.findViewById(R.id.view_card_shimmer_mini);
        }
    }
}

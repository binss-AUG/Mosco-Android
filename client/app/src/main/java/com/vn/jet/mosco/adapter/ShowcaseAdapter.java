package com.vn.jet.mosco.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.model.Objet;
import com.vn.jet.mosco.utils.DatabaseLoader;
import org.json.JSONObject;
import java.util.List;

/**
 * ShowcaseAdapter — Hiển thị danh sách Objet của user trong Profile Preview.
 */
public class ShowcaseAdapter extends RecyclerView.Adapter<ShowcaseAdapter.ViewHolder> {

    private final List<Objet> objets;

    public ShowcaseAdapter(List<Objet> objets) {
        this.objets = objets;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_card_small_placeholder, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Objet objet = objets.get(position);
        if (objet == null) return;

        JSONObject card = DatabaseLoader.findByCollectionId(holder.itemView.getContext(), objet.getCollectionId());
        if (card != null) {
            String imgUrl = card.optString("frontImage", "");
            Glide.with(holder.itemView.getContext())
                    .load(imgUrl)
                    .placeholder(R.drawable.bg_card_placeholder)
                    .into(holder.ivCard);
        }
    }

    @Override
    public int getItemCount() {
        return objets != null ? objets.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivCard;
        ViewHolder(View itemView) {
            super(itemView);
            ivCard = itemView.findViewById(R.id.iv_card_placeholder);
        }
    }
}

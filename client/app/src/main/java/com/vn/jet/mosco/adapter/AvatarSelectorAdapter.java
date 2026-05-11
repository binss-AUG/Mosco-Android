package com.vn.jet.mosco.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.utils.SmartFaceCropTransformation;
import org.json.JSONObject;
import java.util.List;

public class AvatarSelectorAdapter extends RecyclerView.Adapter<AvatarSelectorAdapter.ViewHolder> {

    private final List<JSONObject> items;
    private final OnAvatarClickListener listener;
    private String selectedId;

    public interface OnAvatarClickListener {
        void onAvatarClick(String collectionId);
    }

    public AvatarSelectorAdapter(List<JSONObject> items, String selectedId, OnAvatarClickListener listener) {
        this.items = items;
        this.selectedId = selectedId;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_avatar_select, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        JSONObject item = items.get(position);
        String collectionId = item.optString("collectionId");
        String imageUrl = item.optString("frontImage");

        // Load image with LeftOffsetCrop
        Glide.with(holder.itemView.getContext())
                .load(imageUrl)
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                .transform(new SmartFaceCropTransformation(imageUrl))
                .into(holder.ivAvatar);

        // Highlight if selected
        holder.overlay.setVisibility(collectionId.equals(selectedId) ? View.VISIBLE : View.GONE);

        holder.itemView.setOnClickListener(v -> {
            selectedId = collectionId;
            notifyDataSetChanged();
            if (listener != null) listener.onAvatarClick(collectionId);
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        View overlay;
        ViewHolder(View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_avatar_item);
            overlay = itemView.findViewById(R.id.v_selection_overlay);
        }
    }
}

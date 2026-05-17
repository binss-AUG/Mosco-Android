package com.vn.jet.mosco.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.vn.jet.mosco.R;
import com.vn.jet.mosco.model.WorldChatMessage;
import com.vn.jet.mosco.utils.AvatarUtils;

import java.util.ArrayList;
import java.util.List;

public class WorldChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_SELF = 1;
    private static final int VIEW_TYPE_OTHER = 2;
    
    private final List<WorldChatMessage> messages = new ArrayList<>();
    private String currentUserId;

    public void setCurrentUserId(String userId) {
        this.currentUserId = userId;
    }

    public void addMessage(WorldChatMessage msg) {
        messages.add(msg);
        if (messages.size() > 50) {
            messages.remove(0);
            notifyItemRemoved(0);
        }
        notifyItemInserted(messages.size() - 1);
    }

    public void clear() {
        messages.clear();
        notifyDataSetChanged();
    }


    @Override
    public int getItemViewType(int position) {
        WorldChatMessage msg = messages.get(position);
        String senderId = msg.getSenderId();
        
        // --- 🕵️ DEBUG LOGGING ---
        android.util.Log.d("MoscoChat", "Check Alignment -> Pos: " + position + " | Me: " + currentUserId + " | Sender: " + senderId);
        
        if (currentUserId != null && senderId != null && currentUserId.trim().equals(senderId.trim())) {
            return VIEW_TYPE_SELF;
        }
        return VIEW_TYPE_OTHER;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_SELF) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_right, parent, false);
            return new SelfViewHolder(v);
        } else {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_left, parent, false);
            return new OtherViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        WorldChatMessage msg = messages.get(position);
        if (holder instanceof SelfViewHolder) {
            SelfViewHolder selfHolder = (SelfViewHolder) holder;
            // Giải mã HTML Entities cho tin nhắn của mình
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                selfHolder.tvContent.setText(android.text.Html.fromHtml(msg.getContent(), android.text.Html.FROM_HTML_MODE_LEGACY));
            } else {
                selfHolder.tvContent.setText(android.text.Html.fromHtml(msg.getContent()));
            }
            AvatarUtils.loadAvatar(selfHolder.itemView.getContext(), selfHolder.ivAvatar, null, msg.getAvatarId());
        } else if (holder instanceof OtherViewHolder) {
            OtherViewHolder otherHolder = (OtherViewHolder) holder;
            otherHolder.tvName.setText(msg.getSenderName());
            // Giải mã HTML Entities cho tin nhắn của người khác
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                otherHolder.tvContent.setText(android.text.Html.fromHtml(msg.getContent(), android.text.Html.FROM_HTML_MODE_LEGACY));
            } else {
                otherHolder.tvContent.setText(android.text.Html.fromHtml(msg.getContent()));
            }
            AvatarUtils.loadAvatar(otherHolder.itemView.getContext(), otherHolder.ivAvatar, null, msg.getAvatarId());
        }
    }

    public WorldChatMessage getMessageAt(int position) {
        if (position >= 0 && position < messages.size()) {
            return messages.get(position);
        }
        return null;
    }

    @Override
    public int getItemCount() { return messages.size(); }

    static class SelfViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvContent;
        public SelfViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_chat_avatar);
            tvContent = itemView.findViewById(R.id.tv_chat_content);
        }
    }

    static class OtherViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvName, tvContent;
        public OtherViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_chat_avatar);
            tvName = itemView.findViewById(R.id.tv_chat_name);
            tvContent = itemView.findViewById(R.id.tv_chat_content);
        }
    }
}

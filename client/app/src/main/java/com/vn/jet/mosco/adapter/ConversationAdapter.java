package com.vn.jet.mosco.adapter;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.vn.jet.mosco.R;
import com.vn.jet.mosco.model.PrivateChatMessage;
import com.vn.jet.mosco.utils.AvatarUtils;
import com.vn.jet.mosco.utils.NavigationUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter hiển thị danh sách hội thoại Inbox chat riêng tư (Direct Messages).
 * Tại sao (WHY): Sử dụng danh sách ConversationWrapper đã được resolve sẵn tên hiển thị và avatar
 * ở luồng nền (Background thread) nhằm giữ cho hàm onBindViewHolder chạy mượt mà ở luồng UI,
 * tránh triệt để việc truy vấn Database gây giật lag (Jank/FPS drop).
 */
public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ViewHolder> {

    private final String myId;
    private List<ConversationWrapper> list = new ArrayList<>();
    private final OnConversationClickListener listener;

    public interface OnConversationClickListener {
        void onConversationClick(ConversationWrapper conversation);
    }

    public static class ConversationWrapper {
        private final PrivateChatMessage lastMessage;
        private final String partnerId;
        private String partnerName;
        private String partnerAvatar;
        private boolean isOnline;

        public ConversationWrapper(PrivateChatMessage lastMessage, String partnerId, String partnerName, String partnerAvatar) {
            this.lastMessage = lastMessage;
            this.partnerId = partnerId;
            this.partnerName = partnerName;
            this.partnerAvatar = partnerAvatar;
        }

        public PrivateChatMessage getLastMessage() { return lastMessage; }
        public String getPartnerId() { return partnerId; }
        public String getPartnerName() { return partnerName; }
        public String getPartnerAvatar() { return partnerAvatar; }
        public boolean isOnline() { return isOnline; }
        
        public void setOnline(boolean online) { this.isOnline = online; }
        public void setPartnerName(String partnerName) { this.partnerName = partnerName; }
        public void setPartnerAvatar(String partnerAvatar) { this.partnerAvatar = partnerAvatar; }
    }

    public ConversationAdapter(String myId, OnConversationClickListener listener) {
        this.myId = myId;
        this.listener = listener;
    }

    public void updateData(List<ConversationWrapper> newList) {
        this.list = new ArrayList<>(newList);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_mailbox_conversation, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ConversationWrapper wrapper = list.get(position);
        PrivateChatMessage msg = wrapper.getLastMessage();

        // Đổ tên đối tác
        String name = wrapper.getPartnerName();
        holder.tvName.setText(name != null ? name : "User " + wrapper.getPartnerId());

        // Trạng thái online glow
        holder.viewOnline.setVisibility(wrapper.isOnline() ? View.VISIBLE : View.GONE);

        // Load Avatar
        long partnerIdLong;
        try {
            partnerIdLong = Long.parseLong(wrapper.getPartnerId());
        } catch (NumberFormatException e) {
            partnerIdLong = -1L;
        }
        AvatarUtils.loadAvatar(holder.itemView.getContext(), holder.ivAvatar, partnerIdLong, wrapper.getPartnerAvatar());

        // Đổ tin nhắn xem trước và thời gian
        if (msg != null) {
            holder.tvPreview.setText(msg.getContent());
            // Định dạng thời gian trôi qua (e.g., "5m ago", "1h ago")
            long now = System.currentTimeMillis();
            CharSequence timeStr = DateUtils.getRelativeTimeSpanString(msg.getTimestamp(), now, DateUtils.MINUTE_IN_MILLIS);
            holder.tvTime.setText(timeStr);
            holder.tvTime.setVisibility(View.VISIBLE);
        } else {
            holder.tvPreview.setText("Chạm để bắt đầu trò chuyện");
            holder.tvTime.setText("");
            holder.tvTime.setVisibility(View.GONE);
        }

        // Click Debounce ngăn chặn click spam liên tục gây crash
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            private long lastClickTime = 0;
            @Override
            public void onClick(View v) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastClickTime > 600) {
                    lastClickTime = currentTime;
                    if (listener != null) {
                        listener.onConversationClick(wrapper);
                    }
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        View viewOnline;
        TextView tvName;
        TextView tvPreview;
        TextView tvTime;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_conversation_avatar);
            viewOnline = itemView.findViewById(R.id.view_online_status);
            tvName = itemView.findViewById(R.id.tv_conversation_partner_name);
            tvPreview = itemView.findViewById(R.id.tv_conversation_preview);
            tvTime = itemView.findViewById(R.id.tv_conversation_time);
        }
    }
}

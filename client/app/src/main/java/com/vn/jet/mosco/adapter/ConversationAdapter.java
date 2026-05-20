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
        private boolean isStranger;
        private int unreadCount;

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
        public boolean isStranger() { return isStranger; }
        public int getUnreadCount() { return unreadCount; }
        
        public void setOnline(boolean online) { this.isOnline = online; }
        public void setPartnerName(String partnerName) { this.partnerName = partnerName; }
        public void setPartnerAvatar(String partnerAvatar) { this.partnerAvatar = partnerAvatar; }
        public void setStranger(boolean stranger) { this.isStranger = stranger; }
        public void setUnreadCount(int unreadCount) { this.unreadCount = unreadCount; }
    }

    public ConversationAdapter(String myId, OnConversationClickListener listener) {
        this.myId = myId;
        this.listener = listener;
    }

    public void updateData(List<ConversationWrapper> newList) {
        if (this.list == null) {
            this.list = new ArrayList<>(newList);
            notifyDataSetChanged();
            return;
        }
        androidx.recyclerview.widget.DiffUtil.DiffResult diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(new androidx.recyclerview.widget.DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return list.size();
            }

            @Override
            public int getNewListSize() {
                return newList.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return java.util.Objects.equals(list.get(oldItemPosition).getPartnerId(), newList.get(newItemPosition).getPartnerId());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                ConversationWrapper oldItem = list.get(oldItemPosition);
                ConversationWrapper newItem = newList.get(newItemPosition);
                return oldItem.isOnline() == newItem.isOnline()
                        && oldItem.isStranger() == newItem.isStranger()
                        && oldItem.getUnreadCount() == newItem.getUnreadCount()
                        && java.util.Objects.equals(oldItem.getPartnerName(), newItem.getPartnerName())
                        && java.util.Objects.equals(oldItem.getPartnerAvatar(), newItem.getPartnerAvatar())
                        && (oldItem.getLastMessage() == null ? newItem.getLastMessage() == null : 
                           (newItem.getLastMessage() != null 
                            && oldItem.getLastMessage().getTimestamp() == newItem.getLastMessage().getTimestamp()
                            && java.util.Objects.equals(oldItem.getLastMessage().getContent(), newItem.getLastMessage().getContent())));
            }
        });
        this.list = new ArrayList<>(newList);
        diffResult.dispatchUpdatesTo(this);
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
        String displayName = (name != null && !name.isEmpty()) ? name : "User";
        if (wrapper.isStranger()) {
            displayName += " • Stranger";
        }
        holder.tvName.setText(displayName);

        // Trạng thái online glow
        if (wrapper.isOnline() && holder.cardAvatar != null) {
            holder.cardAvatar.setStrokeColor(holder.itemView.getContext().getResources().getColor(R.color.brand_primary));
            holder.cardAvatar.setStrokeWidth(4);
            holder.viewOnline.setVisibility(View.VISIBLE);
        } else if (holder.cardAvatar != null) {
            holder.cardAvatar.setStrokeColor(holder.itemView.getContext().getResources().getColor(R.color.mosco_white_20));
            holder.cardAvatar.setStrokeWidth(2);
            holder.viewOnline.setVisibility(View.GONE);
        }

        // Load Avatar
        long partnerIdLong;
        try {
            partnerIdLong = Long.parseLong(wrapper.getPartnerId());
        } catch (NumberFormatException e) {
            partnerIdLong = -1L;
        }
        AvatarUtils.loadAvatar(holder.itemView.getContext(), holder.ivAvatar, partnerIdLong, wrapper.getPartnerAvatar());

        // Đổ tin nhắn xem trước và thời gian — phân biệt ai gửi (giống Messenger)
        if (msg != null) {
            String preview;
            String rawContent = msg.getContent();
            String decodedContent = "";
            if (rawContent != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    decodedContent = android.text.Html.fromHtml(rawContent, android.text.Html.FROM_HTML_MODE_LEGACY).toString();
                } else {
                    decodedContent = android.text.Html.fromHtml(rawContent).toString();
                }
            }
            if (msg.getSenderId() != null && msg.getSenderId().equals(myId)) {
                // Tôi gửi → hiện "You: nội dung"
                preview = holder.itemView.getContext().getString(R.string.chat_preview_you_prefix) + decodedContent;
            } else {
                preview = decodedContent;
            }
            holder.tvPreview.setText(preview);
            long now = System.currentTimeMillis();
            long diff = now - msg.getTimestamp();
            CharSequence timeStr;
            if (diff < 60000) {
                timeStr = "just now";
            } else {
                timeStr = DateUtils.getRelativeTimeSpanString(msg.getTimestamp(), now, DateUtils.MINUTE_IN_MILLIS);
            }
            holder.tvTime.setText(timeStr);
            holder.tvTime.setVisibility(View.VISIBLE);
        } else {
            holder.tvPreview.setText(holder.itemView.getContext().getString(R.string.chat_preview_start));
            holder.tvTime.setText("");
            holder.tvTime.setVisibility(View.GONE);
        }

        // Đổ số lượng tin nhắn chưa đọc (Huy hiệu hình tròn màu vàng hổ phách)
        if (wrapper.getUnreadCount() > 0) {
            holder.tvUnreadBadge.setText(String.valueOf(wrapper.getUnreadCount()));
            holder.tvUnreadBadge.setVisibility(View.VISIBLE);
        } else {
            holder.tvUnreadBadge.setVisibility(View.GONE);
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
        com.google.android.material.card.MaterialCardView cardAvatar;
        View viewOnline;
        TextView tvName;
        TextView tvPreview;
        TextView tvTime;
        TextView tvUnreadBadge;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_conversation_avatar);
            cardAvatar = itemView.findViewById(R.id.card_conversation_avatar);
            viewOnline = itemView.findViewById(R.id.view_online_status);
            tvName = itemView.findViewById(R.id.tv_conversation_partner_name);
            tvPreview = itemView.findViewById(R.id.tv_conversation_preview);
            tvTime = itemView.findViewById(R.id.tv_conversation_time);
            tvUnreadBadge = itemView.findViewById(R.id.tv_conversation_unread_badge);
        }
    }
}

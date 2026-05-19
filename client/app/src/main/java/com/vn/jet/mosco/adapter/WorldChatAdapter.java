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

/**
 * WorldChatAdapter - Adapter dùng chung cho World Chat và Private Chat.
 * Thiết kế phẳng cao cấp (Borderless Flat Premium UI) kèm phân tách ngày tháng (Date Separators).
 */
public class WorldChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_SELF = 1;
    private static final int VIEW_TYPE_OTHER = 2;
    private static final int VIEW_TYPE_DATE_SEPARATOR = 3;
    
    private final List<WorldChatMessage> messages = new ArrayList<>();
    private String currentUserId;
    private boolean isPartnerOnline = false;

    public void setCurrentUserId(String userId) {
        this.currentUserId = userId;
    }

    public void setPartnerOnline(boolean online) {
        if (this.isPartnerOnline != online) {
            this.isPartnerOnline = online;
            notifyDataSetChanged();
        }
    }

    private boolean isDifferentDay(long ts1, long ts2) {
        java.util.Calendar cal1 = java.util.Calendar.getInstance();
        java.util.Calendar cal2 = java.util.Calendar.getInstance();
        cal1.setTimeInMillis(ts1);
        cal2.setTimeInMillis(ts2);
        return cal1.get(java.util.Calendar.YEAR) != cal2.get(java.util.Calendar.YEAR)
                || cal1.get(java.util.Calendar.DAY_OF_YEAR) != cal2.get(java.util.Calendar.DAY_OF_YEAR);
    }

    private String getFormattedDate(long timestamp) {
        java.util.Calendar calMsg = java.util.Calendar.getInstance();
        calMsg.setTimeInMillis(timestamp);
        java.util.Calendar calToday = java.util.Calendar.getInstance();
        
        if (calMsg.get(java.util.Calendar.YEAR) == calToday.get(java.util.Calendar.YEAR)
                && calMsg.get(java.util.Calendar.DAY_OF_YEAR) == calToday.get(java.util.Calendar.DAY_OF_YEAR)) {
            return "Today";
        }
        
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("EEE, MMM dd", java.util.Locale.ENGLISH);
        return sdf.format(new java.util.Date(timestamp));
    }

    public void addMessage(WorldChatMessage msg) {
        // Kiểm tra phân tách ngày tháng trước khi thêm
        WorldChatMessage lastRealMsg = null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (!"DATE_SEPARATOR".equals(messages.get(i).getSenderId())) {
                lastRealMsg = messages.get(i);
                break;
            }
        }

        if (lastRealMsg == null || isDifferentDay(lastRealMsg.getTimestamp(), msg.getTimestamp())) {
            WorldChatMessage dateSep = new WorldChatMessage(
                    "DATE_SEPARATOR",
                    "",
                    "",
                    getFormattedDate(msg.getTimestamp()),
                    msg.getTimestamp()
            );
            messages.add(dateSep);
            notifyItemInserted(messages.size() - 1);
        }

        messages.add(msg);
        if (messages.size() > 100) { // Tăng giới hạn cache mượt mà
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
        if ("DATE_SEPARATOR".equals(msg.getSenderId())) {
            return VIEW_TYPE_DATE_SEPARATOR;
        }
        
        String senderId = msg.getSenderId();
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
        } else if (viewType == VIEW_TYPE_OTHER) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_left, parent, false);
            return new OtherViewHolder(v);
        } else {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_date_separator, parent, false);
            return new DateSeparatorViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        WorldChatMessage msg = messages.get(position);

        if (holder instanceof DateSeparatorViewHolder) {
            DateSeparatorViewHolder dateHolder = (DateSeparatorViewHolder) holder;
            dateHolder.tvDate.setText(msg.getContent());
            return;
        }

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
        String timeStr = sdf.format(new java.util.Date(msg.getTimestamp()));

        if (holder instanceof SelfViewHolder) {
            SelfViewHolder selfHolder = (SelfViewHolder) holder;
            
            // Giải mã HTML Entities tránh hiển thị ký tự mã hóa
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                selfHolder.tvContent.setText(android.text.Html.fromHtml(msg.getContent(), android.text.Html.FROM_HTML_MODE_LEGACY));
            } else {
                selfHolder.tvContent.setText(android.text.Html.fromHtml(msg.getContent()));
            }

            if (selfHolder.tvTime != null) {
                selfHolder.tvTime.setText(timeStr);
            }

            // --- 🔄 ĐỒNG BỘ TRẠNG THÁI TICK DƯỚI GÓC BÊN PHẢI ---
            boolean isLastSelfMessage = true;
            for (int i = position + 1; i < messages.size(); i++) {
                if (currentUserId != null && currentUserId.equals(messages.get(i).getSenderId())) {
                    isLastSelfMessage = false;
                    break;
                }
            }

            if (isLastSelfMessage && selfHolder.ivStatus != null) {
                selfHolder.ivStatus.setVisibility(View.VISIBLE);
                
                // Tìm tin nhắn cuối của đối phương
                long partnerLastTs = 0;
                for (int i = messages.size() - 1; i >= 0; i--) {
                    if (!"DATE_SEPARATOR".equals(messages.get(i).getSenderId()) && !messages.get(i).getSenderId().equals(currentUserId)) {
                        partnerLastTs = messages.get(i).getTimestamp();
                        break;
                    }
                }

                // Quy ước: Đã xem (Seen) nếu đối phương phản hồi sau đó, hoặc DTO ghi nhận trạng thái đã xem (seen = 2)
                boolean isSeen = msg.getStatus() == 2 || (partnerLastTs >= msg.getTimestamp());

                if (isSeen) {
                    selfHolder.ivStatus.setImageResource(R.drawable.ic_chat_tick_seen);
                } else if (isPartnerOnline) {
                    selfHolder.ivStatus.setImageResource(R.drawable.ic_chat_tick_received);
                } else {
                    selfHolder.ivStatus.setImageResource(R.drawable.ic_chat_tick_sent);
                }
            } else if (selfHolder.ivStatus != null) {
                selfHolder.ivStatus.setVisibility(View.GONE);
            }
            
        } else if (holder instanceof OtherViewHolder) {
            OtherViewHolder otherHolder = (OtherViewHolder) holder;
            
            // Ép lineshow & hiển thị tên đối phương (bên góc trên trái)
            otherHolder.tvName.setText(msg.getSenderName());
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                otherHolder.tvContent.setText(android.text.Html.fromHtml(msg.getContent(), android.text.Html.FROM_HTML_MODE_LEGACY));
            } else {
                otherHolder.tvContent.setText(android.text.Html.fromHtml(msg.getContent()));
            }

            if (otherHolder.ivAvatar != null) {
                AvatarUtils.loadAvatar(otherHolder.itemView.getContext(), otherHolder.ivAvatar, null, msg.getAvatarId());
            }

            if (otherHolder.tvTime != null) {
                otherHolder.tvTime.setText(timeStr);
            }
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
        TextView tvContent, tvTime;
        ImageView ivStatus;

        public SelfViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tv_chat_content);
            tvTime = itemView.findViewById(R.id.tv_chat_time);
            ivStatus = itemView.findViewById(R.id.iv_chat_status);
        }
    }

    static class OtherViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvName, tvContent, tvTime;

        public OtherViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_chat_avatar);
            tvName = itemView.findViewById(R.id.tv_chat_name);
            tvContent = itemView.findViewById(R.id.tv_chat_content);
            tvTime = itemView.findViewById(R.id.tv_chat_time);
        }
    }

    static class DateSeparatorViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate;

        public DateSeparatorViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tv_date_separator);
        }
    }
}

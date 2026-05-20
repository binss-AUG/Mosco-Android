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
 * Thiết kế phẳng cao cấp (Borderless Flat Premium UI) kèm phân tách ngày tháng
 * (Date Separators).
 */
public class WorldChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_SELF = 1;
    private static final int VIEW_TYPE_OTHER = 2;
    private static final int VIEW_TYPE_DATE_SEPARATOR = 3;

    // Tại sao (WHY): Payload marker để chỉ cập nhật hình dáng bong bóng (bo góc trên/dưới) khi có tin nhắn mới liền kề,
    // ngăn chặn việc full-rebind làm gián đoạn animation và mất scroll focus.
    private static final String PAYLOAD_BUBBLE = "PAYLOAD_BUBBLE";

    private final List<WorldChatMessage> messages = new ArrayList<>();
    private String currentUserId;
    private boolean isPartnerOnline = false;
    private boolean isPrivateChat = false;
    private int lastAnimatedPosition = -1;

    public void setCurrentUserId(String userId) {
        this.currentUserId = userId;
    }

    public void setPrivateChat(boolean privateChat) {
        this.isPrivateChat = privateChat;
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
                    msg.getTimestamp());
            messages.add(dateSep);
            notifyItemInserted(messages.size() - 1);
        }

        messages.add(msg);
        if (messages.size() > 100) { // Tăng giới hạn cache mượt mà
            messages.remove(0);
            notifyItemRemoved(0);
        }
        int newPos = messages.size() - 1;
        notifyItemInserted(newPos);
        if (newPos >= 1) {
            // Tại sao (WHY): Chỉ dùng partial update (PAYLOAD_BUBBLE) để chỉnh sửa hình dáng góc bo 
            // của tin nhắn liền kề trên, không rebind toàn bộ để tránh giật hình.
            notifyItemChanged(newPos - 1, PAYLOAD_BUBBLE);
        }
    }

    public void clear() {
        messages.clear();
        lastAnimatedPosition = -1;
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
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            boolean handled = false;
            if (payloads.contains(PAYLOAD_BUBBLE)) {
                int adapterPos = holder.getAdapterPosition();
                if (adapterPos >= 0 && adapterPos < messages.size()) {
                    WorldChatMessage msg = messages.get(adapterPos);
                    boolean isConsecutiveAbove = checkConsecutiveAbove(msg, adapterPos);
                    
                    if (holder instanceof SelfViewHolder) {
                        updateSelfBubbleShape((SelfViewHolder) holder, msg, adapterPos, isConsecutiveAbove);
                    } else if (holder instanceof OtherViewHolder) {
                        updateOtherBubbleShape((OtherViewHolder) holder, msg, adapterPos, isConsecutiveAbove);
                    }
                }
                handled = true;
            }
            
            if (handled) return;
        }
        onBindViewHolder(holder, position);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        WorldChatMessage msg = messages.get(position);

        if (holder instanceof DateSeparatorViewHolder) {
            DateSeparatorViewHolder dateHolder = (DateSeparatorViewHolder) holder;
            dateHolder.tvDate.setText(msg.getContent());
            return;
        }

        boolean isConsecutiveAbove = checkConsecutiveAbove(msg, position);

        // --- ĐIỀU CHỈNH KHOẢNG CÁCH DỌC DYNAMIC GIỮA CÁC TIN NHẮN (MARGIN TOP) ---
        if (holder.itemView.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) holder.itemView.getLayoutParams();
            android.content.Context ctx = holder.itemView.getContext();

            // Lấy từ Dimens hệ thống - Tuyệt đối không hardcode
            int consecutiveMargin = ctx.getResources().getDimensionPixelSize(R.dimen.chat_spacing_consecutive);
            int separatedMargin = ctx.getResources().getDimensionPixelSize(R.dimen.chat_spacing_separated);
            int bottomMargin = ctx.getResources().getDimensionPixelSize(R.dimen.chat_spacing_bottom);
            int paddingHorizontal = ctx.getResources().getDimensionPixelSize(R.dimen.spacing_md);

            params.topMargin = isConsecutiveAbove ? consecutiveMargin : separatedMargin;
            params.bottomMargin = bottomMargin;
            holder.itemView.setLayoutParams(params);
        }

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
        String timeStr = sdf.format(new java.util.Date(msg.getTimestamp()));

        if (holder instanceof SelfViewHolder) {
            SelfViewHolder selfHolder = (SelfViewHolder) holder;

            // Giải mã HTML Entities tránh hiển thị ký tự mã hóa
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                selfHolder.tvContent
                        .setText(android.text.Html.fromHtml(msg.getContent(), android.text.Html.FROM_HTML_MODE_LEGACY));
            } else {
                selfHolder.tvContent.setText(android.text.Html.fromHtml(msg.getContent()));
            }

            if (selfHolder.tvTime != null) {
                selfHolder.tvTime.setText(timeStr);
            }

            // --- ĐIỀU CHỈNH GÓC BO BONG BÓNG TỰ THÂN (SELF) ---
            updateSelfBubbleShape(selfHolder, msg, position, isConsecutiveAbove);

        } else if (holder instanceof OtherViewHolder) {
            OtherViewHolder otherHolder = (OtherViewHolder) holder;

            // Ép lineshow & hiển thị tên đối phương (bên góc trên trái)
            if (isPrivateChat) {
                otherHolder.tvName.setVisibility(View.GONE);
            } else {
                otherHolder.tvName.setVisibility(View.VISIBLE);
                otherHolder.tvName.setText(msg.getSenderName());
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                otherHolder.tvContent
                        .setText(android.text.Html.fromHtml(msg.getContent(), android.text.Html.FROM_HTML_MODE_LEGACY));
            } else {
                otherHolder.tvContent.setText(android.text.Html.fromHtml(msg.getContent()));
            }

            if (otherHolder.ivAvatar != null) {
                AvatarUtils.loadAvatar(otherHolder.itemView.getContext(), otherHolder.ivAvatar, null,
                        msg.getAvatarId());
            }

            if (otherHolder.tvTime != null) {
                otherHolder.tvTime.setText(timeStr);
            }

            // --- ĐIỀU CHỈNH GÓC BO BONG BÓNG ĐỐI TÁC (OTHER) ---
            updateOtherBubbleShape(otherHolder, msg, position, isConsecutiveAbove);
        }

        // --- LIGHTWEIGHT HARDWARE-ACCELERATED FLOAT-UP ANIMATION ---
        if (position > lastAnimatedPosition) {
            lastAnimatedPosition = position;
            float density = holder.itemView.getResources().getDisplayMetrics().density;
            holder.itemView.setTranslationY(16f * density);
            holder.itemView.setScaleX(0.96f);
            holder.itemView.setScaleY(0.96f);
            holder.itemView.setAlpha(0.6f);
            holder.itemView.animate()
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(160)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        } else {
            // Tại sao (WHY): RESET hoàn toàn các thuộc tính animation nếu view được tái sử dụng 
            // hoặc update lại. Nếu không reset, View sẽ kẹt ở trạng thái scaleX=0.96f làm bong bóng chat 
            // trông nhỏ hơn, bị mờ, và bị lệch nghiêm trọng về một phía như lỗi đã báo cáo.
            holder.itemView.animate().cancel();
            holder.itemView.setTranslationY(0f);
            holder.itemView.setScaleX(1f);
            holder.itemView.setScaleY(1f);
            holder.itemView.setAlpha(1f);
        }
    }

    private boolean checkConsecutiveAbove(WorldChatMessage msg, int position) {
        if (position > 0) {
            WorldChatMessage prevMsg = messages.get(position - 1);
            if (!"DATE_SEPARATOR".equals(prevMsg.getSenderId()) && prevMsg.getSenderId().equals(msg.getSenderId())) {
                return true;
            }
        }
        return false;
    }

    private void updateSelfBubbleShape(SelfViewHolder selfHolder, WorldChatMessage msg, int position, boolean isConsecutiveAbove) {
        boolean isConsecutiveBelowSelf = false;
        if (position + 1 < messages.size()) {
            WorldChatMessage nextMsg = messages.get(position + 1);
            if (!"DATE_SEPARATOR".equals(nextMsg.getSenderId())
                    && nextMsg.getSenderId().equals(msg.getSenderId())) {
                isConsecutiveBelowSelf = true;
            }
        }
        if (selfHolder.layoutBubbleFrame != null) {
            if (isConsecutiveAbove && isConsecutiveBelowSelf) {
                selfHolder.layoutBubbleFrame.setBackgroundResource(R.drawable.bg_chat_bubble_self_middle);
            } else if (isConsecutiveAbove && !isConsecutiveBelowSelf) {
                selfHolder.layoutBubbleFrame.setBackgroundResource(R.drawable.bg_chat_bubble_self_bottom);
            } else if (!isConsecutiveAbove && isConsecutiveBelowSelf) {
                selfHolder.layoutBubbleFrame.setBackgroundResource(R.drawable.bg_chat_bubble_self_top);
            } else {
                selfHolder.layoutBubbleFrame.setBackgroundResource(R.drawable.bg_chat_bubble_self);
            }
        }
    }

    private void updateOtherBubbleShape(OtherViewHolder otherHolder, WorldChatMessage msg, int position, boolean isConsecutiveAbove) {
        boolean isConsecutiveBelowOther = false;
        if (position + 1 < messages.size()) {
            WorldChatMessage nextMsg = messages.get(position + 1);
            if (!"DATE_SEPARATOR".equals(nextMsg.getSenderId())
                    && nextMsg.getSenderId().equals(msg.getSenderId())) {
                isConsecutiveBelowOther = true;
            }
        }
        if (otherHolder.layoutBubbleFrame != null) {
            if (isConsecutiveAbove && isConsecutiveBelowOther) {
                otherHolder.layoutBubbleFrame.setBackgroundResource(R.drawable.bg_chat_bubble_other_middle);
            } else if (isConsecutiveAbove && !isConsecutiveBelowOther) {
                otherHolder.layoutBubbleFrame.setBackgroundResource(R.drawable.bg_chat_bubble_other_bottom);
            } else if (!isConsecutiveAbove && isConsecutiveBelowOther) {
                otherHolder.layoutBubbleFrame.setBackgroundResource(R.drawable.bg_chat_bubble_other_top);
            } else {
                otherHolder.layoutBubbleFrame.setBackgroundResource(R.drawable.bg_chat_bubble_other);
            }
        }
        if (otherHolder.cardAvatar != null) {
            if (isConsecutiveBelowOther) {
                otherHolder.cardAvatar.setVisibility(View.INVISIBLE); 
            } else {
                otherHolder.cardAvatar.setVisibility(View.VISIBLE);
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
    public int getItemCount() {
        return messages.size();
    }

    static class SelfViewHolder extends RecyclerView.ViewHolder {
        TextView tvContent, tvTime;
        View layoutBubbleFrame;

        public SelfViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tv_chat_content);
            tvTime = itemView.findViewById(R.id.tv_chat_time);
            layoutBubbleFrame = itemView.findViewById(R.id.layout_bubble_frame);
        }
    }

    static class OtherViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvName, tvContent, tvTime;
        View layoutBubbleFrame;
        View cardAvatar;

        public OtherViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_chat_avatar);
            tvName = itemView.findViewById(R.id.tv_chat_name);
            tvContent = itemView.findViewById(R.id.tv_chat_content);
            tvTime = itemView.findViewById(R.id.tv_chat_time);
            layoutBubbleFrame = itemView.findViewById(R.id.layout_bubble_frame);
            cardAvatar = itemView.findViewById(R.id.card_chat_avatar);
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

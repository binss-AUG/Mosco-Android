package com.vn.jet.mosco.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.model.AiChatMessage;
import com.vn.jet.mosco.utils.AvatarUtils;

import java.util.List;

public class AiChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_SELF = 1;
    private static final int VIEW_TYPE_OTHER = 2;
    private static final int VIEW_TYPE_THINKING = 3;

    public static final String PAYLOAD_BUBBLE = "PAYLOAD_BUBBLE";

    private final List<AiChatMessage> messages;
    private String avatarUrl = null;
    private int lastAnimatedPosition = -1;

    public AiChatAdapter(List<AiChatMessage> messages) {
        this.messages = messages;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        AiChatMessage msg = messages.get(position);
        if (msg.isThinking) return VIEW_TYPE_THINKING;
        return msg.isFromAi ? VIEW_TYPE_OTHER : VIEW_TYPE_SELF;
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
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_thinking, parent, false);
            return new ThinkingViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.contains(PAYLOAD_BUBBLE)) {
            AiChatMessage msg = messages.get(position);
            boolean isConsecutiveAbove = checkConsecutiveAbove(msg, position);
            
            if (holder.itemView.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) holder.itemView.getLayoutParams();
                int consecutiveMargin = holder.itemView.getContext().getResources().getDimensionPixelSize(R.dimen.chat_spacing_consecutive);
                int separatedMargin = holder.itemView.getContext().getResources().getDimensionPixelSize(R.dimen.chat_spacing_separated);
                int newTopMargin = isConsecutiveAbove ? consecutiveMargin : separatedMargin;
                if (params.topMargin != newTopMargin) {
                    params.topMargin = newTopMargin;
                    holder.itemView.setLayoutParams(params);
                }
            }
            
            if (holder instanceof SelfViewHolder) {
                SelfViewHolder selfHolder = (SelfViewHolder) holder;
                if (msg.message != null) {
                    io.noties.markwon.Markwon.create(selfHolder.itemView.getContext()).setMarkdown(selfHolder.tvContent, msg.message);
                }
                updateSelfBubbleShape(selfHolder, msg, position, isConsecutiveAbove);
            } else if (holder instanceof OtherViewHolder) {
                OtherViewHolder otherHolder = (OtherViewHolder) holder;
                if (msg.message != null) {
                    io.noties.markwon.Markwon.create(otherHolder.itemView.getContext()).setMarkdown(otherHolder.tvContent, msg.message);
                }
                updateOtherBubbleShape(otherHolder, msg, position, isConsecutiveAbove);
            }
            return;
        }
        onBindViewHolder(holder, position);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        AiChatMessage msg = messages.get(position);

        if (holder instanceof ThinkingViewHolder) {
            ThinkingViewHolder thinkingHolder = (ThinkingViewHolder) holder;
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                com.bumptech.glide.Glide.with(holder.itemView.getContext())
                        .load(avatarUrl)
                        .apply(com.bumptech.glide.request.RequestOptions.bitmapTransform(new com.vn.jet.mosco.utils.SmartFaceCropTransformation(avatarUrl)))
                        .placeholder(R.drawable.ic_star_twinkle)
                        .into(thinkingHolder.ivAvatar);
            } else {
                thinkingHolder.ivAvatar.setImageResource(R.drawable.ic_star_twinkle);
            }
            return;
        }

        boolean isConsecutiveAbove = checkConsecutiveAbove(msg, position);

        if (holder.itemView.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) holder.itemView.getLayoutParams();
            int consecutiveMargin = holder.itemView.getContext().getResources().getDimensionPixelSize(R.dimen.chat_spacing_consecutive);
            int separatedMargin = holder.itemView.getContext().getResources().getDimensionPixelSize(R.dimen.chat_spacing_separated);
            int bottomMargin = holder.itemView.getContext().getResources().getDimensionPixelSize(R.dimen.chat_spacing_bottom);

            params.topMargin = isConsecutiveAbove ? consecutiveMargin : separatedMargin;
            params.bottomMargin = bottomMargin;
            holder.itemView.setLayoutParams(params);
        }

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
        String timeStr = sdf.format(new java.util.Date(msg.timestamp));

        if (holder instanceof SelfViewHolder) {
            SelfViewHolder selfHolder = (SelfViewHolder) holder;

            if (msg.message != null) {
                io.noties.markwon.Markwon.create(selfHolder.itemView.getContext()).setMarkdown(selfHolder.tvContent, msg.message);
            }

            if (selfHolder.tvTime != null) {
                selfHolder.tvTime.setText(timeStr);
            }

            updateSelfBubbleShape(selfHolder, msg, position, isConsecutiveAbove);

        } else if (holder instanceof OtherViewHolder) {
            OtherViewHolder otherHolder = (OtherViewHolder) holder;

            otherHolder.tvName.setVisibility(View.GONE); // Private chat style

            if (msg.message != null) {
                io.noties.markwon.Markwon.create(otherHolder.itemView.getContext()).setMarkdown(otherHolder.tvContent, msg.message);
            }

            if (avatarUrl != null && !avatarUrl.isEmpty() && otherHolder.ivAvatar != null) {
                com.bumptech.glide.Glide.with(holder.itemView.getContext())
                        .load(avatarUrl)
                        .apply(com.bumptech.glide.request.RequestOptions.bitmapTransform(new com.vn.jet.mosco.utils.SmartFaceCropTransformation(avatarUrl)))
                        .placeholder(R.drawable.ic_star_twinkle)
                        .into(otherHolder.ivAvatar);
            } else if (otherHolder.ivAvatar != null) {
                otherHolder.ivAvatar.setImageResource(R.drawable.ic_star_twinkle);
            }

            if (otherHolder.tvTime != null) {
                otherHolder.tvTime.setText(timeStr);
            }

            updateOtherBubbleShape(otherHolder, msg, position, isConsecutiveAbove);
        }

        if (position > lastAnimatedPosition) {
            lastAnimatedPosition = position;
            holder.itemView.setPivotY(0f);
            holder.itemView.setScaleX(0.96f);
            holder.itemView.setScaleY(0.96f);
            holder.itemView.setAlpha(0.6f);
            holder.itemView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(160)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        } else {
            holder.itemView.animate().cancel();
            holder.itemView.setScaleX(1f);
            holder.itemView.setScaleY(1f);
            holder.itemView.setAlpha(1f);
        }

        // Add long click to copy
        holder.itemView.setOnLongClickListener(v -> {
            if (msg.message != null && !msg.message.isEmpty()) {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) holder.itemView.getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("Mosco Chat", msg.message);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    android.widget.Toast.makeText(holder.itemView.getContext(), "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show();
                }
            }
            return true;
        });
    }

    private boolean checkConsecutiveAbove(AiChatMessage msg, int position) {
        if (position > 0) {
            AiChatMessage prevMsg = messages.get(position - 1);
            if (prevMsg.isFromAi == msg.isFromAi && !prevMsg.isThinking && !msg.isThinking) {
                return true;
            }
        }
        return false;
    }

    private void updateSelfBubbleShape(SelfViewHolder selfHolder, AiChatMessage msg, int position, boolean isConsecutiveAbove) {
        boolean isConsecutiveBelowSelf = false;
        if (position + 1 < messages.size()) {
            AiChatMessage nextMsg = messages.get(position + 1);
            if (!nextMsg.isThinking && nextMsg.isFromAi == msg.isFromAi) {
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

    private void updateOtherBubbleShape(OtherViewHolder otherHolder, AiChatMessage msg, int position, boolean isConsecutiveAbove) {
        boolean isConsecutiveBelowOther = false;
        if (position + 1 < messages.size()) {
            AiChatMessage nextMsg = messages.get(position + 1);
            if (!nextMsg.isThinking && nextMsg.isFromAi == msg.isFromAi) {
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

    public void addMessage(AiChatMessage msg) {
        messages.add(msg);
        int newPos = messages.size() - 1;
        notifyItemInserted(newPos);
        if (newPos >= 1) {
            notifyItemChanged(newPos - 1, PAYLOAD_BUBBLE);
        }
    }

    public void removeThinkingMessage() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).isThinking) {
                messages.remove(i);
                notifyItemRemoved(i);
                if (i >= 1) {
                    notifyItemChanged(i - 1, PAYLOAD_BUBBLE);
                }
                break;
            }
        }
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

    static class ThinkingViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;

        public ThinkingViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_chat_avatar);
        }
    }
}

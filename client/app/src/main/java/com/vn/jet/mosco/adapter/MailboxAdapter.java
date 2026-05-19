package com.vn.jet.mosco.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.vn.jet.mosco.R;
import com.vn.jet.mosco.model.UserMail;
import com.vn.jet.mosco.utils.NumberUtils;

import java.util.List;

/**
 * Bộ chuyển đổi dữ liệu (Adapter) cho danh sách Thư hệ thống.
 * Lý do (WHY): Tách biệt độc lập khỏi màn hình Collection để dễ bảo trì và tối ưu hiệu năng tái sử dụng.
 */
public class MailboxAdapter extends RecyclerView.Adapter<MailboxAdapter.ViewHolder> {
    private List<UserMail> list;
    private final OnMailClickListener listener;

    public interface OnMailClickListener {
        void onMailClick(UserMail mail);
    }

    public MailboxAdapter(List<UserMail> list, OnMailClickListener listener) {
        this.list = list;
        this.listener = listener;
    }

    public void updateData(List<UserMail> newList) {
        this.list = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(
                LayoutInflater.from(parent.getContext()).inflate(R.layout.item_mailbox, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        UserMail mail = list.get(position);
        holder.tvTitle.setText(mail.getTitle());
        holder.tvQty.setText(mail.getQuantity() != null
                ? "x" + NumberUtils.format(holder.itemView.getContext(), mail.getQuantity())
                : "");
        holder.tvDesc.setText(mail.getContent());

        // Định dạng thời gian rút gọn
        holder.tvTime.setText(mail.getCreatedAt() != null ? mail.getCreatedAt().substring(0, 10) : "");

        holder.ivIcon.setImageResource(R.drawable.item_shop_demo);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null)
                listener.onMailClick(mail);
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ImageView ivIcon;
        public TextView tvTitle, tvQty, tvDesc, tvTime;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_mail_item);
            tvTitle = itemView.findViewById(R.id.tv_mail_title);
            tvQty = itemView.findViewById(R.id.tv_mail_qty);
            tvDesc = itemView.findViewById(R.id.tv_mail_desc);
            tvTime = itemView.findViewById(R.id.tv_mail_time);
        }
    }
}

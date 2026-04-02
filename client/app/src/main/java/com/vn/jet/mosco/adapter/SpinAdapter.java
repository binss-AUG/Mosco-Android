package com.vn.jet.mosco.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.vn.jet.mosco.R;
// Nhớ import R của package bạn vào đây

public class SpinAdapter extends RecyclerView.Adapter<SpinAdapter.SpinViewHolder> {

    // Trả về 3 để mô phỏng 3 thẻ (trái, giữa, phải)
    @Override
    public int getItemCount() {
        return 3;
    }

    @NonNull
    @Override
    public SpinViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_spin_card, parent, false);
        return new SpinViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SpinViewHolder holder, int position) {
        // Tạm thời chưa cần gán dữ liệu gì, chỉ hiển thị UI
    }

    static class SpinViewHolder extends RecyclerView.ViewHolder {
        SpinViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}

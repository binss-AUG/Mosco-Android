package com.vn.jet.mosco.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.model.Objet;

import java.util.ArrayList;
import java.util.List;

public class InventoryBottomSheet extends BottomSheetDialogFragment {

    private OnObjetSelectedListener listener;

    public interface OnObjetSelectedListener {
        void onObjetSelected(Objet objet);
    }

    public void setOnObjetSelectedListener(OnObjetSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Thiết lập style để BottomSheet full màn hình hoặc có background trong suốt nếu cần
        setStyle(STYLE_NORMAL, R.style.CustomBottomSheetDialogTheme);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.layout_inventory_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ImageView ivBack = view.findViewById(R.id.iv_back);
        RecyclerView rvInventory = view.findViewById(R.id.rv_inventory);
        LinearLayout layoutEmptyState = view.findViewById(R.id.layout_empty_state);

        ivBack.setOnClickListener(v -> dismiss());

        rvInventory.setLayoutManager(new GridLayoutManager(getContext(), 3));

        // Dữ liệu mẫu
        List<Objet> dummyList = new ArrayList<>();
        String sampleUrl = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcR_M6z4vU1sV3L2V3N6D2jG1H7M3x-wK8N9vA&s";
        for (int i = 0; i < 15; i++) {
            dummyList.add(new Objet(i, sampleUrl));
        }

        // Kiểm tra hiển thị Empty State
        if (dummyList.isEmpty()) {
            rvInventory.setVisibility(View.GONE);
            layoutEmptyState.setVisibility(View.VISIBLE);
        } else {
            rvInventory.setVisibility(View.VISIBLE);
            layoutEmptyState.setVisibility(View.GONE);
            InventoryAdapter adapter = new InventoryAdapter(dummyList);
            rvInventory.setAdapter(adapter);
        }
    }

    private class InventoryAdapter extends RecyclerView.Adapter<InventoryAdapter.ViewHolder> {
        private List<Objet> objetList;

        public InventoryAdapter(List<Objet> objetList) {
            this.objetList = objetList;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_inventory_card, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Objet objet = objetList.get(position);
            Glide.with(holder.itemView.getContext())
                    .load(objet.getImageUrl())
                    .into(holder.ivObjet);

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onObjetSelected(objet);
                }
                dismiss();
            });
        }

        @Override
        public int getItemCount() {
            return objetList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivObjet;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                ivObjet = itemView.findViewById(R.id.iv_objet_image);
            }
        }
    }
}

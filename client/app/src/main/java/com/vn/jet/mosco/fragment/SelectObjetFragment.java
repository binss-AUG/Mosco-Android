package com.vn.jet.mosco.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.model.Objet;

import java.util.ArrayList;
import java.util.List;

public class SelectObjetFragment extends Fragment {

    private RecyclerView rvInventory;
    private LinearLayout layoutEmptyState;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.layout_inventory_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvInventory = view.findViewById(R.id.rv_inventory);
        layoutEmptyState = view.findViewById(R.id.layout_empty_state);
        ImageView ivBack = view.findViewById(R.id.iv_back);

        ivBack.setOnClickListener(v -> getParentFragmentManager().popBackStack());

        setupRecyclerView();
    }

    private void setupRecyclerView() {
        rvInventory.setLayoutManager(new GridLayoutManager(getContext(), 3));

        // Sử dụng URL ảnh demo bạn đã cung cấp (SeoYeon tripleS)
        String demoImageUrl = "https://imagedelivery.net/qQuMkbHJ-0s6rwu8vup_5w/d6db7447-13f9-4572-b299-7d9ba8be9e00/original"; // URL đại diện cho ảnh demo
        
        List<Objet> dummyList = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            // Trong thực tế bạn sẽ truyền URL thật từ ảnh bạn vừa gửi
            dummyList.add(new Objet(i, "https://imagedelivery.net/qQuMkbHJ-0s6rwu8vup_5w/d6db7447-13f9-4572-b299-7d9ba8be9e00/original"));
        }

        // Nếu bạn đã có file ảnh trong drawable, hãy dùng resource ID hoặc URL trực tiếp
        // Ở đây tôi giả định bạn sẽ load từ một URL demo tương tự ảnh bạn gửi
        
        InventoryAdapter adapter = new InventoryAdapter(dummyList);
        rvInventory.setAdapter(adapter);
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
            
            // Load ảnh demo (giống ảnh bạn gửi) vào card
            Glide.with(holder.itemView.getContext())
                    .load("https://imagedelivery.net/qQuMkbHJ-0s6rwu8vup_5w/d6db7447-13f9-4572-b299-7d9ba8be9e00/original") // Link demo ảnh SeoYeon bạn gửi
                    .placeholder(R.drawable.objet_back_spin)
                    .into(holder.ivObjet);

            holder.itemView.setOnClickListener(v -> {
                Bundle result = new Bundle();
                result.putString("selected_objet_url", "https://imagedelivery.net/qQuMkbHJ-0s6rwu8vup_5w/d6db7447-13f9-4572-b299-7d9ba8be9e00/original");
                getParentFragmentManager().setFragmentResult("objet_selection", result);
                getParentFragmentManager().popBackStack();
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

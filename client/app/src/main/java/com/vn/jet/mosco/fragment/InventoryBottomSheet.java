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

        // View Recycling Optimization: Tái sử dụng view để giải phóng bộ nhớ (RAM)
        rvInventory.setHasFixedSize(true);
        rvInventory.setItemViewCacheSize(20);
        rvInventory.setLayoutManager(new GridLayoutManager(getContext(), 3));

        com.airbnb.lottie.LottieAnimationView loaderLottie = view.findViewById(R.id.loader_lottie);

        loadRealInventory(rvInventory, layoutEmptyState, loaderLottie);
    }

    private void loadRealInventory(RecyclerView rvInventory, View layoutEmptyState, com.airbnb.lottie.LottieAnimationView loaderLottie) {
        if (getContext() == null) return;

        // Khởi tạo Adapter 1 lần duy nhất
        com.vn.jet.mosco.adapter.BaseInventoryAdapter rvAdapter = new com.vn.jet.mosco.adapter.BaseInventoryAdapter(new ArrayList<>(), rvInventory, item -> {
            com.vn.jet.mosco.utils.ObjetDetailBinder.showObjetDetail(requireContext(), item);
        });
        rvInventory.setAdapter(rvAdapter);

        // BƯỚC 1: Hiển thị từ Cache ngay lập tức (nếu có)
        if (com.vn.jet.mosco.utils.DatabaseLoader.cachedUserInventory != null && !com.vn.jet.mosco.utils.DatabaseLoader.cachedUserInventory.isEmpty()) {
            List<Objet> realObjets = new ArrayList<>();
            for (com.vn.jet.mosco.utils.DatabaseLoader.UserInventoryItem item : com.vn.jet.mosco.utils.DatabaseLoader.cachedUserInventory) {
                realObjets.add(new Objet(item.id.intValue(), item.collectionId, item.frontImage, item.level, item.exp, item.upgradeLevel));
            }
            layoutEmptyState.setVisibility(View.GONE);
            rvInventory.setVisibility(View.VISIBLE);
            loaderLottie.setVisibility(View.GONE);
            rvAdapter.updateData(realObjets);
        } else {
            // Không có cache → hiện loading
            loaderLottie.setVisibility(View.VISIBLE);
            loaderLottie.playAnimation();
            rvInventory.setVisibility(View.GONE);
            layoutEmptyState.setVisibility(View.GONE);
        }

        // BƯỚC 2: LUÔN gọi API ngầm để đồng bộ data mới nhất từ Server
        Long userId = new com.vn.jet.mosco.utils.SessionManager(requireContext()).getUserId();
        if (userId == null) return;

        com.vn.jet.mosco.network.GameApiService apiService = com.vn.jet.mosco.network.ApiClient.getClient(requireContext()).create(com.vn.jet.mosco.network.GameApiService.class);
        apiService.getUserCards(userId).enqueue(new retrofit2.Callback<List<com.vn.jet.mosco.model.UserCard>>() {
            @Override
            public void onResponse(retrofit2.Call<List<com.vn.jet.mosco.model.UserCard>> call, retrofit2.Response<List<com.vn.jet.mosco.model.UserCard>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    List<com.vn.jet.mosco.model.UserCard> responseCards = response.body();
                    
                    // Map data in background to prevent freezing UI
                    new Thread(() -> {
                        List<Objet> realObjets = new ArrayList<>();
                        java.util.List<com.vn.jet.mosco.utils.DatabaseLoader.UserInventoryItem> cachedList = new java.util.ArrayList<>();
                        for (com.vn.jet.mosco.model.UserCard userCard : responseCards) {
                            org.json.JSONObject cardJson = com.vn.jet.mosco.utils.DatabaseLoader.findById(requireContext(), userCard.getCollectionId());
                            if (cardJson != null) {
                                String img = cardJson.optString("frontImage", "");
                                Objet obj = new Objet(userCard.getId().intValue(), userCard.getCollectionId(), img, userCard.getLevel(), userCard.getExp(), userCard.getUpgradeLevel());
                                // OVR trực tiếp từ Server (Server Truth)
                                int ovr = userCard.getOvr();
                                obj.setOvr(ovr);
                                realObjets.add(obj);

                                cachedList.add(new com.vn.jet.mosco.utils.DatabaseLoader.UserInventoryItem(userCard.getId(), userCard.getCollectionId(), img, userCard.getLevel(), userCard.getExp(), userCard.getUpgradeLevel(), ovr));
                            }
                        }
                        // Ghi lại cache dự phòng nếu API vừa phải gọi
                        com.vn.jet.mosco.utils.DatabaseLoader.cachedUserInventory = cachedList;

                        if (getActivity() != null && isAdded()) {
                            getActivity().runOnUiThread(() -> {
                                loaderLottie.cancelAnimation();
                                loaderLottie.setVisibility(View.GONE);
                                if (realObjets.isEmpty()) {
                                    rvInventory.setVisibility(View.GONE);
                                    layoutEmptyState.setVisibility(View.VISIBLE);
                                } else {
                                    rvInventory.setVisibility(View.VISIBLE);
                                    layoutEmptyState.setVisibility(View.GONE);
                                    // Sử dụng updateData() để cập nhật adapter đã khởi tạo
                                    if (rvInventory.getAdapter() instanceof com.vn.jet.mosco.adapter.BaseInventoryAdapter) {
                                        ((com.vn.jet.mosco.adapter.BaseInventoryAdapter) rvInventory.getAdapter()).updateData(realObjets);
                                    }
                                }
                            });
                        }
                    }).start();
                } else {
                    loaderLottie.cancelAnimation();
                    loaderLottie.setVisibility(View.GONE);
                    rvInventory.setVisibility(View.GONE);
                    layoutEmptyState.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<List<com.vn.jet.mosco.model.UserCard>> call, Throwable t) {
                loaderLottie.cancelAnimation();
                loaderLottie.setVisibility(View.GONE);
                rvInventory.setVisibility(View.GONE);
                layoutEmptyState.setVisibility(View.VISIBLE);
            }
        });
    }

    // --- Inner Classes InventoryAdapter Deprecated ---
}

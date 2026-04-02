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

import com.vn.jet.mosco.R;
import com.vn.jet.mosco.model.Objet;
import com.vn.jet.mosco.model.UserCard;
import com.vn.jet.mosco.network.ApiClient;
import com.vn.jet.mosco.network.GameApiService;
import com.vn.jet.mosco.utils.DatabaseLoader;
import com.vn.jet.mosco.utils.SessionManager;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class SelectObjetFragment extends Fragment {

    private RecyclerView rvInventory;
    private LinearLayout layoutEmptyState;
    private com.airbnb.lottie.LottieAnimationView loaderLottie;
    private android.widget.TextView tvCount;
    
    private final java.util.Set<String> objetFilter = new java.util.LinkedHashSet<>();
    private final String[] SORT_OPTIONS = {"Newest", "Oldest", "Lowest No.", "Highest No."};
    private List<Objet> originalObjets = new ArrayList<>();

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
        loaderLottie = view.findViewById(R.id.loader_lottie);
        ImageView ivBack = view.findViewById(R.id.iv_back);

        ivBack.setOnClickListener(v -> getParentFragmentManager().popBackStack());

        tvCount = view.findViewById(R.id.tv_select_types_count);

        // Filter button
        View filterBtn = view.findViewById(R.id.btn_filter_select);
        if (filterBtn != null) {
            filterBtn.setOnClickListener(v ->
                CollectionFragment.showFilterBottomSheet(this, CollectionFragment.buildObjetCategories(requireContext()), 0, objetFilter, this::applyFilters));
        }

        // Sort button
        View sortBtn = view.findViewById(R.id.btn_sort_select);
        android.widget.LinearLayout dropdown = view.findViewById(R.id.dropdown_sort_select);
        if (sortBtn != null && dropdown != null) {
            CollectionFragment.setupSortDropdown(sortBtn, null, null, SORT_OPTIONS, dropdown, this::applyFilters);
        }

        setupRecyclerView();
    }

    private void setupRecyclerView() {
        // Tối ưu RecyclerView cho Fragment replace (tránh lag khi xây dựng View mới)
        rvInventory.setHasFixedSize(true);
        rvInventory.setItemViewCacheSize(20);
        // Tắt nested scrolling vì không nằm trong CoordinatorLayout
        rvInventory.setNestedScrollingEnabled(false);
        rvInventory.setLayoutManager(new GridLayoutManager(getContext(), 3));
        loadUserCards();
    }

    private void loadUserCards() {
        Long userId = new SessionManager(requireContext()).getUserId();
        if (userId == null) return;

        // 🌟 CHIẾN THUẬT SIÊU NHANH - CẮT API:
        if (DatabaseLoader.cachedUserInventory != null) {
            List<Objet> displayItems = new ArrayList<>();
            for (DatabaseLoader.UserInventoryItem item : DatabaseLoader.cachedUserInventory) {
                displayItems.add(new Objet(item.id.intValue(), item.collectionId, item.frontImage, item.level, item.exp, item.upgradeLevel));
            }
            if (displayItems.isEmpty()) {
                loaderLottie.setVisibility(View.GONE);
                layoutEmptyState.setVisibility(View.VISIBLE);
                rvInventory.setVisibility(View.GONE);
            } else {
                loaderLottie.setVisibility(View.GONE);
                layoutEmptyState.setVisibility(View.GONE);
                rvInventory.setVisibility(View.VISIBLE);
                
                originalObjets = displayItems;
                
                // 🏆 Sử dụng Shared CORE Cùng Click Listener chuyên biệt cho Chọn Thẻ
                com.vn.jet.mosco.adapter.BaseInventoryAdapter newAdapter = new com.vn.jet.mosco.adapter.BaseInventoryAdapter(new ArrayList<>(), rvInventory, item -> {
                    Bundle result = new Bundle();
                    result.putString("selected_objet_id", String.valueOf(item.getId()));
                    result.putString("selected_objet_url", item.getImageUrl());
                    getParentFragmentManager().setFragmentResult("objet_selection", result);
                    getParentFragmentManager().popBackStack();
                });
                rvInventory.setAdapter(newAdapter);
                applyFilters();
            }
            return; // ĐÃ LOAD TỪ BỘ NHỚ, DỪNG GỌI MẠNG!
        }

        // Bật Lottie Animation đang tải ngầm khi phải chờ API
        loaderLottie.setVisibility(View.VISIBLE);
        loaderLottie.playAnimation();
        rvInventory.setVisibility(View.GONE);
        layoutEmptyState.setVisibility(View.GONE);

        GameApiService apiService = ApiClient.getClient(requireContext()).create(GameApiService.class);
        apiService.getUserCards(userId).enqueue(new retrofit2.Callback<List<UserCard>>() {
            @Override
            public void onResponse(retrofit2.Call<List<UserCard>> call, retrofit2.Response<List<UserCard>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<UserCard> cards = response.body();
                    
                    if (cards.isEmpty()) {
                        loaderLottie.cancelAnimation();
                        loaderLottie.setVisibility(View.GONE);
                        layoutEmptyState.setVisibility(View.VISIBLE);
                        rvInventory.setVisibility(View.GONE);
                        return;
                    }

                    // Map UserCard -> FrontImage (via DatabaseLoader) in background
                    new Thread(() -> {
                        List<Objet> displayItems = new ArrayList<>();
                        List<DatabaseLoader.UserInventoryItem> cachedList = new ArrayList<>();
                        for (UserCard card : cards) {
                            JSONObject metadata = DatabaseLoader.findById(requireContext(), card.getCollectionId());
                            if (metadata != null) {
                                String frontImage = metadata.optString("frontImage", "");
                                displayItems.add(new Objet(card.getId().intValue(), card.getCollectionId(), frontImage, card.getLevel(), card.getExp(), card.getUpgradeLevel()));
                                cachedList.add(new DatabaseLoader.UserInventoryItem(card.getId(), card.getCollectionId(), frontImage, card.getLevel(), card.getExp(), card.getUpgradeLevel()));
                            }
                        }
                        // Ghi lại vào Cache
                        DatabaseLoader.cachedUserInventory = cachedList;

                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                // Tắt Lottie và hiện danh sách
                                loaderLottie.cancelAnimation();
                                loaderLottie.setVisibility(View.GONE);
                                layoutEmptyState.setVisibility(View.GONE);
                                rvInventory.setVisibility(View.VISIBLE);
                                
                                originalObjets = displayItems;
                                
                                // 🏆 Sử dụng Shared CORE
                                com.vn.jet.mosco.adapter.BaseInventoryAdapter newAdapter = new com.vn.jet.mosco.adapter.BaseInventoryAdapter(new ArrayList<>(), rvInventory, item -> {
                                    Bundle result = new Bundle();
                                    result.putString("selected_objet_id", String.valueOf(item.getId()));
                                    result.putString("selected_objet_url", item.getImageUrl());
                                    getParentFragmentManager().setFragmentResult("objet_selection", result);
                                    getParentFragmentManager().popBackStack();
                                });
                                rvInventory.setAdapter(newAdapter);
                                applyFilters();
                            });
                        }
                    }).start();
                } else {
                    loaderLottie.cancelAnimation();
                    loaderLottie.setVisibility(View.GONE);
                    layoutEmptyState.setVisibility(View.VISIBLE);
                    rvInventory.setVisibility(View.GONE);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<List<UserCard>> call, Throwable t) {
                loaderLottie.cancelAnimation();
                loaderLottie.setVisibility(View.GONE);
                layoutEmptyState.setVisibility(View.VISIBLE);
                rvInventory.setVisibility(View.GONE);
                android.widget.Toast.makeText(getContext(), "Lỗi tải thẻ bài: " + t.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void applyFilters() {
        if (originalObjets == null || !isAdded()) return;
        List<Objet> filtered = new ArrayList<>();
        View sortBtn = getView() != null ? getView().findViewById(R.id.btn_sort_select) : null;
        String currentSort = (sortBtn instanceof android.widget.TextView) ? ((android.widget.TextView) sortBtn).getText().toString() : "Newest";

        for (Objet obj : originalObjets) {
            if (objetFilter.isEmpty()) {
                filtered.add(obj);
                continue;
            }
            
            org.json.JSONObject meta = DatabaseLoader.findById(requireContext(), obj.getCollectionId());
            if (meta == null) continue;
            String member = meta.optString("member", "");
            String cardClass = meta.optString("class", "");
            String season = meta.optString("season", "");
            
            boolean match = false;
            for (String f : objetFilter) {
                if (f.equalsIgnoreCase(member) || f.equalsIgnoreCase(cardClass) || f.equalsIgnoreCase(season)) {
                    match = true;
                    break;
                }
            }
            if (match) filtered.add(obj);
        }

        filtered.sort((a, b) -> {
            if ("Oldest".equals(currentSort)) return Integer.compare(a.getId(), b.getId());
            if ("Lowest No.".equals(currentSort)) return Integer.compare(a.getUpgradeLevel(), b.getUpgradeLevel());
            if ("Highest No.".equals(currentSort)) return Integer.compare(b.getUpgradeLevel(), a.getUpgradeLevel());
            return Integer.compare(b.getId(), a.getId());
        });

        if (rvInventory != null && rvInventory.getAdapter() instanceof com.vn.jet.mosco.adapter.BaseInventoryAdapter) {
            ((com.vn.jet.mosco.adapter.BaseInventoryAdapter) rvInventory.getAdapter()).updateData(filtered);
        }
        if (tvCount != null) tvCount.setText(filtered.size() + " Items");
        
        // Cập nhật trạng thái "Rỗng"
        if (layoutEmptyState != null) {
            layoutEmptyState.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
            rvInventory.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    // --- Inner Classes InventoryAdapter Deprecated In favor of BaseInventoryAdapter ---
}

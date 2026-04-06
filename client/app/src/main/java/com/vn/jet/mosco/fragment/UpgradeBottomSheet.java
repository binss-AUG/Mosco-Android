package com.vn.jet.mosco.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.adapter.BaseInventoryAdapter;
import com.vn.jet.mosco.model.Objet;
import com.vn.jet.mosco.utils.DatabaseLoader;
import com.vn.jet.mosco.utils.UpgradeAlgorithm;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bottom Sheet chọn thẻ cho Upgrade / Home / Spin.
 * OVR lấy trực tiếp từ cache (đã sync từ Server), KHÔNG tính local.
 */
public class UpgradeBottomSheet extends BottomSheetDialogFragment {

    private OnUpgradeCardSelectedListener listener;
    private List<Objet> cardList;

    private boolean isMultiSelect = false;
    private List<Objet> selectedMaterials = new ArrayList<>();
    private Objet mainCard;
    private UpgradeAlgorithm upgradeAlgorithm;
    private androidx.appcompat.widget.AppCompatButton btnConfirm;

    private RecyclerView rvInventory;
    private LinearLayout layoutEmptyState;
    private TextView tvTitle;
    private TextView tvCount;
    private BaseInventoryAdapter adapter;

    private final Set<String> objetFilter = new java.util.LinkedHashSet<>();
    private final String[] SORT_OPTIONS = {"Newest", "Oldest", "Lowest No.", "Highest No."};
    private List<Objet> originalObjets = new ArrayList<>();

    public interface OnUpgradeCardSelectedListener {
        void onUpgradeCardSelected(Objet card);
        void onMaterialsSelected(List<Objet> materials);
    }

    public void setOnUpgradeCardSelectedListener(OnUpgradeCardSelectedListener listener) {
        this.listener = listener;
    }

    public void setCardList(List<Objet> cardList) {
        this.cardList = cardList != null ? new ArrayList<>(cardList) : new ArrayList<>();
    }

    /**
     * Chiến thuật "Cache First, Always Sync":
     * Hiển thị cache ngay → gọi API ngầm để đồng bộ data mới nhất.
     * OVR lấy trực tiếp từ Server response (Server Truth).
     */
    public void loadDataFromCache() {
        // BƯỚC 1: Hiển thị từ Cache ngay lập tức
        List<Objet> inventoryList = new ArrayList<>();
        if (DatabaseLoader.cachedUserInventory != null) {
            for (DatabaseLoader.UserInventoryItem item : DatabaseLoader.cachedUserInventory) {
                Objet obj = new Objet(item.id.intValue(), item.collectionId, item.frontImage, item.level, item.exp, item.upgradeLevel);
                // OVR trực tiếp từ cache (đã được Server tính sẵn)
                obj.setOvr(item.ovr);
                // TypeKey tra từ database.json metadata (chỉ dùng cho UpgradeAlgorithm fill %)
                if (getContext() != null) {
                    org.json.JSONObject meta = DatabaseLoader.findById(getContext(), item.collectionId);
                    if (meta != null) {
                        obj.setTypeKey(mapClassToTypeKey(meta.optString("class", "")));
                    }
                }
                inventoryList.add(obj);
            }
        }
        this.originalObjets = inventoryList;
        applyFilters();

        // BƯỚC 2: LUÔN gọi API ngầm để lấy data mới nhất
        if (getContext() == null) return;
        Long userId = new com.vn.jet.mosco.utils.SessionManager(requireContext()).getUserId();
        if (userId == null) return;
        
        com.vn.jet.mosco.network.GameApiService apiService = com.vn.jet.mosco.network.ApiClient.getClient(requireContext()).create(com.vn.jet.mosco.network.GameApiService.class);
        apiService.getUserCards(userId).enqueue(new retrofit2.Callback<java.util.List<com.vn.jet.mosco.model.UserCard>>() {
            @Override
            public void onResponse(retrofit2.Call<java.util.List<com.vn.jet.mosco.model.UserCard>> call, retrofit2.Response<java.util.List<com.vn.jet.mosco.model.UserCard>> response) {
                if (!isAdded() || getContext() == null) return;
                if (response.isSuccessful() && response.body() != null) {
                    new Thread(() -> {
                        List<DatabaseLoader.UserInventoryItem> cachedList = new ArrayList<>();
                        List<Objet> freshList = new ArrayList<>();
                        for (com.vn.jet.mosco.model.UserCard uc : response.body()) {
                            org.json.JSONObject cardJson = DatabaseLoader.findById(requireContext(), uc.getCollectionId());
                            if (cardJson != null) {
                                String img = cardJson.optString("frontImage", "");
                                // OVR trực tiếp từ Server — không tính local
                                int ovr = uc.getOvr();
                                cachedList.add(new DatabaseLoader.UserInventoryItem(uc.getId(), uc.getCollectionId(), img, uc.getLevel(), uc.getExp(), uc.getUpgradeLevel(), ovr));

                                Objet obj = new Objet(uc.getId().intValue(), uc.getCollectionId(), img, uc.getLevel(), uc.getExp(), uc.getUpgradeLevel());
                                obj.setOvr(ovr);
                                String cardClass = cardJson.optString("class", "");
                                obj.setTypeKey(mapClassToTypeKey(cardClass));
                                freshList.add(obj);
                            }
                        }
                        DatabaseLoader.cachedUserInventory = cachedList;
                        if (getActivity() != null && isAdded()) {
                            getActivity().runOnUiThread(() -> {
                                originalObjets = freshList;
                                applyFilters();
                            });
                        }
                    }).start();
                }
            }

            @Override
            public void onFailure(retrofit2.Call<java.util.List<com.vn.jet.mosco.model.UserCard>> call, Throwable t) {
                // Nếu API lỗi thì giữ nguyên cache đã hiển thị
            }
        });
    }

    /**
     * Ánh xạ class → typeKey (chỉ dùng cho UpgradeAlgorithm fill %)
     */
    private String mapClassToTypeKey(String cardClass) {
        if (cardClass == null) return "FirstWelcome";
        String key = cardClass.replaceAll("\\s+", "");
        if (key.equalsIgnoreCase("Double")) return "Double";
        if (key.equalsIgnoreCase("SpecialUnit") || key.equalsIgnoreCase("Special")) return "SpecialUnit";
        if (key.equalsIgnoreCase("Premier")) return "Premier";
        return "FirstWelcome";
    }

    private final DatabaseLoader.OnInventoryChangeListener inventoryListener = () -> {
        if (getActivity() != null && isAdded()) {
            getActivity().runOnUiThread(this::loadDataFromCache);
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        DatabaseLoader.registerInventoryChangeListener(inventoryListener);
    }

    @Override
    public void onPause() {
        super.onPause();
        DatabaseLoader.unregisterInventoryChangeListener(inventoryListener);
    }

    public void setupMultiSelectMode(Objet mainCard, UpgradeAlgorithm algorithm, List<Objet> preSelected) {
        this.isMultiSelect = true;
        this.mainCard = mainCard;
        this.upgradeAlgorithm = algorithm;
        if (preSelected != null) {
            for (Objet c : preSelected) {
                if (c != null) this.selectedMaterials.add(c);
            }
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.CustomBottomSheetDialogTheme);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(dialogInterface -> {
            BottomSheetDialog d = (BottomSheetDialog) dialogInterface;
            FrameLayout bottomSheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                ViewGroup.LayoutParams layoutParams = bottomSheet.getLayoutParams();
                layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
                bottomSheet.setLayoutParams(layoutParams);

                BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        });
        return dialog;
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
        rvInventory = view.findViewById(R.id.rv_inventory);
        layoutEmptyState = view.findViewById(R.id.layout_empty_state);
        tvTitle = view.findViewById(R.id.tv_title);
        btnConfirm = view.findViewById(R.id.btn_confirm);
        tvCount = view.findViewById(R.id.tv_select_types_count);

        ivBack.setOnClickListener(v -> dismiss());

        View filterBtn = view.findViewById(R.id.btn_filter_select);
        if (filterBtn != null) {
            filterBtn.setOnClickListener(v ->
                CollectionFragment.showFilterBottomSheet(this, CollectionFragment.buildObjetCategories(requireContext()), 0, objetFilter, this::applyFilters));
        }

        View sortBtn = view.findViewById(R.id.btn_sort_select);
        LinearLayout dropdown = view.findViewById(R.id.dropdown_sort_select);
        if (sortBtn != null && dropdown != null) {
            CollectionFragment.setupSortDropdown(sortBtn, null, null, SORT_OPTIONS, dropdown, this::applyFilters);
        }

        rvInventory.setLayoutManager(new GridLayoutManager(getContext(), 3));

        adapter = new BaseInventoryAdapter(new ArrayList<>(), rvInventory, item -> {
            if (!isMultiSelect && listener != null) {
                listener.onUpgradeCardSelected(item);
                dismiss();
            }
        });

        if (isMultiSelect) {
            adapter.setMultiSelectMode(true, (item, selected) -> {
                if (selected) {
                    if (selectedMaterials.size() >= 5) {
                        Toast.makeText(getContext(), "Vui lòng gỡ thẻ cũ trước khi thêm mới (Tối đa 5 thẻ)!", Toast.LENGTH_SHORT).show();
                        adapter.setSelectedIds(getSelectedIds());
                        return;
                    }
                    double currentProgress = calculateCurrentProgress();
                    if (currentProgress >= 100.0) {
                        Toast.makeText(getContext(), "Tỷ lệ đã đủ 100%, không cần thêm thẻ!", Toast.LENGTH_SHORT).show();
                        adapter.setSelectedIds(getSelectedIds());
                        return;
                    }
                    if (mainCard != null && mainCard.getId() == item.getId()) {
                        Toast.makeText(getContext(), "Không thể rèn chính nó!", Toast.LENGTH_SHORT).show();
                        adapter.setSelectedIds(getSelectedIds());
                        return;
                    }
                    selectedMaterials.add(0, item);
                } else {
                    selectedMaterials.removeIf(sc -> sc.getId() == item.getId());
                }
                updateConfirmButtonText();
            });
            adapter.setSelectedIds(getSelectedIds());
        }

        rvInventory.setAdapter(adapter);
        applyFilters();

        if (isMultiSelect) {
            btnConfirm.setVisibility(View.VISIBLE);
            tvTitle.setText("Select Materials");
            btnConfirm.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onMaterialsSelected(selectedMaterials);
                }
                dismiss();
            });
            updateConfirmButtonText();
        } else {
            btnConfirm.setVisibility(View.GONE);
            tvTitle.setText("Select a Card");
        }
        
        loadDataFromCache();
    }

    private Set<Integer> getSelectedIds() {
        Set<Integer> ids = new HashSet<>();
        for (Objet obj : selectedMaterials) {
            ids.add(obj.getId());
        }
        return ids;
    }

    private void applyFilters() {
        if (originalObjets == null || !isAdded()) return;
        List<Objet> filtered = new ArrayList<>();
        View sortBtn = getView() != null ? getView().findViewById(R.id.btn_sort_select) : null;
        String currentSort = (sortBtn instanceof TextView) ? ((TextView) sortBtn).getText().toString() : "Newest";

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

        if (adapter != null) {
            adapter.updateData(filtered);
            if (isMultiSelect) {
                adapter.setSelectedIds(getSelectedIds());
            }
        }
        if (tvCount != null) tvCount.setText(filtered.size() + " Items");
        
        if (layoutEmptyState != null) {
            layoutEmptyState.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
            rvInventory.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    private double calculateCurrentProgress() {
        if (!isMultiSelect || mainCard == null || upgradeAlgorithm == null) return 0.0;
        List<UpgradeAlgorithm.Card> algoMaterials = new ArrayList<>();
        for (Objet mc : selectedMaterials) {
            UpgradeAlgorithm.Card c = new UpgradeAlgorithm.Card();
            c.id = mc.getIdString();
            c.typeKey = mc.getTypeKey();
            c.level = mc.getCardLevel();
            c.ovr = mc.getOvr();
            algoMaterials.add(c);
        }

        UpgradeAlgorithm.Card target = new UpgradeAlgorithm.Card();
        target.id = mainCard.getIdString();
        target.typeKey = mainCard.getTypeKey();
        target.level = mainCard.getCardLevel();
        target.ovr = mainCard.getOvr();

        return upgradeAlgorithm.calculateFillPercent(target, algoMaterials);
    }

    private void updateConfirmButtonText() {
        if (btnConfirm == null) return;
        double percent = calculateCurrentProgress();
        if (selectedMaterials.isEmpty()) {
            btnConfirm.setText("Confirm");
        } else {
            btnConfirm.setText(String.format("Confirm (%.1f%%)", percent));
        }
    }
}

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

public class InventoryBottomSheet extends BottomSheetDialogFragment {

    private OnObjetSelectedListener singleSelectListener;
    private OnMultiObjetsSelectedListener multiSelectListener;

    private boolean isMultiSelect = false;
    private List<Objet> selectedMaterials = new ArrayList<>();
    private Objet mainCard;
    private UpgradeAlgorithm upgradeAlgorithm;

    public static final java.util.Set<String> objetFilter = new java.util.LinkedHashSet<>();
    public static String currentSortOption = "Newest";
    private final String[] SORT_OPTIONS = {"Newest", "Oldest", "Highest OVR", "Lowest OVR", "Highest Level", "Lowest Level", "Highest Badge", "Lowest Badge"};
    private List<Objet> originalObjets = new ArrayList<>();

    private androidx.appcompat.widget.AppCompatButton btnConfirm;
    private BaseInventoryAdapter adapter;
    private RecyclerView rvInventory;
    private LinearLayout layoutEmptyState;
    private com.airbnb.lottie.LottieAnimationView loaderLottie;

    public interface OnObjetSelectedListener {
        void onObjetSelected(Objet objet);
    }

    public interface OnMultiObjetsSelectedListener {
        void onMaterialsSelected(List<Objet> materials);
    }

    public void setOnObjetSelectedListener(OnObjetSelectedListener listener) {
        this.singleSelectListener = listener;
    }

    public void setMultiSelectMode(Objet mainCard, UpgradeAlgorithm algorithm, List<Objet> preSelected, OnMultiObjetsSelectedListener listener) {
        this.isMultiSelect = true;
        this.mainCard = mainCard;
        this.upgradeAlgorithm = algorithm;
        this.multiSelectListener = listener;
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
        btnConfirm = view.findViewById(R.id.btn_confirm);
        loaderLottie = view.findViewById(R.id.loader_lottie);
        TextView tvTitle = view.findViewById(R.id.tv_title);

        ivBack.setOnClickListener(v -> dismiss());

        rvInventory.setHasFixedSize(true);
        rvInventory.setItemViewCacheSize(20);
        rvInventory.setLayoutManager(new GridLayoutManager(getContext(), 3));

        View filterBtn = view.findViewById(R.id.btn_filter_select);
        if (filterBtn != null) {
            filterBtn.setOnClickListener(v ->
                CollectionFragment.showFilterBottomSheet(this, CollectionFragment.buildObjetCategories(requireContext()), 0, objetFilter, this::applyFilters));
        }

        View sortBtn = view.findViewById(R.id.btn_sort_select);
        if (sortBtn instanceof TextView) {
            ((TextView) sortBtn).setText(currentSortOption);
        }
        android.widget.LinearLayout dropdown = view.findViewById(R.id.dropdown_sort_select);
        if (sortBtn != null && dropdown != null) {
            CollectionFragment.setupSortDropdown(sortBtn, null, null, SORT_OPTIONS, dropdown, () -> {
                if (sortBtn instanceof TextView) {
                    currentSortOption = ((TextView) sortBtn).getText().toString();
                }
                applyFilters();
            });
        }

        if (isMultiSelect) {
            btnConfirm.setVisibility(View.VISIBLE);
            if (tvTitle != null) tvTitle.setText("Select Materials");
            btnConfirm.setOnClickListener(v -> {
                if (multiSelectListener != null) {
                    multiSelectListener.onMaterialsSelected(selectedMaterials);
                }
                dismiss();
            });
        } else {
            btnConfirm.setVisibility(View.GONE);
            if (tvTitle != null) tvTitle.setText("Select a Card");
        }

        loadRealInventory();
    }

    private void loadRealInventory() {
        if (getContext() == null) return;

        adapter = new BaseInventoryAdapter(new ArrayList<>(), rvInventory, item -> {
            if (!isMultiSelect) {
                if (singleSelectListener != null) {
                    singleSelectListener.onObjetSelected(item);
                    dismiss();
                } else {
                    com.vn.jet.mosco.utils.ObjetDetailBinder.showObjetDetail(requireContext(), item);
                }
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
            updateConfirmButtonText();
        }

        rvInventory.setAdapter(adapter);

        // BƯỚC 1: Hiển thị từ Cache
        if (DatabaseLoader.cachedUserInventory != null && !DatabaseLoader.cachedUserInventory.isEmpty()) {
            List<Objet> realObjets = new ArrayList<>();
            for (DatabaseLoader.UserInventoryItem item : DatabaseLoader.cachedUserInventory) {
                Objet obj = Objet.fromCacheItem(item);
                realObjets.add(obj);
            }
            originalObjets = realObjets;
            layoutEmptyState.setVisibility(View.GONE);
            rvInventory.setVisibility(View.VISIBLE);
            loaderLottie.setVisibility(View.GONE);
            applyFilters();
        } else {
            loaderLottie.setVisibility(View.VISIBLE);
            loaderLottie.playAnimation();
            rvInventory.setVisibility(View.GONE);
            layoutEmptyState.setVisibility(View.GONE);
        }

        // BƯỚC 2: Gọi API ngầm Sync
        Long userId = new com.vn.jet.mosco.utils.SessionManager(requireContext()).getUserId();
        if (userId == null) return;

        com.vn.jet.mosco.network.GameApiService apiService = com.vn.jet.mosco.network.ApiClient.getClient(requireContext()).create(com.vn.jet.mosco.network.GameApiService.class);
        apiService.getUserCards(userId).enqueue(new retrofit2.Callback<List<com.vn.jet.mosco.model.UserCard>>() {
            @Override
            public void onResponse(retrofit2.Call<List<com.vn.jet.mosco.model.UserCard>> call, retrofit2.Response<List<com.vn.jet.mosco.model.UserCard>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    List<com.vn.jet.mosco.model.UserCard> responseCards = response.body();
                    new Thread(() -> {
                        int capacity = responseCards.size();
                        List<Objet> realObjets = new ArrayList<>(capacity);
                        List<DatabaseLoader.UserInventoryItem> cachedList = new ArrayList<>(capacity);
                        
                        android.content.Context ctx = getContext();
                        if (ctx == null) return;
                        
                        for (com.vn.jet.mosco.model.UserCard userCard : responseCards) {
                            DatabaseLoader.UserInventoryItem cachedItem = DatabaseLoader.UserInventoryItem.fromUserCard(userCard);
                            Objet obj = Objet.fromCacheItem(cachedItem);
                            realObjets.add(obj);
                            cachedList.add(cachedItem);
                        }
                        DatabaseLoader.cachedUserInventory = cachedList;

                        if (getActivity() != null && isAdded()) {
                            getActivity().runOnUiThread(() -> {
                                loaderLottie.cancelAnimation();
                                loaderLottie.setVisibility(View.GONE);
                                if (realObjets.isEmpty()) {
                                    rvInventory.setVisibility(View.GONE);
                                    layoutEmptyState.setVisibility(View.VISIBLE);
                                    originalObjets = new ArrayList<>();
                                } else {
                                    rvInventory.setVisibility(View.VISIBLE);
                                    layoutEmptyState.setVisibility(View.GONE);
                                    originalObjets = realObjets;
                                }
                                applyFilters();
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

    private Set<Integer> getSelectedIds() {
        Set<Integer> ids = new HashSet<>();
        for (Objet obj : selectedMaterials) {
            ids.add(obj.getId());
        }
        return ids;
    }

    private void updateItemCount(View view, int count) {
        if (view == null) return;
        TextView tvCount = view.findViewById(R.id.tv_select_types_count);
        if (tvCount != null) {
            tvCount.setText(count + " Items");
        }
    }

    private void applyFilters() {
        if (originalObjets == null || !isAdded()) return;
        List<Objet> filtered = new ArrayList<>();
        View sortBtn = getView() != null ? getView().findViewById(R.id.btn_sort_select) : null;
        String currentSort = currentSortOption;

        java.util.Set<String> artistsList = new java.util.HashSet<>(java.util.Arrays.asList(
            "SeoYeon", "HyeRin", "JiWoo", "ChaeYeon", "YooYeon", "SooMin", "NaKyoung", "YuBin", 
            "Kaede", "DaHyun", "Kotone", "YeonJi", "Nien", "SoHyun", "Xinyu", "Mayu", 
            "Lynn", "JooBin", "HaYeon", "ShiOn", "ChaeWon", "Sullin", "SeoAh", "JiYeon"
        ));
        java.util.Set<String> classesList = new java.util.HashSet<>(java.util.Arrays.asList("First", "Welcome", "Double", "Premier", "Special", "SpecialUnit"));

        java.util.Set<String> selArtists = new java.util.HashSet<>();
        java.util.Set<String> selClasses = new java.util.HashSet<>();
        java.util.Set<String> selSeasons = new java.util.HashSet<>();

        for (String f : objetFilter) {
            boolean isArtist = false;
            for (String a : artistsList) { if (a.equalsIgnoreCase(f)) { selArtists.add(f.toLowerCase()); isArtist = true; break; } }
            if (isArtist) continue;

            boolean isClass = false;
            for (String c : classesList) { if (c.equalsIgnoreCase(f)) { selClasses.add(f.toLowerCase()); isClass = true; break; } }
            if (isClass) continue;

            selSeasons.add(f.toLowerCase());
        }

        for (Objet obj : originalObjets) {
            if (objetFilter.isEmpty()) {
                filtered.add(obj);
                continue;
            }
            // For card member and season lookup
            org.json.JSONObject meta = DatabaseLoader.findById(requireContext(), obj.getCollectionId());
            String member = meta != null ? meta.optString("member", obj.getMember()) : obj.getMember();
            String cardClass = obj.getTypeKey();
            String season = meta != null ? meta.optString("season", obj.getSeason()) : obj.getSeason();
            String mappedClass = mapClassToTypeKey(cardClass);
            
            boolean matchArtist = selArtists.isEmpty() || (member != null && selArtists.contains(member.toLowerCase()));
            boolean matchClass = selClasses.isEmpty() || (cardClass != null && selClasses.contains(cardClass.toLowerCase())) || (mappedClass != null && selClasses.contains(mappedClass.toLowerCase()));
            boolean matchSeason = selSeasons.isEmpty() || (season != null && selSeasons.contains(season.toLowerCase()));

            if (matchArtist && matchClass && matchSeason) {
                filtered.add(obj);
            }
        }

        filtered.sort((a, b) -> {
            if ("Oldest".equals(currentSort)) return Integer.compare(a.getId(), b.getId());
            if ("Highest OVR".equals(currentSort)) return Integer.compare(b.getOvr(), a.getOvr());
            if ("Lowest OVR".equals(currentSort)) return Integer.compare(a.getOvr(), b.getOvr());
            if ("Highest Level".equals(currentSort)) return Integer.compare(b.getLevel(), a.getLevel());
            if ("Lowest Level".equals(currentSort)) return Integer.compare(a.getLevel(), b.getLevel());
            if ("Highest Badge".equals(currentSort)) return Integer.compare(b.getUpgradeLevel(), a.getUpgradeLevel());
            if ("Lowest Badge".equals(currentSort)) return Integer.compare(a.getUpgradeLevel(), b.getUpgradeLevel());
            return Integer.compare(b.getId(), a.getId()); // Default: Newest (ID lớn nhất = mới nhất)
        });

        if (adapter != null) {
            adapter.updateData(filtered);
            if (isMultiSelect) {
                adapter.setSelectedIds(getSelectedIds());
            }
        }
        updateItemCount(getView(), filtered.size());
        
        if (layoutEmptyState != null) {
            layoutEmptyState.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
            if (rvInventory != null) rvInventory.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    private String mapClassToTypeKey(String cardClass) {
        if (cardClass == null) return "FirstWelcome";
        String key = cardClass.replaceAll("\\s+", "");
        if (key.equalsIgnoreCase("Double")) return "Double";
        if (key.equalsIgnoreCase("SpecialUnit") || key.equalsIgnoreCase("Special")) return "SpecialUnit";
        if (key.equalsIgnoreCase("Premier")) return "Premier";
        return "FirstWelcome";
    }

    private double calculateCurrentProgress() {
        if (!isMultiSelect || mainCard == null || upgradeAlgorithm == null) return 0.0;
        List<UpgradeAlgorithm.Card> algoMaterials = new ArrayList<>();
        for (Objet mc : selectedMaterials) {
            UpgradeAlgorithm.Card c = new UpgradeAlgorithm.Card();
            c.id = mc.getIdString();
            c.typeKey = mapClassToTypeKey(mc.getTypeKey());
            c.level = mc.getCardLevel();
            c.ovr = mc.getOvr();
            algoMaterials.add(c);
        }

        UpgradeAlgorithm.Card target = new UpgradeAlgorithm.Card();
        target.id = mainCard.getIdString();
        target.typeKey = mapClassToTypeKey(mainCard.getTypeKey());
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

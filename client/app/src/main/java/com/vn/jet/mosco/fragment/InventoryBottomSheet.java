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
    private int maxSelectionCount = 5; // Default for upgrade
    private boolean isSquadMode = false;
    private java.util.Set<Long> busyIds = new java.util.HashSet<>();

    public static final java.util.Set<String> objetFilter = new java.util.LinkedHashSet<>();
    public static String currentSortOption = "Newest";
    private boolean isAscending = false;
    private final String[] SORT_OPTIONS = {"Newest", "Badge", "Level", "Artist (A-Z)", "Status"};
    private List<Objet> originalObjets = new ArrayList<>();
    private com.vn.jet.mosco.view.InventoryFilterBar filterBar;

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
        this.isSquadMode = false;
        this.maxSelectionCount = 5;
        this.mainCard = mainCard;
        this.upgradeAlgorithm = algorithm;
        this.multiSelectListener = listener;
        if (preSelected != null) {
            for (Objet c : preSelected) {
                if (c != null) this.selectedMaterials.add(c);
            }
        }
    }

    public void setSquadSelectMode(int maxSelect, OnMultiObjetsSelectedListener listener) {
        this.isMultiSelect = true;
        this.isSquadMode = true;
        this.maxSelectionCount = maxSelect;
        this.multiSelectListener = listener;
        this.selectedMaterials = new ArrayList<>();
    }
    
    // Logic kiểm tra xem một Objet có bị trùng Artist với các Objet đã chọn không
    private boolean isArtistCollision(Objet newItem) {
        if (!isSquadMode || newItem == null) return false;
        String newMember = newItem.getMember();
        if (newMember == null) return false;
        
        for (Objet selected : selectedMaterials) {
            if (newMember.equalsIgnoreCase(selected.getMember())) {
                return true;
            }
        }
        return false;
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
        View layoutActionButtons = view.findViewById(R.id.layout_action_buttons);
        btnConfirm = view.findViewById(R.id.btn_confirm);
        View btnQuickBottom = view.findViewById(R.id.btn_quick_pick_bottom);
        loaderLottie = view.findViewById(R.id.loader_lottie);
        TextView tvTitle = view.findViewById(R.id.tv_title);

        ivBack.setOnClickListener(v -> dismiss());

        rvInventory.setHasFixedSize(true);
        rvInventory.setItemViewCacheSize(20);
        rvInventory.setLayoutManager(new GridLayoutManager(getContext(), 3));

        filterBar = view.findViewById(R.id.filter_bar);
        LinearLayout dropdownSort = view.findViewById(R.id.dropdown_sort_select);
        
        if (filterBar != null) {
            if (dropdownSort != null) filterBar.attachDropdown(dropdownSort);
            filterBar.setSortText(currentSortOption);
            filterBar.setListener(new com.vn.jet.mosco.view.InventoryFilterBar.OnFilterChangeListener() {
                @Override
                public void onFilterChanged(String sortOption, boolean ascending) {
                    currentSortOption = sortOption;
                    isAscending = ascending;
                    applyFilters();
                }

                @Override
                public void onFilterRequested() {
                    CollectionFragment.showFilterBottomSheet(InventoryBottomSheet.this, CollectionFragment.buildObjetCategories(requireContext()), 0, objetFilter, InventoryBottomSheet.this::applyFilters);
                }
            });
        }

        if (isMultiSelect) {
            if (layoutActionButtons != null) layoutActionButtons.setVisibility(View.VISIBLE);
            
            if (btnQuickBottom != null) {
                btnQuickBottom.setVisibility(isSquadMode ? View.VISIBLE : View.GONE);
                btnQuickBottom.setOnClickListener(v -> quickPickTeam());
            }

            if (tvTitle != null) tvTitle.setText(isSquadMode ? getString(R.string.stage_squad_title) : getString(R.string.inventory_title));
            btnConfirm.setOnClickListener(v -> {
                if (multiSelectListener != null) {
                    multiSelectListener.onMaterialsSelected(selectedMaterials);
                }
                dismiss();
            });
        } else {
            if (layoutActionButtons != null) layoutActionButtons.setVisibility(View.GONE);
            if (tvTitle != null) tvTitle.setText(getString(R.string.inventory_title));
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
                    if (selectedMaterials.size() >= maxSelectionCount) {
                        int msgRes = isSquadMode ? R.string.stage_msg_squad_full : R.string.upgrade_error_max_materials;
                        String msg = isSquadMode ? getString(msgRes, maxSelectionCount) : getString(msgRes);
                        Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
                        adapter.setSelectedIds(getSelectedIds());
                        return;
                    }
                    
                    if (isSquadMode && isArtistCollision(item)) {
                        Toast.makeText(getContext(), R.string.stage_msg_artist_collision, Toast.LENGTH_SHORT).show();
                        adapter.setSelectedIds(getSelectedIds());
                        return;
                    }

                    if (!isSquadMode) {
                        double currentProgress = calculateCurrentProgress();
                        if (currentProgress >= 100.0) {
                            Toast.makeText(getContext(), R.string.upgrade_error_max_probability, Toast.LENGTH_SHORT).show();
                            adapter.setSelectedIds(getSelectedIds());
                            return;
                        }
                        if (mainCard != null && mainCard.getId() == item.getId()) {
                            Toast.makeText(getContext(), R.string.upgrade_msg_main_as_material, Toast.LENGTH_SHORT).show();
                            adapter.setSelectedIds(getSelectedIds());
                            return;
                        }
                    }
                    selectedMaterials.add(0, item);
                } else {
                    selectedMaterials.removeIf(sc -> sc.getId() == item.getId());
                }
                updateDisabledStates();
                updateConfirmButtonText();
            });
            adapter.setSelectedIds(getSelectedIds());
            updateDisabledStates();
            updateConfirmButtonText();
        }

        rvInventory.setAdapter(adapter);

        // BƯỚC 1: Hiển thị từ Cache NGAY LẬP TỨC
        if (DatabaseLoader.cachedUserInventory != null && !DatabaseLoader.cachedUserInventory.isEmpty()) {
            List<Objet> realObjets = new ArrayList<>(DatabaseLoader.cachedUserInventory.size());
            busyIds.clear();
            for (DatabaseLoader.UserInventoryItem item : DatabaseLoader.cachedUserInventory) {
                Objet obj = Objet.fromCacheItem(item);
                realObjets.add(obj);
                if (obj.getStatus() != null && !"AVAILABLE".equalsIgnoreCase(obj.getStatus())) {
                    busyIds.add(obj.getId());
                }
            }
            originalObjets = realObjets;
            adapter.updateData(originalObjets);
            layoutEmptyState.setVisibility(View.GONE);
            rvInventory.setVisibility(View.VISIBLE);
            loaderLottie.setVisibility(View.GONE);
            
            updateDisabledStates();
            // Chạy applyFilters ngầm sau khi đã hiện data thô
            rvInventory.post(this::applyFilters);
        } else {
            loaderLottie.setVisibility(View.VISIBLE);
            loaderLottie.playAnimation();
            rvInventory.setVisibility(View.GONE);
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

                                    busyIds.clear();
                                    for (Objet obj : realObjets) {
                                        if (obj.getStatus() != null && !"AVAILABLE".equalsIgnoreCase(obj.getStatus())) {
                                            busyIds.add(obj.getId());
                                        }
                                    }
                                }
                                applyFilters();
                                updateDisabledStates();
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

    private Set<Long> getSelectedIds() {
        Set<Long> ids = new HashSet<>();
        for (Objet obj : selectedMaterials) {
            ids.add(obj.getId());
        }
        return ids;
    }

    private void updateItemCount(View view, int count) {
        if (view == null) return;
        TextView tvCount = view.findViewById(R.id.tv_select_types_count);
        if (tvCount != null) {
            tvCount.setText(getString(R.string.inventory_format_items_count, count));
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

        // Cache seasons to avoid DB lookups inside the sort loop
        java.util.Map<Long, String> seasonCache = new java.util.HashMap<>();
        for (Objet obj : filtered) {
            org.json.JSONObject meta = DatabaseLoader.findById(requireContext(), obj.getCollectionId());
            String season = meta != null ? meta.optString("season", obj.getSeason()) : obj.getSeason();
            seasonCache.put(obj.getId(), season != null ? season : "");
        }

        filtered.sort((a, b) -> {
            int result = 0;
            if ("Badge".equals(currentSort)) result = Integer.compare(a.getUpgradeLevel(), b.getUpgradeLevel());
            else if ("Level".equals(currentSort)) result = Integer.compare(a.getLevel(), b.getLevel());
            else if ("Artist (A-Z)".equals(currentSort)) {
                String m1 = a.getMember() != null ? a.getMember() : "";
                String m2 = b.getMember() != null ? b.getMember() : "";
                result = m1.compareToIgnoreCase(m2);
            }
            else if ("Status".equals(currentSort)) {
                boolean b1 = busyIds.contains(a.getId());
                boolean b2 = busyIds.contains(b.getId());
                result = Boolean.compare(b1, b2); // Available first by default
            }
            else if ("Class".equals(currentSort)) {
                int rankA = getCardClassRank(a.getTypeKey());
                int rankB = getCardClassRank(b.getTypeKey());
                if (rankA != rankB) {
                    result = Integer.compare(rankA, rankB);
                } else {
                    String s1 = seasonCache.get(a.getId());
                    String s2 = seasonCache.get(b.getId());
                    if (s1 == null) s1 = "";
                    if (s2 == null) s2 = "";
                    int seasonComp = s1.compareToIgnoreCase(s2);
                    if (seasonComp != 0) {
                        result = seasonComp;
                    } else {
                        int ovrComp = Integer.compare(a.getOvr(), b.getOvr());
                        if (ovrComp != 0) {
                            result = ovrComp;
                        } else {
                            int lvlComp = Integer.compare(a.getLevel(), b.getLevel());
                            if (lvlComp != 0) {
                                result = lvlComp;
                            } else {
                                result = Long.compare(a.getId(), b.getId());
                            }
                        }
                    }
                }
            }
            else if ("Season".equals(currentSort)) {
                String s1 = seasonCache.get(a.getId());
                String s2 = seasonCache.get(b.getId());
                if (s1 == null) s1 = "";
                if (s2 == null) s2 = "";
                int seasonComp = s1.compareToIgnoreCase(s2);
                if (seasonComp != 0) {
                    result = seasonComp;
                } else {
                    int rankA = getCardClassRank(a.getTypeKey());
                    int rankB = getCardClassRank(b.getTypeKey());
                    if (rankA != rankB) {
                        result = Integer.compare(rankA, rankB);
                    } else {
                        int ovrComp = Integer.compare(a.getOvr(), b.getOvr());
                        if (ovrComp != 0) {
                            result = ovrComp;
                        } else {
                            int lvlComp = Integer.compare(a.getLevel(), b.getLevel());
                            if (lvlComp != 0) {
                                result = lvlComp;
                            } else {
                                result = Long.compare(a.getId(), b.getId());
                            }
                        }
                    }
                }
            }
            else result = Long.compare(a.getId(), b.getId()); // Default: Newest (ID)

            return isAscending ? result : -result;
        });

        if (adapter != null) {
            adapter.updateData(filtered);
            if (isMultiSelect) {
                adapter.setSelectedIds(getSelectedIds());
                updateDisabledStates();
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
        if (isSquadMode) {
            btnConfirm.setText(String.format("Confirm (%d/%d)", selectedMaterials.size(), maxSelectionCount));
            return;
        }
        double percent = calculateCurrentProgress();
        if (selectedMaterials.isEmpty()) {
            btnConfirm.setText(getString(R.string.action_confirm));
        } else {
            btnConfirm.setText(String.format("Confirm (%.1f%%)", percent));
        }
    }

    private void updateDisabledStates() {
        if (adapter == null) return;
        java.util.Set<String> disabledMembers = new java.util.HashSet<>();
        if (isSquadMode) {
            for (Objet obj : selectedMaterials) {
                if (obj.getMember() != null) {
                    disabledMembers.add(obj.getMember().trim().toLowerCase());
                }
            }
        }
        adapter.setDisabledStates(busyIds, disabledMembers);
    }

    private int getCardClassRank(String cardClass) {
        if (cardClass == null) return 0;
        String key = cardClass.replaceAll("\\s+", "").toLowerCase();
        if (key.contains("premier")) return 4;
        if (key.contains("special")) return 3;
        if (key.contains("double")) return 2;
        if (key.contains("first") || key.contains("welcome")) return 1;
        return 0;
    }

    private void quickPickTeam() {
        if (!isSquadMode || originalObjets == null) return;
        
        selectedMaterials.clear();
        List<Objet> pool = new ArrayList<>(originalObjets);
        
        // Cache seasons to avoid DB lookups inside the sort loop
        java.util.Map<Long, String> seasonCache = new java.util.HashMap<>();
        for (Objet obj : pool) {
            org.json.JSONObject meta = DatabaseLoader.findById(requireContext(), obj.getCollectionId());
            String season = meta != null ? meta.optString("season", obj.getSeason()) : obj.getSeason();
            seasonCache.put(obj.getId(), season != null ? season : "");
        }

        // Sort by Card Class desc, Season desc, OVR desc, Level desc, ID desc
        pool.sort((a, b) -> {
            int rankA = getCardClassRank(a.getTypeKey());
            int rankB = getCardClassRank(b.getTypeKey());
            if (rankA != rankB) return Integer.compare(rankB, rankA);
            
            String seasonA = seasonCache.get(a.getId());
            String seasonB = seasonCache.get(b.getId());
            if (seasonA != null && seasonB != null) {
                int seasonComp = seasonB.compareToIgnoreCase(seasonA);
                if (seasonComp != 0) return seasonComp;
            }

            int ovrComp = Integer.compare(b.getOvr(), a.getOvr());
            if (ovrComp != 0) return ovrComp;
            
            int lvlComp = Integer.compare(b.getLevel(), a.getLevel());
            if (lvlComp != 0) return lvlComp;
            
            return Long.compare(b.getId(), a.getId());
        });
        
        for (Objet item : pool) {
            if (selectedMaterials.size() >= maxSelectionCount) break;
            
            // Check if busy
            if (busyIds.contains(item.getId())) continue;
            
            // Check artist collision
            if (!isArtistCollision(item)) {
                selectedMaterials.add(item);
            }
        }
        
        if (adapter != null) {
            adapter.setSelectedIds(getSelectedIds());
            updateDisabledStates();
        }
        
        // Auto-scroll and sort to show the selected cards at the top
        currentSortOption = "Class";
        isAscending = false; // Descending (Best first)
        if (filterBar != null) {
            filterBar.setSortText("Class");
            filterBar.setAscending(false);
        }
        applyFilters();
        if (rvInventory != null) {
            rvInventory.scrollToPosition(0);
        }
        
        updateConfirmButtonText();
        Toast.makeText(getContext(), R.string.inventory_msg_quick_pick_success, Toast.LENGTH_SHORT).show();
    }
}

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
import com.vn.jet.mosco.adapter.UnifiedCardAdapter;
import com.vn.jet.mosco.model.CardDisplayItem;
import com.vn.jet.mosco.model.Objet;
import com.vn.jet.mosco.utils.DatabaseLoader;
import com.vn.jet.mosco.utils.UpgradeAlgorithm;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class InventoryBottomSheet extends BottomSheetDialogFragment {

    private OnCardSelectedListener singleSelectListener;
    private OnMultiCardsSelectedListener multiSelectListener;

    private boolean isMultiSelect = false;
    private List<CardDisplayItem> selectedMaterials = new ArrayList<>();
    private CardDisplayItem mainCard;
    private UpgradeAlgorithm upgradeAlgorithm;
    private int maxSelectionCount = 5; // Default for upgrade
    private boolean isSquadMode = false;
    private boolean isShowcaseMode = false;
    private java.util.Set<Long> busyIds = new java.util.HashSet<>();

    public static final java.util.Set<String> objetFilter = new java.util.LinkedHashSet<>();
    public static String currentSortOption = "Newest"; 
    private boolean isAscending = false;
    private String[] SORT_OPTIONS;
    private List<CardDisplayItem> originalObjets = new ArrayList<>();
    private com.vn.jet.mosco.view.InventoryFilterBar filterBar;
    private boolean isApplyingFilter = false;
    private final android.os.Handler filterHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable filterRunnable = this::executeApplyFilters;
    private final java.util.concurrent.ExecutorService filterExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();

    private androidx.appcompat.widget.AppCompatButton btnConfirm;
    private UnifiedCardAdapter adapter;
    private RecyclerView rvInventory;
    private LinearLayout layoutEmptyState;
    private com.airbnb.lottie.LottieAnimationView loaderLottie;

    public interface OnCardSelectedListener {
        void onCardSelected(CardDisplayItem item);
    }

    public interface OnMultiCardsSelectedListener {
        void onMaterialsSelected(List<CardDisplayItem> materials);
    }

    public void setOnCardSelectedListener(OnCardSelectedListener listener) {
        this.singleSelectListener = listener;
    }

    public void setMultiSelectMode(CardDisplayItem mainCard, UpgradeAlgorithm algorithm, List<CardDisplayItem> preSelected, OnMultiCardsSelectedListener listener) {
        this.isMultiSelect = true;
        this.isSquadMode = false;
        this.maxSelectionCount = 5;
        this.mainCard = mainCard;
        this.upgradeAlgorithm = algorithm;
        this.multiSelectListener = listener;
        if (preSelected != null) {
            this.selectedMaterials.addAll(preSelected);
        }
    }

    public void setSquadSelectMode(int maxSelect, OnMultiCardsSelectedListener listener) {
        this.isMultiSelect = true;
        this.isSquadMode = true;
        this.maxSelectionCount = maxSelect;
        this.multiSelectListener = listener;
        this.selectedMaterials = new ArrayList<>();
    }

    public void setShowcaseMode(boolean showcaseMode) {
        this.isShowcaseMode = showcaseMode;
    }
    
    // Logic kiểm tra xem một Card có bị trùng Artist với các Card đã chọn không
    private boolean isArtistCollision(CardDisplayItem newItem) {
        if (!isSquadMode || newItem == null) return false;
        String newMember = newItem.getMember();
        if (newMember == null) return false;
        
        for (CardDisplayItem selected : selectedMaterials) {
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
        
        // Khởi tạo danh sách tùy chọn sắp xếp từ tài nguyên hệ thống để đảm bảo tính nhất quán
        SORT_OPTIONS = getResources().getStringArray(R.array.inventory_sort_options);
        if (currentSortOption == null || currentSortOption.isEmpty()) {
            currentSortOption = SORT_OPTIONS[0]; // Mặc định là 'Newest'
        }

        ImageView ivBack = view.findViewById(R.id.iv_back);
        rvInventory = view.findViewById(R.id.rv_inventory);
        // [QUIET LUXURY] Áp dụng phanh ABS
        com.vn.jet.mosco.utils.ViewUtils.limitFlingVelocity(rvInventory);
        layoutEmptyState = view.findViewById(R.id.layout_empty_state);
        View layoutActionButtons = view.findViewById(R.id.layout_action_buttons);
        btnConfirm = view.findViewById(R.id.btn_confirm);
        View btnQuickBottom = view.findViewById(R.id.btn_quick_pick_bottom);
        loaderLottie = view.findViewById(R.id.loader_lottie);
        TextView tvTitle = view.findViewById(R.id.tv_title);

        ivBack.setOnClickListener(v -> dismiss());

        rvInventory.setHasFixedSize(true);
        rvInventory.setItemViewCacheSize(20);
        // [QUIET LUXURY] ĐÃ GỠ BỎ Drawing Cache (Nguyên nhân gây OOM/Crash khi lướt nhanh)
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
                    
                    // [BUG 10] Debounce spam click sorting
                    filterHandler.removeCallbacks(filterRunnable);
                    filterHandler.postDelayed(filterRunnable, 150);
                }

                @Override
                public void onFilterRequested() {
                    // [PERFORMANCE] Nạp dữ liệu Filter từ Room ở background thread để tránh block Main Thread
                    new Thread(() -> {
                        List<CollectionFragment.FilterCategory> categories = CollectionFragment.buildObjetCategories(requireContext());
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                CollectionFragment.showFilterBottomSheet(
                                    InventoryBottomSheet.this, 
                                    categories, 
                                    objetFilter, 
                                    filterBar, 
                                    SORT_OPTIONS, 
                                    InventoryBottomSheet.this::applyFilters
                                );
                            });
                        }
                    }).start();
                }
            });
        }

        if (isMultiSelect) {
            if (layoutActionButtons != null) layoutActionButtons.setVisibility(View.VISIBLE);
            
            if (btnQuickBottom != null) {
                btnQuickBottom.setVisibility(isSquadMode ? View.VISIBLE : View.GONE);
                btnQuickBottom.setOnClickListener(v -> quickPickTeam());
            }

            if (tvTitle != null) {
                tvTitle.setText(isSquadMode ? getString(R.string.stage_squad_title) : getString(R.string.inventory_title));
            }
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

        adapter = new UnifiedCardAdapter(new ArrayList<>(), rvInventory, UnifiedCardAdapter.DisplayMode.INVENTORY, item -> {
            if (!isMultiSelect) {
                if (singleSelectListener != null) {
                    singleSelectListener.onCardSelected(item);
                    dismiss();
                } else {
                    // Sử dụng trực tiếp CardDisplayItem cho Detail Binder (Đã đồng bộ)
                    com.vn.jet.mosco.utils.CollectionDetailBinder.showDetail(requireContext(), item);
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
            List<CardDisplayItem> displayItems = new ArrayList<>(DatabaseLoader.cachedUserInventory.size());
            busyIds.clear();
            for (DatabaseLoader.UserInventoryItem item : DatabaseLoader.cachedUserInventory) {
                CardDisplayItem displayItem = CardDisplayItem.fromCacheItem(item);
                displayItems.add(displayItem);
                // [SHOWCASE FIX] Nếu là chế độ trưng bày, không khóa thẻ BUSY
                if (!isShowcaseMode && displayItem.getStatus() != null && !"AVAILABLE".equalsIgnoreCase(displayItem.getStatus())) {
                    busyIds.add(displayItem.getId());
                }
            }
            originalObjets = displayItems;
            adapter.updateData(originalObjets);
            layoutEmptyState.setVisibility(View.GONE);
            rvInventory.setVisibility(View.VISIBLE);
            if (loaderLottie != null) loaderLottie.setVisibility(View.GONE);
            
            updateDisabledStates();
            rvInventory.post(this::applyFilters);
        } else {
            adapter.setLoading(true);
            rvInventory.setVisibility(View.VISIBLE);
            if (loaderLottie != null) loaderLottie.setVisibility(View.GONE);
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
                        List<CardDisplayItem> displayItems = new ArrayList<>(capacity);
                        List<DatabaseLoader.UserInventoryItem> cachedList = new ArrayList<>(capacity);
                        
                        for (com.vn.jet.mosco.model.UserCard userCard : responseCards) {
                            DatabaseLoader.UserInventoryItem cachedItem = DatabaseLoader.UserInventoryItem.fromUserCard(userCard);
                            displayItems.add(CardDisplayItem.fromCacheItem(cachedItem));
                            cachedList.add(cachedItem);
                        }
                        DatabaseLoader.cachedUserInventory = cachedList;

                        if (getActivity() != null && isAdded()) {
                            getActivity().runOnUiThread(() -> {
                                if (loaderLottie != null) {
                                    loaderLottie.cancelAnimation();
                                    loaderLottie.setVisibility(View.GONE);
                                }
                                if (displayItems.isEmpty()) {
                                    rvInventory.setVisibility(View.GONE);
                                    layoutEmptyState.setVisibility(View.VISIBLE);
                                    originalObjets = new ArrayList<>();
                                } else {
                                    rvInventory.setVisibility(View.VISIBLE);
                                    layoutEmptyState.setVisibility(View.GONE);
                                    originalObjets = displayItems;

                                    busyIds.clear();
                                    for (CardDisplayItem item : displayItems) {
                                        if (!isShowcaseMode && item.getStatus() != null && !"AVAILABLE".equalsIgnoreCase(item.getStatus())) {
                                            busyIds.add(item.getId());
                                        }
                                    }
                                }
                                applyFilters();
                                updateDisabledStates();
                            });
                        }
                    }).start();
                } else {
                    if (loaderLottie != null) {
                        loaderLottie.cancelAnimation();
                        loaderLottie.setVisibility(View.GONE);
                    }
                    if (adapter != null) adapter.setLoading(false);
                    rvInventory.setVisibility(View.GONE);
                    layoutEmptyState.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<List<com.vn.jet.mosco.model.UserCard>> call, Throwable t) {
                if (loaderLottie != null) {
                    loaderLottie.cancelAnimation();
                    loaderLottie.setVisibility(View.GONE);
                }
                if (adapter != null) adapter.setLoading(false);
                rvInventory.setVisibility(View.GONE);
                layoutEmptyState.setVisibility(View.VISIBLE);
            }
        });
    }

    private Set<Long> getSelectedIds() {
        Set<Long> ids = new HashSet<>();
        for (CardDisplayItem item : selectedMaterials) {
            ids.add(item.getId());
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

    public void applyFilters() {
        filterHandler.removeCallbacks(filterRunnable);
        filterHandler.postDelayed(filterRunnable, 150);
    }

    private void executeApplyFilters() {
        if (originalObjets == null || !isAdded()) return;
        
        // Show Skeleton during filter/sort processing
        if (adapter != null) adapter.setLoading(true);

        filterExecutor.execute(() -> {
            // Artificial delay for "Quiet Luxury" shimmer feel
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}

            if (!isAdded()) {
                isApplyingFilter = false;
                return;
            }
            
            List<CardDisplayItem> filtered = new ArrayList<>();
            if (originalObjets == null || originalObjets.isEmpty()) {
                filtered.addAll(new ArrayList<>());
            } else {
                String currentSort = currentSortOption;
                
                java.util.Set<String> selArtists = new java.util.HashSet<>();
                java.util.Set<String> selClasses = new java.util.HashSet<>();
                java.util.Set<String> selSeasons = new java.util.HashSet<>();

                for (String f : objetFilter) {
                    if (DatabaseLoader.isArtist(f)) {
                        selArtists.add(f.toLowerCase());
                    } else if (DatabaseLoader.isClass(f)) {
                        selClasses.add(f.toLowerCase());
                    } else {
                        selSeasons.add(f.toLowerCase());
                    }
                }

                for (CardDisplayItem item : originalObjets) {
                if (objetFilter.isEmpty()) {
                    filtered.add(item);
                    continue;
                }
                String member = item.getMember();
                String cardClass = item.getCardClass();
                String season = item.getSeason();
                
                // Chuẩn hóa Class Key
                String mappedClass = mapClassToTypeKey(cardClass);
                
                boolean matchArtist = selArtists.isEmpty() || (member != null && selArtists.contains(member.toLowerCase()));
                boolean matchClass = selClasses.isEmpty() || (cardClass != null && selClasses.contains(cardClass.toLowerCase())) || (mappedClass != null && selClasses.contains(mappedClass.toLowerCase()));
                boolean matchSeason = selSeasons.isEmpty() || (season != null && selSeasons.contains(season.toLowerCase()));

                if (matchArtist && matchClass && matchSeason) {
                    filtered.add(item);
                }
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
                else if (SORT_OPTIONS[4].equals(currentSort)) { // Status
                    boolean b1 = busyIds.contains(a.getId());
                    boolean b2 = busyIds.contains(b.getId());
                    result = Boolean.compare(b1, b2); // Thẻ rảnh (Available) hiện lên trước
                }
                else if ("Class".equals(currentSort)) {
                    int rankA = getCardClassRank(a.getCardClass());
                    int rankB = getCardClassRank(b.getCardClass());
                    if (rankA != rankB) {
                        result = Integer.compare(rankA, rankB);
                    } else {
                        String s1 = a.getSeason() != null ? a.getSeason() : "";
                        String s2 = b.getSeason() != null ? b.getSeason() : "";
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
                    String s1 = a.getSeason() != null ? a.getSeason() : "";
                    String s2 = b.getSeason() != null ? b.getSeason() : "";
                    int seasonComp = s1.compareToIgnoreCase(s2);
                    if (seasonComp != 0) {
                        result = seasonComp;
                    } else {
                        int rankA = getCardClassRank(a.getCardClass());
                        int rankB = getCardClassRank(b.getCardClass());
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
        }

            if (getActivity() != null && isAdded()) {
                getActivity().runOnUiThread(() -> {
                    if (adapter != null) {
                        adapter.updateData(filtered);
                        if (isMultiSelect) {
                            adapter.setSelectedIds(getSelectedIds());
                            updateDisabledStates();
                        }
                    }
                    updateItemCount(getView(), filtered.size());
                    
                    if (layoutEmptyState != null) {
                        if (rvInventory != null) rvInventory.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
                    }
                    isApplyingFilter = false;
                });
            } else {
                isApplyingFilter = false;
            }
        });
    }

    private String mapClassToTypeKey(String cardClass) {
        return DatabaseLoader.mapClassToTypeKey(cardClass);
    }

    private double calculateCurrentProgress() {
        if (!isMultiSelect || mainCard == null || upgradeAlgorithm == null) return 0.0;
        List<UpgradeAlgorithm.Card> algoMaterials = new ArrayList<>();
        for (CardDisplayItem mc : selectedMaterials) {
            UpgradeAlgorithm.Card c = new UpgradeAlgorithm.Card();
            c.id = String.valueOf(mc.getId());
            c.typeKey = mapClassToTypeKey(mc.getCardClass());
            c.level = mc.getUpgradeLevel();
            c.ovr = mc.getOvr();
            algoMaterials.add(c);
        }

        UpgradeAlgorithm.Card target = new UpgradeAlgorithm.Card();
        target.id = String.valueOf(mainCard.getId());
        target.typeKey = mapClassToTypeKey(mainCard.getCardClass());
        target.level = mainCard.getUpgradeLevel();
        target.ovr = mainCard.getOvr();

        return upgradeAlgorithm.calculateFillPercent(target, algoMaterials);
    }

    /**
     * Cập nhật văn bản hiển thị trên nút Xác nhận (Confirm) kèm theo tiến độ hoặc số lượng
     */
    private void updateConfirmButtonText() {
        if (btnConfirm == null) return;
        if (isSquadMode) {
            // Hiển thị số lượng thẻ đã chọn trong chế độ lập đội hình
            btnConfirm.setText(String.format("Confirm (%d/%d)", selectedMaterials.size(), maxSelectionCount));
            return;
        }
        double percent = calculateCurrentProgress();
        if (selectedMaterials.isEmpty()) {
            btnConfirm.setText(getString(R.string.action_confirm));
        } else {
            // Hiển thị phần trăm tỉ lệ thành công khi đập thẻ
            btnConfirm.setText(String.format("Confirm (%.1f%%)", percent));
        }
    }

    private void updateDisabledStates() {
        if (adapter == null) return;
        java.util.Set<String> disabledMembers = new java.util.HashSet<>();
        if (isSquadMode) {
            for (CardDisplayItem item : selectedMaterials) {
                if (item.getMember() != null) {
                    disabledMembers.add(item.getMember().trim().toLowerCase());
                }
            }
        }
        adapter.setDisabledStates(busyIds, disabledMembers);
    }

    private int getCardClassRank(String cardClass) {
        return DatabaseLoader.getCardClassRank(cardClass);
    }

    private void quickPickTeam() {
        if (!isSquadMode || originalObjets == null) return;
        
        // Show Skeleton for Luxury Feel
        if (adapter != null) adapter.setLoading(true);

        new Thread(() -> {
            // Artificial delay to let user see the "thinking" process
            try { Thread.sleep(400); } catch (InterruptedException ignored) {}

            selectedMaterials.clear();
            List<CardDisplayItem> pool = new ArrayList<>(originalObjets);
            
            // Sort by Card Class desc, Season desc, OVR desc, Level desc, ID desc
            pool.sort((a, b) -> {
                int rankA = getCardClassRank(a.getCardClass());
                int rankB = getCardClassRank(b.getCardClass());
                if (rankA != rankB) return Integer.compare(rankB, rankA);
                
                String seasonA = a.getSeason() != null ? a.getSeason() : "";
                String seasonB = b.getSeason() != null ? b.getSeason() : "";
                int seasonComp = seasonB.compareToIgnoreCase(seasonA);
                if (seasonComp != 0) return seasonComp;

                int ovrComp = Integer.compare(b.getOvr(), a.getOvr());
                if (ovrComp != 0) return ovrComp;
                
                int lvlComp = Integer.compare(b.getLevel(), a.getLevel());
                if (lvlComp != 0) return lvlComp;
                
                return Long.compare(b.getId(), a.getId());
            });
            
            for (CardDisplayItem item : pool) {
                if (selectedMaterials.size() >= maxSelectionCount) break;
                
                // Check if busy
                if (busyIds.contains(item.getId())) continue;
                
                // Check artist collision
                if (!isArtistCollision(item)) {
                    selectedMaterials.add(item);
                }
            }

            if (getActivity() != null && isAdded()) {
                getActivity().runOnUiThread(() -> {
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
                });
            }
        }).start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (filterExecutor != null) {
            filterExecutor.shutdownNow();
        }
    }
}


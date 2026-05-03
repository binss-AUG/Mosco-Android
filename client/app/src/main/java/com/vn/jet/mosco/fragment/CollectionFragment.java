package com.vn.jet.mosco.fragment;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.utils.NumberUtils;


import android.widget.Button;
import android.widget.GridLayout;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class CollectionFragment extends Fragment {

    private TabLayout tabLayout;
    private ViewPager2 viewPager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_collection, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Nạp Master Data (database.json) ở background sớm để Album/Objets mượt mà
        com.vn.jet.mosco.utils.DatabaseLoader.initMasterData(requireContext());

        tabLayout = view.findViewById(R.id.tab_layout_collection);
        viewPager = view.findViewById(R.id.view_pager_collection);

        View btnShop = view.findViewById(R.id.btn_shop);
        if (btnShop != null) {
            btnShop.setOnClickListener(v -> {
                if (getActivity() != null) {
                    getActivity().getSupportFragmentManager().beginTransaction()
                            .replace(R.id.frame_layout, new ShopFragment())
                            .addToBackStack(null)
                            .commit();
                }
            });
        }

        CollectionPagerAdapter adapter = new CollectionPagerAdapter(this);
        viewPager.setAdapter(adapter);
        viewPager.setUserInputEnabled(false);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0: tab.setText("Album"); break;
                case 1: tab.setText("Mailbox"); break;
                case 2: tab.setText("Objets"); break;
                case 3: tab.setText("Items"); break;
            }
        }).attach();

        // Check if a specific tab was requested (e.g. from Shop Success Dialog)
        if (getArguments() != null) {
            int defaultTab = getArguments().getInt("default_tab", 0);
            viewPager.setCurrentItem(defaultTab, false);
        }
    }

    // ==========================================
    // SHARED HELPER: Sort Dropdown (custom popup)
    // ==========================================
    
    public static void showObjetDetailDialog(Context context, String imageUrl) {
        showObjetDetailDialog(context, imageUrl, null, 1, 0, 1);
    }

    public static void showObjetDetailDialog(Context context, String imageUrl, org.json.JSONObject cardJson) {
        showObjetDetailDialog(context, imageUrl, cardJson, 1, 0, 1);
    }

    /**
     * Hiển thị hộp thoại chi tiết Thẻ bài với tùy chọn liên kết dữ liệu JSON.
     */
    public static void showObjetDetailDialog(Context context, String imageUrl, org.json.JSONObject cardJson, int level, int exp, int upgrade) {
        android.app.Dialog dialog = new android.app.Dialog(context);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_objet_detail);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            // MATCH_PARENT — the card's own margin (12dp) creates the visual inset.
            // No scroll: ConstraintLayout distributes space automatically.
            dialog.getWindow().setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
            );
        }

        // ── Dynamic binding from JSON (Task 2) ────────────────────────
        if (cardJson != null) {
            // Thư viện thẻ bài (Collection) hiển thị theo chỉ số thực tế của thẻ
            com.vn.jet.mosco.utils.ObjetDetailBinder.bind(dialog, context, cardJson, level, exp, upgrade);
        } else {
            // Fallback: load imageUrl directly via Glide
            ImageView ivObjet = dialog.findViewById(R.id.iv_objet_detail_image);
            if (ivObjet != null) {
                Glide.with(context)
                        .load(imageUrl.isEmpty() ? R.drawable.item_shop_demo : imageUrl)
                        .placeholder(R.drawable.item_shop_demo)
                        .into(ivObjet);
            }
        }

        // ── Nút: Đóng ──────────────────────────────────────────────
        View btnClose = dialog.findViewById(R.id.btn_close_detail);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        // ── Button: Recycle / Refresh ────────────────────────────────────
        View btnRecycle = dialog.findViewById(R.id.btn_recycle_detail);
        if (btnRecycle != null) {
            btnRecycle.setOnClickListener(v -> {
                new androidx.appcompat.app.AlertDialog.Builder(context)
                        .setTitle("Làm mới thẻ")
                        .setMessage("Bạn có chắc chắn muốn làm mới dữ liệu của thẻ này không?")
                        .setPositiveButton("Chắc chắn", (d, w) -> {
                            if (cardJson != null) {
                                String slug = cardJson.optString("slug", "");
                                if (!slug.isEmpty()) {
                                    // Reload data from DatabaseLoader
                                    org.json.JSONObject refreshedCard = com.vn.jet.mosco.utils.DatabaseLoader.findBySlug(context, slug);
                                    if (refreshedCard != null) {
                                        com.vn.jet.mosco.utils.ObjetDetailBinder.bind(dialog, context, refreshedCard, level, exp, upgrade);
                                        android.widget.Toast.makeText(context, "Làm mới thành công!", android.widget.Toast.LENGTH_SHORT).show();
                                    } else {
                                        android.widget.Toast.makeText(context, "Lỗi: Không tìm thấy thẻ", android.widget.Toast.LENGTH_SHORT).show();
                                    }
                                }
                            }
                        })
                        .setNegativeButton("Hủy", (d, w) -> d.dismiss())
                        .show();
            });
        }

        // ── Nút: Nâng cấp Level (Outlined / Secondary) ────────────────────
        View btnLevelUp = dialog.findViewById(R.id.btn_level_up_detail);
        if (btnLevelUp != null) {
            btnLevelUp.setOnClickListener(v -> {
                Toast.makeText(context, "Level Up clicked", Toast.LENGTH_SHORT).show();
            });
        }

        // ── Nút: NÂNG CẤP (Primary — giữ nguyên logic cũ) ─────────────
        View btnUpgrade = dialog.findViewById(R.id.btn_upgrade_detail);
        if (btnUpgrade != null) {
            btnUpgrade.setOnClickListener(v -> {
                Toast.makeText(context, "Upgrade clicked", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        }

        dialog.show();
    }


    public static void setupSortDropdown(
            View sortBtn, ImageView arrowIcon, TextView labelView,
            String[] options, LinearLayout dropdownContainer, Runnable onSortChanged) {

        final boolean[] isOpen = {false};

        sortBtn.setOnClickListener(v -> {
            if (isOpen[0]) {
                dropdownContainer.setVisibility(View.GONE);
                if (arrowIcon != null) arrowIcon.setImageResource(R.drawable.ic_arrow_up);
                else if (sortBtn instanceof TextView) ((TextView) sortBtn).setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_up, 0);
                isOpen[0] = false;
                return;
            }

            // Build dropdown items
            dropdownContainer.removeAllViews();
            LayoutInflater inf = LayoutInflater.from(v.getContext());
            int[] loc = new int[2];
            sortBtn.getLocationInWindow(loc);

            String currentLabel = labelView != null ? labelView.getText().toString() : 
                                 (sortBtn instanceof TextView ? ((TextView) sortBtn).getText().toString() : "");

            for (String opt : options) {
                TextView item = (TextView) inf.inflate(R.layout.item_sort_option, dropdownContainer, false);
                item.setText(opt);
                // Highlight currently selected
                if (opt.equals(currentLabel)) {
                    item.setTextColor(0xFF8A2BE2);
                    item.setTypeface(null, android.graphics.Typeface.BOLD);
                }
                item.setOnClickListener(sel -> {
                    if (labelView != null) labelView.setText(opt);
                    else if (sortBtn instanceof TextView) ((TextView) sortBtn).setText(opt);
                    
                    // Reset styles
                    for (int i = 0; i < dropdownContainer.getChildCount(); i++) {
                        View child = dropdownContainer.getChildAt(i);
                        if (child instanceof TextView) {
                            ((TextView) child).setTextColor(Color.WHITE);
                            ((TextView) child).setTypeface(null, android.graphics.Typeface.NORMAL);
                        }
                    }
                    item.setTextColor(0xFF8A2BE2);
                    dropdownContainer.setVisibility(View.GONE);
                    if (arrowIcon != null) arrowIcon.setImageResource(R.drawable.ic_arrow_up);
                    else if (sortBtn instanceof TextView) ((TextView) sortBtn).setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_up, 0);
                    isOpen[0] = false;
                    
                    if (onSortChanged != null) onSortChanged.run();
                });
                dropdownContainer.addView(item);
            }

            dropdownContainer.setVisibility(View.VISIBLE);
            if (arrowIcon != null) arrowIcon.setImageResource(R.drawable.ic_arrow_down);
            else if (sortBtn instanceof TextView) ((TextView) sortBtn).setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_down, 0);
            isOpen[0] = true;

            // Position below the sort button
            CoordinatorLayout_setDropdownPosition(dropdownContainer, sortBtn);
        });
    }

    private static void CoordinatorLayout_setDropdownPosition(View dropdown, View anchor) {
        int[] loc = new int[2];
        anchor.getLocationInWindow(loc);
        
        ViewGroup parent = (ViewGroup) dropdown.getParent();
        int[] parentLoc = new int[2];
        if (parent != null) {
            parent.getLocationInWindow(parentLoc);
        }
        
        int relativeY = loc[1] - parentLoc[1];
        int relativeX = loc[0] - parentLoc[0];

        ViewGroup.LayoutParams genericLp = dropdown.getLayoutParams();
        if (genericLp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) genericLp;
            lp.topMargin = relativeY + anchor.getHeight() + dpToPx(anchor.getContext(), 8); // small 8dp gap
            lp.leftMargin = relativeX;
            dropdown.setLayoutParams(lp);
        }
    }

    // ==========================================
    // SHARED HELPER: Filter BottomSheet
    // ==========================================
    public static class FilterCategory {
        public final String tabName;
        public final List<String> items;
        public final boolean isArtistGrid; // shows avatar circle grid

        public FilterCategory(String tabName, List<String> items, boolean isArtistGrid) {
            this.tabName = tabName;
            this.items = items;
            this.isArtistGrid = isArtistGrid;
        }
    }

    public static void showFilterBottomSheet(
            Fragment fragment,
            List<FilterCategory> categories,
            int initialTabIndex,
            Set<String> currentSelections,
            Runnable onFilterApplied) {

        Context ctx = fragment.requireContext();
        BottomSheetDialog dialog = new BottomSheetDialog(ctx, R.style.CustomBottomSheetDialogTheme);
        View bsView = LayoutInflater.from(ctx).inflate(R.layout.layout_bottom_sheet_objet_filter, null);
        
        android.widget.FrameLayout wrapper = new android.widget.FrameLayout(ctx) {
            @Override
            public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
                View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
                if (sheet != null) {
                    BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
                    int action = ev.getActionMasked();
                    if (action == android.view.MotionEvent.ACTION_DOWN) {
                        float y = ev.getY();
                        View tabs = bsView.findViewById(R.id.tab_filter_categories);
                        if (tabs != null && y > tabs.getBottom()) {
                            behavior.setDraggable(false);
                            getParent().requestDisallowInterceptTouchEvent(true);
                        } else {
                            behavior.setDraggable(true);
                        }
                    } else if (action == android.view.MotionEvent.ACTION_UP || action == android.view.MotionEvent.ACTION_CANCEL) {
                        behavior.setDraggable(true);
                        getParent().requestDisallowInterceptTouchEvent(false);
                    }
                }
                return super.dispatchTouchEvent(ev);
            }
        };
        wrapper.addView(bsView, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        dialog.setContentView(wrapper);

        // Configure 3-state behavior:
        // HIDDEN → COLLAPSED (half, default open state) → EXPANDED (full)
        dialog.setOnShowListener(di -> {
            View sheet = ((BottomSheetDialog) di)
                    .findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet == null) return;

            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);

            // peekHeight = ~50% screen height for the "half" state
            int screenHeight = ctx.getResources().getDisplayMetrics().heightPixels;
            int peekH = (int) (screenHeight * 0.50f);
            behavior.setPeekHeight(peekH, false);

            // Start at COLLAPSED (half screen)
            behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

            // Allow drag-down to HIDDEN from both COLLAPSED and EXPANDED
            behavior.setHideable(true);
            behavior.setSkipCollapsed(false); // must pass through COLLAPSED on the way down
            behavior.setDraggable(true);

            // Make the sheet fill max height when EXPANDED
            sheet.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
            sheet.requestLayout();

            // Setup callback to keep bottom actions pinned to bottom of screen in COLLAPSED state
            BottomSheetBehavior.BottomSheetCallback slideCallback = new BottomSheetBehavior.BottomSheetCallback() {
                @Override
                public void onStateChanged(@NonNull View bottomSheet, int newState) {
                    updatePinnedActions(bottomSheet, bsView, ctx);
                }
                @Override
                public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                    if (slideOffset >= 0) {
                        updatePinnedActions(bottomSheet, bsView, ctx);
                    }
                }
            };
            behavior.addBottomSheetCallback(slideCallback);
            sheet.post(() -> updatePinnedActions(sheet, bsView, ctx));
        });


        TabLayout tabLayout = bsView.findViewById(R.id.tab_filter_categories);
        android.widget.FrameLayout flFilterContent = bsView.findViewById(R.id.fl_filter_content);
        LinearLayout llChips = bsView.findViewById(R.id.ll_selected_chips);

        // A local working set so we can "Clear" without touching the original until Apply
        Set<String> workingSet = new LinkedHashSet<>(currentSelections);

        // Build tabs
        for (FilterCategory cat : categories) {
            tabLayout.addTab(tabLayout.newTab().setText(cat.tabName));
        }

        // Chip renderer
        Runnable renderChips = new Runnable() {
            @Override
            public void run() {
                llChips.removeAllViews();
                for (String selected : new ArrayList<>(workingSet)) {
                    TextView chip = new TextView(ctx);
                    chip.setText(selected + " ×");
                    chip.setTextColor(Color.WHITE);
                    chip.setTextSize(13f);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                    lp.setMarginEnd(dpToPx(ctx, 20));
                    chip.setLayoutParams(lp);
                    chip.setOnClickListener(v2 -> {
                        workingSet.remove(selected);
                        this.run();
                        // Also visually deselect in content
                        rebuildContent(ctx, flFilterContent, categories,
                                tabLayout.getSelectedTabPosition(), workingSet, this);
                        View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
                        if (sheet != null) updatePinnedActions(sheet, bsView, ctx);
                    });
                    llChips.addView(chip);
                }
            }
        };

        // Content builder (called on tab switch)
        buildContentForTab(ctx, flFilterContent, categories, initialTabIndex, workingSet, renderChips);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                buildContentForTab(ctx, flFilterContent, categories,
                        tab.getPosition(), workingSet, renderChips);
                View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
                if (sheet != null) updatePinnedActions(sheet, bsView, ctx);
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        // Render initial chips
        renderChips.run();

        // Select initial tab
        if (initialTabIndex < categories.size()) {
            TabLayout.Tab t = tabLayout.getTabAt(initialTabIndex);
            if (t != null) t.select();
        }

        bsView.findViewById(R.id.btn_filter_apply).setOnClickListener(v -> {
            currentSelections.clear();
            currentSelections.addAll(workingSet);
            dialog.dismiss();
            if (onFilterApplied != null) onFilterApplied.run();
        });
        bsView.findViewById(R.id.btn_filter_clear).setOnClickListener(v -> {
            workingSet.clear();
            renderChips.run();
            buildContentForTab(ctx, flFilterContent, categories,
                    tabLayout.getSelectedTabPosition(), workingSet, renderChips);
            View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) updatePinnedActions(sheet, bsView, ctx);
        });

        dialog.show();
    }

    private static void updatePinnedActions(View bottomSheet, View bsView, Context ctx) {
        ViewGroup parent = (ViewGroup) bottomSheet.getParent();
        if (parent == null) return;
        int parentHeight = parent.getHeight();
        int offScreenAmount = bottomSheet.getHeight() + bottomSheet.getTop() - parentHeight;
        if (offScreenAmount < 0) offScreenAmount = 0;
        
        View actions = bsView.findViewById(R.id.layout_filter_actions);
        if (actions != null) actions.setTranslationY(-offScreenAmount);
        
        View flContent = bsView.findViewById(R.id.fl_filter_content);
        if (flContent instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) flContent;
            if (vg.getChildCount() > 0) {
                View sv = vg.getChildAt(0);
                if (sv != null) {
                    sv.setPadding(sv.getPaddingLeft(), sv.getPaddingTop(), sv.getPaddingRight(), dpToPx(ctx, 8) + offScreenAmount);
                }
            }
        }
    }

    private static void rebuildContent(Context ctx, android.widget.FrameLayout fl,
            List<FilterCategory> categories, int tabIndex,
            Set<String> workingSet, Runnable renderChips) {
        buildContentForTab(ctx, fl, categories, tabIndex, workingSet, renderChips);
    }

    private static void buildContentForTab(Context ctx, android.widget.FrameLayout fl,
            List<FilterCategory> categories, int tabIndex,
            Set<String> workingSet, Runnable renderChips) {
        fl.removeAllViews();
        if (tabIndex < 0 || tabIndex >= categories.size()) return;
        FilterCategory cat = categories.get(tabIndex);

        if (cat.isArtistGrid) {
            buildArtistGrid(ctx, fl, cat.items, workingSet, renderChips);
        } else {
            buildTwoColumnList(ctx, fl, cat.items, workingSet, renderChips);
        }
    }

    /** Artist grid: 3-column circles */
    private static void buildArtistGrid(Context ctx, android.widget.FrameLayout fl,
            List<String> items, Set<String> workingSet, Runnable renderChips) {
        ScrollView sv = new ScrollView(ctx);
        sv.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        sv.setPadding(dpToPx(ctx, 8), dpToPx(ctx, 8), dpToPx(ctx, 8), dpToPx(ctx, 8));
        sv.setNestedScrollingEnabled(false); // disable nested scrolling so BottomSheet doesn't react
        GridLayout grid = new GridLayout(ctx);
        grid.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        grid.setColumnCount(3);

        for (String name : items) {
            View cell = buildArtistCell(ctx, name, workingSet, renderChips);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            lp.width = 0;
            lp.setMargins(dpToPx(ctx, 4), dpToPx(ctx, 8), dpToPx(ctx, 4), dpToPx(ctx, 8));
            cell.setLayoutParams(lp);
            grid.addView(cell);
        }

        sv.addView(grid);
        fl.addView(sv);
    }

    private static View buildArtistCell(Context ctx, String name, Set<String> workingSet, Runnable renderChips) {
        LinearLayout cell = new LinearLayout(ctx);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);

        // Avatar card
        MaterialCardView card = new MaterialCardView(ctx);
        int size = dpToPx(ctx, 76);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(size, size);
        card.setLayoutParams(cardLp);
        card.setRadius(size / 2f);
        card.setStrokeColor(Color.parseColor("#8A2BE2"));
        card.setCardBackgroundColor(Color.parseColor("#3A3A55"));
        card.setStrokeWidth(workingSet.contains(name) ? dpToPx(ctx, 2) : 0);
        // Add purple overlay if selected
        if (workingSet.contains(name)) {
            card.setAlpha(0.9f);
        }

        ImageView iv = new ImageView(ctx);
        iv.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setImageResource(R.drawable.item_shop_demo); // placeholder: replace with actual resource
        card.addView(iv);

        // Name label
        TextView label = new TextView(ctx);
        label.setText(name);
        label.setTextSize(11f);
        label.setTextColor(workingSet.contains(name) ? Color.WHITE : Color.parseColor("#A2A2A7"));
        label.setTypeface(null, workingSet.contains(name) ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dpToPx(ctx, 6);
        label.setLayoutParams(tlp);

        cell.addView(card);
        cell.addView(label);

        cell.setOnClickListener(v -> {
            if (workingSet.contains(name)) {
                workingSet.remove(name);
                card.setStrokeWidth(0);
                card.setAlpha(1f);
                label.setTextColor(Color.parseColor("#A2A2A7"));
                label.setTypeface(null, android.graphics.Typeface.NORMAL);
            } else {
                workingSet.add(name);
                card.setStrokeWidth(dpToPx(ctx, 2));
                card.setAlpha(0.9f);
                label.setTextColor(Color.WHITE);
                label.setTypeface(null, android.graphics.Typeface.BOLD);
            }
            renderChips.run();
        });

        return cell;
    }

    /** Season/Class: 2-column card list */
    private static void buildTwoColumnList(Context ctx, android.widget.FrameLayout fl,
            List<String> items, Set<String> workingSet, Runnable renderChips) {
        ScrollView sv = new ScrollView(ctx);
        sv.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        sv.setNestedScrollingEnabled(false); // disable nested scrolling so BottomSheet doesn't react
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.setPadding(dpToPx(ctx, 12), dpToPx(ctx, 12), dpToPx(ctx, 12), dpToPx(ctx, 12));

        // Build rows of 2
        for (int i = 0; i < items.size(); i += 2) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setWeightSum(2f);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = dpToPx(ctx, 10);
            row.setLayoutParams(rowLp);

            String name1 = items.get(i);
            row.addView(buildListCard(ctx, name1, workingSet, renderChips, true));

            if (i + 1 < items.size()) {
                String name2 = items.get(i + 1);
                row.addView(buildListCard(ctx, name2, workingSet, renderChips, false));
            } else {
                // Empty placeholder
                View placeholder = new View(ctx);
                LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                placeholder.setLayoutParams(plp);
                row.addView(placeholder);
            }

            root.addView(row);
        }

        sv.addView(root);
        fl.addView(sv);
    }

    private static View buildListCard(Context ctx, String name, Set<String> workingSet,
            Runnable renderChips, boolean isLeft) {
        TextView card = new TextView(ctx);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dpToPx(ctx, 48), 1f);
        if (isLeft) lp.rightMargin = dpToPx(ctx, 6);
        else lp.leftMargin = dpToPx(ctx, 6);
        card.setLayoutParams(lp);

        boolean selected = workingSet.contains(name);
        card.setBackground(ctx.getDrawable(R.drawable.bg_button));
        card.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                selected ? Color.parseColor("#6B2FD4") : Color.parseColor("#41455E")));
        card.setTextColor(selected ? Color.WHITE : Color.parseColor("#A2A2A7"));
        card.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        card.setText(name);
        card.setTextSize(14f);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dpToPx(ctx, 16), 0, dpToPx(ctx, 16), 0);

        card.setOnClickListener(v -> {
            if (workingSet.contains(name)) {
                workingSet.remove(name);
                card.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#41455E")));
                card.setTextColor(Color.parseColor("#A2A2A7"));
                card.setTypeface(null, android.graphics.Typeface.NORMAL);
            } else {
                workingSet.add(name);
                card.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#6B2FD4")));
                card.setTextColor(Color.WHITE);
                card.setTypeface(null, android.graphics.Typeface.BOLD);
            }
            renderChips.run();
        });

        return card;
    }

    private static int dpToPx(Context ctx, int dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }

    // ==========================================
    // DEMO DATA
    // ==========================================
    public static List<FilterCategory> buildObjetCategories(Context context) {
        List<String> artists = java.util.Arrays.asList(
            "SeoYeon", "HyeRin", "JiWoo", "ChaeYeon", "YooYeon", "SooMin", "NaKyoung", "YuBin", 
            "Kaede", "DaHyun", "Kotone", "YeonJi", "Nien", "SoHyun", "Xinyu", "Mayu", 
            "Lynn", "JooBin", "HaYeon", "ShiOn", "ChaeWon", "Sullin", "SeoAh", "JiYeon"
        );

        List<org.json.JSONObject> cards = com.vn.jet.mosco.utils.DatabaseLoader.loadAllCards(context);
        java.util.Set<String> seasonsSet = new java.util.LinkedHashSet<>();
        for (org.json.JSONObject card : cards) {
            String season = card.optString("season", "");
            if (!season.isEmpty()) seasonsSet.add(season);
        }

        List<String> seasons = new ArrayList<>(seasonsSet);
        // Tách biệt các class thẻ bài theo yêu cầu mới
        List<String> classes = java.util.Arrays.asList("First", "Welcome", "Double", "Premier", "Special", "Unit");

        List<FilterCategory> cats = new ArrayList<>();
        cats.add(new FilterCategory("Artist", artists, true));
        cats.add(new FilterCategory("Season", seasons, false));
        cats.add(new FilterCategory("Class", classes, false));
        return cats;
    }

    public static List<FilterCategory> buildAlbumCategories(Context context) {
        List<FilterCategory> cats = buildObjetCategories(context);
        List<String> statuses = java.util.Arrays.asList("All", "Owned", "Missing");
        cats.add(0, new FilterCategory("Status", statuses, false));
        return cats;
    }

    private static List<FilterCategory> buildMailboxCategories() {
        List<String> types = new ArrayList<>();
        for (String s : new String[]{"Pack", "Objet", "Item"}) types.add(s);
        List<FilterCategory> cats = new ArrayList<>();
        cats.add(new FilterCategory("Type", types, false));
        return cats;
    }

    private static List<FilterCategory> buildItemsCategories() {
        List<String> types = new ArrayList<>();
        for (String s : new String[]{"Materials", "Consumables", "Equipments"}) types.add(s);
        List<FilterCategory> cats = new ArrayList<>();
        cats.add(new FilterCategory("Category", types, false));
        return cats;
    }

    // ==========================================
    // PAGER ADAPTER
    // ==========================================
    private static class CollectionPagerAdapter extends FragmentStateAdapter {
        public CollectionPagerAdapter(@NonNull Fragment fragment) { super(fragment); }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0: return new AlbumFragment();
                case 1: return new MailboxFragment();
                case 2: return new ObjetsFragment();
                case 3: return new ItemsFragment();
                default: return new AlbumFragment();
            }
        }

        @Override
        public int getItemCount() { return 4; }
    }

    // ==========================================
    // TAB 1: MAILBOX
    // ==========================================
    public static class MailboxFragment extends Fragment {
        private final Set<String> mailboxFilter = new LinkedHashSet<>();
        private final String[] SORT_OPTIONS = {"Newest", "Oldest", "Lowest No.", "Highest No."};
        private MailboxAdapter adapter;
        private List<com.vn.jet.mosco.model.UserMail> originalMails = new ArrayList<>();

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_collection_mailbox, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            View sortBtn = view.findViewById(R.id.btn_sort_mailbox);
            LinearLayout dropdown = view.findViewById(R.id.dropdown_sort_mailbox);

            setupSortDropdown(sortBtn, null, null, SORT_OPTIONS, dropdown, this::applyFilters);

            // Filter
            View filterBtn = view.findViewById(R.id.btn_filter_mailbox);
            filterBtn.setOnClickListener(v ->
                showFilterBottomSheet(this, buildMailboxCategories(), 0, mailboxFilter, this::applyFilters));

            // RecyclerView
            RecyclerView rvMailbox = view.findViewById(R.id.rv_mailbox);
            rvMailbox.setLayoutManager(new LinearLayoutManager(getContext()));

            adapter = new MailboxAdapter(new ArrayList<>(), this::onMailClicked);
            rvMailbox.setAdapter(adapter);

            loadMailbox();
        }

        private void onMailClicked(com.vn.jet.mosco.model.UserMail mail) {
            String giftInfo = (mail.getItemCode() != null && mail.getQuantity() != null) 
                    ? "\n\n🎁 Quà tặng: " + mail.getItemCode() + " x" + NumberUtils.format(getContext(), mail.getQuantity()) 
                    : "";

            AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(), 
                    android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(mail.getTitle())
                    .setMessage(mail.getContent() + giftInfo);

            if (!mail.isReceived()) {
                builder.setPositiveButton("Nhận quà", (d, w) -> performClaim(mail));
                builder.setNegativeButton("Để sau", null);
            } else {
                builder.setPositiveButton("Đã nhận", null);
                builder.setNegativeButton("Đóng", null);
            }
            
            builder.show();
        }

        /**
         * Thực hiện gửi yêu cầu nhận quà lên Server.
         */
        private void performClaim(com.vn.jet.mosco.model.UserMail mail) {
            // Hiển thị Loading Dialog phong cách Galactic
            AlertDialog loading = new AlertDialog.Builder(requireContext())
                    .setMessage("Đang kết nối tới trung tâm điều khiển để nhận quà...")
                    .setCancelable(false)
                    .show();

            com.vn.jet.mosco.network.GameApiService apiService = 
                    com.vn.jet.mosco.network.ApiClient.getClient(requireContext())
                    .create(com.vn.jet.mosco.network.GameApiService.class);
            
            apiService.claimMail(mail.getId()).enqueue(new retrofit2.Callback<okhttp3.ResponseBody>() {
                @Override
                public void onResponse(retrofit2.Call<okhttp3.ResponseBody> call, 
                                     retrofit2.Response<okhttp3.ResponseBody> response) {
                    loading.dismiss();
                    if (response.isSuccessful()) {
                        // Cập nhật trạng thái local
                        mail.setReceived(true);
                        
                        // Hiển thị thông báo thành công cao cấp
                        new AlertDialog.Builder(requireContext())
                                .setTitle("THÀNH CÔNG!")
                                .setMessage("Chúc mừng sếp! Vật phẩm " + (mail.getItemCode() != null ? mail.getItemCode() : "") + " đã được chuyển vào kho đồ.")
                                .setPositiveButton("Tuyệt vời", (d, w) -> loadMailbox())
                                .show();
                    } else {
                        Toast.makeText(getContext(), "Lỗi hệ thống: Không thể nhận quà lúc này.", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(retrofit2.Call<okhttp3.ResponseBody> call, Throwable t) {
                    loading.dismiss();
                    Toast.makeText(getContext(), "Mất kết nối tới vệ tinh: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }

        private void loadMailbox() {
            Long userId = new com.vn.jet.mosco.utils.SessionManager(requireContext()).getUserId();
            if (userId == null) return;

            com.vn.jet.mosco.network.GameApiService apiService = com.vn.jet.mosco.network.ApiClient.getClient(requireContext()).create(com.vn.jet.mosco.network.GameApiService.class);
            apiService.getUserMails(userId).enqueue(new retrofit2.Callback<List<com.vn.jet.mosco.model.UserMail>>() {
                @Override
                public void onResponse(retrofit2.Call<List<com.vn.jet.mosco.model.UserMail>> call, retrofit2.Response<List<com.vn.jet.mosco.model.UserMail>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        originalMails = response.body();
                        applyFilters();
                    }
                }

                @Override
                public void onFailure(retrofit2.Call<List<com.vn.jet.mosco.model.UserMail>> call, Throwable t) {
                    Toast.makeText(getContext(), "Failed to load mailbox", Toast.LENGTH_SHORT).show();
                }
            });
        }

        private void applyFilters() {
            if (originalMails == null) return;
            View sortBtn = getView() != null ? getView().findViewById(R.id.btn_sort_mailbox) : null;
            String currentSort = (sortBtn instanceof TextView) ? ((TextView) sortBtn).getText().toString() : "Newest";
            
            List<com.vn.jet.mosco.model.UserMail> filtered = new ArrayList<>();
            for (com.vn.jet.mosco.model.UserMail m : originalMails) {
                // Chỉ hiển thị những thư CHƯA nhận quà để danh sách gọn gàng
                if (!m.isReceived()) {
                    if (mailboxFilter.isEmpty()) {
                        filtered.add(m);
                    } else {
                        // Tương lai: Lọc theo Type nếu sếp muốn
                        filtered.add(m);
                    }
                }
            }

            filtered.sort((a,b) -> {
                if ("Oldest".equals(currentSort)) return a.getId().compareTo(b.getId());
                if ("Lowest No.".equals(currentSort)) return Integer.compare(a.getQuantity() != null ? a.getQuantity() : 0, b.getQuantity() != null ? b.getQuantity() : 0);
                if ("Highest No.".equals(currentSort)) return Integer.compare(b.getQuantity() != null ? b.getQuantity() : 0, a.getQuantity() != null ? a.getQuantity() : 0);
                return b.getId().compareTo(a.getId());
            });

            if (adapter != null) adapter.updateData(filtered);
        }
    }

    interface OnMailClickListener {
        void onMailClick(com.vn.jet.mosco.model.UserMail mail);
    }

    private static class MailboxAdapter extends RecyclerView.Adapter<MailboxAdapter.ViewHolder> {
        private List<com.vn.jet.mosco.model.UserMail> list;
        private final OnMailClickListener listener;

        public MailboxAdapter(List<com.vn.jet.mosco.model.UserMail> list, OnMailClickListener listener) {
            this.list = list;
            this.listener = listener;
        }

        public void updateData(List<com.vn.jet.mosco.model.UserMail> newList) {
            this.list = newList;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_mailbox, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            com.vn.jet.mosco.model.UserMail mail = list.get(position);
            holder.tvTitle.setText(mail.getTitle());
            holder.tvQty.setText(mail.getQuantity() != null ? "x" + NumberUtils.format(holder.itemView.getContext(), mail.getQuantity()) : "");
            holder.tvDesc.setText(mail.getContent());
            
            // Format time if possible, or use raw
            holder.tvTime.setText(mail.getCreatedAt() != null ? mail.getCreatedAt().substring(0, 10) : "");
            
            holder.ivIcon.setImageResource(R.drawable.item_shop_demo);
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onMailClick(mail);
            });
        }

        @Override
        public int getItemCount() { return list.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivIcon;
            TextView tvTitle, tvQty, tvDesc, tvTime;

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

    // ==========================================
    // ==========================================
    // TAB 2: OBJETS (Data-Driven from Server & database.json)
    // ==========================================
    public static class ObjetsFragment extends Fragment {
        private final Set<String> objetFilter = new LinkedHashSet<>();
        private final String[] SORT_OPTIONS = {"Newest", "Oldest", "Highest OVR", "Lowest OVR", "Highest Level", "Lowest Level", "Highest Badge", "Lowest Badge"};
        private RecyclerView rvObjets;
        private TextView tvCount;
        private List<com.vn.jet.mosco.model.Objet> originalObjets = new ArrayList<>();

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_collection_objets, container, false);
        }

        @Override
        public void onResume() {
            super.onResume();
            com.vn.jet.mosco.utils.DatabaseLoader.registerInventoryChangeListener(inventoryChangeListener);
        }

        @Override
        public void onPause() {
            super.onPause();
            com.vn.jet.mosco.utils.DatabaseLoader.unregisterInventoryChangeListener(inventoryChangeListener);
        }

        private final com.vn.jet.mosco.utils.DatabaseLoader.OnInventoryChangeListener inventoryChangeListener = new com.vn.jet.mosco.utils.DatabaseLoader.OnInventoryChangeListener() {
            @Override
            public void onInventoryChanged() {
                if (getActivity() != null && isAdded()) {
                    getActivity().runOnUiThread(() -> {
                        loadObjets(false);
                    });
                }
            }
        };

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            tvCount = view.findViewById(R.id.tv_objet_types_count);

            // Filter button
            view.findViewById(R.id.btn_filter_objets).setOnClickListener(v ->
                showFilterBottomSheet(this, buildObjetCategories(requireContext()), 0, objetFilter, this::applyFilters));

            // Sort
            View sortBtn = view.findViewById(R.id.btn_sort_objets);
            LinearLayout dropdown = view.findViewById(R.id.dropdown_sort_objets);
            setupSortDropdown(sortBtn, null, null, SORT_OPTIONS, dropdown, this::applyFilters);

            // RecyclerView — load REAL data from server
            rvObjets = view.findViewById(R.id.rv_objets);
            rvObjets.setLayoutManager(new GridLayoutManager(getContext(), 3));

            rvObjets.setAdapter(new com.vn.jet.mosco.adapter.BaseInventoryAdapter(new ArrayList<>(), rvObjets, item -> {
                Context ctx = getContext();
                if (ctx == null) return;
                
                org.json.JSONObject cardJson = com.vn.jet.mosco.utils.DatabaseLoader.findById(ctx, item.getCollectionId());
                if (cardJson != null) {
                    // Áp dụng chung logic hiển thị Detail của Album (sử dụng CollectionDetailBinder) cho phần Tab Objets
                    com.vn.jet.mosco.model.CollectionEntry entry = new com.vn.jet.mosco.model.CollectionEntry();
                    entry.setCollectionId(item.getCollectionId());
                    entry.setFrontImage(item.getImageUrl());
                    entry.setOvr(item.getOvr());
                    entry.setLevel(item.getCardLevel());
                    entry.setMember(cardJson.optString("member"));
                    entry.setSeason(cardJson.optString("season"));
                    entry.setCardClass(cardJson.optString("class"));
                    entry.setCollectionNo(cardJson.optString("collectionNo"));
                    entry.setUserCardId(item.getIdString());
                    entry.setOwned(true);
                    
                    com.vn.jet.mosco.utils.CollectionDetailBinder.showDetail(ctx, entry);
                } else {
                    showObjetDetailDialog(ctx, item.getImageUrl());
                }
            }));

            loadObjets(false);
        }
        
        /**
         * Smart Load: Ưu tiên nạp từ cache để UI hiện lên TỨC THÌ (Instant Load).
         * @param forceFromServer Nếu true sẽ bỏ qua cache, nạp thẳng từ API.
         */
        private void loadObjets(boolean forceFromServer) {
            Long userId = new com.vn.jet.mosco.utils.SessionManager(requireContext()).getUserId();
            if (userId == null) return;

            // ── 1. KIỂM TRA CACHE (INSTANT LOAD) ─────────────────────────
            List<com.vn.jet.mosco.utils.DatabaseLoader.UserInventoryItem> cache = com.vn.jet.mosco.utils.DatabaseLoader.cachedUserInventory;
            if (!forceFromServer && cache != null && !cache.isEmpty()) {
                android.util.Log.d("ObjetsFragment", "Instant Load from Galactic Cache: " + cache.size() + " items");
                processAndDisplayInventory(cache);
                // Vẫn gọi ngầm để update nếu có gì mới (Silent Update)
            }

            // ── 2. NẠP TỪ SERVER (BACKGROUND) ────────────────────────────
            com.vn.jet.mosco.network.GameApiService apiService = com.vn.jet.mosco.network.ApiClient.getClient(requireContext()).create(com.vn.jet.mosco.network.GameApiService.class);
            apiService.getUserCards(userId).enqueue(new retrofit2.Callback<List<com.vn.jet.mosco.model.UserCard>>() {
                @Override
                public void onResponse(retrofit2.Call<List<com.vn.jet.mosco.model.UserCard>> call, retrofit2.Response<List<com.vn.jet.mosco.model.UserCard>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        List<com.vn.jet.mosco.model.UserCard> userCards = response.body();
                        // Chuyển đổi list UserCard sang list Objet (Model cũ của UI)
                        new Thread(() -> {
                            List<com.vn.jet.mosco.utils.DatabaseLoader.UserInventoryItem> items = new ArrayList<>(userCards.size());
                            for (com.vn.jet.mosco.model.UserCard uc : userCards) {
                                items.add(com.vn.jet.mosco.utils.DatabaseLoader.UserInventoryItem.fromUserCard(uc));
                            }
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> processAndDisplayInventory(items));
                            }
                        }).start();
                    }
                }
                @Override
                public void onFailure(retrofit2.Call<List<com.vn.jet.mosco.model.UserCard>> call, Throwable t) {}
            });
        }

        /**
         * Xử lý mapping và hiển thị dữ liệu lên RecyclerView.
         */
        private void processAndDisplayInventory(List<com.vn.jet.mosco.utils.DatabaseLoader.UserInventoryItem> items) {
            if (items == null) return;
            new Thread(() -> {
                List<com.vn.jet.mosco.model.Objet> realObjets = new ArrayList<>();
                for (com.vn.jet.mosco.utils.DatabaseLoader.UserInventoryItem uc : items) {
                    com.vn.jet.mosco.model.Objet objet = new com.vn.jet.mosco.model.Objet(
                            uc.id.intValue(), uc.collectionId, uc.frontImage, uc.level, uc.exp, uc.upgradeLevel
                    );
                    objet.setOvr(uc.ovr);
                    objet.setMember(uc.member);
                    objet.setSeason(uc.season);
                    
                    objet.setTypeKey(mapClassToTypeKey(uc.cardClass));
                    objet.setBackImageUrl(uc.backImage);
                    objet.setCollectionNo(uc.collectionNo);
                    objet.setSlug(uc.slug);
                    objet.setBackgroundColor(uc.backgroundColor);
                    objet.setTextColor(uc.textColor);
                    objet.setAvailableTags(uc.availableTags);
                    objet.setDimension(uc.dimension);
                    realObjets.add(objet);
                }

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        originalObjets = realObjets;
                        applyFilters();
                    });
                }
            }).start();
        }

        private void applyFilters() {
            new Thread(() -> {
                List<com.vn.jet.mosco.model.Objet> filtered = new ArrayList<>();
                View sortBtnView = getView() != null ? getView().findViewById(R.id.btn_sort_objets) : null;
                String currentSort = (sortBtnView instanceof TextView) ? ((TextView) sortBtnView).getText().toString() : "Newest";

                java.util.Set<String> selArtists = new java.util.HashSet<>();
                java.util.Set<String> selClasses = new java.util.HashSet<>();
                java.util.Set<String> selSeasons = new java.util.HashSet<>();

                for (String f : objetFilter) {
                    if (isArtist(f)) selArtists.add(f.toLowerCase());
                    else if (isClass(f)) selClasses.add(f.toLowerCase());
                    else selSeasons.add(f.toLowerCase());
                }

                for (com.vn.jet.mosco.model.Objet obj : originalObjets) {
                    if (objetFilter.isEmpty()) {
                        filtered.add(obj);
                        continue;
                    }
                    
                    String member = obj.getMember();
                    String season = obj.getSeason();
                    String rawClass = obj.getTypeKey();
                    String mappedClass = mapClassToTypeKey(rawClass);
                    
                    boolean matchArtist = selArtists.isEmpty() || (member != null && selArtists.contains(member.toLowerCase()));
                    boolean matchClass = selClasses.isEmpty() || (rawClass != null && selClasses.contains(rawClass.toLowerCase())) || (mappedClass != null && selClasses.contains(mappedClass.toLowerCase().replaceAll("\\s+", "")));
                    boolean matchSeason = selSeasons.isEmpty() || (season != null && selSeasons.contains(season.toLowerCase()));

                    if (matchArtist && matchClass && matchSeason) {
                        filtered.add(obj);
                    }
                }

                filtered.sort((a, b) -> {
                    if ("Oldest".equals(currentSort)) return Long.compare(a.getId(), b.getId());
                    if ("Highest OVR".equals(currentSort)) return Integer.compare(b.getOvr(), a.getOvr());
                    if ("Lowest OVR".equals(currentSort)) return Integer.compare(a.getOvr(), b.getOvr());
                    if ("Highest Level".equals(currentSort)) return Integer.compare(b.getLevel(), a.getLevel());
                    if ("Lowest Level".equals(currentSort)) return Integer.compare(a.getLevel(), b.getLevel());
                    if ("Highest Badge".equals(currentSort)) return Integer.compare(b.getUpgradeLevel(), a.getUpgradeLevel());
                    if ("Lowest Badge".equals(currentSort)) return Integer.compare(a.getUpgradeLevel(), b.getUpgradeLevel());
                    return Long.compare(b.getId(), a.getId());
                });

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (rvObjets != null && rvObjets.getAdapter() instanceof com.vn.jet.mosco.adapter.BaseInventoryAdapter) {
                            ((com.vn.jet.mosco.adapter.BaseInventoryAdapter) rvObjets.getAdapter()).updateData(filtered);
                        }
                        if (tvCount != null) tvCount.setText(filtered.size() + " Items");
                    });
                }
            }).start();
        }
    }

    // ==========================================
    // TAB 3: ITEMS
    // ==========================================
    public static class ItemsFragment extends Fragment {
        private final Set<String> itemsFilter = new LinkedHashSet<>();
        private final String[] SORT_OPTIONS = {"Newest", "Oldest", "Lowest No.", "Highest No."};
        private RecyclerView rvItems;
        private ItemsAdapter adapter;
        private List<com.vn.jet.mosco.model.UserItem> originalItems = new ArrayList<>();

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_collection_items, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            // Sort
            View sortBtn = view.findViewById(R.id.btn_sort_items);
            LinearLayout dropdown = view.findViewById(R.id.dropdown_sort_items);
            setupSortDropdown(sortBtn, null, null, SORT_OPTIONS, dropdown, this::applyFilters);

            // Filter
            view.findViewById(R.id.btn_filter_items).setOnClickListener(v ->
                showFilterBottomSheet(this, buildItemsCategories(), 0, itemsFilter, this::applyFilters));

            // RecyclerView
            rvItems = view.findViewById(R.id.rv_items);
            rvItems.setLayoutManager(new GridLayoutManager(getContext(), 3));

            adapter = new ItemsAdapter(new ArrayList<>(), this::onItemClicked);
            rvItems.setAdapter(adapter);

            loadInventory();
        }

        private void onItemClicked(com.vn.jet.mosco.model.UserItem item) {
            String type = item.getType() != null ? item.getType().toUpperCase() : "";
            int qty = item.getQuantity() != null ? item.getQuantity() : 1;

            if (type.equals("PACK")) {
                // Chỉ PACK mới gọi API mở pack.
                if (getActivity() != null) {
                    ItemRevealFragment revealFragment = ItemRevealFragment.newInstance(
                            item.getName(), item.getDescription(),
                            item.getImageUri(), qty, item.getItemCode());
                    getActivity().getSupportFragmentManager().beginTransaction()
                            .replace(R.id.frame_layout, revealFragment)
                            .addToBackStack(null)
                            .commit();
                }
            } else if (type.equals("OBJET")) {
                Toast.makeText(requireContext(), getString(R.string.reveal_only_pack_supported), Toast.LENGTH_SHORT).show();
            } else {
                // Buff / other items: show use dialog with quantity picker
                showUseBuffDialog(item);
            }
        }

        private void showUseBuffDialog(com.vn.jet.mosco.model.UserItem item) {
            android.app.Dialog dialog = new android.app.Dialog(requireContext());
            dialog.setContentView(R.layout.dialog_use_buff);

            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(
                        new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                dialog.getWindow().setLayout(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            }

            ImageView ivImage = dialog.findViewById(R.id.iv_buff_image);
            TextView tvName = dialog.findViewById(R.id.tv_buff_name);
            TextView tvDesc = dialog.findViewById(R.id.tv_buff_desc);
            TextView tvAvailQty = dialog.findViewById(R.id.tv_available_qty);
            TextView btnMinus = dialog.findViewById(R.id.btn_minus);
            TextView btnPlus = dialog.findViewById(R.id.btn_plus);
            android.widget.EditText etQty = dialog.findViewById(R.id.et_quantity);
            com.google.android.material.button.MaterialButton btnUse = dialog.findViewById(R.id.btn_use);
            ImageView btnClose = dialog.findViewById(R.id.btn_dialog_close);

            int maxQty = item.getQuantity() != null ? Math.min(item.getQuantity(), 99) : 1;
            final int[] qty = {1};

            tvName.setText(item.getName());
            tvDesc.setText(item.getDescription() != null ? item.getDescription() : "");
            tvAvailQty.setText("Available: " + (item.getQuantity() != null ? NumberUtils.format(getContext(), item.getQuantity()) : "0"));

            Glide.with(this)
                    .load(item.getImageUri() != null && !item.getImageUri().isEmpty() ? item.getImageUri() : "")
                    .placeholder(R.drawable.item_shop_demo)
                    .into(ivImage);

            btnMinus.setOnClickListener(v -> {
                if (qty[0] > 1) {
                    qty[0]--;
                    etQty.setText(String.valueOf(qty[0]));
                }
            });

            btnPlus.setOnClickListener(v -> {
                if (qty[0] < maxQty) {
                    qty[0]++;
                    etQty.setText(String.valueOf(qty[0]));
                }
            });

            etQty.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(android.text.Editable s) {
                    if (s.length() == 0) return;
                    try {
                        int val = Integer.parseInt(s.toString());
                        if (val < 1) qty[0] = 1;
                        else if (val > maxQty) qty[0] = maxQty;
                        else qty[0] = val;
                    } catch (Exception e) {
                        qty[0] = 1;
                    }
                }
            });

            btnClose.setOnClickListener(v -> dialog.dismiss());

            btnUse.setOnClickListener(v -> {
                dialog.dismiss();
                Toast.makeText(requireContext(),
                        "Used " + qty[0] + "x " + item.getName(), Toast.LENGTH_SHORT).show();
                // TODO: Call API to use items, then refresh inventory
            });

            dialog.show();
        }

        private void loadInventory() {
            Long userId = new com.vn.jet.mosco.utils.SessionManager(requireContext()).getUserId();
            if (userId == null) return;

            com.vn.jet.mosco.network.GameApiService apiService = com.vn.jet.mosco.network.ApiClient.getClient(requireContext()).create(com.vn.jet.mosco.network.GameApiService.class);
            apiService.getUserItems(userId).enqueue(new retrofit2.Callback<List<com.vn.jet.mosco.model.UserItem>>() {
                @Override
                public void onResponse(retrofit2.Call<List<com.vn.jet.mosco.model.UserItem>> call, retrofit2.Response<List<com.vn.jet.mosco.model.UserItem>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        originalItems = response.body();
                        applyFilters();
                    }
                }

                @Override
                public void onFailure(retrofit2.Call<List<com.vn.jet.mosco.model.UserItem>> call, Throwable t) {
                    Toast.makeText(getContext(), "Failed to load inventory", Toast.LENGTH_SHORT).show();
                }
            });
        }

        private void applyFilters() {
            if (originalItems == null || !isAdded()) return;
            List<com.vn.jet.mosco.model.UserItem> filtered = new ArrayList<>();
            View sortBtn = getView() != null ? getView().findViewById(R.id.btn_sort_items) : null;
            String currentSort = (sortBtn instanceof TextView) ? ((TextView) sortBtn).getText().toString() : "Newest";

            for (com.vn.jet.mosco.model.UserItem item : originalItems) {
                if (item.getName() == null || item.getName().isEmpty() || item.getName().equalsIgnoreCase("Unknown")) continue;
                
                if (itemsFilter.isEmpty()) {
                    filtered.add(item);
                } else {
                    String type = item.getType() != null ? item.getType().toUpperCase() : "";
                    boolean match = false;
                    for (String f : itemsFilter) {
                        if (f.equalsIgnoreCase(type)) match = true;
                        // Map internal type to UI type labels if needed
                        if (f.equalsIgnoreCase("Materials") && type.equals("MATERIAL")) match = true;
                        if (f.equalsIgnoreCase("Consumables") && (type.equals("BUFF") || type.equals("CONSUMABLE"))) match = true;
                    }
                    if (match) filtered.add(item);
                }
            }

            filtered.sort((a, b) -> {
                if ("Oldest".equals(currentSort)) return b.getId().compareTo(a.getId());
                if ("Lowest No.".equals(currentSort)) return Integer.compare(a.getQuantity() != null ? a.getQuantity() : 0, b.getQuantity() != null ? b.getQuantity() : 0);
                if ("Highest No.".equals(currentSort)) return Integer.compare(b.getQuantity() != null ? b.getQuantity() : 0, a.getQuantity() != null ? a.getQuantity() : 0);
                return a.getId().compareTo(b.getId());
            });

            if (adapter != null) adapter.updateData(filtered);
        }
    }

    // Callback interface for item clicks
    interface OnItemClickListener {
        void onItemClick(com.vn.jet.mosco.model.UserItem item);
    }

    private static class ItemsAdapter extends RecyclerView.Adapter<ItemsAdapter.ViewHolder> {
        private List<com.vn.jet.mosco.model.UserItem> list;
        private final OnItemClickListener listener;

        public ItemsAdapter(List<com.vn.jet.mosco.model.UserItem> list, OnItemClickListener listener) {
            this.list = list;
            this.listener = listener;
        }
        
        public void updateData(List<com.vn.jet.mosco.model.UserItem> newList) {
            this.list = newList;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_inventory_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            com.vn.jet.mosco.model.UserItem item = list.get(position);
            holder.tvName.setText(item.getName());
            holder.tvDesc.setText(item.getDescription() != null ? item.getDescription() : "");
            holder.tvQty.setText("x" + NumberUtils.format(holder.itemView.getContext(), item.getQuantity() != null ? item.getQuantity() : 0));
            
            Glide.with(holder.itemView.getContext())
                    .load(item.getImageUri() != null && !item.getImageUri().isEmpty() ? item.getImageUri() : "")
                    .placeholder(R.drawable.item_shop_demo)
                    .into(holder.ivImage);

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onItemClick(item);
            });
        }

        @Override
        public int getItemCount() { return list.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivImage;
            TextView tvName, tvDesc, tvQty;
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                ivImage = itemView.findViewById(R.id.iv_item_image);
                tvName = itemView.findViewById(R.id.tv_item_name);
                tvDesc = itemView.findViewById(R.id.tv_item_desc);
                tvQty = itemView.findViewById(R.id.tv_item_qty);
            }
        }
    }

    // ==========================================
    // TAB 0: ALBUM — Bộ Sưu Tập (Pokédex-style Collection Book)
    // ==========================================
    public static class AlbumFragment extends Fragment {
        private final Set<String> albumFilter = new LinkedHashSet<>();
        private final String[] SORT_OPTIONS = {"Newest", "Oldest", "Highest OVR", "Lowest OVR", "Highest Level", "Lowest Level", "Highest Badge", "Lowest Badge"};
        private RecyclerView rvAlbum;
        private com.vn.jet.mosco.adapter.CollectionBookAdapter adapter;
        private TextView tvProgress, tvCount;
        private android.widget.ProgressBar progressBar;
        private List<com.vn.jet.mosco.model.CollectionEntry> originalEntries = new ArrayList<>();
        private int totalCards = 0;
        private int ownedCount = 0;

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_collection_album, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            tvProgress = view.findViewById(R.id.tv_album_progress);
            tvCount = view.findViewById(R.id.tv_album_count);
            progressBar = view.findViewById(R.id.progress_album);

            // Sort
            View sortBtn = view.findViewById(R.id.btn_sort_album);
            LinearLayout dropdown = view.findViewById(R.id.dropdown_sort_album);
            setupSortDropdown(sortBtn, null, null, SORT_OPTIONS, dropdown, this::applyFilters);

            // Filter
            view.findViewById(R.id.btn_filter_album).setOnClickListener(v ->
                showFilterBottomSheet(this, buildAlbumCategories(requireContext()), 0, albumFilter, this::applyFilters));

            // RecyclerView
            rvAlbum = view.findViewById(R.id.rv_album);
            rvAlbum.setLayoutManager(new GridLayoutManager(getContext(), 3));

            adapter = new com.vn.jet.mosco.adapter.CollectionBookAdapter(new ArrayList<>(), this::onBookCardClicked);
            rvAlbum.setAdapter(adapter);

            loadCollectionBook();
        }

        /**
         * Xử lý click thẻ trong Album.
         * Luôn mở Dialog chi tiết thẻ (hỗ trợ cả thẻ chưa sở hữu với icon ổ khóa).
         */
        private void onBookCardClicked(com.vn.jet.mosco.model.CollectionEntry entry) {
            if (getContext() != null && entry != null) {
                com.vn.jet.mosco.utils.CollectionDetailBinder.showDetail(getContext(), entry);
            }
        }

        /**
         * Gọi API lấy dữ liệu Collection Book từ Server.
         */
        private void loadCollectionBook() {
            Long userId = new com.vn.jet.mosco.utils.SessionManager(requireContext()).getUserId();
            if (userId == null) return;

            android.util.Log.d("AlbumFragment", "Loading collection book for user: " + userId);

            com.vn.jet.mosco.network.GameApiService apiService = 
                    com.vn.jet.mosco.network.ApiClient.getClient(requireContext())
                    .create(com.vn.jet.mosco.network.GameApiService.class);

            apiService.getCollectionBook(userId).enqueue(new retrofit2.Callback<com.vn.jet.mosco.model.CollectionBookResponse>() {
                @Override
                public void onResponse(retrofit2.Call<com.vn.jet.mosco.model.CollectionBookResponse> call,
                                      retrofit2.Response<com.vn.jet.mosco.model.CollectionBookResponse> response) {
                    if (!isAdded()) return;
                    if (response.isSuccessful() && response.body() != null) {
                        com.vn.jet.mosco.model.CollectionBookResponse book = response.body();
                        totalCards = book.getTotalCards();
                        ownedCount = book.getOwnedCount();
                        originalEntries = book.getEntries() != null ? book.getEntries() : new ArrayList<>();

                        // Cập nhật tiến trình
                        if (tvProgress != null) {
                            tvProgress.setText(ownedCount + "/" + totalCards);
                        }
                        if (progressBar != null && totalCards > 0) {
                            int percent = (int) ((ownedCount * 100.0f) / totalCards);
                            progressBar.setProgress(percent);
                        }
                        
                        // Cập nhật Milestones
                        updateMilestones(ownedCount);

                        applyFilters();
                        android.util.Log.d("AlbumFragment", "Loaded " + totalCards + " cards, owned: " + ownedCount);
                    } else {
                        android.util.Log.e("AlbumFragment", "Server error: " + response.code());
                    }
                }

                @Override
                public void onFailure(retrofit2.Call<com.vn.jet.mosco.model.CollectionBookResponse> call, Throwable t) {
                    if (!isAdded()) return;
                    android.util.Log.e("AlbumFragment", "API Failure", t);
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Không thể tải Bộ Sưu Tập", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        /**
         * Xử lý UI hiển thị mốc quà (sáng lên nếu đủ thẻ).
         */
        /**
         * Cập nhật trạng thái các cột mốc phần thưởng (30%, 60%, 100%).
         */
        private void updateMilestones(int owned) {
            if (getView() == null || totalCards <= 0) return;
            Context ctx = getContext();
            if (ctx == null) return;

            // Tính toán ngưỡng theo %
            int m1 = (int) (totalCards * 30 / 100);
            int m2 = (int) (totalCards * 60 / 100);
            int m3 = totalCards;

            // Xử lý từng mốc
            handleMilestoneState(1, owned >= m1, owned, m1);
            handleMilestoneState(2, owned >= m2, owned, m2);
            handleMilestoneState(3, owned >= m3, owned, m3);
        }

        private void handleMilestoneState(int index, boolean achieved, int owned, int req) {
            View view = getView();
            if (view == null) return;

            int iconId = (index == 1) ? R.id.iv_ms_1_icon : (index == 2) ? R.id.iv_ms_2_icon : R.id.iv_ms_3_icon;
            int containerId = (index == 1) ? R.id.ms_1_container : (index == 2) ? R.id.ms_2_container : R.id.ms_3_container;
            int textId = (index == 1) ? R.id.tv_ms_1_req : (index == 2) ? R.id.tv_ms_2_req : R.id.tv_ms_3_req;

            ImageView iv = view.findViewById(iconId);
            View container = view.findViewById(containerId);
            TextView tv = view.findViewById(textId);

            if (iv == null || container == null || tv == null) return;

            // Kiểm tra trạng thái đã nhận quà
            com.vn.jet.mosco.utils.SessionManager session = new com.vn.jet.mosco.utils.SessionManager(getContext());
            Long userIdLong = session.getUserId();
            String userId = userIdLong != null ? String.valueOf(userIdLong) : "unknown";
            
            android.content.SharedPreferences prefs = getContext().getSharedPreferences("MoscoCollection", Context.MODE_PRIVATE);
            boolean isClaimed = prefs.getBoolean("claimed_" + userId + "_ms_" + index, false);

            if (isClaimed) {
                // ĐÃ NHẬN: Mờ đi để báo hiệu đã lấy quà
                iv.setColorFilter(null);
                iv.setAlpha(0.3f);
                tv.setTextColor(android.graphics.Color.GRAY);
                tv.setText("COMPLETED");
                container.clearAnimation();
                container.setOnClickListener(v -> 
                    android.widget.Toast.makeText(getContext(), "Bạn đã nhận phần thưởng này!", android.widget.Toast.LENGTH_SHORT).show());
            } else if (achieved) {
                // ĐÃ ĐẠT (CHƯA NHẬN): Hiệu ứng Pulse (Nhịp đập) mời gọi click
                iv.setAlpha(1.0f);
                iv.setColorFilter(null); 
                tv.setTextColor(android.graphics.Color.WHITE);
                tv.setText("REWARD");
                
                if (container.getAnimation() == null) {
                    android.view.animation.Animation pulse = android.view.animation.AnimationUtils.loadAnimation(getContext(), R.anim.pulse_milestone);
                    container.startAnimation(pulse);
                }

                container.setOnClickListener(v -> claimMilestone(index, req));
            } else {
                // CHƯA ĐẠT: Bộ lọc Grayscale
                android.graphics.ColorMatrix matrix = new android.graphics.ColorMatrix();
                matrix.setSaturation(0f);
                iv.setColorFilter(new android.graphics.ColorMatrixColorFilter(matrix));
                iv.setAlpha(0.2f);
                tv.setTextColor(android.graphics.Color.parseColor("#66FFFFFF"));
                tv.setText(owned + "/" + req);
                container.clearAnimation();
                container.setOnClickListener(v -> 
                    android.widget.Toast.makeText(getContext(), "Cố gắng đạt " + req + " thẻ để nhận quà!", android.widget.Toast.LENGTH_SHORT).show());
            }
        }

        private void claimMilestone(int index, int req) {
            com.vn.jet.mosco.utils.SessionManager session = new com.vn.jet.mosco.utils.SessionManager(getContext());
            Long userIdLong = session.getUserId();
            String userId = userIdLong != null ? String.valueOf(userIdLong) : "unknown";
            
            String rewardName = (index == 1) ? "1,000 Coin" : (index == 2) ? "5,000 Coin & 1 Voucher" : "10,000 Coin & 1 Special Card";

            // Gọi Binder cao cấp để hiện hiệu ứng "Nổ quà"
            com.vn.jet.mosco.utils.CollectionRewardBinder.showReward(requireContext(), 
                    "Bạn đã đạt cột mốc " + req + " thẻ.\nPhần thưởng: " + rewardName, 
                    () -> {
                        // Logic sau khi nhấn THU THẬP
                        getContext().getSharedPreferences("MoscoCollection", Context.MODE_PRIVATE)
                                .edit().putBoolean("claimed_" + userId + "_ms_" + index, true).apply();
                        
                        updateMilestones(ownedCount);
                    }
            );
        }

        /**
         * Áp dụng bộ lọc + sắp xếp cho danh sách entries.
         */
        private void applyFilters() {
            if (originalEntries == null || !isAdded()) return;

            new Thread(() -> {
                View sortBtnView = getView() != null ? getView().findViewById(R.id.btn_sort_album) : null;
                String currentSortLabel = (sortBtnView instanceof TextView) ? ((TextView) sortBtnView).getText().toString() : "Newest";

                java.util.Set<String> selArtists = new java.util.HashSet<>();
                java.util.Set<String> selClasses = new java.util.HashSet<>();
                java.util.Set<String> selSeasons = new java.util.HashSet<>();
                java.util.Set<String> selStatus = new java.util.HashSet<>();

                for (String f : albumFilter) {
                    if (isStatus(f)) selStatus.add(f.toLowerCase());
                    else if (isArtist(f)) selArtists.add(f.toLowerCase());
                    else if (isClass(f)) selClasses.add(f.toLowerCase());
                    else selSeasons.add(f.toLowerCase());
                }

                List<com.vn.jet.mosco.model.CollectionEntry> filtered = new ArrayList<>();

                for (com.vn.jet.mosco.model.CollectionEntry entry : originalEntries) {
                    boolean matchStatus = selStatus.isEmpty() || selStatus.contains("all") || selStatus.contains("tất cả")
                            || ((selStatus.contains("owned") || selStatus.contains("đã sở hữu")) && entry.isOwned())
                            || ((selStatus.contains("missing") || selStatus.contains("chưa sở hữu")) && !entry.isOwned());

                    String member = entry.getMember();
                    boolean matchArtist = selArtists.isEmpty() || (member != null && selArtists.contains(member.toLowerCase()));

                    String rawClass = entry.getCardClass();
                    String mappedClass = mapClassToTypeKey(rawClass);
                    boolean matchClass = selClasses.isEmpty() || (rawClass != null && selClasses.contains(rawClass.toLowerCase())) || (mappedClass != null && selClasses.contains(mappedClass.toLowerCase().replaceAll("\\s+", "")));

                    String season = entry.getSeason();
                    boolean matchSeason = selSeasons.isEmpty() || (season != null && selSeasons.contains(season.toLowerCase()));

                    if (matchStatus && matchArtist && matchClass && matchSeason) {
                        filtered.add(entry);
                    }
                }

                filtered.sort((a, b) -> {
                    if ("Newest".equals(currentSortLabel)) return compareNatural(b.getCollectionNo(), a.getCollectionNo());
                    if ("Oldest".equals(currentSortLabel)) return compareNatural(a.getCollectionNo(), b.getCollectionNo());
                    if ("Highest OVR".equals(currentSortLabel)) return Integer.compare(b.getOvr(), a.getOvr());
                    if ("Lowest OVR".equals(currentSortLabel)) return Integer.compare(a.getOvr(), b.getOvr());
                    if ("Highest Level".equals(currentSortLabel)) return Integer.compare(b.getLevel(), a.getLevel());
                    if ("Lowest Level".equals(currentSortLabel)) return Integer.compare(a.getLevel(), b.getLevel());
                    if ("Highest Badge".equals(currentSortLabel)) return Integer.compare(b.getUpgradeLevel(), a.getUpgradeLevel());
                    if ("Lowest Badge".equals(currentSortLabel)) return Integer.compare(a.getUpgradeLevel(), b.getUpgradeLevel());
                    if (a.isOwned() != b.isOwned()) return a.isOwned() ? -1 : 1;
                    return compareNatural(b.getCollectionNo(), a.getCollectionNo());
                });

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (adapter != null) adapter.updateData(filtered);
                        if (tvCount != null) tvCount.setText(filtered.size() + " Cards");
                    });
                }
            }).start();
        }
    }

    private static boolean isStatus(String f) {
        if (f == null) return false;
        String lower = f.toLowerCase();
        return java.util.Arrays.asList("tất cả", "đã sở hữu", "chưa sở hữu", "all", "owned", "missing").contains(lower);
    }

    private static boolean isArtist(String f) {
        if (f == null) return false;
        return java.util.Arrays.asList("SeoYeon", "HyeRin", "JiWoo", "ChaeYeon", "YooYeon", "SooMin", "NaKyoung", "YuBin", "Kaede", "DaHyun", "Kotone", "YeonJi", "Nien", "SoHyun", "Xinyu", "Mayu", "Lynn", "JooBin", "HaYeon", "ShiOn", "ChaeWon", "Sullin", "SeoAh", "JiYeon").contains(f);
    }

    private static boolean isClass(String f) {
        if (f == null) return false;
        return java.util.Arrays.asList("First", "Welcome", "Double", "Premier", "Special", "SpecialUnit").contains(f);
    }

    private static int compareNatural(String s1, String s2) {
        if (s1 == null && s2 == null) return 0;
        if (s1 == null) return -1;
        if (s2 == null) return 1;

        // Tối ưu hóa: Trích xuất số bằng tay thay vì dùng Regex replaceAll
        long n1 = extractDigits(s1);
        long n2 = extractDigits(s2);
        
        if (n1 != -1 && n2 != -1) {
            return Long.compare(n1, n2);
        }

        return s1.compareTo(s2);
    }

    private static long extractDigits(String s) {
        if (s == null || s.isEmpty()) return -1;
        long res = 0;
        boolean found = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                res = res * 10 + (c - '0');
                found = true;
            } else if (found) break; // Chỉ lấy cụm số đầu tiên gặp được
        }
        return found ? res : -1;
    }

    /** Mapping class từ UI sang database key (Đã tách bạch Welcome/First, Special/Unit) */
    private static String mapClassToTypeKey(String cardClass) {
        if (cardClass == null) return "First";
        String key = cardClass.trim();
        
        if (key.equalsIgnoreCase("Welcome")) return "Welcome";
        if (key.equalsIgnoreCase("First")) return "First";
        if (key.equalsIgnoreCase("Double")) return "Double";
        if (key.equalsIgnoreCase("Premier")) return "Premier";
        if (key.equalsIgnoreCase("Special")) return "Special";
        if (key.equalsIgnoreCase("Unit")) return "Unit";
        
        // Hỗ trợ hạ cấp các kiểu cũ (Legacy support)
        if (key.contains("Welcome")) return "Welcome";
        if (key.contains("Unit")) return "Unit";
        if (key.equalsIgnoreCase("SpecialUnit")) return "Special";
        if (key.equalsIgnoreCase("FirstWelcome")) return "First";
        
        return "First";
    }
}

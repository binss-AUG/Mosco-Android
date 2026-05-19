package com.vn.jet.mosco.fragment;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
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
import androidx.core.content.ContextCompat;
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
import com.vn.jet.mosco.model.PrivateChatMessage;

import android.widget.Button;
import android.widget.GridLayout;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class CollectionFragment extends Fragment {

    private TabLayout tabLayout;
    private ViewPager2 viewPager;

    // [QUIET LUXURY] Constants for Sorting to avoid magic strings
    public static final String SORT_NEWEST = "Newest";
    public static final String SORT_BADGE = "Badge";
    public static final String SORT_LEVEL = "Level";
    public static final String SORT_STATUS = "Status";
    public static final String SORT_ARTIST = "Artist (A-Z)";
    public static final String SORT_CLASS = "Class";
    public static final String SORT_SEASON = "Season";
    public static final String SORT_LOWEST_NO = "Lowest No.";
    public static final String SORT_HIGHEST_NO = "Highest No.";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_collection, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Nạp Master Data (database.json) ở background sớm để Album/Objets mượt mà
        com.vn.jet.mosco.utils.DatabaseLoader.initMasterData(requireContext());

        // [QUIET LUXURY] Đồng bộ tiêu đề màn hình Collection dùng chung layout_common_header và ẩn nút Back
        View headerView = view.findViewById(R.id.layout_collection_header);
        if (headerView != null) {
            TextView tvTitle = headerView.findViewById(R.id.tv_header_title);
            if (tvTitle != null) {
                tvTitle.setText(R.string.collection_header_title);
            }
            View btnBack = headerView.findViewById(R.id.btn_back_common);
            if (btnBack != null) {
                btnBack.setVisibility(View.GONE);
            }
        }

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

        String[] tabs = getResources().getStringArray(R.array.collection_tabs);
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            if (position < tabs.length) {
                tab.setText(tabs[position]);
            }
        }).attach();

        // Check if a specific tab was requested (e.g. from Shop Success Dialog)
        if (getArguments() != null) {
            int defaultTab = getArguments().getInt("default_tab", 0);
            viewPager.setCurrentItem(defaultTab, false);
        } else {
            // Default to Cards (index 0)
            viewPager.setCurrentItem(0, false);
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
    public static void showObjetDetailDialog(Context context, String imageUrl, org.json.JSONObject cardJson, int level,
            int exp, int upgrade) {
        android.app.Dialog dialog = new android.app.Dialog(context);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_objet_detail);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            // MATCH_PARENT — the card's own margin (12dp) creates the visual inset.
            // No scroll: ConstraintLayout distributes space automatically.
            dialog.getWindow().setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT);
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
                        .setTitle(context.getString(R.string.dialog_refresh_title))
                        .setMessage(context.getString(R.string.dialog_refresh_msg))
                        .setPositiveButton(context.getString(R.string.action_confirm), (d, w) -> {
                            if (cardJson != null) {
                                String slug = cardJson.optString("slug", "");
                                if (!slug.isEmpty()) {
                                    // Reload data from DatabaseLoader
                                    org.json.JSONObject refreshedCard = com.vn.jet.mosco.utils.DatabaseLoader
                                            .findBySlug(context, slug);
                                    if (refreshedCard != null) {
                                        com.vn.jet.mosco.utils.ObjetDetailBinder.bind(dialog, context, refreshedCard,
                                                level, exp, upgrade);
                                        android.widget.Toast
                                                .makeText(context, context.getString(R.string.msg_refresh_success),
                                                        android.widget.Toast.LENGTH_SHORT)
                                                .show();
                                    } else {
                                        android.widget.Toast
                                                .makeText(context, context.getString(R.string.msg_refresh_error),
                                                        android.widget.Toast.LENGTH_SHORT)
                                                .show();
                                    }
                                }
                            }
                        })
                        .setNegativeButton(context.getString(R.string.action_cancel), (d, w) -> d.dismiss())
                        .show();
            });
        }

        // ── Nút: Nâng cấp Level (Outlined / Secondary) ────────────────────
        View btnLevelUp = dialog.findViewById(R.id.btn_level_up_detail);
        if (btnLevelUp != null) {
            btnLevelUp.setOnClickListener(v -> {
                Toast.makeText(context, context.getString(R.string.common_msg_coming_soon), Toast.LENGTH_SHORT).show();
            });
        }

        // ── Nút: NÂNG CẤP (Primary — giữ nguyên logic cũ) ─────────────
        View btnUpgrade = dialog.findViewById(R.id.btn_upgrade_detail);
        if (btnUpgrade != null) {
            btnUpgrade.setOnClickListener(v -> {
                Toast.makeText(context, context.getString(R.string.common_msg_coming_soon), Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        }

        dialog.show();
    }

    public static void setupSortDropdown(
            View sortBtn, ImageView arrowIcon, TextView labelView,
            String[] options, LinearLayout dropdownContainer, Runnable onSortChanged) {

        final boolean[] isOpen = { false };

        sortBtn.setOnClickListener(v -> {
            if (isOpen[0]) {
                dropdownContainer.setVisibility(View.GONE);
                if (arrowIcon != null)
                    arrowIcon.setImageResource(R.drawable.ic_arrow_up);
                else if (sortBtn instanceof TextView)
                    ((TextView) sortBtn).setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_up, 0);
                isOpen[0] = false;
                return;
            }

            // Build dropdown items
            dropdownContainer.removeAllViews();
            LayoutInflater inf = LayoutInflater.from(v.getContext());
            int[] loc = new int[2];
            sortBtn.getLocationInWindow(loc);

            String currentLabel = labelView != null ? labelView.getText().toString()
                    : (sortBtn instanceof TextView ? ((TextView) sortBtn).getText().toString() : "");

            for (String opt : options) {
                TextView item = (TextView) inf.inflate(R.layout.item_sort_option, dropdownContainer, false);
                item.setText(opt);
                // Highlight currently selected
                if (opt.equals(currentLabel)) {
                    item.setTextColor(ContextCompat.getColor(v.getContext(), R.color.mosco_card_stroke));
                    item.setTypeface(null, android.graphics.Typeface.BOLD);
                }
                item.setOnClickListener(sel -> {
                    if (labelView != null)
                        labelView.setText(opt);
                    else if (sortBtn instanceof TextView)
                        ((TextView) sortBtn).setText(opt);

                    // Reset styles
                    for (int i = 0; i < dropdownContainer.getChildCount(); i++) {
                        View child = dropdownContainer.getChildAt(i);
                        if (child instanceof TextView) {
                            ((TextView) child).setTextColor(Color.WHITE);
                            ((TextView) child).setTypeface(null, android.graphics.Typeface.NORMAL);
                        }
                    }
                    item.setTextColor(ContextCompat.getColor(v.getContext(), R.color.mosco_card_stroke));
                    dropdownContainer.setVisibility(View.GONE);
                    if (arrowIcon != null)
                        arrowIcon.setImageResource(R.drawable.ic_arrow_up);
                    else if (sortBtn instanceof TextView)
                        ((TextView) sortBtn).setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_up, 0);
                    isOpen[0] = false;

                    if (onSortChanged != null)
                        onSortChanged.run();
                });
                dropdownContainer.addView(item);
            }

            dropdownContainer.setVisibility(View.VISIBLE);
            if (arrowIcon != null)
                arrowIcon.setImageResource(R.drawable.ic_arrow_down);
            else if (sortBtn instanceof TextView)
                ((TextView) sortBtn).setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_down, 0);
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
        public final List<String> items; // For simple text lists (Season, Class)
        public final List<com.vn.jet.mosco.utils.DatabaseLoader.MemberFilterItem> memberItems; // For Artist Grid
        public final boolean isArtistGrid;

        public FilterCategory(String tabName, List<String> items, boolean isArtistGrid) {
            this.tabName = tabName;
            this.items = items;
            this.memberItems = null;
            this.isArtistGrid = isArtistGrid;
        }

        public FilterCategory(String tabName,
                List<com.vn.jet.mosco.utils.DatabaseLoader.MemberFilterItem> memberItems) {
            this.tabName = tabName;
            this.items = null;
            this.memberItems = memberItems;
            this.isArtistGrid = true;
        }
    }

    public static void showFilterBottomSheet(
            Fragment fragment,
            List<FilterCategory> categories,
            Set<String> currentSelections,
            com.vn.jet.mosco.view.InventoryFilterBar filterBar,
            String[] sortOptions,
            Runnable onFilterApplied) {

        Context ctx = fragment.getContext();
        BottomSheetDialog dialog = new BottomSheetDialog(ctx, R.style.CustomBottomSheetDialogTheme);
        View bsView = LayoutInflater.from(ctx).inflate(R.layout.layout_bottom_sheet_objet_filter, null);

        // State cục bộ cho Sort
        final String[] workingSort = { filterBar != null ? filterBar.getSortOption() : SORT_NEWEST };
        final boolean[] workingAsc = { filterBar != null && filterBar.isAscending() };

        android.widget.FrameLayout wrapper = new android.widget.FrameLayout(ctx) {
            @Override
            public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
                View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
                if (sheet != null) {
                    BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
                    int action = ev.getActionMasked();
                    if (action == android.view.MotionEvent.ACTION_DOWN) {
                        float y = ev.getY();
                        View header = bsView.findViewById(R.id.layout_filter_header);
                        if (header == null) {
                            header = bsView.findViewById(R.id.v_drag_handle);
                        }
                        if (header != null && y > header.getBottom()) {
                            behavior.setDraggable(false);
                            getParent().requestDisallowInterceptTouchEvent(true);
                        } else {
                            behavior.setDraggable(true);
                        }
                    } else if (action == android.view.MotionEvent.ACTION_UP
                            || action == android.view.MotionEvent.ACTION_CANCEL) {
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

        dialog.setOnShowListener(di -> {
            View sheet = ((BottomSheetDialog) di).findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet == null) return;
            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
            int screenHeight = ctx.getResources().getDisplayMetrics().heightPixels;
            int peekH = (int) (screenHeight * 0.70f);
            behavior.setPeekHeight(peekH, false);
            behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            behavior.setHideable(true);
            behavior.setSkipCollapsed(false);
            behavior.setDraggable(true);
            sheet.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
            sheet.requestLayout();

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

        LinearLayout llContainerTemp = bsView.findViewById(R.id.ll_filter_sections_container);
        if (llContainerTemp == null) {
            android.widget.FrameLayout flContent = bsView.findViewById(R.id.fl_filter_content);
            if (flContent != null) {
                android.widget.ScrollView sv = new android.widget.ScrollView(ctx);
                sv.setVerticalScrollBarEnabled(false);
                llContainerTemp = new LinearLayout(ctx);
                llContainerTemp.setOrientation(LinearLayout.VERTICAL);
                llContainerTemp.setPadding(0, 0, 0, dpToPx(ctx, 20));
                sv.addView(llContainerTemp);
                flContent.addView(sv, new android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
        }
        final LinearLayout llContainer = llContainerTemp;
        TextView btnApply = bsView.findViewById(R.id.btn_filter_apply);
        TextView btnReset = bsView.findViewById(R.id.btn_filter_reset);
        if (btnReset == null) {
            View clearBtn = bsView.findViewById(R.id.btn_filter_clear);
            if (clearBtn instanceof TextView) {
                btnReset = (TextView) clearBtn;
            }
        }

        Set<String> workingSet = new LinkedHashSet<>(currentSelections);

        // === Đếm tổng filter + sort thay đổi ===
        Runnable updateCount = () -> {
            int count = workingSet.size();
            btnApply.setText(count > 0 ? "Apply (" + count + ")" : "Apply");
        };

        // === Render toàn bộ UI (Sort + Filter sections) ===
        Runnable renderUI = new Runnable() {
            @Override
            public void run() {
                llContainer.removeAllViews();

                // ====== SORT BY SECTION ======
                if (sortOptions != null && sortOptions.length > 0) {
                    // Section Title: "Sort By"
                    TextView sortTitle = new TextView(ctx);
                    sortTitle.setText("Sort By");
                    sortTitle.setTextColor(Color.WHITE);
                    sortTitle.setTextSize(14f);
                    sortTitle.setTypeface(null, android.graphics.Typeface.BOLD);
                    LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    stLp.bottomMargin = dpToPx(ctx, 10);
                    sortTitle.setLayoutParams(stLp);
                    llContainer.addView(sortTitle);

                    // Sort Chips + Direction Toggle trong một dòng ngang
                    LinearLayout sortRow = new LinearLayout(ctx);
                    sortRow.setOrientation(LinearLayout.HORIZONTAL);
                    sortRow.setGravity(Gravity.CENTER_VERTICAL);

                    // Direction Toggle (↑↓) bên trái — [QUIET LUXURY] Sử dụng ic_arrow_up cho nét vẽ mỏng thanh lịch
                    ImageView ivDir = new ImageView(ctx);
                    ivDir.setImageResource(R.drawable.ic_arrow_up);
                    ivDir.setColorFilter(androidx.core.content.ContextCompat.getColor(ctx, R.color.white));
                    ivDir.setRotation(workingAsc[0] ? 0f : 180f);
                    LinearLayout.LayoutParams dirLp = new LinearLayout.LayoutParams(
                            dpToPx(ctx, 32), dpToPx(ctx, 32));
                    dirLp.rightMargin = dpToPx(ctx, 8);
                    ivDir.setLayoutParams(dirLp);
                    ivDir.setPadding(dpToPx(ctx, 6), dpToPx(ctx, 6), dpToPx(ctx, 6), dpToPx(ctx, 6));
                    ivDir.setBackgroundResource(R.drawable.lg_chip_unselected_bg);
                    ivDir.setOnClickListener(v -> {
                        workingAsc[0] = !workingAsc[0];
                        ivDir.animate().rotation(workingAsc[0] ? 0f : 180f).setDuration(200).start();
                    });
                    sortRow.addView(ivDir);

                    // Scrollable Sort Chips
                    android.widget.HorizontalScrollView sortHsv = new android.widget.HorizontalScrollView(ctx);
                    sortHsv.setHorizontalScrollBarEnabled(false);
                    sortHsv.setClipToPadding(false);
                    LinearLayout sortChipsRow = new LinearLayout(ctx);
                    sortChipsRow.setOrientation(LinearLayout.HORIZONTAL);

                    for (String opt : sortOptions) {
                        TextView chip = new TextView(ctx);
                        boolean sel = opt.equals(workingSort[0]);
                        chip.setBackground(ctx.getDrawable(sel ? R.drawable.lg_nav_item_indicator : R.drawable.lg_chip_unselected_bg));
                        chip.setTextColor(sel ? Color.WHITE : androidx.core.content.ContextCompat.getColor(ctx, R.color.lg_text_secondary));
                        chip.setTypeface(null, android.graphics.Typeface.BOLD); // [QUIET LUXURY] Cố định BOLD để chiều rộng chữ không thay đổi giữa các trạng thái, tránh lỗi giật kích thước.
                        chip.setText(opt);
                        chip.setTextSize(13f);
                        chip.setGravity(Gravity.CENTER);
                        chip.setPadding(dpToPx(ctx, 14), 0, dpToPx(ctx, 14), 0);
                        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT, dpToPx(ctx, 36));
                        clp.rightMargin = dpToPx(ctx, 8);
                        chip.setLayoutParams(clp);
                        chip.setOnClickListener(v -> {
                            workingSort[0] = opt;
                            // [QUIET LUXURY UX] Thay đổi trực tiếp visual state của các chip con trong hàng.
                            // Việc này giúp tránh gọi renderUI.run() làm hủy và vẽ lại toàn bộ HorizontalScrollView,
                            // triệt tiêu hoàn toàn lỗi giật cuộn ngang khi chọn chip ở cuối.
                            for (int i = 0; i < sortChipsRow.getChildCount(); i++) {
                                View child = sortChipsRow.getChildAt(i);
                                if (child instanceof TextView) {
                                    TextView cTv = (TextView) child;
                                    boolean isSel = opt.equals(cTv.getText().toString());
                                    cTv.setBackground(ctx.getDrawable(isSel ? R.drawable.lg_nav_item_indicator : R.drawable.lg_chip_unselected_bg));
                                    cTv.setTextColor(isSel ? Color.WHITE : androidx.core.content.ContextCompat.getColor(ctx, R.color.lg_text_secondary));
                                }
                            }
                        });
                        sortChipsRow.addView(chip);
                    }
                    sortHsv.addView(sortChipsRow);
                    sortRow.addView(sortHsv, new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                    LinearLayout.LayoutParams sortRowLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    sortRowLp.bottomMargin = dpToPx(ctx, 8);
                    sortRow.setLayoutParams(sortRowLp);
                    llContainer.addView(sortRow);

                    // Divider mỏng giữa Sort và Filter
                    View divider = new View(ctx);
                    divider.setBackgroundColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.mosco_white_10));
                    LinearLayout.LayoutParams dvLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(ctx, 1));
                    dvLp.topMargin = dpToPx(ctx, 4);
                    dvLp.bottomMargin = dpToPx(ctx, 4);
                    divider.setLayoutParams(dvLp);
                    llContainer.addView(divider);
                }

                // ====== FILTER SECTIONS ======
                for (FilterCategory cat : categories) {
                    TextView title = new TextView(ctx);
                    title.setText(cat.tabName);
                    title.setTextColor(Color.WHITE);
                    title.setTextSize(14f);
                    title.setTypeface(null, android.graphics.Typeface.BOLD);
                    LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    tLp.topMargin = dpToPx(ctx, 16);
                    tLp.bottomMargin = dpToPx(ctx, 10);
                    title.setLayoutParams(tLp);
                    llContainer.addView(title);

                    if (cat.isArtistGrid) {
                        GridLayout grid = new GridLayout(ctx);
                        grid.setLayoutParams(new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                        grid.setColumnCount(3);
                        if (cat.memberItems != null) {
                            for (com.vn.jet.mosco.utils.DatabaseLoader.MemberFilterItem item : cat.memberItems) {
                                View cell = buildArtistCell(ctx, item, workingSet, updateCount);
                                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                                lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
                                lp.width = 0;
                                lp.setMargins(dpToPx(ctx, 4), dpToPx(ctx, 4), dpToPx(ctx, 4), dpToPx(ctx, 12));
                                cell.setLayoutParams(lp);
                                grid.addView(cell);
                            }
                        }
                        llContainer.addView(grid);
                    } else {
                        android.widget.HorizontalScrollView hsv = new android.widget.HorizontalScrollView(ctx);
                        hsv.setHorizontalScrollBarEnabled(false);
                        hsv.setClipToPadding(false);
                        hsv.setPadding(0, 0, dpToPx(ctx, 16), dpToPx(ctx, 8));
                        LinearLayout llRow = new LinearLayout(ctx);
                        llRow.setOrientation(LinearLayout.HORIZONTAL);
                        if (cat.items != null) {
                            for (String item : cat.items) {
                                View chip = buildListCard(ctx, item, workingSet, updateCount);
                                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                                        ViewGroup.LayoutParams.WRAP_CONTENT, dpToPx(ctx, 40));
                                clp.rightMargin = dpToPx(ctx, 10);
                                chip.setLayoutParams(clp);
                                llRow.addView(chip);
                            }
                        }
                        hsv.addView(llRow);
                        llContainer.addView(hsv);
                    }
                }
                updateCount.run();
            }
        };

        renderUI.run();

        btnReset.setOnClickListener(v -> {
            workingSet.clear();
            workingSort[0] = (sortOptions != null && sortOptions.length > 0) ? sortOptions[0] : SORT_NEWEST;
            workingAsc[0] = false;
            renderUI.run();
        });

        btnApply.setOnClickListener(v -> {
            currentSelections.clear();
            currentSelections.addAll(workingSet);
            // Cập nhật sort state về filterBar
            if (filterBar != null) {
                filterBar.setSortText(workingSort[0]);
                filterBar.setAscending(workingAsc[0]);
            }
            dialog.dismiss();
            if (onFilterApplied != null)
                onFilterApplied.run();
        });

        dialog.show();
    }

    private static void updatePinnedActions(View bottomSheet, View bsView, Context ctx) {
        ViewGroup parent = (ViewGroup) bottomSheet.getParent();
        if (parent == null)
            return;
        int parentHeight = parent.getHeight();
        int offScreenAmount = bottomSheet.getHeight() + bottomSheet.getTop() - parentHeight;
        if (offScreenAmount < 0)
            offScreenAmount = 0;

        View actions = bsView.findViewById(R.id.layout_filter_actions);
        if (actions != null)
            actions.setTranslationY(-offScreenAmount);

        View svContent = bsView.findViewById(R.id.sv_filter_content);
        if (svContent == null) {
            android.widget.FrameLayout flContent = bsView.findViewById(R.id.fl_filter_content);
            if (flContent != null && flContent.getChildCount() > 0) {
                View child = flContent.getChildAt(0);
                if (child instanceof android.widget.ScrollView) {
                    svContent = child;
                }
            }
        }
        if (svContent instanceof android.widget.ScrollView) {
            android.widget.ScrollView sv = (android.widget.ScrollView) svContent;
            sv.setPadding(sv.getPaddingLeft(), sv.getPaddingTop(), sv.getPaddingRight(),
                    dpToPx(ctx, 88) + offScreenAmount); // 88dp for actions area height
        }
    }

    private static View buildArtistCell(Context ctx, com.vn.jet.mosco.utils.DatabaseLoader.MemberFilterItem item,
            Set<String> workingSet, Runnable updateCount) {
        String name = item.name;
        LinearLayout cell = new LinearLayout(ctx);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);

        MaterialCardView card = new MaterialCardView(ctx);
        int size = dpToPx(ctx, 76);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(size, size);
        card.setLayoutParams(cardLp);
        card.setRadius(size / 2f);
        card.setStrokeColor(ContextCompat.getColor(ctx, R.color.mosco_card_stroke));
        card.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.mosco_card_bg_variant));
        card.setStrokeWidth(workingSet.contains(name) ? dpToPx(ctx, 2) : 0);
        if (workingSet.contains(name)) card.setAlpha(0.9f);

        ImageView iv = new ImageView(ctx);
        iv.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);

        String finalUrl = item.imageUrl;
        if (finalUrl != null && !finalUrl.isEmpty()) {
            Glide.with(ctx)
                    .load(finalUrl)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.item_shop_demo)
                    .transform(new com.vn.jet.mosco.utils.SmartFaceCropTransformation(finalUrl))
                    .into(iv);
        } else {
            iv.setImageResource(R.drawable.item_shop_demo);
        }
        card.addView(iv);

        TextView label = new TextView(ctx);
        label.setText(name);
        label.setTextSize(11f);
        label.setTextColor(workingSet.contains(name) ? Color.WHITE : ContextCompat.getColor(ctx, R.color.lg_text_disabled));
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
                label.setTextColor(ContextCompat.getColor(ctx, R.color.lg_text_disabled));
                label.setTypeface(null, android.graphics.Typeface.NORMAL);
            } else {
                workingSet.add(name);
                card.setStrokeWidth(dpToPx(ctx, 2));
                card.setAlpha(0.9f);
                label.setTextColor(Color.WHITE);
                label.setTypeface(null, android.graphics.Typeface.BOLD);
            }
            updateCount.run();
        });

        return cell;
    }

    private static View buildListCard(Context ctx, String name, Set<String> workingSet, Runnable updateCount) {
        TextView card = new TextView(ctx);
        boolean selected = workingSet.contains(name);
        card.setBackground(ctx.getDrawable(selected ? R.drawable.lg_nav_item_indicator : R.drawable.lg_chip_unselected_bg));
        card.setTextColor(selected ? Color.WHITE : ContextCompat.getColor(ctx, R.color.lg_text_secondary));
        card.setTypeface(null, android.graphics.Typeface.BOLD); // [QUIET LUXURY] Cố định BOLD để tránh lỗi nhảy kích thước khi đổi kiểu chữ.
        card.setText(name);
        card.setTextSize(14f);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dpToPx(ctx, 16), 0, dpToPx(ctx, 16), 0);

        card.setOnClickListener(v -> {
            if (workingSet.contains(name)) {
                workingSet.remove(name);
                card.setBackground(ctx.getDrawable(R.drawable.lg_chip_unselected_bg));
                card.setTextColor(ContextCompat.getColor(ctx, R.color.lg_text_secondary));
            } else {
                workingSet.add(name);
                card.setBackground(ctx.getDrawable(R.drawable.lg_nav_item_indicator));
                card.setTextColor(Color.WHITE);
            }
            updateCount.run();
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
        List<com.vn.jet.mosco.utils.DatabaseLoader.MemberFilterItem> artists = com.vn.jet.mosco.utils.DatabaseLoader
                .getUniqueMembers(context);
        List<String> seasons = com.vn.jet.mosco.utils.DatabaseLoader.getUniqueSeasons(context);
        List<String> classes = com.vn.jet.mosco.utils.DatabaseLoader.getUniqueClasses(context);

        // [LUXURY CHECK] Nếu dữ liệu rỗng (do đang sync), báo cho người dùng biết thay
        // vì hiện tab trống
        if (artists.isEmpty() && seasons.isEmpty() && classes.isEmpty()) {
            android.util.Log.w("CollectionFragment", "Filter categories are empty, Room sync might be in progress.");
        }

        List<FilterCategory> cats = new ArrayList<>();
        cats.add(new FilterCategory(context.getString(R.string.filter_tab_artist), artists));
        cats.add(new FilterCategory(context.getString(R.string.filter_tab_season), seasons, false));
        cats.add(new FilterCategory(context.getString(R.string.filter_tab_class), classes, false));
        return cats;
    }

    public static List<FilterCategory> buildAlbumCategories(Context context) {
        List<FilterCategory> cats = buildObjetCategories(context);
        List<String> statuses = java.util.Arrays.asList("All", "Owned", "Missing");
        cats.add(0, new FilterCategory(context.getString(R.string.filter_tab_status), statuses, false));
        return cats;
    }

    private static List<FilterCategory> buildMailboxCategories() {
        List<String> types = new ArrayList<>();
        for (String s : new String[] { "Pack", "Objet", "Item" })
            types.add(s);
        List<FilterCategory> cats = new ArrayList<>();
        cats.add(new FilterCategory("Type", types, false));
        return cats;
    }

    private static List<FilterCategory> buildItemsCategories() {
        List<String> types = new ArrayList<>();
        for (String s : new String[] { "Materials", "Consumables", "Equipments" })
            types.add(s);
        List<FilterCategory> cats = new ArrayList<>();
        cats.add(new FilterCategory("Category", types, false));
        return cats;
    }

    // ==========================================
    // PAGER ADAPTER
    // ==========================================
    private static class CollectionPagerAdapter extends FragmentStateAdapter {
        public CollectionPagerAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return new ObjetsFragment(); // Cards
                case 1:
                    return new ItemsFragment();
                case 2:
                    return new AlbumFragment();
                default:
                    return new ObjetsFragment();
            }
        }

        @Override
        public int getItemCount() {
            return 3;
        }
    }

    // ==========================================
    // TAB 1: MAILBOX
    // ==========================================
    public static class MailboxFragment extends Fragment {
        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_collection_mailbox, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            // Gắn sự kiện nút back để quay lại màn hình Collection trước đó
            View backBtn = view.findViewById(R.id.btn_back_common);
            if (backBtn != null) {
                backBtn.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
            }

            // Thiết lập tiêu đề thanh Header dùng chung
            TextView tvTitle = view.findViewById(R.id.tv_header_title);
            if (tvTitle != null) {
                tvTitle.setText(getString(R.string.collection_tab_mailbox));
            }

            TabLayout tabLayout = view.findViewById(R.id.tab_layout_mailbox);
            ViewPager2 viewPager = view.findViewById(R.id.view_pager_mailbox);

            if (viewPager != null && tabLayout != null) {
                viewPager.setAdapter(new MailboxPagerAdapter(this));
                viewPager.setUserInputEnabled(false); // Chặn vuốt tay để giữ tương tác mượt mà qua click tab

                new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
                    switch (position) {
                        case 0: tab.setText("SYSTEM"); break;
                        case 1: tab.setText("GIFTS"); break;
                        case 2: tab.setText("INBOX"); break;
                    }
                }).attach();
            }
        }

        private static class MailboxPagerAdapter extends FragmentStateAdapter {
            public MailboxPagerAdapter(@NonNull Fragment fragment) {
                super(fragment);
            }

            @NonNull
            @Override
            public Fragment createFragment(int position) {
                switch (position) {
                    case 0: return new SystemMailFragment();
                    case 1: return new PlayerGiftsFragment();
                    case 2: return new PrivateChatListFragment();
                    default: return new SystemMailFragment();
                }
            }

            @Override
            public int getItemCount() {
                return 3;
            }
        }
    }

    // ==========================================
    // SUB-TAB 1: THƯ HỆ THỐNG (SYSTEM MAILS)
    // ==========================================
    public static class SystemMailFragment extends Fragment {
        private final Set<String> mailboxFilter = new LinkedHashSet<>();
        private MailboxAdapter adapter;
        private TextView tvCount;
        private List<com.vn.jet.mosco.model.UserMail> originalMails = new ArrayList<>();

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_mailbox_system, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            tvCount = view.findViewById(R.id.tv_system_mails_count);

            // Thiết lập Filter Bar (Smart Pill) cho danh sách thư hệ thống
            com.vn.jet.mosco.view.InventoryFilterBar filterBar = view.findViewById(R.id.filter_bar_system_mails);
            if (filterBar != null) {
                filterBar.setSortOptions(new String[] { SORT_NEWEST, SORT_LOWEST_NO, SORT_HIGHEST_NO });
                filterBar.setListener(new com.vn.jet.mosco.view.InventoryFilterBar.OnFilterChangeListener() {
                    @Override
                    public void onFilterChanged(String sortOption, boolean isAscending) {
                        applyFilters();
                    }

                    @Override
                    public void onFilterRequested() {
                        // Gọi Bottom Sheet lọc nâng cao dùng chung trong hệ sinh thái Mosco
                        showFilterBottomSheet(SystemMailFragment.this, buildMailboxCategories(), mailboxFilter, filterBar, filterBar.getSortOptions(), SystemMailFragment.this::applyFilters);
                    }
                });
            }

            // Nút "Nhận Tất Cả" (Receive All) dưới đáy màn hình
            View btnReceiveAll = view.findViewById(R.id.btn_receive_all);
            if (btnReceiveAll != null) {
                btnReceiveAll.setOnClickListener(v -> receiveAll());
            }

            RecyclerView rvMailbox = view.findViewById(R.id.rv_system_mails);
            if (rvMailbox != null) {
                rvMailbox.setLayoutManager(new LinearLayoutManager(requireContext()));
                adapter = new MailboxAdapter(new ArrayList<>(), this::onMailClicked);
                rvMailbox.setAdapter(adapter);
            }

            loadMailbox();
        }

        private void onMailClicked(com.vn.jet.mosco.model.UserMail mail) {
            String giftInfo = (mail.getItemCode() != null && mail.getQuantity() != null)
                    ? getString(R.string.social_gift_summary_format, mail.getItemCode(),
                            NumberUtils.format(requireContext(), mail.getQuantity()))
                    : "";

            AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(),
                    android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(mail.getTitle())
                    .setMessage(mail.getContent() + giftInfo);

            if (!mail.isReceived()) {
                builder.setPositiveButton(getString(R.string.mailbox_action_claim), (d, w) -> performClaim(mail));
                builder.setNegativeButton(getString(R.string.mailbox_action_later), null);
            } else {
                builder.setPositiveButton(getString(R.string.mailbox_action_received), null);
                builder.setNegativeButton(getString(R.string.mailbox_action_close), null);
            }

            builder.show();
        }

        private void performClaim(com.vn.jet.mosco.model.UserMail mail) {
            com.vn.jet.mosco.network.GameApiService apiService = com.vn.jet.mosco.network.ApiClient
                    .getClient(requireContext())
                    .create(com.vn.jet.mosco.network.GameApiService.class);

            apiService.claimMail(mail.getId()).enqueue(new retrofit2.Callback<okhttp3.ResponseBody>() {
                @Override
                public void onResponse(retrofit2.Call<okhttp3.ResponseBody> call,
                        retrofit2.Response<okhttp3.ResponseBody> response) {
                    if (response.isSuccessful()) {
                        mail.setReceived(true);
                        loadMailbox();
                    }
                }

                @Override
                public void onFailure(retrofit2.Call<okhttp3.ResponseBody> call, Throwable t) {}
            });
        }

        private void receiveAll() {
            if (originalMails == null || originalMails.isEmpty()) {
                return;
            }

            List<com.vn.jet.mosco.model.UserMail> unreceivedMails = new ArrayList<>();
            for (com.vn.jet.mosco.model.UserMail m : originalMails) {
                if (!m.isReceived()) {
                    unreceivedMails.add(m);
                }
            }

            if (unreceivedMails.isEmpty()) {
                return;
            }

            com.vn.jet.mosco.network.GameApiService apiService = com.vn.jet.mosco.network.ApiClient
                    .getClient(requireContext())
                    .create(com.vn.jet.mosco.network.GameApiService.class);

            java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger(unreceivedMails.size());

            for (com.vn.jet.mosco.model.UserMail mail : unreceivedMails) {
                apiService.claimMail(mail.getId()).enqueue(new retrofit2.Callback<okhttp3.ResponseBody>() {
                    @Override
                    public void onResponse(retrofit2.Call<okhttp3.ResponseBody> call,
                            retrofit2.Response<okhttp3.ResponseBody> response) {
                        if (response.isSuccessful()) {
                            mail.setReceived(true);
                        }
                        checkCompletion();
                    }

                    @Override
                    public void onFailure(retrofit2.Call<okhttp3.ResponseBody> call, Throwable t) {
                        checkCompletion();
                    }

                    private void checkCompletion() {
                        if (count.decrementAndGet() == 0) {
                            loadMailbox(); // Reload list
                        }
                    }
                });
            }
        }

        private void loadMailbox() {
            Long userId = new com.vn.jet.mosco.utils.SessionManager(requireContext()).getUserId();
            if (userId == null)
                return;

            com.vn.jet.mosco.network.GameApiService apiService = com.vn.jet.mosco.network.ApiClient
                    .getClient(requireContext()).create(com.vn.jet.mosco.network.GameApiService.class);
            apiService.getUserMails(userId).enqueue(new retrofit2.Callback<List<com.vn.jet.mosco.model.UserMail>>() {
                @Override
                public void onResponse(retrofit2.Call<List<com.vn.jet.mosco.model.UserMail>> call,
                        retrofit2.Response<List<com.vn.jet.mosco.model.UserMail>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        originalMails = response.body();
                        applyFilters();
                    }
                }

                @Override
                public void onFailure(retrofit2.Call<List<com.vn.jet.mosco.model.UserMail>> call, Throwable t) {
                    Toast.makeText(requireContext(), getString(R.string.collection_msg_error_mailbox),
                            Toast.LENGTH_SHORT).show();
                }
            });
        }

        private void applyFilters() {
            if (originalMails == null)
                return;
            com.vn.jet.mosco.view.InventoryFilterBar filterBar = getView() != null ? getView().findViewById(R.id.filter_bar_system_mails) : null;
            String currentSort = filterBar != null ? filterBar.getSortOption() : SORT_NEWEST;

            List<com.vn.jet.mosco.model.UserMail> filtered = new ArrayList<>();
            for (com.vn.jet.mosco.model.UserMail m : originalMails) {
                if (!m.isReceived()) {
                    if (mailboxFilter.isEmpty()) {
                        filtered.add(m);
                    } else {
                        // Tương lai: Lọc theo Type nếu cần
                        filtered.add(m);
                    }
                }
            }

            filtered.sort((a, b) -> {
                int res;
                if (SORT_LOWEST_NO.equals(currentSort)) {
                    res = Integer.compare(a.getQuantity() != null ? a.getQuantity() : 0,
                            b.getQuantity() != null ? b.getQuantity() : 0);
                } else if (SORT_HIGHEST_NO.equals(currentSort)) {
                    res = Integer.compare(b.getQuantity() != null ? b.getQuantity() : 0,
                            a.getQuantity() != null ? a.getQuantity() : 0);
                } else {
                    res = b.getId().compareTo(a.getId());
                }
                return res;
            });

            if (adapter != null)
                adapter.updateData(filtered);
            if (tvCount != null) {
                tvCount.setText(String.valueOf(filtered.size()));
            }
        }
    }

    // ==========================================
    // SUB-TAB 2: QUÀ ĐÃ NHẬN TỪ NGƯỜI CHƠI (PLAYER GIFTS)
    // ==========================================
    public static class PlayerGiftsFragment extends Fragment {
        private final Set<String> giftFilter = new LinkedHashSet<>();
        private com.vn.jet.mosco.adapter.GiftHistoryAdapter giftHistoryAdapter;
        private TextView tvCount;
        private List<JSONObject> originalGifts = new ArrayList<>();

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_mailbox_player_gifts, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            tvCount = view.findViewById(R.id.tv_player_gifts_count);

            // Cấu hình Filter Bar (Smart Pill) cho danh sách quà nhận từ bạn bè
            com.vn.jet.mosco.view.InventoryFilterBar filterBar = view.findViewById(R.id.filter_bar_player_gifts);
            if (filterBar != null) {
                filterBar.setSortOptions(new String[] { SORT_NEWEST, SORT_LOWEST_NO, SORT_HIGHEST_NO });
                filterBar.setListener(new com.vn.jet.mosco.view.InventoryFilterBar.OnFilterChangeListener() {
                    @Override
                    public void onFilterChanged(String sortOption, boolean isAscending) {
                        applyFilters();
                    }

                    @Override
                    public void onFilterRequested() {
                        showFilterBottomSheet(PlayerGiftsFragment.this, buildMailboxCategories(), giftFilter, filterBar, filterBar.getSortOptions(), PlayerGiftsFragment.this::applyFilters);
                    }
                });
            }

            RecyclerView rvGifts = view.findViewById(R.id.rv_player_gifts);
            if (rvGifts != null) {
                rvGifts.setLayoutManager(new LinearLayoutManager(requireContext()));
                giftHistoryAdapter = new com.vn.jet.mosco.adapter.GiftHistoryAdapter(new ArrayList<>(), true);
                rvGifts.setAdapter(giftHistoryAdapter);
            }

            loadPlayerGifts();
        }

        private void loadPlayerGifts() {
            if (giftHistoryAdapter != null) {
                giftHistoryAdapter.setLoading(true);
            }

            com.vn.jet.mosco.network.GameApiService apiService = com.vn.jet.mosco.network.ApiClient
                    .getClient(requireContext())
                    .create(com.vn.jet.mosco.network.GameApiService.class);

            apiService.getReceivedGifts().enqueue(new retrofit2.Callback<okhttp3.ResponseBody>() {
                @Override
                public void onResponse(retrofit2.Call<okhttp3.ResponseBody> call,
                        retrofit2.Response<okhttp3.ResponseBody> response) {
                    try {
                        if (response.isSuccessful() && response.body() != null) {
                            JSONObject json = new JSONObject(response.body().string());
                            JSONArray dataArr = json.optJSONArray("data");
                            List<JSONObject> gifts = new ArrayList<>();
                            if (dataArr != null) {
                                for (int i = 0; i < dataArr.length(); i++) {
                                    gifts.add(dataArr.getJSONObject(i));
                                }
                            }
                            originalGifts = gifts;
                            applyFilters();
                        }
                    } catch (Exception e) {
                        Log.e("PlayerGiftsFragment", "Lỗi phân tích quà tặng bạn bè", e);
                    }
                }

                @Override
                public void onFailure(retrofit2.Call<okhttp3.ResponseBody> call, Throwable t) {
                    Log.e("PlayerGiftsFragment", "Lỗi kết nối tải quà nhận", t);
                }
            });
        }

        private void applyFilters() {
            if (originalGifts == null) return;
            com.vn.jet.mosco.view.InventoryFilterBar filterBar = getView() != null ? getView().findViewById(R.id.filter_bar_player_gifts) : null;
            String currentSort = filterBar != null ? filterBar.getSortOption() : SORT_NEWEST;

            List<JSONObject> filtered = new ArrayList<>();
            for (JSONObject g : originalGifts) {
                if (giftFilter.isEmpty()) {
                    filtered.add(g);
                } else {
                    filtered.add(g);
                }
            }

            // Sắp xếp quà tặng theo thời gian hoặc độ hiếm cấp độ thẻ được tặng
            filtered.sort((a, b) -> {
                int res;
                if (SORT_LOWEST_NO.equals(currentSort)) {
                    res = Integer.compare(a.optInt("cardLevel", 0), b.optInt("cardLevel", 0));
                } else if (SORT_HIGHEST_NO.equals(currentSort)) {
                    res = Integer.compare(b.optInt("cardLevel", 0), a.optInt("cardLevel", 0));
                } else {
                    String ca = a.optString("createdAt", "");
                    String cb = b.optString("createdAt", "");
                    res = cb.compareTo(ca); // Mới nhất lên đầu
                }
                return res;
            });

            if (giftHistoryAdapter != null) {
                giftHistoryAdapter.updateData(filtered);
            }

            View emptyView = getView() != null ? getView().findViewById(R.id.tv_no_gifts) : null;
            if (emptyView != null) {
                emptyView.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
            }

            if (tvCount != null) {
                tvCount.setText(String.valueOf(filtered.size()));
            }
        }
    }

    // ==========================================
    // SUB-TAB 3: DANH SÁCH TIN NHẮN RIÊNG TƯ (CHAT INBOX)
    // ==========================================
    public static class PrivateChatListFragment extends Fragment {
        private final Set<String> chatFilter = new LinkedHashSet<>();
        private com.vn.jet.mosco.adapter.ConversationAdapter adapter;
        private TextView tvCount;
        private List<com.vn.jet.mosco.adapter.ConversationAdapter.ConversationWrapper> originalConversations = new ArrayList<>();

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_mailbox_private_chats, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            tvCount = view.findViewById(R.id.tv_private_chats_count);

            com.vn.jet.mosco.view.InventoryFilterBar filterBar = view.findViewById(R.id.filter_bar_private_chats);
            if (filterBar != null) {
                filterBar.setSortOptions(new String[] { SORT_NEWEST });
                filterBar.setListener(new com.vn.jet.mosco.view.InventoryFilterBar.OnFilterChangeListener() {
                    @Override
                    public void onFilterChanged(String sortOption, boolean isAscending) {
                        applyFilters();
                    }

                    @Override
                    public void onFilterRequested() {
                        showFilterBottomSheet(PrivateChatListFragment.this, buildMailboxCategories(), chatFilter, filterBar, filterBar.getSortOptions(), PrivateChatListFragment.this::applyFilters);
                    }
                });
            }

            RecyclerView rvChats = view.findViewById(R.id.rv_private_chats);
            if (rvChats != null) {
                rvChats.setLayoutManager(new LinearLayoutManager(requireContext()));
            }

            loadConversations();
        }

        private void loadConversations() {
            Context context = getContext();
            if (context == null) return;

            String myId = String.valueOf(new com.vn.jet.mosco.utils.SessionManager(context).getUserId());
            if (myId == null) return;

            // Chạy ngầm trong luồng background (Background thread) để tránh chặn đứng Main Thread (ANR)
            new Thread(() -> {
                try {
                    com.vn.jet.mosco.database.AppDatabase db = com.vn.jet.mosco.database.AppDatabase.getInstance(context);
                    com.vn.jet.mosco.database.MessageDao dao = db.messageDao();
                    List<PrivateChatMessage> lastMessages = dao.getRecentConversations(myId);

                    List<com.vn.jet.mosco.adapter.ConversationAdapter.ConversationWrapper> wrappers = new ArrayList<>();
                    for (PrivateChatMessage msg : lastMessages) {
                        String partnerId = myId.equals(msg.getSenderId()) ? msg.getReceiverId() : msg.getSenderId();

                        String partnerName = null;
                        String partnerAvatar = null;

                        if (myId.equals(msg.getSenderId())) {
                            // Nếu tin nhắn cuối do mình gửi, tìm tên/avatar từ tin nhắn đối tác gửi trước đó
                            partnerName = dao.getPartnerName(partnerId);
                            partnerAvatar = dao.getPartnerAvatar(partnerId);
                        } else {
                            // Nếu tin nhắn cuối do đối tác gửi, lấy trực tiếp từ tin nhắn
                            partnerName = msg.getSenderName();
                            partnerAvatar = msg.getAvatarId();
                        }

                        if (partnerAvatar == null) partnerAvatar = "1";

                        wrappers.add(new com.vn.jet.mosco.adapter.ConversationAdapter.ConversationWrapper(
                                msg, partnerId, partnerName, partnerAvatar
                        ));
                    }

                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (!isAdded()) return;
                            originalConversations = wrappers;
                            applyFilters();
                            // Sau khi tải dữ liệu Local-First tức thì, chạy đồng bộ trạng thái online glow ngầm từ API
                            fetchOnlineStatus();
                        });
                    }
                } catch (Exception e) {
                    Log.e("PrivateChatListFragment", "Lỗi tải danh sách tin nhắn riêng tư", e);
                }
            }).start();
        }

        private void fetchOnlineStatus() {
            Context context = getContext();
            if (context == null) return;

            com.vn.jet.mosco.network.GameApiService apiService = com.vn.jet.mosco.network.ApiClient
                    .getClient(context).create(com.vn.jet.mosco.network.GameApiService.class);

            apiService.getFriendList().enqueue(new retrofit2.Callback<okhttp3.ResponseBody>() {
                @Override
                public void onResponse(retrofit2.Call<okhttp3.ResponseBody> call,
                        retrofit2.Response<okhttp3.ResponseBody> response) {
                    try {
                        if (response.isSuccessful() && response.body() != null) {
                            JSONObject json = new JSONObject(response.body().string());
                            JSONArray friendsArr = json.optJSONArray("data");
                            if (friendsArr != null) {
                                Map<String, Boolean> onlineMap = new HashMap<>();
                                for (int i = 0; i < friendsArr.length(); i++) {
                                    JSONObject friendObj = friendsArr.getJSONObject(i);
                                    String idStr = String.valueOf(friendObj.optLong("userId"));
                                    boolean online = friendObj.optBoolean("online", false);
                                    onlineMap.put(idStr, online);
                                }

                                boolean updated = false;
                                for (com.vn.jet.mosco.adapter.ConversationAdapter.ConversationWrapper w : originalConversations) {
                                    Boolean online = onlineMap.get(w.getPartnerId());
                                    if (online != null && online != w.isOnline()) {
                                        w.setOnline(online);
                                        updated = true;
                                    }
                                }

                                if (updated && adapter != null) {
                                    adapter.notifyDataSetChanged();
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e("PrivateChatListFragment", "Lỗi phân tích online status", e);
                    }
                }

                @Override
                public void onFailure(retrofit2.Call<okhttp3.ResponseBody> call, Throwable t) {}
            });
        }

        private void applyFilters() {
            if (originalConversations == null) return;
            com.vn.jet.mosco.view.InventoryFilterBar filterBar = getView() != null ? getView().findViewById(R.id.filter_bar_private_chats) : null;

            List<com.vn.jet.mosco.adapter.ConversationAdapter.ConversationWrapper> filtered = new ArrayList<>();
            for (com.vn.jet.mosco.adapter.ConversationAdapter.ConversationWrapper w : originalConversations) {
                if (chatFilter.isEmpty()) {
                    filtered.add(w);
                } else {
                    filtered.add(w);
                }
            }

            // Mặc định sắp xếp theo thời gian tin nhắn mới nhất lên đầu
            filtered.sort((a, b) -> Long.compare(b.getLastMessage().getTimestamp(), a.getLastMessage().getTimestamp()));

            if (adapter == null) {
                Context context = getContext();
                if (context == null) return;
                String myId = String.valueOf(new com.vn.jet.mosco.utils.SessionManager(context).getUserId());

                adapter = new com.vn.jet.mosco.adapter.ConversationAdapter(myId, wrapper -> {
                    long partnerId;
                    try {
                        partnerId = Long.parseLong(wrapper.getPartnerId());
                    } catch (NumberFormatException e) {
                        partnerId = -1L;
                    }
                    com.vn.jet.mosco.utils.NavigationUtils.openPrivateChat(requireActivity(), partnerId, wrapper.getPartnerName(), wrapper.getPartnerAvatar());
                });

                RecyclerView rv = getView().findViewById(R.id.rv_private_chats);
                if (rv != null) {
                    rv.setAdapter(adapter);
                }
            }

            adapter.updateData(filtered);

            View emptyView = getView() != null ? getView().findViewById(R.id.tv_no_chats) : null;
            if (emptyView != null) {
                emptyView.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
            }

            if (tvCount != null) {
                tvCount.setText(String.valueOf(filtered.size()));
            }
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
            return new ViewHolder(
                    LayoutInflater.from(parent.getContext()).inflate(R.layout.item_mailbox, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            com.vn.jet.mosco.model.UserMail mail = list.get(position);
            holder.tvTitle.setText(mail.getTitle());
            holder.tvQty.setText(mail.getQuantity() != null
                    ? "x" + NumberUtils.format(holder.itemView.getContext(), mail.getQuantity())
                    : "");
            holder.tvDesc.setText(mail.getContent());

            // Format time if possible, or use raw
            holder.tvTime.setText(mail.getCreatedAt() != null ? mail.getCreatedAt().substring(0, 10) : "");

            holder.ivIcon.setImageResource(R.drawable.item_shop_demo);
            holder.itemView.setOnClickListener(v -> {
                if (listener != null)
                    listener.onMailClick(mail);
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

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
        private final String[] SORT_OPTIONS = {
                SORT_NEWEST, SORT_BADGE, SORT_LEVEL, SORT_ARTIST, SORT_CLASS, SORT_SEASON
        };
        private com.vn.jet.mosco.view.InventoryFilterBar filterBar;
        private RecyclerView rvObjets;
        private TextView tvCount;
        private com.vn.jet.mosco.adapter.UnifiedCardAdapter adapter;
        private List<com.vn.jet.mosco.model.CardDisplayItem> originalObjets = new ArrayList<>();

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                @Nullable Bundle savedInstanceState) {
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

            // [QUIET LUXURY] Smart Pill — bấm mở Bottom Sheet tổng hợp Sort + Filter
            filterBar = view.findViewById(R.id.filter_bar_objets);
            if (filterBar != null) {
                filterBar.setSortOptions(SORT_OPTIONS);
                filterBar.setListener(new com.vn.jet.mosco.view.InventoryFilterBar.OnFilterChangeListener() {
                    @Override
                    public void onFilterChanged(String sortOption, boolean isAscending) {
                        applyFilters();
                    }

                    @Override
                    public void onFilterRequested() {
                        new Thread(() -> {
                            List<FilterCategory> categories = buildObjetCategories(requireContext());
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    showFilterBottomSheet(ObjetsFragment.this, categories,
                                            objetFilter, filterBar, SORT_OPTIONS,
                                            ObjetsFragment.this::applyFilters);
                                });
                            }
                        }).start();
                    }
                });
            }

            // RecyclerView — load REAL data from server
            rvObjets = view.findViewById(R.id.rv_objets);
            rvObjets.setLayoutManager(new GridLayoutManager(requireContext(), 3));
            // [QUIET LUXURY] Hiệu ứng thẻ mờ dần và nhỏ lại ở 2 viền
            rvObjets.addOnScrollListener(new com.vn.jet.mosco.utils.GridScaleScrollListener(0.85f));
            // [QUIET LUXURY] Áp dụng phanh ABS: Giới hạn tốc độ lướt
            com.vn.jet.mosco.utils.ViewUtils.limitFlingVelocity(rvObjets);

            adapter = new com.vn.jet.mosco.adapter.UnifiedCardAdapter(
                    new ArrayList<>(), rvObjets,
                    com.vn.jet.mosco.adapter.UnifiedCardAdapter.DisplayMode.INVENTORY,
                    item -> {
                        Context ctx = requireContext();
                        if (ctx == null)
                            return;

                        // Dùng trực tiếp CardDisplayItem — không cần chuyển đổi qua CollectionEntry nữa
                        com.vn.jet.mosco.utils.CollectionDetailBinder.showDetail(ctx, item, false, this::applyFilters);
                    });
            rvObjets.setAdapter(adapter);

            loadObjets(false);
        }

        /**
         * Smart Load: Ưu tiên nạp từ cache để UI hiện lên TỨC THÌ (Instant Load).
         * 
         * @param forceFromServer Nếu true sẽ bỏ qua cache, nạp thẳng từ API.
         */
        private void loadObjets(boolean forceFromServer) {
            Long userId = new com.vn.jet.mosco.utils.SessionManager(requireContext()).getUserId();
            if (userId == null)
                return;

            List<com.vn.jet.mosco.utils.DatabaseLoader.UserInventoryItem> cache = com.vn.jet.mosco.utils.DatabaseLoader.cachedUserInventory;
            if (forceFromServer || cache == null || cache.isEmpty()) {
                if (adapter != null) {
                    adapter.setLoading(true);
                }
            }

            // ── 1. KIỂM TRA CACHE (INSTANT LOAD) ─────────────────────────
            if (!forceFromServer && cache != null && !cache.isEmpty()) {
                android.util.Log.d("ObjetsFragment", "Instant Load from Galactic Cache: " + cache.size() + " items");
                // isSilent = false: lần đầu vào tab, chưa có gì trên screen cả → cho phép show skeleton nếu cần
                processAndDisplayInventory(cache, false);
                // Vẫn gọi ngầm để update nếu có gì mới (Silent Update)
            }

            // ── 2. NẠP TỪ SERVER (BACKGROUND) ────────────────────────────
            com.vn.jet.mosco.network.GameApiService apiService = com.vn.jet.mosco.network.ApiClient
                    .getClient(requireContext()).create(com.vn.jet.mosco.network.GameApiService.class);
            apiService.getUserCards(userId).enqueue(new retrofit2.Callback<List<com.vn.jet.mosco.model.UserCard>>() {
                @Override
                public void onResponse(retrofit2.Call<List<com.vn.jet.mosco.model.UserCard>> call,
                        retrofit2.Response<List<com.vn.jet.mosco.model.UserCard>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        List<com.vn.jet.mosco.model.UserCard> userCards = response.body();
                        new Thread(() -> {
                            List<com.vn.jet.mosco.utils.DatabaseLoader.UserInventoryItem> items = new ArrayList<>(
                                    userCards.size());
                            for (com.vn.jet.mosco.model.UserCard uc : userCards) {
                                items.add(com.vn.jet.mosco.utils.DatabaseLoader.UserInventoryItem.fromUserCard(uc));
                            }
                            if (getActivity() != null) {
                                // Server response: đã có data trên màn hình rồi → silent refresh (isSilent = true)
                                boolean hasCurrent = (originalObjets != null && !originalObjets.isEmpty());
                                getActivity().runOnUiThread(() -> processAndDisplayInventory(items, hasCurrent));
                            }
                        }).start();
                    }
                }

                @Override
                public void onFailure(retrofit2.Call<List<com.vn.jet.mosco.model.UserCard>> call, Throwable t) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (adapter != null) {
                                adapter.setLoading(false);
                            }
                        });
                    }
                }
            });
        }

        /**
         * Xử lý mapping và hiển thị dữ liệu lên RecyclerView.
         * @param isSilent Nếu true: không hiển skeleton (silent refresh khi đã có data)
         */
        private void processAndDisplayInventory(List<com.vn.jet.mosco.utils.DatabaseLoader.UserInventoryItem> items, boolean isSilent) {
            if (items == null)
                return;
            new Thread(() -> {
                List<com.vn.jet.mosco.model.CardDisplayItem> displayItems = new ArrayList<>();
                for (com.vn.jet.mosco.utils.DatabaseLoader.UserInventoryItem uc : items) {
                    com.vn.jet.mosco.model.CardDisplayItem displayItem = com.vn.jet.mosco.model.CardDisplayItem
                            .fromCacheItem(uc);
                    displayItem.setCardClass(mapClassToTypeKey(uc.cardClass));
                    displayItems.add(displayItem);
                }

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        originalObjets = displayItems;
                        applyFilters(isSilent);
                    });
                }
            }).start();
        }

        private void applyFilters() {
            applyFilters(false);
        }

        private void applyFilters(boolean isSilent) {
            // Chỉ hiển Skeleton khi THỰC SỰ chưa có data (lần mở đầu tiên)
            // Tránh flash khi đang refresh ngầm (silent update từ server)
            if (!isSilent && adapter != null && (originalObjets == null || originalObjets.isEmpty())) {
                adapter.setLoading(true);
            }

            new Thread(() -> {
                // Bỏ Thread.sleep(250) nhân tạo — gây flash kép khó chịu

                List<com.vn.jet.mosco.model.CardDisplayItem> filtered = new ArrayList<>();
                String currentSort = (filterBar != null) ? filterBar.getSortOption() : "Newest";
                boolean isAsc = (filterBar != null) && filterBar.isAscending();

                java.util.Set<String> selArtists = new java.util.HashSet<>();
                java.util.Set<String> selClasses = new java.util.HashSet<>();
                java.util.Set<String> selSeasons = new java.util.HashSet<>();

                for (String f : objetFilter) {
                    if (isArtist(f))
                        selArtists.add(f.toLowerCase());
                    else if (isClass(f))
                        selClasses.add(f.toLowerCase());
                    else
                        selSeasons.add(f.toLowerCase());
                }

                for (com.vn.jet.mosco.model.CardDisplayItem item : originalObjets) {
                    if (objetFilter.isEmpty()) {
                        filtered.add(item);
                        continue;
                    }

                    String member = item.getMember();
                    String season = item.getSeason();
                    String rawClass = item.getCardClass();
                    String mappedClass = mapClassToTypeKey(rawClass);

                    boolean matchArtist = selArtists.isEmpty()
                            || (member != null && selArtists.contains(member.toLowerCase()));
                    boolean matchClass = selClasses.isEmpty()
                            || (rawClass != null && selClasses.contains(rawClass.toLowerCase())) || (mappedClass != null
                                    && selClasses.contains(mappedClass.toLowerCase().replaceAll("\\s+", "")));
                    boolean matchSeason = selSeasons.isEmpty()
                            || (season != null && selSeasons.contains(season.toLowerCase()));

                    if (matchArtist && matchClass && matchSeason) {
                        filtered.add(item);
                    }
                }

                filtered.sort((a, b) -> {
                    // [PRIORITY] Pinned cards always on top (Only if sorting by newest)
                    if (SORT_NEWEST.equals(currentSort)) {
                        boolean pinA = com.vn.jet.mosco.utils.PinManager.isPinned(requireContext(), String.valueOf(a.getId()));
                        boolean pinB = com.vn.jet.mosco.utils.PinManager.isPinned(requireContext(), String.valueOf(b.getId()));
                        if (pinA != pinB) return pinA ? -1 : 1;
                    }

                    int res = 0;
                    if (SORT_NEWEST.equals(currentSort)) {
                        String t1 = a.getCreatedAt() != null ? a.getCreatedAt() : "";
                        String t2 = b.getCreatedAt() != null ? b.getCreatedAt() : "";
                        if (!t1.isEmpty() && !t2.isEmpty()) {
                            res = t1.compareTo(t2);
                        }
                        if (res == 0) {
                            res = Long.compare(a.getId(), b.getId());
                        }
                        if (res == 0) {
                            res = compareNatural(a.getCollectionNo(), b.getCollectionNo());
                        }
                    } else if (SORT_BADGE.equals(currentSort))
                        res = Integer.compare(a.getUpgradeLevel(), b.getUpgradeLevel());
                    else if (SORT_LEVEL.equals(currentSort))
                        res = Integer.compare(a.getLevel(), b.getLevel());
                    else if (SORT_ARTIST.equals(currentSort)) {
                        String m1 = a.getMember() != null ? a.getMember() : "";
                        String m2 = b.getMember() != null ? b.getMember() : "";
                        res = m1.compareToIgnoreCase(m2);
                    } else if (SORT_CLASS.equals(currentSort)) {
                        int r1 = getCardClassRank(a.getCardClass());
                        int r2 = getCardClassRank(b.getCardClass());
                        res = Integer.compare(r1, r2);
                    } else if ("Season".equals(currentSort)) {
                        String s1 = a.getSeason() != null ? a.getSeason() : "";
                        String s2 = b.getSeason() != null ? b.getSeason() : "";
                        res = s1.compareToIgnoreCase(s2);
                    } else
                        res = Long.compare(a.getId(), b.getId());

                    return isAsc ? res : -res;
                });

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (adapter != null) {
                            adapter.updateData(filtered);
                        }
                        if (tvCount != null) {
                            tvCount.setText(String.valueOf(filtered.size()));
                        }
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
        private final String[] SORT_OPTIONS = { SORT_NEWEST, SORT_LOWEST_NO, SORT_HIGHEST_NO };
        private com.vn.jet.mosco.view.InventoryFilterBar filterBar;
        private TextView tvCount;
        private RecyclerView rvItems;
        private ItemsAdapter adapter;
        private List<com.vn.jet.mosco.model.UserItem> originalItems = new ArrayList<>();

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_collection_items, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            // [QUIET LUXURY] Smart Pill — bấm mở Bottom Sheet tổng hợp Sort + Filter
            filterBar = view.findViewById(R.id.filter_bar_items);
            if (filterBar != null) {
                filterBar.setSortOptions(SORT_OPTIONS);
                filterBar.setListener(new com.vn.jet.mosco.view.InventoryFilterBar.OnFilterChangeListener() {
                    @Override
                    public void onFilterChanged(String sortOption, boolean isAscending) {
                        applyFilters();
                    }

                    @Override
                    public void onFilterRequested() {
                        showFilterBottomSheet(ItemsFragment.this, buildItemsCategories(),
                                itemsFilter, filterBar, SORT_OPTIONS,
                                ItemsFragment.this::applyFilters);
                    }
                });
            }
            tvCount = view.findViewById(R.id.tv_items_count_title);

            // RecyclerView
            rvItems = view.findViewById(R.id.rv_items);
            rvItems.setLayoutManager(new GridLayoutManager(requireContext(), 3));
            rvItems.setHasFixedSize(true);
            rvItems.setItemViewCacheSize(20);
            rvItems.setDrawingCacheEnabled(true);
            rvItems.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);

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
                Toast.makeText(requireContext(), getString(R.string.reveal_error_only_packs), Toast.LENGTH_SHORT)
                        .show();
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
            final int[] qty = { 1 };

            tvName.setText(item.getName());
            tvDesc.setText(item.getDescription() != null ? item.getDescription() : "");
            tvAvailQty.setText(getString(R.string.items_label_available,
                    (item.getQuantity() != null ? NumberUtils.format(requireContext(), item.getQuantity()) : "0")));

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
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {
                    if (s.length() == 0)
                        return;
                    try {
                        int val = Integer.parseInt(s.toString());
                        if (val < 1)
                            qty[0] = 1;
                        else if (val > maxQty)
                            qty[0] = maxQty;
                        else
                            qty[0] = val;
                    } catch (Exception e) {
                        qty[0] = 1;
                    }
                }
            });

            btnClose.setOnClickListener(v -> dialog.dismiss());

            btnUse.setOnClickListener(v -> {
                dialog.dismiss();
                Toast.makeText(requireContext(),
                        getString(R.string.shop_format_used, qty[0], item.getName()), Toast.LENGTH_SHORT).show();
                // TODO: Call API to use items, then refresh inventory
            });

            dialog.show();
        }

        private void loadInventory() {
            Long userId = new com.vn.jet.mosco.utils.SessionManager(requireContext()).getUserId();
            if (userId == null)
                return;

            com.vn.jet.mosco.network.GameApiService apiService = com.vn.jet.mosco.network.ApiClient
                    .getClient(requireContext()).create(com.vn.jet.mosco.network.GameApiService.class);
            apiService.getUserItems(userId).enqueue(new retrofit2.Callback<List<com.vn.jet.mosco.model.UserItem>>() {
                @Override
                public void onResponse(retrofit2.Call<List<com.vn.jet.mosco.model.UserItem>> call,
                        retrofit2.Response<List<com.vn.jet.mosco.model.UserItem>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        originalItems = response.body();
                        applyFilters();
                    }
                }

                @Override
                public void onFailure(retrofit2.Call<List<com.vn.jet.mosco.model.UserItem>> call, Throwable t) {
                    Toast.makeText(requireContext(), getString(R.string.collection_msg_error_inventory),
                            Toast.LENGTH_SHORT).show();
                }
            });
        }

        private void applyFilters() {
            if (originalItems == null || !isAdded())
                return;
            List<com.vn.jet.mosco.model.UserItem> filtered = new ArrayList<>();
            String currentSort = (filterBar != null) ? filterBar.getSortOption() : "Newest";
            boolean isAsc = (filterBar != null) && filterBar.isAscending();

            for (com.vn.jet.mosco.model.UserItem item : originalItems) {
                if (item.getName() == null || item.getName().isEmpty() || item.getName().equalsIgnoreCase("Unknown"))
                    continue;

                if (itemsFilter.isEmpty()) {
                    filtered.add(item);
                } else {
                    String type = item.getType() != null ? item.getType().toUpperCase() : "";
                    boolean match = false;
                    for (String f : itemsFilter) {
                        if (f.equalsIgnoreCase(type))
                            match = true;
                        // Map internal type to UI type labels if needed
                        if (f.equalsIgnoreCase("Materials") && type.equals("MATERIAL"))
                            match = true;
                        if (f.equalsIgnoreCase("Consumables") && (type.equals("BUFF") || type.equals("CONSUMABLE")))
                            match = true;
                    }
                    if (match)
                        filtered.add(item);
                }
            }

            filtered.sort((a, b) -> {
                int res = 0;
                if (SORT_LOWEST_NO.equals(currentSort) || SORT_HIGHEST_NO.equals(currentSort)) {
                    res = Integer.compare(a.getQuantity() != null ? a.getQuantity() : 0,
                            b.getQuantity() != null ? b.getQuantity() : 0);
                } else {
                    res = a.getId().compareTo(b.getId());
                }
                return isAsc ? res : -res;
            });

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (adapter != null) {
                        adapter.updateData(filtered);
                    }
                    if (tvCount != null) {
                        tvCount.setText(String.valueOf(filtered.size()));
                    }
                });
            }
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
            return new ViewHolder(
                    LayoutInflater.from(parent.getContext()).inflate(R.layout.item_inventory_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            com.vn.jet.mosco.model.UserItem item = list.get(position);
            holder.tvName.setText(item.getName());
            holder.tvDesc.setText(item.getDescription() != null ? item.getDescription() : "");
            holder.tvQty.setText("x" + NumberUtils.format(holder.itemView.getContext(),
                    item.getQuantity() != null ? item.getQuantity() : 0));

            // Sử dụng GlideBindingAdapter đã có Local-First
            com.vn.jet.mosco.utils.GlideBindingAdapter.loadImage(holder.ivImage,
                    item.getImageUri() != null && !item.getImageUri().isEmpty() ? item.getImageUri() : "", true);

            holder.itemView.setOnClickListener(v -> {
                if (listener != null)
                    listener.onItemClick(item);
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

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
        private final String[] SORT_OPTIONS = {
                SORT_NEWEST, SORT_BADGE, SORT_LEVEL, SORT_ARTIST, SORT_CLASS, SORT_SEASON,
                SORT_STATUS
        };
        private com.vn.jet.mosco.view.InventoryFilterBar filterBar;
        private RecyclerView rvAlbum;
        private com.vn.jet.mosco.adapter.UnifiedCardAdapter adapter;
        private TextView tvProgress, tvCount;
        private android.widget.ProgressBar progressBar;
        private List<com.vn.jet.mosco.model.CardDisplayItem> originalEntries = new ArrayList<>();
        private int totalCards = 0;
        private int ownedCount = 0;

        private final android.content.BroadcastReceiver inventoryUpdateReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(Context context, android.content.Intent intent) {
                if ("ACTION_INVENTORY_UPDATED".equals(intent.getAction())) {
                    loadCollectionBook();
                }
            }
        };

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_collection_album, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            // tvProgress = view.findViewById(R.id.tv_album_progress);
            tvCount = view.findViewById(R.id.tv_album_count);
            // progressBar = view.findViewById(R.id.progress_album);

            // [QUIET LUXURY] Smart Pill — bấm mở Bottom Sheet tổng hợp Sort + Filter
            filterBar = view.findViewById(R.id.filter_bar_album);
            if (filterBar != null) {
                filterBar.setSortOptions(SORT_OPTIONS);
                filterBar.setListener(new com.vn.jet.mosco.view.InventoryFilterBar.OnFilterChangeListener() {
                    @Override
                    public void onFilterChanged(String sortOption, boolean isAscending) {
                        applyFilters();
                    }

                    @Override
                    public void onFilterRequested() {
                        new Thread(() -> {
                            List<FilterCategory> categories = buildAlbumCategories(requireContext());
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    showFilterBottomSheet(AlbumFragment.this, categories,
                                            albumFilter, filterBar, SORT_OPTIONS,
                                            AlbumFragment.this::applyFilters);
                                });
                            }
                        }).start();
                    }
                });
            }

            // RecyclerView
            rvAlbum = view.findViewById(R.id.rv_album);
            rvAlbum.setLayoutManager(new GridLayoutManager(requireContext(), 3));
            // [QUIET LUXURY] Áp dụng phanh ABS
            com.vn.jet.mosco.utils.ViewUtils.limitFlingVelocity(rvAlbum);

            adapter = new com.vn.jet.mosco.adapter.UnifiedCardAdapter(
                    new ArrayList<>(), rvAlbum,
                    com.vn.jet.mosco.adapter.UnifiedCardAdapter.DisplayMode.ALBUM,
                    this::onBookCardClicked);
            rvAlbum.setAdapter(adapter);

            loadCollectionBook();
        }

        @Override
        public void onResume() {
            super.onResume();
            if (getContext() != null) {
                androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(getContext())
                        .registerReceiver(inventoryUpdateReceiver,
                                new android.content.IntentFilter("ACTION_INVENTORY_UPDATED"));
            }
        }

        @Override
        public void onPause() {
            super.onPause();
            if (getContext() != null) {
                androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(getContext())
                        .unregisterReceiver(inventoryUpdateReceiver);
            }
        }

        /**
         * Xử lý click thẻ trong Album.
         * Luôn mở Dialog chi tiết thẻ (hỗ trợ cả thẻ chưa sở hữu với icon ổ khóa).
         */
        private void onBookCardClicked(com.vn.jet.mosco.model.CardDisplayItem item) {
            if (requireContext() != null && item != null) {
                com.vn.jet.mosco.utils.CollectionDetailBinder.showDetail(requireContext(), item, true, this::applyFilters);
            }
        }

        /**
         * Gọi API lấy dữ liệu Collection Book từ Server.
         */
        private void loadCollectionBook() {
            Long userId = new com.vn.jet.mosco.utils.SessionManager(requireContext()).getUserId();
            if (userId == null)
                return;

            android.util.Log.d("AlbumFragment", "Loading collection book for user: " + userId);

            com.vn.jet.mosco.network.GameApiService apiService = com.vn.jet.mosco.network.ApiClient
                    .getClient(requireContext())
                    .create(com.vn.jet.mosco.network.GameApiService.class);

            apiService.getCollectionBook(userId)
                    .enqueue(new retrofit2.Callback<com.vn.jet.mosco.model.CollectionBookResponse>() {
                        @Override
                        public void onResponse(retrofit2.Call<com.vn.jet.mosco.model.CollectionBookResponse> call,
                                retrofit2.Response<com.vn.jet.mosco.model.CollectionBookResponse> response) {
                            if (!isAdded())
                                return;
                            if (response.isSuccessful() && response.body() != null) {
                                com.vn.jet.mosco.model.CollectionBookResponse book = response.body();
                                totalCards = book.getTotalCards();
                                ownedCount = book.getOwnedCount();

                                // Chuyển đổi CollectionEntry -> CardDisplayItem (Unified Model)
                                List<com.vn.jet.mosco.model.CollectionEntry> rawEntries = book.getEntries() != null
                                        ? book.getEntries()
                                        : new ArrayList<>();
                                originalEntries = new ArrayList<>();
                                for (com.vn.jet.mosco.model.CollectionEntry entry : rawEntries) {
                                    originalEntries
                                            .add(com.vn.jet.mosco.model.CardDisplayItem.fromCollectionEntry(entry));
                                }

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
                                android.util.Log.d("AlbumFragment",
                                        "Loaded " + totalCards + " cards, owned: " + ownedCount);
                            } else {
                                android.util.Log.e("AlbumFragment", "Server error: " + response.code());
                            }
                        }

                        @Override
                        public void onFailure(retrofit2.Call<com.vn.jet.mosco.model.CollectionBookResponse> call,
                                Throwable t) {
                            if (!isAdded())
                                return;
                            android.util.Log.e("AlbumFragment", "API Failure", t);
                            if (requireContext() != null) {
                                Toast.makeText(requireContext(), getString(R.string.collection_msg_error_album),
                                        Toast.LENGTH_SHORT).show();
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
            if (getView() == null || totalCards <= 0)
                return;
            Context ctx = requireContext();
            if (ctx == null)
                return;

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
            // Milestone UI has been removed in Liquid Glass V1.1
        }

        private void claimMilestone(int index, int req) {
            com.vn.jet.mosco.utils.SessionManager session = new com.vn.jet.mosco.utils.SessionManager(requireContext());
            Long userIdLong = session.getUserId();
            String userId = userIdLong != null ? String.valueOf(userIdLong) : "unknown";

            String rewardName = (index == 1) ? "1,000 Coin"
                    : (index == 2) ? "5,000 Coin & 1 Voucher" : "10,000 Coin & 1 Special Card";

            // Gọi Binder cao cấp để hiện hiệu ứng "Nổ quà"
            com.vn.jet.mosco.utils.CollectionRewardBinder.showReward(requireContext(),
                    "Bạn đã đạt cột mốc " + req + " thẻ.\nPhần thưởng: " + rewardName,
                    () -> {
                        // Logic sau khi nhấn THU THẬP
                        requireContext().getSharedPreferences("MoscoCollection", Context.MODE_PRIVATE)
                                .edit().putBoolean("claimed_" + userId + "_ms_" + index, true).apply();

                        updateMilestones(ownedCount);
                    });
        }

        /**
         * Áp dụng bộ lọc + sắp xếp cho danh sách entries.
         */
        private void applyFilters() {
            if (originalEntries == null || !isAdded())
                return;

            new Thread(() -> {
                String currentSort = (filterBar != null) ? filterBar.getSortOption() : "Newest";
                boolean isAsc = (filterBar != null) && filterBar.isAscending();

                java.util.Set<String> selArtists = new java.util.HashSet<>();
                java.util.Set<String> selClasses = new java.util.HashSet<>();
                java.util.Set<String> selSeasons = new java.util.HashSet<>();
                java.util.Set<String> selStatus = new java.util.HashSet<>();

                for (String f : albumFilter) {
                    if (isStatus(f))
                        selStatus.add(f.toLowerCase());
                    else if (isArtist(f))
                        selArtists.add(f.toLowerCase());
                    else if (isClass(f))
                        selClasses.add(f.toLowerCase());
                    else
                        selSeasons.add(f.toLowerCase());
                }

                List<com.vn.jet.mosco.model.CardDisplayItem> filtered = new ArrayList<>();

                for (com.vn.jet.mosco.model.CardDisplayItem item : originalEntries) {
                    boolean matchStatus = selStatus.isEmpty() || selStatus.contains("all")
                            || selStatus.contains("tất cả")
                            || ((selStatus.contains("owned") || selStatus.contains("đã sở hữu")) && item.isOwned())
                            || ((selStatus.contains("missing") || selStatus.contains("chưa sở hữu"))
                                    && !item.isOwned());

                    String member = item.getMember();
                    boolean matchArtist = selArtists.isEmpty()
                            || (member != null && selArtists.contains(member.toLowerCase()));

                    String rawClass = item.getCardClass();
                    String mappedClass = mapClassToTypeKey(rawClass);
                    boolean matchClass = selClasses.isEmpty()
                            || (rawClass != null && selClasses.contains(rawClass.toLowerCase())) || (mappedClass != null
                                    && selClasses.contains(mappedClass.toLowerCase().replaceAll("\\s+", "")));

                    String season = item.getSeason();
                    boolean matchSeason = selSeasons.isEmpty()
                            || (season != null && selSeasons.contains(season.toLowerCase()));

                    if (matchStatus && matchArtist && matchClass && matchSeason) {
                        filtered.add(item);
                    }
                }

                filtered.sort((a, b) -> {
                    int res = 0;
                    if (SORT_NEWEST.equals(currentSort)) {
                        String t1 = a.getCreatedAt() != null ? a.getCreatedAt() : "";
                        String t2 = b.getCreatedAt() != null ? b.getCreatedAt() : "";

                        if (!t1.isEmpty() && !t2.isEmpty()) {
                            res = t1.compareTo(t2);
                        }

                        if (res == 0) {
                            res = Long.compare(a.getUserCardId() != null ? a.getUserCardId() : -1,
                                    b.getUserCardId() != null ? b.getUserCardId() : -1);
                        }

                        if (res == 0) {
                            res = compareNatural(a.getCollectionNo(), b.getCollectionNo());
                        }
                    } else if (SORT_BADGE.equals(currentSort))
                        res = Integer.compare(a.getUpgradeLevel(), b.getUpgradeLevel());
                    else if (SORT_LEVEL.equals(currentSort))
                        res = Integer.compare(a.getLevel(), b.getLevel());
                    else if (SORT_STATUS.equals(currentSort))
                        res = Boolean.compare(a.isOwned(), b.isOwned());
                    else if (SORT_ARTIST.equals(currentSort)) {
                        String m1 = a.getMember() != null ? a.getMember() : "";
                        String m2 = b.getMember() != null ? b.getMember() : "";
                        res = m1.compareToIgnoreCase(m2);
                    } else if (SORT_CLASS.equals(currentSort)) {
                        int r1 = getCardClassRank(a.getCardClass());
                        int r2 = getCardClassRank(b.getCardClass());
                        res = Integer.compare(r1, r2);
                    } else if ("Season".equals(currentSort)) {
                        String s1 = a.getSeason() != null ? a.getSeason() : "";
                        String s2 = b.getSeason() != null ? b.getSeason() : "";
                        res = s1.compareToIgnoreCase(s2);
                    } else
                        res = compareNatural(a.getCollectionNo(), b.getCollectionNo());

                    return isAsc ? res : -res;
                });

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (adapter != null)
                            adapter.updateData(filtered);
                        if (tvCount != null) {
                            int filteredOwned = 0;
                            for (com.vn.jet.mosco.model.CardDisplayItem item : filtered) {
                                if (item.isOwned())
                                    filteredOwned++;
                            }
                            tvCount.setText(filteredOwned + "/" + filtered.size());
                        }
                    });
                }
            }).start();
        }
    }

    private static boolean isStatus(String f) {
        return com.vn.jet.mosco.utils.DatabaseLoader.isStatus(f);
    }

    private static boolean isArtist(String f) {
        return com.vn.jet.mosco.utils.DatabaseLoader.isArtist(f);
    }

    private static boolean isClass(String f) {
        return com.vn.jet.mosco.utils.DatabaseLoader.isClass(f);
    }

    private static int compareNatural(String s1, String s2) {
        if (s1 == null && s2 == null)
            return 0;
        if (s1 == null)
            return -1;
        if (s2 == null)
            return 1;

        // Tối ưu hóa: Trích xuất số bằng tay thay vì dùng Regex replaceAll
        long n1 = extractDigits(s1);
        long n2 = extractDigits(s2);

        if (n1 != -1 && n2 != -1) {
            return Long.compare(n1, n2);
        }

        return s1.compareTo(s2);
    }

    private static long extractDigits(String s) {
        if (s == null || s.isEmpty())
            return -1;
        long res = 0;
        boolean found = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                res = res * 10 + (c - '0');
                found = true;
            } else if (found)
                break; // Chỉ lấy cụm số đầu tiên gặp được
        }
        return found ? res : -1;
    }

    public static int getCardClassRank(String cardClass) {
        return com.vn.jet.mosco.utils.DatabaseLoader.getCardClassRank(cardClass);
    }

    /**
     * Mapping class từ UI sang database key (Đã tách bạch Welcome/First,
     * Special/Unit)
     */
    public static String mapClassToTypeKey(String cardClass) {
        return com.vn.jet.mosco.utils.DatabaseLoader.mapClassToTypeKey(cardClass);
    }
}

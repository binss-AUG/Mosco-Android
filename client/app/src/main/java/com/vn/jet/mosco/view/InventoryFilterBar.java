package com.vn.jet.mosco.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.vn.jet.mosco.R;
import com.vn.jet.mosco.fragment.CollectionFragment;

/**
 * [QUIET LUXURY] Smart Pill — Viên thuốc thông minh hiển thị tên Sort hiện tại.
 * Bấm vào → mở Bottom Sheet tổng hợp Sort + Filter.
 * Lưu giữ state Sort/Ascending nội bộ để các Fragment đọc qua getter.
 */
public class InventoryFilterBar extends LinearLayout {

    private TextView btnSort;

    private boolean isAscending = false;
    private String currentSort = CollectionFragment.SORT_NEWEST;
    private String[] sortOptions;

    private OnFilterChangeListener listener;

    public interface OnFilterChangeListener {
        void onFilterChanged(String sortOption, boolean isAscending);
        void onFilterRequested();
    }

    public InventoryFilterBar(Context context) {
        super(context);
        init(context);
    }

    public InventoryFilterBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setOrientation(HORIZONTAL);
        setBackgroundResource(R.drawable.lg_chip_unselected_bg);
        LayoutInflater.from(context).inflate(R.layout.view_inventory_filter_bar, this, true);

        btnSort = findViewById(R.id.btn_sort_select);

        // Bấm vào pill → mở Bottom Sheet tổng hợp
        setOnClickListener(v -> {
            if (listener != null) listener.onFilterRequested();
        });
    }

    public void setSortOptions(String[] options) {
        this.sortOptions = options;
    }

    public String[] getSortOptions() {
        return sortOptions;
    }

    public void setListener(OnFilterChangeListener listener) {
        this.listener = listener;
    }

    public void setSortText(String text) {
        // [QUIET LUXURY] Lưu lại trạng thái sort nội bộ nhưng KHÔNG cập nhật text hiển thị của Pill.
        // Điều này giúp giữ vững giao diện Clean Minimalist (luôn là chữ Filter cùng icon phễu).
        this.currentSort = text;
    }

    public void setAscending(boolean ascending) {
        this.isAscending = ascending;
    }

    /**
     * Cập nhật cả Sort + Direction từ Bottom Sheet kết quả,
     * sau đó notify listener để Fragment gọi applyFilters().
     */
    public void applySortFromBottomSheet(String sortOption, boolean ascending) {
        // [QUIET LUXURY] Đồng bộ hóa state sort nội bộ mà không phá vỡ giao diện "Filter" tối giản ở màn hình ngoài.
        this.currentSort = sortOption;
        this.isAscending = ascending;
        if (listener != null) {
            listener.onFilterChanged(sortOption, ascending);
        }
    }

    public String getSortOption() {
        return currentSort;
    }

    public boolean isAscending() {
        return isAscending;
    }

    // Không cần attachDropdown() nữa — Sort đã di chuyển vào Bottom Sheet
}

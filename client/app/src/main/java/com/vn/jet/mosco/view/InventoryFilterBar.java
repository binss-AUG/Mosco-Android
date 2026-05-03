package com.vn.jet.mosco.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.vn.jet.mosco.R;
import com.vn.jet.mosco.fragment.CollectionFragment;

public class InventoryFilterBar extends LinearLayout {

    private TextView btnSort;
    private FrameLayout btnDirection;
    private ImageView ivDirection;
    private TextView btnFilter;
    private LinearLayout dropdownSort;

    private boolean isAscending = false;
    private String currentSort = "Newest";
    private final String[] SORT_OPTIONS = {"Newest", "Badge", "Level", "Artist (A-Z)", "Status", "Class", "Season"};

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
        LayoutInflater.from(context).inflate(R.layout.view_inventory_filter_bar, this, true);

        btnSort = findViewById(R.id.btn_sort_select);
        btnDirection = findViewById(R.id.btn_sort_direction_container);
        ivDirection = findViewById(R.id.iv_sort_direction);
        btnFilter = findViewById(R.id.btn_filter_select);
        // dropdownSort is injected via attachDropdown()

        setupDirectionToggle();
        
        btnFilter.setOnClickListener(v -> {
            if (listener != null) listener.onFilterRequested();
        });
    }

    public void attachDropdown(LinearLayout dropdown) {
        this.dropdownSort = dropdown;
        setupSortDropdown();
    }

    private void setupSortDropdown() {
        if (btnSort != null && dropdownSort != null) {
            btnSort.setText(currentSort);
            CollectionFragment.setupSortDropdown(btnSort, null, null, SORT_OPTIONS, dropdownSort, () -> {
                currentSort = btnSort.getText().toString();
                notifyChange();
            });
        }
    }

    private void setupDirectionToggle() {
        if (btnDirection != null && ivDirection != null) {
            ivDirection.setRotation(isAscending ? 0f : 180f);
            btnDirection.setOnClickListener(v -> {
                isAscending = !isAscending;
                ivDirection.animate().rotation(isAscending ? 0f : 180f).setDuration(200).start();
                notifyChange();
            });
        }
    }

    private void notifyChange() {
        if (listener != null) {
            listener.onFilterChanged(currentSort, isAscending);
        }
    }

    public void setListener(OnFilterChangeListener listener) {
        this.listener = listener;
    }

    public void setSortText(String text) {
        this.currentSort = text;
        if (btnSort != null) btnSort.setText(text);
    }

    public void setAscending(boolean ascending) {
        this.isAscending = ascending;
        if (ivDirection != null) {
            ivDirection.setRotation(isAscending ? 0f : 180f);
        }
    }
}

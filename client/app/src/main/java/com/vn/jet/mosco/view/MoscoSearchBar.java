package com.vn.jet.mosco.view;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.vn.jet.mosco.R;

public class MoscoSearchBar extends LinearLayout {

    private EditText etSearchInput;
    private ImageView ivClear;
    private ImageView ivFilter;

    private Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;
    private OnSearchListener searchListener;
    private OnFilterClickListener filterClickListener;

    private static final long DEBOUNCE_DELAY_MS = 300;

    public interface OnSearchListener {
        void onSearch(String query);
    }

    public interface OnFilterClickListener {
        void onFilterClicked();
    }

    public MoscoSearchBar(Context context) {
        super(context);
        init(context);
    }

    public MoscoSearchBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.view_mosco_search_bar, this, true);

        etSearchInput = findViewById(R.id.et_search_input);
        ivClear = findViewById(R.id.iv_search_clear);
        ivFilter = findViewById(R.id.iv_search_filter);

        ivClear.setOnClickListener(v -> {
            etSearchInput.setText("");
            // Clear text sẽ trigger TextWatcher
        });

        ivFilter.setOnClickListener(v -> {
            if (filterClickListener != null) {
                filterClickListener.onFilterClicked();
            }
        });

        etSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String query = s.toString().trim();
                ivClear.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);

                if (searchRunnable != null) {
                    debounceHandler.removeCallbacks(searchRunnable);
                }

                searchRunnable = () -> {
                    if (searchListener != null) {
                        searchListener.onSearch(query);
                    }
                };

                debounceHandler.postDelayed(searchRunnable, DEBOUNCE_DELAY_MS);
            }
        });
    }

    public void setOnSearchListener(OnSearchListener listener) {
        this.searchListener = listener;
    }

    public void setOnFilterClickListener(OnFilterClickListener listener) {
        this.filterClickListener = listener;
    }
    
    public void setFilterVisible(boolean visible) {
        ivFilter.setVisibility(visible ? View.VISIBLE : View.GONE);
        findViewById(R.id.v_search_divider).setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}

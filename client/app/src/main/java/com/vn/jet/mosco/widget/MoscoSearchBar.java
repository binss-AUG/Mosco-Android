package com.vn.jet.mosco.widget;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.vn.jet.mosco.R;

/**
 * Thanh tìm kiếm chuẩn Glassmorphism cho dự án Mosco.
 * Tích hợp sẵn hiệu ứng kính mờ, icon tìm kiếm và nút xóa nhanh.
 */
public class MoscoSearchBar extends FrameLayout {

    private EditText etSearch;
    private ImageView ivClear;
    private SearchListener listener;

    public interface SearchListener {
        void onQueryChanged(String query);
        void onSearchAction(String query);
    }

    public MoscoSearchBar(@NonNull Context context) {
        super(context);
        init(null);
    }

    public MoscoSearchBar(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public MoscoSearchBar(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(@Nullable AttributeSet attrs) {
        // Inflate layout bằng Java để đóng gói component
        View view = LayoutInflater.from(getContext()).inflate(R.layout.layout_mosco_search_bar, this, true);
        
        etSearch = view.findViewById(R.id.et_search);
        ivClear = view.findViewById(R.id.iv_clear);

        // Xử lý sự kiện thay đổi text
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString();
                ivClear.setVisibility(query.isEmpty() ? GONE : VISIBLE);
                if (listener != null) {
                    listener.onQueryChanged(query);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Xử lý nút xóa nhanh
        ivClear.setOnClickListener(v -> etSearch.setText(""));

        // Xử lý nút Search trên bàn phím
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (listener != null) {
                listener.onSearchAction(etSearch.getText().toString());
            }
            return true;
        });
    }

    public void setSearchListener(SearchListener listener) {
        this.listener = listener;
    }

    public void setHint(String hint) {
        etSearch.setHint(hint);
    }

    public String getQuery() {
        return etSearch.getText().toString();
    }

    public void clearFocus() {
        super.clearFocus();
        etSearch.clearFocus();
    }
}

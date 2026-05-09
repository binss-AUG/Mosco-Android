package com.vn.jet.mosco.base;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

/**
 * Lớp nền tảng (Base) cho toàn bộ Fragment trong dự án Mosco.
 * Quản lý vòng đời an toàn của ViewBinding (chống rò rỉ bộ nhớ) và tự động liên kết với BaseActivity.
 *
 * @param <VB> Kiểu ViewBinding của Fragment con.
 */
public abstract class BaseMoscoFragment<VB extends ViewBinding> extends Fragment {

    // ViewBinding của màn hình, tự động giải phóng khi View bị hủy
    @Nullable
    protected VB binding;

    /**
     * Yêu cầu Fragment con tự cung cấp ViewBinding (Zero-Overhead).
     *
     * @param inflater LayoutInflater từ hệ thống.
     * @param container ViewGroup chứa Fragment (nếu có).
     * @param attachToParent Cờ đánh dấu có gắn vào parent ngay lập tức hay không (thường là false).
     * @return Đối tượng ViewBinding đã được inflate.
     */
    @NonNull
    protected abstract VB inflateBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, boolean attachToParent);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Khởi tạo Binding
        binding = inflateBinding(inflater, container, false);
        if (binding == null) {
            throw new IllegalStateException("ViewBinding của Fragment không được phép null.");
        }
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Móc nối các View và Listener
        initViews();
        initListeners();
    }

    /**
     * Nơi Fragment con khởi tạo UI (Adapter, Text, Color...).
     */
    protected abstract void initViews();

    /**
     * Nơi Fragment con thiết lập các sự kiện tương tác.
     */
    protected abstract void initListeners();

    /**
     * Hàm tiện ích hiển thị Loading bằng cách mượn quyền từ BaseMoscoActivity (nếu Activity chứa Fragment này là Base).
     */
    public void showLoading() {
        if (getActivity() instanceof BaseMoscoActivity) {
            ((BaseMoscoActivity<?>) getActivity()).showLoading();
        }
    }

    /**
     * Hàm tiện ích ẩn Loading.
     */
    public void hideLoading() {
        if (getActivity() instanceof BaseMoscoActivity) {
            ((BaseMoscoActivity<?>) getActivity()).hideLoading();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // BẮT BUỘC: Giải phóng binding để tránh rò rỉ bộ nhớ (Memory Leak)
        // do vòng đời của Fragment View ngắn hơn vòng đời của Fragment instance.
        binding = null;
    }
}

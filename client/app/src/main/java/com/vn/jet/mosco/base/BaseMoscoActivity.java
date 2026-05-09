package com.vn.jet.mosco.base;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewbinding.ViewBinding;

import com.vn.jet.mosco.R;

/**
 * Lớp nền tảng (Base) cho toàn bộ Activity trong dự án Mosco.
 * Áp dụng kiến trúc Generics với ViewBinding để đảm bảo Zero-Overhead và Type-Safety.
 * Mọi Activity con bắt buộc phải kế thừa lớp này để đồng bộ hóa UX "Quiet Luxury".
 *
 * @param <VB> Kiểu ViewBinding của Activity con.
 */
public abstract class BaseMoscoActivity<VB extends ViewBinding> extends AppCompatActivity {

    // ViewBinding của màn hình, được khởi tạo thông qua abstract method
    @Nullable
    protected VB binding;

    // Dialog hiển thị trạng thái đang tải (Loading Overlay)
    @Nullable
    private Dialog loadingDialog;

    /**
     * Phương thức trừu tượng yêu cầu Activity con phải tự inflate ViewBinding.
     * Cách tiếp cận này loại bỏ hoàn toàn Reflection, giúp tăng tối đa tốc độ khởi tạo màn hình.
     *
     * @param inflater LayoutInflater của Activity.
     * @return Đối tượng ViewBinding đã được inflate.
     */
    @NonNull
    protected abstract VB inflateBinding(@NonNull LayoutInflater inflater);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Khởi tạo ViewBinding
        binding = inflateBinding(getLayoutInflater());
        if (binding == null) {
            throw new IllegalStateException("ViewBinding không được phép null. Hãy kiểm tra lại hàm inflateBinding().");
        }
        setContentView(binding.getRoot());

        // Thiết lập giao diện tràn viền (Edge-to-edge) chuẩn Galactic
        setupEdgeToEdge();

        // Khởi tạo các thành phần UI cơ bản (nếu có)
        initViews();

        // Lắng nghe các sự kiện logic (Click, Swipe...)
        initListeners();
    }

    /**
     * Xử lý giao diện tràn viền (Edge-to-edge) mượt mà.
     * Đẩy nội dung vẽ chìm dưới Status Bar và Navigation Bar.
     */
    private void setupEdgeToEdge() {
        // Cho phép vẽ xuyên qua viền hệ thống
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // Đảm bảo nội dung không bị lẹm vào các thanh hệ thống bằng cách thêm padding động
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, windowInsets) -> {
            androidx.core.graphics.Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            
            // Tùy chỉnh padding dựa trên thanh trạng thái (Top) và thanh điều hướng (Bottom)
            // Lưu ý: Các Activity con có thể override lại nếu muốn vẽ full màn hình (vd: màn xem ảnh thẻ)
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            
            return WindowInsetsCompat.CONSUMED;
        });
    }

    /**
     * Hiển thị trạng thái chờ (Loading) chặn tương tác người dùng.
     * Sử dụng Dialog trong suốt với hiệu ứng chuẩn Luxury.
     */
    public void showLoading() {
        if (loadingDialog == null) {
            loadingDialog = new Dialog(this);
            // Có thể đổi thành layout Skeleton hoặc Mosco Spinner sau khi hoàn thiện Atom phase
            loadingDialog.setContentView(R.layout.layout_objekt_card_skeleton); 
            
            if (loadingDialog.getWindow() != null) {
                loadingDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                loadingDialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                loadingDialog.getWindow().setDimAmount(0.6f); // Hiệu ứng tối nền
            }
            loadingDialog.setCancelable(false); // Không cho phép tắt khi đang xử lý
        }
        
        if (!loadingDialog.isShowing() && !isFinishing()) {
            loadingDialog.show();
        }
    }

    /**
     * Ẩn trạng thái chờ.
     */
    public void hideLoading() {
        if (loadingDialog != null && loadingDialog.isShowing() && !isFinishing()) {
            loadingDialog.dismiss();
        }
    }

    /**
     * Nơi Activity con khởi tạo UI (Adapter, RecyclerView...).
     */
    protected abstract void initViews();

    /**
     * Nơi Activity con thiết lập các sự kiện tương tác (OnClick, OnScroll...).
     */
    protected abstract void initListeners();

    @Override
    protected void onDestroy() {
        // Giải phóng Dialog chống Memory Leak
        hideLoading();
        loadingDialog = null;
        
        // Giải phóng Binding
        binding = null;
        super.onDestroy();
    }
}

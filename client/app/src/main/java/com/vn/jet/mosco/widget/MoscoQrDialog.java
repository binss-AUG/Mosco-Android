package com.vn.jet.mosco.widget;

import android.Manifest;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.tabs.TabLayout;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.BarcodeView;
import com.vn.jet.mosco.FriendActivity;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.utils.SessionManager;

import java.util.List;

/**
 * Quản lý Hộp thoại định danh qua QR (Galactic ID QR Gateway).
 * Hỗ trợ tự động sinh mã QR từ ID người chơi hiện tại và quét mã QR của người khác.
 * Tái thiết kế sử dụng hộp thoại căn giữa màn hình (Liquid Dialog) nhằm tối ưu tương tác người dùng theo thiết kế mới.
 */
public class MoscoQrDialog {

    private static final String TAG = "MoscoQrDialog";
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 2026;

    /**
     * Khởi tạo và hiển thị Hộp thoại QR chuyên nghiệp ở trung tâm màn hình.
     * Tách biệt logic xử lý khỏi Activity nhằm ngăn ngừa hoàn toàn nguy cơ rò rỉ bộ nhớ (Memory Leak).
     */
    public static void show(@NonNull FriendActivity activity) {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_galactic_qr, null);
        // Khởi tạo hộp thoại cao cấp căn giữa với nền lỏng (Liquid Glass) theo quy chuẩn đồ họa nhất quán
        Dialog dialog = MoscoDialogManager.createLiquidDialog(activity, view);

        // Ánh xạ các thành phần View
        TextView tvTitle = view.findViewById(R.id.tv_qr_dialog_title);
        ImageView btnClose = view.findViewById(R.id.btn_close_qr_dialog);
        TabLayout tabLayout = view.findViewById(R.id.tab_layout_qr);
        View myQrContainer = view.findViewById(R.id.my_qr_container);
        View scanQrContainer = view.findViewById(R.id.scan_qr_container);
        ImageView ivQrCode = view.findViewById(R.id.iv_qr_code);
        TextView tvUserId = view.findViewById(R.id.tv_qr_user_id);
        BarcodeView barcodeView = view.findViewById(R.id.barcode_view);

        // Nút đóng hộp thoại
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        // Truy xuất ID người chơi hiện tại từ bộ nhớ phiên giao dịch
        SessionManager sessionManager = new SessionManager(activity);
        Long userId = sessionManager.getUserId();

        // Cập nhật nhãn ID định dạng cao cấp phía dưới mã QR
        if (tvUserId != null) {
            long displayId = userId != null ? userId : 0;
            tvUserId.setText(activity.getString(R.string.social_qr_my_code_label) + "\nID: " + (10000000 + displayId));
        }

        // Sinh mã QR tĩnh với chuỗi định danh đơn giản MOSCO_QR:{id}
        // Lý do: Tiền tố trơn giúp tối giản độ dài chuỗi mã hóa, giảm thiểu mật độ chấm QR, từ đó tăng đột biến độ nhạy khi camera bắt hình
        if (ivQrCode != null && userId != null) {
            try {
                String qrContentStr = "MOSCO_QR:" + userId;
                BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
                // Kích thước 600x600 pixels đảm bảo mã vạch hiển thị sắc nét tuyệt đối trên mọi độ phân giải
                Bitmap bitmap = barcodeEncoder.encodeBitmap(qrContentStr, BarcodeFormat.QR_CODE, 600, 600);
                ivQrCode.setImageBitmap(bitmap);
            } catch (Exception e) {
                Log.e(TAG, "Lỗi sinh mã QR", e);
            }
        }

        // Khởi tạo và thiết lập các Tab chuyển đổi chức năng
        if (tabLayout != null) {
            tabLayout.addTab(tabLayout.newTab().setText(R.string.social_qr_tab_my_qr));
            tabLayout.addTab(tabLayout.newTab().setText(R.string.social_qr_tab_scan));

            tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(TabLayout.Tab tab) {
                    if (tab.getPosition() == 0) {
                        // Chế độ My QR Code: Tạm ngắt kết nối phần cứng Camera để bảo toàn năng lượng và tối ưu khung hình
                        if (barcodeView != null) {
                            barcodeView.pause();
                        }
                        if (myQrContainer != null) myQrContainer.setVisibility(View.VISIBLE);
                        if (scanQrContainer != null) scanQrContainer.setVisibility(View.GONE);
                        if (tvTitle != null) tvTitle.setText(R.string.social_qr_tab_my_qr);
                    } else {
                        // Chế độ Scan: Xác thực đặc quyền truy cập Camera tại thời điểm chạy nhằm tuân thủ chính sách bảo mật
                        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            if (myQrContainer != null) myQrContainer.setVisibility(View.GONE);
                            if (scanQrContainer != null) scanQrContainer.setVisibility(View.VISIBLE);
                            if (tvTitle != null) tvTitle.setText(R.string.social_qr_tab_scan);
                            if (barcodeView != null) {
                                barcodeView.resume();
                            }
                        } else {
                            // Cảnh báo người dùng và phát động yêu cầu cấp quyền từ hệ điều hành
                            Toast.makeText(activity, R.string.social_qr_camera_permission_required, Toast.LENGTH_SHORT).show();
                            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
                            // Tự động đẩy người dùng về Tab mặc định cho đến khi quyền truy cập phần cứng được thiết lập
                            TabLayout.Tab firstTab = tabLayout.getTabAt(0);
                            if (firstTab != null) {
                                firstTab.select();
                            }
                        }
                    }
                }

                @Override
                public void onTabUnselected(TabLayout.Tab tab) {}

                @Override
                public void onTabReselected(TabLayout.Tab tab) {}
            });
        }

        // Lập trình cơ chế giải mã liên tục (Continuous Decoding) cho khung camera
        if (barcodeView != null) {
            barcodeView.decodeContinuous(new BarcodeCallback() {
                @Override
                public void barcodeResult(BarcodeResult result) {
                    if (result != null && result.getText() != null) {
                        String text = result.getText().trim();
                        // Đối soát chính xác tiền tố trơn MOSCO_QR: nhằm loại trừ triệt để các mã ngoài hệ thống hoặc mã rác
                        if (text.startsWith("MOSCO_QR:")) {
                            barcodeView.pause();
                            dialog.dismiss();
                            String scannedUserId = text.substring("MOSCO_QR:".length());
                            // Kích hoạt nạp dữ liệu từ máy chủ và hiển thị giao diện trang cá nhân hoàn chỉnh
                            activity.fetchAndShowProfile(scannedUserId);
                        }
                    }
                }

                @Override
                public void possibleResultPoints(List<com.google.zxing.ResultPoint> resultPoints) {}
            });
        }

        // Thiết lập bộ lắng nghe giải phóng triệt để tài nguyên camera khi đóng hộp thoại
        dialog.setOnDismissListener(d -> {
            if (barcodeView != null) {
                barcodeView.pause();
            }
        });

        dialog.show();
    }
}

# BÁO CÁO KỸ THUẬT: SỰ CỐ CRASH KHI VÀO PROFILE V2

## 1. Triệu chứng
Ứng dụng bị dừng đột ngột (Crash) ngay khi người dùng nhấn vào Profile (của bản thân hoặc Guest).

## 2. Nguyên nhân (Root Cause)
Lỗi nằm ở phương thức `setupTabThumb()` trong file [ProfileFragment.java](file:///d:/MEox\UITer\DOAN\Mosco_Megre\Mosco\client\app\src\main\java\com\vn\jet\mosco\fragment\ProfileFragment.java).

- **Chi tiết:** Câu lệnh `tabLayout.addView(thumb, 0);` đã cố gắng thêm một `View` thông thường vào làm con trực tiếp của `TabLayout`.
- **Cơ chế gây lỗi:** `TabLayout` là một component phức hợp kế thừa từ `HorizontalScrollView`. Nó quản lý nội bộ một `SlidingTabIndicator` (một lớp con của `LinearLayout`) làm con trực tiếp duy nhất để hiển thị các tab.
- **Hệ quả:** Khi hệ thống thực hiện vẽ giao diện (`onMeasure`/`onLayout`), `TabLayout` cố gắng truy cập con tại vị trí index 0 và ép kiểu (cast) nó về `SlidingTabIndicator`. Do `thumb` chỉ là một `View` cơ bản, hành động này gây ra lỗi **ClassCastException**, dẫn đến crash toàn bộ ứng dụng.

## 3. Giải pháp đề xuất (Proposed Solution)

Để đạt được hiệu ứng trượt "Pill Chip" giống màn hình Stage mà không phá vỡ cấu trúc của `TabLayout`, chúng ta cần thực hiện các bước sau:

### Bước 1: Điều chỉnh XML Layout
Bọc `TabLayout` trong một `FrameLayout` để cho phép xếp chồng các View (Thumb nằm dưới, TabLayout nằm trên).

```xml
<FrameLayout
    android:id="@+id/layout_tab_container"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginHorizontal="@dimen/spacing_lg"
    android:layout_marginTop="@dimen/spacing_md"
    android:layout_marginBottom="@dimen/spacing_xs"
    android:background="@drawable/bg_profile_tab_container"
    android:padding="@dimen/spacing_xxs">

    <!-- Thumb trượt nằm phía dưới TabLayout -->
    <View
        android:id="@+id/tab_sliding_thumb"
        android:layout_width="0dp"
        android:layout_height="match_parent"
        android:background="@drawable/bg_profile_tab_indicator" />

    <com.google.android.material.tabs.TabLayout
        android:id="@+id/tab_layout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@android:color/transparent"
        app:tabIndicatorHeight="0dp"
        app:tabMode="fixed"
        app:tabGravity="fill"
        ... />
</FrameLayout>
```

### Bước 2: Cập nhật Logic Java
- Lấy reference của `View thumb` từ XML thay vì khởi tạo động và `addView`.
- Tính toán tọa độ trượt dựa trên vị trí của `TabView` con (nằm sâu bên trong `TabLayout`).

### Bước 3: Kiểm tra Null-Safety
Đảm bảo các phương thức `getGeneralFragment()` không gây crash nếu Adapter chưa sẵn sàng.

---
> [!IMPORTANT]
> **Tech Lead Lưu ý:** Giải pháp này đảm bảo tính ổn định tuyệt đối vì không can thiệp vào cấu trúc View hierarchy nội bộ của Google Material Components, đồng thời vẫn giữ được trải nghiệm "Quiet Luxury" mượt mà.

**Bạn có đồng ý với phương án refactor này không?**

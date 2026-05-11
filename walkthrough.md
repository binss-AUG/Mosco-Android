# BÁO CÁO CÔNG VIỆC: NÂNG CẤP PROFILE ĐỊNH TUYẾN (LOCAL-FIRST)

Dưới đây là chi tiết các thay đổi kỹ thuật nhằm tối ưu hiệu năng và trải nghiệm người dùng cho màn hình Profile theo chuẩn "Quiet Luxury".

## 1. Kiến trúc Dữ liệu & Persistence (Room Database)
Để đáp ứng yêu cầu **Local-First**, hệ thống hiện tại đã có khả năng cache thông tin người dùng cục bộ.

| Thành phần | Thay đổi | Chi tiết |
| :--- | :--- | :--- |
| **[UserStats.java](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/model/UserStats.java)** | Chuyển đổi sang `@Entity` | Thêm PrimaryKey và các trường `avatarId`, `ingameName` để cache đầy đủ profile. |
| **[UserStatsDao.java](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/database/UserStatsDao.java)** | **[NEW]** Tạo mới DAO | Hỗ trợ truy vấn `LiveData<UserStats>` và phương thức `insertUserStats` (Upsert logic). |
| **[AppDatabase.java](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/database/AppDatabase.java)** | Nâng cấp Version 2 | Đăng ký `UserStats` vào database và cấu hình `fallbackToDestructiveMigration`. |

## 2. Tầng Logic & Hiệu năng (ViewModel & Utils)
Tuân thủ nguyên tắc **Clean Code** và **DRY**, tách biệt hoàn toàn logic dữ liệu khỏi UI.

- **[ProfileViewModel.java](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/ProfileViewModel.java) [NEW]**: 
    - Sử dụng `switchMap` để lắng nghe thay đổi của `userId`.
    - Triển khai logic đồng bộ: Render từ DB trước, gọi API cập nhật ngầm (Background Sync).
- **[ClickDebounce.java](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/utils/ClickDebounce.java)**:
    - Nâng cấp từ Abstract class sang Concrete class.
    - Hỗ trợ Lambda listener và tùy chỉnh `minClickInterval` (Mặc định 500ms cho các nút tương tác).

## 3. Giao diện người dùng (UI/UX - ViewStub & Shimmer)
Tối ưu bộ nhớ (RAM) cho giả lập bằng cách chỉ nạp các thành phần cần thiết.

| Module | File Layout | Mô tả |
| :--- | :--- | :--- |
| **Skeleton Loading** | [layout_profile_shimmer.xml](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/res/layout/layout_profile_shimmer.xml) | Hiệu ứng Shimmer khớp 1:1 với layout thực tế khi cache trống. |
| **Owner Actions** | [layout_owner_actions.xml](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/res/layout/layout_owner_actions.xml) | Chứa nút Sửa Profile, Cài đặt, Tài nguyên. |
| **Guest Actions** | [layout_guest_actions.xml](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/res/layout/layout_guest_actions.xml) | Chứa nút Kết bạn, Theo dõi, Nhắn tin. |

## 4. Logic Định tuyến trong Fragment
File **[ProfileFragment.java](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/fragment/ProfileFragment.java)** đã được refactor toàn diện:

1.  **State Routing**: Tự động so sánh `targetUserId` với `currentUserId` từ Session.
2.  **ViewStub Inflation**: Chỉ gọi `.inflate()` cho module chức năng tương ứng với quyền hạn người dùng.
3.  **Data Binding**: Sử dụng `Transformations.distinctUntilChanged()` để ngăn chặn re-render khi dữ liệu không thay đổi.
4.  **Security**: Tự động che giấu Email (Masking) nếu view ở chế độ Guest.
5.  **Optimistic UI**: Các nút tương tác xã hội phản hồi ngay lập tức để tạo cảm giác mượt mà.

## 5. Kiểm tra & Lưu ý
- **Database**: Do đã nâng cấp version database, cần thực hiện **Sync Gradle** và **Clear Data/Uninstall** ứng dụng trên giả lập để Room khởi tạo schema mới.
- **Tái sử dụng**: Đã tái sử dụng `bg_skeleton_circle.xml` và các style `MoscoText.Luxury`.

---
*Báo cáo được thực hiện bởi Senior AI Developer - Mosco Project.*

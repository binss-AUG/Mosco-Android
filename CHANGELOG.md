# 📜 NHẬT KÝ CẬP NHẬT - DỰ ÁN MOSCO

Tất cả các thay đổi kỹ thuật và cải tiến UI/UX được ghi lại tại đây để phục vụ việc bảo trì và mở rộng.

---

## [V6.2] - Fix SignIn Crash & Full Auth Reform
- **Anti-Spam:** Bổ sung cờ kiểm soát trạng thái (`isSigningIn`/`isSubmitting`) cho cả nút bấm và hành động bàn phím (IME_ACTION_DONE) để chống spam tuyệt đối trên toàn bộ màn hình Auth.
- **Null-Safety:** Áp dụng kiểm tra null nghiêm ngặt cho phản hồi API (`Resource`, `AuthResponse`, `UserData`) và dữ liệu Token để loại bỏ crash NPE khi mạng không ổn định.
- **Stability:** Tối ưu hóa việc disable/enable các view input trong quá trình Loading để tránh xung đột Focus khi người dùng thao tác quá nhanh.

## [V6.1] - UX Stability & Anti-Spam Reform
- **Anti-Spam:** Tích hợp bộ giải pháp `ClickDebounce` (1000ms) chuẩn dự án cho nút "Get Started" tại `OnboardingActivity`.
- **Navigation:** Tự động gọi `finish()` ngay sau khi chuyển màn hình để triệt tiêu hoàn toàn khả năng quay lại Onboarding bằng phím Back, tối ưu luồng người dùng.
- **Stability:** Ngăn chặn tuyệt đối lỗi mở trùng lặp 2-3 màn hình Sign In khi người dùng spam phím nhanh.

## [V6.0] - Saira Reform: Galactic HUD Evolution
- **Typography:** Thay thế toàn bộ hệ thống Orbitron/Chakra Petch sang font **SAIRA**.
- **Visual:** Chữ nén ngang (Condensed style) mang lại cảm giác mạnh mẽ, uy lực như các game FPS đỉnh cao (Valorant/Apex).
- **Localization:** Đạt độ tương thích 100% với tiếng Việt, không còn lỗi font ở bất kỳ nhãn (Label) nào.
- **Cleanup:** Xóa bỏ các resource font dư thừa để tối ưu dung lượng App.

## [V5.9] - Galactic HUD Typography Upgrade
- **Font System:** Tích hợp **Orbitron** (Gaming Standard) và **Chakra Petch** (Vietnamese Support) qua cơ chế Downloadable Fonts.
- **Typography:** Refactor toàn bộ Header Home Fragment: Username (Chakra Petch Bold + AllCaps), Currency & Stats (Orbitron Bold).
- **Showcase:** Đồng bộ font **Orbitron** cho chỉ số OVR trên thẻ bài để tăng tính uy lực.
- **Standard:** Áp dụng `letterSpacing` chuẩn HUD (0.08) cho các nhãn quan trọng để tăng độ sang trọng.

## [V5.8] - Header Balance & Avatar Refinement
- **UI:** Triệt tiêu hoàn toàn viền vuông dư thừa của Avatar bằng `MaterialCardView` bo tròn tuyệt đối (100dp).
- **Typography:** Đại trùng tu kích thước Header: Username (22sp), Currency (15sp), Badges (12sp).
- **Showcase:** Nâng cấp OVR trên thẻ bài lên 34sp sử dụng font **Poppins Bold** để tăng uy lực.
- **Icon:** Tinh chỉnh `icon_size_sm` xuống 18dp để đạt độ tinh tế (Refined) cao nhất.
- **Alignment:** Cân bằng lại toàn bộ trục dọc (Y-axis) của Header, đảm bảo text luôn nằm đúng tâm Avatar.

## [V5.7] - Showcase Persistence & UI Clean
- **UI:** Loại bỏ dòng greeting `Welcome back` để tinh gọn Header Home.
- **Logic:** Triển khai cơ chế lưu ID thẻ bài Showcase vào `SessionManager`. 
- **UX:** Thẻ bài được chọn sẽ được hiển thị ưu tiên thay vì mặc định lấy thẻ OVR cao nhất, giúp duy trì trạng thái khi chuyển Tab.

## [V5.6] - Icon Scaling Reform
- **System:** Xây dựng hệ thống kích thước icon tập trung trong `dimens.xml` (sm: 22dp, md: 28dp, lg: 38dp).
- **Refactor:** Đồng bộ hóa toàn bộ icon trong các màn hình Home, Profile, Shop, Collection.
- **Scalability:** Loại bỏ triệt để hardcode kích thước icon, cho phép điều chỉnh quy mô toàn UI tại một nơi duy nhất.

## [V5.5] - Icon Evolution
- **Design:** Tái thiết kế toàn diện `ic_objets.xml` với style "Dark Matte" chồng lớp (Multi-layer).
- **Branding:** Đồng bộ mã màu Neon Cyan cho icon Objet trên toàn bộ Fragment.
- **Visual:** Dìm tone icon để hòa quyện với phong cách Galactic Dark Mode.

## [V5.4] - UI Refactor & UX Polish
- **Typography:** Loại bỏ lỗi lặp nhãn `OVR` và xóa mũi tên thừa ở `LV.`.
- **Alignment:** Căn giữa dọc cụm Tên/Lời chào đối xứng với Avatar.
- **Standard:** Đồng bộ hóa style **Solid Background** cho toàn bộ hệ thống Badge.
- **UX:** Nâng cấp vùng tương tác của icon Inventory lên chuẩn **48x48dp**.
- **Logic:** Triển khai hàm `formatCurrencyValue` (Java) rút gọn con số tài nguyên thành M/K.

## [V5.3] - HUD Transformation
- **Feature:** Chuyển đổi Quick Tool từ dạng vòng sang **Rounded Rect Board** (3x2 Grid).
- **Sync:** Tự động tính toán vị trí bảng menu đối xứng với FAB để tránh tràn màn hình.
- **Animation:** Thêm hiệu ứng Staggered hiện dần cho các nút công cụ.

---
*Ghi chú: Luôn tuân thủ 100% Rules dự án Mosco khi thực hiện các bản cập nhật mới.*

# Báo Cáo Phân Tích Tài Nguyên (Resource Components)

Tương tự như UI Component (XML và Java), các file tài nguyên tĩnh (`res/values/`) cũng đang gặp phải tình trạng phân mảnh và trùng lặp trầm trọng do thiếu một Design System xuyên suốt. Dưới đây là phân tích chi tiết cho từng loại tài nguyên.

> [!WARNING]
> Tình trạng trùng lặp ở tầng Resource khiến việc bảo trì rất khó khăn. Khi cần thay đổi một màu sắc hay khoảng cách (margin/padding), lập trình viên sẽ không biết phải đổi ở biến nào do có quá nhiều biến cùng trỏ về một giá trị.

---

## 1. Màu Sắc (Colors)
Tài nguyên màu sắc được quản lý chủ yếu ở `colors.xml` và `colors_palette.xml`.

**Vấn đề 1: Xung đột Design System**
Hiện tại file `colors.xml` (dài hơn 260 dòng) đang chứa 2 hệ thống thiết kế song song:
- **Hệ thống cũ (Mosco):** Sử dụng các tiền tố `mosco_` (như `mosco_card_bg`, `mosco_primary`).
- **Hệ thống mới (Liquid Glass):** Chứa các tiền tố `lg_` (như `lg_glass_surface`, `lg_background`).
Điều này dẫn đến việc cùng một bề mặt giao diện nhưng lại có 2 biến màu khác nhau.

**Vấn đề 2: Lạm dụng Alias và Hardcode mã HEX**
- **Trùng lặp biến Alpha:** Có sự tồn tại đồng thời của `mosco_white_10`, `white_10`, `glass_white_10`. Tất cả đều ám chỉ một màu trắng có độ trong suốt 10% (`#1AFFFFFF`).
- **Hardcode tùy tiện:** Dù đã có file `colors_palette.xml` làm chuẩn, nhưng trong `colors.xml` vẫn xuất hiện rất nhiều mã HEX cứng lẻ tẻ như `#CC1A1C29` (`mosco_surface_container_high_80`), `#E60A0A0A` (`mosco_overlay_dark`).

---

## 2. Kích Thước & Khoảng Cách (Dimens)
File `dimens.xml` và `dimens_components.xml` đang bị lạm dụng để khai báo cục bộ thay vì dùng chuẩn Global.

**Vấn đề 1: Sai ngữ nghĩa Semantic (Semantic Mismatch)**
Việc gán biến alias diễn ra khá lộn xộn:
- `spacing_4dp` lại trỏ tới `@dimen/spacing_xs`.
- `spacing_xxs` cũng trỏ tới `@dimen/spacing_xs`.
- Đáng nói nhất là `spacing_12dp` lại được gán bằng `@dimen/radius_lg` (Khoảng cách Margin/Padding lại dùng chung biến với Bo góc).

**Vấn đề 2: Component Dimens bị lặp giá trị (Value Duplication)**
Do chia kích thước theo module (`home_`, `inventory_`, `reveal_`), rất nhiều UI có cùng kích thước nhưng bị đặt tên thành chục biến khác nhau.
- **Icon Size:** `home_chat_send_icon_size` (24dp), `home_streak_icon_size` (24dp), `daily_reward_icon_size` (24dp), `social_icon_size` (24dp). Lẽ ra nên dùng chung một biến `icon_size_md`.
- **Button Height:** Chiều cao nút bấm ở các module (48dp hoặc 56dp) bị phân mảnh thành `item_reveal_result_button_height`, `stage_button_height`, `inventory_action_btn_height`,... 

---

## 3. Ngôn Ngữ & Text (Strings)
Tập trung ở file `strings_common.xml`. Do thiếu quản lý tập trung, nhiều chuỗi thông báo phổ biến bị sao chép nhiều lần.

**Trùng lặp Từ khóa Hành động (Actions):**
- Nút "Confirm" (Xác nhận) có đến 3 biến: `action_confirm`, `dialog_action_confirm`, `common_action_confirm`.
- Nút "Cancel" (Hủy) có 3 biến tương tự: `action_cancel`, `dialog_action_cancel`, `common_action_cancel`.

**Trùng lặp Thông báo (Messages):**
- Thông báo chức năng chưa ra mắt: Xuất hiện dưới dạng `common_msg_coming_soon`, `common_mission_coming_soon`, và `msg_feature_coming_soon`.

---

### Tổng Kết
Tài nguyên của dự án đang trong tình trạng "tạo mới mỗi khi cần" thay vì "tái sử dụng cái đã có". 
Để dọn dẹp, dự án cần thực hiện một đợt Refactor (tái cấu trúc) lớn:
1. Xóa bỏ hoàn toàn các tiền tố biến cũ, quy hoạch tất cả về chuẩn **Liquid Glass** (`lg_`).
2. Gộp các `dimens` có cùng kích thước (vd: 24dp) về chuẩn Global (`icon_size_md`, `spacing_lg`) thay vì gán tên theo tên Màn hình.
3. Hợp nhất các thẻ `<string>` bị trùng lặp ngữ nghĩa.

# Báo Cáo Phân Tích UI Component & Tình Trạng Trùng Lặp

Dựa trên cấu trúc thư mục `res/layout` của dự án Mosco, dưới đây là bản đồ phân loại các "Component" (thành phần giao diện) hiện có, kèm theo đánh giá chi tiết về tình trạng trùng lặp (nhiều phiên bản cho cùng một mục đích) do thiếu quy hoạch từ đầu.

> [!WARNING]
> Có sự trùng lặp lớn ở các nhóm **Card (Thẻ)**, **Dialog (Hộp thoại)** và **Skeleton (Trạng thái chờ)**. Điều này gây khó khăn trong việc đồng bộ UI (ví dụ: đổi bo góc thẻ sẽ phải sửa ở 10 file khác nhau).

---

## 1. Nhóm Card Component (Thẻ & Vật phẩm)
Đây là nhóm chịu ảnh hưởng nặng nhất từ việc thiếu quy hoạch, dẫn đến việc mỗi màn hình tự định nghĩa một loại thẻ riêng dù bản chất giao diện rất giống nhau.

| Tên File | Mục Đích Hiện Tại | Đánh Giá Trùng Lặp |
| :--- | :--- | :--- |
| `layout_core_card.xml` | Khung thẻ gốc | **Tốt**. Đáng lẽ mọi thẻ nên dùng cái này (include/merge). |
| `item_collection_book_card.xml` | Thẻ trong màn Collection | ⚠️ Lặp lại cấu trúc thẻ |
| `item_inventory_card.xml` | Thẻ trong màn Kho đồ | ⚠️ Lặp lại cấu trúc thẻ |
| `item_shop_card.xml` | Thẻ trong Cửa hàng | ⚠️ Lặp lại cấu trúc thẻ |
| `item_spin_card.xml` | Thẻ khi quay Gacha | ⚠️ Lặp lại cấu trúc thẻ |
| `item_secret_card.xml` | Thẻ ẩn (mặt sau) | ⚠️ Có thể gộp vào trạng thái của Core Card |
| `item_stage_map_card.xml` | Thẻ trên bản đồ nhiệm vụ | ⚠️ Lặp lại cấu trúc thẻ |
| `item_collection_objet.xml` | Hiển thị Objet (vật phẩm nhỏ) | ⚠️ Lặp lại logic hiển thị hình ảnh nhỏ |
| `item_inventory_item.xml` | Vật phẩm trong kho | ⚠️ Trùng lặp với collection_objet |

---

## 2. Nhóm Dialog Component (Hộp Thoại)
Quá nhiều hộp thoại cơ bản (Chỉ có Tiêu đề, Nội dung, Nút Hủy, Nút Đồng ý) bị tách thành từng file XML riêng biệt thay vì dùng chung một `layout_mosco_dialog_base.xml` và set text động.

**Các hộp thoại cảnh báo/xác nhận (Có thể gộp thành 1 Alert Dialog dùng chung):**
- `dialog_exit_confirm.xml` (Thoát app)
- `dialog_logout_confirm.xml` (Đăng xuất)
- `dialog_discard_changes.xml` (Hủy chỉnh sửa)
- `dialog_connection_lost.xml` (Mất mạng)
- `dialog_session_expired.xml` (Hết hạn đăng nhập)
- `dialog_friend_confirm.xml` (Xác nhận kết bạn)
- `dialog_spin_confirm.xml` (Xác nhận quay thẻ)

**Các hộp thoại thông tin/chi tiết (Có thể gộp thành 1 Detail Dialog dùng chung):**
- `dialog_item_detail.xml` (Chi tiết item)
- `dialog_objet_detail.xml` (Chi tiết Objet)
- `dialog_collection_detail.xml` (Chi tiết bộ sưu tập)
- `dialog_collection_book_detail.xml`

**Các hộp thoại độc bản (Nên giữ lại):**
- `dialog_avatar_zoom.xml`, `dialog_galactic_qr.xml`, `dialog_shop_buy.xml`, `dialog_use_buff.xml`

---

## 3. Nhóm Skeleton / Loading Component (Trạng Thái Chờ)
Các hiệu ứng tải trang (Shimmer) đang bị phân mảnh nghiêm trọng. Mỗi màn hình tự vẽ lại các thanh ngang/khung xám thay vì tái sử dụng.

| Tên File | Mục Đích | Đánh Giá Gộp |
| :--- | :--- | :--- |
| `layout_shimmer_row.xml` | Khung ngang loading cơ bản | **Tốt**. Nên dùng làm chuẩn. |
| `item_friend_skeleton_row.xml` | Loading bạn bè | ⚠️ Nên thay bằng `layout_shimmer_row.xml` |
| `item_skeleton_rank_row.xml` | Loading bảng xếp hạng | ⚠️ Nên thay bằng `layout_shimmer_row.xml` |
| `item_objet_skeleton.xml` | Loading Objet | ⚠️ Trùng lặp khung vuông |
| `layout_objekt_card_skeleton.xml`| Loading Thẻ | ⚠️ Trùng lặp với khung dọc của thẻ |
| `item_inventory_shimmer.xml` | Loading Kho đồ | ⚠️ Có thể gộp với objekt_card_skeleton |
| `layout_profile_guest_shimmer.xml`| Loading Profile Khách | ⚠️ Trùng cấu trúc cơ bản với Owner |
| `layout_profile_shimmer.xml` | Loading Profile Chủ | ⚠️ Lặp layout header |

---

## 4. Nhóm User/List Row Component (Danh Sách Người Dùng)
Việc hiển thị "Avatar + Tên người dùng + Nút hành động" đang bị nhân bản ở nhiều nơi.

- `item_friend_entry.xml` (Bạn bè)
- `item_friend_request.xml` (Lời mời)
- `item_friend_select.xml` (Chọn bạn bè)
- `item_rank_entry.xml` (Bảng xếp hạng)

> [!TIP]
> **Giải pháp kiến trúc:** Tạo một `layout_user_list_item_base.xml` chứa Avatar + Name + Subtext. Khoảng trống bên phải (Action Area) sẽ dùng `<ViewStub>` hoặc `<FrameLayout>` để nhúng các nút khác nhau (Nút kết bạn, Nút đồng ý, Điểm Rank) tùy ngữ cảnh.

---

## 5. Nhóm Lập Trình & Custom View (Java UI Components)
Bên cạnh XML, các Component còn được khởi tạo thông qua mã Java, và tại đây cũng xuất hiện sự phân mảnh đáng kể:

| Tên Lớp (Java) | Mục Đích | Đánh Giá Trùng Lặp |
| :--- | :--- | :--- |
| `MoscoDialogHelper.java` | Lớp tiện ích Mega-class (~700 dòng) gọi mọi loại dialog từ XML. | ⚠️ Ôm đồm quá nhiều chức năng (hiển thị, binding dữ liệu, network callback). |
| `MoscoDialogManager.java`| Lớp quản lý Dialog mới chuẩn "Liquid Glass" | ⚠️ **Trùng lặp 100% logic** tạo `showConfirm` với `MoscoDialogHelper` bên trên. Sinh ra tình trạng code base có 2 chuẩn gọi Hộp thoại. |
| `MoscoButton.java` | Custom Button tĩnh trên nền Liquid Glass | ⚠️ Trong XML vẫn dùng `<MaterialButton>` là chủ đạo, tạo sự không nhất quán giữa Code và Layout. |
| `MoscoQrDialog.java` | Tạo Dialog mã QR | ⚠️ Trùng lặp với `showQrDialog` (nếu có) trong `MoscoDialogHelper`. |
| Các `*Binder.java` | `CollectionDetailBinder`, `CollectionRewardBinder`, `ObjetDetailBinder` | Đây là các Component lắp ráp thủ công bằng mã nguồn (Programmatic UI). Nên cân nhắc chuyển đổi sang `<include>` Layout hoặc `<fragment>` để dễ bảo trì. |

---

## 6. Nhóm Action/Bottom Sheet Component (Thao tác & Filter)
Đã có sự phân chia khá tốt ở một số component chức năng.

**Tốt (Đã chia component hợp lý):**
- `layout_guest_actions.xml` (Nút Profile Khách)
- `layout_owner_actions.xml` (Nút Profile Chủ)
- `layout_mosco_search_bar.xml` (Thanh tìm kiếm chung)
- `view_inventory_filter_bar.xml` (Thanh lọc chung)

**Trùng lặp (Bottom Sheet Filter):**
- `layout_bottom_sheet_objet_filter.xml`
- `layout_inventory_bottom_sheet.xml`
*(Có thể gộp thành 1 Bottom Sheet Filter động)*

---

### Tổng Kết
Hệ thống hiện tại có khoảng **~115 file layout** và hàng chục **Custom View / Helper Java**, trong đó:
- Từ **30 - 40 file XML là các phiên bản trùng lặp** của Card, Alert Dialog, Skeleton, User Row.
- Có sự **xung đột hệ sinh thái trong Java**: tồn tại song song `MoscoDialogHelper` (cũ) và `MoscoDialogManager` (chuẩn mới Liquid Glass) thực hiện cùng một chức năng.

Việc tạo ra một **Design System** (hệ thống thiết kế) để quy chuẩn hóa và thu gọn lại các file này (cả XML lẫn class Java) sẽ giảm thiểu đáng kể lỗi giao diện và tăng tốc độ phát triển trong giai đoạn sau.

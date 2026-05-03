---
trigger: always_on
---

# Quy Ước Commit Message (Git Convention) - Mosco Project

Tài liệu này quy định cách đặt tên cho các commit trong dự án Mosco để đảm bảo tính chuyên nghiệp và dễ dàng tra cứu lịch sử thay đổi.

## 1. Cấu Trúc Cơ Bản
Mọi commit phải tuân theo định dạng:
`type(scope): description`

- **type**: Loại thay đổi (bắt buộc).
- **scope**: Phạm vi ảnh hưởng (tên module, màn hình, hoặc tính năng).
- **description**: Mô tả ngắn gọn thay đổi.

---

## 2. Các Loại Type Bắt Buộc
| Type | Ý nghĩa | Khi nào dùng |
| :--- | :--- | :--- |
| **feat** | Feature | Thêm một tính năng mới cho hệ thống. |
| **fix** | Bug Fix | Sửa một lỗi nào đó trong code. |
| **style** | Style/UI | Thay đổi liên quan đến UI, format, spacing (không ảnh hưởng logic). |
| **refactor** | Refactor | Tái cấu trúc code để sạch hơn, dễ đọc hơn (không đổi tính năng). |
| **perf** | Performance | Tối ưu hóa hiệu năng, tốc độ xử lý. |
| **docs** | Documentation | Cập nhật tài liệu, README, hoặc ghi chú. |
| **chore** | Chore | Cập nhật dependencies, cấu hình build, Gradle, Proguard. |

---

## 3. Phạm Vi (Scope) Phổ Biến
Dự án Mosco có các scope chính sau:
- `auth`: Đăng nhập, đăng ký, social login.
- `inventory`: Kho đồ, quản lý thẻ bài.
- `home`: Màn hình chính, streak.
- `gacha`: Hệ thống quay thưởng, nâng cấp.
- `api`: Các service gọi lên server.
- `ui`: Các custom components dùng chung.

---

## 4. Ví dụ Cụ Thể
- `feat(gacha): implement animation for premium card reveal`
- `fix(inventory): prevent NPE when sorting empty collection`
- `style(ui): extract all hardcoded dimensions to dimens.xml`
- `refactor(auth): simplify login flow using BaseService`
- `chore(gradle): upgrade okhttp to latest stable version`

---

## 5. Lưu Ý Quan Trọng
- **Ngôn ngữ:** Khuyến khích sử dụng **Tiếng Anh** cho description để chuẩn hóa quốc tế (vì Git là môi trường chung).
- **Viết thường:** Chữ cái đầu tiên của description nên viết thường.
- **Ngắn gọn:** Không nên viết description quá 50 ký tự.
- **Tần suất:** Nên commit ngay sau khi hoàn thành một đơn vị logic nhỏ, không nên dồn quá nhiều tính năng vào một commit duy nhất.

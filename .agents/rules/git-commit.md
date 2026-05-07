---
trigger: always_on
---

# Quy Ước Commit Message (Git Convention) - Mosco Project

Tài liệu này quy định cách đặt tên cho các commit trong dự án Mosco để đảm bảo tính chuyên nghiệp, thống nhất và dễ dàng tra cứu lịch sử thay đổi trong giai đoạn nước rút.

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
| **style** | Style/UI | Thay đổi liên quan đến UI, Dark Mode, format, spacing. |
| **refactor** | Refactor | Tái cấu trúc code (Clean Code, DRY) không đổi tính năng. |
| **perf** | Performance | Tối ưu hóa hiệu năng, giảm RAM, tối ưu tốc độ mạng/DB. |
| **docs** | Documentation | Cập nhật tài liệu, README, hoặc ghi chú. |
| **chore** | Chore | Cập nhật dependencies, Gradle, cấu hình môi trường. |

---

## 3. Phạm Vi (Scope) Phổ Biến
Dự án Mosco có các scope chính sau:
- `auth`: Đăng nhập, đăng ký, social login.
- `inventory`: Kho đồ, màn Collection, Local-First Room DB.
- `gacha-engine`: Hệ thống quay thưởng, nâng cấp thẻ (Pessimistic Locking).
- `etl`: Hệ thống cào và đồng bộ dữ liệu JSON vào MySQL.
- `network`: Cấu hình API, OkHttp, Interceptor (ép WebP Cloudflare).
- `ui`: Các custom components dùng chung (Shimmer, Animation lật thẻ).
- `api`: Các service phân trang (Pagination) ở phía Server.

---

## 4. Ví dụ Cụ Thể
- `feat(gacha-engine): implement pessimistic lock for fo4 upgrade mechanic`
- `perf(network): add okhttp interceptor to request webp from cloudflare`
- `feat(etl): create scheduled spring boot task to upsert triples data`
- `perf(inventory): apply room db for offline caching and pagination`
- `style(ui): add shimmer loading effect for card list on emulator`

---

## 5. Lưu Ý Quan Trọng
- **Ngôn ngữ:** Khuyến khích sử dụng **Tiếng Anh** cho description.
- **Viết thường:** Chữ cái đầu tiên của description nên viết thường.
- **Ngắn gọn:** Không nên viết description quá 50 ký tự.
- **Tần suất:** Commit ngay sau khi xong một logic nhỏ (ví dụ: xong Interceptor là commit), không dồn code.
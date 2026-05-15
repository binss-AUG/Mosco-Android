# Đặc Tả Kỹ Thuật & Phân Tích Tính Năng Like & Friend (Mosco Project)

Tài liệu này đặc tả toàn bộ kết quả phân tích hiện trạng, phát hiện lỗi nghiêm trọng (Critical Bugs) và đề xuất giải pháp kiến trúc tối ưu cho hệ thống Tương tác Xã hội (Thích hồ sơ và Quản lý bạn bè) trong dự án Mosco, bám sát các nguyên tắc vàng về hiệu năng và trải nghiệm Local-First trên Android 9.

---

## 1. Phân Tích Hiện Trạng & Phát Hiện Lỗi Nghiêm Trọng

### 1.1. Luồng chức năng Like (Thích hồ sơ người chơi khác)
- **Cơ chế hiện tại (Client)**: Khi người dùng truy cập trang cá nhân của một người chơi khác (Guest Mode) và bấm nút Thích (`btnLike` trong `ProfileFragment.java`), Client cập nhật giao diện ngay lập tức (Optimistic UI) bằng cách đảo trạng thái `stats.setLiked(!stats.isLiked())`, tăng/giảm `likesCount`, lưu vào Room DB nội bộ, sau đó gọi phương thức `syncStatsToServer(stats.getLikesCount(), stats.getFriendsCount())`.
- **Nguyên nhân cốt lõi gây lỗi (Root Cause)**:
  - Phương thức `syncStatsToServer` thực hiện gửi yêu cầu `UpdateProfileRequest` chứa trường `likesCount` tới API `PUT /api/user/update-profile`.
  - Tại phía Backend (`UserController.java`), API `update-profile` trích xuất định danh người dùng từ Token xác thực JWT thông qua `request.getAttribute("userId")`. Đây **luôn là ID của người đang đăng nhập (Current User)**, không phải ID của người được xem trang (Target User).
  - **Hệ quả nghiêm trọng**: Số lượt thích mới của Target User bị Server lấy và **ghi đè nhầm vào cột `likesCount` của chính Current User** trong cơ sở dữ liệu. Dữ liệu lượt thích của người được xem trang hoàn toàn không được cập nhật thực tế trên Server.
  - **Thiếu ràng buộc dữ liệu**: Hệ thống Backend hiện chưa có bảng lưu vết Thích (`user_likes`), dẫn đến việc không thể xác minh ai đã thích ai, tạo sơ hở cho việc bấm thích nhiều lần hoặc mất đồng bộ trạng thái khi tải lại dữ liệu.

### 1.2. Luồng chức năng Friend (Quản lý Bạn bè)
- **Cơ chế hiện tại (Client)**:
  - Tại tệp `ProfileFragment.java` (giao diện xem trang cá nhân), các hành động xử lý nút Kết bạn (`btnFriend`), Hủy kết bạn (`showUnfriendDialog`), hoặc Hủy lời mời (`showCancelRequestDialog`) hiện **chỉ dừng lại ở mức cập nhật Local Room DB** (`friendshipStatus`) và hiển thị thông báo Toast.
  - Các lời gọi API thực tế tới Server hoàn toàn bị bỏ trống và để lại dưới dạng các khối chú thích: `// TODO: Gọi API /api/friends/add`, `// TODO: Gọi API /api/friends/remove`.
  - Thay vào đó, mã nguồn tiếp tục gọi sai phương thức `syncStatsToServer(null, stats.getFriendsCount())` đẩy số lượng bạn bè lên API `update-profile` của chính người dùng hiện tại.
- **Sự thiếu đồng bộ với Module có sẵn**:
  - Phía Backend đã có sẵn `FriendController` và `FriendService` với đầy đủ các API chuẩn hóa (`POST /api/friends/add`, `DELETE /api/friends/remove/{friendshipId}`).
  - Phía Client tại màn hình `FriendActivity.java` cũng đã triển khai thành công logic tìm kiếm và gửi lời mời qua `apiService.addFriend()`. Tuy nhiên logic này bị đứt gãy và hoàn toàn chưa được đấu nối vào màn hình `ProfileFragment`.

---

## 2. Đề Xuất Giải Pháp Kiến Trúc & Tái Cấu Trúc

Để giải quyết triệt để các vấn đề trên mà không làm ảnh hưởng đến hiệu năng hay vi phạm các quy tắc của dự án, giải pháp kiến trúc sau được đề xuất:

### 2.1. Tái Cấu Trúc Backend (Spring Boot 3.x & Hibernate)
1. **Thiết lập bảng theo dõi lượt Thích (`user_likes`)**:
   - Tạo mới Entity `UserLike` (hoặc cấu trúc bảng ánh xạ) chứa `likerId` (Người thích) và `targetUserId` (Người được thích), đi kèm `@UniqueConstraint(columnNames = {"likerId", "targetUserId"})` để đảm bảo mỗi người chỉ được thích một hồ sơ duy nhất một lần.
2. **Bổ sung API chuyên dụng cho luồng Like**:
   - Xây dựng API mới: `POST /api/user/{targetUserId}/like`.
   - Logic xử lý sử dụng `@Transactional`: Kiểm tra sự tồn tại của Target User, kiểm tra bản ghi trong bảng `user_likes`. Nếu chưa thích thì thêm mới bản ghi và tăng giá trị `likesCount` của Target User một cách nguyên tử (Atomic Increment). Nếu đã thích thì thực hiện Hủy thích (Bỏ bản ghi và giảm `likesCount`).
   - Trả về đối tượng phản hồi chứa trạng thái `liked` (boolean) và tổng số `likesCount` mới nhất để Client đồng bộ.
3. **Hoàn thiện dữ liệu trả về cho API Friend**:
   - Tối ưu hóa các API gửi lời mời (`/api/friends/add`) và xóa bạn bè (`/api/friends/remove`) để trả về chi tiết trạng thái quan hệ (`friendshipStatus`) và ID quan hệ (`friendshipId`) giúp Client dễ dàng ánh xạ vào Local DB.

### 2.2. Tối Ưu Hóa Android Client (100% Java thuần)
1. **Đấu nối API thực tế vào `ProfileFragment.java`**:
   - Loại bỏ hoàn toàn các khối `// TODO` và thay thế bằng các lời gọi mạng chính xác thông qua `GameApiService`:
     - **Thích hồ sơ**: Gọi `POST /api/user/{targetUserId}/like`.
     - **Gửi lời mời kết bạn**: Gọi `POST /api/friends/add` với Body map chứa `addresseeId`.
     - **Hủy kết bạn / Hủy lời mời**: Lấy thông tin `friendshipId` tương ứng từ danh sách bạn bè nội bộ và gọi `DELETE /api/friends/remove/{friendshipId}`.
2. **Duy trì UX mượt mà với cơ chế Optimistic UI & Auto-Rollback**:
   - Khi người dùng tương tác, giao diện và dữ liệu Room DB Local (`UserStatsDao`) vẫn được cập nhật ngay lập tức thông qua `AppExecutors.getInstance().diskIO()` để đảm bảo độ trễ bằng 0 trên giả lập Android 9.
   - Bổ sung logic **Rollback (Hoàn tác)**: Nếu API trả về lỗi (mất mạng, lỗi máy chủ) hoặc phản hồi không thành công, Client tự động khôi phục trạng thái nút bấm và dữ liệu Room DB về giá trị cũ, kèm thông báo lỗi tinh tế cho người dùng.
3. **Bảo vệ luồng thực thi**:
   - Tuyệt đối tuân thủ việc bọc các thao tác gọi mạng và cập nhật giao diện qua `ClickDebounce(500ms)` nhằm ngăn chặn người dùng bấm liên tục gây nghẽn hàng đợi API hoặc lỗi Race Condition ở Local DB.

---

## 3. Đặc Tả Giao Tiếp API (API Specification)

### 3.1. API Thích / Bỏ thích hồ sơ (New Endpoint)
- **URL**: `POST /api/user/{targetUserId}/like`
- **Headers**: `Authorization: Bearer <JWT_TOKEN>`
- **Phản hồi thành công (200 OK)**:
```json
{
  "status": 200,
  "message": "Đã cập nhật trạng thái thích hồ sơ",
  "data": {
    "liked": true,
    "likesCount": 142
  }
}
```

### 3.2. Lộ trình tích hợp API Bạn bè trên Client
| Hành động UI | Trạng thái hiện tại | API Tích hợp Đề xuất | Xử lý Local Room DB |
| :--- | :--- | :--- | :--- |
| **Bấm nút Thích** | Gọi nhầm `update-profile` | `POST /api/user/{targetUserId}/like` | Cập nhật `liked` và `likesCount` |
| **Bấm Kết bạn** | Chú thích `// TODO` | `POST /api/friends/add` | Chuyển `friendshipStatus` sang `1` (Pending) |
| **Hủy yêu cầu** | Chú thích `// TODO` | `DELETE /api/friends/remove/{friendshipId}` | Chuyển `friendshipStatus` sang `0` (None) |
| **Hủy kết bạn** | Chú thích `// TODO` | `DELETE /api/friends/remove/{friendshipId}` | Chuyển `friendshipStatus` sang `0` và giảm `friendsCount` |

---

## 4. Kế Hoạch Triển Khai (Các tệp sẽ can thiệp ở giai đoạn Code)
1. **Server Layer**:
   - `[NEW] model/UserLike.java`: Thực thể lưu trữ quan hệ Thích kèm ràng buộc duy nhất.
   - `[NEW] repository/UserLikeRepository.java`: Interface truy vấn JPA.
   - `[MODIFY] controller/UserController.java`: Thêm mới endpoint xử lý Thích hồ sơ đảm bảo tính nguyên tử.
2. **Client Layer**:
   - `[MODIFY] network/GameApiService.java`: Khai báo endpoint `POST /api/user/{targetUserId}/like`.
   - `[MODIFY] fragment/ProfileFragment.java`: Tích hợp các API thực tế vào các bộ lắng nghe sự kiện của nút bấm, hoàn thiện cơ chế Optimistic UI kèm tự động Rollback khi có lỗi xảy ra.

> [!IMPORTANT]
> Toàn bộ mã nguồn Client ở giai đoạn tiếp theo bắt buộc viết bằng **100% Java thuần**, không sử dụng Kotlin. Các giải thích kỹ thuật và lý do nghiệp vụ trong mã nguồn phải được chú thích bằng **Tiếng Việt**.

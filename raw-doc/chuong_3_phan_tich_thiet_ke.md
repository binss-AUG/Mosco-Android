# CHƯƠNG 3. PHÂN TÍCH VÀ THIẾT KẾ HỆ THỐNG

### 3.1. Phân tích chức năng hệ thống (Use-case)
Hệ thống Mosco được thiết kế để phục vụ hai tác nhân chính: Khách vãng lai (Guest) và Người chơi (User). 

**3.1.1. Sơ đồ Use-case tổng quát**

*[CHÈN HÌNH 3.1: Sơ đồ Use-case tổng quát (Dùng mã PlantUML tổng quát để tạo ảnh)]*
*Ghi chú cho hình: Hình 3.1: Sơ đồ Use-case tổng quát của hệ thống Mosco*

Hệ thống bao gồm các nhóm chức năng lớn: Xác thực, Quản lý tài khoản, Gameplay (Kho đồ, Gacha, Nâng cấp) và Tương tác xã hội.

**3.1.2. Phân rã nhóm tính năng Tài khoản (Auth & Profile)**

*[CHÈN HÌNH 3.2: Sơ đồ Use-case phân hệ Tài khoản]*
*Ghi chú cho hình: Hình 3.2: Sơ đồ Use-case phân hệ Tài khoản*

Phân hệ tài khoản chịu trách nhiệm quản lý định danh người chơi, hỗ trợ đăng nhập đa nền tảng và đồng bộ hóa siêu tốc khối lượng dữ liệu khổng lồ thông qua cơ chế kéo Manifest. Đảm bảo trải nghiệm xuyên suốt qua việc lưu trữ phiên đăng nhập và bảo mật bằng chuẩn JWT.

**3.1.3. Phân rã nhóm tính năng Gameplay (Core Logic)**

*[CHÈN HÌNH 3.3: Sơ đồ Use-case phân hệ Gameplay]*
*Ghi chú cho hình: Hình 3.3: Sơ đồ Use-case phân hệ Gameplay*

Đây là tính năng cốt lõi của ứng dụng, cho phép người dùng dùng tài nguyên để rút thẻ ngẫu nhiên, hoặc dùng các thẻ phụ làm phôi để nâng cấp thẻ chính nhằm gia tăng chỉ số sức mạnh (OVR). Mọi hoạt động nâng cấp đều được đảm bảo nguyên vẹn nhờ cơ chế khóa giao dịch.

**3.1.4. Phân rã nhóm tính năng Xã hội (Social & Chat)**

*[CHÈN HÌNH 3.4: Sơ đồ Use-case phân hệ Xã hội]*
*Ghi chú cho hình: Hình 3.4: Sơ đồ Use-case phân hệ Xã hội*

Phân hệ Xã hội hỗ trợ người chơi tương tác với nhau thông qua cơ chế kết đôi (Couple Streak), nhắn tin thời gian thực dựa trên giao thức WebSocket và gửi tặng vật phẩm trực tiếp.

### 3.2. Thiết kế Kiến trúc Hệ thống
Dự án Mosco áp dụng mô hình kiến trúc phân lớp chuẩn hóa kết hợp cơ chế **Local-First Architecture** nhằm mang lại trải nghiệm độ trễ bằng không (Zero-latency UX).

*[CHÈN HÌNH 3.5: Sơ đồ kiến trúc hệ thống Client - Server]*
*Ghi chú cho hình: Hình 3.5: Sơ đồ kiến trúc tổng thể hệ thống Mosco*

*   **Phía Server (Spring Boot MVC):** Tuân thủ luồng dữ liệu 1 chiều `Controller -> Service -> Repository -> MySQL`. Tầng Service đảm nhận trọng trách khóa giao dịch (Transaction Locking) và tính toán các luồng nghiệp vụ nhạy cảm như quay thưởng, đập thẻ để chống gian lận.
*   **Phía Client (Android MVVM):** Áp dụng kiến trúc Repository Pattern. Dữ liệu khi cần hiển thị sẽ ưu tiên được truy xuất lập tức từ cơ sở dữ liệu cục bộ (Room SQLite). Song song đó, hệ thống gọi API nền để kéo dữ liệu mới từ Server và ghi đè cập nhật lại Room DB.

### 3.3. Thiết kế Cơ sở dữ liệu (DB Schema - ERD)
Cơ sở dữ liệu của Mosco được thiết kế phân tán nhằm giảm tải cho server trung tâm. Server sử dụng **MySQL** quản lý dữ liệu toàn vẹn, trong khi Client sử dụng **Room SQLite** để lưu cache.

*[CHÈN HÌNH 3.6: Sơ đồ thực thể kết nối ERD]*
*Ghi chú cho hình: Hình 3.6: Sơ đồ thực thể kết nối ERD của cơ sở dữ liệu trung tâm*

*   **Khử chuẩn & Tối ưu khóa ngoại:** Tại một số bảng phụ như `Friendships` hay `UserLikes`, hệ thống sử dụng "Khóa ngoại Logic" (Logical FK) trên tầng mã nguồn Java thay vì ràng buộc vật lý trong MySQL để tránh các truy vấn đệ quy làm thắt cổ chai hệ thống.

### 3.4. Thiết kế Giải thuật và Logic cốt lõi

**3.4.1. Thuật toán Tính toán Tỉ lệ Nâng cấp và Cơ chế Khóa bi quan**
Khi người chơi thực hiện nâng cấp thẻ, hệ thống đối mặt với nguy cơ Race Condition nếu người dùng gửi nhiều request cùng lúc. Giải pháp là kết hợp Cơ chế `PESSIMISTIC_WRITE` (chống double-spending) và Thuật toán tính toán OVR phi tuyến tính để quyết định kết quả.

*Mã giả (Pseudo-code):*
```text
FUNCTION upgradeCard(baseCardId, materialCardIds):
    START TRANSACTION
    
    // 1. Áp dụng Khóa Bi Quan
    mainCard = userCardRepository.findWithLockById(baseCardId)
    IF mainCard IS NULL OR mainCard.level >= 10 THEN ROLLBACK
    
    // 2. Khóa đồng thời các thẻ nguyên liệu
    materials = khóa và lấy toàn bộ danh sách thẻ từ materialCardIds
    IF materials.size < 1 OR materials.size > 5 THEN ROLLBACK
    
    // 3. Tính tỉ lệ thành công
    successRate = Tính toán công thức OVR phi tuyến tính(mainCard, materials)
    
    // 4. Quay RNG quyết định
    roll = ChaosTheoryHelper.nextDouble() * 100.0
    IF roll <= successRate THEN
        mainCard.upgradeLevel += 1
    ELSE
        mainCard.upgradeLevel = MAX(1, mainCard.upgradeLevel - 2)
    
    // 5. Xóa thẻ nguyên liệu và lưu
    userCardRepository.deleteAll(materials)
    userCardRepository.save(mainCard)
    
    COMMIT TRANSACTION
```

**3.4.2. Thuật toán Gacha sinh số ngẫu nhiên từ Nhiễu khí quyển**
Mosco sử dụng nguồn số ngẫu nhiên thực sự thông qua hàm `ChaosTheoryHelper`. Thuật toán sẽ lên lịch chạy nền mỗi 10 phút để tải số True Random từ API nhiễu khí quyển, sau đó XOR với `System.nanoTime()` để làm hạt giống (seed) nhằm tạo ra độ ngẫu nhiên tuyệt đối mà không gây trễ (blocking) luồng game chính.

**3.4.3. Thuật toán Đồng bộ hóa Dữ liệu Delta (Delta Sync Algorithm)**
Việc tải lại danh mục 20.000+ thẻ bài mỗi lần mở app sẽ gây thắt cổ chai mạng và tràn bộ nhớ (OOM). Thuật toán Delta Sync giải quyết vấn đề này bằng cách chỉ đồng bộ những dữ liệu có thay đổi (thêm, sửa, xóa) kể từ lần đồng bộ cuối cùng, hỗ trợ tối đa cho kiến trúc Local-First.

*Mã giả (Pseudo-code):*
```text
FUNCTION executeDeltaSync():
    // 1. Lấy mốc thời gian đồng bộ cuối cùng từ Local
    lastSync = sharedPreferences.getLong("LAST_SYNC_TIME", 0)
    
    // 2. Kéo dữ liệu phần thừa (Delta Payload) từ Server
    payload = api.get("/api/v1/cards/sync?lastSyncTime=" + lastSync)
    IF payload.isEmpty() THEN RETURN
    
    // 3. Phân loại dữ liệu
    upsertList = [], deleteList = []
    FOR EACH item IN payload:
        IF item.isDeleted THEN deleteList.add(item)
        ELSE upsertList.add(item)
            
    // 4. Ghi đè vào Room SQLite bằng Transaction (Background Thread)
    roomDatabase.runInTransaction(() -> {
        cardDao.delete(deleteList)
        cardDao.upsert(upsertList)
    })
    
    // 5. Cập nhật mốc thời gian mới
    sharedPreferences.putLong("LAST_SYNC_TIME", currentTime)
```

**3.4.4. Thuật toán Bộ đệm LRU (Least Recently Used) và Cơ chế Lazy Loading Hình ảnh**
Để xử lý 3GB tài nguyên hình ảnh trên thiết bị RAM yếu (Giả lập Android 9), thuật toán chặn các request tải ảnh qua OkHttp Interceptor. Interceptor tự động tiêm Header `Accept: image/webp` và ép trỏ về `/thumbnail` trên CDN Cloudflare cho giao diện danh sách. Ảnh độ phân giải gốc (`/original`) chỉ được tải theo cơ chế On-Demand, kết hợp cùng **Thuật toán LRU Cache** để liên tục đẩy các bitmap ít được sử dụng nhất ra khỏi bộ nhớ (Garbage Collection), chống Memory Leak.

**3.4.5. Cơ chế Xử lý Tin nhắn Thời gian thực (Real-time Pub/Sub Message Broker)**
Nhằm đảm bảo trải nghiệm tương tác (Chat thế giới, Nhắn tin cá nhân) cho lượng lớn người dùng cùng lúc, hệ thống không dùng HTTP Polling mà áp dụng giao thức WebSocket kết hợp STOMP.

*Cơ chế định tuyến và Xử lý sự cố:*
1. **Định tuyến Pub/Sub (Publish-Subscribe):** Khi tin nhắn được gửi, Server Message Broker (Spring Boot) định tuyến bản tin thông qua In-memory Queue để giảm độ trễ.
   - Với World Chat: Broker phân phát (broadcast) ngay lập tức tới tất cả client đang subscribe `/topic/public`.
   - Với Private Chat: Broker phân giải Session ID qua UserPrincipal và gửi đích danh tới hàng đợi `/user/{userId}/queue/private`.
2. **Lưu trữ Bất đồng bộ (Async Persistence):** Một Worker Thread chạy ngầm sẽ gom các tin nhắn (Batching) để lưu vào MySQL theo từng lô, tránh tình trạng Insert liên tục làm thắt cổ chai DB.
3. **Reconnection & Debounce (Phía Client):** Ứng dụng Android triển khai thuật toán *Exponential Backoff* để tự phục hồi kết nối khi rớt mạng. Các nút gửi tin nhắn được áp dụng `ClickDebounce` nhằm ngăn chặn người chơi spam API làm ngập lụt Server.

### 3.5. Phác thảo giao diện ứng dụng (UI/UX)
Giao diện ứng dụng được thiết kế tối giản, áp dụng phong cách Dark Mode mặc định. Việc ứng dụng Skeleton Loading (Shimmer Effect) và giảm thiểu hiệu ứng lật trang thừa thãi giúp duy trì độ mượt mà.

*[CHÈN HÌNH 3.7: Màn hình Home và Kho đồ]*
*Ghi chú cho hình: Hình 3.7: Giao diện màn hình chính và bộ sưu tập*

*[CHÈN HÌNH 3.8: Màn hình Quay thưởng Gacha]*
*Ghi chú cho hình: Hình 3.8: Giao diện cơ chế quay thưởng Gacha*

*[CHÈN HÌNH 3.9: Màn hình Nâng cấp thẻ]*
*Ghi chú cho hình: Hình 3.9: Giao diện nâng cấp thẻ bài FO4 Style*

### 3.6. Thiết kế Giao tiếp Hệ thống (API Endpoints)
Hệ thống giao tiếp giữa Client (Android) và Server (Spring Boot) được thiết kế theo tiêu chuẩn **RESTful API** đối với các tác vụ phi đồng bộ truyền thống, và **WebSocket** đối với các tương tác thời gian thực. Dữ liệu truyền tải định dạng chuẩn JSON và được bảo mật bởi Spring Security (JWT).

**Bảng 3.1: Danh sách các API Endpoints lõi của hệ thống**

| Phương thức | Endpoint | Chức năng & Mô tả kỹ thuật | Phân hệ |
| :--- | :--- | :--- | :--- |
| **POST** | `/api/auth/login` | Xác thực người dùng và sinh JWT Token. Hỗ trợ cơ chế đăng nhập đa nền tảng (Email/Social). | Tài khoản |
| **GET** | `/api/v1/cards/sync` | Cơ chế **Delta Sync** (Đồng bộ phần thừa). Yêu cầu tham số `lastSyncTime` để server chỉ trả về những thẻ bài mới hoặc bị sửa đổi, tối ưu băng thông cho kiến trúc Local-First. | Hệ thống |
| **POST** | `/api/gacha/spin/{packId}` | Giao dịch quay thẻ Gacha. Gọi thuật toán TRNG nhiễu khí quyển để sinh kết quả ngẫu nhiên, lưu lịch sử và cấp thẻ trong một `@Transactional` thống nhất. | Gameplay |
| **POST** | `/api/cards/upgrade` | Giao dịch đập thẻ (Cơ chế FO4). Truyền vào `baseCardId` và mảng `materialIds`. Thực thi **Pessimistic Lock** để khóa mọi thẻ bài liên quan, ngăn triệt để Double-spending. | Gameplay |
| **POST** | `/api/mailbox/claim-all` | Nhận tất cả thư phần thưởng trong một lần quét. Sử dụng batch update để gom tổng tài nguyên cộng vào tài khoản người chơi với hiệu suất cao nhất. | Xã hội |
| **WS** | `/ws/chat` | Kênh kết nối WebSocket (Sử dụng STOMP Protocol) phục vụ cơ chế Pub/Sub cho tính năng Chat thế giới và nhắn tin riêng tư thời gian thực (Real-time). | Xã hội |

*(Lưu ý: Tất cả các API trừ `/auth` đều phải đính kèm Header `Authorization: Bearer <token>` để đi qua bộ lọc JWT Filter của Spring Security. Mọi endpoint trả về danh sách đều được cấu hình hỗ trợ phân trang `Pageable` để tránh OOM - Out of Memory cho Mobile Client).*

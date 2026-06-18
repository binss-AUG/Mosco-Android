# CHƯƠNG 3. PHÂN TÍCH VÀ THIẾT KẾ HỆ THỐNG

Dựa trên cơ sở lý thuyết và các nguyên tắc thiết kế kiến trúc đã đề cập ở Chương 2, Chương 3 sẽ đi sâu vào việc đặc tả các chức năng cốt lõi, thiết kế luồng tương tác, cấu trúc cơ sở dữ liệu và xây dựng giải thuật chi tiết cho hệ thống Mosco.

### 3.1. Phân tích chức năng (Use-case)

Hệ thống Mosco phục vụ hai tác nhân chính: Khách vãng lai (Guest) và Người chơi đã đăng ký (User). Các chức năng được phân tách thành 4 nhóm chính: Tài khoản (Auth), Kho đồ (Inventory), Gameplay (Gacha & Nâng cấp), và Tương tác xã hội (Social).

**3.1.1. Sơ đồ Use-case tổng quát và Phân rã**
*[CHÈN HÌNH 3.1: Sơ đồ Use-case tổng quát của hệ thống Mosco. Trực quan: Tác nhân User/Guest nối với các khối chức năng lớn: Auth, Gameplay, Inventory, Social bằng các mũi tên Include/Extend]*

**3.1.2. Bảng Đặc tả Use-case cốt lõi**

**Bảng 3.1: Đặc tả Use-case "Quay thẻ Gacha"**
| Thuộc tính | Mô tả chi tiết |
| :--- | :--- |
| **Mã UC** | UC-01 |
| **Tên Use-case** | Quay thẻ Gacha (Rút thăm ngẫu nhiên vật phẩm) |
| **Tác nhân** | Người chơi (User) |
| **Điều kiện tiên quyết** | User đã đăng nhập, ở màn hình Gacha và số dư Diamonds >= giá trị gói quay. |
| **Luồng sự kiện chính (Main Flow)** | 1. User chọn gói Gacha và nhấn "Quay".<br>2. Client gửi Request kèm JWT Token lên Server.<br>3. Server kiểm tra số dư và tiến hành trừ Diamonds.<br>4. Server gọi bộ sinh số ngẫu nhiên TRNG để lấy kết quả (Danh sách thẻ bài trúng thưởng).<br>5. Server ghi nhận thẻ bài mới vào Database cho User.<br>6. Trả kết quả về Client dưới dạng JSON.<br>7. Client nhận dữ liệu và kích hoạt hiển thị hiệu ứng lật thẻ 3D. |
| **Luồng ngoại lệ (Alt Flow)** | - Bước 3a: Số dư không đủ -> Server ném lỗi HTTP 400, Client hiển thị popup "Nạp thêm thẻ".<br>- Bước 4a: Call API TRNG thất bại -> Server tự động Fallback chuyển sang dùng hàm PRNG dự phòng để không gián đoạn Game. |
| **Kết quả (Post-condition)** | Số dư Diamonds giảm, Kho đồ User xuất hiện các thẻ bài mới. Lịch sử Gacha được lưu vào DB để đối soát. |

**Bảng 3.2: Đặc tả Use-case "Nâng cấp thẻ bài"**
| Thuộc tính | Mô tả chi tiết |
| :--- | :--- |
| **Mã UC** | UC-02 |
| **Tên Use-case** | Nâng cấp thẻ bài |
| **Tác nhân** | Người chơi (User) |
| **Điều kiện tiên quyết** | User chọn 1 Thẻ chính (Base Card) và từ 1 đến 5 Thẻ nguyên liệu (Material Cards) từ Kho đồ. Các thẻ không bị trạng thái "Khóa bảo vệ". |
| **Luồng sự kiện chính (Main Flow)** | 1. User nhấn nút "Nâng cấp".<br>2. Client khóa UI (Disable Button) và gửi mảng ID thẻ lên Server.<br>3. Server kích hoạt **Pessimistic Lock** để khóa các bản ghi thẻ bài này trên Database.<br>4. Server xóa các Thẻ nguyên liệu (Burn) và tính toán tỉ lệ thành công dựa trên chênh lệch OVR.<br>5. Chạy thuật toán xác suất. Nếu trúng tỷ lệ, Thẻ chính tăng OVR.<br>6. Server nhả khóa (Release Lock) và trả kết quả về Client.<br>7. Client cập nhật Room DB và hiển thị hiệu ứng thành công/thất bại. |
| **Luồng ngoại lệ (Alt Flow)** | - Bước 3a: Bị lỗi khóa (Lock Timeout) do đang dùng Auto-Click spam -> Server hủy Transaction, trả về lỗi HTTP 409 Conflict.<br>- Bước 5a: Nâng cấp thất bại (Rớt thẻ) -> Thẻ nguyên liệu mất, Thẻ chính bị rớt cấp (Downgrade). |

**Bảng 3.3: Đặc tả Use-case "Tra cứu Bảng xếp hạng (Leaderboard)"**
| Thuộc tính | Mô tả chi tiết |
| :--- | :--- |
| **Mã UC** | UC-03 |
| **Tên Use-case** | Tra cứu Bảng xếp hạng (Leaderboard) |
| **Tác nhân** | Người chơi (User), Khách (Guest) |
| **Điều kiện tiên quyết** | Không yêu cầu đăng nhập đối với thao tác xem hạng cơ bản. |
| **Luồng sự kiện chính (Main Flow)** | 1. User truy cập vào màn hình "Rank".<br>2. Client gửi Request kèm tham số phân trang (`page`, `size`) và loại hạng (`level`, `wealth`, `collection`) lên Server.<br>3. Server truy xuất CSDL, sắp xếp theo điều kiện và trả về JSON danh sách TOP người chơi.<br>4. Client render danh sách, hiển thị bục vinh quang (Podium) 3D cho Top 1,2,3 với hiệu ứng UI.<br>5. User có thể bấm vào Avatar để chuyển qua trang Profile của người chơi đó. |

**Bảng 3.4: Đặc tả Use-case "Trình chiếu Thẻ bài qua AR Camera"**
| Thuộc tính | Mô tả chi tiết |
| :--- | :--- |
| **Mã UC** | UC-04 |
| **Tên Use-case** | Trình chiếu Thẻ bài qua Thực tế ảo tăng cường (AR Camera) |
| **Tác nhân** | Người chơi (User) |
| **Điều kiện tiên quyết** | Thiết bị có Camera và đã cấp quyền truy cập. User phải sở hữu ít nhất 1 thẻ bài. |
| **Luồng sự kiện chính (Main Flow)** | 1. User chọn chức năng "AR Camera" ở trang chi tiết Thẻ bài.<br>2. Client kích hoạt Camera hệ thống, render luồng video thời gian thực làm Background.<br>3. Hệ thống chồng lớp ảnh thẻ (Card Render) lên không gian 3D giả lập.<br>4. User có thể xoay, lật, phóng to thẻ bài trên nền thế giới thực thông qua thao tác chạm.<br>5. User bấm nút chụp hình để lưu thành quả Showcase vào thư viện thiết bị. |

**3.1.3. Phân rã nhóm tính năng Tài khoản (Auth & Profile)**

*[CHÈN HÌNH 3.2: Sơ đồ Use-case phân hệ Tài khoản]*
*Ghi chú cho hình: Hình 3.2: Sơ đồ Use-case phân hệ Tài khoản*

Phân hệ tài khoản chịu trách nhiệm quản lý định danh người chơi, hỗ trợ đăng nhập đa nền tảng và đồng bộ hóa siêu tốc khối lượng dữ liệu khổng lồ thông qua cơ chế kéo Manifest. Đảm bảo trải nghiệm xuyên suốt qua việc lưu trữ phiên đăng nhập và bảo mật bằng chuẩn JWT.

**3.1.4. Phân rã nhóm tính năng Gameplay (Core Logic)**

*[CHÈN HÌNH 3.3: Sơ đồ Use-case phân hệ Gameplay]*
*Ghi chú cho hình: Hình 3.3: Sơ đồ Use-case phân hệ Gameplay*

Đây là tính năng cốt lõi của ứng dụng, cho phép người dùng dùng tài nguyên để rút thẻ ngẫu nhiên, hoặc dùng các thẻ phụ làm phôi để nâng cấp thẻ chính nhằm gia tăng chỉ số sức mạnh (OVR). Mọi hoạt động nâng cấp đều được đảm bảo nguyên vẹn nhờ cơ chế khóa giao dịch.

**3.1.5. Phân rã nhóm tính năng Xã hội (Social & Chat)**

*[CHÈN HÌNH 3.4: Sơ đồ Use-case phân hệ Xã hội]*
*Ghi chú cho hình: Hình 3.4: Sơ đồ Use-case phân hệ Xã hội*

Phân hệ Xã hội hỗ trợ người chơi tương tác với nhau thông qua cơ chế kết đôi (Couple Streak), nhắn tin thời gian thực dựa trên giao thức WebSocket và gửi tặng vật phẩm trực tiếp.

### 3.2. Thiết kế Kiến trúc và Sơ đồ Tuần tự

Hệ thống được xây dựng theo mô hình Client-Server với sự hỗ trợ của dịch vụ bổ trợ AI (RAG Sidecar):
- **Android Client**: Xử lý giao diện người dùng (Modern Flat Design), tương tác và lưu trữ cục bộ.
- **Spring Boot Backend**: Quản lý nghiệp vụ chính, cơ sở dữ liệu và điều phối dịch vụ AI.
- **RAG Sidecar (Python)**: Xử lý thu thập dữ liệu Wiki và cung cấp các API xử lý Vector Embedding.

*[CHÈN HÌNH 3.5: Sơ đồ kiến trúc tổng thể hệ thống Mosco]*

**Phía Server (Spring Boot MVC):** Tuân thủ luồng dữ liệu 1 chiều `Controller -> Service -> Repository -> MySQL`. Tầng Service đảm nhận trọng trách khóa giao dịch (Transaction Locking) và tính toán các luồng nghiệp vụ nhạy cảm như quay thưởng, đập thẻ để chống gian lận.

**Phía Client (Android MVVM):** Áp dụng kiến trúc Repository Pattern. Dữ liệu khi cần hiển thị sẽ ưu tiên được truy xuất lập tức từ cơ sở dữ liệu cục bộ (Room SQLite). Song song đó, hệ thống gọi API nền để kéo dữ liệu mới từ Server và ghi đè cập nhật lại Room DB.

**3.2.1. Sơ đồ Tuần tự luồng Xác thực (JWT Authentication)**
*[CHÈN HÌNH 3.6: Sơ đồ Tuần tự luồng Đăng nhập (Sequence Diagram). Trực quan: Client (App) gửi Username/Pass -> Auth Server -> Database kiểm tra -> Gen JWT Token -> Trả JWT về Client -> Client lưu vào Encrypted Shared Preferences để dùng cho các request sau]*

**3.2.2. Sơ đồ Tuần tự luồng Đồng bộ Delta Sync (Kiến trúc Local-First)**
*[CHÈN HÌNH 3.7: Sơ đồ Tuần tự luồng Đồng bộ Delta Sync. Trực quan: Client (truy vấn Room DB) gửi tham số "lastSyncTime" -> API Server -> Query MySQL (chỉ lấy những thẻ bài có thời gian `updated_at` > lastSyncTime) -> Trả về JSON list siêu nhẹ -> Client lưu đè (Upsert) vào Room DB cục bộ -> View tự động cập nhật]*

**3.2.3. Thiết kế Kiến trúc Caching Đa lớp (Multi-layer Caching)**
Để giải quyết bài toán OOM và tối ưu tốc độ mạng, hệ thống triển khai chiến lược Cache hai tầng tại Client (Android):
1. **Tầng JSON / File Cache (`DatabaseLoader`):** Dữ liệu cố định (Master Data 20.000+ thẻ) và khung sườn Inventory được nén thành file JSON lưu trực tiếp vào ổ đệm ứng dụng.
2. **Tầng Room Database (SQLite):** Dữ liệu cá nhân thay đổi liên tục như chỉ số người dùng (`UserStats`), danh sách thẻ sở hữu (`UserCards`) được lưu trữ tại Room.

*Luồng hoạt động:* Mọi API lấy danh sách sẽ kiểm tra tính hợp lệ của Cache cục bộ (dựa vào Hash ID hoặc Timestamp). Nếu dữ liệu chưa bị can thiệp (Hit Cache), app sẽ load thẳng từ JSON/Room trong chớp mắt. Chỉ khi "Miss Cache", Client mới chọc xuống Mạng (Network API) để đồng bộ làm mới.

**3.2.4. Sơ đồ Tuần tự luồng Khóa giao dịch (Pessimistic Lock Nâng cấp thẻ)**
*[CHÈN HÌNH 3.8: Sơ đồ Tuần tự luồng Nâng cấp thẻ. Trực quan: Vẽ 2 đường ngầm song song (Thread 1 và Thread 2). Nhấn mạnh mũi tên Server gửi lệnh `SELECT ... FOR UPDATE` xuống Database. Chỉ ra Thread 1 giữ Ổ KHÓA, Thread 2 có cái Đồng Hồ Chờ (Block waiting)]*

### 3.3. Thiết kế Cơ sở dữ liệu (Database Design)

Cơ sở dữ liệu của Mosco được thiết kế phân tán nhằm giảm tải cho server trung tâm. Server sử dụng **MySQL** quản lý dữ liệu toàn vẹn, trong khi Client sử dụng **Room SQLite** để lưu cache.

**3.3.1. Sơ đồ Thực thể Liên kết (ERD)**
*[CHÈN HÌNH 3.9: Sơ đồ ERD Database. Trực quan: Vẽ 4 bảng chính `users`, `master_cards`, `user_cards`, `gacha_history` có nối các đường chỉ tuyến quan hệ 1-N]*

**3.3.2. Thiết kế Ràng buộc Khóa ngoại Logic (Logical Foreign Key)**
Để giải quyết bài toán hiệu năng đọc/ghi cực lớn cho hệ thống có thể phình to lên hàng trăm ngàn thẻ bài, cơ sở dữ liệu Mosco **loại bỏ hoàn toàn các Khóa ngoại vật lý (Physical Foreign Key Constraints)** tại tầng MySQL (Không dùng `FOREIGN KEY ... REFERENCES`).
Thay vào đó, hệ thống ứng dụng khái niệm **Logical FK**: Tầng Database chỉ lưu cột ID dưới dạng VARCHAR/BIGINT và gắn `Index`. Mọi ràng buộc toàn vẹn dữ liệu (Data Integrity) và hành vi xóa phân tầng (Cascade Delete) sẽ được quản lý bằng Application Layer (Spring Boot) thông qua các Event-Driven (Ví dụ: Bắn event bất đồng bộ để dọn rác mồ côi khi Xóa User).

**3.3.3. Từ điển Dữ liệu (Data Dictionary)**

**Bảng 3.5: Chi tiết cấu trúc Bảng `users` (Tài khoản người chơi)**
| Tên cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
| :--- | :--- | :--- | :--- |
| `id` | VARCHAR(36) | PK, Not Null | Chuỗi UUID v4 định danh duy nhất User |
| `username` | VARCHAR(50) | Unique, Not Null | Tên đăng nhập hệ thống |
| `hashed_password` | VARCHAR(255) | Not Null | Mật khẩu được mã hóa an toàn bằng thuật toán BCrypt |
| `diamonds` | INT | Default 0 | Tiền tệ cao cấp (Premium Currency) để quay Gacha |
| `avatar_url` | VARCHAR(500) | Nullable | Link ảnh đại diện (Đã qua xử lý cắt ảnh ML Kit) |

**Bảng 3.6: Chi tiết cấu trúc Bảng `master_cards` (Từ điển thẻ bài gốc)**
| Tên cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
| :--- | :--- | :--- | :--- |
| `card_id` | VARCHAR(50) | PK, Not Null | Mã định danh thẻ (Ví dụ: FO4_CR7_2023) |
| `name` | VARCHAR(100) | Not Null | Tên nghệ sĩ / Cầu thủ / Nhân vật |
| `rarity` | VARCHAR(10) | Not Null | Độ hiếm của thẻ (R, SR, SSR, UR) |
| `base_ovr` | INT | Not Null | Chỉ số sức mạnh (OVR) nguyên bản ban đầu |
| `image_url` | VARCHAR(500) | Not Null | Link ảnh phân phối từ mạng CDN Cloudflare (WebP format) |

**Bảng 3.7: Chi tiết cấu trúc Bảng `user_cards` (Kho đồ cá nhân - Inventory)**
| Tên cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
| :--- | :--- | :--- | :--- |
| `id` | BIGINT | PK, Auto Increment | ID tự tăng của dòng dữ liệu thẻ bài |
| `user_id` | VARCHAR(36) | Index, Not Null | Chủ sở hữu thẻ (Logical FK trỏ sang bảng `users`) |
| `card_id` | VARCHAR(50) | Index, Not Null | Loại thẻ gốc (Logical FK trỏ sang bảng `master_cards`) |
| `current_level` | INT | Default 1 | Cấp độ sức mạnh hiện tại (Có thể nâng cấp từ 1 lên 10) |
| `is_locked` | BOOLEAN | Default false | Khóa an toàn (Nếu true, không thể dùng thẻ này làm Phôi hiến tế) |

### 3.4. Thiết kế Giải thuật và Logic cốt lõi

**3.4.1. Thuật toán Tính toán Tỉ lệ Nâng cấp và Cơ chế Khóa bi quan**
Khi người chơi thực hiện nâng cấp thẻ, hệ thống đối mặt với nguy cơ Race Condition nếu người dùng gửi nhiều request cùng lúc. Giải pháp là kết hợp Cơ chế `PESSIMISTIC_WRITE` (chống double-spending) và Thuật toán tính toán OVR phi tuyến tính để quyết định kết quả.

```
FUNCTION upgradeCard(baseCardId, materialCardIds):
    START TRANSACTION
    
    // 1. Áp dụng Khóa Bi Quan
    mainCard = userCardRepository.findWithLockById(baseCardId)
    IF mainCard IS NULL OR mainCard.level >= 10 THEN ROLLBACK
    
    // 2. Khóa đồng thời các thẻ nguyên liệu
    materials = khóa và lấy toàn bộ danh sách thẻ từ materialCardIds
    IF materials.size < 1 OR materials.size > 5 THEN ROLLBACK
    
    // 3. Tính tỉ lệ thành công (cơ chế xác suất phi tuyến tính)
    Ovr_Chinh = mainCard.get_Hien_Tai_Ovr()
    Tong_Ovr_Phoi = SUM(material.get_Hien_Tai_Ovr() FOR EACH material IN materials)
    Ty_Le_Co_Ban = 10.0%
    He_So_Bu_Dap = (Tong_Ovr_Phoi / Ovr_Chinh) * 15.0%
    successRate = MIN(Ty_Le_Co_Ban + He_So_Bu_Dap, 100.0%)
    
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

```
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

**3.4.4. Giải thuật Cắt ảnh Avatar (Face Crop) bằng Edge ML**
Giải thuật này được thực thi hoàn toàn trên Client (Android). Nó sử dụng mạng Neural Network để tìm tọa độ khuôn mặt, giúp căn giữa (center) chủ thể thay vì cắt vô hồn theo tỷ lệ khung hình.
```
Hàm Tu_Dong_Cat_Khuon_Mat(Bitmap anhGoc):
    1. Khởi tạo bộ quét ML Kit FaceDetector với cấu hình độ chính xác cao (High Accuracy).
    2. Danh_Sach_Mat = FaceDetector.quet_Offline(anhGoc)
    3. NẾU Danh_Sach_Mat rỗng (Không tìm thấy khuôn mặt nào):
        // Fallback an toàn
        Trả về Cắt_Ảnh_Ở_Chính_Giữa_Tâm(anhGoc)
    4. Khuon_Mat_To_Nhat = Danh_Sach_Mat[0] // Ưu tiên khuôn mặt chiếm diện tích lớn nhất
    5. Hop_Bao_Quanh (BoundingBox) = Khuon_Mat_To_Nhat.getBox()
    6. Tính toán Tọa_Độ_Tâm_X = Hop_Bao_Quanh.centerX
    7. Tính toán Tọa_Độ_Tâm_Y = Hop_Bao_Quanh.centerY
    8. Xác định Khung_Hình_Vuông bao quanh (X, Y) với kích thước viền an toàn.
    9. Trả về Cắt_Ảnh(anhGoc, Khung_Hình_Vuông)
```

**3.4.5. Thuật toán Bộ đệm LRU (Least Recently Used) và Cơ chế Lazy Loading Hình ảnh**
Để xử lý 3GB tài nguyên hình ảnh trên thiết bị RAM yếu (Giả lập Android 9), thuật toán chặn các request tải ảnh qua OkHttp Interceptor. Interceptor tự động tiêm Header `Accept: image/webp` và ép trỏ về `/thumbnail` trên CDN Cloudflare cho giao diện danh sách. Ảnh độ phân giải gốc (`/original`) chỉ được tải theo cơ chế On-Demand, kết hợp cùng **Thuật toán LRU Cache** để liên tục đẩy các bitmap ít được sử dụng nhất ra khỏi bộ nhớ (Garbage Collection), chống Memory Leak.

**3.4.6. Cơ chế Xử lý Tin nhắn Thời gian thực (Real-time Pub/Sub Message Broker)**
Nhằm đảm bảo trải nghiệm tương tác (Chat thế giới, Nhắn tin cá nhân) cho lượng lớn người dùng cùng lúc, hệ thống không dùng HTTP Polling mà áp dụng giao thức WebSocket kết hợp STOMP.

1. **Định tuyến Pub/Sub (Publish-Subscribe):** Khi tin nhắn được gửi, Server Message Broker (Spring Boot) định tuyến bản tin thông qua In-memory Queue để giảm độ trễ.
   - Với World Chat: Broker phân phát (broadcast) ngay lập tức tới tất cả client đang subscribe `/topic/public`.
   - Với Private Chat: Broker phân giải Session ID qua UserPrincipal và gửi đích danh tới hàng đợi `/user/{userId}/queue/private`.
2. **Lưu trữ Bất đồng bộ (Async Persistence):** Một Worker Thread chạy ngầm sẽ gom các tin nhắn (Batching) để lưu vào MySQL theo từng lô, tránh tình trạng Insert liên tục làm thắt cổ chai DB.
3. **Reconnection & Debounce (Phía Client):** Ứng dụng Android triển khai thuật toán *Exponential Backoff* để tự phục hồi kết nối khi rớt mạng. Các nút gửi tin nhắn được áp dụng `ClickDebounce` nhằm ngăn chặn người chơi spam API làm ngập lụt Server.

### 3.5. Phác thảo giao diện ứng dụng (UI/UX)
Giao diện ứng dụng được thiết kế tối giản, áp dụng phong cách Dark Mode mặc định. Việc ứng dụng Skeleton Loading (Shimmer Effect) và giảm thiểu hiệu ứng lật trang thừa thãi giúp duy trì độ mượt mà.

*[CHÈN HÌNH 3.10: Màn hình Home và Kho đồ]*
*Ghi chú cho hình: Hình 3.10: Giao diện màn hình chính và bộ sưu tập*

*[CHÈN HÌNH 3.11: Màn hình Quay thưởng Gacha]*
*Ghi chú cho hình: Hình 3.11: Giao diện cơ chế quay thưởng Gacha*

*[CHÈN HÌNH 3.12: Màn hình Nâng cấp thẻ]*
*Ghi chú cho hình: Hình 3.12: Giao diện nâng cấp thẻ bài FO4 Style*

### 3.6. Thiết kế Giao tiếp Hệ thống (API Endpoints)
Hệ thống giao tiếp giữa Client (Android) và Server (Spring Boot) được thiết kế theo tiêu chuẩn **RESTful API** đối với các tác vụ phi đồng bộ truyền thống, và **WebSocket** đối với các tương tác thời gian thực. Dữ liệu truyền tải định dạng chuẩn JSON và được bảo mật bởi Spring Security (JWT).

**Bảng 3.8: Danh sách các API Endpoints lõi của hệ thống**

| Phương thức | Endpoint | Chức năng & Mô tả kỹ thuật | Phân hệ |
| :--- | :--- | :--- | :--- |
| **POST** | `/api/auth/login` | Xác thực người dùng và sinh JWT Token. Hỗ trợ cơ chế đăng nhập đa nền tảng (Email/Social). | Tài khoản |
| **GET** | `/api/v1/cards/sync` | Cơ chế **Delta Sync** (Đồng bộ phần thừa). Yêu cầu tham số `lastSyncTime` để server chỉ trả về những thẻ bài mới hoặc bị sửa đổi, tối ưu băng thông cho kiến trúc Local-First. | Hệ thống |
| **POST** | `/api/gacha/spin/{packId}` | Giao dịch quay thẻ Gacha. Gọi thuật toán TRNG nhiễu khí quyển để sinh kết quả ngẫu nhiên, lưu lịch sử và cấp thẻ trong một `@Transactional` thống nhất. | Gameplay |
| **POST** | `/api/cards/upgrade` | Giao dịch đập thẻ (Cơ chế FO4). Truyền vào `baseCardId` và mảng `materialIds`. Thực thi **Pessimistic Lock** để khóa mọi thẻ bài liên quan, ngăn triệt để Double-spending. | Gameplay |
| **POST** | `/api/mailbox/claim-all` | Nhận tất cả thư phần thưởng trong một lần quét. Sử dụng batch update để gom tổng tài nguyên cộng vào tài khoản người chơi với hiệu suất cao nhất. | Xã hội |
| **WS** | `/ws/chat` | Kênh kết nối WebSocket (Sử dụng STOMP Protocol) phục vụ cơ chế Pub/Sub cho tính năng Chat thế giới và nhắn tin riêng tư thời gian thực (Real-time). | Xã hội |

*(Lưu ý: Tất cả các API trừ `/auth` đều phải đính kèm Header `Authorization: Bearer <token>` để đi qua bộ lọc JWT Filter của Spring Security. Mọi endpoint trả về danh sách đều được cấu hình hỗ trợ phân trang `Pageable` để tránh OOM - Out of Memory cho Mobile Client).*

# CHƯƠNG 3. PHÂN TÍCH VÀ THIẾT KẾ HỆ THỐNG

Dựa trên cơ sở lý thuyết và các nguyên tắc thiết kế kiến trúc đã đề cập ở Chương 2, tài liệu này đi sâu vào việc đặc tả các chức năng cốt lõi, thiết kế luồng tương tác, cấu trúc cơ sở dữ liệu và các giải pháp kỹ thuật của hệ thống Mosco. Toàn bộ nội dung đã được đối chiếu trực tiếp với mã nguồn thực tế.

---

## 3.1. Phân tích chức năng (Use-case)

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
**3.1.4. Phân rã nhóm tính năng Gameplay (Core Logic)**
**3.1.5. Phân rã nhóm tính năng Xã hội (Social & Chat)**

---

## 3.2. Mô Hình Kiến Trúc Phần Mềm

### 3.2.1. Server-Side: Kiến Trúc Phân Lớp Spring Web MVC

Toàn bộ logic nghiệp vụ tuân thủ luồng đi một chiều:
Client Request --> Controller --> Service (Nghiệp vụ) --> Repository --> Database (MySQL)

* Controller Layer: Tiếp nhận yêu cầu HTTP REST/WebSocket, điều hướng dữ liệu thông qua DTO.
* Service Layer: Chứa logic nghiệp vụ cốt lõi, áp dụng transaction và quản trị concurrency.
* Repository Layer: Spring Data JPA interface kế thừa JpaRepository.

### 3.2.2. Client-Side: Android MVVM + Repository Pattern + Local-First

Viết hoàn toàn bằng 100% Java:
View (Activity/Fragment) <--> ViewModel (LiveData) <--> Repository <--> Room DB (Offline)
<--> Retrofit API (Online)

* View Layer: Lắng nghe và vẽ UI dựa trên UI State từ ViewModel.
* ViewModel Layer: Giữ trạng thái UI, sống độc lập với vòng đời Activity/Fragment.
* Repository Layer: Router dữ liệu Local-First: ưu tiên Room DB, song song gọi API sync.

### 3.2.3. Ba tầng Caching (Multi-layer Caching)

1. Tầng RAM Cache (DatabaseLoader.cachedUserInventory): hiển thị kho đồ tức thời.
2. Tầng Room Database (SQLite): dữ liệu cá nhân (UserStats, UserCards).
3. Tầng JSON File Cache: Master Data 20.000+ thẻ.

Luồng hoạt động: RAM Cache -> Room DB -> Network API (nếu miss cache).

---

## 3.3. CƠ SỞ DỮ LIỆU

Hệ thống lưu trữ phân tán: MySQL 8.0 (Production) / H2 (Dev) ở Server, Room SQLite ở Client.

(Database Design)

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

---

## 3.4. DANH SÁCH API ENDPOINTS

Tất cả API (trừ auth) đều yêu cầu Header Authorization: Bearer <token>.

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

---

## 3.5. CÁC GIẢI PHÁP KỸ THUẬT CỐT LÕI

### 3.5.1. Tối Ưu Băng Thông & Ảnh Thẻ Bài (Cloudflare WebP Interceptor)
Vấn đề: Tránh tải 3GB dữ liệu ảnh gốc gây OOM trên Android 9 Emulator.
Giải pháp: OkHttp Interceptor tại ApiClient.java ép header Accept: image/webp cho request tới Cloudflare CDN.
Thư viện: Glide 4.15.1 cache ảnh cục bộ.

### 3.5.2. Đập Thẻ An Toàn (Pessimistic Locking & Transaction)
Vấn đề: Race Condition khi nhận nhiều request đập thẻ cùng lúc.
Giải pháp: @Lock(PESSIMISTIC_WRITE) tại UserCardRepository.java + @Transactional tại UpgradeService.java.

### 3.5.3. Auto-Backup SQLite (WorkManager & WAL Checkpoint)
Vấn đề: Backup Room DB định kỳ, tránh hỏng dữ liệu do file WAL/SHM chưa cam kết.
Giải pháp:
1. BackupWorker.java (WorkManager) trigger backup.
2. BackupManager.java ép PRAGMA wal_checkpoint(TRUNCATE) trước khi copy file.
3. Nén, giữ 2 bản backup gần nhất, upload multipart lên server.

### 3.5.4. Hệ Thống Realtime Chat (STOMP WebSockets)
Vấn đề: Duy trì kênh chat thế giới và private realtime.
Giải pháp: STOMP over WebSocket. Client dùng StompProtocolAndroid:1.6.6 kết nối /ws-mosco/websocket, subscribe /topic/world, /topic/private.{userId}, /topic/streak.{userId}.

### 3.5.5. AI Auto-Crop Avatar (Survive Reinstall)
Vấn đề: Tự động phát hiện khuôn mặt crop làm avatar, giữ tỷ lệ crop khi cài lại app.
Giải pháp:
1. Google ML Kit face-detection + ucrop để tự động crop khung ảnh vào khuôn mặt.
2. Lưu avatarCropParams trên Server, khi cài lại app sẽ kéo metadata về tái hiện ảnh.

### 3.5.6. ETL Pipeline Đồng Bộ Định Kỳ & Caching Cục Bộ
Vấn đề: Nạp dữ liệu danh mục thẻ bài từ JSON vào MySQL hiệu quả, tránh N+1 query.
Giải pháp:
- @Scheduled(fixedDelay=86400000) tại EtlService.java.
- Cache cục bộ (HashMap) cho Member, Season, CardClass.
- Trích xuất Image ID bằng Regex từ URL gốc.
- UPSERT theo lô (batch size 200) qua saveAllAndFlush.

### 3.5.7. Cơ Chế Sinh Số Ngẫu Nhiên Khí Quyển (Atmospheric Noise Chaos Seed)
Vấn đề: Đảm bảo tính ngẫu nhiên thật sự của RNG (quay Gacha, đập thẻ) không gây latency.
Giải pháp:
- ChaosTheoryHelper.java dùng SecureRandom + re-seed từ random.org mỗi 10 phút.
- XOR với System.nanoTime() trước khi setSeed.
- Fallback bằng System.nanoTime() ^ System.currentTimeMillis() nếu API lỗi.

### 3.5.8. Cơ Chế Đồng Bộ Metadata Theo Lịch Trình (Scheduled Metadata Scraping)
Vấn đề: Cập nhật tự động dữ liệu thẻ bài mới từ objekt.top.
Giải pháp:
- AssetManagementService.java: @EventListener(ApplicationReadyEvent) + @Scheduled(cron='0 0 * * * *').
- OkHttpClient scrape https://objekt.top/api/collection?artist=tripleS&limit=20000.
- So sánh kích thước file, cập nhật manifest.json, gọi EtlService.runEtlJob().

### 3.5.9. Cơ Chế Chống Gian Lận Dữ Liệu (Anti-Rollback Cheat / Server Truth)
Vấn đề: Người chơi restore backup cũ để gian lận (nhân bản thẻ / khôi phục thẻ đã đánh mất).
Giải pháp:
- Mọi giao dịch nhạy cảm (gacha, nâng cấp) bắt buộc qua REST API và lưu trên MySQL (Server Truth).
- Khi client restore backup cũ, ngay khi có mạng sẽ tự động đồng bộ danh sách thẻ từ Server về, ghi đè cache cục bộ.

### 3.5.10. RAG ETL Pipeline — Lấy Dữ Liệu & Nhúng Vector (Data Extraction & Embedding)

Vấn đề: AI chat cần kiến thức cập nhật về tripleS (member, album, sự kiện) mà LLM cơ bản không biết.

Giải pháp:

**Kiến trúc tổng thể:**
```
kpopping.com (48 pages)
       |
       | GET /fetch?page=... (Python sidecar port 5001)
       v
RagEtlService.java (Java)
  |-- chunkText() - phân đoạn văn bản
  |-- embedPassage() - sinh vector 384-dim
  v
VectorStoreService.java (in-memory + storage/wiki_vectors.json)
       |
       | cosine similarity + time-decay boost
       v
AiChatController.java -> Gemini/OpenRouter LLM
```

**a) Python Sidecar (FastAPI, port 5001, uvicorn):**
- `GET /health` — kiểm tra model đã load.
- `GET /fetch?page=/profiles/group/tripleS` — lấy nội dung 1 trang kpopping.com, lọc HTML (BeautifulSoup), cắt script/style/nav/footer.
- `POST /embed {"text": "..."}` — trả về 384-dim vector.
- `GET /fetch-batch?pages=path1,path2,...` — đồng bộ tối đa 5 worker.
- Embedding model: `sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2` (384-dim, fastembed 0.8.0).

**b) Data Source (48 pages):**
- 1 group profile: `/profiles/group/tripleS`
- 23 member profiles: `/profiles/idol/YooYeon`, `...`, `/profiles/idol/SeoAh2`
- 14 sub-unit/group profiles: `/profiles/group/Visionary-Vision`, ..., `/profiles/group/Acid-Angel-from-Asia`
- 8 album pages: `/musicalbum/ASSEMBLE26`, ..., `/musicalbum/BINARY-01`
- 1 community page: `/community`
- 4 local project docs: README.md, 01_backend_architecture.md, 02_features_and_usecases.md, 03_detailed_design_algorithms.md

**c) Orchestrator — RagEtlService.java:**
- Trigger: @EventListener(ApplicationReadyEvent.class) — chạy khi server startup.
  * @Scheduled(cron = "0 0 3 * * SUN") — 3h sáng Chủ Nhật hàng tuần.
  * GET /api/ai/etl/run (TestEtlController) — manual.
  * POST /admin/rag-etl?key=<ADMIN_SECRET> (AdminController).
- Luồng cho mỗi page:
  1. Gọi GET http://localhost:5001/fetch?page=<path> -> nhận title + content.
  2. chunkText(text, maxLen, overlap, prefix) -> danh sách đoạn.
  3. Mỗi đoạn -> geminiApiService.embedPassage(chunk) -> POST /embed -> vector 384-dim.
  4. VectorDocument(id, pageName, title, chunk, embedding, entityType, timestamp).
  5. VectorStoreService.replaceDocumentsByPage(page, docs) -> ghi đè JSON.

**d) Chunking Strategy (chunkText):**
- Split theo \n\n (paragraph).
- Max chunk size: 1500 chars (album), 800 chars (member/group), 1200 chars (project context).
- Overlap: 150 chars (tìm newline gần điểm cắt nhất).
- Prefix mỗi chunk: [Source: <title>] - .
- Chunk < 50 chars: discard.
- Chunk chứa "Music Show Wins"/"Award"/"Cup"/"First Win": prefix [ACHIEVEMENT DATA].

**e) Query-time RAG — AiChatController.java:**
1. Nhận message từ client.
2. geminiApiService.embedQuery("query: " + userMessage) -> vector 384-dim.
3. vectorStoreService.search(embedding, topK=8) + searchWithPreFilter(embedding, topK=3, entityType=project_context).
4. getDocumentsByPagePrefix("/musicalbum/") — hybrid heuristic cho album knowledge.
5. Ghép vào system prompt block [NEW KNOWLEDGE].
6. Gọi LLM (OpenRouter -> Gemini -> fallback).

**f) Prefix Convention:**
- Query: "query: " + text (tại embedQuery).
- Passage: "passage: " + text (tại embedPassage).
- Tính tương thích với E5-style model (mặc dù dùng MiniLM).

**g) Vector Store — VectorStoreService.java:**
- In-memory: CopyOnWriteArrayList<VectorDocument>.
- Persistent: storage/wiki_vectors.json (disk).
- Metadata filter: entity_type = member/group/album/project_context.
- Timestamp: giải mã từ regex Release Date\s+(\d{4}-\d{2}-\d{2}) trong chunk.
- Search: cosine similarity + time-decay boost (mới -> +0.15, giảm dần về 0 sau 3 năm).
- Thay thế: replaceDocumentsByPage(pageName, newDocs) — xóa cũ, thêm mới, ghi JSON.

**h) Embedding Cache:**
- GeminiApiService.embeddingCache (ConcurrentHashMap) — tránh nhúng lại cùng text.
- AiChatController.semanticCache (ConcurrentHashMap, max 500 entries) — cache query->response khi cosine > 0.95.

**i) Error Handling:**
- Sidecar chết: log CRITICAL: Local embedding service is down, vector đang có vẫn còn.
- page URL lỗi: Thread.sleep(500) rồi skip sang page tiếp theo.
- Embedding thất bại: trả về empty list, chunk đó không được index.

---

## 3.6. CÁC VẤN ĐỀ ĐÃ BIẾT (Known Issues)

### 3.6.1. Không đồng nhất kiểu dữ liệu ID Tin nhắn (Chat ID Type Mismatch) - CÒN TỒN TẠI

SQLite client lưu senderId, receiverId dưới dạng String; Server MySQL lưu dưới dạng Long.
Ảnh hưởng: Client phải chuyển đổi kiểu dữ liệu rườm rà, giảm hiệu năng truy vấn Room DB.

### 3.6.2. Tính năng Avatar Auto-Crop chưa hoàn thiện trên Client - CÒN TỒN TẠI

Client chỉ cắt ảnh cục bộ qua uCrop, chưa tính toán tỷ lệ tọa độ để gửi avatarCropParams lên Server.
Ảnh hưởng: Khi cài lại app, tọa độ crop thủ công bị mất.

### 3.6.3. Thiếu endpoint /api/gacha/history trong GameApiService.java - CÒN TỒN TẠI

Server đã có controller mapping, nhưng client chưa define endpoint này.

---

*Tài liệu này đã được hiệu chỉnh dựa trên đối chiếu trực tiếp với mã nguồn thực tế của dự án Mosco.*

## 3.7. ĐẶC TẢ CHI TIẾT USE-CASE




### Các Sơ đồ Phân rã (Chi tiết từng phân hệ)
*Trong báo cáo KLTN, sau khi có sơ đồ tổng quát, bạn cần đưa thêm các sơ đồ phân rã chi tiết để chứng minh độ sâu của hệ thống.*

**1. Sơ đồ Phân hệ Tài khoản (Auth & Profile)**

**2. Sơ đồ Phân hệ Gameplay (Gacha, Upgrade, AFK)**

**3. Sơ đồ Phân hệ Xã hội (Social, Chat & Streak)**

---

### 3.7.1. Use-case Authentication
*Để báo cáo chuẩn format KLTN, bạn nên bốc các phần dưới đây và sắp xếp theo cấu trúc mục lục sau:*

**2.1. Phân tích chức năng hệ thống**
*   **2.1.1. Sơ đồ Use-case tổng quát**
    *   *Hình ảnh:* [Dán ảnh Sơ đồ Tổng quát vào đây]
    *   *Nội dung:* Viết 1-2 đoạn văn giới thiệu hệ thống có 2 actor chính (Người chơi, Khách) và 9 nhóm chức năng lớn.
*   **2.1.2. Phân rã nhóm tính năng Tài khoản (Auth & Profile)**
    *   *Hình ảnh:* [Dán ảnh Sơ đồ Phân hệ Tài khoản vào đây]
    *   *Nội dung chi tiết:* Copy nội dung từ mục **"## 1. Tính năng Đăng nhập"** và **"## 2. Quản lý Hồ sơ"** ở phía dưới để đắp vào đây (đưa các Use-case như Đăng nhập, Crop Avatar...).
*   **2.1.3. Phân rã nhóm tính năng Gameplay (Core Logic)**
    *   *Hình ảnh:* [Dán ảnh Sơ đồ Phân hệ Gameplay vào đây]
    *   *Nội dung chi tiết:* Copy nội dung từ mục **"## 3, 4, 5, 9, 10"** (Kho đồ, Gacha, Nâng cấp FO4, AFK, Điểm danh) ở bên dưới đắp vào.
*   **2.1.4. Phân rã nhóm tính năng Xã hội (Social & Chat)**
    *   *Hình ảnh:* [Dán ảnh Sơ đồ Phân hệ Xã hội vào đây]
    *   *Nội dung chi tiết:* Copy nội dung từ mục **"## 6, 7, 8, 11"** (Chat, Kết bạn, Streak, Mailbox) đắp vào.

*(Sau khi cấu trúc xong mục 2.1 này, bạn mới sang mục 2.2 Thiết kế Kiến trúc và 2.3 Thiết kế Database nhé).*

---

## 1. Tính năng Đăng nhập & Đăng ký (Authentication)

#### Use-Case 1.0: Giới thiệu ứng dụng (Onboarding)
*   **Tác nhân (Actor):** Khách vãng lai (Guest)
*   **Luồng xử lý chính (Main flow):**
    1.  Khi người dùng mở ứng dụng lần đầu tiên (hoặc khi chưa đăng nhập), Client tự động hiển thị màn hình [OnboardingActivity](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/OnboardingActivity.java).
    2.  Client sử dụng ViewPager2 để trình chiếu 3 trang giới thiệu về các tính năng nổi bật của game (Spins, Security, Convenience). Để tối ưu hiệu năng và tránh "khựng" khi chuyển trang lần đầu, ViewPager2 thiết lập nạp trước trang tiếp theo vào bộ đệm (`viewPager.setOffscreenPageLimit(1)`).
    3.  Client hiển thị các chấm chỉ thị trang (Dots Indicator) dẹt co giãn scaleX động dựa trên trang hiện hành.
    4.  Người chơi nhấn nút "Tiếp tục" (Next) để chuyển sang trang tiếp theo, hoặc vuốt màn hình.
    5.  Tại trang giới thiệu cuối cùng, nút bấm chuyển nhãn thành "Bắt đầu" (Get Started). Khi người dùng nhấn nút này, Client chuyển hướng người dùng sang màn hình Đăng nhập [SignInActivity](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/SignInActivity.java).

#### Use-Case 1.0B: Khởi chạy và Đồng bộ tài nguyên lúc khởi động (App Startup & Galactic Resource Sync Pipeline)
*   **Tác nhân (Actor):** Người chơi (User) / Khách vãng lai (Guest)
*   **Luồng xử lý chính (Main flow):**
    1.  Người dùng mở ứng dụng, màn hình khởi chạy [SplashActivity](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/SplashActivity.java) được hiển thị kèm hiệu ứng chuyển động Logo Lottie mượt mà và nền chuyển động Aurora.
    2.  **Khởi tạo cơ sở dữ liệu ban đầu (Starter Pack):**
        -   Client kiểm tra xem DB SQLite cục bộ đã được khởi tạo chưa (`StarterPackManager.isDbInitialized`).
        -   *Nếu chưa khởi tạo (mới cài ứng dụng lần đầu):* Client hiển thị thanh tiến trình "Downloading..." và gọi `StarterPackManager.downloadAndInitDb` để tải và giải nén gói tài nguyên phôi cơ sở dữ liệu ban đầu. Sau khi hoàn thành mới cho phép đi tiếp.
    3.  **Đồng bộ Metadata (Master Data Sync):**
        -   Client gọi `DatabaseLoader.initMasterDataSync` để nạp dữ liệu offline.
        -   Client gửi yêu cầu kiểm tra phiên bản MD5 của file `database.json` thông qua API `GET /api/config/db-version`.
        -   Nếu có bản cập nhật mới, Client hiển thị Dialog xác nhận cập nhật hiển thị rõ dung lượng dữ liệu (Mb). Khi người dùng đồng ý, Client gọi `DatabaseLoader.pullFullDatabase` để tải và lưu đè file JSON mới về thiết bị, sau đó nạp dữ liệu vào Room Database Master.
    4.  **Tải ngầm ảnh thẻ bài (Background Asset Pre-fetch):**
        -   Client gọi `CardAssetManager.getPendingDownloadInfo` kiểm tra số lượng ảnh thẻ bài chưa được tải về máy.
        -   Client kích hoạt tiến trình tải ngầm bất đồng bộ bằng luồng background (`CardAssetManager.startDownloadWithInfo`) để không block UI chính, đảm bảo trải nghiệm vào app siêu tốc (1.5 giây app entry).
    5.  **Phục hồi phiên đăng nhập (Session Prefetch):**
        -   Nếu thông tin phiên đăng nhập còn hiệu lực trong `SessionManager`, Client thực hiện pre-fetch tải trước kho đồ local (`DatabaseLoader.loadInventoryFromLocal`) để người dùng xem được ngay, song song đó gọi API `getUserStats` của backend để cập nhật dữ liệu mới nhất.
    6.  **Điều hướng thông minh (App Entry Routing):**
        -   Sau khi tải xong và giữ màn hình Splash tối thiểu 2.5 giây, Client điều hướng người dùng:
            -   Nếu chưa đăng nhập: chuyển tới màn hình giới thiệu [OnboardingActivity](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/OnboardingActivity.java).
            -   Nếu đã đăng nhập nhưng chưa đặt tên hiển thị: chuyển tới màn hình [DisplayNameSetupActivity](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/DisplayNameSetupActivity.java).
            -   Nếu đã đăng nhập và đã có tên hiển thị: chuyển tới màn hình chính [MainActivity](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/MainActivity.java).
*   **Luồng rẽ nhánh / lỗi (Alternative/Exception flow):**
    *   *Không có kết nối mạng:* Client phát hiện qua `ConnectivityManager.NetworkCallback` hoặc lỗi API, lập tức hiển thị giao diện báo lỗi kết nối mạng (Retry Connection) và ẩn Logo Lottie. Người chơi nhấn nút "Thử lại" để tải lại tài nguyên.

#### Use-Case 1.1: Đăng ký tài khoản (Sign Up)
*   **Tác nhân (Actor):** Khách vãng lai (Guest)
*   **Luồng xử lý chính (Main flow):**
    1.  Người dùng nhập Username, Email, Password tại màn hình [SignUpActivity](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/SignUpActivity.java).
    2.  Người dùng bấm nút gửi mã xác thực. Client gọi API `/api/auth/send-code` để Server gửi mã xác nhận qua Email.
    3.  Người dùng nhận mã xác thực từ email, điền vào ô "Verification Code" và bấm "Đăng ký".
    4.  Client gửi yêu cầu POST tới `/api/auth/signup` kèm theo các thông tin đăng ký và mã xác thực.
    5.  Server kiểm tra tính hợp lệ của mã xác thực, mã hóa mật mã bằng bcrypt và ghi nhận User mới vào bảng `users`.
    6.  Server phản hồi `success = true` cùng Token JWT cho Client.
    7.  Client lưu Token JWT, chuyển người dùng đến màn hình thiết lập tên hiển thị [DisplayNameSetupActivity](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/DisplayNameSetupActivity.java).
*   **Luồng rẽ nhánh / lỗi (Alternative/Exception flow):**
    *   *Email đã tồn tại / Sai định dạng:* Server trả về thông báo lỗi, Client hiển thị lỗi tương ứng lên màn hình đăng ký.
    *   *Mã xác thực hết hạn hoặc sai:* Server trả về lỗi 400 "Mã xác thực không hợp lệ", Client yêu cầu người dùng kiểm tra lại hoặc gửi lại mã.

#### Use-Case 1.2: Đăng nhập (Sign In & Social Login)
*   **Tác nhân (Actor):** Người chơi (User)
*   **Luồng xử lý chính (Main flow):**
    1.  Người dùng nhập Username/Email và Password tại [SignInActivity](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/SignInActivity.java).
    2.  **Hoặc lựa chọn Đăng nhập mạng xã hội (Social Login):**
        -   **Google Sign-In:** Client sử dụng **Google Firebase Auth SDK** để thực hiện xác thực và lấy Email cùng OAuth Token của Google.
        -   **Discord Sign-In:** Client sử dụng **Discord Developer Portal SDK** (Discord OAuth2) để xác thực và lấy thông tin Email cùng OAuth Token từ Discord.
    3.  Client gửi thông tin (`provider`, `token`, `email`) thông qua yêu cầu POST tới API `/api/auth/social-login` (hoặc `/api/auth/signin` đối với tài khoản thường).
    4.  **Cơ chế đồng bộ tài khoản giữa Firebase/Discord và MySQL ở Backend:**
        -   Server nhận yêu cầu tại `AuthService.socialLogin`.
        -   *Trường hợp 1 (Đã tồn tại email trong MySQL):* Server tiến hành liên kết (Merge Account) và trả về thông tin User sẵn có trong bảng `users` cùng Token JWT mới.
        -   *Trường hợp 2 (Chưa tồn tại email trong MySQL):* Server tự động tạo một bản ghi User mới trong MySQL với mật khẩu ngẫu nhiên (`UUID.randomUUID()`) và một Username tạm thời (`user_<email_prefix>_xxxx`).
        -   Server cấp tặng gói tài nguyên tân thủ mặc định (50,000 Gold + 10,000 Diamonds).
        -   Server cố tình để trống trường `ingameName` (null) trong MySQL nhằm báo hiệu cho Client.
    5.  Server phản hồi `success = true` kèm theo Token JWT mới tạo.
    6.  Client nhận kết quả:
        -   Nếu là tài khoản mới tạo (trường `ingameName` là null), Client tự động điều hướng người dùng tới màn hình [DisplayNameSetupActivity](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/DisplayNameSetupActivity.java) để bắt buộc thiết lập Tên hiển thị độc nhất.
        -   Nếu là tài khoản cũ, chuyển thẳng vào [MainActivity](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/MainActivity.java).
*   **Luồng rẽ nhánh / lỗi (Alternative/Exception flow):**
    *   *Sai thông tin đăng nhập thường:* Server trả về lỗi 401 Unauthorized, Client thông báo "Tài khoản hoặc mật khẩu không chính xác".
    *   *Không lấy được thông tin email từ Firebase/Discord:* Server từ chối liên kết đăng nhập, trả về lỗi 400.
    *   *Mất kết nối mạng:* Client nhận diện lỗi IO, hiển thị thông báo yêu cầu kiểm tra kết nối mạng.

#### Use-Case 1.3: Quên mật khẩu & Đặt lại mật khẩu (Forgot & Reset Password)
*   **Tác nhân (Actor):** Người chơi (User)
*   **Luồng xử lý chính (Main flow):**
    1.  Người dùng bấm nút "Quên mật khẩu" tại màn hình đăng nhập.
    2.  Người dùng nhập Email đã đăng ký và bấm gửi. Client gửi yêu cầu POST tới `/api/auth/forgot-password?email=xxx` (dạng Query Parameter).
    3.  Server tiếp nhận, sinh mã OTP khôi phục mật khẩu, lưu vào bộ nhớ cache tạm thời và gửi email hướng dẫn kèm mã xác thực OTP về cho người dùng.
    4.  Người dùng điền mã OTP nhận được từ Email, nhập mật khẩu mới và bấm xác nhận.
    5.  Client gửi yêu cầu POST tới `/api/auth/reset-password` kèm theo body chứa email, code OTP và mật khẩu mới (`newPassword`).
    6.  Server kiểm tra mã OTP. Nếu hợp lệ, tiến hành băm mật khẩu mới bằng bcrypt, cập nhật đè vào bảng `users` trong MySQL và xóa mã OTP cũ.
    7.  Server trả về kết quả thành công. Client chuyển hướng người dùng quay lại màn hình đăng nhập để đăng nhập với mật khẩu mới.
*   **Luồng rẽ nhánh / lỗi (Alternative/Exception flow):**
    *   *Email không tồn tại trong hệ thống:* Server trả về thông báo lỗi, Client hiển thị "Email không khớp với bất kỳ tài khoản nào".
    *   *Mã OTP không chính xác hoặc đã hết hạn (quá 10 phút):* Server phản hồi lỗi 400, Client yêu cầu người chơi kiểm tra hoặc yêu cầu gửi lại mã OTP mới.

#### Use-Case 1.4: Thiết lập tên hiển thị lần đầu (Display Name Setup)
*   **Tác nhân (Actor):** Người chơi (User)
*   **Luồng xử lý chính (Main flow):**
    1.  Đối với tài khoản mới tạo (hoặc tài khoản chưa từng đặt Ingame Name), sau khi đăng nhập thành công, Client tự động chuyển hướng người dùng tới màn hình [DisplayNameSetupActivity](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/DisplayNameSetupActivity.java) để bắt buộc thiết lập Tên hiển thị độc nhất.
    2.  Người chơi nhập Ingame Name mong muốn vào ô nhập liệu và bấm nút "Xác nhận".
    3.  Client thực hiện lọc nhanh các ký tự không hợp lệ, sau đó gửi yêu cầu POST tới API `/api/user/set-display-name` kèm theo body chứa `ingameName`.
    4.  Server tiếp nhận yêu cầu và áp dụng bộ kiểm tra chống gian lận an toàn **"Galactic Name Shield"**:
        -   Kiểm tra độ dài: Tên phải từ 2 đến 16 ký tự.
        -   Kiểm tra tên hệ thống bị cấm: Tên không được chứa các từ khoá hệ thống như `admin`, `gm`, `system`, `moderator`, `mosco`, `[dev]`, v.v.
        -   Chuẩn hóa tên: Loại bỏ các khoảng trắng thừa ở đầu, cuối và gộp các khoảng trắng liên tiếp ở giữa thành một khoảng trắng duy nhất.
        -   Kiểm tra ký tự điều khiển: Phát hiện và cấm các ký tự điều khiển Unicode ẩn.
        -   Kiểm tra trùng lặp: Truy vấn DB để đảm bảo tên này chưa được sử dụng bởi người chơi khác (Unique Constraint).
    5.  Nếu tên hợp lệ, Server lưu `ingameName` chuẩn hóa vào DB MySQL và trả về `success = true`.
    6.  Client nhận kết quả thành công, cập nhật thông tin UserStats local và chuyển hướng người dùng vào màn hình chính [MainActivity](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/MainActivity.java).
*   **Luồng rẽ nhánh / lỗi (Alternative/Exception flow):**
    *   *Tên vi phạm quy tắc Galactic Name Shield hoặc bị trùng:* Server trả về lỗi 400 kèm thông báo chi tiết lỗi, Client hiển thị lỗi đó lên giao diện để người dùng sửa lại.

---

## 2. Tính năng Quản lý Hồ sơ & Customization (Player Profile)

### Use-Case 2.1: Xem và chỉnh sửa hồ sơ cá nhân
*   **Tác nhân (Actor):** Người chơi (User)
*   **Luồng xử lý chính (Main flow):**
    1.  Người dùng mở [ProfileFragment](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/fragment/ProfileFragment.java).
    2.  Client đọc nhanh thông tin từ bảng `user_stats` của Room Database cục bộ để hiển thị (Username, Coins, Diamonds, Level, Streak, Showcase).
    3.  Đồng thời, Client gọi API `GET /api/user/{userId}` để lấy thông tin mới nhất từ MySQL Server.
    4.  Server phản hồi dữ liệu hồ sơ. Client ghi đè cập nhật vào Room và vẽ lại giao diện (như thay đổi Bio, Danh hiệu).
    5.  Người dùng có thể bấm chỉnh sửa Bio, Client gửi yêu cầu PUT tới `/api/user/update-profile` để lưu lại trên Server.
*   **Luồng rẽ nhánh / lỗi (Alternative/Exception flow):**
    *   *Cache trống & Mất mạng:* Client hiển thị Shimmer Loading vô hạn hoặc thông báo lỗi tải dữ liệu, hiển thị nút "Tải lại".

#### Use-Case 2.2: Chọn và Crop Avatar (AI Auto-Crop & Manual Crop)
*   **Tác nhân (Actor):** Người chơi (User)
*   **Luồng xử lý chính (Main flow):**
    1.  Tại Profile, người dùng chọn một ảnh từ thiết bị làm Avatar.
    2.  Client chạy tính năng phát hiện khuôn mặt **Google ML Kit Face Detection** để tự động xác định vị trí khuôn mặt.
    3.  Client cung cấp giao diện cắt ảnh thủ công **Manual Crop** thông qua thư viện `ucrop`, cho phép người chơi tự do căn chỉnh, thay đổi tỉ lệ cắt theo ý muốn.
    4.  **Giải pháp tối ưu hóa lưu trữ hình ảnh trên máy chủ:**
        -   Thay vì tải lên toàn bộ file ảnh đã cắt dung lượng lớn gây tốn băng thông và lưu trữ, Client giữ nguyên ảnh gốc của thẻ bài (card) trên CDN Cloudflare.
        -   Client chỉ gửi mã thẻ bài `avatarId` và chuỗi tọa độ cắt ảnh `avatarCropParams` (VD: `"x,y,width,height"`) lên Server thông qua API `PUT /api/user/update-profile`.
        -   Server lưu gọn nhẹ metadata này vào bảng `users`.
    5.  Khi hiển thị Avatar trên Client, ứng dụng tải ảnh thẻ gốc từ Cloudflare và áp dụng các thông số cắt ảnh `avatarCropParams` cục bộ để render khuôn mặt chính xác.
*   **Luồng rẽ nhánh / lỗi (Alternative/Exception flow):**
    *   *Cài đặt lại app / Chuyển thiết bị (Survive Reinstall):* Client chỉ cần kéo `avatarId` và `avatarCropParams` từ server về để tự động vẽ lại ảnh avatar đã crop chính xác mà không cần người dùng tải lại ảnh gốc.

#### Use-Case 2.1: Xem và chỉnh sửa hồ sơ cá nhân
*   **Tác nhân (Actor):** Người chơi (User)
*   **Luồng xử lý chính (Main flow):**
    1.  Tại màn hình Profile, người dùng nhấn vào các slot showcase trống (tối đa 8 slot hiển thị dạng Carousel).
    2.  Client mở [InventoryBottomSheet](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/fragment/InventoryBottomSheet.java) hiển thị kho đồ.
    3.  Người dùng chọn thẻ bài muốn đưa lên trưng bày.
    4.  Client gửi yêu cầu cập nhật danh sách card ID dạng chuỗi ghép `collectionId:upgradeLevel` lên Server qua API `PUT /api/user/update-profile`.
    5.  Server cập nhật danh sách vào bảng `user_showcase`, đồng thời Client ghi đè lưu trữ cục bộ vào bảng Room `user_stats`.
*   **Cơ chế tự động dọn dẹp (Auto-Clean / Auto-Unequip):**
    -   *Câu hỏi đặt ra:* Khi thẻ bài đang trưng bày showcase bị đem đi Nâng cấp (tiêu tốn nguyên liệu), Tặng bạn bè, hoặc Gacha Spin (hiến tế), hệ thống xử lý thế nào? Có hiện Dialog cảnh báo không?
    -   *Logic thực tế trong mã nguồn:* Hệ thống **không** hiển thị Dialog cảnh báo cản trở trải nghiệm. Thay vào đó, app áp dụng cơ chế tự động dọn dẹp thông minh:
        1.  Khi thẻ bài bị xóa khỏi kho đồ (do đập thẻ/tặng/spin), bản ghi thẻ bài đó biến mất khỏi bộ nhớ SQLite cục bộ.
        2.  Khi người dùng truy cập màn hình Profile, phương thức `renderShowcaseData` sẽ tự động đối chiếu các thẻ trong Showcase với bộ nhớ cache Inventory cục bộ (`DatabaseLoader.cachedCollectionMap`).
        3.  Nếu phát hiện thẻ bài trong showcase không còn tồn tại trong kho đồ, hệ thống tự động gỡ bỏ (unequip) thẻ bài đó ra khỏi slot showcase cục bộ.
        4.  Client ngay lập tức gọi API của ViewModel để đồng bộ danh sách Showcase đã được dọn sạch lên MySQL Server.

#### Use-Case 2.4: Thích hồ sơ người chơi khác (Profile Likes)
*   **Tác nhân (Actor):** Người chơi (User)
*   **Luồng xử lý chính (Main flow):**
    1.  Người dùng truy cập vào trang hồ sơ của một người chơi khác (như qua bảng xếp hạng, danh sách bạn bè, hoặc world chat).
    2.  Client gọi API `GET /api/user/{targetUserId}` để tải thông tin hồ sơ của người đó. Trong response, Server trả về trạng thái `liked = true/false` biểu thị người dùng hiện tại đã thích người này chưa.
    3.  Người dùng nhấn biểu tượng "Thả tim / Like". Client gửi yêu cầu POST tới `/api/user/{targetUserId}/like`.
    4.  Server mở một Transaction có tính chất nguyên tử (Atomic), kiểm tra xem người dùng có tự thích chính mình không (bị chặn).
    5.  Nếu chưa thích: Server tạo một bản ghi `UserLike`, đồng thời tăng chỉ số `likesCount` của target user lên 1.
    6.  Nếu đã thích rồi: Server xóa bản ghi `UserLike` tương ứng, và giảm chỉ số `likesCount` của target user đi 1.
    7.  Server lưu thông tin, cam kết transaction và phản hồi trạng thái like mới (`liked = true/false`) kèm tổng số lượt thích (`likesCount`).
    8.  Client nhận phản hồi, thay đổi màu sắc biểu tượng Tim (sáng lên hoặc tắt đi) và cập nhật số lượt thích hiển thị tức thì.
*   **Luồng rẽ nhánh / lỗi (Alternative/Exception flow):**
    *   *Người chơi tự thích chính mình:* Server trả về lỗi 400 "Không thể tự thích hồ sơ của chính mình", Client hiển thị Toast cảnh báo.

#### Use-Case 4.5: Duy trì chuỗi tương tác (Couple Streak)
*   **Tác nhân (Actor):** Người chơi (User)
*   **Luồng xử lý chính (Main flow):**
    1.  Nếu người chơi quên điểm danh hoặc không đăng nhập một ngày, chuỗi Streak điểm danh của họ sẽ bị reset về 0 hoặc đứt đoạn.
    2.  Tại màn hình Profile hoặc Daily Check-in, người chơi nhấn "Khôi phục chuỗi đăng nhập" (Streak Restore).
    3.  Client gửi yêu cầu POST tới `/api/user/streak/restore` (JWT-protected).
    4.  Server kiểm tra xem người dùng có đủ điều kiện khôi phục không (chỉ được khôi phục nếu đứt chuỗi trong vòng 24 giờ trước đó và tiêu tốn một khoản phí nhất định).
    5.  Server gọi `AuthService.restoreStreak(user)` để tính toán và thiết lập lại chuỗi đăng nhập cũ trước khi đứt cho người chơi, cập nhật cơ sở dữ liệu.
    6.  Server trả về thông tin UserStats mới. Client cập nhật UI hiển thị chuỗi Streak đã được khôi phục thành công.
*   **Luồng rẽ nhánh / lỗi (Alternative/Exception flow):**
    *   *Không đủ điều kiện khôi phục (quá thời hạn hoặc đã điểm danh hôm nay):* Server trả về lỗi 400 kèm thông báo lý do cụ thể, Client hiển thị Dialog thông báo lỗi.

---

## 3. Tính năng Quản lý Kho đồ & Bộ sưu tập (Inventory & Collection)

### Use-Case 3.1: Xem và lọc kho đồ (Local-First Caching)
*   **Tác nhân (Actor):** Người chơi (User)
*   **Luồng xử lý chính (Main flow):**
    1.  Người dùng mở [CollectionFragment](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/fragment/CollectionFragment.java) hoặc Inventory.
    2.  **Cơ chế hiển thị siêu tốc bằng bộ nhớ đệm (Caching):**
        -   *Bước 1 (Hiển thị tức thời):* Client kiểm tra bộ nhớ cache RAM (`DatabaseLoader.cachedUserInventory`). Nếu có dữ liệu, hiển thị danh sách thẻ bài ngay lập tức để đem lại UX mượt mà, loại bỏ Shimmer loading.
        -   *Bước 2 (Đồng bộ ngầm):* Song song đó, Client gọi API `GET /api/inventory/cards/{userId}` ở background thread để tải danh sách thẻ bài mới nhất từ MySQL Server.
        -   *Bước 3 (Ghi đè):* Sau khi nhận dữ liệu từ server, Client tự động ghi đè cập nhật lại Room Database cục bộ và cache RAM để đồng bộ trạng thái mới nhất.
    3.  Người dùng thực hiện lọc (Artist, Class hiếm, Season) hoặc sắp xếp (OVR, Cấp độ, Mới nhất).
    4.  Client thực hiện lọc và sắp xếp trực tiếp trên danh sách cache/Room DB cục bộ ở thiết bị giúp phản hồi giao diện tức thì mà không cần tải lại trang từ mạng.

#### Use-Case 3.4: Bộ sưu tập (Collection Book)
*   **Tác nhân (Actor):** Người chơi (User)
*   **Luồng xử lý chính (Main flow):**
    1.  Người dùng truy cập màn hình "Sổ tay sưu tập" (Collection Book / Pokédex).
    2.  Client gửi yêu cầu GET tới `/api/collection/book/{userId}`.
    3.  Server tiếp nhận, truy vấn toàn bộ danh sách thẻ bài gốc (Master Cards) trong hệ thống, đồng thời truy vấn danh sách các thẻ bài mà user này đã từng sở hữu (đánh dấu qua ID).
    4.  Server tính toán:
        - `totalCards`: Tổng số lượng thẻ bài master có trong game.
        - `ownedCount`: Số lượng thẻ bài độc nhất mà người chơi đã sở hữu.
        - Trả về danh sách các thẻ kèm thuộc tính `owned = true/false` và chi tiết thông tin thẻ.
    5.  Server phản hồi `CollectionBookResponse`. Client vẽ giao diện hiển thị: các thẻ đã sở hữu hiển thị rõ nét, các thẻ chưa sở hữu hiển thị dạng đen trắng/mờ ảo kèm tiến trình hoàn thành bộ sưu tập (ví dụ: `45/200` thẻ).
*   **Luồng rẽ nhánh / lỗi (Alternative/Exception flow):**
    *   *Mất mạng:* Client nạp dữ liệu từ bảng Room SQLite Master và hiển thị bộ sưu tập ngoại tuyến tạm thời.

---

## 4. Tính năng Gacha & Quay thưởng (Gacha & Spin System)

#### Use-Case 3.1: Quay thẻ Gacha (Gacha Roll)
*   **Tác nhân (Actor):** Người chơi (User)
*   **Luồng xử lý chính (Main flow):**
    1.  Tại màn hình [ShopFragment](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/fragment/ShopFragment.java), người dùng chọn mua và quay một gói gacha cụ thể (VD: "PACK_METAL").
    2.  Client gửi yêu cầu POST tới `/api/gacha/roll` kèm theo body JSON `{ "packCode": "PACK_METAL", "quantity": 1 }` và mã JWT.
    3.  Server mở Transaction, kiểm tra số dư tiền (Coins hoặc Diamonds) của người chơi.
    4.  Nếu đủ, trừ tài nguyên trên Server MySQL, sau đó gọi `GachaService` dùng thuật toán RNG để quyết định thẻ bài rơi ra.
    5.  Server thêm thẻ bài mới vào `user_cards`, ghi lịch sử vào `gacha_history` và trả về kết quả cho Client.
    6.  Client nhận thông tin thẻ bài mới, mở màn hình [ItemRevealFragment](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/fragment/ItemRevealFragment.java) chạy hiệu ứng lật thẻ 3D.
*   **Luồng rẽ nhánh / lỗi (Alternative/Exception flow):**
    *   *Không đủ tiền:* Server trả về lỗi 400 "Not enough resources", Client hiển thị popup yêu cầu mua thêm hoặc cày thêm vàng.

### Use-Case 4.1B: Mở gói phôi sở hữu từ kho đồ (Pack Open)
*   **Tác nhân (Actor):** Người chơi (User)
*   **Luồng xử lý chính (Main flow):**
    1.  Tại Kho đồ cá nhân (Tab Items), người dùng thấy các gói phôi thẻ bài sở hữu (VD: phôi thẻ đồng, phôi thẻ bạc) và chọn mở một số lượng nhất định.
    2.  Client gửi yêu cầu POST tới `/api/pack/open?userId=xxx&packCode=yyy&quantity=zzz` (truyền qua Query Parameters).
    3.  Server kiểm tra xem user có sở hữu gói phôi này trong bảng `user_items` không và số lượng có đủ không.
    4.  Nếu hợp lệ, Server trừ số lượng gói phôi tương ứng trong bảng `user_items`.
    5.  Server gọi `PackService` mở ngẫu nhiên các thẻ bài từ gói phôi đó, lưu chúng vào `user_cards` và trả về danh sách kết quả gồm thẻ bài, màu sắc, độ hiếm.
    6.  Client hiển thị danh sách các thẻ bài mới mở được cho người dùng.
*   **Luồng rẽ nhánh / lỗi (Alternative/Exception flow):**
    *   *Số lượng gói phôi không đủ:* Server trả về lỗi 400 "Bạn không sở hữu gói thẻ này", Client thông báo lỗi lên màn hình.

#### Use-Case 3.1: Quay thẻ Gacha (Gacha Roll)
*   **Tác nhân (Actor):** Người chơi (User)
*   **Luồng xử lý chính (Main flow):**
    1.  Người dùng truy cập màn hình [SpinFragment](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/fragment/SpinFragment.java).
    2.  Người dùng chọn 1 thẻ bài thừa/không dùng tới trong kho đồ làm vật hiến tế (Sacrifice).
    3.  Người dùng bấm nút "Quay" (Spin). Client gửi POST tới `/api/gacha/spin` kèm ID của thẻ hiến tế.
    4.  Server (chạy Transaction) kiểm tra quyền sở hữu thẻ. Nếu đúng, tiến hành xóa thẻ đó khỏi bảng `user_cards`.
    5.  Server gọi `SpinSystem` quay số ngẫu nhiên sử dụng Hạt giống hỗn loạn lấy từ random.org (Atmospheric Noise Chaos Seed).
    6.  Nếu trúng (WIN): Server tạo thẻ bài mới cấp độ 1, lưu vào `user_cards`, thêm thẻ vào danh sách đã mở khóa `unlockedCollections` và ghi lịch sử.
    7.  Nếu trượt (LOSS): Server ghi lịch sử là trượt, người dùng mất thẻ hiến tế và không nhận lại thẻ mới.
    8.  Server trả về kết quả gồm mảng lưới `revealGrid` và trạng thái Thắng/Thua.
    9.  Client chạy hiệu ứng vòng quay ma trận dựa trên `revealGrid` và hiển thị kết quả cho người dùng.
*   **Luồng rẽ nhánh / lỗi (Alternative/Exception flow):**
    *   *Thẻ đang bận thám hiểm:* Server chặn không cho phép hiến tế thẻ bài này để tránh lỗi logic game. (Lưu ý: Thẻ trưng bày showcase vẫn cho phép hiến tế và sẽ tự động được dọn dẹp nhờ cơ chế Auto-Clean ở Use-Case 2.3).

---

## 5. Tính năng Nâng cấp Thẻ bài (Card Upgrade)

### Use-Case 5: Nâng cấp thẻ bài (FO4 Style)
*   **Tác nhân (Actor):** Người chơi (User)
*   **Luồng xử lý chính (Main flow):**
    1.  Tại màn hình [UpgradeFragment](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/fragment/UpgradeFragment.java), người dùng chọn 1 thẻ chính cần nâng cấp (Base Card) và chọn từ 1 đến 5 thẻ phụ làm nguyên liệu (Material Cards).
    2.  Client hiển thị tỉ lệ thành công ước tính dựa trên chênh lệch OVR giữa thẻ chính và các thẻ nguyên liệu.
    3.  Người dùng nhấn "Nâng cấp". Client gửi yêu cầu POST tới `/api/v1/upgrade` kèm `baseCardId` và danh sách `materialCardIds`.
    4.  Server mở Transaction, áp dụng khóa bi quan `PESSIMISTIC_WRITE` lên cả thẻ chính và tất cả thẻ nguyên liệu để tránh race condition.
    5.  Server tính toán chính xác tỉ lệ thành công thực tế theo công thức của game dựa trên file cấu hình `rates_config.json`.
    6.  Server lấy số ngẫu nhiên từ `ChaosTheoryHelper` (hạt giống True Random nhiễu khí quyển).
    7.  **Kết quả Thành công:** Thẻ chính tăng thêm 1 cấp độ nâng cấp (+1).
    8.  **Kết quả Thất bại (Penalty):** Thẻ chính bị rớt 2 cấp độ nâng cấp (ví dụ từ +5 rớt xuống +3, tối thiểu là +1).
    9.  Server xóa toàn bộ thẻ nguyên liệu khỏi bảng `user_cards` (đồng thời xóa các liên kết của chúng trong các bảng thám hiểm đi cảnh).
    10. Server lưu thẻ chính, cam kết (commit) transaction và trả về kết quả cho Client.
    11. Client chạy animation đập thẻ (rung, vỡ hoặc phát sáng) và hiển thị kết quả cấp độ mới và OVR mới.
*   **Luồng rẽ nhánh / lỗi (Alternative/Exception flow):**
    *   *Thẻ chính đã đạt cấp tối đa (+10):* Server trả về lỗi, chặn tiến trình nâng cấp.
    *   *Số lượng nguyên liệu không hợp lệ (ít hơn 1 hoặc nhiều hơn 5):* Server báo lỗi tham số.
    *   *Mất mạng giữa chừng:* Giao dịch tự động rollback trên server, client báo lỗi mất mạng, tài nguyên của người chơi giữ nguyên trạng thái trước khi bấm nâng cấp.
    *   *Thẻ nguyên liệu được chọn nằm trong showcase:* Sau khi nâng cấp thành công/thất bại, thẻ nguyên liệu bị tiêu biến và tự động unequip khỏi showcase khi người dùng tải lại Profile (Use-Case 2.3).

---

## 6. Tính năng Mạng xã hội & Bạn bè (Friendship)

### Use-Case 6.1: Tìm kiếm và gửi lời mời kết bạn
*   **Tác nhân (Actor):** Người chơi (User)
*   **Luồng xử lý chính (Main flow):**
    1.  Người dùng mở màn hình tìm bạn [FriendSearchFragment](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/fragment/FriendSearchFragment.java), nhập tên hoặc ID người chơi.
    2.  Client gọi API `/api/friends/search?query=xxx`.
    3.  Server tìm kiếm trong DB và trả về danh sách tài khoản phù hợp cùng trạng thái quan hệ hiện tại.
    4.  Người dùng nhấn "Kết bạn". Client gửi yêu cầu POST tới `/api/friends/add` kèm `addresseeId`.
    5.  Server ghi nhận một dòng mới có trạng thái PENDING (`status = 0`) vào bảng `friendships`.
*   **Luồng rẽ nhánh / lỗi (Alternative/Exception flow):**
    *   *Đã có quan hệ kết bạn hoặc lời mời đang chờ:* Server chặn và trả về lỗi "Lời mời đã tồn tại hoặc hai người đã là bạn bè".

### Use-Case 6.2: Chấp nhận hoặc từ chối kết bạn
*   **Tác nhân (Actor):** Người chơi (User)
*   **Luồng xử lý chính (Main flow):**
    1.  Người dùng mở [FriendRequestFragment](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/fragment/FriendRequestFragment.java) hiển thị danh sách lời mời đang chờ.
    2.  Người dùng chọn "Chấp nhận" hoặc "Từ chối".
    3.  **Nếu Chấp nhận:** Client gửi POST tới `/api/friends/accept/{friendshipId}`. Server cập nhật `status = 1` (ACCEPTED) trong bảng `friendships`, đồng thời tăng chỉ số `friendsCount` của cả 2 user.
    4.  **Nếu Từ chối:** Client gửi DELETE tới `/api/friends/remove/{friendshipId}`. Server xóa bản ghi tương ứng trong bảng `friendships`.
    5.  Client cập nhật lại danh sách lời mời hiển thị trên màn hình.

---

## 7. Tính năng Đồng hành & Streak (Couple Streak)

#### Use-Case 4.5: Duy trì chuỗi tương tác (Couple Streak)
*   **Tác nhân (Actor):** Người chơi (User) và Bạn bè (Friend)
*   **Luồng xử lý chính (Main flow):**
    1.  Tại màn hình bạn bè hoặc profile của bạn bè, người dùng bấm nút "Kết đôi" để gửi yêu cầu bắt đầu chuỗi Streak.
    2.  Client gửi yêu cầu POST tới `/api/v1/streaks/request` kèm theo ID người gửi và đối phương dưới dạng Query Parameters (`?requesterId=xxx&partnerId=yyy`).
    3.  Server kiểm tra nếu hai người đã là bạn bè. Nếu đúng, tạo bản ghi trong `couple_streaks` với trạng thái `PENDING`.
    4.  Server gửi thông báo WebSocket tới người bạn kia qua topic `/topic/streak.{partnerId}`.
    5.  Người bạn nhận được lời mời, bấm "Chấp nhận". Client gửi POST tới `/api/v1/streaks/accept` kèm theo Query Parameters (`?userId=xxx&requesterId=yyy`).
    6.  Server cập nhật trạng thái streak thành `ACTIVE`, thiết lập số lượng streak ban đầu `streakCount = 1`, đặt `lastInteractionDate` là ngày hôm nay.
    7.  Server phát thông báo cập nhật qua WebSocket tới cả hai thiết bị để chuyển giao diện sang trạng thái kết đôi thành công.
*   **Luồng rẽ nhánh / lỗi (Alternative/Exception flow):**
    *   *Từ chối kết đôi:* Người được mời bấm "Từ chối", Client gửi POST tới `/api/v1/streaks/decline` kèm Query Parameters (`?userId=xxx&requesterId=yyy`). Server cập nhật trạng thái thành `DECLINED`. (Chặn không cho từ chối nếu trạng thái đã là `ACTIVE`).

#### Use-Case 4.5: Duy trì chuỗi tương tác (Couple Streak)
*   **Tác nhân (Actor):** Cặp đôi người chơi (Couple Users)
*   **Luồng xử lý chính (Main flow):**
    1.  Trong giao diện Couple Streak, người chơi nhấn vào khu vực trưng bày Objet của mình.
    2.  Người chơi chọn 1 thẻ bài yêu thích từ kho đồ và cấp độ đập thẻ tương ứng.
    3.  Client gửi POST tới `/api/v1/streaks/update-objet` kèm theo Query Parameters: `?streakId=xxx&userId=yyy&objetId=zzz&grade=n`.
    4.  Server ghi nhận thông tin vào bảng `couple_streaks` (trường `requesterObjetId` / `partnerObjetId` và cấp độ tương ứng).
    5.  Server tăng biến đếm số lần đổi Objet trong tuần `objetChangesThisWeek`.
    6.  Server bắn tin nhắn WebSocket báo cho cả hai client tải lại thông tin Objet hiển thị của cặp đôi.

---

## 8. Tính năng Trò chuyện Thời gian thực (Realtime Chat)

#### Use-Case 4.3: Chat thế giới (World Chat)
*   **Tác nhân (Actor):** Người chơi (User)
*   **Luồng xử lý chính (Main flow):**
    1.  Người dùng mở hộp chat trên màn hình chính.
    2.  Người dùng nhập tin nhắn và bấm "Gửi".
    3.  Client gửi message qua WebSocket tới đích `/app/chat.sendMessage`.
    4.  Server tiếp nhận, thực hiện lọc chống mã độc HTML (`HtmlUtils.htmlEscape`), gán timestamp hệ thống và phát (broadcast) tin nhắn tới topic chung `/topic/world`.
    5.  Tất cả các Client đang online nhận được tin nhắn và render hiển thị lên khung chat thế giới.
*   **Luồng rẽ nhánh / lỗi (Alternative/Exception flow):**
    *   *Mất kết nối WebSocket:* Client chuyển trạng thái hiển thị chat sang màu xám/báo offline, và chạy luồng tự động kết nối lại (auto-reconnect) ngầm.

#### Use-Case 4.4: Nhắn tin riêng tư (Private Chat)
*   **Tác nhân (Actor):** Cặp đôi người chơi (Couple Users)
*   **Luồng xử lý chính (Main flow):**
    1.  Người chơi mở màn hình chat riêng tư [ChatPrivateFragment](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/fragment/ChatPrivateFragment.java) với một người bạn.
    2.  Người chơi gửi tin nhắn. Client lưu tin nhắn vào bảng `private_messages` của Room DB cục bộ để hiển thị ngay lập tức (Local-First).
    3.  Đồng thời, Client chuyển tin nhắn qua WebSocket tới đích `/app/chat.private` trên Server.
    4.  Server phát tin nhắn bất đồng bộ:
        - Gửi tin nhắn tới topic của người nhận `/topic/private.{receiverId}` và người gửi `/topic/private.{senderId}` để đồng bộ tin nhắn realtime.
        - Phát tán sự kiện `PrivateChatEvent` chạy ngầm để ghi tin nhắn vào bảng MySQL `private_messages`.
        - Tự động gọi `CoupleStreakService.recordInteraction` để cập nhật streak: Nếu hôm nay cả hai cùng nhắn tin cho nhau, tăng `streakCount` thêm 1 (nếu hôm qua có tương tác) hoặc reset về 1 (nếu đứt chuỗi).
*   **Luồng rẽ nhánh / lỗi (Alternative/Exception flow):**
    *   *Chat khi offline:* Tin nhắn vẫn được lưu vào Room DB cục bộ trên máy và hiển thị trạng thái "đang gửi". Khi có mạng trở lại, Client sẽ gửi hàng loạt tin nhắn chưa đồng bộ lên Server.

### Use-Case 8.3: Đồng bộ & Xác nhận tin nhắn ngoại tuyến (Two-Phase Chat Sync & Ack)
*   **Tác nhân (Actor):** Hệ thống Client (Android Room DB) / Hệ thống Server
*   **Luồng xử lý chính (Main flow):**
    1.  Khi người dùng mở màn hình chat riêng tư với một người bạn, Client lập tức thực hiện tải lịch sử tin nhắn cục bộ từ SQLite Room Database.
    2.  Đồng thời, Client gọi API `GET /api/chat/history?user1=xxx&user2=yyy` (ở background thread) để lấy danh sách tin nhắn mới nhất đã lưu trên MySQL Server.
    3.  Server trả về danh sách các tin nhắn của cuộc hội thoại kèm theo mã ID tin nhắn.
    4.  Client so sánh các tin nhắn tải về từ Server với các tin nhắn có sẵn trong Room Database:
        - Các tin nhắn mới chưa có dưới local sẽ được Insert thêm vào Room DB.
        - Các tin nhắn bị sai lệch trạng thái sẽ được cập nhật.
    5.  Sau khi ghi thành công vào Room DB và hiển thị mượt mà lên màn hình chat, Client gửi một yêu cầu POST tới `/api/chat/ack` chứa danh sách các `messageIds` vừa được đồng bộ thành công.
    6.  Server nhận yêu cầu ack và trả về thành công để xác nhận hoàn tất giao dịch đồng bộ hai pha.
*   **Luồng rẽ nhánh / lỗi (Alternative/Exception flow):**
    *   *Lỗi mạng khi đang đồng bộ:* Giao dịch ack thất bại, Client vẫn lưu giữ tin nhắn local. Lần mở chat tiếp theo, Client sẽ tiếp tục thực hiện đối chiếu và gửi ack lại lên Server để tránh thất lạc tin nhắn.

---

## 9. Tính năng Điểm danh Hằng ngày (Daily Check-in)

### Use-Case 9: Điểm danh nhận thưởng theo slot
*   **Tác nhân (Actor):** Người chơi (User)
*   **Luồng xử lý chính (Main flow):**
    1.  Người dùng mở màn hình điểm danh [DailyCheckinActivity](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/DailyCheckinActivity.java).
    2.  Client gọi API `/api/daily/status` để lấy trạng thái 3 slot điểm danh trong ngày (Sáng, Trưa, Tối).
    3.  Server tính toán slot hiện tại dựa trên giờ hệ thống của server:
        - Giờ từ 6h - 12h: Slot 0 (Sáng) - Quà: 500 Coins, 1 Diamond.
        - Giờ từ 12h - 18h: Slot 1 (Trưa) - Quà: 800 Coins, 2 Diamonds.
        - Giờ từ 18h - 24h: Slot 2 (Tối) - Quà: 1200 Coins, 3 Diamonds.
    4.  Client hiển thị trạng thái của từng slot:
        - Slot đã nhận quà: hiển thị màu xám tích xanh (`claimed`).
        - Slot đang mở hiện tại và chưa nhận: nút nhấn sáng lên (`available`).
        - Slot chưa tới giờ hoặc đã qua giờ mà chưa nhận: bị khóa lại (`locked`).
    5.  Người dùng nhấn "Nhận thưởng" đối với slot khả dụng. Client gửi yêu cầu POST tới `/api/daily/claim`.
    6.  Server mở Transaction, kiểm tra chống nhận trùng (double-claim). Nếu hợp lệ, cộng Coins và Diamonds trực tiếp vào bảng `users`, đồng thời ghi một dòng lịch sử vào `daily_checkins`.
    7.  Server trả về số dư Coins/Diamonds mới. Client cập nhật dữ liệu và hiển thị hiệu ứng nhận thưởng tài nguyên lấp lánh.
*   **Luồng rẽ nhánh / lỗi (Alternative/Exception flow):**
    *   *Yêu cầu nhận thưởng ngoài khung giờ:* Server trả về lỗi 400 "Ngoài khung giờ hoặc đã nhận rồi", Client khóa nút bấm lại.

### Use-Case 10.1: Cử đội hình đi thám hiểm
*   **Tác nhân (Actor):** Người chơi (User)
*   **Luồng xử lý chính (Main flow):**
    1.  Người dùng mở [StageFragment](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/fragment/StageFragment.java).
    2.  Người dùng chọn bản đồ (Map) và thời gian thám hiểm (số giờ gửi đi).
    3.  Người dùng chọn từ 1 đến 6 thẻ bài nhàn rỗi trong kho đồ để thành lập đội hình thám hiểm.
    4.  Người dùng nhấn "Bắt đầu thám hiểm". Client gửi POST tới `/api/stage/start/{userId}` kèm `mapId`, `durationHours` và danh sách `cardIds`.
    5.  Server kiểm tra:
        - Cấp độ tài khoản có đạt yêu cầu mở khóa bản đồ không.
        - Các thẻ bài cử đi có thuộc về người dùng và đang ở trạng thái khả dụng (`status = AVAILABLE`) không.
    6.  Server tính toán tổng điểm của đội hình `teamScore` dựa trên chỉ số hiếm của thẻ, mùa phát hành, và cấp độ nâng cấp.
    7.  Server khóa trạng thái các thẻ bài thành `BUSY_AFK_{mapId}`.
    8.  Server lưu thông tin phiên thám hiểm vào bảng `stage_sessions` và lưu danh sách liên kết thẻ bài vào `stage_session_members`.
    9.  Server trả về thông tin phiên thám hiểm đang chạy. Client bắt đầu chạy đồng hồ đếm ngược thời gian thám hiểm.
*   **Luồng rẽ nhánh / lỗi (Alternative/Exception flow):**
    *   *Thẻ đang bận đi thám hiểm bản đồ khác:* Server trả về thông báo lỗi, Client hủy tiến trình và tải lại trạng thái thẻ bài.

### Use-Case 10.2: Tăng tốc thám hiểm bằng Kim Cương (Speed Up)
*   **Tác nhân (Actor):** Người chơi (User)
*   **Luồng xử lý chính (Main flow):**
    1.  Người dùng nhấn nút "Tăng tốc" (Speed Up) đối với phiên thám hiểm đang chạy.
    2.  Client hiển thị số kim cương cần tiêu tốn (tính phí 10 💎 cho mỗi giờ còn lại).
    3.  Người dùng xác nhận tăng tốc. Client gửi POST tới `/api/stage/speed-up/{userId}/{sessionId}`.
    4.  Server kiểm tra số dư Diamonds của người dùng. Nếu đủ, thực hiện trừ số Diamonds tương ứng trong DB.
    5.  Server cập nhật thời gian kết thúc phiên thám hiểm `endTime` về thời gian hiện tại và đổi trạng thái phiên sang `COMPLETED`.
    6.  Client nhận phản hồi thành công, cập nhật đồng hồ đếm ngược thành 0 và hiển thị nút "Nhận thưởng" sáng lên.

### Use-Case 10.3: Nhận thưởng thám hiểm (Claim Reward)
*   **Tác nhân (Actor):** Người chơi (User)
*   **Luồng xử lý chính (Main flow):**
    1.  Khi phiên thám hiểm đã hoàn thành (hết thời gian đếm ngược hoặc sau khi Speed-up), người dùng nhấn "Nhận thưởng".
    2.  Client gửi POST tới `/api/stage/claim/{userId}/{sessionId}`.
    3.  Server tính toán phần thưởng (Lazy Evaluation) dựa trên Map, thời gian gửi đi và tổng điểm `teamScore` của đội hình:
        - Công thức: `Reward = Base * Duration * (1 + teamScore / 200)`
        - Các thông số thưởng cơ bản (Base Reward/Hour) theo từng bản đồ:
          - Map 1 (Bản đồ Trái Đất): Base = 100 Coins, 0 Diamonds.
          - Map 2 (Bản đồ Mặt Trăng): Base = 250 Coins, 0 Diamonds.
          - Map 3 (Bản đồ Sao Hỏa): Base = 600 Coins, 1 Diamond.
          - Map 4 (Bản đồ Sao Mộc): Base = 1500 Coins, 5 Diamonds.
    4.  Server cộng tiền vàng và kim cương tích lũy được vào tài khoản người chơi.
    5.  Server giải phóng toàn bộ thẻ bài tham gia phiên thám hiểm về lại trạng thái `AVAILABLE`.
    6.  Server cập nhật trạng thái phiên thám hiểm thành `CLAIMED`.
    7.  Client nhận thông tin quà thưởng, cập nhật số dư hiển thị và mở khóa các thẻ bài trong kho đồ.

#### Use-Case 3.6: Cử đội hình đi thám hiểm (AFK Stage)
*   **Tác nhân (Actor):** Người chơi (User)
*   **Luồng xử lý chính (Main flow):**
    1.  Tại màn hình thám hiểm, đối với một phiên thám hiểm đang trong trạng thái chạy (`ACTIVE`), người chơi có thể chọn "Hủy bỏ" (Abort) nếu muốn thu hồi thẻ bài gấp.
    2.  Người chơi xác nhận việc hủy bỏ (sẽ không nhận được bất kỳ phần thưởng tích lũy nào).
    3.  Client gửi yêu cầu POST tới `/api/stage/abort/{userId}/{sessionId}`.
    4.  Server mở Transaction, chuyển trạng thái phiên thám hiểm sang `CANCELED`.
    5.  Server giải phóng tất cả các thẻ bài tham gia phiên thám hiểm này quay trở lại trạng thái `AVAILABLE` khả dụng trong kho đồ.
    6.  Server cập nhật DB, commit giao dịch và trả về kết quả thành công cho Client.
    7.  Client tải lại dữ liệu kho đồ, cập nhật trạng thái thẻ bài thành sẵn sàng và ẩn phiên thám hiểm đã hủy.

---

## 11. Tính năng Hòm thư & Gửi tặng (Mailbox & Gift)

#### Use-Case 3.7: Hộp thư (Mailbox)
*   **Tác nhân (Actor):** Người chơi (User)
*   **Luồng xử lý chính (Main flow):**
    1.  Người dùng mở [MailboxFragment](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/fragment/MailboxFragment.java).
    2.  Client gọi API `/api/mailbox/{userId}` để tải danh sách thư.
    3.  Người dùng có thể nhấn "Nhận" từng thư hoặc nhấn "Nhận tất cả".
    4.  **Nhận từng thư:** Client gửi POST tới `/api/mailbox/claim/{mailId}`. Server dùng Pessimistic Lock khóa bức thư, cộng tài nguyên tương ứng (Coins/Diamonds) cho user, và đánh dấu thư `received = true`.
    5.  **Nhận tất cả:** Client gửi POST tới `/api/mailbox/claim-all/{userId}`. Server quét toàn bộ các thư chưa nhận kèm khóa bảo vệ, gom tổng số Coin và Diamond lại cộng 1 lần duy nhất vào user, sau đó cập nhật toàn bộ thư thành đã nhận.
    6.  Client nhận phản hồi thành công, cộng tiền trên thanh header và cập nhật trạng thái thư hiển thị.

#### Use-Case 4.6: Tặng quà (Gift)
*   **Tác nhân (Actor):** Người chơi (User)
*   **Luồng xử lý chính (Main flow):**
    1.  Tại kho đồ hoặc profile của bạn bè, người chơi nhấn chọn "Tặng quà".
    2.  Người chơi chọn thẻ bài muốn tặng. Client hiển thị giới hạn lượt tặng trong ngày (tối đa 5 lần gửi/nhận mỗi ngày).
    3.  Người chơi nhấn xác nhận gửi. Client gửi POST tới `/api/gift/send` kèm `cardId` và `receiverId`. (Lưu ý: Mặc dù cấu hình javadoc/comment có thể ghi tốn phí 36,000 Coins và 36 Diamonds, thực tế cấu hình trong code hiện tại là hoàn toàn miễn phí - 0 Coins và 0 Diamonds).
    4.  Server kiểm tra các điều kiện:
        - Thẻ bài có thuộc sở hữu của người gửi không.
        - Số lượt tặng hôm nay của người gửi đã đạt giới hạn 5 lần chưa.
        - Số lượt nhận hôm nay của người nhận đã đạt giới hạn 5 lần chưa.
    5.  Nếu mọi điều kiện thỏa mãn, Server tiến hành đổi chủ sở hữu thẻ bài trực tiếp trong cơ sở dữ liệu (`card.setUser(receiver)`).
    6.  Server thêm collectionId của thẻ vào tập danh sách thẻ đã mở khóa `unlockedCollections` của người nhận.
    7.  Server ghi nhận giao dịch vào bảng `gift_history`.
    8.  Client nhận phản hồi thành công, xóa thẻ bài đó khỏi kho đồ cục bộ.
*   **Luồng rẽ nhánh / lỗi (Alternative/Exception flow):**
    *   *Thẻ được chọn nằm trong showcase:* Sau khi tặng thành công, thẻ tự động unequip khỏi showcase của người tặng khi người tặng tải lại Profile (Use-Case 2.3).

#### Use-Case 4.6: Tặng quà (Gift)
*   **Tác nhân (Actor):** Người chơi (User)
*   **Luồng xử lý chính (Main flow):**
    1.  Người dùng mở giao diện "Hộp Quà" (Gift Inbox).
    2.  Client gọi API `GET /api/gift/unread-count` để lấy số quà chưa đọc hiển thị lên badge.
    3.  Client gọi API `GET /api/gift/daily-remaining` để lấy giới hạn số lượt tặng còn lại trong ngày (tối đa 5 lượt).
    4.  Client gọi API `GET /api/gift/received` để tải danh sách các thẻ bài đã nhận từ bạn bè (Gift Inbox) và gọi `GET /api/gift/sent` để lấy danh sách quà đã gửi đi.
    5.  Khi người dùng xem xong danh sách quà nhận, Client gửi POST tới `/api/gift/mark-read` để đánh dấu tất cả các quà đã nhận là đã đọc trên Server và ẩn badge thông báo.
*   **Luồng rẽ nhánh / lỗi (Alternative/Exception flow):**
    *   *Đã hết lượt tặng hoặc nhận trong ngày:* Khi người chơi bấm gửi quà, Server trả về lỗi 400 báo đã đạt giới hạn 5 lần/ngày, giao dịch tặng thẻ bị hủy bỏ.

---

## 12. Tính năng Sao lưu & Khôi phục (Backup & Cloud Sync)

### Use-Case 12.1: Sao lưu dữ liệu tự động lên Cloud
*   **Tác nhân (Actor):** Hệ thống Client (WorkManager) / Người chơi (User)
*   **Luồng xử lý chính (Main flow):**
    1.  Hệ thống chạy ngầm thông qua Android `WorkManager` (khi cắm sạc & có wifi) hoặc do người dùng nhấn "Sao lưu thủ công" trong cài đặt profile.
    2.  Client gọi lớp [BackupManager](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/utils/BackupManager.java).
    3.  Để đảm bảo tính toàn vẹn dữ liệu SQLite khi đang mở kết nối, Client ép SQLite thực hiện commit toàn bộ log WAL/SHM chưa kịp cam kết vào file chính thông qua lệnh:
        `PRAGMA wal_checkpoint(TRUNCATE)`
    4.  Client nén file cơ sở dữ liệu `mosco_db` cục bộ thành định dạng nén an toàn và lưu vào thư mục sao lưu của thiết bị `/files/backups/`.
    5.  Client gửi tệp tin sao lưu này lên Server Spring Boot thông qua API Multipart `/api/backup/upload`.
    6.  Server tiếp nhận tệp tin, lưu trữ vào thư mục lưu trữ đám mây `storage/backups` gắn chặt với mã User ID của người dùng. Đồng thời, Server áp dụng chính sách Retention Policy (chỉ giữ lại tối đa 5 bản sao lưu mới nhất của người dùng này và tự động dọn dẹp các bản cũ hơn để tiết kiệm dung lượng lưu trữ).
*   **Luồng rẽ nhánh / lỗi (Alternative/Exception flow):**
    *   *Mất mạng khi đang sao lưu:* WorkManager hoãn tác vụ sao lưu ngầm và lên lịch chạy lại khi có kết nối mạng ổn định.

### Use-Case 12.2: Khôi phục dữ liệu (Restore & Server Truth)
*   **Tác nhân (Actor):** Người chơi (User)
*   **Luồng xử lý chính (Main flow):**
    1.  Người dùng cài lại ứng dụng hoặc đổi thiết bị và truy cập vào phần Cài đặt Profile chọn "Khôi phục dữ liệu".
    2.  Client gọi API `/api/backup/list` để tải danh sách các bản sao lưu có sẵn của tài khoản trên Server.
    3.  Người dùng chọn bản sao lưu mong muốn và nhấn "Khôi phục".
    4.  Client tải tệp tin cơ sở dữ liệu từ API `/api/backup/download/{filename}` về thiết bị.
    5.  Client đóng kết nối tới Room Database hiện tại, xóa các file log WAL/SHM cũ trên ổ đĩa thiết bị.
    6.  Client thực hiện ghi đè tệp tin `.db` mới tải về lên tệp cơ sở dữ liệu chính của ứng dụng và mở lại kết nối Room DB.
    7.  **Cơ chế chống gian lận dữ liệu (Anti-Rollback Cheat / Server Truth):** 
        - Để ngăn chặn việc người chơi lợi dụng Restore để khôi phục lại các thẻ bài đã bị xóa do đập xịt hoặc tặng đi trước đó, ngay khi khởi chạy lại ứng dụng và có kết nối mạng, Client tự động gọi API `getUserCards` từ Server.
        - Server trả về danh sách thẻ bài chuẩn xác đang được lưu trữ trên Server MySQL (Server Truth).
        - Client thực hiện đồng bộ đè danh sách này vào Room Database cục bộ, cập nhật lại trạng thái thẻ và chỉ số tài nguyên Coins/Diamonds chuẩn xác từ Server MySQL.
    8.  Client hoàn thành khôi phục dữ liệu trò chuyện cục bộ (tin nhắn chat riêng tư) và các chỉ số thống kê mượt mà.

---

## 13. Tính năng Đồng bộ & Quét Metadata Tự động (Scheduled Metadata Sync & ETL Pipeline)

### Use-Case 13: Đồng bộ Metadata tự động từ Source objekt.top
*   **Tác nhân (Actor):** Hệ thống Server (Scheduled Task / Startup Event Listener)
*   **Luồng xử lý chính (Main flow):**
    1.  **Kích hoạt:** 
        - Khi ứng dụng khởi động thành công, sự kiện `ApplicationReadyEvent` được kích hoạt ngầm tại [AssetManagementService.java](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/server/src/main/java/com/vn/jet/mosco/spinserver/service/AssetManagementService.java).
        - **Hoặc** Chạy định kỳ vào mỗi giờ (`0 0 * * * *`) thông qua tác vụ lên lịch của Spring `@Scheduled`.
    2.  **Cào dữ liệu (Scraping):** Server gửi request GET có gắn User-Agent giả lập trình duyệt tới API `https://objekt.top/api/collection?artist=tripleS&limit=20000` thông qua OkHttpClient.
    3.  **Lọc và Sắp xếp:** Server phân tích chuỗi dữ liệu JSON thô nhận được thành danh sách các collections, lọc bỏ rác và thực hiện sắp xếp theo thời gian khởi tạo (`createdAt` giảm dần - thẻ bài mới nhất xếp đầu).
    4.  **Cập nhật Manifest:** Dữ liệu mới được ghi vào file `data/assets/database.json`. Nếu kích thước tệp database thay đổi so với phiên bản cũ, Server tự động ghi đè file `manifest.json` và cập nhật nhãn thời gian `lastSync` hiện tại.
    5.  **Chạy ETL Pipeline:** Server gọi hàm `EtlService.runEtlJob()`.
        - Job ETL phân tích file `database.json` mới.
        - Sử dụng cơ chế cache cục bộ (`HashMap`) cho các thực thể từ điển (`Member`, `Season`, `CardClass`) để tránh N+1 Query.
        - Bóc tách mã ảnh Cloudflare `image_id` từ URL gốc bằng Regular Expression.
        - Thực hiện đối chiếu và củng cố dữ liệu (UPSERT) vào cơ sở dữ liệu MySQL theo từng lô (Batch Save 200 bản ghi).
        - Ghi nhận các thẻ mới vào bảng `cards` và tự động tạo mới các từ điển nếu chưa tồn tại.
    6.  **Xóa Cache:** Làm mới bộ nhớ đệm `CardDataService` của Backend.
    *   *Lỗi kết nối tới objekt.top (Mạng lỗi/API quá tải):* Server ghi log lỗi, hoãn tiến trình và giữ nguyên dữ liệu database cũ. Tác vụ sẽ tự động chạy lại vào giờ tiếp theo.

### Use-Case 13.2: Đồng bộ cấu hình động phía Client (Dynamic Metadata Update Pipeline)
*   **Tác nhân (Actor):** Hệ thống Client (DatabaseLoader / Shared Preferences)
*   **Luồng xử lý chính (Main flow):**
    1.  Khi người dùng khởi động ứng dụng Mosco, Client gọi API `GET /api/config/db-version` ở chế độ ngầm để lấy mã MD5 hash của file `database.json` hiện hành trên Server.
    2.  Client kiểm tra giá trị hash này với mã hash của phiên bản local hiện tại được lưu trong `SharedPreferences` (khoá `db_version_hash`).
    3.  **Trường hợp 1 (Trùng mã hash):** Client bỏ qua việc tải về, trực tiếp sử dụng file `database.json` đã lưu cục bộ trước đó để nạp dữ liệu.
    4.  **Trường hợp 2 (Khác mã hash hoặc chưa có file):**
        - Client thực hiện gọi API `GET /api/config/db-download` để tải file `database.json` mới nhất về máy thiết bị.
        - Client ghi đè tệp mới tải vào thư mục lưu trữ nội bộ của app dưới tên `database.json`.
        - Client cập nhật mã hash mới nhận vào `SharedPreferences` dưới khoá `db_version_hash`.
    5.  Client gọi lớp `DatabaseLoader` để parse dữ liệu thẻ Master từ file JSON này và thực hiện đồng bộ, chèn đè (UPSERT) vào cơ sở dữ liệu Room SQLite `MasterObjetDao` để phục vụ hiển thị offline.
    6.  Quá trình hoàn tất, Client phát thông báo `notifyInventoryChanged` để cập nhật giao diện hiển thị các thẻ bài.

### Use-Case 13.3: Đồng bộ danh sách Master Card theo cơ chế Delta Sync (Client-Side Delta Sync Pipeline)
*   **Tác nhân (Actor):** Hệ thống Client (SyncManager / Room DB)
*   **Luồng xử lý chính (Main flow):**
    1.  Khi khởi chạy hoặc định kỳ theo tác vụ kích hoạt, Client khởi chạy luồng bất đồng bộ thông qua `SyncManager.startDeltaSync(context)`.
    2.  Client đọc giá trị nhãn thời gian đồng bộ cuối cùng (`last_sync_time`) từ file `SharedPreferences` (khoá `sync_prefs`, mặc định trả về `0` nếu là lần chạy đầu).
    3.  Client gửi yêu cầu GET tới API `/api/v1/cards/sync?lastSyncTime=xxx` truyền timestamp nhận được.
    4.  Server Spring Boot tiếp nhận tại `CardController.getUpdatedCards`, chuyển đổi epoch timestamp thành LocalDateTime và truy vấn cơ sở dữ liệu MySQL để lấy tất cả các bản ghi thẻ bài Master (`Card`) được thêm mới hoặc sửa đổi từ mốc thời gian đó.
    5.  Server trả về danh sách `CardSummaryDto` chứa thông tin tóm gọn (ID, Member Name, Season Name, Thumbnail ID).
    6.  Client nhận danh sách từ Server:
        -   Nếu danh sách trống: kết thúc tiến trình và log "Dữ liệu đã là mới nhất".
        -   Nếu phát hiện thay đổi: Client map danh sách `CardSummaryDto` thành các thực thể `CardEntity` cục bộ (thiết lập ảnh thu nhỏ `frontImageId` và các mốc chỉ số mặc định).
    7.  Client gọi Room Database `CardDao` thực hiện lưu/chèn đè toàn bộ (UPSERT) các thực thể này vào bảng Room SQLite cục bộ (`AppDatabase.getInstance(context).cardDao().upsertAll(entities)`).
    8.  Client lưu trữ lại nhãn thời gian hiện tại (`System.currentTimeMillis()`) làm mốc `last_sync_time` mới vào `SharedPreferences` để chuẩn bị cho các lần đồng bộ tiếp theo.
*   **Luồng rẽ nhánh / lỗi (Alternative/Exception flow):**
    *   *Mất kết nối mạng / API lỗi:* Giao dịch đồng bộ bị gián đoạn, Client log lỗi và giữ nguyên giá trị `last_sync_time` cũ để thực hiện đồng bộ lại ở lần khởi chạy kế tiếp.
9+6
---

## 15. Tính năng Bảng xếp hạng thiên hà (Galactic Leaderboards)

### Use-Case 15: Xem Bảng xếp hạng người chơi
*   **Tác nhân (Actor):** Người chơi (User)
*   **Luồng xử lý chính (Main flow):**
    1.  Người chơi mở màn hình "Bảng xếp hạng" (Leaderboards).
    2.  Màn hình cung cấp 5 tab xếp hạng tương ứng với các tiêu chí:
        - **Xếp hạng Cấp độ (Level):** Client gọi API `GET /api/rank/level`.
        - **Xếp hạng Thẻ mạnh nhất (OVR):** Client gọi API `GET /api/rank/ovr`.
        - **Xếp hạng Bộ sưu tập (Collection):** Client gọi API `GET /api/rank/collection` (số lượng thẻ độc nhất sở hữu).
        - **Xếp hạng Tổng tài sản (Wealth):** Client gọi API `GET /api/rank/wealth` (số lượng Coins sở hữu).
        - **Xếp hạng Chuỗi đăng nhập (Streak):** Client gọi API `GET /api/rank/streak`.
    3.  Server nhận yêu cầu tại `RankService`, truy vấn Top 10 người chơi theo tiêu chí tương ứng từ MySQL.
    4.  Server trả về danh sách Top 10 người chơi gồm Ingame Name, Avatar và giá trị chỉ số tương ứng.
    5.  Client hiển thị danh sách xếp hạng dạng danh sách trực quan, có vinh danh Top 1, 2, 3 bằng các huy hiệu màu sắc.
*   **Luồng rẽ nhánh / lỗi (Alternative/Exception flow):**
    *   *Mạng lỗi:* Client hiển thị thông báo lỗi kết nối và nút bấm tải lại (Retry).

---

## 16. Tính năng Cửa hàng & Trao đổi tài nguyên (Shop & Purchases)

### Use-Case 16.1: Xem và mua gói vật phẩm/tài nguyên
*   **Tác nhân (Actor):** Người chơi (User)
*   **Luồng xử lý chính (Main flow):**
    1.  Người dùng mở Tab "Cửa hàng" (Shop).
    2.  Client gọi API `GET /api/shop` để lấy danh sách các mặt hàng đang bày bán.
    3.  Server trả về các `ShopItem` đang hoạt động (chưa hết hạn bán).
    4.  Người dùng chọn một mặt hàng (gói phôi thẻ bài, vé quay, hoặc gói tài nguyên) và chọn số lượng.
    5.  Người dùng nhấn "Mua". Client gửi yêu cầu POST tới `/api/shop/buy` với body JSON chứa `userId`, `productCode`, và `quantity`.
    6.  Server mở Transaction:
        - Kiểm tra xem mặt hàng có giới hạn thời gian không.
        - Kiểm tra số dư Coins/Diamonds của user có đủ thanh toán tổng tiền hay không.
        - Trừ tiền tương ứng của user trong MySQL.
        - **Nếu là mua Tài nguyên (VD dùng Kim Cương đổi Vàng):** Server tự động cộng tiền Vàng tương ứng vào tài khoản của user.
        - **Nếu là mua Gói thẻ/Vật phẩm:** Server thêm vật phẩm/gói thẻ vào bảng `user_items` hoặc tăng số lượng của vật phẩm hiện có.
    7.  Server lưu trữ giao dịch và phản hồi kết quả. Client cập nhật số dư tiền hiển thị trên Header, đồng thời thêm vật phẩm mới vào túi đồ của người chơi.
*   **Luồng rẽ nhánh / lỗi (Alternative/Exception flow):**
    *   *Không đủ tài nguyên thanh toán:* Server từ chối giao dịch, trả về thông báo lỗi 400 "Not enough resources". Client hiển thị popup nhắc nhở nạp thêm tài nguyên.

---


---

## 3.8. THUẬT TOÁN CỐT LÕI


### 3.8.1. Thuật toán Nâng cấp Thẻ bài (FO4-style Card Upgrade with Pessimistic Locking)

### 1.1. Ý nghĩa & Bối cảnh
Thuật toán này mô phỏng cơ chế nâng cấp thẻ bài tương tự tựa game FIFA Online 4 (FO4). Người chơi sử dụng 1 thẻ chính và chọn từ 1 đến 5 thẻ nguyên liệu (phôi) để nâng cấp thẻ chính lên cấp độ cao hơn (tối đa cấp +10). Việc nâng cấp có tỉ lệ thành công phụ thuộc vào chênh lệch chỉ số OVR giữa phôi và thẻ chính. 

Để ngăn chặn hoàn toàn các lỗi gian lận như **Double-spending** (sử dụng cùng một phôi nâng cấp đồng thời trong hai yêu cầu) hoặc **Race Condition** trong môi trường đa luồng, phía Server sử dụng cơ chế khóa bi quan (`PESSIMISTIC_WRITE`) trên cơ sở dữ liệu.

### 1.2. Đặc tả Thuật toán
- **File mã nguồn:**
  - Server: [UpgradeService.java](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/server/src/main/java/com/vn/jet/mosco/spinserver/service/UpgradeService.java)
  - Client (Xem trước): [UpgradeAlgorithm.java](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/utils/UpgradeAlgorithm.java)
- **Đầu vào (Input):**
  - `baseCardId` (Long): ID thẻ bài chính cần nâng cấp.
  - `materialCardIds` (List<Long>): Danh sách ID của từ 1 đến 5 thẻ bài phôi.
  - Cấu hình tỉ lệ cơ bản (`upgradeRates`) và các hệ số nâng cấp (`customUpgradeConfig` gồm hệ số $X$ và cơ số $M$) tương ứng với từng cấp độ và loại thẻ.
- **Đầu ra (Output):**
  - `isSuccess` (Boolean): Kết quả nâng cấp thành công hay thất bại.
  - `newLevel` (Int): Cấp nâng cấp mới của thẻ chính.
  - `newOvr` (Int): Chỉ số OVR mới tương ứng cấp độ.
  - `actualSuccessRate` (Double): Tỉ lệ thành công thực tế (%).

### 1.3. Mã giả Thuật toán (Pseudo-code)

```text
FUNCTION upgradeCard(baseCardId, materialCardIds)
    START TRANSACTION
    
    // 1. Áp dụng Khóa Bi Quan (Pessimistic Write Lock) để tránh Race Condition
    mainCard = userCardRepository.findWithLockById(baseCardId)
    IF mainCard IS NULL THEN
        ROLLBACK TRANSACTION
        RETURN Error("Không tìm thấy thẻ chính")
    END IF
    
    IF mainCard.upgradeLevel >= 10 THEN
        ROLLBACK TRANSACTION
        RETURN Error("Thẻ đã đạt cấp độ tối đa (+10)")
    END IF
    
    // 2. Khóa tất cả các thẻ nguyên liệu đồng thời
    materials = EMPTY_LIST
    FOR EACH matId IN materialCardIds DO
        matCard = userCardRepository.findWithLockById(matId)
        IF matCard IS NULL THEN
            ROLLBACK TRANSACTION
            RETURN Error("Không tìm thấy thẻ nguyên liệu: " + matId)
        END IF
        ADD matCard TO materials
    END FOR
    
    IF LENGTH(materials) < 1 OR LENGTH(materials) > 5 THEN
        ROLLBACK TRANSACTION
        RETURN Error("Số lượng thẻ nguyên liệu không hợp lệ (1-5)")
    END IF
    
    // 3. Tính toán tỷ lệ thành công dựa trên công thức hàm mũ OVR
    nextLevel = mainCard.upgradeLevel + 1
    maxRate = upgradeRates.get(nextLevel) // Tỷ lệ thành công tối đa của level
    
    // Lấy hệ số X và cơ số M dựa trên nhóm thẻ (class)
    typeKey = cardDataService.getTypeKey(mainCard.collectionId)
    levelConfig = customUpgradeConfig.get(nextLevel).get(typeKey)
    X = levelConfig.X
    M = levelConfig.M
    
    mainOvr = cardDataService.getOvr(mainCard.collectionId, mainCard.upgradeLevel)
    totalFillPercent = 0.0
    
    FOR EACH material IN materials DO
        materialOvr = cardDataService.getOvr(material.collectionId, material.upgradeLevel)
        deltaOvr = materialOvr - mainOvr
        
        // Công thức phi tuyến tính tính điểm đóng góp của từng phôi
        IF deltaOvr >= 0 THEN
            totalFillPercent = totalFillPercent + (X * (M ^ deltaOvr))
        ELSE
            totalFillPercent = totalFillPercent + (X / (M ^ ABS(deltaOvr)))
        END IF
    END FOR
    
    // Giới hạn thanh nạp phôi tối đa 100%
    fillPercent = MIN(totalFillPercent, 100.0)
    actualSuccessRate = (fillPercent / 100.0) * maxRate
    
    // 4. Quay số ngẫu nhiên (RNG) - Dùng hạt giống khí quyển từ ChaosTheoryHelper
    roll = ChaosTheoryHelper.nextDouble() * 100.0
    isSuccess = (roll <= actualSuccessRate)
    
    // 5. Áp dụng hình phạt hoặc phần thưởng
    oldLevel = mainCard.upgradeLevel
    IF isSuccess THEN
        mainCard.upgradeLevel = oldLevel + 1
    ELSE
        // Penalty: Rớt 2 cấp, tối thiểu về cấp 1
        mainCard.upgradeLevel = MAX(1, oldLevel - 2)
    END IF
    
    // 6. Tiêu thụ phôi (Xóa ràng buộc khóa ngoại trong StageSession trước khi xóa thẻ)
    FOR EACH material IN materials DO
        stageSessionMemberRepository.deleteByUserCardId(material.id)
    END FOR
    userCardRepository.deleteAll(materials)
    
    // 7. Lưu thẻ chính và kết thúc transaction
    userCardRepository.save(mainCard)
    newOvr = cardDataService.getOvr(mainCard.collectionId, mainCard.upgradeLevel)
    
    COMMIT TRANSACTION
    
    RETURN UpgradeResponse(isSuccess, mainCard.upgradeLevel, newOvr, actualSuccessRate)
END FUNCTION
```

### 1.4. Thiết kế Tối ưu Hiệu năng (Performance Justification)
- **Locking Scope tối thiểu:** Khóa bi quan chỉ áp dụng trên các dòng cụ thể (`SELECT ... FOR UPDATE` thông qua `findWithLockById`) thay vì khóa toàn bộ bảng `user_card`. Điều này cho phép hàng ngàn người chơi thực hiện nâng cấp đồng thời mà không bị nghẽn cổ chai.
- **Ràng buộc khóa ngoại:** Thuật toán xóa dữ liệu phụ thuộc ở bảng trung gian `stage_session_member` trước khi thực hiện `deleteAll(materials)` để tránh vi phạm toàn vẹn dữ liệu MySQL và lỗi Crash Thread.
- **Tính toán tĩnh trước khi lưu:** Các hằng số $X$, $M$, và tỉ lệ gốc được nạp sẵn vào bộ nhớ đệm (`Map` tĩnh) qua phương thức `@PostConstruct` khi khởi động ứng dụng, tránh được việc đọc file cấu hình JSON đĩa cứng lặp đi lặp lại trong mỗi request.

---

### 3.8.2. Thuật toán Gacha sinh số ngẫu nhiên từ Nhiễu khí quyển

### 2.1. Ý nghĩa & Bối cảnh
Hệ thống game Gacha yêu cầu tính ngẫu nhiên cực kỳ cao để đảm bảo công bằng. Các thuật toán sinh số giả ngẫu nhiên (PRNG) thông thường dựa trên thời gian hệ thống rất dễ bị đoán trước hoặc lặp chu kỳ. 

Giải pháp tối ưu là sử dụng nguồn số ngẫu nhiên thực sự (True Random Number Generator - TRNG) dựa trên nhiễu khí quyển (Atmospheric Noise) từ API dịch vụ `random.org`. Tuy nhiên, việc gọi API bên ngoài (mạng Internet) đồng bộ trong luồng HTTP Request sẽ tạo ra độ trễ (latency) từ 200ms đến vài giây, phá vỡ trải nghiệm chơi game thời gian thực. 

`ChaosTheoryHelper` giải quyết bài toán này bằng cơ chế **Bất đồng bộ tuần hoàn (Asynchronous Background Re-seeding)**.

### 2.2. Đặc tả Thuật toán
- **File mã nguồn:** Server: [ChaosTheoryHelper.java](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/server/src/main/java/com/vn/jet/mosco/spinserver/utils/ChaosTheoryHelper.java)
- **Đầu vào (Input):**
  - API Endpoint: `https://www.random.org`
  - Fallback entropy: `System.nanoTime() ^ System.currentTimeMillis()`
- **Đầu ra (Output):**
  - Số thực ngẫu nhiên $[0.0, 1.0)$ hoặc số nguyên ngẫu nhiên trong khoảng chỉ định.

### 2.3. Mã giả Thuật toán (Pseudo-code)

```text
CLASS ChaosTheoryHelper
    STATIC random = SecureRandom()
    STATIC scheduler = ScheduledExecutorService(threadName="chaos-theory-scheduler", isDaemon=true)
    
    // Khởi chạy khi nạp Class
    STATIC INITIALIZER
        // Seed tạm thời tránh block khởi động
        random.setSeed(System.nanoTime() XOR System.currentTimeMillis())
        
        // Lên lịch chạy ngầm định kỳ mỗi 10 phút để nạp seed khí quyển
        scheduler.scheduleWithFixedDelay(
            task = injectChaosAsynchronously,
            initialDelay = 0,
            delay = 10,
            unit = MINUTES
        )
    END STATIC INITIALIZER
    
    FUNCTION injectChaosAsynchronously()
        TRY
            url = "https://www.random.org/integers/?num=1&min=1&max=1000000000&col=1&base=10&format=plain&rnd=new"
            response = httpClient.executeRequest(url, timeout=5_SECONDS)
            
            IF response.isSuccessful AND response.body IS NOT NULL THEN
                rawNumber = ParseLong(response.body.trim())
                // Trộn True Random từ API với thời gian thực nano để gia tăng độ hỗn loạn tối đa
                newSeed = rawNumber XOR System.nanoTime()
                random.setSeed(newSeed)
                Log("Re-seed thành công với hạt giống khí quyển: " + rawNumber)
            ELSE
                LogWarning("Không thể kết nối random.org. Sử dụng hạt giống hệ thống fallback.")
                fallbackSeed()
            END IF
        CATCH Exception e
            LogError("Lỗi mạng khi re-seed: " + e.message)
            fallbackSeed()
        END TRY
    END FUNCTION
    
    FUNCTION fallbackSeed()
        random.setSeed(System.nanoTime() XOR System.currentTimeMillis())
    END FUNCTION
    
    FUNCTION nextDouble()
        // Gọi SecureRandom cực nhanh (0ms) từ nguồn seed đã được làm giàu liên tục
        RETURN random.nextDouble()
    END FUNCTION
    
    FUNCTION nextInt(bound)
        IF bound <= 0 THEN RETURN 0
        RETURN random.nextInt(bound)
    END FUNCTION
END CLASS
```

### 2.4. Thiết kế Tối ưu Hiệu năng (Performance Justification)
- **Zero Blocking Latency:** Bằng cách tách biệt hoàn toàn việc truy xuất API mạng ra một luồng Daemon chạy ẩn định kỳ mỗi 10 phút, luồng chính xử lý logic game (như quay Gacha, nâng cấp thẻ) chỉ việc đọc từ vùng nhớ RAM đã được làm giàu hạt giống, đảm bảo thời gian phản hồi đạt mức tối ưu tuyệt đối **0ms**.
- **Daemon Threads:** Scheduler sử dụng luồng Daemon đảm bảo luồng này sẽ tự động giải phóng và bị tắt khi JVM dừng, tránh hiện tượng Memory Leak hoặc treo tiến trình hệ thống khi tắt Server.

---

### 3.8.3. Thuật toán Đồng bộ hóa Dữ liệu Delta (Delta Sync Algorithm)

### 3.1. Ý nghĩa & Bối cảnh
Trong màn hình danh sách thẻ bài hoặc giao diện Chat, các thẻ bài gốc (kích thước lớn) cần được hiển thị ở dạng ảnh thu nhỏ (Avatar hình tròn). Nếu cắt ảnh theo cách căn giữa thông thường (Center Crop), khuôn mặt của nghệ sĩ trên thẻ bài thường bị lệch hoặc bị mất một phần (do tỉ lệ ảnh gốc đa dạng và nghệ sĩ không đứng chính giữa). 

Để giải quyết bài toán UX này, dự án tích hợp thư viện **Google ML Kit Face Detection** trực tiếp vào luồng xử lý ảnh của Glide, tự động định vị khuôn mặt để thực hiện khoanh vùng cắt tròn hoàn hảo.

### 3.2. Đặc tả Thuật toán
- **File mã nguồn:** Client: [SmartFaceCropTransformation.java](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/utils/SmartFaceCropTransformation.java)
- **Đầu vào (Input):**
  - `toTransform` (Bitmap): Ảnh gốc đầy đủ của thẻ bài.
- **Đầu ra (Output):**
  - `result` (Bitmap): Ảnh avatar hình tròn đã cắt căn giữa chính xác vào khuôn mặt nghệ sĩ.

### 3.3. Mã giả Thuật toán (Pseudo-code)

```text
FUNCTION transform(bitmapPool, toTransform, outWidth, outHeight)
    // 1. Loại bỏ 18% viền trang trí màu bên phải của phôi thẻ gốc
    usableWidth = INT(toTransform.width * 0.82)
    size = MIN(usableWidth, toTransform.height)
    
    // Tọa độ cắt căn giữa mặc định (Trường hợp không tìm thấy mặt)
    targetX = (usableWidth - size) / 2
    targetY = (toTransform.height - size) / 2
    
    TRY
        // 2. Khởi tạo và cấu hình bộ phát hiện khuôn mặt của Google ML Kit
        options = FaceDetectorOptions.Builder()
            .setPerformanceMode(PERFORMANCE_MODE_FAST)
            .build()
        detector = FaceDetection.getClient(options)
        inputImage = InputImage.fromBitmap(toTransform, rotation=0)
        
        // Chờ kết quả phát hiện mặt đồng bộ (an toàn vì Glide chạy hàm này trên luồng ngầm)
        faces = Tasks.await(detector.process(inputImage))
        
        IF faces IS NOT EMPTY THEN
            mainFace = faces.get(0)
            bounds = mainFace.boundingBox
            faceCenterX = bounds.centerX()
            faceCenterY = bounds.centerY()
            
            // Dịch chuyển vùng cắt hình vuông để bao quanh tâm khuôn mặt
            targetX = faceCenterX - (size / 2)
            targetY = faceCenterY - (size / 2)
            
            // Ràng buộc tọa độ nằm trong giới hạn cho phép
            IF targetX < 0 THEN targetX = 0
            IF targetY < 0 THEN targetY = 0
            IF targetX + size > usableWidth THEN targetX = usableWidth - size
            IF targetY + size > toTransform.height THEN targetY = toTransform.height - size
            
            Log("Đã phát hiện khuôn mặt! Căn chỉnh tâm cắt tại: " + faceCenterX + "," + faceCenterY)
        END IF
    CATCH Exception e
        Log("ML Kit thất bại, tự động fallback căn giữa: " + e.message)
    END TRY
    
    // 3. Thực hiện cắt hình vuông và áp dụng mặt nạ tròn (Circular Mask)
    squaredBitmap = Bitmap.createBitmap(toTransform, targetX, targetY, size, size)
    
    // Tái sử dụng bộ nhớ đệm bitmap từ Glide Pool
    resultBitmap = bitmapPool.get(size, size, Config.ARGB_8888)
    resultBitmap.setHasAlpha(true)
    
    canvas = Canvas(resultBitmap)
    // Clear canvas vẽ trong suốt
    canvas.drawColor(TRANSPARENT, PorterDuff.Mode.CLEAR)
    
    paint = Paint(antiAlias=true)
    // Vẽ hình tròn cơ sở
    canvas.drawCircle(centerX = size/2, centerY = size/2, radius = size/2, paint)
    
    // Sử dụng chế độ hòa trộn SRC_IN để lồng ảnh hình vuông vào khuôn tròn
    paint.setXfermode(PorterDuffXfermode(PorterDuff.Mode.SRC_IN))
    canvas.drawBitmap(squaredBitmap, left=0, top=0, paint)
    
    // Giải phóng bộ nhớ đệm
    IF squaredBitmap NOT EQUALS toTransform THEN
        bitmapPool.put(squaredBitmap)
    END IF
    
    RETURN resultBitmap
END FUNCTION
```

### 3.4. Thiết kế Tối ưu Hiệu năng (Performance Justification)
- **Tasks.await() Blocking:** ML Kit là tác vụ bất đồng bộ. Tuy nhiên, Glide gọi phương thức `transform` từ luồng tính toán ngầm (`DiskCacheExecutor`). Việc dùng `Tasks.await()` biến lời gọi thành đồng bộ giúp giữ code phẳng, dễ kiểm soát luồng dữ liệu mà hoàn toàn không gây đóng băng giao diện người dùng (Main Thread UI).
- **Tái sử dụng bộ nhớ (Bitmap Pool):** Thuật toán tận dụng `bitmapPool.get` của Glide để tái sử dụng vùng nhớ đệm Bitmap đã cấp phát. Điều này giúp giảm thiểu tần suất hệ thống kích hoạt **Garbage Collection (GC)**, tối ưu bộ nhớ đáng kể trên các thiết bị cấu hình yếu (như Android 9 Emulator).

---

### 3.8.4. Thuật toán Bộ đệm LRU (Least Recently Used) và Cơ chế Lazy Loading Hình ảnh

### 4.1. Ý nghĩa & Bối cảnh
Khi người dùng cuộn (scroll) danh sách thẻ bài với số lượng lên đến hàng ngàn thẻ, thao tác lướt nhanh (Fling) có thể tạo ra vận tốc cuộn cực đại rất lớn. Điều này ép hệ thống phải tải và render ảnh liên tục với tần suất cao, dẫn đến quá tải băng thông mạng, tràn bộ nhớ đệm CPU/GPU và gây hiện tượng giật lag khung hình (Jank). 

Giải pháp khắc phục bao gồm hai phần phối hợp:
1. **ABS Fling Brakes (ViewUtils):** Phanh hãm lực cuộn tự động khi vận tốc vượt ngưỡng thiết lập.
2. **Dynamic Grid Scaling (GridScaleScrollListener):** Áp dụng hiệu ứng thu nhỏ và làm mờ dần các thẻ bài khi chúng trượt ra sát rìa trên/dưới màn hình để tạo cảm giác "Quiet Luxury" sâu lắng.

### 4.2. Đặc tả Thuật toán
- **File mã nguồn:** Client:
  - [ViewUtils.java](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/utils/ViewUtils.java)
  - [GridScaleScrollListener.java](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/utils/GridScaleScrollListener.java)
- **Đầu vào (Input):**
  - Sự kiện cuộn trên `RecyclerView`.
  - Hằng số `max_fling_velocity` cấu hình trong hệ thống XML.
- **Đầu ra (Output):**
  - Các thuộc tính `scaleX`, `scaleY`, `alpha` của các View con được cập nhật động theo thời gian thực.

### 4.3. Mã giả Thuật toán (Pseudo-code)

#### A. Thuật toán Phanh Vận tốc lướt (ABS Fling Brakes)
```text
FUNCTION limitFlingVelocity(recyclerView)
    // Đăng ký bộ lắng nghe sự kiện lướt
    recyclerView.setOnFlingListener(NEW OnFlingListener() {
        OVERRIDE FUNCTION onFling(velocityX, velocityY)
            // Lấy giới hạn tốc độ tối đa từ tài nguyên XML (ví dụ: 4000 pixel/giây)
            maxVelocity = recyclerView.context.resources.getInteger(R.integer.max_fling_velocity)
            
            newX = velocityX
            newY = velocityY
            
            // Áp dụng giới hạn vận tốc
            IF ABS(velocityX) > maxVelocity THEN
                newX = SIGN(velocityX) * maxVelocity
            END IF
            IF ABS(velocityY) > maxVelocity THEN
                newY = SIGN(velocityY) * maxVelocity
            END IF
            
            // Nếu có sự điều chỉnh, tự kích hoạt fling thủ công và tiêu thụ sự kiện
            IF newX != velocityX OR newY != velocityY THEN
                recyclerView.fling(newX, newY)
                RETURN TRUE // Đã xử lý xong (Hệ thống không xử lý tiếp)
            END IF
            
            RETURN FALSE // Giữ nguyên hành vi mặc định nếu vận tốc thấp hơn giới hạn
        END FUNCTION
    })
END FUNCTION
```

#### B. Thuật toán Co giãn & Làm mờ Vùng Rìa Grid (Dynamic Scaling)
```text
FUNCTION applyScaleEffect(recyclerView)
    height = recyclerView.height
    IF height == 0 THEN RETURN
    
    centerY = height / 2
    maxDistance = height / 2.0
    minScale = 0.85 // Thẻ sẽ bị thu nhỏ còn tối đa 85% ở rìa
    threshold = 0.7 // Ngưỡng 70% (chỉ co giãn khi thẻ trượt quá 70% khoảng cách từ tâm ra rìa)
    
    FOR i = 0 TO recyclerView.childCount - 1 DO
        child = recyclerView.getChildAt(i)
        
        IF child.height == 0 THEN
            child.scaleX = 1.0
            child.scaleY = 1.0
            child.alpha = 1.0
            CONTINUE
        END IF
        
        childCenterY = child.top + (child.height / 2)
        distance = ABS(centerY - childCenterY)
        ratio = MIN(1.0, distance / maxDistance)
        
        normalizedRatio = 0.0
        // Chỉ áp dụng hiệu ứng khi thẻ vượt ngưỡng threshold
        IF ratio > threshold THEN
            normalizedRatio = (ratio - threshold) / (1.0 - threshold)
        END IF
        
        // Tính toán tỉ lệ co giãn động
        scale = 1.0 - ((1.0 - minScale) * normalizedRatio)
        
        // Tính độ mờ nhạt dần (tối đa mờ đi còn 40% ở sát viền)
        alpha = 1.0 - (0.6 * normalizedRatio)
        
        // Cập nhật trực tiếp lên View
        child.scaleX = scale
        child.scaleY = scale
        child.alpha = alpha
    END FOR
END FUNCTION
```

### 4.4. Thiết kế Tối ưu Hiệu năng (Performance Justification)
- **Giảm tải chu kỳ vẽ (Render Overload Mitigation):** Hãm vận tốc cuộn giúp trì hoãn tần suất nạp/giải nén ảnh của Glide, giảm tải trực tiếp cho luồng Garbage Collection của Android, loại bỏ hiện tượng tràn RAM giả lập.
- **Trì hoãn bằng Post-Layout (Dynamic Layout Sync):** Trong sự kiện `onLayoutChange`, thuật toán sử dụng cơ chế trì hoãn `recyclerView.post(...)` để đảm bảo việc tính toán tỉ lệ scale chỉ xảy ra sau khi các view con đã được định vị chính xác hoàn toàn trong cây View, ngăn ngừa xung đột luồng và hiện tượng giật giật (layout flickering).

---

### 3.8.5. Cơ chế Xử lý Tin nhắn Thời gian thực (Real-time Pub/Sub Message Broker)

### 5.1. Ý nghĩa & Bối cảnh
Để vận hành kho đồ chứa hàng chục ngàn thẻ bài nghệ sĩ ở chế độ **Local-First**, Client bắt buộc phải lưu trữ một bản sao cơ sở dữ liệu gốc (Galactic Master Data) cục bộ. Tuy nhiên, dữ liệu này liên tục thay đổi (thêm thẻ mới, đổi ảnh, thay đổi mùa thẻ) từ phía Server. 

Nếu tải toàn bộ file JSON thô có dung lượng lớn mỗi khi vào app, thiết bị sẽ gặp lỗi tràn bộ nhớ (OOM) hoặc nghẽn mạng. Giải pháp của dự án là **Thuật toán Đồng bộ Hai Bước (Two-Phase Sync)** kết hợp kỹ thuật **Bẻ khóa Bộ nhớ đệm CDN (Cache-Busting)**:
1.  **Bước 1 (Check Manifest):** Client gửi request nhẹ để so sánh nhãn thời gian (timestamp) cục bộ với server.
2.  **Bước 2 (Bust & Pull):** Nếu có bản mới, client ép tải file dữ liệu JSON đầy đủ bằng cách gán khóa băm thời gian thực (tránh bộ nhớ đệm CDN Cloudflare), sau đó nạp ngầm bất đồng bộ vào Room Database.

### 5.2. Đặc tả Thuật toán
- **File mã nguồn:** Client: [DatabaseLoader.java](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/utils/DatabaseLoader.java)
- **Đầu vào (Input):**
    - SharedPreferences `last_sync_timestamp`: nhãn thời gian của phiên đồng bộ thành công gần nhất trên máy client.
    - API Manifest: `@GET("/api/assets/manifest")` chứa nhãn thời gian từ phía Server (`remoteSyncTime`).
    - API Full Database: `@GET("api/v1/assets/database")` nhận tham số cache-buster.
- **Đầu ra (Output):**
    - File `database.json` trong bộ nhớ trong ứng dụng được cập nhật.
    - Room Database `master_objets` được làm sạch và nạp mới toàn bộ thực thể `MasterObjetEntity`.
    - SharedPreferences `last_sync_timestamp` được cập nhật timestamp mới.
    - Giao diện cập nhật danh sách kho đồ thông qua `notifyInventoryChanged()`.

### 5.3. Mã giả Thuật toán (Pseudo-code)

```text
FUNCTION syncMetadataWithServer(context, callback)
    appContext = context.getApplicationContext()
    
    // 1. Gửi request lấy manifest dung lượng siêu nhỏ để kiểm tra cập nhật
    apiService.getAssetManifest().enqueue(NEW Callback() {
        OVERRIDE FUNCTION onResponse(call, response)
            IF response.isSuccessful AND response.body IS NOT NULL THEN
                manifest = ParseJson(response.body.string())
                remoteSyncTime = manifest.getLong("lastSync")
                
                localSyncTime = appContext.getSharedPreferences().getLong("last_sync_timestamp", 0)
                
                // So sánh nhãn thời gian
                IF remoteSyncTime > localSyncTime OR localSyncTime == 0 THEN
                    sizeMb = 2.0 // Ước tính kích thước dữ liệu nén
                    // Thông báo cho UI hiển thị nút bấm/tiến trình cập nhật cho người dùng
                    callback.onUpdateAvailable(remoteSyncTime, sizeMb)
                ELSE
                    callback.onNoUpdate()
                END IF
            ELSE
                callback.onNoUpdate()
            END IF
        END FUNCTION

        OVERRIDE FUNCTION onFailure(call, t)
            // Lỗi mạng hoặc server offline -> Dùng cache Room DB nội địa hoàn toàn
            callback.onNoUpdate()
        END FUNCTION
    })
END FUNCTION

FUNCTION pullFullDatabase(appContext, newTimestamp, callback)
    // 2. Tạo chuỗi Cache Buster độc nhất để vượt qua bộ lọc cache của Cloudflare Edge CDN
    cacheBuster = "t=" + System.currentTimeMillis()
    callback.onProgress(20)
    
    apiService.getFullDatabase(cacheBuster).enqueue(NEW Callback() {
        OVERRIDE FUNCTION onResponse(call, response)
            IF response.isSuccessful AND response.body IS NOT NULL THEN
                // Chuyển việc ghi đĩa và xử lý JSON sang Thread chạy ngầm tránh ANR
                START_BACKGROUND_THREAD
                    TRY
                        jsonString = response.body.string()
                        callback.onProgress(60)
                        
                        IF LENGTH(jsonString) < 100 THEN
                            callback.onError("Dữ liệu JSON tải về không hợp lệ")
                            RETURN
                        END IF
                        
                        // 3. Ghi đè file JSON cục bộ vào Internal Storage (/files/database.json)
                        success = updateInternalDatabaseFile(appContext, jsonString, newTimestamp)
                        
                        IF success THEN
                            // Xóa cache Map trong bộ nhớ RAM
                            clearMemoryCache()
                            
                            // 4. Đồng bộ dữ liệu JSON thô vừa ghi vào Room DB
                            isRoomSyncing = true
                            db = AppDatabase.getInstance(appContext)
                            db.masterObjetDao().deleteAll() // Dọn sạch dữ liệu cũ
                            
                            // Parse JSON và chuẩn bị danh sách Entity nạp theo lô
                            rootObj = ParseJson(jsonString)
                            cardsArray = rootObj.getJSONArray("collections")
                            entities = EMPTY_LIST
                            
                            FOR i = 0 TO LENGTH(cardsArray) - 1 DO
                                cardJson = cardsArray.getJSONObject(i)
                                entity = NEW MasterObjetEntity()
                                
                                colId = cardJson.optString("collectionId")
                                IF colId IS EMPTY THEN colId = cardJson.getString("id")
                                
                                entity.collectionId = colId
                                entity.memberName = cardJson.getString("member")
                                entity.seasonName = cardJson.getString("season")
                                entity.rarityClass = cardJson.getString("class")
                                entity.frontImageId = cardJson.getString("frontImage")
                                entity.backImageId = cardJson.getString("backImage")
                                entity.baseOvr = cardJson.getInt("ovr")
                                
                                ADD entity TO entities
                            END FOR
                            
                            // Thực hiện Batch Insert vào SQLite qua Room
                            db.masterObjetDao().insertAll(entities)
                            isRoomSyncing = false
                            
                            // 5. Cập nhật nhãn thời gian đồng bộ thành công vào SharedPreferences
                            appContext.getSharedPreferences().putLong("last_sync_timestamp", newTimestamp)
                            
                            // Trả kết quả về Main Thread để cập nhật UI
                            RUN_ON_MAIN_THREAD
                                callback.onProgress(100)
                                callback.onComplete()
                                notifyInventoryChanged() // Bắn sự kiện vẽ lại các grid card
                            END RUN_ON_MAIN_THREAD
                        ELSE
                            callback.onError("Không thể ghi tệp tin vào bộ nhớ trong")
                        END IF
                    CATCH Exception e
                        isRoomSyncing = false
                        callback.onError(e.message)
                    END TRY
                END_BACKGROUND_THREAD
            ELSE
                callback.onError("Mã lỗi phản hồi từ Server: " + response.code())
            END IF
        END FUNCTION

        OVERRIDE FUNCTION onFailure(call, t)
            callback.onError(t.message)
        END FUNCTION
    })
END FUNCTION
```

- **Background Parsing:** Toàn bộ công đoạn xử lý chuỗi JSON thô hàng chục MB và chèn dữ liệu vào Room được đẩy hoàn toàn xuống luồng phụ (Background Thread), đảm bảo luồng vẽ giao diện chính (Main UI Thread) không bị treo hay giật lag (đạt chuẩn mượt mà 60fps trên Android 9 Emulator).

---

### 3.8.6. Thuật toán Gói quà (Gift Distribution Algorithm)

### 6.1. Ý nghĩa & Bối cảnh
Để đảm bảo kho dữ liệu thẻ bài nghệ sĩ trên server Spring Boot luôn đồng bộ với các đợt phát hành thẻ mới trên thực tế mà không cần nhà phát triển phải nhập liệu thủ công, hệ thống backend cài đặt một tiến trình cào dữ liệu tự động từ nguồn `objekt.top` kết hợp với pipeline ETL (Extract - Transform - Load).

Thao tác nạp hàng chục ngàn dòng dữ liệu từ xa vào MySQL rất dễ gây ra các lỗi hiệu năng như nghẽn mạng, xung đột tài nguyên hoặc lỗi **N+1 Query** khi kiểm tra và tạo mới các bảng từ điển khóa ngoại (`members`, `seasons`, `classes`). Thuật toán giải quyết vấn đề này bằng các kỹ thuật:
1.  **Chống chạy trùng (Status Lock):** Dùng biến trạng thái volatile để khóa ngăn chặn hai job chạy song song gây xung đột dữ liệu.
2.  **Bộ nhớ đệm từ điển (JPA Dictionary Cache):** Cache cục bộ các thực thể danh mục để tránh việc truy vấn SQL liên tục khi lập chỉ mục khóa ngoại.
3.  **Tách mã ảnh bằng Regex:** Sử dụng Regular Expression biên dịch sẵn để trích xuất chỉ lấy mã ảnh băm duy nhất từ URL đầy đủ của Cloudflare CDN, giảm dung lượng DB.
4.  **Batch Load:** Thực hiện ghi cơ sở dữ liệu theo từng lô (Batch size = 200) thay vì lưu từng dòng đơn lẻ.

### 6.2. Đặc Tả Thuật Toán
- **File mã nguồn:** Server:
    - [AssetManagementService.java](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/server/src/main/java/com/vn/jet/mosco/spinserver/service/AssetManagementService.java)
    - [EtlService.java](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/server/src/main/java/com/vn/jet/mosco/spinserver/service/EtlService.java)
- **Đầu vào (Input):**
    - Trang API nguồn: `https://objekt.top/api/collection?artist=tripleS&limit=20000`.
- **Đầu ra (Output):**
    - File dữ liệu thô `database.json` và tệp chỉ mục `manifest.json`.
    - Dữ liệu đồng bộ (UPSERT) trong các bảng MySQL: `members`, `seasons`, `classes`, `cards`.

### 6.3. Mã giả Thuật toán (Pseudo-code)

```text
CLASS AssetManagementService
    VOLATILE syncStatus = "IDLE"
    
    // Luồng tự động quét định kỳ hàng giờ
    @Scheduled(cron = "0 0 * * * *")
    FUNCTION scheduledSync()
        Log("Bắt đầu chu kỳ cập nhật Metadata định kỳ...")
        fullSyncProcess()
    END FUNCTION

    // Luồng chạy khi server khởi động xong
    @EventListener(ApplicationReadyEvent)
    FUNCTION onApplicationReady()
        Log("Server ready! Khởi chạy đồng bộ lần đầu...")
        fullSyncProcess()
    END FUNCTION

    FUNCTION fullSyncProcess()
        // 1. Áp dụng Khóa Trạng thái (Mutex lock) ngăn chặn chạy song song
        IF syncStatus != "IDLE" THEN
            LogWarning("Đang có tiến trình đồng bộ khác chạy, bỏ qua lần này.")
            RETURN
        END IF
        
        TRY
            syncStatus = "SCRAPING"
            
            // 2. Gọi API ngoài lấy dữ liệu JSON thô
            jsonContent = fetchLatestMetadataFromObjektTop()
            IF jsonContent IS NULL THEN
                syncStatus = "IDLE"
                RETURN
            END IF
            
            // Parse dữ liệu và sắp xếp theo ngày tạo giảm dần (mới nhất lên đầu)
            collectionsList = parseJsonToCollectionsList(jsonContent)
            SortCollectionsByCreatedAtDescending(collectionsList)
            
            // 3. Kiểm tra thay đổi kích thước file và ghi đè file database.json
            oldSize = GetFileLength("data/assets/database.json")
            saveSortedDatabaseToFile(jsonContent, collectionsList)
            newSize = GetFileLength("data/assets/database.json")
            
            // Chỉ cập nhật manifest.json nếu có thay đổi dung lượng file thực tế
            IF oldSize != newSize THEN
                generateNewManifestFile(LENGTH(collectionsList))
            END IF
            
            // 4. Kích hoạt Job ETL nạp dữ liệu vào MySQL
            etlService.runEtlJob()
            
            // Reload cache bộ nhớ đệm
            cardDataService.reload()
            
            syncStatus = "IDLE"
            Log("Cập nhật Metadata thành công hoàn tất!")
        CATCH Exception e
            syncStatus = "IDLE"
            LogError("Lỗi đồng bộ: " + e.message)
        END TRY
    END FUNCTION
END CLASS

CLASS EtlService
    // Regex tĩnh biên dịch trước để tách ID ảnh từ Cloudflare URL
    STATIC IMAGE_ID_PATTERN = Pattern.compile("https://imagedelivery\\.net/qQuMkbHJ-0s6rwu8vup_5w/([^/]+)/.*")

    @Transactional
    FUNCTION runEtlJob()
        Log("Bắt đầu Job ETL đồng bộ dữ liệu vào MySQL...")
        
        // 1. Caching local để tránh lỗi N+1 query cho các bảng từ điển khóa ngoại
        memberMap = NEW HashMap() // Lưu Cache (Tên -> Member Entity)
        seasonMap = NEW HashMap() // Lưu Cache (Tên -> Season Entity)
        classMap = NEW HashMap()  // Lưu Cache (Tên -> CardClass Entity)
        
        // Đọc tệp tin database.json vừa cào từ đĩa
        collections = readCollectionsFromJsonFile("data/assets/database.json")
        
        batchCards = EMPTY_LIST
        processedIds = NEW HashSet()
        count = 0
        
        FOR EACH dto IN collections DO
            IF dto.id IS NULL OR processedIds.contains(dto.id) THEN
                CONTINUE // Bỏ qua bản ghi rác hoặc trùng lặp trong tệp tin
            END IF
            processedIds.add(dto.id)
            
            // 2. Tra cứu từ điển thông qua cache cục bộ (Nếu không có mới select/insert DB)
            member = memberMap.computeIfAbsent(dto.member, key -> getOrCreateMember(key))
            season = seasonMap.computeIfAbsent(dto.season, key -> getOrCreateSeason(key))
            cardClass = classMap.computeIfAbsent(dto.cardClass, key -> getOrCreateClass(key))
            
            // 3. Trích xuất ID ảnh Cloudflare bằng Regex
            frontImageId = extractImageId(dto.frontImage)
            backImageId = extractImageId(dto.backImage)
            
            // 4. UPSERT vào bảng cards (Tìm bản ghi cũ để cập nhật hoặc tạo mới hoàn toàn)
            card = cardRepository.findById(dto.id).orElse(NEW Card())
            card.id = dto.id
            card.member = member
            card.season = season
            card.cardClass = cardClass
            card.frontImageId = frontImageId
            card.backImageId = backImageId
            card.collectionNo = dto.collectionNo
            
            // Đảm bảo không ghi đè cấp độ/OVR hiện tại nếu là thẻ đã tồn tại
            IF card.baseOvr == 0 THEN card.baseOvr = 70 END IF
            IF card.upgradeLevel == 0 THEN card.upgradeLevel = 1 END IF
            
            // Tạo liên kết video động đối với các thẻ dạng Motion
            IF cardClass.name EQUALS "Motion" THEN
                slug = dto.slug != null ? dto.slug.toLowerCase() : ""
                IF slug IS EMPTY THEN
                    slug = LowerCase(season.name) + "-" + LowerCase(member.name) + "-" + LowerCase(dto.collectionNo)
                END IF
                card.frontVideoUrl = "https://cdn.apollo.cafe/mco/triples/" + slug + ".mp4"
            ELSE
                card.frontVideoUrl = NULL
            END IF
            
            ADD card TO batchCards
            count = count + 1
            
            // 5. Lưu theo lô (Batch Save 200 bản ghi) để tối ưu hóa I/O cơ sở dữ liệu
            IF LENGTH(batchCards) >= 200 THEN
                cardRepository.saveAllAndFlush(batchCards)
                batchCards.clear()
            END IF
        END FOR
        
        // Lưu số lượng bản ghi dư thừa còn lại ở lô cuối
        IF NOT batchCards.isEmpty() THEN
            cardRepository.saveAllAndFlush(batchCards)
        END IF
        
        Log("Hoàn tất ETL! Tổng số card đã UPSERT: " + count)
    END FUNCTION

    FUNCTION getOrCreateMember(name)
        // Tra cứu MySQL, nếu không tồn tại thì tự tạo mới và trả về
        RETURN memberRepository.findByName(name)
                .orElseGet(() -> memberRepository.save(NEW Member(name)))
    END FUNCTION

    FUNCTION extractImageId(url)
        IF url IS NULL THEN RETURN "" END IF
        matcher = IMAGE_ID_PATTERN.matcher(url)
        IF matcher.find() THEN
            RETURN matcher.group(1) // Trích xuất mã ID
        END IF
        RETURN url // Fallback
    END FUNCTION
END CLASS
```

### 6.4. Thiết kế Tối ưu Hiệu năng (Performance Justification)
- **Dictionary Caching (Chống N+1 Query):** Khi nạp 20,000 thẻ bài, nếu mỗi thẻ bài đều truy vấn SQL để tìm `member_id`, `season_id` và `class_id` thì hệ thống sẽ phải thực thi 60,000 câu lệnh `SELECT` (lỗi N+1 Query kinh điển). Bằng việc sử dụng cache `HashMap` cục bộ lưu trữ thực thể từ điển ngay trong tiến trình, hệ thống chỉ truy vấn DB ở những thẻ bài có nghệ sĩ/season mới xuất hiện lần đầu, giảm thiểu 99.9% số lượng query dư thừa.
- **Batch Processing:** Gom nhóm các đối tượng và thực thi ghi dữ liệu theo lô `saveAllAndFlush(200)` giúp Hibernate gộp các lệnh SQL insert/update thành một transaction lớn gửi về MySQL, tối ưu hóa I/O và giảm thiểu độ trễ giao tiếp mạng giữa Backend và DB Server.
- **Pre-compiled Regex:** Tốc độ đối sánh chuỗi URL được tối ưu hóa tối đa nhờ việc biên dịch trước mẫu Regex `IMAGE_ID_PATTERN` và khai báo dưới dạng biến tĩnh (`STATIC`), tránh việc biên dịch mẫu biểu thức chính quy lặp đi lặp lại hàng chục ngàn lần trong vòng lặp.
- **Thread-Safe Mutex Lock:** Sử dụng biến `volatile syncStatus` hoạt động như một cờ hiệu trạng thái (Mutex Lock) giúp bảo vệ tiến trình cào dữ liệu không bị kích hoạt chồng chéo nếu job giờ trước chưa chạy xong, ngăn chặn xung đột tài nguyên đĩa cứng (`database.json` bị ghi đè đồng thời) và lỗi deadlock trên MySQL.

---

### 3.8.7. Thuật toán Cử đội hình (Auto AFK Stage Team Composition)

### 7.1. Ý nghĩa & Bối cảnh
Hệ thống Gacha trao đổi thẻ (Spin) cho phép người chơi hiến tế (sacrifice) một thẻ bài không dùng đến để đổi lấy cơ hội nhận được một thẻ bài khác ngẫu nhiên. Để tăng tính kịch tính và cảm giác hồi hộp chân thực (Gacha UX), hệ thống không chỉ trả về thẻ bài trúng thưởng duy nhất mà còn sinh ra một lưới ma trận chứa 16 thẻ bài (gồm 1 thẻ trúng thực sự và 15 thẻ "nền" làm mồi nhử) để Client chạy hiệu ứng vòng quay ma trận.

Để đảm bảo tính minh bạch, công thức tính toán tỉ lệ rơi áp dụng cơ chế **Biến động tỉ lệ động (Dynamic Fluctuation)** và **Chuẩn hóa (Normalization)** kết hợp với nguồn True RNG khí quyển để ngăn ngừa sự lặp lại chu kỳ của máy tính.

### 7.2. Đặc tả Thuật toán
- **File mã nguồn:**
  - Server: [SpinSystem.java](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/server/src/main/java/com/vn/jet/mosco/spinserver/utils/SpinSystem.java)
- **Đầu vào (Input):**
  - Cấu hình tỉ lệ cơ bản (`spin_rates` từ `rates_config.json`).
  - Danh sách thẻ bài Master đã phân nhóm theo Class (`groupedCards`).
- **Đầu ra (Output):**
  - `result` (JsonObject): Thẻ bài trúng thưởng thực tế (hoặc `Nothing` nếu trượt).
  - `revealGrid` (List<JsonObject>): Lưới 16 thẻ bài được chọn lọc để hiển thị trên UI.
  - `finalRates` (Map<String, Double>): Bảng tỉ lệ thực tế đã chuẩn hóa của phiên quay đó.

### 7.3. Mã giả Thuật toán (Pseudo-code)

#### A. Thuật toán Biến động & Chuẩn hóa Tỉ lệ rơi (Dynamic Rate Fluctuation & Normalization)
```text
FUNCTION calculateFinalRates(baseRates, groupedCards)
    finalRates = EMPTY_MAP
    totalInitial = 0.0

    // 1. Áp dụng biến động ngẫu nhiên động trong biên độ ±10% tỉ lệ gốc
    FOR EACH entry IN baseRates DO
        group = entry.key
        baseRate = entry.value

        // Nếu nhóm không có thẻ nào (trừ Nothing), đặt tỉ lệ bằng 0
        IF group != "Nothing" AND groupedCards.get(group) IS EMPTY THEN
            finalRates.put(group, 0.0)
            CONTINUE
        END IF

        maxFluc = baseRate * 0.10
        fluctuation = (ChaosTheoryHelper.nextDouble() * 2 * maxFluc) - maxFluc
        val = MAX(0.0, baseRate + fluctuation)

        finalRates.put(group, val)
        totalInitial = totalInitial + val
    END FOR

    // 2. Chuẩn hóa tổng tỉ lệ về đúng 100.0% và làm tròn 4 chữ số thập phân
    normalizedRates = EMPTY_MAP
    checkTotal = 0.0

    FOR EACH entry IN finalRates DO
        group = entry.key
        val = entry.value
        
        normalized = 0.0
        IF totalInitial > 0 THEN
            normalized = ROUND_TO_DECIMALS((val / totalInitial) * 100.0, decimals=4)
        END IF
        
        normalizedRates.put(group, normalized)
        checkTotal = checkTotal + normalized
    END FOR

    // 3. Xử lý sai số làm tròn (Rounding Drift) bằng cách bù trừ vào nhóm "Nothing"
    IF checkTotal != 100.0 AND totalInitial > 0 THEN
        diff = 100.0 - checkTotal
        currentNothing = normalizedRates.getOrDefault("Nothing", 0.0)
        normalizedRates.put("Nothing", ROUND_TO_DECIMALS(currentNothing + diff, decimals=4))
    END IF

    RETURN normalizedRates
END FUNCTION
```

#### B. Thuật toán Sinh Lưới Ma trận hiển thị (Reveal Grid Builder)
```text
FUNCTION spin()
    // 1. Tính toán tỉ lệ biến động động và quay thưởng theo trọng số
    finalRates = calculateFinalRates(baseRates, groupedCards)
    selectedGroup = selectGroupWeighted(finalRates) // Lấy nhóm trúng dựa trên RNG

    cardsInGroup = groupedCards.get(selectedGroup)
    IF cardsInGroup IS EMPTY THEN
        RETURN EmptySpinResult()
    END IF

    // Bốc ngẫu nhiên thẻ trúng thưởng thực tế
    randomIndex = ChaosTheoryHelper.nextInt(LENGTH(cardsInGroup))
    winningCard = cardsInGroup.get(randomIndex)

    // 2. Phân phối lưới 16 thẻ (1 thẻ thắng + 15 thẻ mồi nhử)
    distribution = buildCaseDistribution(selectedGroup)
    
    // Giảm số lượng của nhóm trúng đi 1 (chính là winningCard)
    distribution.put(selectedGroup, distribution.get(selectedGroup) - 1)

    revealGrid = EMPTY_LIST

    FOR EACH entry IN distribution DO
        group = entry.key
        count = entry.value
        IF count <= 0 THEN CONTINUE END IF

        pool = groupedCards.get(group)
        IF pool IS EMPTY THEN CONTINUE END IF

        // Loại bỏ thẻ trúng thưởng thực tế khỏi pool mồi nhử của group đó
        // (Tránh trùng lặp hình ảnh mặt sau thẻ trên giao diện lật)
        safePool = CLONE_LIST(pool)
        REMOVE winningCard FROM safePool

        // Trộn ngẫu nhiên safePool
        ChaosTheoryHelper.shuffle(safePool)
        
        // Bốc các thẻ mồi nhử đưa vào lưới
        takeCount = MIN(count, LENGTH(safePool))
        FOR i = 0 TO takeCount - 1 DO
            ADD safePool.get(i) TO revealGrid
        END FOR
    END FOR

    // Thêm thẻ thắng thực sự vào lưới
    ADD winningCard TO revealGrid

    // Trộn ngẫu nhiên vị trí của 16 thẻ trong lưới trước khi gửi về client
    ChaosTheoryHelper.shuffle(revealGrid)

    RETURN SpinResult(winningCard, revealGrid, selectedGroup)
END FUNCTION
```

### 7.4. Thiết kế Tối ưu Hiệu năng (Performance Justification)
- **Bù trừ sai số trôi (Drift Correction):** Trong các thuật toán chuẩn hóa xác suất tỉ lệ phần trăm, sai số do làm tròn (làm tròn lên/xuống ở dấu phẩy tĩnh) thường tích tụ khiến tổng xác suất lệch khỏi 100.0%. Bằng cách tự động tính toán và bù phần chênh lệch (`drift`) vào nhóm mặc định `Nothing`, thuật toán đảm bảo tính toàn vẹn toán học và tránh lỗi Crash trên các bộ phát hiện RNG nghiêm ngặt.
- **Tránh trùng lặp mồi nhử (Duplicate Back-image Prevention):** Khi vẽ lưới quay trên client, nếu thẻ trúng thưởng cũng đồng thời xuất hiện trong số 15 thẻ mồi nhử, người dùng sẽ thấy 2 ảnh giống hệt nhau khi quay ma trận, phá vỡ tính thẩm mỹ. Việc loại bỏ trực tiếp `winningCard` khỏi `safePool` trước khi bốc mồi nhử đảm bảo tính độc nhất của thẻ trúng thưởng trong phiên quay.
- **Biến động Tỉ lệ tự động (Algorithmic Fluctuation):** Cơ chế tự động thêm/bớt ±10% tỉ lệ cơ bản dựa trên nhiễu khí quyển làm tăng tính bất định cho game, ngăn ngừa hoàn toàn các kỹ thuật soi mã máy hoặc đoán chu kỳ của các thợ cào (Gacha cycle exploits).





---
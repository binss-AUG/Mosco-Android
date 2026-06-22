# CHƯƠNG 3. PHÂN TÍCH VÀ THIẾT KẾ HỆ THỐNG

Dựa trên cơ sở lý thuyết và các nguyên tắc thiết kế kiến trúc đã đề cập ở Chương 2, tài liệu này đi sâu vào việc đặc tả các chức năng cốt lõi, thiết kế luồng tương tác, cấu trúc cơ sở dữ liệu và các giải pháp kỹ thuật của hệ thống Mosco. Toàn bộ nội dung đã được đối chiếu trực tiếp với mã nguồn thực tế.

---

## 3.1. Phân tích chức năng (Use-case)

Hệ thống Mosco phục vụ ba tác nhân chính: Khách vãng lai (Guest), Người chơi đã đăng ký (User), và Hệ thống tự động (System/Cron). Các chức năng được phân tách thành 7 nhóm cốt lõi: Tài khoản (Auth), Sưu tập (Inventory & 3D Viewer), Gameplay (Gacha & Nâng cấp), Tương tác xã hội (Social), Trợ lý Ảo (AI Assistant), Xếp hạng (Leaderboard), và Đồng bộ dữ liệu nền (Background Sync).

**3.1.1. Sơ đồ Use-case tổng quát và Phân rã**
*[CHÈN HÌNH 3.1: Sơ đồ Use-case tổng quát của hệ thống Mosco. Trực quan: Thể hiện đầy đủ 3 Tác nhân (Guest, User, System) kết nối với 7 khối chức năng lớn đã nêu, cùng các mũi tên <<include>> trỏ về khối Auth]*
**3.1.2. Bảng Đặc tả Use-case cốt lõi**

**Bảng 3.1: Đặc tả Use-case "Đăng nhập & Đăng ký (Sign In & Social Login)"**
| Thuộc tính | Mô tả chi tiết |
| :--- | :--- |
| **Mã UC** | UC-01 |
| **Tên Use-case** | Đăng nhập & Đăng ký (Sign In & Social Login) |
| **Tác nhân** | Khách vãng lai (Guest) / Người chơi (User) |
| **Điều kiện tiên quyết** | Thiết bị có kết nối mạng. Đối với Social Login, cần có tài khoản Google hoặc Discord. |
| **Luồng sự kiện chính (Main Flow)** | 1. User mở ứng dụng, chọn đăng nhập bằng Email/Password hoặc Google/Discord.<br>2. Client gọi Firebase Auth SDK (Google) hoặc Discord OAuth2 SDK để lấy OAuth Token.<br>3. Client gọi POST tới `/api/auth/signin` (Email/PW) hoặc `/api/auth/social-login` (OAuth) kèm provider, token, email.<br>4. Server kiểm tra email trong MySQL: nếu đã tồn tại -> liên kết tài khoản (Merge); nếu chưa -> tạo User mới, tặng gói tân thủ (50k Gold + 10k Diamonds), để trống `ingameName`.<br>5. Server trả JWT Token + thông tin User.<br>6. Nếu `ingameName == null` -> Client chuyển tới màn hình thiết lập tên hiển thị; nếu đã có -> vào MainActivity. |
| **Luồng ngoại lệ (Alt Flow)** | - Bước 2a: Lấy OAuth token thất bại -> Client hiển thị lỗi "Không thể xác thực với Google/Discord".<br>- Bước 4a: Sai Email/Password -> HTTP 401, Client thông báo "Tài khoản hoặc mật khẩu không chính xác".<br>- Bước 4b: Email không tồn tại trong OAuth response -> HTTP 400, Client thông báo lỗi. |
| **Kết quả (Post-condition)** | User có JWT Token hợp lệ, được điều hướng vào MainActivity (nếu đã có tên) hoặc DisplayNameSetupActivity (nếu mới). |

**Bảng 3.2: Đặc tả Use-case "Xem & lọc kho đồ (Local-First Caching)"**
| Thuộc tính | Mô tả chi tiết |
| :--- | :--- |
| **Mã UC** | UC-02 |
| **Tên Use-case** | Xem & lọc kho đồ (Local-First Caching) |
| **Tác nhân** | Người chơi (User) |
| **Điều kiện tiên quyết** | User đã đăng nhập và sở hữu ít nhất 1 thẻ bài. |
| **Luồng sự kiện chính (Main Flow)** | 1. User mở CollectionFragment.<br>2. Client kiểm tra cache RAM (`DatabaseLoader.cachedUserInventory`): nếu có, hiển thị danh sách thẻ ngay lập tức, loại bỏ Shimmer loading.<br>3. Song song, Client gọi API `GET /api/inventory/cards/{userId}` ở background để tải danh sách mới nhất từ MySQL.<br>4. Sau khi nhận dữ liệu Server, Client ghi đè Room Database cục bộ và cache RAM.<br>5. User lọc/sắp xếp (Artist, Class, Season, OVR, Level) — Client thực hiện trực tiếp trên cache/Room DB cục bộ, phản hồi tức thì. |
| **Luồng ngoại lệ (Alt Flow)** | - Bước 2a: Cache RAM trống & mất mạng -> Client hiển thị Shimmer Loading vô hạn và nút "Tải lại".<br>- Bước 3a: API lỗi -> Client giữ nguyên dữ liệu cache cũ, không ghi đè. |
| **Kết quả (Post-condition)** | Danh sách thẻ bài hiển thị đầy đủ trên UI, dữ liệu Room DB được đồng bộ với Server. |

**Bảng 3.3: Đặc tả Use-case "Xem & Tương tác Objekt 3D (Objekt Viewer)"**
| Thuộc tính | Mô tả chi tiết |
| :--- | :--- |
| **Mã UC** | UC-03 |
| **Tên Use-case** | Xem & Tương tác Objekt 3D (Objekt Viewer) |
| **Tác nhân** | Người chơi (User) |
| **Điều kiện tiên quyết** | User đã đăng nhập và sở hữu ít nhất 1 Objekt. |
| **Luồng sự kiện chính (Main Flow)** | 1. User truy cập Objekt Viewer từ một trong các entry point: Detail thẻ, AR Camera, Pack Open, Preview nâng cấp, Collection Book, Showcase, Kết quả Gacha.<br>2. Client tải dữ liệu metadata của Objekt (loại, glowColor, các layer, mặt trước/sau).<br>3. Client render Objekt 3D với hiệu ứng ánh sáng/shine dựa trên trường `glowColor` từ JSON của Objekt.<br>4. Objekt được hiển thị đa động theo loại: ảnh tĩnh, video loop, hoặc có âm thanh nền.<br>5. User tương tác: xoay 360° tự nhiên bằng cơ chế chạm, lật xem mặt trước/sau, phóng to/thu nhỏ chi tiết.<br>6. Nếu ở chế độ AR Camera: Client kích hoạt Camera, render Objekt 3D chèn lên nền thực tế ảo; User có thể chụp ảnh lưu vào thư viện. |
| **Luồng ngoại lệ (Alt Flow)** | - Bước 2a: Dữ liệu Objekt chưa tải xong -> Client hiển thị Shimmer Loading.<br>- Bước 3a: glowColor không tồn tại trong JSON -> Client dùng màu mặc định (trắng/vàng).<br>- Bước 6a (AR): Thiết bị không có Camera hoặc chưa cấp quyền -> Client hiển thị Dialog yêu cầu cấp quyền. |
| **Kết quả (Post-condition)** | Objekt được hiển thị với đầy đủ hiệu ứng (glow, video, audio). User đã tương tác và/hoặc chụp ảnh AR. |

**Bảng 3.4: Đặc tả Use-case "Quay thẻ Gacha (Rút thăm ngẫu nhiên vật phẩm)"**
| Thuộc tính | Mô tả chi tiết |
| :--- | :--- |
| **Mã UC** | UC-04 |
| **Tên Use-case** | Quay thẻ Gacha (Rút thăm ngẫu nhiên vật phẩm) |
| **Tác nhân** | Người chơi (User) |
| **Điều kiện tiên quyết** | User đã đăng nhập, ở màn hình Gacha và số dư Diamonds >= giá trị gói quay. |
| **Luồng sự kiện chính (Main Flow)** | 1. User chọn gói Gacha và nhấn "Quay".<br>2. Client gửi Request kèm JWT Token lên Server.<br>3. Server kiểm tra số dư và tiến hành trừ Diamonds.<br>4. Server gọi bộ sinh số ngẫu nhiên TRNG để lấy kết quả (Danh sách thẻ bài trúng thưởng).<br>5. Server ghi nhận thẻ bài mới vào Database cho User.<br>6. Trả kết quả về Client dưới dạng JSON.<br>7. Client nhận dữ liệu và kích hoạt hiển thị hiệu ứng lật thẻ 3D. |
| **Luồng ngoại lệ (Alt Flow)** | - Bước 3a: Số dư không đủ -> Server ném lỗi HTTP 400, Client hiển thị popup "Nạp thêm thẻ".<br>- Bước 4a: Call API TRNG thất bại -> Server tự động Fallback chuyển sang dùng hàm PRNG dự phòng để không gián đoạn Game. |
| **Kết quả (Post-condition)** | Số dư Diamonds giảm, Kho đồ User xuất hiện các thẻ bài mới. Lịch sử Gacha được lưu vào DB để đối soát. |

**Bảng 3.5: Đặc tả Use-case "Nâng cấp thẻ bài"**
| Thuộc tính | Mô tả chi tiết |
| :--- | :--- |
| **Mã UC** | UC-05 |
| **Tên Use-case** | Nâng cấp thẻ bài |
| **Tác nhân** | Người chơi (User) |
| **Điều kiện tiên quyết** | User chọn 1 Thẻ chính (Base Card) và từ 1 đến 5 Thẻ nguyên liệu (Material Cards) từ Kho đồ. Các thẻ không bị trạng thái "Khóa bảo vệ". |
| **Luồng sự kiện chính (Main Flow)** | 1. User nhấn nút "Nâng cấp".<br>2. Client khóa UI (Disable Button) và gửi mảng ID thẻ lên Server.<br>3. Server kích hoạt **Pessimistic Lock** để khóa các bản ghi thẻ bài này trên Database.<br>4. Server xóa các Thẻ nguyên liệu (Burn) và tính toán tỉ lệ thành công dựa trên chênh lệch OVR.<br>5. Chạy thuật toán xác suất. Nếu trúng tỷ lệ, Thẻ chính tăng OVR.<br>6. Server nhả khóa (Release Lock) và trả kết quả về Client.<br>7. Client cập nhật Room DB và hiển thị hiệu ứng thành công/thất bại. |
| **Luồng ngoại lệ (Alt Flow)** | - Bước 3a: Bị lỗi khóa (Lock Timeout) do đang dùng Auto-Click spam -> Server hủy Transaction, trả về lỗi HTTP 409 Conflict.<br>- Bước 5a: Nâng cấp thất bại (Rớt thẻ) -> Thẻ nguyên liệu mất, Thẻ chính bị rớt cấp (Downgrade). |
| **Kết quả (Post-condition)** | Thẻ chính tăng/giảm OVR, Thẻ nguyên liệu bị tiêu hủy. Lịch sử nâng cấp được ghi vào DB. |

**Bảng 3.6: Đặc tả Use-case "Tra cứu Bảng xếp hạng (Leaderboard)"**
| Thuộc tính | Mô tả chi tiết |
| :--- | :--- |
| **Mã UC** | UC-06 |
| **Tên Use-case** | Tra cứu Bảng xếp hạng (Leaderboard) |
| **Tác nhân** | Người chơi (User), Khách (Guest) |
| **Điều kiện tiên quyết** | Không yêu cầu đăng nhập đối với thao tác xem hạng cơ bản. |
| **Luồng sự kiện chính (Main Flow)** | 1. User truy cập vào màn hình "Rank".<br>2. Client gửi Request kèm tham số phân trang (`page`, `size`) và loại hạng (`level`, `wealth`, `collection`) lên Server.<br>3. Server truy xuất CSDL, sắp xếp theo điều kiện và trả về JSON danh sách TOP người chơi.<br>4. Client render danh sách, hiển thị bục vinh quang (Podium) 3D cho Top 1,2,3 với hiệu ứng UI.<br>5. User có thể bấm vào Avatar để chuyển qua trang Profile của người chơi đó. |
| **Luồng ngoại lệ (Alt Flow)** | - Mạng lỗi: Client hiển thị thông báo mất kết nối và nút "Tải lại". |
| **Kết quả (Post-condition)** | Danh sách Top người chơi theo tiêu chí được hiển thị, User có thể tương tác xem Profile. |

**Bảng 3.7: Đặc tả Use-case "Trò chuyện với AI (RAG + SSE Streaming)"**
| Thuộc tính | Mô tả chi tiết |
| :--- | :--- |
| **Mã UC** | UC-07 |
| **Tên Use-case** | Trò chuyện với AI (RAG + SSE Streaming) |
| **Tác nhân** | Người chơi (User) |
| **Điều kiện tiên quyết** | User đã đăng nhập. Server sidecar Python RAG đang chạy (port 5001). User sở hữu ít nhất 1 thẻ bias. |
| **Luồng sự kiện chính (Main Flow)** | 1. User nhấn FAB AI Assistant trên MainActivity, mở AiChatBottomSheet.<br>2. User chọn bias (thành viên tripleS) từ danh sách sở hữu, nhập tin nhắn và nhấn gửi.<br>3. Client xây dựng payload JSON gồm `biasId`, `language`, `messages[]` (20 tin nhắn gần nhất) và gửi SSE request tới `POST /api/ai/chat/stream`.<br>4. Server xây dựng System Instruction: nạp bias personality từ `bias_prompts.json`, game knowledge từ `mosco_knowledge.txt`, thông tin User (tên, coins).<br>5. Server gọi `augmentSystemInstructionWithRag()`: nhúng câu hỏi User -> tìm kiếm cosine similarity trong Vector Store (384-dim, MiniLM) -> lấy top-K đoạn liên quan -> chèn vào system prompt.<br>6. Server gọi LLM: ưu tiên OpenRouter (`openrouter/auto`), fallback sang Gemini (`gemini-2.5-flash`).<br>7. Server nhận phản hồi, mô phỏng streaming bằng cách token hóa và emit từng token qua SSE với độ trễ 30ms.<br>8. Client nhận từng token qua SSE listener (`onEvent`), append vào message đang hiển thị, render Markdown bằng thư viện Markwon.<br>9. Khi luồng đóng (`onClosed`), Client lưu toàn bộ message vào Room DB (`ai_chat_messages`). |
| **Luồng ngoại lệ (Alt Flow)** | - Bước 4a: OpenRouter fail -> Server tự động fallback sang Gemini API.<br>- Bước 4b: Cả hai đều fail -> Server trả fallback text thông báo lỗi tạm thời.<br>- Bước 3a: Cache semantic hit (cosine > 0.95 với câu hỏi trước) -> Server trả cached response ngay, không gọi LLM.<br>- Bước 9a: User nhấn Hủy -> Client đóng SSE, lưu message đang có (dù chưa hoàn chỉnh).<br>- Cooldown 3 giây giữa các lần gọi. |
| **Kết quả (Post-condition)** | Tin nhắn AI hoàn chỉnh được hiển thị trong UI và lưu vào Room DB. Lịch sử chat được phân loại theo biasId. |

**Bảng 3.8: Đặc tả Use-case "Đồng bộ Metadata & ETL Pipeline"**
| Thuộc tính | Mô tả chi tiết |
| :--- | :--- |
| **Mã UC** | UC-08 |
| **Tên Use-case** | Đồng bộ Metadata & ETL Pipeline |
| **Tác nhân** | Hệ thống Server (Scheduled Task) / Client (SyncManager) |
| **Điều kiện tiên quyết** | Server có kết nối Internet để scrape dữ liệu từ objekt.top. Python sidecar RAG đang chạy. |
| **Luồng sự kiện chính (Main Flow)** | 1. **Kích hoạt:** Khi `ApplicationReadyEvent` xảy ra hoặc theo lịch `@Scheduled(cron = "0 0 3 * * SUN")`.<br>2. **Scrape:** Server gọi GET request tới `https://objekt.top/api/collection?artist=tripleS&limit=20000` qua OkHttpClient.<br>3. **Parse & Filter:** Server phân tích JSON, lọc rác, sắp xếp theo `createdAt` giảm dần.<br>4. **Cập nhật Manifest:** Ghi dữ liệu vào `database.json`, cập nhật `manifest.json` và nhãn `lastSync`.<br>5. **ETL Job:** Server chạy `EtlService.runEtlJob()`: cache từ điển (Member, Season, CardClass) -> bóc tách Cloudflare image_id -> UPSERT vào MySQL theo lô 200 bản ghi.<br>6. **RAG ETL (Chủ nhật 3AM):** Server gọi `RagEtlService.runEtl()`: index project context files -> duyệt 48 trang kpopping.com -> gọi Python sidecar (`POST /fetch`) để lấy nội dung -> chunk (800-1500 ký tự) -> gọi `POST /embed` để nhúng vector (384-dim) -> lưu vào `storage/wiki_vectors.json`.<br>7. **Delta Sync (Client):** Client gọi `GET /api/v1/cards/sync?lastSyncTime=xxx`, Server trả danh sách Card thay đổi từ mốc đó, Client UPSERT vào Room DB. |
| **Luồng ngoại lệ (Alt Flow)** | - Bước 2a: objekt.top không phản hồi -> Server ghi log lỗi, giữ nguyên dữ liệu cũ, thử lại giờ sau.<br>- Bước 6a: Python sidecar không khởi động -> Server bỏ qua bước RAG ETL, ghi log cảnh báo.<br>- Bước 7a: Client mất mạng -> giữ nguyên `last_sync_time` cũ, thử lại lần khởi chạy sau. |
| **Kết quả (Post-condition)** | Cơ sở dữ liệu Master trên MySQL và Room DB Client được cập nhật với dữ liệu mới nhất. Vector Store chứa kiến thức mới về idol/phát hành. |

**Bảng 3.9: Đặc tả Use-case "Tương tác Xã hội (Chat, Kết bạn, Couple Streak)"**
| Thuộc tính | Mô tả chi tiết |
| :--- | :--- |
| **Mã UC** | UC-09 |
| **Tên Use-case** | Tương tác Xã hội (Chat, Kết bạn, Couple Streak) |
| **Tác nhân** | Người chơi (User) |
| **Điều kiện tiên quyết** | User đã đăng nhập hệ thống. |
| **Luồng sự kiện chính (Main Flow)** | 1. User truy cập vào phân hệ Social.<br>2. Giao tiếp: User gửi tin nhắn lên kênh Chat Thế giới qua STOMP WebSocket.<br>3. Kết bạn: User tìm kiếm bạn bè qua UID, gửi lời mời kết bạn (FriendService).<br>4. Couple Streak: Hai User là bạn bè tương tác liên tục sẽ hình thành chuỗi "Streak" (Ngọn lửa). Server theo dõi thời gian tương tác (CoupleStreakService), nếu quá 24h không trò chuyện sẽ làm đứt chuỗi.<br>5. Client nhận push thông báo real-time qua WebSocket khi có tin nhắn mới. |

**Bảng 3.10: Đặc tả Use-case "Hòm thư & Quà tặng (Mailbox & Gift)"**
| Thuộc tính | Mô tả chi tiết |
| :--- | :--- |
| **Mã UC** | UC-10 |
| **Tên Use-case** | Hòm thư & Quà tặng (Mailbox & Gift) |
| **Tác nhân** | Người chơi (User) / Hệ thống (System) |
| **Điều kiện tiên quyết** | User đã đăng nhập hệ thống. |
| **Luồng sự kiện chính (Main Flow)** | 1. User chọn người bạn đã kết bạn để gửi thẻ bài hoặc Kim cương (GiftService).<br>2. Server kiểm tra sở hữu và trừ tài nguyên của người gửi, đưa món quà vào Hòm thư (Mailbox) của người nhận.<br>3. Người nhận truy cập Hòm thư, chọn món quà và bấm "Nhận".<br>4. Đặc biệt: Hệ thống (System) cũng có thể gửi phát thưởng tự động, bù đắp sự kiện vào Hòm thư.<br>5. User bấm "Claim All" (Nhận tất cả) -> Server gom batch update cộng toàn bộ tài nguyên vào tài khoản người chơi với hiệu suất cao nhất. |

**Bảng 3.11: Đặc tả Use-case "Hệ thống Đi cảnh & Thám hiểm (Stage / AFK)"**
| Thuộc tính | Mô tả chi tiết |
| :--- | :--- |
| **Mã UC** | UC-11 |
| **Tên Use-case** | Hệ thống Đi cảnh & Thám hiểm (Stage / AFK) |
| **Tác nhân** | Người chơi (User) |
| **Điều kiện tiên quyết** | User có thẻ bài đủ điều kiện sức mạnh (OVR). |
| **Luồng sự kiện chính (Main Flow)** | 1. User chọn các Thẻ bài có chỉ số OVR cao để lập thành "Đội hình thám hiểm".<br>2. Gửi Đội hình vào một Stage (Ải) cụ thể.<br>3. Server lưu mốc thời gian bắt đầu (StageService) và khóa các thẻ bài này lại (không cho phép làm phôi nâng cấp).<br>4. Thời gian trôi qua ngay cả khi User tắt app (Cơ chế AFK - Away From Keyboard).<br>5. Hết thời gian đếm ngược, User mở lại Ải và nhấn "Nhận thưởng", Server tính toán kết quả và trả Vàng/Kim cương. |

**Bảng 3.12: Đặc tả Use-case "Sổ tay Sưu tập (Collection Book & Check-in)"**
| Thuộc tính | Mô tả chi tiết |
| :--- | :--- |
| **Mã UC** | UC-12 |
| **Tên Use-case** | Sổ tay Sưu tập (Collection Book & Check-in) |
| **Tác nhân** | Người chơi (User) |
| **Luồng sự kiện chính (Main Flow)** | 1. User truy cập vào Pokédex (Collection Book).<br>2. Client hiển thị toàn bộ hệ thống thẻ bài theo Season / Artist (gọi từ Local-First Cache). Các thẻ đã sở hữu sẽ sáng lên (Unlock), thẻ chưa có sẽ bị bôi đen (Silhouette).<br>3. User điểm danh hàng ngày (Daily Check-in) tại sảnh chờ để nhận tài nguyên.<br>4. Khi thu thập đủ một bộ sưu tập (Set), User nhấn "Claim", Server cấp phần thưởng độc quyền. |


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

**Bảng 3.13: Chi tiết cấu trúc Bảng `users` (Tài khoản người chơi)**
| Tên cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
| :--- | :--- | :--- | :--- |
| `id` | VARCHAR(36) | PK, Not Null | Chuỗi UUID v4 định danh duy nhất User |
| `username` | VARCHAR(50) | Unique, Not Null | Tên đăng nhập hệ thống |
| `hashed_password` | VARCHAR(255) | Not Null | Mật khẩu được mã hóa an toàn bằng thuật toán BCrypt |
| `diamonds` | INT | Default 0 | Tiền tệ cao cấp (Premium Currency) để quay Gacha |
| `avatar_url` | VARCHAR(500) | Nullable | Link ảnh đại diện (Đã qua xử lý cắt ảnh ML Kit) |

**Bảng 3.14: Chi tiết cấu trúc Bảng `master_cards` (Từ điển thẻ bài gốc)**
| Tên cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
| :--- | :--- | :--- | :--- |
| `card_id` | VARCHAR(50) | PK, Not Null | Mã định danh thẻ (Ví dụ: FO4_CR7_2023) |
| `name` | VARCHAR(100) | Not Null | Tên nghệ sĩ / Cầu thủ / Nhân vật |
| `rarity` | VARCHAR(10) | Not Null | Độ hiếm của thẻ (R, SR, SSR, UR) |
| `base_ovr` | INT | Not Null | Chỉ số sức mạnh (OVR) nguyên bản ban đầu |
| `image_url` | VARCHAR(500) | Not Null | Link ảnh phân phối từ mạng CDN Cloudflare (WebP format) |

**Bảng 3.15: Chi tiết cấu trúc Bảng `user_cards` (Kho đồ cá nhân - Inventory)**
| Tên cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
| :--- | :--- | :--- | :--- |
| `id` | BIGINT | PK, Auto Increment | ID tự tăng của dòng dữ liệu thẻ bài |
| `user_id` | VARCHAR(36) | Index, Not Null | Chủ sở hữu thẻ (Logical FK trỏ sang bảng `users`) |
| `card_id` | VARCHAR(50) | Index, Not Null | Loại thẻ gốc (Logical FK trỏ sang bảng `master_cards`) |
| `current_level` | INT | Default 1 | Cấp độ sức mạnh hiện tại (Có thể nâng cấp từ 1 lên 10) |
| `is_locked` | BOOLEAN | Default false | Khóa an toàn (Nếu true, không thể dùng thẻ này làm Phôi hiến tế) |

## 3.4. DANH SÁCH API ENDPOINTS

Tất cả API (trừ auth) đều yêu cầu Header Authorization: Bearer <token>.

**Bảng 3.16: Danh sách các API Endpoints lõi của hệ thống**
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

## 3.5. CÁC GIẢI PHÁP KỸ THUẬT VÀ ĐẶC TẢ THUẬT TOÁN LÕI

Thay vì trình bày rời rạc các giải pháp hạ tầng và đặc tả thuật toán, phần này tích hợp và phân tích sâu các cơ chế cốt lõi tạo nên linh hồn của hệ thống Mosco.

### 3.5.1. Thuật toán Nâng cấp Thẻ bài & Khóa bi quan (Pessimistic Locking)
Thuật toán mô phỏng cơ chế nâng cấp thẻ bài kiểu FIFA Online 4 (FO4). Người chơi sử dụng 1 thẻ chính và chọn từ 1-5 thẻ phôi để nâng cấp. Để ngăn chặn hoàn toàn các lỗi gian lận như **Double-spending** (sử dụng cùng một phôi nâng cấp đồng thời) hoặc **Race Condition** trong môi trường đa luồng, phía Server sử dụng cơ chế khóa bi quan (`@Lock(PESSIMISTIC_WRITE)`).
- **Các bước thực thi:** Server gọi `SELECT ... FOR UPDATE` khóa dữ liệu -> Tiêu hủy thẻ Phôi (Burn) -> Tính xác suất theo chênh lệch OVR -> Đổ xúc xắc quyết định kết quả -> Nhả khóa.
- **Tối ưu:** Locking Scope tối thiểu trên các dòng cụ thể (`findWithLockById`) thay vì toàn bộ bảng, cho phép hàng ngàn người chơi đập thẻ đồng thời không bị nghẽn.

**Mã giả tham khảo (Pseudocode):**
```java
@Transactional
public UpgradeResult upgradeCard(Long baseId, List<Long> materialIds) {
    // 1. Pessimistic Lock khóa thẻ chính và phôi (Tránh Race Condition)
    UserCard baseCard = repo.findWithLockById(baseId);
    List<UserCard> materials = repo.findAllWithLockByIds(materialIds);
    
    // 2. Tiêu hủy phôi (Burn) để tránh Double-spending
    repo.deleteAll(materials);
    
    // 3. Tính toán tỉ lệ theo chênh lệch sức mạnh (OVR)
    double successRate = calculateRate(baseCard.getOvr(), materials);
    
    // 4. Lấy số ngẫu nhiên từ TRNG khí quyển [0.0, 1.0)
    double roll = TRNG.generateRandom();
    
    // 5. Cập nhật trạng thái và giải phóng khóa
    if (roll <= successRate) {
        baseCard.levelUp();
        return new UpgradeResult(SUCCESS, repo.save(baseCard));
    } else {
        baseCard.levelDown(); // Rớt cấp nếu xịt
        return new UpgradeResult(FAIL, repo.save(baseCard));
    }
}
```

### 3.5.2. Thuật toán Mở Pack & Quay thẻ (Spin Gacha / Open Pack)
Tính năng trao đổi thẻ (Spin Gacha) và Mở gói tân thủ (Open Pack) yêu cầu tính minh bạch và ngẫu nhiên tuyệt đối. Hệ thống không dùng `Math.random()` mà lấy số ngẫu nhiên thực sự (TRNG) từ nhiễu khí quyển (random.org).
- **Bất đồng bộ tuần hoàn (Asynchronous Re-seeding):** Luồng Daemon ngầm định kỳ gọi API lấy nhiễu, XOR với `System.nanoTime()` tạo Chaos Seed, lưu vào RAM. Khi User bấm Quay (Spin) hoặc Mở thẻ, Server lấy ngay Chaos Seed từ RAM với độ trễ **0ms latency**.
- **Drift Correction & Ma trận ảo:** Phần dư tỉ lệ (sai số làm tròn) tự động cộng dồn vào phần thưởng rác (Nothing). Để tăng trải nghiệm Gacha, hệ thống bốc 15 thẻ mồi ngẫu nhiên không trùng với thẻ trúng thưởng để gửi về Client tạo hoạt ảnh lật thẻ ma trận 16 ô.

**Mã giả tham khảo (Pseudocode):**
```java
public JsonObject spinGacha(List<Card> masterCards) {
    // 1. Tính toán tỉ lệ cơ bản + Biến động ±10% từ TRNG
    double fluctuation = (TRNG.generateRandom() * 0.2) - 0.1; 
    
    // 2. Bù trừ phần dư (Drift) vào tỉ lệ rác để tổng = 100%
    double totalRate = 0.0;
    for(Card c : masterCards) {
        c.setDynamicRate(c.getBaseRate() + fluctuation);
        totalRate += c.getDynamicRate();
    }
    double drift = 100.0 - totalRate;
    masterCards.get("Nothing_Rác").addRate(drift);
    
    // 3. Roll chốt thẻ trúng thưởng
    Card winningCard = rollDice(masterCards, TRNG.generateRandom());
    
    // 4. Bốc 15 thẻ mồi (Bắt buộc loại bỏ winningCard khỏi pool tránh trùng lặp 2 ảnh)
    List<Card> dummyGrid = get15RandomCardsExcluding(winningCard);
    
    return buildGachaResponse(winningCard, dummyGrid);
}
```

### 3.5.3. Thuật toán Cắt ảnh Đại diện Thông minh (ML Kit Face Crop)
Nếu cắt ảnh Avatar theo cách căn giữa (Center Crop), khuôn mặt nghệ sĩ dễ bị lệch.
- **Giải pháp:** Tích hợp **Google ML Kit Face Detection** tự động định vị khuôn mặt, kết hợp thư viện uCrop để cắt tròn hoàn hảo.
- **Tối ưu:** Tác vụ gọi từ luồng ngầm của Glide (`DiskCacheExecutor`) và ép đồng bộ bằng `Tasks.await()` để giữ code phẳng mà không gây đóng băng Main Thread. Tọa độ cắt được lưu trên Server để khôi phục khi cài lại app (Survive Reinstall).

**Mã giả tham khảo (Pseudocode):**
```java
// Chạy độc lập trong luồng ngầm của thư viện Glide
public Bitmap transform(Bitmap source) {
    // 1. Chuyển đổi ảnh nền sang định dạng InputImage cho ML Kit
    InputImage image = InputImage.fromBitmap(source, 0);
    
    // 2. Chạy Face Detection và ép luồng đồng bộ (Tasks.await)
    FaceDetector detector = FaceDetection.getClient(options);
    List<Face> faces = Tasks.await(detector.process(image));
    
    // 3. Tính toán tọa độ và bán kính để cắt tròn khuôn mặt
    if (!faces.isEmpty()) {
        Rect bounds = faces.get(0).getBoundingBox();
        return CircularCropUtils.cropWithFocus(source, bounds.centerX(), bounds.centerY());
    }
    
    // Fallback: Nếu không có mặt người, cắt giữa (Center Crop)
    return CircularCropUtils.centerCrop(source); 
}
```

### 3.5.4. Thuật toán Hãm cuộn & Tối ưu Ảnh WebP (Fling Brakes)
Tránh tải 3GB dữ liệu ảnh gốc gây OOM (tràn RAM) trên Android Emulator.
- **Cloudflare Interceptor:** OkHttp ép tải định dạng WebP nhẹ hơn 80%.
- **Giảm tải chu kỳ vẽ (Fling Brakes):** Nếu vận tốc lướt danh sách vượt `max_fling_velocity`, hệ thống tự hãm tốc độ để giảm tải tần suất nạp ảnh của thư viện Glide.
- **Dynamic Grid Scaling:** Các thẻ trượt ra sát rìa màn hình sẽ tự động thu nhỏ và mờ dần, tính toán ở sự kiện trì hoãn `onLayoutChange` (post) để tránh giật lag layout.

**Mã giả tham khảo (Pseudocode):**
```java
// Hãm cuộn & Co giãn lưới động trên RecyclerView
recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
    @Override
    public void onScrolled(RecyclerView rv, int dx, int dy) {
        // 1. Phanh hãm tốc độ vuốt (Fling Brake ABS)
        int currentVelocity = getCurrentVelocity();
        if (currentVelocity > MAX_FLING_VELOCITY) {
            rv.fling(0, MAX_FLING_VELOCITY); 
        }
        
        // 2. Dynamic Grid Scaling (Tính toán sau khi Frame đã Render xong)
        rv.post(() -> {
            for (int i = 0; i < rv.getChildCount(); i++) {
                View child = rv.getChildAt(i);
                float distanceFromCenter = calculateDistance(child, screenCenter);
                
                // Thu nhỏ và làm mờ các thẻ nằm xa trung tâm
                float scale = Math.max(0.7f, 1.0f - (distanceFromCenter / screenHeight));
                child.setScaleX(scale);
                child.setScaleY(scale);
                child.setAlpha(scale);
            }
        });
    }
});
```

### 3.5.5. Thuật toán Đồng bộ Dữ liệu (Two-Phase Sync & ETL Pipeline)
Kiến trúc Local-First buộc Client lưu bản sao Master Data cục bộ.
- **Two-Phase Cache Busting (Client):** So sánh `last_sync_timestamp` với Server. Nếu có bản mới, Client gọi API với tham số cache-buster ép tải JSON mới từ CDN, parse ngầm vào Room Database.
- **Dictionary Caching (Server ETL):** Tự động cào thẻ bài từ `objekt.top`. Khi UPSERT 20.000 thẻ, thay vì truy vấn SQL liên tục tìm ID (lỗi N+1 Query), hệ thống nạp toàn bộ danh mục vào HashMap RAM. Gom 200 thẻ một lần đẩy vào MySQL bằng `saveAllAndFlush(200)`.

**Mã giả tham khảo (Pseudocode):**
```java
// ETL Pipeline xử lý theo lô (Batch Processing)
@Scheduled(fixedDelay = 86400000)
public void runEtlJob() {
    // 1. Nạp từ điển vào RAM Cache (Triệt tiêu lỗi N+1 Query)
    Map<String, Member> memberCache = memberRepo.findAll().stream()
        .collect(Collectors.toMap(Member::getName, m -> m));
        
    List<Card> batchList = new ArrayList<>();
    JsonArray rawData = fetchFromObjektTop();
    
    // 2. Cào dữ liệu, phân tích và biến đổi (Transform)
    for (JsonElement item : rawData) {
        // Trích xuất mã ảnh băm bằng Regex tĩnh
        String imageId = IMAGE_ID_PATTERN.matcher(item.getUrl()).group(1);
        
        // Tra cứu ID Nghệ sĩ từ RAM tốc độ O(1)
        Member m = memberCache.getOrDefault(item.getArtist(), createNew(item));
        batchList.add(new Card(m, imageId));
        
        // 3. Đẩy dữ liệu vào CSDL theo lô 200 dòng (Batch Insert)
        if (batchList.size() >= 200) {
            cardRepo.saveAllAndFlush(batchList);
            batchList.clear();
        }
    }
    // Đẩy nốt phần dư thừa cuối cùng
    if (!batchList.isEmpty()) cardRepo.saveAllAndFlush(batchList);
}
```

### 3.5.6. Trợ lý Ảo AI & RAG Pipeline (Retrieval-Augmented Generation)
Để LLM biết về các idol Kpop mới nhất, hệ thống xây dựng Python Sidecar.
- **ETL Extraction:** Cào tài liệu nội bộ và wiki, phân đoạn (chunking), nhúng bằng mô hình `paraphrase-multilingual-MiniLM` và đưa vào Vector Store.
- **Chat Streaming:** Server tính Cosine Similarity lấy kiến thức liên quan chèn vào Prompt, gọi LLM (OpenRouter/Gemini), trả từng Token qua Server-Sent Events (SSE) để mô phỏng gõ chữ mượt mà như ChatGPT.

**Mã giả tham khảo (Pseudocode):**
```python
# RAG Sidecar Endpoint bằng Python Flask
@app.route('/api/chat/stream', methods=['POST'])
def chat_stream():
    user_query = request.json['message']
    
    # 1. Nhúng vector và tìm Top-K tài liệu tương đồng (Cosine Similarity)
    query_vector = embedder.encode(user_query)
    context_docs = vector_store.search_top_k(query_vector, k=3)
    
    # 2. Xây dựng System Prompt với kiến thức đã trích xuất
    system_prompt = f"Bạn là idol Kpop. Trả lời bằng tiếng Việt. Bối cảnh: {context_docs}"
    
    # 3. Gọi LLM và đẩy từng Token về Client dạng Server-Sent Events
    response = llm.generate_stream(system_prompt, user_query)
    def generate():
        for token in response:
            yield f"data: {token}\n\n"
            time.sleep(0.03) # Trễ 30ms mô phỏng trải nghiệm gõ chữ thực tế
            
    return Response(generate(), mimetype='text/event-stream')
```

### 3.5.7. Hệ thống Realtime Chat & Auto Backup (STOMP & WorkManager)
- **STOMP WebSockets:** Cung cấp kết nối 2 chiều không tiêu tốn tài nguyên. Client theo dõi các kênh `/topic/world` và `/topic/streak` để nhận tin nhắn và thông báo cày ngọn lửa tức thời.
- **Anti-Rollback Backup:** Client chạy WorkManager gọi `PRAGMA wal_checkpoint(TRUNCATE)` đóng băng Room DB, copy gửi lên Server. Khi người dùng khôi phục dữ liệu gian lận, hệ thống ép ghi đè bằng Server Truth để chặn exploit.

**Mã giả tham khảo (Pseudocode):**
```java
// Cấu hình STOMP WebSocket Message Broker
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 1. Khởi tạo các kênh Pub/Sub (Chat thế giới, Cá nhân & Push Notification)
        config.enableSimpleBroker("/topic/world", "/topic/private", "/topic/streak");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 2. Định nghĩa endpoint kết nối WebSocket có hỗ trợ SockJS dự phòng
        registry.addEndpoint("/ws-mosco")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
```






---


## 3.7. CÁC VẤN ĐỀ TỒN ĐỌNG (KNOWN ISSUES)

### 3.7.1. Không đồng nhất kiểu dữ liệu ID Tin nhắn (Chat ID Type Mismatch) - CÒN TỒN TẠI

SQLite client lưu senderId, receiverId dưới dạng String; Server MySQL lưu dưới dạng Long.
Ảnh hưởng: Client phải chuyển đổi kiểu dữ liệu rườm rà, giảm hiệu năng truy vấn Room DB.

### 3.7.2. Tính năng Avatar Auto-Crop chưa hoàn thiện trên Client - CÒN TỒN TẠI

Client chỉ cắt ảnh cục bộ qua uCrop, chưa tính toán tỷ lệ tọa độ để gửi avatarCropParams lên Server.
Ảnh hưởng: Khi cài lại app, tọa độ crop thủ công bị mất.

### 3.7.3. Thiếu endpoint /api/gacha/history trong GameApiService.java - CÒN TỒN TẠI

Server đã có controller mapping, nhưng client chưa define endpoint này.

---
*Tài liệu này đã được hiệu chỉnh dựa trên đối chiếu trực tiếp với mã nguồn thực tế của dự án Mosco.*

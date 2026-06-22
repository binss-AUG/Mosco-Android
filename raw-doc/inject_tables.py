import re

tables_md = '''
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
'''

with open('raw-doc/03_chuong_3_phan_tich_thiet_ke_rut_gon.md', 'r', encoding='utf-8') as f:
    text = f.read()

# Replace the old tables section (from Bảng 3.1 to right before 3.1.3)
pattern = r'\*\*Bảng 3\.1:.*?(?=\*\*3\.1\.3\. Phân rã)'
text = re.sub(pattern, tables_md + '\n', text, flags=re.DOTALL)

with open('raw-doc/03_chuong_3_phan_tich_thiet_ke_rut_gon.md', 'w', encoding='utf-8') as f:
    f.write(text)

print('Successfully injected 8 tables!')

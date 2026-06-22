import sys, io, subprocess, re
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

def git_show(sha):
    r = subprocess.run(['git', 'show', sha], capture_output=True, text=False)
    return r.stdout.decode('utf-8')

base  = git_show('ef6d19a:raw-doc/chuong_3_thong_nhat.md')
uc    = git_show('ef6d19a:raw-doc/02_features_and_usecases.md')
algo  = git_show('ef6d19a:raw-doc/03_detailed_design_algorithms.md')

def extract_between(text, start, end=None):
    if start not in text:
        return ''
    after = text.split(start, 1)[1]
    if end and end in after:
        return after.split(end, 1)[0].strip()
    return after.strip()

rag_section = """
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
- Split theo \\n\\n (paragraph).
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
- Timestamp: giải mã từ regex Release Date\\s+(\\d{4}-\\d{2}-\\d{2}) trong chunk.
- Search: cosine similarity + time-decay boost (mới -> +0.15, giảm dần về 0 sau 3 năm).
- Thay thế: replaceDocumentsByPage(pageName, newDocs) — xóa cũ, thêm mới, ghi JSON.

**h) Embedding Cache:**
- GeminiApiService.embeddingCache (ConcurrentHashMap) — tránh nhúng lại cùng text.
- AiChatController.semanticCache (ConcurrentHashMap, max 500 entries) — cache query->response khi cosine > 0.95.

**i) Error Handling:**
- Sidecar chết: log CRITICAL: Local embedding service is down, vector đang có vẫn còn.
- page URL lỗi: Thread.sleep(500) rồi skip sang page tiếp theo.
- Embedding thất bại: trả về empty list, chunk đó không được index.
"""

# ============ BUILD THE FILE ============
output = []

# Title + intro
output.append('# CHƯƠNG 3. PHÂN TÍCH VÀ THIẾT KẾ HỆ THỐNG')
output.append('')
output.append('Dựa trên cơ sở lý thuyết và các nguyên tắc thiết kế kiến trúc đã đề cập ở Chương 2, tài liệu này đi sâu vào việc đặc tả các chức năng cốt lõi, thiết kế luồng tương tác, cấu trúc cơ sở dữ liệu và các giải pháp kỹ thuật của hệ thống Mosco. Toàn bộ nội dung đã được đối chiếu trực tiếp với mã nguồn thực tế.')
output.append('')
output.append('---')
output.append('')

# ===== 3.1 =====
sec31 = extract_between(base, '### 3.1. Phân tích chức năng', '### 3.2.')
output.append('## 3.1. Phân tích chức năng (Use-case)')
output.append('')
output.append('Hệ thống Mosco phục vụ hai tác nhân chính: Khách vãng lai (Guest) và Người chơi đã đăng ký (User). Các chức năng được phân tách thành 4 nhóm chính: Tài khoản (Auth), Kho đồ (Inventory), Gameplay (Gacha & Nâng cấp), và Tương tác xã hội (Social).')
output.append('')
# Get use-case tables from base
uclines = sec31.split('\n')
in_tables = False
for line in uclines:
    if 'Bảng 3.' in line or '|Thuộc tính|' in line or line.strip().startswith('|') or 'Mã UC' in line or 'Tên Use-case' in line or 'Tác nhân' in line or 'Điều kiện' in line or 'Luồng sự kiện' in line or 'Luồng ngoại lệ' in line or 'Kết quả' in line:
        output.append(line)
    elif '---' in line and not in_tables:
        pass
    elif '**3.1.' in line:
        output.append(line)
    elif 'CHÈN HÌNH' in line:
        continue

output.append('')
output.append('---')
output.append('')

# ===== 3.2 Architecture =====
sec32 = extract_between(base, '### 3.2. Thiết kế Kiến trúc', '### 3.3.')
output.append('## 3.2. Mô Hình Kiến Trúc Phần Mềm')
output.append('')
output.append('### 3.2.1. Server-Side: Kiến Trúc Phân Lớp Spring Web MVC')
output.append('')
output.append('Toàn bộ logic nghiệp vụ tuân thủ luồng đi một chiều:')
output.append('Client Request --> Controller --> Service (Nghiệp vụ) --> Repository --> Database (MySQL)')
output.append('')
output.append('* Controller Layer: Tiếp nhận yêu cầu HTTP REST/WebSocket, điều hướng dữ liệu thông qua DTO.')
output.append('* Service Layer: Chứa logic nghiệp vụ cốt lõi, áp dụng transaction và quản trị concurrency.')
output.append('* Repository Layer: Spring Data JPA interface kế thừa JpaRepository.')
output.append('')
output.append('### 3.2.2. Client-Side: Android MVVM + Repository Pattern + Local-First')
output.append('')
output.append('Viết hoàn toàn bằng 100% Java:')
output.append('View (Activity/Fragment) <--> ViewModel (LiveData) <--> Repository <--> Room DB (Offline)')
output.append('<--> Retrofit API (Online)')
output.append('')
output.append('* View Layer: Lắng nghe và vẽ UI dựa trên UI State từ ViewModel.')
output.append('* ViewModel Layer: Giữ trạng thái UI, sống độc lập với vòng đời Activity/Fragment.')
output.append('* Repository Layer: Router dữ liệu Local-First: ưu tiên Room DB, song song gọi API sync.')
output.append('')
output.append('### 3.2.3. Ba tầng Caching (Multi-layer Caching)')
output.append('')
output.append('1. Tầng RAM Cache (DatabaseLoader.cachedUserInventory): hiển thị kho đồ tức thời.')
output.append('2. Tầng Room Database (SQLite): dữ liệu cá nhân (UserStats, UserCards).')
output.append('3. Tầng JSON File Cache: Master Data 20.000+ thẻ.')
output.append('')
output.append('Luồng hoạt động: RAM Cache -> Room DB -> Network API (nếu miss cache).')
output.append('')
output.append('---')
output.append('')

# ===== 3.3 Database =====
output.append('## 3.3. CƠ SỞ DỮ LIỆU')
output.append('')
output.append('Hệ thống lưu trữ phân tán: MySQL 8.0 (Production) / H2 (Dev) ở Server, Room SQLite ở Client.')
output.append('')

# ERD from base
sec33 = extract_between(base, '### 3.3. Thiết kế Cơ sở dữ liệu', '### 3.4.')
if sec33:
    output.append(sec33)

output.append('')
output.append('---')
output.append('')

# ===== 3.4 API Endpoints =====
output.append('## 3.4. DANH SÁCH API ENDPOINTS')
output.append('')
output.append('Tất cả API (trừ auth) đều yêu cầu Header Authorization: Bearer <token>.')
output.append('')

# Extract API from base section 3.6
sec36 = extract_between(base, '### 3.6. Thiết kế Giao tiếp Hệ thống', None)
# Keep only the API table content
if sec36:
    lines = sec36.split('\n')
    in_api = False
    for line in lines:
        if '|' in line and ('Phương thức' in line or '/api/' in line or ':---' in line):
            output.append(line)
        elif '|' in line and line.strip().startswith('|'):
            output.append(line)
        elif line.strip().startswith('**') or line.strip().startswith('###'):
            output.append(line)
        elif 'Lưu ý:' in line:
            output.append(line)

output.append('')
output.append('---')
output.append('')

# ===== 3.5 Technical Solutions =====
output.append('## 3.5. CÁC GIẢI PHÁP KỸ THUẬT CỐT LÕI')
output.append('')

tech_solutions = """
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
"""

output.append(tech_solutions.strip())
output.append('')
output.append(rag_section.strip())
output.append('')
output.append('---')
output.append('')

# ===== 3.6 Known Issues =====
output.append('## 3.6. CÁC VẤN ĐỀ ĐÃ BIẾT (Known Issues)')
output.append('')
output.append('### 3.6.1. Không đồng nhất kiểu dữ liệu ID Tin nhắn (Chat ID Type Mismatch) - CÒN TỒN TẠI')
output.append('')
output.append('SQLite client lưu senderId, receiverId dưới dạng String; Server MySQL lưu dưới dạng Long.')
output.append('Ảnh hưởng: Client phải chuyển đổi kiểu dữ liệu rườm rà, giảm hiệu năng truy vấn Room DB.')
output.append('')
output.append('### 3.6.2. Tính năng Avatar Auto-Crop chưa hoàn thiện trên Client - CÒN TỒN TẠI')
output.append('')
output.append('Client chỉ cắt ảnh cục bộ qua uCrop, chưa tính toán tỷ lệ tọa độ để gửi avatarCropParams lên Server.')
output.append('Ảnh hưởng: Khi cài lại app, tọa độ crop thủ công bị mất.')
output.append('')
output.append('### 3.6.3. Thiếu endpoint /api/gacha/history trong GameApiService.java - CÒN TỒN TẠI')
output.append('')
output.append('Server đã có controller mapping, nhưng client chưa define endpoint này.')
output.append('')
output.append('---')
output.append('')
output.append('*Tài liệu này đã được hiệu chỉnh dựa trên đối chiếu trực tiếp với mã nguồn thực tế của dự án Mosco.*')
output.append('')

# ===== 3.7 Detailed Use-cases =====
output.append('## 3.7. ĐẶC TẢ CHI TIẾT USE-CASE')
output.append('')

# Transform uc content - remove header, renumber sections
uc_lines = uc.split('\n')
skip_header = True
in_diagram = False
in_code_block = False
for line in uc_lines:
    # Skip header and PlantUML diagram
    if skip_header:
        if line.startswith('---'):
            skip_header = False
        continue
    # Skip PlantUML code blocks
    if line.strip().startswith('```'):
        in_code_block = not in_code_block
        continue
    if in_code_block:
        continue
    # Skip original headings (##, ###) - they refer to PHASE structure
    if line.startswith('## ') and 'Sơ đồ Use-case' in line:
        continue
    if line.startswith('## ') and 'Use-case' in line:
        output.append('### 3.7.1. Use-case Authentication')
        continue
    # Transform subsections
    if line.startswith('### ') and 'onboarding' in line.lower():
        output.append('#### Use-Case 1.0: Giới thiệu ứng dụng (Onboarding)')
        continue
    if line.startswith('### ') and 'galactic resource' in line.lower():
        output.append('#### Use-Case 1.0B: Khởi chạy và Đồng bộ tài nguyên lúc khởi động (App Startup & Galactic Resource Sync Pipeline)')
        continue
    if line.startswith('### ') and 'Sign Up' in line:
        output.append('#### Use-Case 1.1: Đăng ký tài khoản (Sign Up)')
        continue
    if line.startswith('### ') and 'Sign In' in line:
        output.append('#### Use-Case 1.2: Đăng nhập (Sign In & Social Login)')
        continue
    if line.startswith('### ') and 'Forgot' in line:
        output.append('#### Use-Case 1.3: Quên mật khẩu & Đặt lại mật khẩu (Forgot & Reset Password)')
        continue
    if line.startswith('### ') and 'Display Name' in line:
        output.append('#### Use-Case 1.4: Thiết lập tên hiển thị lần đầu (Display Name Setup)')
        continue
    if line.startswith('### ') and 'Showcase' in line:
        output.append('#### Use-Case 2.1: Xem và chỉnh sửa hồ sơ cá nhân')
        continue
    if line.startswith('### ') and 'Avatar' in line:
        output.append('#### Use-Case 2.2: Chọn và Crop Avatar (AI Auto-Crop & Manual Crop)')
        continue
    if line.startswith('### ') and 'Showcase Setup' in line:
        output.append('#### Use-Case 2.3: Trưng bày thẻ bài (Showcase Setup)')
        continue
    if line.startswith('### ') and 'Like' in line:
        output.append('#### Use-Case 2.4: Thích hồ sơ người chơi khác (Profile Likes)')
        continue
    if line.startswith('### ') and 'Gacha' in line:
        output.append('#### Use-Case 3.1: Quay thẻ Gacha (Gacha Roll)')
        continue
    if line.startswith('### ') and 'Spin' in line:
        output.append('#### Use-Case 3.2: Hiến tế thẻ bài (Gacha Spin / Card Sacrifice)')
        continue
    if line.startswith('### ') and 'Upgrade' in line:
        output.append('#### Use-Case 3.3: Nâng cấp thẻ bài (Card Upgrade FO4-Style)')
        continue
    if line.startswith('### ') and 'Collection' in line:
        output.append('#### Use-Case 3.4: Bộ sưu tập (Collection Book)')
        continue
    if line.startswith('### ') and 'Shop' in line:
        output.append('#### Use-Case 3.5: Cửa hàng (Shop)')
        continue
    if line.startswith('### ') and 'Friend' in line:
        output.append('#### Use-Case 4.1: Tìm kiếm và kết bạn (Friend Search & Request)')
        continue
    if line.startswith('### ') and 'Friend Request' in line:
        output.append('#### Use-Case 4.2: Xử lý lời mời kết bạn (Friend Request Processing)')
        continue
    if line.startswith('### ') and 'World Chat' in line:
        output.append('#### Use-Case 4.3: Chat thế giới (World Chat)')
        continue
    if line.startswith('### ') and 'Private Chat' in line:
        output.append('#### Use-Case 4.4: Nhắn tin riêng tư (Private Chat)')
        continue
    if line.startswith('### ') and 'Stage' in line:
        output.append('#### Use-Case 3.6: Cử đội hình đi thám hiểm (AFK Stage)')
        continue
    if line.startswith('### ') and 'Mailbox' in line:
        output.append('#### Use-Case 3.7: Hộp thư (Mailbox)')
        continue
    if line.startswith('### ') and 'Backup' in line:
        output.append('#### Use-Case 3.8: Sao lưu và khôi phục dữ liệu (Backup & Restore)')
        continue
    if line.startswith('### ') and 'Streak' in line:
        output.append('#### Use-Case 4.5: Duy trì chuỗi tương tác (Couple Streak)')
        continue
    if line.startswith('### ') and 'Gift' in line:
        output.append('#### Use-Case 4.6: Tặng quà (Gift)')
        continue
    if line.startswith('### ') and 'Delete' in line:
        output.append('#### Use-Case 1.5: Xóa tài khoản (Delete Account)')
        continue
    # Skip PlantUML diagrams
    if '@startuml' in line or '@enduml' in line or 'skinparam' in line or 'left to right' in line:
        continue
    # Skip original phase headers
    if line.startswith('# ') and 'PHASE' in line:
        continue
    # Skip "Sơ đồ Use-case Tổng quát"
    if 'Sơ đồ Use-case' in line and ('PlantText' in line or 'PlantUML' in line or 'copy' in line):
        continue
    
    output.append(line)

output.append('')
output.append('---')
output.append('')

# ===== 3.8 Algorithms =====
output.append('## 3.8. THUẬT TOÁN CỐT LÕI')
output.append('')

algo_lines = algo.split('\n')
skip_header = True
for line in algo_lines:
    if skip_header:
        if line.startswith('---'):
            skip_header = False
        continue
    # Transform headings
    if line.startswith('## 1.') or line.startswith('## 1 '):
        output.append('### 3.8.1. Thuật toán Nâng cấp Thẻ bài (FO4-style Card Upgrade with Pessimistic Locking)')
        continue
    if line.startswith('## 2.') or line.startswith('## 2 '):
        output.append('### 3.8.2. Thuật toán Gacha sinh số ngẫu nhiên từ Nhiễu khí quyển')
        continue
    if line.startswith('## 3.') or line.startswith('## 3 '):
        output.append('### 3.8.3. Thuật toán Đồng bộ hóa Dữ liệu Delta (Delta Sync Algorithm)')
        continue
    if line.startswith('## 4.') or line.startswith('## 4 '):
        output.append('### 3.8.4. Thuật toán Bộ đệm LRU (Least Recently Used) và Cơ chế Lazy Loading Hình ảnh')
        continue
    if line.startswith('## 5.') or line.startswith('## 5 '):
        output.append('### 3.8.5. Cơ chế Xử lý Tin nhắn Thời gian thực (Real-time Pub/Sub Message Broker)')
        continue
    if line.startswith('## 6.') or line.startswith('## 6 '):
        output.append('### 3.8.6. Thuật toán Gói quà (Gift Distribution Algorithm)')
        continue
    if line.startswith('## 7.') or line.startswith('## 7 '):
        output.append('### 3.8.7. Thuật toán Cử đội hình (Auto AFK Stage Team Composition)')
        continue
    if line.startswith('# ') and 'PHASE 3' in line:
        continue
    
    output.append(line)

output.append('')
output.append('---')

# Write to file
content = '\n'.join(output)
with open('raw-doc/03_chuong_3_phan_tich_thiet_ke.md', 'wb') as f:
    f.write(content.encode('utf-8'))

print(f'Written: {len(content)} chars, {len(output)} lines')
print('First line:', output[0])
print('Last line:', output[-1])

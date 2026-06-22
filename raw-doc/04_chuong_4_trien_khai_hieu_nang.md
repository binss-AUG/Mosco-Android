# CHƯƠNG 4. TRIỂN KHAI HỆ THỐNG VÀ ĐÁNH GIÁ HIỆU NĂNG

Dựa trên thiết kế hệ thống đã trình bày ở Chương 3, chương này mô tả cấu hình triển khai thực tế, quy trình vận hành, cũng như các tối ưu hiệu năng đã được áp dụng trong dự án Mosco.

\---

## 4.1. CẤU HÌNH HỆ THỐNG

### 4.1.1. Cơ chế Cấu hình (Configuration Mechanism)

Cấu hình Backend Mosco được quản lý qua 2 tầng:

1. **`application.properties`** — chứa các cấu hình cố định: HikariCP pool, compression, graceful shutdown, batch size, file upload, mail SMTP template. Spring Boot load file này tự động từ `classpath:`.
2. **`.env`** — chứa secret/credential (DB\_PASS, JWT\_SECRET, API keys), được nạp chồng lên `Environment` qua thư viện `spring-dotenv` (`me.paulschwarz:spring-dotenv:4.0.0`) lúc runtime. Các biến trong `.env` override giá trị default trong `application.properties` nhờ `${VAR\_NAME:default}` syntax.

Luồng nạp cấu hình:

```
start\_server.bat
  └─ gradlew bootRun
     └─ Spring Boot starts Server.java
        ├─ application.properties (classpath) ─── base config (pool, compress, batch, ...)
        ├─ spring-dotenv đọc server/.env ───────── override biến secret
        ├─ Spring DataSource auto-config ───────── DB\_HOST, DB\_PORT, DB\_NAME, ...
        ├─ Java MailSender auto-config ─────────── MAIL\_USER, MAIL\_PASS
        ├─ Hibernate ddl-auto=update
        └─ @Value annotations resolve từ Environment
```

### 4.1.2. File .env và Danh sách Biến Cấu hình

|Biến|Mục đích|Class sử dụng|
|-|-|-:|
|DB\_HOST, DB\_PORT, DB\_NAME, DB\_USER, DB\_PASS|Kết nối MySQL|`DatabaseBackupService`, Spring auto-config|
|JWT\_SECRET, JWT\_EXPIRATION|JWT token|`JwtUtil`|
|ADMIN\_SECRET|Admin dashboard|`AdminController`|
|ASSET\_DATA\_DIR|Thư mục dữ liệu asset|`ConfigController`, `AssetController`|
|OBJEKT\_API\_URL|URL cào dữ liệu thẻ bài|`AssetManagementService`|
|MAIL\_USER, MAIL\_PASS|Gmail SMTP|`AuthService`, Spring auto-config|
|GEMINI\_API\_KEY|Google Gemini|`GeminiApiService`|
|OPENROUTER\_API\_KEY|OpenRouter LLM|`GeminiApiService`|

### 4.1.3. Các lớp Cấu hình Java (@Configuration)

**WebSocketConfig.java:** Cấu hình STOMP over WebSocket:

* Prefix broker: `/topic`
* App prefix: `/app`
* Endpoint: `/ws-mosco` (hỗ trợ SockJS)
* Interceptor: `WebSocketRateLimitInterceptor`

**AsyncConfig.java:** Thread pool cho tác vụ bất đồng bộ:

* Core pool: 10
* Max pool: 30
* Queue capacity: 5000

**FilterConfig.java:** Chuỗi Filter HTTP:

* `RateLimitFilter` (order=1) — `/api/gacha/\*`
* `JwtAuthFilter` (order=2) — `/api/gacha/\*`, `/api/user/\*`, `/api/inventory/\*`, `/api/upgrade/\*`, etc.

### 4.1.4. Hồ sơ Môi trường (Spring Profile)

* **Mặc định (không profile):** Dùng MySQL production, config từ `.env`.
* **H2 Profile** (`--spring.profiles.active=h2`): Dùng H2 file-based để test đơn lẻ:

```
  spring.datasource.url=jdbc:h2:file:./data/moscodb;DB\_CLOSE\_DELAY=-1
  spring.h2.console.enabled=true
  spring.h2.console.path=/h2-console
  ```

\---

## 4.2. QUY TRÌNH BUILD VÀ VẬN HÀNH

### 4.2.1. Build Backend

Server sử dụng Gradle (Spring Boot Plugin) với JDK 21:

```
cd server
gradlew bootJar --no-daemon
```

Output: `server/build/libs/mosco-0.0.1-SNAPSHOT.jar`

Cấu hình JVM trong `build.gradle`:

```groovy
bootRun {
    jvmArgs = \["-Xmx1024m", "-Xms512m"]
}
```

### 4.2.2. Khởi động Hệ thống

**Backend:**

```
server/start\_server.bat
```

Script này đặt codepage UTF-8, chuyển vào thư mục `server/` và chạy `gradlew bootRun`. Spring Boot tự động nạp `.env` và kết nối MySQL.

**Python Sidecar:**

```
tools/rag\_sidecar/start.bat
```

Chạy FastAPI trên port 5001, phục vụ RAG ETL endpoint.

**Client Android:**

```
client/build\_and\_run\_app.bat
```

Build APK qua Gradle và cài đặt lên Emulator.

### 4.2.3. Cấu trúc Gradle

|File|Mô tả|
|-|-|
|`server/build.gradle`|Spring Boot 3.4.2, Java 21, dependencies|
|`server/settings.gradle`|Project name|
|`server/gradle/gradle-daemon-jvm.properties`|JVM config cho Gradle daemon|
|`server/gradle/wrapper/gradle-wrapper.properties`|Gradle wrapper version|

### 4.2.4. Các Script Hỗ trợ

|Script|Mô tả|
|-|-|
|`scripts/run\_setup.bat`|Thiết lập môi trường Windows lần đầu|
|`scripts/mosco\_launcher.bat`|Launch tổng thể backend + sidecar|
|`tools/reset\_server\_data.bat`|Reset MySQL + database.json về trạng thái ban đầu|

\---

## 4.3. TỐI ƯU HIỆU NĂNG

### 4.3.1. Đa tầng Cache (Multi-layer Caching)

Hệ thống áp dụng 3 tầng cache phía Client để đạt tốc độ hiển thị kho đồ tức thời:

|Tầng|Vị trí|Mục đích|Tốc độ|
|-|-|-:|-:|
|RAM Cache|`DatabaseLoader.cachedUserInventory` (HashMap)|Hiển thị danh sách thẻ ngay lập tức|\~0ms|
|Room SQLite|App Database (File .db)|Dữ liệu cá nhân, tra cứu offline|\~5-10ms|
|JSON File|`database.json` (Internal Storage)|Master Data 20,000+ thẻ, nạp khi khởi động|\~50ms (parse)|

Luồng đọc: RAM Cache → Room DB → JSON File (nếu miss). Chỉ gọi API Server khi cache hoàn toàn không có dữ liệu.

### 4.3.2. Tối ưu Băng thông Hình ảnh (Cloudflare WebP + Glide)

* OkHttp Interceptor trong `ApiClient.java` tự động ép header `Accept: image/webp` cho mọi request đến Cloudflare CDN.
* Glide 4.15.1 cache bitmap qua `DiskCache` + `BitmapPool`, tái sử dụng bộ nhớ, giảm GC.
* Kết quả: Giảm ảnh gốc 2-3MB xuống \~100-200KB WebP, chống OOM trên Android 9 Emulator RAM 2GB.

### 4.3.3. Ghi dữ liệu theo Lô (Batch Processing)

* **ETL Pipeline (Server):** `saveAllAndFlush(200)` — gom 200 thẻ bài mỗi lô, giảm số lần commit xuống MySQL.
* **Metadata Sync (Client):** `masterObjetDao().insertAll(entities)` — batch insert toàn bộ master data vào Room.
* **Backup:** WorkManager chạy ngầm, ép `PRAGMA wal\_checkpoint(TRUNCATE)` trước khi copy file, upload multipart lên server.

### 4.3.4. Xử lý Bất đồng bộ và Luồng (Async \& Threading)

* **`AsyncConfig.java`**: Cấu hình `@EnableAsync` + `ThreadPoolTaskExecutor` (core=10, max=30, queue=5000) cho tác vụ nền.
* **Daemon Thread TRNG**: `ChaosTheoryHelper` dùng `ScheduledExecutorService` với Daemon thread, re-seed từ random.org mỗi 10 phút, không block luồng request.
* **Background JSON Parse**: `DatabaseLoader` parse file JSON hàng chục MB trên Background thread, tránh ANR.

### 4.3.5. Giới hạn Tốc độ (Rate Limiting \& Filter Chain)

* **`WebSocketRateLimitInterceptor`**: Giới hạn số message/giây trên kênh STOMP, chặn spam chat.
* **`RateLimitFilter`**: Filter HTTP giới hạn request tới `/api/gacha/\*`.
* **ABS Fling Brakes (Client)**: `ViewUtils.limitFlingVelocity()` — giới hạn tốc độ cuộn RecyclerView ở 4000 pixel/giây, giảm tải Glide + GPU.

### 4.3.6. Tối ưu Database

* **Indexes:** Bảng `gacha\_history` có index trên `user\_id` và `rolled\_at`.
* **Pessimistic Locking:** Chỉ khóa dòng (`findWithLockById` = `SELECT ... FOR UPDATE`), không khóa bảng.
* **Dictionary Cache HashMap:** Chống N+1 Query khi ETL 20,000 thẻ — cache `Member`, `Season`, `CardClass` trong `EtlService`.
* **Pre-compiled Regex:** `IMAGE\_ID\_PATTERN` là `static final Pattern`, biên dịch một lần.

### 4.3.7. Tổng hợp Chỉ số Hiệu năng

|Chỉ số|Giá trị|Phương pháp|
|-|-:|-|
|Thời gian khởi động app (cold start)|\~1.5 giây|SplashActivity + pre-fetch|
|Hiển thị danh sách 20,000 thẻ|\~0ms (tức thời)|Local-First + RAM Cache|
|Tải ảnh WebP qua Cloudflare|\~100-200KB/ảnh|Glide + OkHttp Interceptor|
|Batch ETL 20,000 thẻ vào MySQL|\~500ms|saveAllAndFlush(200)|
|Re-seed TRNG (background)|\~200ms, không block|ChaosTheoryHelper, 10 phút/lần|
|Crop ảnh ML Kit (on-device)|\~50ms|Google ML Kit Face Detection|
|RAM Emulator tối thiểu|2GB|Mục tiêu thiết kế|

\---

## 4.4. KẾT QUẢ TRIỂN KHAI GIAO DIỆN (UI/UX)

Giao diện người dùng của hệ thống Mosco được xây dựng theo phong cách **Modern Flat Design** kết hợp Dark Mode mặc định. Mục tiêu thiết kế là mang lại trải nghiệm thị giác cao cấp, tối giản các chi tiết thừa để làm nổi bật hình ảnh của các thẻ bài sưu tập, đồng thời tối ưu hóa luồng thao tác người dùng (UX).

**4.4.1. Nhóm màn hình Xác thực và Khởi tạo**
Hệ thống cung cấp luồng định danh mượt mà, hỗ trợ đăng nhập truyền thống và liên kết mạng xã hội (Google/Discord). Sau khi xác thực thành công, người dùng mới sẽ được điều hướng đến màn hình thiết lập tên định danh trong game.
*[CHÈN HÌNH 4.1: Màn hình Đăng nhập (Login) và Màn hình Khởi tạo tên nhân vật]*

**4.4.2. Nhóm màn hình Tính năng Cốt lõi (Gameplay & Inventory)**
Đây là trung tâm của ứng dụng. Màn hình kho đồ (Inventory) được tối ưu hóa hiển thị hàng ngàn thẻ bài nhờ cơ chế tải ảnh WebP và lưới động. Màn hình Gacha và Nâng cấp thẻ được thiết kế với các hiệu ứng lật thẻ 3D và thông báo kết quả trực quan nhằm tăng tính hấp dẫn.
*[CHÈN HÌNH 4.2: Màn hình Kho đồ cá nhân (Inventory)]*
*[CHÈN HÌNH 4.3: Màn hình Quay thẻ ngẫu nhiên (Gacha Animation) và Màn hình Nâng cấp thẻ (FO4 Style)]*

**4.4.3. Nhóm màn hình Tương tác đa phương tiện (3D & AR)**
Trải nghiệm sưu tập được nâng tầm thông qua màn hình Objekt Viewer, cho phép người dùng xoay thẻ 3D 360 độ, chiêm ngưỡng hiệu ứng ánh sáng (Glow), và đặc biệt là tính năng Camera AR để chèn thẻ bài ảo vào không gian thực tế.
*[CHÈN HÌNH 4.4: Màn hình tương tác thẻ 3D (Objekt Viewer) và Màn hình chụp ảnh AR Camera]*

**4.4.5. Nhóm màn hình Tương tác Xã hội và Trợ lý AI**
Hệ thống cung cấp không gian giao tiếp thời gian thực cho người chơi thông qua World Chat. Nổi bật nhất là giao diện trò chuyện với Trợ lý ảo AI (RAG Assistant) có khả năng streaming text mượt mà như các mô hình AI hiện đại.
*[CHÈN HÌNH 4.5: Màn hình Kênh Chat Thế Giới và Màn hình Chat với Trợ lý AI]*
*[CHÈN HÌNH 4.6: Bảng xếp hạng (Leaderboard) vinh danh người chơi]*

\---

## 4.5. HẠN CHẾ VÀ HƯỚNG PHÁT TRIỂN

### 4.5.1. Hạn chế Hiện tại

* **Chưa có Caching Framework tập trung:** Không dùng Redis, Memcached cho server-side cache ngoài HashMap tự quản.

### 4.5.2. Hướng phát triển

\---

*Tài liệu này được xây dựng dựa trên mã nguồn thực tế của dự án Mosco.*


# CHƯƠNG 2. CƠ SỞ LÝ THUYẾT VÀ CÔNG NGHỆ ÁP DỤNG

Để giải quyết bài toán đặt ra ở Chương 1, hệ thống Mosco được xây dựng dựa trên sự kết hợp của nhiều nền tảng công nghệ và kiến trúc phần mềm tiên tiến. Chương này trình bày các khái niệm lý thuyết cốt lõi và lý do lựa chọn các công nghệ này (Tech-Stack) thay vì các giải pháp truyền thống.

### 2.1. Nền tảng Kiến trúc Hệ thống

**2.1.1. Kiến trúc Ưu tiên cục bộ (Local-First Architecture)**
*   **Khái niệm:** Local-First Architecture là mô hình thiết kế mà trong đó, Cơ sở dữ liệu cục bộ (Local Database) trên thiết bị người dùng được đóng vai trò là Nguồn dữ liệu chính (Primary Source of Truth). Mọi thao tác đọc/ghi của giao diện đều lấy từ Local Database, trong khi quá trình đồng bộ với máy chủ (Cloud) diễn ra ngầm ở luồng phụ.
*   **Lý do áp dụng:** So với mô hình Cloud-First truyền thống (mở kho đồ phải gọi API và đợi Loading), Local-First loại bỏ hoàn toàn độ trễ mạng (Zero-latency). Người chơi có thể cuộn danh sách 20.000 thẻ bài mượt mà ngay cả khi đứt kết nối internet, đồng thời giảm tải 90% băng thông cho máy chủ Mosco.

*[CHÈN HÌNH 2.1: Sơ đồ minh họa Kiến trúc Local-First. Trực quan: Mô tả luồng mũi tên 2 chiều giữa "Mobile UI" và "Local Database" nét liền đậm (Tốc độ cao), trong khi luồng từ "Local Database" lên "Cloud Server" là nét đứt (Sync ngầm chạy nền)]*

**2.1.2. Kiến trúc Máy chủ Nguyên khối hướng Mô-đun (Modular Monolith)**
*   **Khái niệm:** Là mô hình kiến trúc phần mềm vận hành trên một quy trình duy nhất (Monolith), nhưng mã nguồn bên trong được chia tách rạch ròi thành các mô-đun độc lập (như AuthModule, GachaModule) giao tiếp qua Interface thay vì liên kết chéo (tight-coupling).
*   **Lý do áp dụng:** Thay vì áp dụng Microservices (vốn gây tốn kém chi phí gọi API nội bộ và quản lý Transaction phân tán phức tạp), Modular Monolith trên Spring Boot cho phép tốc độ gọi hàm tính bằng nano-giây (In-memory) và xử lý tính toàn vẹn giao dịch (ACID) cực kỳ an toàn, rất phù hợp với nghiệp vụ đập thẻ/Gacha khắt khe của dự án.

*[CHÈN HÌNH 2.2: Sơ đồ khối (Block Diagram) của Modular Monolith. Trực quan: Vẽ 1 hình chữ nhật lớn (Đại diện cho 1 JVM/Server), bên trong chứa các khối hình chữ nhật nhỏ (Auth Module, Gacha Module, Inventory Module). Các khối nhỏ này liên kết với nhau bằng các mũi tên gọi hàm nội bộ (In-memory calls)]*

### 2.2. Công nghệ phía Máy chủ (Backend Technologies)

**2.2.1. Hệ sinh thái Spring Boot 3 và Java 21**
*   **Khái niệm:** Spring Boot là bộ framework Java mã nguồn mở, giúp xây dựng các ứng dụng độc lập, cấp độ doanh nghiệp với cấu hình tối thiểu.
*   **Lý do áp dụng:** Spring Boot cung cấp các annotation mạnh mẽ như `@Transactional`, `@Scheduled` và tích hợp sẵn Hibernate (Spring Data JPA), giúp đẩy nhanh quá trình phát triển các API bảo mật (JWT) và quản lý luồng dữ liệu an toàn.

**2.2.2. Hệ quản trị CSDL MySQL & Cơ chế Khóa bi quan (Pessimistic Locking)**
*   **Khái niệm:** MySQL là hệ quản trị cơ sở dữ liệu quan hệ (RDBMS) đáng tin cậy. Trong MySQL, Pessimistic Locking (Khóa bi quan) là kỹ thuật sử dụng lệnh `SELECT ... FOR UPDATE` để khóa cứng bản ghi vật lý ngay khi vừa truy vấn, ngăn các luồng khác can thiệp cho đến khi giao dịch kết thúc.
*   **Lý do áp dụng:** Trong các tính năng Nâng cấp thẻ bài, người dùng có thể dùng Auto-Click để tạo ra lỗi Tương tranh (Race Condition). Nếu dùng Khóa lạc quan (Optimistic Lock), hệ thống sẽ gặp rất nhiều lỗi 409 Conflict. Do đó, Mosco bắt buộc dùng Khóa bi quan để đảm bảo không có bất kỳ thẻ bài nào bị nhân bản trái phép (Double Spending).

*[CHÈN HÌNH 2.3: Sơ đồ luồng khóa bi quan (Pessimistic Locking). Trực quan: Vẽ 2 User cùng trỏ vào 1 thẻ bài. User 1 đi vào trước, có biểu tượng cái Ổ KHÓA khóa thẻ bài lại. User 2 đi vào sau, chạm phải ổ khóa bị dội ngược lại hoặc có biểu tượng Đứng chờ (Waiting clock)]*

**2.2.3. Trình sinh số ngẫu nhiên thực sự (TRNG - True Random Number Generator)**
*   **Khái niệm:** Khác với Pseudo-Random (PRNG - sinh số giả ngẫu nhiên dựa trên thuật toán và thời gian máy), TRNG là phương pháp trích xuất số ngẫu nhiên từ các hiện tượng hỗn loạn của vật lý tự nhiên (như nhiễu điện từ, nhiễu khí quyển).
*   **Lý do áp dụng:** Để đảm bảo tính minh bạch tuyệt đối của cơ chế Quay Gacha, Mosco không dùng `Math.random()`. Thay vào đó, máy chủ định kỳ lấy số ngẫu nhiên từ API Nhiễu khí quyển, sau đó XOR với `System.nanoTime()` làm hạt giống (seed). Điều này giúp tỉ lệ rớt thẻ chuẩn xác và không thể bị hacker dự đoán chu kỳ.

**2.2.4. Giao thức STOMP qua WebSocket**
*   **Khái niệm:** WebSocket cung cấp kết nối TCP hai chiều liên tục. STOMP (Simple Text Oriented Messaging Protocol) là giao thức định tuyến bản tin chạy trên nền WebSocket, áp dụng mô hình Pub/Sub (Publisher/Subscriber).
*   **Lý do áp dụng:** Thay vì dùng HTTP Polling (Client liên tục hỏi Server "có tin nhắn mới không?"), STOMP over WebSocket cho phép Server chủ động đẩy (Push) tin nhắn Chat trực tiếp xuống đúng các máy Client đang đăng ký theo dõi kênh (`/topic` hoặc `/queue`), giảm thiểu lãng phí tài nguyên mạng.

### 2.3. Công nghệ phía Máy khách (Mobile Frontend Technologies)

**2.3.1. Android Native (Java) và Mô hình MVVM**
*   **Khái niệm:** MVVM (Model - View - ViewModel) là mẫu kiến trúc phân tách rạch ròi giữa giao diện (View) và logic dữ liệu (ViewModel). 
*   **Lý do áp dụng:** Khi thiết bị Android bị xoay màn hình hoặc ẩn ứng dụng, View có thể bị hệ điều hành tiêu hủy và tạo lại. Nhờ MVVM, ViewModel vẫn sống độc lập trên RAM, giữ nguyên trạng thái dữ liệu (ví dụ: đang loading Gacha). Khi View phục hồi, nó lập tức cập nhật lại trạng thái mà không cần gọi lại API, chống rò rỉ bộ nhớ (Memory Leak) hiệu quả. Lựa chọn Android Native thay vì React Native giúp ứng dụng tận dụng tối đa RAM và xử lý đa luồng tốt hơn trên các môi trường giả lập (Emulator).

*[CHÈN HÌNH 2.4: Biểu đồ Vòng đời (Lifecycle) của MVVM trên Android. Trực quan: Vẽ vòng đời của View (bị hủy và tạo lại khi xoay ngang/dọc điện thoại), trong khi khối ViewModel ở bên cạnh vẫn đứng im một chỗ và duy trì đường viền bao bọc trạng thái dữ liệu (Data State)]*

**2.3.2. Cơ sở dữ liệu Room SQLite (Local Cache)**
*   **Khái niệm:** Room là một thư viện ORM (Object-Relational Mapping) của Google, cung cấp lớp lang trừu tượng bọc ngoài SQLite cục bộ trên thiết bị.
*   **Lý do áp dụng:** Room là trái tim của kiến trúc Local-First. Nó chuyển hóa các câu truy vấn SQL thô thành các luồng quan sát trực tiếp (LiveData). Mỗi khi có dữ liệu đồng bộ từ Server về, Room tự động đẩy thông báo lên giao diện để render cập nhật thẻ bài mà không cần lập trình viên can thiệp thủ công.

**2.3.3. Học máy tại biên (Edge ML) với Google ML Kit**
*   **Khái niệm:** ML Kit là bộ SDK của Google đưa các mô hình học máy nhúng trực tiếp vào thiết bị di động, tính toán bằng chip (CPU/NPU) của điện thoại thay vì đám mây.
*   **Lý do áp dụng:** Mosco sử dụng module Face Detection (Nhận diện khuôn mặt) của ML Kit để tự động tính toán trọng tâm khuôn mặt khi người dùng tải ảnh Avatar lên. Việc xử lý Offline (On-device) giúp thao tác cắt ảnh (Crop) diễn ra chỉ trong 50 mili-giây, không tiêu tốn băng thông, không chịu độ trễ mạng và đảm bảo quyền riêng tư tuyệt đối cho dữ liệu ảnh cá nhân.

*[CHÈN HÌNH 2.5: Minh họa công nghệ Google ML Kit. Trực quan: Tìm một bức ảnh minh họa có khuôn mặt người, trên mặt có vẽ các đường viền Bounding Box hình vuông bao quanh mắt, mũi, miệng. Ảnh này chứng minh khả năng định vị tọa độ để tự động căn giữa khung hình Avatar]*

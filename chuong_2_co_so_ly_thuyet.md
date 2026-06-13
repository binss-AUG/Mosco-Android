# CHƯƠNG 2. CƠ SỞ LÝ THUYẾT

### 2.1. Nền tảng Kiến trúc Phần mềm
**2.1.1. Kiến trúc Ưu tiên cục bộ (Local-First Architecture)**
Local-First Architecture là một mô hình thiết kế phần mềm trong đó cơ sở dữ liệu cục bộ (trên thiết bị của người dùng) được ưu tiên sử dụng làm nguồn dữ liệu chính (Primary Data Source) cho giao diện người dùng. Thay vì phải chờ đợi phản hồi từ máy chủ (Server) qua mạng internet, ứng dụng có thể đọc, ghi và hiển thị dữ liệu tức thời từ thiết bị, mang lại trải nghiệm độ trễ bằng không (Zero-latency). Quá trình đồng bộ hóa dữ liệu với Server sẽ được thực thi ngầm ở các luồng nền (Background Threads) và tự động giải quyết xung đột khi có kết nối mạng. Kiến trúc này đặc biệt thiết yếu đối với các ứng dụng chứa khối lượng tài sản kỹ thuật số lớn nhằm giảm tải thắt cổ chai băng thông.

**2.1.2. Mô hình MVVM (Model - View - ViewModel) trên Android**
MVVM là mẫu kiến trúc (Architectural Pattern) được Google khuyến nghị sử dụng cho việc phát triển ứng dụng Android gốc (Native). Mô hình này phân tách mã nguồn thành ba thành phần độc lập:
*   **Model:** Chịu trách nhiệm quản lý dữ liệu và logic nghiệp vụ, giao tiếp trực tiếp với cơ sở dữ liệu cục bộ (Room) hoặc API.
*   **View:** Lớp giao diện người dùng (Activity, Fragment), hoàn toàn không chứa logic xử lý dữ liệu.
*   **ViewModel:** Đóng vai trò cầu nối, giữ trạng thái dữ liệu (State) và tồn tại độc lập với vòng đời của View. MVVM giúp ứng dụng Android không bị mất hoặc rò rỉ dữ liệu khi cấu hình thiết bị thay đổi (ví dụ: xoay màn hình).

### 2.2. Xử lý Tương tranh và Quản lý Cơ sở dữ liệu
**2.2.1. Tình trạng Tương tranh (Race Condition)**
Race Condition là một rủi ro hệ thống xảy ra trong môi trường đa luồng (Multi-threading), khi hai hoặc nhiều tiến trình cùng đọc và cố gắng thay đổi một bản ghi dữ liệu tại cùng một thời điểm. Trong các hệ thống Gacha và Nâng cấp vật phẩm, nếu không xử lý Race Condition, người dùng có thể lợi dụng độ trễ của mạng để gửi nhiều yêu cầu cùng lúc (Double-spending), dẫn đến việc nhân bản tài nguyên trái phép hoặc làm hỏng tính toàn vẹn dữ liệu.

**2.2.2. Cơ chế Khóa bi quan (Pessimistic Locking)**
Để giải quyết bài toán Race Condition, cơ chế Khóa bi quan (Pessimistic Locking) được áp dụng tại tầng cơ sở dữ liệu (MySQL). Khi một giao dịch (Transaction) như "Nâng cấp thẻ bài" bắt đầu, khóa bi quan sẽ khóa (Lock) các bản ghi thẻ bài liên quan, ngăn chặn mọi tiến trình khác đọc hoặc ghi lên các thẻ này cho đến khi giao dịch hiện tại hoàn tất và giải phóng khóa. Mặc dù làm giảm đôi chút tốc độ xử lý song song, nhưng đây là giải pháp duy nhất đảm bảo tính nhất quán và an toàn tuyệt đối cho các hệ thống giao dịch tài sản số.

### 2.3. Các công nghệ và Thuật toán hỗ trợ
**2.3.1. Sinh số ngẫu nhiên thực sự (True Random Number Generator - TRNG)**
Đa số các ngôn ngữ lập trình hiện nay sử dụng Pseudo-Random Number Generator (PRNG) - thuật toán sinh số giả ngẫu nhiên dựa trên một giá trị khởi tạo (seed). Nhược điểm của PRNG là có thể bị dự đoán. Để đảm bảo tính minh bạch trong cơ chế quay Gacha, hệ thống áp dụng TRNG thông qua nhiễu khí quyển (Atmospheric Noise). Đây là nguồn dữ liệu ngẫu nhiên sinh ra từ các hiện tượng vật lý tự nhiên không thể đoán trước, giúp kết quả luôn mang tính ngẫu nhiên thuần túy (True Entropy).

**2.3.2. Google ML Kit (Machine Learning Kit)**
Google ML Kit là một SDK cung cấp các mô hình học máy (Machine Learning) chạy trực tiếp trên thiết bị di động (On-device ML). ML Kit xử lý dữ liệu hình ảnh (Face Detection) mà không cần gửi dữ liệu lên máy chủ, đảm bảo tốc độ phản hồi nhanh và bảo vệ quyền riêng tư. Công nghệ này được ứng dụng để tự động tính toán tọa độ khuôn mặt, hỗ trợ tính năng cắt ảnh đại diện (Smart Crop) tự động, giảm thiểu băng thông lưu trữ.

**2.3.3. Giao thức truyền tải thời gian thực (WebSocket & STOMP)**
Khác với giao thức HTTP truyền thống, WebSocket duy trì một kết nối TCP hai chiều liên tục giữa Client và Server. STOMP (Simple Text Oriented Messaging Protocol) là giao thức định dạng tin nhắn chạy trên nền WebSocket, cung cấp cơ chế định tuyến (Pub/Sub) hiệu quả cho các tính năng tương tác mạng xã hội như Chat toàn cầu hay cập nhật chuỗi trạng thái trực tuyến.

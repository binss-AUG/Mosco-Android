# CHƯƠNG 1. TỔNG QUAN BÀI TOÁN VÀ KHẢO SÁT HIỆN TRẠNG

### 1.1. Bối cảnh hình thành đề tài

Trong thập kỷ qua, ngành công nghiệp phần mềm giải trí đã chứng kiến sự trỗi dậy mạnh mẽ của mô hình "Gacha" (cơ chế rút thăm ngẫu nhiên nhận vật phẩm) và trào lưu sưu tập tài sản kỹ thuật số (Digital Collection). Các ứng dụng và trò chơi áp dụng mô hình này, từ những tựa game nhập vai thế giới mở (Genshin Impact, Honkai: Star Rail) cho đến các tựa game thẻ bài thể thao (FIFA Online 4, eFootball), đã tạo ra những cộng đồng người chơi khổng lồ, đi kèm với khối lượng giao dịch vật phẩm ảo cực kỳ lớn.
Sự dịch chuyển từ sở hữu tài sản vật lý (ví dụ: thẻ bài giấy Yu-Gi-Oh, thẻ bài Pokemon) sang tài sản kỹ thuật số đỏi hỏi các hệ thống công nghệ thông tin phải có khả năng quản lý và lưu trữ hàng triệu, thậm chí hàng tỷ bản ghi dữ liệu riêng biệt. Người dùng ngày nay không chỉ muốn sở hữu vật phẩm mà còn yêu cầu trải nghiệm tương tác mượt mà, khả năng cá nhân hóa (Showcase), và tính minh bạch tuyệt đối trong các cơ chế quay thưởng ngẫu nhiên.
Tuy nhiên, bối cảnh này sinh ra một nghịch lý về mặt công nghệ: Để mang lại trải nghiệm đồ họa sống động và kho vật phẩm đồ sộ, các ứng dụng buộc phải tải xuống một lượng dữ liệu khổng lồ (thường dao động từ 5GB đến 30GB). Điều này trực tiếp loại bỏ một bộ phận rất lớn người dùng phổ thông - những người sở hữu thiết bị di động tầm trung, máy tính cấu hình thấp, hoặc những người có thói quen sử dụng trình giả lập (Emulator) để treo máy.

### 1.2. Khảo sát hiện trạng các hệ thống tương đương trên thị trường

Để đánh giá chính xác bài toán, nhóm nghiên cứu đã tiến hành khảo sát các mô hình ứng dụng Gacha và Collection phổ biến trên thị trường hiện nay. Kết quả khảo sát cho thấy 3 vấn đề nổi cộm:

**1.2.1. Vấn đề "Nút thắt cổ chai" mạng (Network Bottleneck) do kiến trúc Cloud-First**
Phần lớn các ứng dụng hiện nay (điển hình như các web-app sưu tập thẻ K-pop) được thiết kế theo kiến trúc **Cloud-First**. Nghĩa là, dữ liệu gốc (Single Source of Truth) luôn nằm trên máy chủ.

* **Hệ quả:** Mỗi khi người dùng thực hiện một thao tác cơ bản như mở kho đồ (Inventory), chuyển trang danh sách thẻ bài, hoặc xem chi tiết hồ sơ, ứng dụng đều phải phát sinh một hoặc nhiều HTTP Request lên máy chủ. Ở quy mô một vài trăm người dùng, hệ thống hoạt động ổn định. Tuy nhiên, trong các sự kiện giờ vàng (Peak Time), việc hàng chục ngàn người dùng liên tục Polling (gọi API) dẫn đến hiện tượng quá tải máy chủ (Server Overload). Về phía người dùng, họ phải liên tục chờ đợi màn hình tải (Loading Screen xoay vòng), gây ức chế nghiêm trọng.

**1.2.2. Vấn đề tràn bộ nhớ (Out of Memory - OOM) khi xử lý đa phương tiện**
Việc hiển thị danh sách thẻ bài (Card Grid) đòi hỏi tải một lượng lớn hình ảnh cùng lúc. Nếu hệ thống không có chiến lược tối ưu hóa hình ảnh (như load ảnh gốc dung lượng 2-3MB thay vì ảnh thu nhỏ Thumbnail), thiết bị Client sẽ nhanh chóng tiêu thụ hết lượng RAM (Heap Memory) được cấp phép.

* **Hệ quả:** Đối với các trình giả lập Android (thường được set cứng RAM ở mức 2GB hoặc 4GB), việc nhồi nhét hàng chục ảnh gốc vào bộ nhớ đệm (Cache) sẽ dẫn đến tình trạng Crash ứng dụng ngay lập tức do lỗi `OutOfMemoryError`.

**1.2.3. Lỗ hổng Tương tranh dữ liệu (Double Spending / Race Condition)**
Một thực trạng đáng báo động ở các ứng dụng thẻ bài nghiệp dư hoặc các hệ thống không được thiết kế chuyên sâu về giao dịch là việc bỏ qua cơ chế khóa cơ sở dữ liệu (Database Locking).

* **Hệ quả:** Người chơi có thể sử dụng các phần mềm tự động nhấp chuột (Auto-Clicker) hoặc cố tình tạo độ trễ mạng (Network Lag) để gửi cùng lúc hàng chục yêu cầu nâng cấp/tiêu thụ cho cùng một thẻ bài phôi. Nếu máy chủ chỉ kiểm tra điều kiện (IF) và cập nhật dữ liệu một cách tuần tự thông thường, thẻ bài phôi đó sẽ bị trừ nhiều lần (âm tài nguyên) nhưng lại mang về nhiều lần lợi ích nâng cấp. Đây là lỗ hổng "nhân bản tài sản" chết người phá vỡ toàn bộ nền kinh tế ảo của ứng dụng.

### 1.3. Tính cấp thiết của đề tài Mosco

Dựa trên những hạn chế thực tế đã khảo sát, việc xây dựng một hệ thống giải quyết đồng thời cả 3 bài toán: Tối ưu mạng, Tối ưu RAM, và Đảm bảo an toàn giao dịch là cực kỳ cấp thiết.
Dự án **Mosco** (Hệ thống Mosco Gacha \& Collection) được đề xuất không chỉ đơn thuần là một ứng dụng giải trí, mà là một sản phẩm thực hành các kỹ thuật tối ưu hóa hệ thống nâng cao. Mosco lấy tiêu chí "Chạy mượt mà trên Giả lập Android 9 RAM 2GB" làm thước đo hiệu năng cuối cùng. Để làm được điều này, hệ thống bắt buộc phải thay đổi tư duy thiết kế, từ bỏ Cloud-First để chuyển sang **Local-First Architecture** (Kiến trúc ưu tiên cục bộ), đồng thời áp dụng các chuẩn mực khắt khe nhất trong xử lý giao dịch cơ sở dữ liệu (Transaction Management).

### 1.4. Mục tiêu của đồ án

Đồ án hướng tới việc thiết kế và xây dựng thành công nền tảng Mosco với các mục tiêu cụ thể:

**1.4.1. Mục tiêu lý thuyết và nghiên cứu kỹ thuật**

* Nghiên cứu và chứng minh tính ưu việt của kiến trúc **Local-First** kết hợp cơ chế **Delta Sync** (Đồng bộ phần thừa) trong việc giảm thiểu tối đa băng thông.
* Nghiên cứu cơ chế **Pessimistic Locking (Khóa bi quan)** trong MySQL và Spring Boot để chống lại lỗ hổng Double-Spending trong hệ thống nâng cấp thẻ bài.
* Nghiên cứu ứng dụng **True Random Number Generator (TRNG - Trình sinh số ngẫu nhiên thực sự)** thông qua nhiễu khí quyển để thay thế PRNG (Sinh số giả ngẫu nhiên) truyền thống, đảm bảo tính minh bạch tuyệt đối của hệ thống Gacha.
* Tìm hiểu và ứng dụng mô hình học máy tại biên (On-device ML) thông qua **Google ML Kit** để xử lý hình ảnh trực tiếp trên thiết bị, giảm tải cho máy chủ.

**1.4.2. Mục tiêu thực tiễn và xây dựng phần mềm**

* Xây dựng thành công ứng dụng Android Native (Client) sử dụng ngôn ngữ Java thuần, kết hợp kiến trúc MVVM và Room SQLite để quản lý kho đồ cục bộ.
* Xây dựng hệ thống máy chủ Spring Boot (Backend) quản lý khối lượng dữ liệu thực thể thẻ bài.
* Tích hợp hệ thống trợ lý ảo thông minh dựa trên kỹ thuật RAG (Retrieval-Augmented Generation), kết hợp giữa mô hình ngôn ngữ lớn (LLM) và kho dữ liệu tri thức chuyên biệt.
* Xây dựng dịch vụ bổ trợ (Python Sidecar) để thực hiện các tác vụ thu thập dữ liệu và xử lý ngôn ngữ bổ trợ cho hệ thống chính.
* Triển khai bộ đệm (OkHttp Interceptor) tối ưu hóa việc tải tài nguyên hình ảnh, đảm bảo tính ổn định trên các thiết bị cấu hình thấp.

### 1.5. Đối tượng và Phạm vi nghiên cứu

* **Đối tượng nghiên cứu:** Kiến trúc hệ thống phân tán Client-Server, cơ sở dữ liệu quan hệ (RDBMS) và SQLite, các thuật toán xử lý tương tranh, tối ưu hóa bộ nhớ trên nền tảng Android.
* **Phạm vi dữ liệu:** Hệ thống chịu tải mô phỏng với tập dữ liệu Master Data lên tới hơn 20,000 thẻ bài đa dạng chủng loại.
* **Phạm vi chức năng:** Đồ án tập trung đi sâu vào logic phân bổ Gacha (TRNG), thuật toán nâng cấp thẻ bài kiểu phi tuyến tính (FO4 Style), và cơ chế đồng bộ kho đồ Local-First. Bên cạnh đó, hệ thống phát triển toàn diện các tính năng vệ tinh quan trọng: Tương tác thời gian thực (Real-time Chat, Kết đôi Streak, Gửi tặng quà qua Hòm thư), Hệ thống Đi cảnh thám hiểm (Stage/AFK), Sổ tay sưu tập (Collection Book), Hệ thống Chiến đấu (Battle Engine), và Trợ lý ảo AI. Các luồng thanh toán bằng tiền thật (Fiat Payment) được loại bỏ, thay thế bằng cơ chế tiền tệ ảo cục bộ để tập trung tối đa cho việc xử lý kỹ thuật và hiệu năng.


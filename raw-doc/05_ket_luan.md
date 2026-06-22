# PHẦN KẾT LUẬN

## 1. Kết quả đạt được

Trải qua quá trình nghiên cứu, phân tích thiết kế và triển khai thực tế, dự án **"Mosco - Ứng dụng Sưu tập Thẻ bài Vũ trụ tích hợp Trợ lý AI"** đã hoàn thành các mục tiêu ban đầu đề ra, mang đến một sản phẩm hoàn chỉnh từ Backend đến Frontend với các tính năng nổi bật:

* **Về mặt Ứng dụng (Android Client):**
    * Xây dựng thành công giao diện người dùng theo phong cách Modern Flat Design và Dark Mode, mang lại trải nghiệm thị giác cao cấp.
    * Giải quyết bài toán tải dữ liệu lớn (Big Data) ở phía Client bằng việc ứng dụng kiến trúc Local-First (Room Database + JSON Caching), giúp hiển thị mượt mà hơn 20,000 thẻ bài mà không bị độ trễ mạng.
    * Tích hợp thành công hiệu ứng tương tác 3D (Objekt Viewer), thực tế tăng cường (AR Camera) và các bộ lọc hình ảnh (Smart Face Crop).
    * Hiện thực hóa tính năng trò chuyện AI (Streaming Chat) theo thời gian thực với độ phản hồi nhanh chóng, mượt mà.

* **Về mặt Hệ thống (Server-side):**
    * Triển khai kiến trúc Spring Boot RESTful API vững chắc, áp dụng cơ chế xác thực bảo mật JWT kết hợp Spring Security.
    * Tối ưu hóa Database với chiến lược phân trang (Pagination), khóa bi quan (Pessimistic Locking) giải quyết triệt để vấn đề Race Condition trong cơ chế nâng cấp thẻ bài.
    * Xây dựng luồng ETL Pipeline tự động, có khả năng đồng bộ hàng vạn dữ liệu thẻ bài từ API bên thứ ba vào MySQL một cách an toàn và tối ưu (Batch Insert).
    * Áp dụng thành công cơ chế giao tiếp thời gian thực WebSockets cho tính năng World Chat và công nghệ Server-Sent Events (SSE) để truyền phát câu trả lời từ AI LLM (Gemini/OpenRouter).

## 2. Kiến thức và Kỹ năng thu được

Thực hiện đồ án là một cơ hội quý báu để vận dụng các kiến thức lý thuyết trên giảng đường vào một bài toán thực tế quy mô lớn. Qua đó, nhóm đã củng cố và học hỏi được nhiều kỹ năng quan trọng:
* **Kỹ năng Lập trình và Cấu trúc Hệ thống:** Làm quen và áp dụng hiệu quả các Design Pattern (MVVM, Repository Pattern), nguyên lý SOLID và Clean Architecture trong cả Java Android và Spring Boot.
* **Kỹ năng Tối ưu Hiệu năng (Performance Tuning):** Nắm bắt được cách xử lý Memory Leak, tối ưu vòng đời Activity/Fragment, quản lý đa luồng (Multithreading) và tối ưu hóa truy vấn Database.
* **Nghiên cứu Công nghệ mới:** Bước đầu tiếp cận và ứng dụng thành công công nghệ AI tạo sinh (Generative AI) vào ứng dụng di động thông qua RAG Pipeline.
* **Kỹ năng Quản lý Dự án:** Sử dụng thành thạo Git/GitHub để quản lý mã nguồn, biết cách phân tách module, lên kế hoạch và tuân thủ các quy chuẩn viết code (Coding Convention), viết commit message.

## 3. Lời kết

Dự án Mosco không chỉ dừng lại ở một ứng dụng quản lý bộ sưu tập thông thường mà còn là một hệ sinh thái nhỏ, kết hợp nhiều công nghệ hiện đại từ Mobile Development, Backend Architecture đến Artificial Intelligence. Dù vẫn còn một số hạn chế nhất định về mặt mở rộng hạ tầng Cloud hay Caching phân tán do giới hạn tài nguyên đồ án, nhưng sản phẩm đã chứng minh tính khả thi, sự ổn định và sẵn sàng cho việc nâng cấp ở tương lai. 

Những kiến thức và kinh nghiệm thực tiễn từ quá trình xây dựng Mosco sẽ là hành trang vô giá, làm nền tảng vững chắc cho sự nghiệp Kỹ sư Phần mềm (Software Engineering) của nhóm trong tương lai.

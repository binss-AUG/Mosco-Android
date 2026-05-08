# Mosco - Dự án Gacha Vũ trụ Cao cấp

Mosco là một ứng dụng di động mô phỏng game thẻ bài (Gacha) cao cấp, được xây dựng trên nền tảng Android Native với triết lý thiết kế "Quiet Luxury" và kiến trúc "Local-First". Dự án tập trung vào trải nghiệm người dùng mượt mà, giao diện mang phong cách vũ trụ (Galactic UI) và hệ thống quản lý tài nguyên tối ưu.

## Điểm Nổi Bật của Dự án

### 1. Hệ thống Typography Chuẩn hóa
Dự án sử dụng hệ thống phông chữ đa tầng để phân tách rõ rệt vai trò của từng loại thông tin:
- Mosco Luxury (Pretendard): Sử dụng cho toàn bộ phần thân văn bản, mô tả và các nhãn điều hướng. Mang lại cảm giác hiện đại và dễ đọc.
- Mosco Galactic (Orbitron): Dành riêng cho các tiêu đề module và các thành phần mang tính thương hiệu, tạo phong cách tương lai.
- Mosco Technical (Chakra Petch): Sử dụng cho các con số kỹ thuật, chỉ số thẻ bài (OVR, Level, HP, ATK) để tạo sự chính xác và chuyên nghiệp.

### 2. Kiến trúc Tài nguyên Local-First 2.0
Để đảm bảo tốc độ phản hồi 0ms, Mosco triển khai kiến trúc tài nguyên thông minh:
- Tải dữ liệu đa luồng: Sử dụng 32 luồng đồng thời để tải tài nguyên từ server ngay khi khởi động.
- Caching Hybrid: Kết hợp lưu trữ tạm thời và bộ nhớ cục bộ để hiển thị hình ảnh tức thì, sau đó tự động cập nhật bản chất lượng cao.
- Tối ưu hóa hiệu năng: Loại bỏ hoàn toàn hiện tượng lag khi cuộn danh sách thẻ bài lớn bằng cách xử lý sắp xếp và lọc ở luồng nền.
- ABS Fling Brakes: Hệ thống giới hạn tốc độ cuộn (ViewUtils) giúp kiểm soát tốc độ lướt tối đa, tránh hiện tượng overload khi render hàng ngàn hình ảnh đồng thời. Tốc độ được quản lý tập trung qua AppConfig.

### 3. Quy chuẩn Zero-Hardcoding
Toàn bộ ứng dụng đã được tái cấu trúc để không còn giá trị cứng (hardcoded) trong mã nguồn:
- Strings: 100% văn bản được quản lý tập trung trong hệ thống strings.xml để dễ dàng bản địa hóa.
- Colors: Sử dụng hệ thống màu semantic (theo vai trò) giúp thay đổi giao diện toàn cục chỉ bằng cách cập nhật token màu.
- Dimensions: Mọi khoảng cách và kích thước đều tuân thủ lưới Galactic Grid (8pt) để đảm bảo sự cân đối trên mọi thiết bị.
- AppConfig: Các hằng số hiệu năng (MAX_FLING_VELOCITY, DETAIL_DIALOG_DIM_AMOUNT) được tập trung tại một file cấu hình duy nhất, loại bỏ hoàn toàn magic numbers trong toàn bộ codebase.

### 4. Tính năng Cao cấp
- Hệ thống Gacha 3D: Hiệu ứng reveal thẻ bài 3D cho phép tương tác xoay thẻ quanh trục Y và xem mặt sau.
- Mở Pack Hàng loạt: Hỗ trợ mở đến 36 packs trong một lần yêu cầu với hiệu ứng chuyển cảnh mang tính điện ảnh.
- Đội hình Cộng hưởng (Passive Synergy): Hệ thống tự động tính toán điểm thưởng dựa trên sự kết hợp giữa các nghệ sĩ trong đội hình.
- Album Sưu tầm: Chế độ hiển thị bóng đen (Silhouette) cho thẻ chưa sở hữu và hiệu ứng kim loại (Metallic) cho thẻ hiếm.
- Smart Name Tag: Hệ thống hiển thị tên thẻ thông minh theo format "Artist [Prefix]No" (ví dụ: SeoAh D322A), tối ưu cho không gian hiển thị Grid mà vẫn đảm bảo đầy đủ thông tin định danh.
- Shimmer Skeleton đồng bộ: Hiệu ứng loading skeleton được chuẩn hóa và đồng bộ xuyên suốt tất cả các module (Inventory, Collection Objets, Album).

## Công nghệ Sử dụng

- Client: Java Android Native, Retrofit 2, OkHttp (32-Threads), Glide, Lottie Animation, Google ML Kit (Smart Face-Crop).
- Server: Java 21, Spring Boot 3.4.x, Spring Data JPA, MySQL 8.x, JWT Security.
- Thiết kế: Galactic Dark Mode, Glassmorphism, Hệ thống Font Pretendard/Orbitron/Chakra Petch.

## Cấu Trúc Dự án

- client/: Mã nguồn ứng dụng Android.
- server/: Mã nguồn backend Spring Boot.
- res/values/: Hệ thống tài nguyên (colors, strings, dimens, styles) đã được chuẩn hóa.
- res/font/: Thư viện font chữ được sử dụng trong hệ thống Typography.

## Hướng Dẫn Cài Đặt

### Phía Backend
1. Cấu hình thông số MySQL trong file application.properties.
2. Chạy lệnh gradlew bootRun để khởi động server (mặc định port 8080).

### Phía Client
1. Cập nhật địa chỉ IP của server trong strings_config.xml.
2. Build ứng dụng bằng Android Studio.

## Quy Tắc Phát Triển

- Ngôn ngữ mã nguồn: Sử dụng duy nhất ngôn ngữ Java cho phía Client.
- Ghi chú: Ghi chú bằng tiếng Việt để giải thích lý do (WHY) thực hiện logic.
- Commit: Tuân thủ định dạng type(scope): description (ví dụ: style(ui): update typography styles).

Copyright 2026 Mosco Project.

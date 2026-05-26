# PHASE 2: Bóc Tách Tính Năng & Đặc Tả Use-Case - Dự án Mosco

Tài liệu này bóc tách toàn bộ các tính năng thực tế từ mã nguồn của dự án Mosco (Client Android & Backend Spring Boot). Tài liệu đã được cập nhật dựa trên phản hồi thực tế của nhà phát triển.

---

## 1. Tính năng Đăng nhập & Đăng ký (Authentication)

### Use-Case 1.1: Đăng ký tài khoản (Sign Up)
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

### Use-Case 1.2: Đăng nhập (Sign In & Social Login)
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

### Use-Case 1.3: Quên mật khẩu & Đặt lại mật khẩu (Forgot & Reset Password)
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

### Use-Case 2.2: Chọn và Crop Avatar (AI Auto-Crop & Manual Crop)
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

### Use-Case 2.3: Trưng bày thẻ bài (Showcase Setup)
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

### Use-Case 2.4: Thích hồ sơ người chơi khác (Profile Likes)
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

### Use-Case 2.5: Khôi phục chuỗi đăng nhập (Streak Restore)
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

### Use-Case 3.2: Sổ tay sưu tầm Pokédex-style (Collection Book)
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

### Use-Case 4.1A: Quay gói thẻ bài Gacha (Gacha Roll)
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

### Use-Case 4.2: Vòng quay trao đổi thẻ bài (Gacha Spin / Card Sacrifice)
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

### Use-Case 7.1: Kết nối Couple và Kích hoạt Streak
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

### Use-Case 7.2: Trưng bày Objet trong Couple Streak
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

### Use-Case 8.1: Chat Thế giới (World Chat)
*   **Tác nhân (Actor):** Người chơi (User)
*   **Luồng xử lý chính (Main flow):**
    1.  Người dùng mở hộp chat trên màn hình chính.
    2.  Người dùng nhập tin nhắn và bấm "Gửi".
    3.  Client gửi message qua WebSocket tới đích `/app/chat.sendMessage`.
    4.  Server tiếp nhận, thực hiện lọc chống mã độc HTML (`HtmlUtils.htmlEscape`), gán timestamp hệ thống và phát (broadcast) tin nhắn tới topic chung `/topic/world`.
    5.  Tất cả các Client đang online nhận được tin nhắn và render hiển thị lên khung chat thế giới.
*   **Luồng rẽ nhánh / lỗi (Alternative/Exception flow):**
    *   *Mất kết nối WebSocket:* Client chuyển trạng thái hiển thị chat sang màu xám/báo offline, và chạy luồng tự động kết nối lại (auto-reconnect) ngầm.

### Use-Case 8.2: Chat Riêng tư & Duy trì Streak (Private Chat)
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

---

## 10. Tính năng Thám hiểm / Đi cảnh AFK (AFK Stage Expedition)

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
    4.  Server cộng tiền vàng và kim cương tích lũy được vào tài khoản người chơi.
    5.  Server giải phóng toàn bộ thẻ bài tham gia phiên thám hiểm về lại trạng thái `AVAILABLE`.
    6.  Server cập nhật trạng thái phiên thám hiểm thành `CLAIMED`.
    7.  Client nhận thông tin quà thưởng, cập nhật số dư hiển thị và mở khóa các thẻ bài trong kho đồ.

### Use-Case 10.4: Hủy bỏ thám hiểm đi cảnh giữa chừng (Abort Stage Expedition)
*   **Tác nhân (Actor):** Người chơi (User)
*   **Luồng xử lý chính (Main flow):**
    1.  Tại màn hình thám hiểm, đối với một phiên thám hiểm đang trong trạng thái chạy (`ACTIVE`), người chơi có thể chọn "Hủy bỏ" (Abort) nếu muốn thu hồi thẻ bài gấp.
    2.  Người chơi xác nhận việc hủy bỏ (sẽ không nhận được bất kỳ phần thưởng tích lũy nào).
    3.  Client gửi yêu cầu POST tới `/api/stage/abort/{userId}/{sessionId}`.
    4.  Server mở Transaction, chuyển trạng thái phiên thám hiểm sang `ABORTED`.
    5.  Server giải phóng tất cả các thẻ bài tham gia phiên thám hiểm này quay trở lại trạng thái `AVAILABLE` khả dụng trong kho đồ.
    6.  Server cập nhật DB, commit giao dịch và trả về kết quả thành công cho Client.
    7.  Client tải lại dữ liệu kho đồ, cập nhật trạng thái thẻ bài thành sẵn sàng và ẩn phiên thám hiểm đã hủy.

---

## 11. Tính năng Hòm thư & Gửi tặng (Mailbox & Gift)

### Use-Case 11.1: Nhận quà từ thư hệ thống (Mailbox Claim)
*   **Tác nhân (Actor):** Người chơi (User)
*   **Luồng xử lý chính (Main flow):**
    1.  Người dùng mở [MailboxFragment](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/fragment/MailboxFragment.java).
    2.  Client gọi API `/api/mailbox/{userId}` để tải danh sách thư.
    3.  Người dùng có thể nhấn "Nhận" từng thư hoặc nhấn "Nhận tất cả".
    4.  **Nhận từng thư:** Client gửi POST tới `/api/mailbox/claim/{mailId}`. Server dùng Pessimistic Lock khóa bức thư, cộng tài nguyên tương ứng (Coins/Diamonds) cho user, và đánh dấu thư `received = true`.
    5.  **Nhận tất cả:** Client gửi POST tới `/api/mailbox/claim-all/{userId}`. Server quét toàn bộ các thư chưa nhận kèm khóa bảo vệ, gom tổng số Coin và Diamond lại cộng 1 lần duy nhất vào user, sau đó cập nhật toàn bộ thư thành đã nhận.
    6.  Client nhận phản hồi thành công, cộng tiền trên thanh header và cập nhật trạng thái thư hiển thị.

### Use-Case 11.2: Tặng thẻ bài trực tiếp cho bạn bè (Gifting)
*   **Tác nhân (Actor):** Người chơi (User)
*   **Luồng xử lý chính (Main flow):**
    1.  Tại kho đồ hoặc profile của bạn bè, người chơi nhấn chọn "Tặng quà".
    2.  Người chơi chọn thẻ bài muốn tặng. Client hiển thị giới hạn lượt tặng trong ngày (tối đa 5 lần gửi/nhận mỗi ngày).
    3.  Người chơi nhấn xác nhận gửi. Client gửi POST tới `/api/gift/send` kèm `cardId` và `receiverId`.
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

### Use-Case 11.3: Quản lý hộp quà và lượt tặng còn lại (Gift Inbox & Daily Limits Tracking)
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

---

## 14. Tính năng Đội hình & Synergy chiến đấu (Battle Formation & Synergy)

### Use-Case 14.1: Thiết lập và lưu Đội hình chiến đấu
*   **Tác nhân (Actor):** Người chơi (User)
*   **Luồng xử lý chính (Main flow):**
    1.  Người dùng truy cập vào giao diện "Đội hình chiến đấu" (Battle Formation).
    2.  Client gọi API `GET /api/battle/formation/{userId}` để lấy đội hình 6 slot hiện tại của user. Đội hình được Server lưu trữ dưới dạng một chuỗi phân tách dấu phẩy (VD: `"1,2,null,4,null,6"` với các số là ID của thẻ bài `UserCard`).
    3.  Client parse chuỗi này thành mảng 6 phần tử tương ứng với 6 slot trên giao diện.
    4.  Người chơi nhấn vào slot bất kỳ để chọn hoặc thay đổi thẻ bài trong kho đồ.
    5.  Người chơi nhấn "Lưu Đội hình". Client gửi POST tới `/api/battle/formation/{userId}/save` kèm theo body chứa mảng 6 phần tử ID thẻ bài (chứa `null` ở các slot trống).
    6.  Server cập nhật chuỗi `activeFormation` trong bảng `users` và trả về thông báo thành công.
    7.  Client cập nhật cache và lưu cục bộ.

### Use-Case 14.2: Preview chỉ số OVR & Synergy của Đội hình
*   **Tác nhân (Actor):** Người chơi (User)
*   **Luồng xử lý chính (Main flow):**
    1.  Mỗi khi người chơi thay đổi thẻ bài trong 6 slot của Đội hình chiến đấu trên giao diện, Client lập tức kích hoạt luồng tính toán chỉ số động.
    2.  Client gửi yêu cầu POST tới `/api/battle/preview` kèm theo danh sách các ID thẻ bài trong đội hình.
    3.  Server gọi `BattleEngineService` thực hiện tính toán:
        - Tổng điểm OVR cơ bản của các thẻ bài.
        - Các chỉ số Synergy kết hợp (như cùng Season, cùng Artist, hoặc cùng Member).
        - Trả về đối tượng `BattleResponse` chứa tổng OVR và các thuộc tính kết hợp (Synergy Buff).
    4.  Client nhận phản hồi và cập nhật hiển thị chỉ số OVR tổng của đội hình cùng danh sách các buff Synergy đang hoạt động trực quan lên màn hình.

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

## 17. Tính năng Quản trị & Vận hành cho Admin (Asset Control Dashboard)

### Use-Case 17: Quản lý và vận hành tài nguyên trên Dashboard Admin
*   **Tác nhân (Actor):** Quản trị viên (Admin)
*   **Luồng xử lý chính (Main flow):**
    1.  Admin truy cập vào URL Dashboard Admin: `http://localhost:8080/admin/assets?key=ADMIN_SECRET`.
    2.  Server kiểm tra mã bảo mật `ADMIN_SECRET`. Nếu khớp, hiển thị giao diện Dashboard Dark Mode điều khiển tài nguyên.
    3.  Trên Dashboard, Admin có thể theo dõi:
        - Trạng thái đồng bộ (Sync Status: IDLE hoặc BUSY).
        - Tổng số ảnh thẻ bài đã tải về đĩa (Total Images).
        - Số lượng Sealed Bundles & Patches nén dữ liệu.
        - Nhãn thời gian của lượt đồng bộ cuối cùng (Last Sync).
    4.  **Kích hoạt Đồng bộ Thủ công:** Admin bấm nút "🚀 SYNC NOW". Server gọi API `POST /api/assets/sync` để bắt đầu cào và đồng bộ metadata từ objekt.top ở chế độ nền.
    5.  **Nén lại gói tài nguyên (Rebuild Bundles):** Admin bấm nút "📦 REBUILD BUNDLES". Server gọi API `POST /api/assets/rebuild` để nén lại toàn bộ Sealed Bundles làm mới dữ liệu cho Client tải offline.
    6.  Admin có thể theo dõi tiến trình chạy ngầm qua log được hiển thị realtime trên Dashboard từ endpoint `/api/assets/status`.
*   **Luồng rẽ nhánh / lỗi (Alternative/Exception flow):**
    *   *Sai mã bảo mật (ADMIN_SECRET):* Server từ chối truy cập và hiển thị thông báo "🔒 ACCESS DENIED — Invalid Key".

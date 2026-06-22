import re

with open('raw-doc/03_chuong_3_phan_tich_thiet_ke_rut_gon.md', 'r', encoding='utf-8') as f:
    text = f.read()

# 1. Xóa các mục trống 3.1.3, 3.1.4, 3.1.5
text = re.sub(r'\*\*3\.1\.3\. Phân rã nhóm tính năng Tài khoản \(Auth & Profile\)\*\*\n', '', text)
text = re.sub(r'\*\*3\.1\.4\. Phân rã nhóm tính năng Gameplay \(Core Logic\)\*\*\n', '', text)
text = re.sub(r'\*\*3\.1\.5\. Phân rã nhóm tính năng Xã hội \(Social & Chat\)\*\*\n', '', text)

# 2. Đổi tên các mục
text = text.replace('## 3.5. CÁC GIẢI PHÁP KỸ THUẬT CỐT LÕI', '## 3.5. CÁC GIẢI PHÁP KỸ THUẬT NỔI BẬT')
# Đổi 3.7 Thuật toán thành 3.6
text = text.replace('## 3.7. THUẬT TOÁN CỐT LÕI', '## 3.6. ĐẶC TẢ THUẬT TOÁN NGHIỆP VỤ')
text = text.replace('### 3.7.1.', '### 3.6.1.')
text = text.replace('### 3.7.2.', '### 3.6.2.')
text = text.replace('### 3.7.3.', '### 3.6.3.')
text = text.replace('### 3.7.4.', '### 3.6.4.')
text = text.replace('### 3.7.5.', '### 3.6.5.')
text = text.replace('### 3.7.6.', '### 3.6.6.')
text = text.replace('### 3.7.7.', '### 3.6.7.')

# Đổi 3.6 Các vấn đề đã biết thành 3.7
text = text.replace('## 3.6. CÁC VẤN ĐỀ ĐÃ BIẾT (Known Issues)', '## 3.7. CÁC VẤN ĐỀ TỒN ĐỌNG (KNOWN ISSUES)')
text = text.replace('### 3.6.1.', '### 3.7.1.')
text = text.replace('### 3.6.2.', '### 3.7.2.')
text = text.replace('### 3.6.3.', '### 3.7.3.')


# 3. Hoàn thiện Cơ sở dữ liệu (Mục 3.3)
db_dict = '''**3.3.3. Từ điển Dữ liệu (Data Dictionary)**

**Bảng 3.9: Chi tiết cấu trúc Bảng `users` (Tài khoản người chơi)**
| Tên cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
| :--- | :--- | :--- | :--- |
| `id` | VARCHAR(36) | PK, Not Null | Chuỗi UUID v4 định danh duy nhất User |
| `username` | VARCHAR(50) | Unique, Not Null | Tên đăng nhập hệ thống |
| `hashed_password` | VARCHAR(255) | Not Null | Mật khẩu được mã hóa an toàn bằng thuật toán BCrypt |
| `diamonds` | INT | Default 0 | Tiền tệ cao cấp (Premium Currency) để quay Gacha |
| `avatar_url` | VARCHAR(500) | Nullable | Link ảnh đại diện (Đã qua xử lý cắt ảnh ML Kit) |

**Bảng 3.10: Chi tiết cấu trúc Bảng `master_cards` (Từ điển thẻ bài gốc)**
| Tên cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
| :--- | :--- | :--- | :--- |
| `card_id` | VARCHAR(50) | PK, Not Null | Mã định danh thẻ (Ví dụ: FO4_CR7_2023) |
| `name` | VARCHAR(100) | Not Null | Tên nghệ sĩ / Cầu thủ / Nhân vật |
| `rarity` | VARCHAR(10) | Not Null | Độ hiếm của thẻ (R, SR, SSR, UR) |
| `base_ovr` | INT | Not Null | Chỉ số sức mạnh (OVR) nguyên bản ban đầu |
| `image_url` | VARCHAR(500) | Not Null | Link ảnh phân phối từ mạng CDN Cloudflare (WebP format) |

**Bảng 3.11: Chi tiết cấu trúc Bảng `user_cards` (Kho đồ cá nhân - Inventory)**
| Tên cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
| :--- | :--- | :--- | :--- |
| `id` | BIGINT | PK, Auto Increment | ID tự tăng của dòng dữ liệu thẻ bài |
| `user_id` | VARCHAR(36) | Index, Not Null | Chủ sở hữu thẻ (Logical FK trỏ sang bảng `users`) |
| `card_id` | VARCHAR(50) | Index, Not Null | Loại thẻ gốc (Logical FK trỏ sang bảng `master_cards`) |
| `current_level` | INT | Default 1 | Cấp độ sức mạnh hiện tại (Có thể nâng cấp từ 1 lên 10) |
| `is_locked` | BOOLEAN | Default false | Khóa an toàn (Nếu true, không thể dùng thẻ này làm Phôi hiến tế) |

'''
# Insert db_dict right before ## 3.4
text = text.replace('## 3.4. DANH SÁCH API ENDPOINTS', db_dict + '\n## 3.4. DANH SÁCH API ENDPOINTS')

# 4. Rút gọn cực đại Mục 3.5.10 (AI RAG)
rag_pattern = r'### 3\.5\.10\. RAG ETL Pipeline.*?(\n\n---)'
rag_replacement = '''### 3.5.10. RAG ETL Pipeline — Lấy Dữ Liệu & Nhúng Vector (Data Extraction & Embedding)
Vấn đề: AI chat cần kiến thức cập nhật về đối tượng trò chơi (thành viên, album, sự kiện) mà LLM cơ bản không biết.
Giải pháp: Xây dựng một tiến trình tự động (Python Sidecar) cào dữ liệu từ trang web kpopping.com và các tài liệu dự án nội bộ. Dữ liệu văn bản thô sẽ được phân đoạn (chunking), sau đó nhúng (embed) vào Vector Store bằng mô hình `sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2` để phục vụ truy xuất thông tin (RAG) khi trò chuyện với AI.
\\1'''
text = re.sub(rag_pattern, rag_replacement, text, flags=re.DOTALL)


# 5. Triệt tiêu Mã giả ở Mục Thuật toán
# Replace 1: Nâng cấp thẻ
text = re.sub(r'### 1\.3\. Mã giả Thuật toán.*?### 1\.4\.', 
'''### 1.3. Lưu đồ & Các bước thực hiện

*[CHÈN HÌNH 3.10: Lưu đồ thuật toán Nâng cấp thẻ bài có khóa bị quan]*

**Các bước xử lý logic:**
1. **Khóa dòng dữ liệu (Pessimistic Lock):** Server gọi lệnh `SELECT ... FOR UPDATE` trên các bản ghi thẻ bài liên quan trong MySQL để đảm bảo không ai khác được quyền sửa đổi thẻ bài này trong suốt quá trình đập thẻ.
2. **Kiểm tra hợp lệ:** Xác thực Thẻ nguyên liệu (Phôi) không bị trùng lặp, không bị gắn cờ `is_locked`.
3. **Tiêu hủy Phôi (Burn):** Xóa ngay lập tức các thẻ nguyên liệu khỏi cơ sở dữ liệu để ngăn chặn khai thác lặp (Double-spending).
4. **Tính toán Tỷ lệ (Probability Calculation):** Dựa trên chênh lệch OVR giữa thẻ Phôi và Thẻ Chính, tính toán xác suất thành công. Cộng dồn tỷ lệ nếu có nhiều thẻ phôi.
5. **Đổ xúc xắc (Roll):** Sử dụng hệ thống quay số ngẫu nhiên để quyết định thẻ chính được thăng cấp hay rớt hạng.
6. **Nhả khóa & Cập nhật:** Lưu trạng thái mới của Thẻ chính và giải phóng Database Lock (Commit Transaction).

### 1.4.''', text, flags=re.DOTALL)

# Replace 2: Gacha
text = re.sub(r'### 2\.3\. Mã giả Thuật toán.*?### 2\.4\.',
'''### 2.3. Lưu đồ & Các bước thực hiện

*[CHÈN HÌNH 3.11: Lưu đồ thuật toán Mồi hỗn loạn RNG lấy dữ liệu từ random.org]*

**Các bước xử lý logic:**
1. **Khởi động Daemon:** Một luồng ngầm chạy định kỳ (mỗi 10 phút) sẽ gửi yêu cầu HTTP tới API của random.org để lấy chuỗi nhiễu khí quyển (Atmospheric Noise).
2. **Cập nhật Entropy:** Dữ liệu nhiễu được lấy về sẽ được giải mã và XOR với `System.nanoTime()` để tạo ra một hạt giống số cực kỳ khó đoán (Chaos Seed).
3. **Lưu trữ vào RAM:** Hạt giống số được cấp phát sẵn trên RAM (`AtomicLong`).
4. **Quay Gacha:** Bất cứ khi nào người chơi nhấn nút "Quay", Server sẽ lấy ngay lập tức Chaos Seed từ RAM mà không bị độ trễ mạng (0ms latency), trả về kết quả ngẫu nhiên và chuẩn xác tuyệt đối.

### 2.4.''', text, flags=re.DOTALL)

# Replace 3: Delta Sync
text = re.sub(r'### 3\.3\. Mã giả Thuật toán.*?### 3\.4\.',
'''### 3.3. Lưu đồ & Các bước thực hiện

*[CHÈN HÌNH 3.12: Lưu đồ thao tác định vị và cắt khuôn mặt sử dụng Google ML Kit]*

**Các bước xử lý logic:**
1. **Tải ảnh gốc:** Glide tiến hành tải ảnh thẻ có dung lượng lớn về bộ đệm.
2. **Face Detection (ML Kit):** Ảnh được chuyển qua tiến trình phát hiện khuôn mặt. Tác vụ bất đồng bộ này được đồng bộ hóa thông qua `Tasks.await()` ngay trên luồng DiskCacheExecutor để không đóng băng UI.
3. **Tính toán Tọa độ Cắt:** Khi phát hiện được bounding box của khuôn mặt, thuật toán tính toán lại hình vuông lý tưởng bọc quanh khuôn mặt.
4. **Biến đổi Hình dạng:** Cắt ảnh thành hình tròn (Circular Crop) có tâm chính xác là khuôn mặt nghệ sĩ.
5. **Lưu đệm kết quả:** Trả lại ảnh Avatar sắc nét và lưu vào RAM Cache/Disk Cache của Glide.

### 3.4.''', text, flags=re.DOTALL)

# Replace 4: ABS Fling
text = re.sub(r'### 4\.3\. Mã giả Thuật toán.*?### 4\.4\.',
'''### 4.3. Lưu đồ & Các bước thực hiện

*[CHÈN HÌNH 3.13: Lưu đồ hãm phanh cuộn và co giãn lưới Grid động]*

**Các bước xử lý logic:**
1. **Lắng nghe Lực vuốt (Fling Listener):** Bắt ngay sự kiện lướt màn hình trên `RecyclerView`.
2. **So sánh Ngưỡng (Threshold Check):** Lấy vận tốc hiện tại so sánh với `max_fling_velocity`. Nếu vượt mức cho phép, ép vận tốc về bằng mức tối đa (Giảm tốc độ lướt).
3. **Tính toán Tọa độ (OnScrolled):** Lặp qua mọi thẻ bài đang hiển thị trên màn hình. Tính toán khoảng cách từ thẻ bài đến trung tâm màn hình.
4. **Biến đổi Kích thước:** Áp dụng hệ số `ScaleX`, `ScaleY` và `Alpha`. Thẻ càng xa trung tâm (gần mép trên/dưới), kích thước càng thu nhỏ và trở nên mờ ảo.
5. **Yêu cầu Vẽ lại (Invalidate):** Cập nhật thuộc tính hiển thị ra View.

### 4.4.''', text, flags=re.DOTALL)

# Replace 5: PubSub (Two-Phase Sync)
text = re.sub(r'### 5\.3\. Mã giả Thuật toán.*?- \*\*Background Parsing:\*\*',
'''### 5.3. Lưu đồ & Các bước thực hiện

*[CHÈN HÌNH 3.14: Lưu đồ Đồng bộ Metadata hai bước (Two-Phase Cache Busting)]*

**Các bước xử lý logic:**
1. **Truy vấn Manifest:** Client khởi động và gọi API nhẹ (`/api/assets/manifest`) để lấy nhãn thời gian `remoteSyncTime`.
2. **So sánh Đồng bộ:** So khớp với `last_sync_timestamp` tại bộ nhớ SharedPreferences của Client. Nếu khác nhau, chuyển sang bước 3.
3. **Bust Cache & Tải tệp:** Tạo URL chứa Query Parameter là thời gian mới nhất để ép Cloudflare bỏ qua file cache tĩnh, tiến hành tải file `database.json` mới dung lượng lớn.
4. **Lưu trữ Cục bộ (Room):** Parse file JSON ở luồng ngầm và làm sạch Room DB cũ, nạp mới toàn bộ dữ liệu.
5. **Cập nhật Giao diện:** Cập nhật nhãn thời gian mới và kích hoạt `notifyInventoryChanged()` lên UI.

- **Background Parsing:**''', text, flags=re.DOTALL)

# Replace 6: Gift / ETL
text = re.sub(r'### 6\.3\. Mã giả Thuật toán.*?### 6\.4\.',
'''### 6.3. Lưu đồ & Các bước thực hiện

*[CHÈN HÌNH 3.15: Lưu đồ ETL Pipeline với Từ điển Cục bộ]*

**Các bước xử lý logic:**
1. **Kiểm tra Khóa (Status Lock):** Kiểm tra cờ `syncStatus`. Nếu đang chạy dở dang, lập tức chặn các tác vụ lập lịch khác (Ngăn Deadlock/Race Condition).
2. **Nạp Tự điển (Dictionary Caching):** Tải toàn bộ `members`, `seasons`, `classes` hiện có trên cơ sở dữ liệu lên bộ nhớ `HashMap` RAM.
3. **Lặp dữ liệu Nguồn:** Vòng lặp lấy từng dữ liệu đối tượng từ `database.json`. Kiểm tra xem thành viên/thẻ đó đã có trong HashMap hay chưa. Nếu chưa -> Ghi nhận đối tượng mới.
4. **Cắt Regex (Transform):** Dùng Regular Expression dịch sẵn để lấy ID ảnh.
5. **Lưu trữ Lô (Batch Insert):** Gom đủ 200 bản ghi mới và thực thi `saveAllAndFlush` đẩy một lượt xuống MySQL.

### 6.4.''', text, flags=re.DOTALL)

# Replace 7: Auto AFK Stage
text = re.sub(r'### 7\.3\. Mã giả Thuật toán.*?### 7\.4\.',
'''### 7.3. Lưu đồ & Các bước thực hiện

*[CHÈN HÌNH 3.16: Lưu đồ Biến động tỉ lệ quay Gacha linh hoạt]*

**Các bước xử lý logic:**
1. **Tạo mảng Biến động (Fluctuation):** Lấy xác suất rơi gốc của từng vật phẩm, cộng trừ ngẫu nhiên một biên độ nhỏ bằng thuật toán PRNG dựa trên thời gian thực.
2. **Bù trừ Trôi (Drift Correction):** Tính tổng xác suất mới (chắc chắn sẽ lệch khỏi 100%). Lấy phần dư (drift) cộng dồn vào phần thưởng hạng thấp nhất (Nothing/Rác) để bù trừ sai số dấu phẩy động.
3. **Quay thưởng (Spin):** Đổ xúc xắc để nhặt ra Thẻ trúng thưởng duy nhất (`winningCard`).
4. **Sinh Lưới Hiển thị (Reveal Grid):** Loại bỏ Thẻ trúng thưởng khỏi mảng, bốc 15 thẻ "làm nền" ngẫu nhiên khác trộn chung với Thẻ trúng thưởng để gửi về cho giao diện Client vẽ hoạt ảnh lưới ma trận lật thẻ.

### 7.4.''', text, flags=re.DOTALL)

# Fix order of known issues -> move to end
text = re.sub(r'## 3\.7\. CÁC VẤN ĐỀ TỒN ĐỌNG \(KNOWN ISSUES\).*?(?=## 3\.6\. ĐẶC TẢ THUẬT TOÁN)', '', text, flags=re.DOTALL)
known_issues = '''
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
'''
text = re.sub(r'\*Tài liệu này đã được hiệu chỉnh.*?Mosco\.\*', '', text)
text = text.strip() + '\n\n' + known_issues

with open('raw-doc/03_chuong_3_phan_tich_thiet_ke_rut_gon.md', 'w', encoding='utf-8') as f:
    f.write(text)

print('Script finished successfully!')

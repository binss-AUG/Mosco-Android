import re
import os

file_path = r'd:\MEox\UITer\DOAN\Mosco_Megre\Mosco\raw-doc\03_chuong_3_phan_tich_thiet_ke_rut_gon.md'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Fix Table Numbers 3.5, 3.6, 3.7 to 3.9, 3.10, 3.11
content = content.replace('Bảng 3.5: Chi tiết cấu trúc Bảng `users`', 'Bảng 3.9: Chi tiết cấu trúc Bảng `users`')
content = content.replace('Bảng 3.6: Chi tiết cấu trúc Bảng `master_cards`', 'Bảng 3.10: Chi tiết cấu trúc Bảng `master_cards`')
content = content.replace('Bảng 3.7: Chi tiết cấu trúc Bảng `user_cards`', 'Bảng 3.11: Chi tiết cấu trúc Bảng `user_cards`')

# 2. Delete duplicate Data Dictionary block
duplicate_start = content.find('---\n\n**3.3.3. Từ điển Dữ liệu (Data Dictionary)**\n\n**Bảng 3.9:')
if duplicate_start != -1:
    duplicate_end = content.find('## 3.4. DANH SÁCH API ENDPOINTS', duplicate_start)
    if duplicate_end != -1:
        content = content[:duplicate_start] + content[duplicate_end:]

# 3. Fix Table 3.8 -> 3.12 for API Endpoints
content = content.replace('**Bảng 3.8: Danh sách các API Endpoints lõi của hệ thống**', '**Bảng 3.12: Danh sách các API Endpoints lõi của hệ thống**')

# 4. Fix algorithm titles
content = content.replace('### 3.7.1. Thuật toán Nâng cấp Thẻ bài', '### 3.6.1. Thuật toán Nâng cấp Thẻ bài')
content = content.replace('### 3.7.2. Thuật toán Gacha sinh số ngẫu nhiên từ Nhiễu khí quyển', '### 3.6.2. Thuật toán Gacha sinh số ngẫu nhiên từ Nhiễu khí quyển (Atmospheric Noise Chaos Seed)')
content = content.replace('### 3.7.3. Thuật toán Đồng bộ hóa Dữ liệu Delta (Delta Sync Algorithm)', '### 3.6.3. Thuật toán Cắt ảnh Đại diện Thông minh (Smart Face Crop Transformation)')
content = content.replace('### 3.6.4. Thuật toán Bộ đệm LRU (Least Recently Used) và Cơ chế Lazy Loading Hình ảnh', '### 3.6.4. Thuật toán Hãm cuộn & Co giãn lưới động (Fling Brakes & Dynamic Grid Scaling)')
content = content.replace('### 3.6.5. Cơ chế Xử lý Tin nhắn Thời gian thực (Real-time Pub/Sub Message Broker)', '### 3.6.5. Thuật toán Đồng bộ Metadata Hai bước (Two-Phase Cache Busting Sync)')
content = content.replace('### 3.6.6. Thuật toán Gói quà (Gift Distribution Algorithm)', '### 3.6.6. Thuật toán Đồng bộ dữ liệu định kỳ (ETL Pipeline & Dictionary Caching)')
content = content.replace('### 3.6.7. Thuật toán Cử đội hình (Auto AFK Stage Team Composition)', '### 3.6.7. Thuật toán Biến động tỷ lệ Gacha linh hoạt (Dynamic Fluctuation Spin)')

# 5. Fix inner numbering (1.1 -> 1., 2.1 -> 1., etc.)
def replace_inner(match):
    num1 = match.group(1) # The prefix number (1-7)
    num2 = match.group(2) # The suffix number (1-4)
    text = match.group(3) # The text after
    return f"#### {num2}. {text}"

# Match: ### 1.1. Ý nghĩa & Bối cảnh
content = re.sub(r'### (\d)\.(\d)\.\s+(.*)', replace_inner, content)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

print("Fixes applied successfully!")

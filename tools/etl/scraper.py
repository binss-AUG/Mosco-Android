import requests
import json
import logging

# 1. CẤU HÌNH LOGGING
# Sẽ tạo ra 1 file scraper_mosco.log lưu lại lịch sử, đồng thời in ra màn hình (StreamHandler)
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler("scraper_mosco.log", encoding='utf-8'),
        logging.StreamHandler()
    ]
)

def crawl_objekt_data():
    url = "https://objekt.top/api/collection?artist=tripleS"
    
    # Fake User-Agent để API không nghĩ mình là bot
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept": "application/json"
    }
    
    logging.info(f"🚀 BẮT ĐẦU CÀO DỮ LIỆU TỪ: {url}")
    
    try:
        # Gửi request lấy dữ liệu
        response = requests.get(url, headers=headers)
        
        # Nếu server trả về lỗi (404, 500...), dòng này sẽ quăng ra Exception ngay lập tức
        response.raise_for_status() 
        
        # Parse dữ liệu sang định dạng JSON
        data = response.json()
        
        # Đếm số lượng thẻ lấy được (Thường API này trả về 1 list/mảng các object)
        if isinstance(data, list):
            total_cards = len(data)
        elif isinstance(data, dict) and 'objekts' in data: # Phòng hờ cấu trúc khác
            total_cards = len(data['objekts'])
        else:
            total_cards = "Không đếm được (cấu trúc lạ)"

        logging.info(f"✅ TẢI THÀNH CÔNG! Đã lấy được metadata của {total_cards} thẻ.")
        
        # 2. XUẤT RA FILE JSON
        json_filename = 'triples_data.json'
        with open(json_filename, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=4)
            
        logging.info(f"💾 ĐÃ LƯU DỮ LIỆU VÀO FILE: '{json_filename}'. Kích thước file đã sẵn sàng.")
        logging.info("🎉 BƯỚC TIỀN XỬ LÝ (ETL) HOÀN TẤT TRỌN VẸN!")
        
    except requests.exceptions.HTTPError as http_err:
        logging.error(f"❌ Lỗi HTTP (Server từ chối hoặc sập): {http_err}")
    except requests.exceptions.ConnectionError:
        logging.error("❌ Lỗi mạng: Không thể kết nối tới server.")
    except json.JSONDecodeError:
        logging.error("❌ Lỗi Parse JSON: Server không trả về định dạng JSON hợp lệ.")
    except Exception as err:
        logging.error(f"❌ Lỗi không xác định: {err}")

if __name__ == "__main__":
    crawl_objekt_data()
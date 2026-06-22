FROM python:3.10-slim

WORKDIR /app

# Cài đặt các thư viện phụ thuộc
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy mã nguồn vào container
COPY . .

# Expose port theo chuẩn của dự án (5001)
EXPOSE 5001

# Chạy server FastAPI
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "5001"]

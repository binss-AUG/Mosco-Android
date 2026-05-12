# ==========================================
# Stage 1: Build Application from Source
# ==========================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy các tệp cấu hình Gradle và mã nguồn vào container
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY src src

# Cấp quyền thực thi cho tệp Gradle Wrapper
RUN chmod +x gradlew

# Tự động tải thư viện và biên dịch mã nguồn thành tệp .jar (Từ A đến Z)
RUN ./gradlew bootJar --no-daemon

# ==========================================
# Stage 2: Minimal Runtime Environment
# ==========================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy tệp .jar thành phẩm từ Stage 1 sang Stage 2
COPY --from=builder /app/build/libs/*.jar app.jar

# Phơi cổng kết nối mặc định của Spring Boot
EXPOSE 8080

# Thiết lập lệnh khởi chạy ứng dụng
ENTRYPOINT ["java", "-jar", "app.jar"]

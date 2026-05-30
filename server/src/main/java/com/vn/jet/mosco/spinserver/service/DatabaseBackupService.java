package com.vn.jet.mosco.spinserver.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;

@Slf4j
@Service
public class DatabaseBackupService {

    @Value("${DB_HOST:localhost}")
    private String dbHost;

    @Value("${DB_PORT:3306}")
    private String dbPort;

    @Value("${DB_NAME:mosco_db}")
    private String dbName;

    @Value("${DB_USER:root}")
    private String dbUser;

    @Value("${DB_PASS:31072006}")
    private String dbPass;

    @Value("${mosco.backup.retention-days:7}")
    private int retentionDays;

    // TẠI SAO: Đặt thư mục sao lưu riêng biệt trong storage/db_backups để dễ quản lý và phân tách khỏi code chính
    private static final String BACKUP_DIR = "storage/db_backups";

    @jakarta.annotation.PostConstruct
    public void init() {
        // TẠI SAO: In log xác nhận dịch vụ sao lưu được khởi tạo thành công cùng với thư mục lưu và mốc thời gian lưu giữ
        log.info("DatabaseBackupService initialized. Backups will be stored in: {} (Retention: {} days)", BACKUP_DIR, retentionDays);
    }

    /**
     * Tự động chạy sao lưu cơ sở dữ liệu vào lúc 02:00 sáng mỗi ngày.
     * TẠI SAO: Lựa chọn 2:00 sáng là khung giờ thấp điểm, ít người dùng hoạt động nhất để giảm tải áp lực I/O cho server.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void performBackup() {
        log.info("Starting automated MySQL database backup for: {}", dbName);
        try {
            Files.createDirectories(Paths.get(BACKUP_DIR));
            
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = String.format("backup_%s_%s.sql", dbName, timestamp);
            Path outputPath = Paths.get(BACKUP_DIR).resolve(fileName);

            // TẠI SAO: Xây dựng mảng câu lệnh gọi mysqldump của hệ thống để sao lưu nóng không gây lock database
            String[] command = {
                "mysqldump",
                "-h", dbHost,
                "-P", dbPort,
                "-u", dbUser,
                "-p" + dbPass,
                dbName
            };

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectOutput(outputPath.toFile());
            
            // TẠI SAO: redirectError ra một file tạm giúp ghi nhận chi tiết lỗi của mysqldump nếu có, phục vụ debug
            File errorFile = File.createTempFile("mysqldump_err_", ".log");
            pb.redirectError(errorFile);

            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                log.info("Database backup completed successfully: {}", outputPath.toAbsolutePath());
                cleanupOldBackups();
            } else {
                String errorMsg = Files.readString(errorFile.toPath());
                log.error("Failed to dump database. Exit code: {}. Error: {}", exitCode, errorMsg);
            }
            Files.deleteIfExists(errorFile.toPath());

        } catch (Exception e) {
            log.error("Error executing database backup", e);
        }
    }

    /**
     * Tự động xóa các bản sao lưu cũ vượt quá số ngày lưu trữ quy định.
     * TẠI SAO: Giới hạn lưu trữ (mặc định 7 ngày) giúp tiết kiệm đĩa cứng, tránh tình trạng đầy ổ đĩa dẫn đến sập server.
     */
    private void cleanupOldBackups() {
        log.info("Cleaning up database backups older than {} days...", retentionDays);
        try (Stream<Path> files = Files.list(Paths.get(BACKUP_DIR))) {
            files.filter(Files::isRegularFile)
                 .filter(path -> path.getFileName().toString().startsWith("backup_"))
                 .forEach(path -> {
                     try {
                         long ageInMillis = System.currentTimeMillis() - Files.getLastModifiedTime(path).toMillis();
                         long maxAgeInMillis = (long) retentionDays * 24 * 60 * 60 * 1000;
                         if (ageInMillis > maxAgeInMillis) {
                             Files.delete(path);
                             log.info("Deleted expired database backup file: {}", path.getFileName());
                         }
                     } catch (IOException e) {
                         log.warn("Failed to clean up backup file: {}", path, e);
                     }
                 });
        } catch (IOException e) {
            log.error("Failed to read backup directory for cleanup", e);
        }
    }
}

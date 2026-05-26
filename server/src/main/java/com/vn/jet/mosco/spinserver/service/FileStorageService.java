package com.vn.jet.mosco.spinserver.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FileStorageService {
    private final Path rootLocation = Paths.get("storage/backups");

    public FileStorageService() {
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage location", e);
        }
    }

    public String store(MultipartFile file, Long userId) {
        try {
            if (file.isEmpty()) {
                throw new RuntimeException("Failed to store empty file.");
            }
            
            Path userPath = rootLocation.resolve(String.valueOf(userId));
            Files.createDirectories(userPath);

            String filename = file.getOriginalFilename();
            if (filename == null || filename.contains("..")) {
                throw new RuntimeException("Cannot store file with relative path outside current directory.");
            }

            Path destinationFile = userPath.resolve(Paths.get(filename)).normalize().toAbsolutePath();
            
            try (var inputStream = file.getInputStream()) {
                Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            }
            
            // Dọn dẹp các bản sao lưu cũ để giải phóng dung lượng đĩa
            // Chỉ giữ lại 5 bản sao lưu mới nhất của người dùng này
            cleanOldBackups(userPath);
            
            return filename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file.", e);
        }
    }

    public Path load(String filename, Long userId) {
        return rootLocation.resolve(String.valueOf(userId)).resolve(filename);
    }

    /**
     * Dọn dẹp các file sao lưu cũ, chỉ giữ lại tối đa 5 bản sao lưu gần nhất.
     */
    private void cleanOldBackups(Path userPath) {
        try (var stream = Files.list(userPath)) {
            List<Path> files = stream
                    .filter(path -> path.toString().endsWith(".db"))
                    .collect(Collectors.toList());

            if (files.size() <= 5) return;

            // Sắp xếp các file theo thời gian chỉnh sửa mới nhất lên trước
            files.sort((p1, p2) -> {
                try {
                    return Files.getLastModifiedTime(p2).compareTo(Files.getLastModifiedTime(p1));
                } catch (IOException e) {
                    return 0;
                }
            });

            // Xóa các file cũ từ vị trí thứ 5 trở đi
            for (int i = 5; i < files.size(); i++) {
                Files.deleteIfExists(files.get(i));
            }
        } catch (IOException e) {
            // Không ném ngoại lệ ở đây để đảm bảo việc upload chính không bị gián đoạn
            System.err.println("Lỗi khi dọn dẹp các bản sao lưu cũ: " + e.getMessage());
        }
    }
}

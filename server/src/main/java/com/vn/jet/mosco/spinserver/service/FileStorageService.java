package com.vn.jet.mosco.spinserver.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

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
            
            return filename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file.", e);
        }
    }

    public Path load(String filename, Long userId) {
        return rootLocation.resolve(String.valueOf(userId)).resolve(filename);
    }
}

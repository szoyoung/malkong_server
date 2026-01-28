package com.example.ddorang.common.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class FileStorageService {
    
    @Value("${app.upload.dir:uploads}")
    private String uploadDir;
    
    @Value("${app.upload.thumbnail.dir:uploads/thumbnails}")
    private String thumbnailUploadDir;

    
    // 썸네일 파일 저장
    public FileInfo storeThumbnailFile(byte[] thumbnailData, String userId, Long projectId, String originalVideoFileName) {
        try {
            // 저장 디렉토리 생성
            Path uploadPath = createUploadDirectory(thumbnailUploadDir, userId, projectId);
            
            // 썸네일 파일명 생성
            String storedFileName = generateThumbnailFileName(originalVideoFileName);
            
            // 파일 저장
            Path targetLocation = uploadPath.resolve(storedFileName);
            Files.write(targetLocation, thumbnailData);
            
            log.info("썸네일 파일 저장 완료: {}", targetLocation.toString());
            
            return FileInfo.builder()
                    .originalFileName(storedFileName)
                    .storedFileName(storedFileName)
                    .filePath(targetLocation.toString())
                    .relativePath(getRelativePath(targetLocation))
                    .fileSize((long) thumbnailData.length)
                    .contentType("image/jpeg")
                    .build();
                    
        } catch (IOException e) {
            log.error("썸네일 저장 실패: {}", e.getMessage());
            throw new RuntimeException("썸네일 저장에 실패했습니다: " + e.getMessage());
        }
    }
    
    // 파일 삭제
    public boolean deleteFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            boolean deleted = Files.deleteIfExists(path);
            
            if (deleted) {
                log.info("파일 삭제 완료: {}", filePath);
            } else {
                log.warn("삭제할 파일이 존재하지 않습니다: {}", filePath);
            }
            
            return deleted;
            
        } catch (IOException e) {
            log.error("파일 삭제 실패: {}", e.getMessage());
            return false;
        }
    }
    
    // 디렉토리 생성
    private Path createUploadDirectory(String baseDir, String userId, Long projectId) throws IOException {
        String dateDir = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        Path uploadPath = Paths.get(baseDir, userId, projectId.toString(), dateDir);

        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
            log.debug("디렉토리 생성: {}", uploadPath.toString());
        }

        return uploadPath;
    }
    
    // 비디오 파일 관련 메서드 제거됨 (비디오 파일은 분석 서버에서 처리)
    
    // 썸네일 파일명 생성
    private String generateThumbnailFileName(String originalVideoFileName) {
        String baseName = originalVideoFileName.substring(0, originalVideoFileName.lastIndexOf('.'));
        return baseName + "_thumbnail.jpg";
    }
    
    // 상대 경로 생성
    private String getRelativePath(Path absolutePath) {
        Path basePath = Paths.get(uploadDir);
        return basePath.relativize(absolutePath).toString().replace("\\", "/");
    }
    
    // 파일 정보 클래스
    public static class FileInfo {
        public final String originalFileName;
        public final String storedFileName;
        public final String filePath;
        public final String relativePath;
        public final String videoUrl;
        public final Long fileSize;
        public final String contentType;
        
        private FileInfo(Builder builder) {
            this.originalFileName = builder.originalFileName;
            this.storedFileName = builder.storedFileName;
            this.filePath = builder.filePath;
            this.relativePath = builder.relativePath;
            this.videoUrl = builder.videoUrl;
            this.fileSize = builder.fileSize;
            this.contentType = builder.contentType;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private String originalFileName;
            private String storedFileName;
            private String filePath;
            private String relativePath;
            private String videoUrl;
            private Long fileSize;
            private String contentType;
            
            public Builder originalFileName(String originalFileName) {
                this.originalFileName = originalFileName;
                return this;
            }
            
            public Builder storedFileName(String storedFileName) {
                this.storedFileName = storedFileName;
                return this;
            }
            
            public Builder filePath(String filePath) {
                this.filePath = filePath;
                return this;
            }
            
            public Builder relativePath(String relativePath) {
                this.relativePath = relativePath;
                return this;
            }
            
            public Builder videoUrl(String videoUrl) {
                this.videoUrl = videoUrl;
                return this;
            }
            
            public Builder fileSize(Long fileSize) {
                this.fileSize = fileSize;
                return this;
            }
            
            public Builder contentType(String contentType) {
                this.contentType = contentType;
                return this;
            }
            
            public FileInfo build() {
                return new FileInfo(this);
            }
        }
    }
} 
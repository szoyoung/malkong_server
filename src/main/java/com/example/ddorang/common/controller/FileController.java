package com.example.ddorang.common.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@RestController
@RequestMapping("/api/files")
@Slf4j
public class FileController {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.upload.use-external-storage:false}")
    private boolean useExternalStorage;

    @Value("${app.upload.external-storage-url:https://malkongserver.shop/api/files}")
    private String externalStorageUrl;

    // 메인 비디오 파일 제공 엔드포인트
    @GetMapping("/videos/**")
    public ResponseEntity<?> getVideoFile(HttpServletRequest request) {
        try {
            // URL에서 /api/files/videos/ 이후 경로 추출
            // /api/files/videos/ 이후의 전체 경로 추출
            String fullPath = request.getRequestURI();
            String videoPath = fullPath.substring(fullPath.indexOf("/api/files/videos/") + "/api/files/videos/".length());
            
            // videos/videos/ 중복 제거 (기존 잘못된 URL 호환성)
            if (videoPath.startsWith("videos/")) {
                videoPath = videoPath.substring("videos/".length());
            }
            
            log.info("비디오 파일 요청 경로: {}", videoPath);
            
            // 외부 파일 서버 사용 시 리디렉션
            if (useExternalStorage) {
                String redirectUrl = externalStorageUrl + "/videos/" + videoPath;
                log.info("외부 파일 서버로 리디렉션: {}", redirectUrl);
                return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                        .header(HttpHeaders.LOCATION, redirectUrl)
                        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                        .header("Pragma", "no-cache")
                        .header("Expires", "0")
                        .build();
            }
            
            // 파일 경로 구성
            // videoPath가 stored_videos/로 시작하는 경우: uploadDir + videoPath 직접 사용
            // 그 외의 경우: uploadDir/videos/videoPath 또는 uploadDir/stored_videos/videoPath 시도
            Path filePath = null;
            Resource resource = null;
            
            if (videoPath.startsWith("stored_videos/")) {
                // stored_videos/ 경로인 경우 uploadDir 하위에 직접 위치
                filePath = Paths.get(uploadDir).resolve(videoPath).normalize();
                resource = new UrlResource(filePath.toUri());
            } else {
                // 기존 방식: uploadDir/videos/videoPath 시도
                filePath = Paths.get(uploadDir).resolve("videos").resolve(videoPath).normalize();
                resource = new UrlResource(filePath.toUri());
                
                // 파일이 없으면 stored_videos/ 디렉토리에서도 시도
                if (!resource.exists() || !resource.isReadable()) {
                    Path storedVideoPath = Paths.get(uploadDir).resolve("stored_videos").resolve(videoPath).normalize();
                    Resource storedResource = new UrlResource(storedVideoPath.toUri());
                    if (storedResource.exists() && storedResource.isReadable()) {
                        filePath = storedVideoPath;
                        resource = storedResource;
                        log.info("stored_videos 디렉토리에서 파일 발견: {}", filePath);
                    }
                }
            }
            
            log.info("비디오 파일 절대 경로: {}", filePath);

            if (!resource.exists() || !resource.isReadable()) {
                log.warn("로컬 파일을 찾을 수 없거나 읽을 수 없습니다: {}", filePath);
                
                // 외부 파일 서버로 리디렉션 시도
                String redirectUrl = externalStorageUrl + "/videos/" + videoPath;
                log.info("로컬 파일 없음, 외부 파일 서버로 리디렉션 시도: {}", redirectUrl);
                
                // 307 Temporary Redirect 사용 (브라우저가 리디렉션을 따르도록)
                return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                        .header(HttpHeaders.LOCATION, redirectUrl)
                        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                        .header("Pragma", "no-cache")
                        .header("Expires", "0")
                        .build();
            }
            
            // 파일명 추출
            String filename = filePath.getFileName().toString();

            MediaType mediaType = MediaTypeFactory.getMediaType(filename)
                    .orElse(MediaType.APPLICATION_OCTET_STREAM);

            log.info("비디오 파일 제공 성공: {} (타입: {})", filePath, mediaType);

            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.setContentType(mediaType);
            responseHeaders.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"");
            responseHeaders.setCacheControl(CacheControl.maxAge(31536000, java.util.concurrent.TimeUnit.SECONDS).getHeaderValue());
            responseHeaders.set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            responseHeaders.set(HttpHeaders.ACCEPT_RANGES, "bytes");

            // 파일 크기 가져오기 (Content-Length 설정용)
            long contentLength;
            try {
                contentLength = resource.contentLength();
                responseHeaders.setContentLength(contentLength);
            } catch (Exception e) {
                log.warn("파일 크기를 가져올 수 없습니다: {}", e.getMessage());
                contentLength = -1; // 크기를 알 수 없는 경우
            }

            List<HttpRange> httpRanges = parseRanges(request);
            if (!httpRanges.isEmpty()) {
                HttpRange range = httpRanges.get(0);
                ResourceRegion resourceRegion = rangeToRegion(range, resource);

                log.info("부분 콘텐츠 제공 - range: {}, 전체 크기: {} bytes", range, contentLength);

                // Range 요청 시 Content-Range 헤더 추가
                long rangeStart = resourceRegion.getPosition();
                long rangeEnd = rangeStart + resourceRegion.getCount() - 1;
                if (contentLength > 0) {
                    responseHeaders.set(HttpHeaders.CONTENT_RANGE, 
                        String.format("bytes %d-%d/%d", rangeStart, rangeEnd, contentLength));
                } else {
                    responseHeaders.set(HttpHeaders.CONTENT_RANGE, 
                        String.format("bytes %d-%d/*", rangeStart, rangeEnd));
                }
                responseHeaders.setContentLength(resourceRegion.getCount());

                return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                        .headers(responseHeaders)
                        .body(resourceRegion);
            }

            if (contentLength > 0) {
                log.info("전체 비디오 파일 제공 - 크기: {} bytes", contentLength);
            } else {
                log.info("전체 비디오 파일 제공 - 크기: 알 수 없음");
            }
            return ResponseEntity.ok()
                    .headers(responseHeaders)
                    .body(resource);
                    
        } catch (Exception e) {
            log.error("파일 제공 실패: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // 헬스체크용 엔드포인트
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("File service is running. Upload directory: " + uploadDir);
    }

    private List<HttpRange> parseRanges(HttpServletRequest request) {
        String rangeHeader = request.getHeader(HttpHeaders.RANGE);
        if (rangeHeader == null) {
            return List.of();
        }
        try {
            return HttpRange.parseRanges(rangeHeader);
        } catch (IllegalArgumentException ex) {
            log.warn("잘못된 Range 헤더: {}", rangeHeader);
            return List.of();
        }
    }

    private ResourceRegion rangeToRegion(HttpRange range, Resource resource) {
        return range.toResourceRegion(resource);
    }
} 
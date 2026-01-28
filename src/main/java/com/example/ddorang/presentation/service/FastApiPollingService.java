package com.example.ddorang.presentation.service;

import com.example.ddorang.presentation.entity.VideoAnalysisJob;
import com.example.ddorang.presentation.repository.PresentationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class FastApiPollingService {

    private final VideoAnalysisService videoAnalysisService;
    private final VideoChunkService videoChunkService;
    private final PresentationRepository presentationRepository;
    private final RestTemplate restTemplate;

    @Value("${fastapi.base-url:http://localhost:8000}")
    private String fastApiUrl;

    // 비동기 영상 분석 시작 (MultipartFile - 트랜잭션 커밋 전에만 사용 가능)
    @Async
    public CompletableFuture<Void> startVideoAnalysis(VideoAnalysisJob job, MultipartFile videoFile) {
        log.info("🎬 FastAPI 비동기 분석 시작 (MultipartFile): {} - {}", job.getId(), job.getPresentation().getTitle());
        
        try {
            // MultipartFile을 임시 파일로 저장 (트랜잭션 커밋 후에는 MultipartFile이 정리되므로)
            log.info("📁 MultipartFile을 임시 파일로 저장 시작: {} (크기: {}MB)", 
                videoFile.getOriginalFilename(), videoFile.getSize() / (1024 * 1024));
            File tempFile = File.createTempFile("video_upload_", "_" + videoFile.getOriginalFilename());
            videoFile.transferTo(tempFile);
            log.info("✅ 임시 파일 생성 완료: {} ({}MB)", tempFile.getAbsolutePath(), tempFile.length() / (1024 * 1024));
            
            // File을 받는 오버로드 메서드 호출
            return startVideoAnalysis(job, tempFile);
            
        } catch (Exception e) {
            log.error("❌ MultipartFile을 임시 파일로 저장 실패: {}", job.getId(), e);
            videoAnalysisService.markJobAsFailed(job.getId(), "파일 저장 실패: " + e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }
    
    // 비동기 영상 분석 시작 (File - 트랜잭션 커밋 후에도 사용 가능)
    @Async
    public CompletableFuture<Void> startVideoAnalysis(VideoAnalysisJob job, File videoFile) {
        log.info("🎬 FastAPI 비동기 분석 시작 (File): {} - {}", job.getId(), job.getPresentation().getTitle());
        log.debug("DEBUG: VideoAnalysisJob - videoPath: {}, presentationId: {}", job.getVideoPath(), job.getPresentation().getId());
        log.debug("DEBUG: VideoChunkService bean: {}", videoChunkService != null ? "OK" : "NULL");

        try {
            // FastAPI /analysis 엔드포인트 호출 (파일 직접 전달)
            log.debug("DEBUG: callFastApiAnalysis() 호출 직전");
            String fastApiJobId = callFastApiAnalysisWithFile(job, videoFile);
            log.debug("DEBUG: callFastApiAnalysis() 호출 직후 - 반환값: {}", fastApiJobId);

            if (fastApiJobId == null) {
                log.warn("⚠️ FastAPI 초기 호출 실패, 백그라운드 처리 대기 중: {}", job.getId());
                videoAnalysisService.updateJobStatus(job.getId(), "processing", "분석 서버 연결 중입니다. 잠시만 기다려주세요...");
                // 실패로 마킹하지 않고 processing 상태 유지하여 폴링 기회 제공
                return CompletableFuture.completedFuture(null);
            }

            // 상태를 processing으로 업데이트
            videoAnalysisService.updateJobStatus(job.getId(), "processing", "FastAPI에서 분석 중...");

            // 백그라운드에서 결과 폴링 시작
            pollFastApiResult(job.getId(), fastApiJobId);

        } catch (Exception e) {
            log.error("FastAPI 분석 시작 실패: {}", job.getId(), e);
            videoAnalysisService.markJobAsFailed(job.getId(), "분석 시작 실패: " + e.getMessage());
        } finally {
            // 임시 파일 삭제 (File로 전달된 경우에만)
            if (videoFile != null && videoFile.exists() && videoFile.getName().startsWith("video_upload_")) {
                boolean deleted = videoFile.delete();
                if (deleted) {
                    log.debug("DEBUG: 임시 파일 삭제 완료: {}", videoFile.getAbsolutePath());
                } else {
                    log.warn("⚠️ 임시 파일 삭제 실패: {}", videoFile.getAbsolutePath());
                }
            }
        }

        return CompletableFuture.completedFuture(null);
    }


    // FastAPI /analysis 엔드포인트 호출 (File 직접 전달)
    private String callFastApiAnalysisWithFile(VideoAnalysisJob job, File videoFile) {
        log.debug("DEBUG: callFastApiAnalysisWithFile() 메서드 진입");

        try {
            log.info("📹 FastAPI 분석 호출 (File 직접 전달): {} (크기: {}MB)", 
                videoFile.getName(), videoFile.length() / (1024 * 1024));

            // ===== 1. 메타데이터 구성 =====
            Map<String, Object> metadata = new HashMap<>();
            String targetTime = job.getPresentation().getGoalTime() != null ?
                job.getPresentation().getGoalTime() + ":00" : "6:00";
            metadata.put("target_time", targetTime);
            log.debug("DEBUG: 메타데이터 구성 완료 - target_time: {}", targetTime);

            // ===== 2. 청크 업로드 =====
            videoAnalysisService.updateJobStatus(job.getId(), "processing", "영상 업로드 중...");
            
            // 메모리 사용량 확인
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            log.info("💾 메모리 상태 - 사용: {}MB / 전체: {}MB / 사용 가능: {}MB", 
                usedMemory / (1024 * 1024), totalMemory / (1024 * 1024), freeMemory / (1024 * 1024));
            
            // video_path를 받기 위한 Map 생성
            Map<String, String> videoPathMap = new HashMap<>();
            
            // 청크 업로드
            log.info("🔄 videoChunkService.uploadVideoInChunks() 호출 시작");
            String fastApiJobId = videoChunkService.uploadVideoInChunks(videoFile, metadata, videoPathMap);
            log.info("✅ videoChunkService.uploadVideoInChunks() 호출 완료 - 반환값: {}", fastApiJobId);
            log.info("✅ FastAPI 청크 업로드 성공 - job_id: {}", fastApiJobId);
            
            // 메모리 사용량 재확인
            runtime = Runtime.getRuntime();
            totalMemory = runtime.totalMemory();
            freeMemory = runtime.freeMemory();
            usedMemory = totalMemory - freeMemory;
            log.info("💾 청크 업로드 후 메모리 상태 - 사용: {}MB / 전체: {}MB / 사용 가능: {}MB", 
                usedMemory / (1024 * 1024), totalMemory / (1024 * 1024), freeMemory / (1024 * 1024));
            
            // video_path가 있으면 즉시 URL 생성 및 저장
            if (videoPathMap.containsKey("video_path")) {
                String videoPath = videoPathMap.get("video_path");
                log.info("📹 즉시 video_path 수신: {}", videoPath);
                saveVideoPathImmediately(job, videoPath);
            }
            
            return fastApiJobId;

        } catch (Exception e) {
            log.error("❌ FastAPI /analysis 호출 실패 - 예외 타입: {}, 메시지: {}",
                e.getClass().getSimpleName(), e.getMessage(), e);
        }

        log.debug("DEBUG: callFastApiAnalysisWithFile() null 반환");
        return null;
    }

    /**
     * video_path를 받은 직후 즉시 URL 생성 및 저장
     */
    private void saveVideoPathImmediately(VideoAnalysisJob job, String videoPath) {
        try {
            log.info("📹 비디오 경로 즉시 저장 시작: {}", videoPath);
            
            // VideoAnalysisJob에 video_path 저장
            job.setVideoPath(videoPath);
            videoAnalysisService.updateJobStatus(job.getId(), "processing", "영상 파일 저장 완료. 분석을 시작합니다...");
            
            // 파일 서버 URL 생성
            String videoUrl = generateVideoUrl(videoPath);
            
            // Presentation에 videoUrl 저장
            job.getPresentation().setVideoUrl(videoUrl);
            // Presentation 저장
            presentationRepository.save(job.getPresentation());
            
            log.info("📹 비디오 URL 즉시 생성 및 저장 완료: {}", videoUrl);
            
        } catch (Exception e) {
            log.warn("비디오 경로 즉시 저장 실패 (분석 완료 시 재시도 예정): {}", e.getMessage());
        }
    }

    /**
     * FastAPI에서 받은 비디오 상대 경로를 파일 서버 URL로 변환
     */
    private String generateVideoUrl(String videoPath) {
        if (videoPath == null || videoPath.isEmpty()) {
            return null;
        }
        
        // 이미 URL 형식인 경우 그대로 반환
        if (videoPath.startsWith("http://") || videoPath.startsWith("https://")) {
            return videoPath;
        }
        
        // 상대 경로를 파일 서버 URL로 변환
        // stored_videos/{filename} -> /api/files/videos/stored_videos/{filename}
        String url = "/api/files/videos/" + videoPath;
        if (url.startsWith("//")) {
            url = url.substring(1); // 앞의 / 중 하나 제거
        }
        log.debug("비디오 URL 생성: {} -> {}", videoPath, url);
        return url;
    }

    // FastAPI 결과 폴링
    // 5초마다 /result/{job_id} 호출
    private void pollFastApiResult(java.util.UUID springJobId, String fastApiJobId) {
        log.info("FastAPI 결과 폴링 시작: {} → {}", springJobId, fastApiJobId);

        int maxAttempts = 240; // 최대 20분 (5초 × 240회)
        int attempts = 0;

        while (attempts < maxAttempts) {
            try {
                // FastAPI /result/{job_id} 호출
                @SuppressWarnings("unchecked")
                ResponseEntity<Map<String, Object>> response = (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) restTemplate.getForEntity(
                    fastApiUrl + "/result/" + fastApiJobId,
                    Map.class
                );

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    Map<String, Object> result = response.getBody();
                    String status = (String) result.get("status");

                    log.debug("폴링 결과: {} - {} ({}회차)", springJobId, status, attempts + 1);

                    switch (status) {
                        case "processing":
                            // 계속 대기
                            break;

                        case "completed":
                            // 분석 완료
                            Map<String, Object> analysisResult = (Map<String, Object>) result.get("result");

                            log.info("FastAPI 분석 완료: {} → {}", springJobId, fastApiJobId);

                            // DB에 결과 저장 + 직접 웹소켓 알림 발행
                            videoAnalysisService.completeJob(springJobId, analysisResult);
                            return;

                        case "error":
                            // 분석 실패
                            String error = (String) result.get("error");
                            log.error("FastAPI 분석 실패: {} - {}", springJobId, error);

                            videoAnalysisService.markJobAsFailed(springJobId, "FastAPI 분석 오류: " + error);
                            return;

                        case "not_found":
                            log.warn("⚠FastAPI 작업 없음: {}", fastApiJobId);
                            videoAnalysisService.markJobAsFailed(springJobId, "FastAPI에서 작업을 찾을 수 없음");
                            return;

                        default:
                            log.warn(" 알 수 없는 상태: {} - {}", springJobId, status);
                    }
                }

                // 5초 대기
                Thread.sleep(5000);
                attempts++;

            } catch (InterruptedException e) {
                log.info("폴링 중단: {}", springJobId);
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.error("폴링 오류: {} ({}회차)", springJobId, attempts + 1, e);
                attempts++;

                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        // 타임아웃 처리
        log.error("FastAPI 폴링 타임아웃: {} (20분 초과)", springJobId);
        videoAnalysisService.markJobAsFailed(springJobId, "FastAPI 응답 타임아웃 (20분 초과)");
    }

}
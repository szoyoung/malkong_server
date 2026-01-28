package com.example.ddorang.presentation.service;

import com.example.ddorang.common.enums.JobStatus;
import com.example.ddorang.common.service.NotificationService;
import com.example.ddorang.presentation.entity.Presentation;
import com.example.ddorang.presentation.entity.VideoAnalysisJob;
import com.example.ddorang.presentation.repository.PresentationRepository;
import com.example.ddorang.presentation.repository.VideoAnalysisJobRepository;
import com.example.ddorang.presentation.service.VoiceAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoAnalysisService {

    private final VideoAnalysisJobRepository videoAnalysisJobRepository;
    private final PresentationRepository presentationRepository;
    private final NotificationService notificationService;
    private final VoiceAnalysisService voiceAnalysisService;

    // 메모리에 결과 임시 저장 (TTL 캐시)
    private final Map<UUID, CacheEntry> resultCache = new ConcurrentHashMap<>();

    // 캐시 엔트리 클래스
    private static class CacheEntry {
        private final Map<String, Object> data;
        private final LocalDateTime expireTime;

        public CacheEntry(Map<String, Object> data) {
            this.data = data;
            this.expireTime = LocalDateTime.now().plusHours(24); // 24시간 후 만료
        }

        public boolean isExpired() {
            return LocalDateTime.now().isAfter(expireTime);
        }

        public Map<String, Object> getData() {
            return data;
        }
    }

    // 작업 초기 상태 설정
    public void initializeJob(VideoAnalysisJob job) {
        try {
            log.info("작업 초기화: {}", job.getId());

            // DB에 이미 저장되어 있으므로 별도 처리 불필요
            log.info("작업 초기화 완료: {}", job.getId());

        } catch (Exception e) {
            log.error("작업 초기화 실패: {}", job.getId(), e);
            throw new RuntimeException("작업 초기화에 실패했습니다", e);
        }
    }

    // 작업 상태 업데이트
    public void updateJobStatus(UUID jobId, String status, String message) {
        try {
            VideoAnalysisJob job = videoAnalysisJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 작업: " + jobId));

            // 상태 업데이트
            JobStatus newStatus = JobStatus.valueOf(status.toUpperCase());
            job.setStatus(newStatus);

            if (JobStatus.FAILED.equals(newStatus)) {
                job.setErrorMessage(message);
            }

            videoAnalysisJobRepository.save(job);
            log.debug("상태 업데이트: {} - {}", jobId, message);

        } catch (Exception e) {
            log.error("상태 업데이트 실패: {}", jobId, e);
        }
    }

    // 작업 완료 처리 - 이벤트 발행 (트랜잭션 없이 처리)
    public void completeJob(UUID jobId, Map<String, Object> analysisResult) {
        try {
            log.info("작업 완료 처리 시작: {}", jobId);

            // 분석 결과를 메모리 캐시에 저장 (24시간 보관)
            resultCache.put(jobId, new CacheEntry(analysisResult));

            // 만료된 캐시 엔트리 정리
            cleanupExpiredCache();

            // 상태를 'COMPLETED'로 업데이트
            VideoAnalysisJob job = videoAnalysisJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 작업: " + jobId));

            // FastAPI에서 반환된 video_path 추출 및 저장
            String videoPath = null;
            if (analysisResult.containsKey("video_path")) {
                Object videoPathObj = analysisResult.get("video_path");
                if (videoPathObj != null) {
                    videoPath = videoPathObj.toString();
                    log.info("📹 저장된 비디오 경로 수신: {}", videoPath);
                    // VideoAnalysisJob에 video_path 저장
                    job.setVideoPath(videoPath);
                }
            }

            job.setStatus(JobStatus.COMPLETED);
            videoAnalysisJobRepository.save(job);
            
            // 트랜잭션 커밋 전에 정보 추출 (Lazy Loading)
            UUID userId = job.getPresentation().getTopic().getUser().getUserId();
            String presentationTitle = job.getPresentation().getTitle();
            UUID presentationId = job.getPresentation().getId();
            
            // 비디오 파일 URL 생성 및 Presentation에 저장
            if (videoPath != null && !videoPath.isEmpty()) {
                try {
                    // 파일 서버 URL 생성 (예: /api/files/videos/stored_videos/{filename})
                    String videoUrl = generateVideoUrl(videoPath);
                    Presentation presentation = job.getPresentation();
                    presentation.setVideoUrl(videoUrl);
                    // Presentation 저장 (videoUrl 업데이트)
                    presentationRepository.save(presentation);
                    log.info("📹 Presentation.videoUrl 설정 및 저장 완료: {}", videoUrl);
                } catch (Exception e) {
                    log.warn("비디오 URL 생성/저장 실패 (무시됨): {}", e.getMessage());
                }
            }
            
            // 분석 결과를 DB에 저장 (VoiceAnalysis, SttResult, PresentationFeedback)
            voiceAnalysisService.saveAnalysisResults(presentationId, analysisResult);
            log.info("분석 결과 DB 저장 완료: {}", presentationId);

            // 알림 발송 (트랜잭션이 없으므로 이벤트 대신 직접 호출)
            log.info("🔔 알림 발송 시작 - 사용자: {}, 발표: {}", userId, presentationTitle);
            try {
                notificationService.sendAnalysisCompleteNotification(
                    userId, presentationTitle, presentationId
                );
                log.info("✅ 알림 발송 완료 - 사용자: {}", userId);
            } catch (Exception notificationError) {
                log.error("❌ 알림 발송 실패: {}", notificationError.getMessage(), notificationError);
            }

            log.info("작업 완료 처리 성공: {}", jobId);

        } catch (Exception e) {
            log.error("작업 완료 처리 실패: {}", jobId, e);
            // 실패 처리도 트랜잭션 없이 처리
            try {
                markJobAsFailedWithoutTransaction(jobId, "결과 저장 중 오류: " + e.getMessage());
            } catch (Exception e2) {
                log.error("작업 실패 처리도 실패: {}", jobId, e2);
            }
        }
    }
    
    // 트랜잭션 없이 작업 실패 처리
    public void markJobAsFailedWithoutTransaction(UUID jobId, String errorMessage) {
        try {
            log.error("작업 실패 처리: {} - {}", jobId, errorMessage);

            VideoAnalysisJob job = videoAnalysisJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 작업: " + jobId));

            job.markAsFailed(errorMessage);
            videoAnalysisJobRepository.save(job);

            log.info("작업 실패 처리 완료: {}", jobId);

        } catch (Exception e) {
            log.error("작업 실패 처리 실패: {}", jobId, e);
        }
    }

    // 작업 실패 처리 - 이벤트 발행
    @Transactional
    public void markJobAsFailed(UUID jobId, String errorMessage) {
        try {
            log.error("작업 실패 처리: {} - {}", jobId, errorMessage);

            VideoAnalysisJob job = videoAnalysisJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 작업: " + jobId));

            job.markAsFailed(errorMessage);
            videoAnalysisJobRepository.save(job);

        } catch (Exception e) {
            log.error("실패 처리 중 추가 오류: {}", jobId, e);
        }
    }

    // 상태 조회 (사용자 폴링용)
    public Map<String, Object> getJobStatus(UUID jobId) {
        try {
            VideoAnalysisJob job = videoAnalysisJobRepository.findById(jobId).orElse(null);

            if (job == null) {
                return null;
            }

            Map<String, Object> status = new HashMap<>();
            status.put("presentationId", job.getPresentation().getId().toString());
            status.put("status", job.getStatus().toString().toLowerCase());
            status.put("message", getStatusMessage(job));
            status.put("createdAt", job.getCreatedAt().toString());

            return status;

        } catch (Exception e) {
            log.error("상태 조회 실패: {}", jobId, e);
            return null;
        }
    }

    // 결과 조회
    public Map<String, Object> getJobResult(UUID jobId) {
        try {
            CacheEntry entry = resultCache.get(jobId);

            if (entry == null) {
                return null;
            }

            // 만료된 캐시 확인
            if (entry.isExpired()) {
                resultCache.remove(jobId);
                log.debug("만료된 캐시 제거: {}", jobId);
                return null;
            }

            return entry.getData();
        } catch (Exception e) {
            log.error("결과 조회 실패: {}", jobId, e);
            return null;
        }
    }

    // 만료된 캐시 엔트리 정리
    private void cleanupExpiredCache() {
        resultCache.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired()) {
                log.debug("만료된 캐시 엔트리 제거: {}", entry.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * 스케줄러: 매 1시간마다 만료된 캐시 자동 정리
     *
     * fixedRate: 1시간 = 3,600,000ms
     * 작업 시작 후 1시간마다 실행 (작업 실행 시간 무관)
     */
    @Scheduled(fixedRate = 3600000)
    public void scheduledCacheCleanup() {
        try {
            int beforeSize = resultCache.size();

            if (beforeSize == 0) {
                return; // 캐시가 비어있으면 로그 안 남김
            }

            cleanupExpiredCache();
            int afterSize = resultCache.size();
            int removedCount = beforeSize - afterSize;

            if (removedCount > 0) {
                log.info("스케줄 캐시 정리 완료: {}개 제거 ({}개 → {}개 남음)",
                    removedCount, beforeSize, afterSize);
            } else {
                log.debug("스케줄 캐시 정리: 만료된 항목 없음 ({}개 유지)", beforeSize);
            }
        } catch (Exception e) {
            log.error("스케줄 캐시 정리 실패", e);
        }
    }

    // === Private 헬퍼 메서드들 ===

    // 상태별 메세지 생성
    private String getStatusMessage(VideoAnalysisJob job) {
        return switch (job.getStatus()) {
            case PENDING -> "분석을 준비하고 있습니다...";
            case PROCESSING -> "FastAPI에서 분석 중...";
            case COMPLETED -> "분석이 완료되었습니다.";
            case FAILED -> job.getErrorMessage() != null ?
                "분석 중 오류가 발생했습니다: " + job.getErrorMessage() :
                "분석 중 오류가 발생했습니다";
        };
    }

    /**
     * FastAPI에서 받은 비디오 상대 경로를 파일 서버 URL로 변환
     * 
     * @param videoPath FastAPI에서 받은 상대 경로 (예: "stored_videos/{job_id}.mp4")
     * @return 파일 서버 URL (예: "/api/files/videos/stored_videos/{job_id}.mp4")
     */
    private String generateVideoUrl(String videoPath) {
        if (videoPath == null || videoPath.isEmpty()) {
            return null;
        }
        
        // 이미 URL 형식인 경우 그대로 반환
        if (videoPath.startsWith("http://") || videoPath.startsWith("https://") || videoPath.startsWith("/")) {
            // 절대 URL이 아닌 경우 상대 경로로 처리
            if (videoPath.startsWith("/api/files/videos/")) {
                return videoPath;
            }
        }
        
        // 상대 경로를 파일 서버 URL로 변환
        // stored_videos/{filename} -> /api/files/videos/stored_videos/{filename}
        String url = "/api/files/videos/" + videoPath;
        log.debug("비디오 URL 생성: {} -> {}", videoPath, url);
        return url;
    }
}
package com.example.ddorang.presentation.controller;

import com.example.ddorang.common.service.AuthorizationService;
import com.example.ddorang.common.util.SecurityUtil;
import com.example.ddorang.common.ApiPaths;
import com.example.ddorang.presentation.entity.Presentation;
import com.example.ddorang.presentation.service.PresentationService;
import com.example.ddorang.presentation.dto.PresentationResponse;
import com.example.ddorang.presentation.entity.VideoAnalysisJob;
import com.example.ddorang.presentation.service.VideoAnalysisService;
import com.example.ddorang.presentation.service.FastApiPollingService;
import com.example.ddorang.presentation.dto.VideoAnalysisResponse;
import com.example.ddorang.presentation.dto.VideoAnalysisJobSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.ROOT)
@RequiredArgsConstructor
@Slf4j
public class PresentationController {
    
    private final PresentationService presentationService;
    private final AuthorizationService authorizationService;
    private final ObjectMapper objectMapper;
    private final VideoAnalysisService videoAnalysisService;
    private final FastApiPollingService fastApiPollingService;
    
    // 새 프레젠테이션 생성
    @PostMapping("/topics/{topicId}/presentations")
    public ResponseEntity<PresentationResponse> createPresentation(
            @PathVariable UUID topicId,
            @RequestParam("presentationData") String presentationDataJson,
            @RequestParam(value = "videoFile", required = false) MultipartFile videoFile) {
        
        log.info("프레젠테이션 생성 요청 - 토픽: {}", topicId);
        
        try {
            // JSON 파싱
            CreatePresentationRequest request = objectMapper.readValue(presentationDataJson, CreatePresentationRequest.class);
            
            // 프레젠테이션 생성
            Presentation presentation = presentationService.createPresentation(
                    topicId,
                    request.getTitle(),
                    request.getScript(),
                    request.getGoalTime(),
                    videoFile
            );
            
            PresentationResponse response = PresentationResponse.from(presentation);
            return ResponseEntity.ok(response);
            
        } catch (RuntimeException e) {
            if (e.getMessage().contains("권한") || e.getMessage().contains("멤버")) {
                return ResponseEntity.status(403).body(null);
            } else {
                return ResponseEntity.badRequest().build();
            }
        } catch (Exception e) {
            log.error("프레젠테이션 생성 실패: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    // 특정 프레젠테이션 조회
    @GetMapping("/presentations/{presentationId}")
    public ResponseEntity<PresentationResponse> getPresentation(@PathVariable UUID presentationId) {
        log.info("프레젠테이션 조회 요청 - ID: {}", presentationId);
        
        try {
            Presentation presentation = presentationService.getPresentationById(presentationId);
            PresentationResponse response = PresentationResponse.from(presentation);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("프레젠테이션 조회 실패: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    // 팀 프레젠테이션 조회 (권한 확인)
    @GetMapping("/presentations/{presentationId}/team")
    public ResponseEntity<PresentationResponse> getTeamPresentation(
            @PathVariable UUID presentationId) {
        
        UUID userId = SecurityUtil.getCurrentUserId();
        log.info("팀 프레젠테이션 조회 요청 - ID: {}, 사용자: {}", presentationId, userId);
        
        authorizationService.requirePresentationViewPermission(presentationId);
        
        try {
            Presentation presentation = presentationService.getTeamPresentation(presentationId, userId);
            PresentationResponse response = PresentationResponse.from(presentation);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("팀 프레젠테이션 조회 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    // 프레젠테이션 수정 (권한 확인)
    @PutMapping("/presentations/{presentationId}")
    public ResponseEntity<PresentationResponse> updatePresentation(
            @PathVariable UUID presentationId,
            @RequestBody UpdatePresentationRequest request) {
        
        UUID userId = SecurityUtil.getCurrentUserId();
        log.info("프레젠테이션 수정 요청 - ID: {}, 사용자: {}", presentationId, userId);
        
        authorizationService.requirePresentationModifyPermission(presentationId);
        
        try {
            Presentation presentation = presentationService.updatePresentation(
                    presentationId,
                    request.getTitle(),
                    request.getScript(),
                    request.getGoalTime()
            );
            
            PresentationResponse response = PresentationResponse.from(presentation);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("프레젠테이션 수정 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    // 프레젠테이션 삭제 (권한 확인)
    @DeleteMapping("/presentations/{presentationId}")
    public ResponseEntity<Void> deletePresentation(
            @PathVariable UUID presentationId) {
        
        UUID userId = SecurityUtil.getCurrentUserId();
        log.info("프레젠테이션 삭제 요청 - ID: {}, 사용자: {}", presentationId, userId);
        
        authorizationService.requirePresentationModifyPermission(presentationId);
        
        try {
            presentationService.deletePresentation(presentationId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("프레젠테이션 삭제 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    // 비디오 업로드 (별도 업로드)
    @PostMapping("/presentations/{presentationId}/video")
    public ResponseEntity<PresentationResponse> uploadVideo(
            @PathVariable UUID presentationId,
            @RequestParam("videoFile") MultipartFile videoFile) {

        log.info("비디오 업로드 요청 - 프레젠테이션: {}", presentationId);

        try {
            Presentation presentation = presentationService.updateVideoFile(presentationId, videoFile);
            PresentationResponse response = PresentationResponse.from(presentation);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("비디오 업로드 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    //비동기 영상 분석 엔드포인트
    @PostMapping("/presentations/{presentationId}/video/async")
    public ResponseEntity<VideoAnalysisResponse> startAsyncVideoAnalysis(
            @PathVariable UUID presentationId,
            @RequestParam("videoFile") MultipartFile videoFile) {

        log.info("폴링 기반 비동기 영상 분석 시작 - 프레젠테이션: {}", presentationId);

        authorizationService.requirePresentationModifyPermission(presentationId);

        try {
            // 프레젠테이션 정보 조회 (파일 서버 저장 없이)
            Presentation presentation = presentationService.getPresentationById(presentationId);

            // 비동기 분석 작업 생성 (파일은 분석 서버에 직접 저장됨)
            VideoAnalysisJob job = presentationService.createVideoAnalysisJob(
                presentation,
                videoFile.getOriginalFilename(),
                videoFile.getSize()
            );

            // DB에 초기 상태 저장
            videoAnalysisService.initializeJob(job);

            // FastAPI 폴링 시작 (백그라운드) - 파일을 분석 서버로 직접 전달
            fastApiPollingService.startVideoAnalysis(job, videoFile);

            // 즉시 응답 반환
            VideoAnalysisResponse response = VideoAnalysisResponse.builder()
                .jobId(job.getId())
                .presentationId(presentationId)
                .status("pending")
                .message("영상 분석이 시작되었습니다. 완료되면 알림을 보내드릴게요!")
                .build();

            log.info("분석 작업 시작 완료 - 작업 ID: {}", job.getId());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("분석 작업 시작 실패: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    // 진행 상태 조회 - 프론트엔드가 폴링하며 확인
    @GetMapping("/video-analysis/{jobId}/progress")
    public ResponseEntity<Map<String, Object>> getAnalysisProgress(@PathVariable UUID jobId) {

        try {
            Map<String, Object> progress = videoAnalysisService.getJobStatus(jobId);

            if (progress == null) {
                log.warn("존재하지 않는 작업 ID: {}", jobId);
                return ResponseEntity.notFound().build();
            }

            log.debug("진행상태 조회: {} - {}", jobId, progress.get("status"));
            return ResponseEntity.ok(progress);

        } catch (Exception e) {
            log.error("진행상태 조회 실패: {}", jobId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    // 분석 결과 조회 - 작업 완료 후 AI 분석 결과를 가져옴
    @GetMapping("/video-analysis/{jobId}/result")
    public ResponseEntity<Map<String, Object>> getAnalysisResult(@PathVariable UUID jobId) {

        try {
            // 작업 상태 확인
            Map<String, Object> progress = videoAnalysisService.getJobStatus(jobId);

            if (progress == null) {
                return ResponseEntity.notFound().build();
            }

            if (!"completed".equals(progress.get("status"))) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "분석이 아직 완료되지 않았습니다", "status", progress.get("status")));
            }

            // 결과 캐시에서 분석 결과 조회
            Map<String, Object> result = videoAnalysisService.getJobResult(jobId);

            if (result == null) {
                return ResponseEntity.notFound()
                    .build();
            }

            log.info("분석 결과 조회 성공: {}", jobId);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("분석 결과 조회 실패: {}", jobId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    // 사용자의 분석 작업 목록 조회 - 사용자 대시보드에서 확인(진행 중/진행 완료)
    @GetMapping("/users/{userId}/video-analysis-jobs")
    public ResponseEntity<List<VideoAnalysisJobSummary>> getUserAnalysisJobs(@PathVariable UUID userId) {

        UUID currentUserId = SecurityUtil.getCurrentUserId();

        // 본인 또는 팀 권한 확인
        if (!currentUserId.equals(userId)) {
            // 팀 권한 확인: 동일한 팀에 속해 있는지 확인
            try {
                org.springframework.security.core.Authentication auth =
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();

                if (auth != null && auth.getPrincipal() instanceof com.example.ddorang.auth.security.CustomUserDetails) {
                    com.example.ddorang.auth.security.CustomUserDetails userDetails =
                        (com.example.ddorang.auth.security.CustomUserDetails) auth.getPrincipal();

                    // 요청된 사용자의 팀 멤버십 확인
                    boolean hasTeamAccess = userDetails.getTeamMemberships().stream()
                        .anyMatch(teamMember -> {
                            // 같은 팀에 요청된 사용자가 있는지 확인
                            return teamMember.getTeam().getMembers().stream()
                                .anyMatch(member -> member.getUser().getUserId().equals(userId));
                        });

                    if (!hasTeamAccess) {
                        log.warn("팀 권한 없음 - 현재 사용자: {}, 요청 사용자: {}", currentUserId, userId);
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                    }

                    log.debug("팀 권한 확인 완료 - 현재 사용자: {}, 요청 사용자: {}", currentUserId, userId);
                } else {
                    log.error("CustomUserDetails를 가져올 수 없음");
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                }
            } catch (Exception e) {
                log.error("팀 권한 확인 중 오류: {}", e.getMessage(), e);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }

        try {
            List<VideoAnalysisJob> jobs = presentationService.getUserVideoAnalysisJobs(userId);

            List<VideoAnalysisJobSummary> summaries = jobs.stream()
                .map(job -> {
                    Map<String, Object> progress = videoAnalysisService.getJobStatus(job.getId());
                    return VideoAnalysisJobSummary.from(job, progress);
                })
                .toList();

            return ResponseEntity.ok(summaries);

        } catch (Exception e) {
            log.error("사용자 분석 작업 조회 실패: {}", userId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    // 분석 작업 취소 (대기 중인 작업일 경우에만)
    @DeleteMapping("/video-analysis/{jobId}")
    public ResponseEntity<Void> cancelAnalysisJob(@PathVariable UUID jobId) {

        try {
            Map<String, Object> progress = videoAnalysisService.getJobStatus(jobId);

            if (progress == null) {
                return ResponseEntity.notFound().build();
            }

            if ("processing".equals(progress.get("status"))) {
                return ResponseEntity.badRequest().build(); // 이미 처리 중
            }

            if (!"pending".equals(progress.get("status"))) {
                return ResponseEntity.badRequest().build(); // 이미 완료됨
            }

            // 작업을 취소 상태로 변경
            videoAnalysisService.markJobAsFailed(jobId, "사용자에 의해 취소됨");

            log.info("분석 작업 취소: {}", jobId);
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("분석 작업 취소 실패: {}", jobId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    // 사용자의 모든 프레젠테이션 조회
    @GetMapping("/users/{userId}/presentations")
    public ResponseEntity<List<PresentationResponse>> getUserPresentations(@PathVariable UUID userId) {
        log.info("사용자 프레젠테이션 목록 조회 요청 - 사용자: {}", userId);
        
        try {
            List<Presentation> presentations = presentationService.getPresentationsByUserId(userId);
            List<PresentationResponse> response = presentations.stream()
                    .map(PresentationResponse::from)
                    .toList();
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("사용자 프레젠테이션 조회 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    // 프레젠테이션 검색
    @GetMapping("/topics/{topicId}/presentations/search")
    public ResponseEntity<List<PresentationResponse>> searchPresentations(
            @PathVariable UUID topicId,
            @RequestParam String keyword) {
        
        log.info("프레젠테이션 검색 요청 - 토픽: {}, 키워드: {}", topicId, keyword);
        
        try {
            List<Presentation> presentations = presentationService.searchPresentations(topicId, keyword);
            List<PresentationResponse> response = presentations.stream()
                    .map(PresentationResponse::from)
                    .toList();
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("프레젠테이션 검색 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    // 팀의 모든 프레젠테이션 조회
    @GetMapping("/teams/{teamId}/presentations")
    public ResponseEntity<List<PresentationResponse>> getTeamPresentations(
            @PathVariable UUID teamId) {
        
        UUID userId = SecurityUtil.getCurrentUserId();
        log.info("팀 프레젠테이션 목록 조회 요청 - 팀: {}, 사용자: {}", teamId, userId);
        
        authorizationService.requireTeamMemberPermission(teamId);
        
        try {
            List<Presentation> presentations = presentationService.getTeamPresentations(teamId, userId);
            List<PresentationResponse> response = presentations.stream()
                    .map(PresentationResponse::from)
                    .toList();
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("팀 프레젠테이션 조회 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    // 팀 프레젠테이션 수정 (권한 확인)
    @PutMapping("/presentations/{presentationId}/team")
    public ResponseEntity<PresentationResponse> updateTeamPresentation(
            @PathVariable UUID presentationId,
            @RequestBody UpdatePresentationRequest request) {
        
        UUID userId = SecurityUtil.getCurrentUserId();
        log.info("팀 프레젠테이션 수정 요청 - ID: {}, 사용자: {}", presentationId, userId);
        
        authorizationService.requirePresentationModifyPermission(presentationId);
        
        try {
            Presentation presentation = presentationService.updateTeamPresentation(
                    presentationId,
                    userId,
                    request.getTitle(),
                    request.getScript(),
                    request.getGoalTime()
            );
            
            PresentationResponse response = PresentationResponse.from(presentation);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("팀 프레젠테이션 수정 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    // 팀 프레젠테이션 삭제 (권한 확인)
    @DeleteMapping("/presentations/{presentationId}/team")
    public ResponseEntity<Void> deleteTeamPresentation(
            @PathVariable UUID presentationId) {
        
        UUID userId = SecurityUtil.getCurrentUserId();
        log.info("팀 프레젠테이션 삭제 요청 - ID: {}, 사용자: {}", presentationId, userId);
        
        authorizationService.requirePresentationModifyPermission(presentationId);
        
        try {
            presentationService.deleteTeamPresentation(presentationId, userId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("팀 프레젠테이션 삭제 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    // 내부 클래스들
    public static class CreatePresentationRequest {
        private String title;
        private String script;
        private Integer goalTime;
        private String type;
        private String originalFileName;
        private Integer duration;
        
        // getters and setters
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getScript() { return script; }
        public void setScript(String script) { this.script = script; }
        public Integer getGoalTime() { return goalTime; }
        public void setGoalTime(Integer goalTime) { this.goalTime = goalTime; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getOriginalFileName() { return originalFileName; }
        public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }
        public Integer getDuration() { return duration; }
        public void setDuration(Integer duration) { this.duration = duration; }
    }
    
    public static class UpdatePresentationRequest {
        private String title;
        private String script;
        private Integer goalTime;
        
        // getters and setters
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getScript() { return script; }
        public void setScript(String script) { this.script = script; }
        public Integer getGoalTime() { return goalTime; }
        public void setGoalTime(Integer goalTime) { this.goalTime = goalTime; }
    }
} 
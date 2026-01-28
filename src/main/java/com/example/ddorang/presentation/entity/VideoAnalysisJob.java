package com.example.ddorang.presentation.entity;

import com.example.ddorang.common.enums.JobStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

//비동기 영상 분석 관리 엔터티
@Entity
@Table(name = "video_analysis_job")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VideoAnalysisJob {

    @Id @GeneratedValue
    @Column(name = "job_id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "presentation_id", nullable = false)
    private Presentation presentation;

    @Builder.Default
    @Column(name = "video_path")
    private String videoPath = "";  // 업로드된 영상 파일 경로 (초기에는 빈 문자열)

    @Column(name = "original_filename")
    private String originalFilename;  // 원본 파일명

    @Column(name = "file_size")
    private Long fileSize;  // 파일 크기 (분석 시간 예측용)

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private JobStatus status = JobStatus.PENDING;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;  // 실패 시 에러 메시지

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();


    // 간단한 비즈니스 메서드들
    // 작업을 실패 상태로 변경 (필요 시)
    public void markAsFailed(String errorMessage) {
        this.status = JobStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    //작업이 진행 중인지 확인
    public boolean isInProgress() {
        return status.isInProgress();
    }

    // 작업이 완료된건지 확인 (성공/실패 무관)
    public boolean isCompleted() {
        return status.isFinished();
    }

}
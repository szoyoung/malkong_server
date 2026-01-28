package com.example.ddorang.presentation.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "stt_result")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SttResult {

    @Id
    @GeneratedValue
    @Column(name = "stt_result_id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "presentation_id", nullable = false)
    private Presentation presentation;

    @Column(name = "transcription", columnDefinition = "TEXT")
    private String transcription;

    @Column(name = "pronunciation_score")
    private Float pronunciationScore;

    @Column(name = "pronunciation_grade", length = 10)
    private String pronunciationGrade;

    @Column(name = "pronunciation_comment", columnDefinition = "TEXT")
    private String pronunciationComment;

    @Column(name = "adjusted_script", columnDefinition = "TEXT")
    private String adjustedScript;

    @Column(name = "corrected_script", columnDefinition = "TEXT")
    private String correctedScript;
} 
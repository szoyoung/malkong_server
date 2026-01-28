package com.example.ddorang.presentation.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "voice_analysis")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceAnalysis {

    @Id
    @GeneratedValue
    @Column(name = "voice_analysis_id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "presentation_id", nullable = false)
    private Presentation presentation;

    // 음성 강도 분석
    @Column(name = "intensity_grade", length = 10)
    private String intensityGrade;

    @Column(name = "intensity_db")
    private Float intensityDb;

    @Column(name = "intensity_text", columnDefinition = "TEXT")
    private String intensityText;

    // 피치 분석
    @Column(name = "pitch_grade", length = 10)
    private String pitchGrade;

    @Column(name = "pitch_avg")
    private Float pitchAvg;

    @Column(name = "pitch_text", columnDefinition = "TEXT")
    private String pitchText;

    // WPM(Words Per Minute) 분석
    @Column(name = "wpm_grade", length = 10)
    private String wpmGrade;

    @Column(name = "wpm_avg")
    private Float wpmAvg;

    @Column(name = "wpm_comment", columnDefinition = "TEXT")
    private String wpmComment;

    // 불안 분석
    @Column(name = "anxiety_grade", length = 10)
    private String anxietyGrade;

    @Column(name = "anxiety_ratio")
    private Float anxietyRatio;

    @Column(name = "anxiety_comment", columnDefinition = "TEXT")
    private String anxietyComment;
} 
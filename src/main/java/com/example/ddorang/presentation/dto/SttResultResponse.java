package com.example.ddorang.presentation.dto;

import com.example.ddorang.presentation.entity.SttResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SttResultResponse {
    
    private UUID id;
    private UUID presentationId;
    private String presentationTitle;
    private String transcription;
    private Float pronunciationScore;
    private String pronunciationGrade;
    private String pronunciationComment;
    private String adjustedScript;
    private String correctedScript;
    
    // Entity에서 DTO로 변환하는 정적 메서드
    public static SttResultResponse from(SttResult sttResult) {
        return SttResultResponse.builder()
                .id(sttResult.getId())
                .presentationId(sttResult.getPresentation().getId())
                .presentationTitle(sttResult.getPresentation().getTitle())
                .transcription(sttResult.getTranscription())
                .pronunciationScore(sttResult.getPronunciationScore())
                .pronunciationGrade(sttResult.getPronunciationGrade())
                .pronunciationComment(sttResult.getPronunciationComment())
                .adjustedScript(sttResult.getAdjustedScript())
                .correctedScript(sttResult.getCorrectedScript())
                .build();
    }
} 
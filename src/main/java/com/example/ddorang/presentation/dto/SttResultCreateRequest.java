package com.example.ddorang.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SttResultCreateRequest {
    
    private UUID presentationId;
    private String transcription;
    private Float pronunciationScore;
    private String pronunciationGrade;
    private String pronunciationComment;
} 
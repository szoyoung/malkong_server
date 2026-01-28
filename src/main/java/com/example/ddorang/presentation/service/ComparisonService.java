package com.example.ddorang.presentation.service;

import com.example.ddorang.presentation.dto.ComparisonDataDto;
import com.example.ddorang.presentation.dto.ComparisonResponseDto;
import com.example.ddorang.presentation.entity.Presentation;
import com.example.ddorang.presentation.entity.PresentationComparison;
import com.example.ddorang.presentation.entity.VoiceAnalysis;
import com.example.ddorang.presentation.entity.SttResult;
import com.example.ddorang.presentation.repository.PresentationComparisonRepository;
import com.example.ddorang.presentation.repository.PresentationRepository;
import com.example.ddorang.presentation.repository.VoiceAnalysisRepository;
import com.example.ddorang.presentation.repository.SttResultRepository;
import com.example.ddorang.auth.entity.User;
import com.example.ddorang.auth.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComparisonService {
    
    private final PresentationComparisonRepository comparisonRepository;
    private final PresentationRepository presentationRepository;
    private final VoiceAnalysisRepository voiceAnalysisRepository;
    private final SttResultRepository sttResultRepository;
    private final UserRepository userRepository;
    private final FastApiService fastApiService;
    private final ObjectMapper objectMapper;
    
    /**
     * 두 발표를 비교하는 메인 메서드
     */
    @Transactional
    public ComparisonResponseDto comparePresentations(UUID userId, UUID presentationId1, UUID presentationId2) {
        log.info("발표 비교 시작 - 사용자: {}, 발표1: {}, 발표2: {}", userId, presentationId1, presentationId2);
        
        // 1. 기존 비교 기록이 있는지 확인 (있으면 삭제하고 새로 생성)
        Optional<PresentationComparison> existingComparison = 
            comparisonRepository.findExistingComparison(userId, presentationId1, presentationId2);
            
        if (existingComparison.isPresent()) {
            log.info("기존 비교 기록 발견, 삭제 후 최신 데이터로 재생성");
            comparisonRepository.delete(existingComparison.get());
        }
        
        // 2. 발표 및 사용자 정보 조회
        User user = getUserById(userId);
        Presentation presentation1 = getPresentationById(presentationId1);
        Presentation presentation2 = getPresentationById(presentationId2);
        
        // 3. 권한 검증 - 두 발표 모두 해당 사용자의 것인지 확인
        validateUserOwnership(user, presentation1, presentation2);
        
        // 4. 음성 분석 데이터 조회
        VoiceAnalysis analysis1 = getVoiceAnalysis(presentationId1);
        VoiceAnalysis analysis2 = getVoiceAnalysis(presentationId2);
        
        // 5. 비교 데이터 생성
        ComparisonDataDto comparisonData = createComparisonData(analysis1, analysis2);
        
        // 6. AI 기반 최적화된 대본 비교 분석
        Map<String, Object> aiComparisonResult = generateAiComparisonResult(presentation1, presentation2);
        
        // 7. 비교 결과 저장
        PresentationComparison comparison = PresentationComparison.builder()
                .user(user)
                .presentation1(presentation1)
                .presentation2(presentation2)
                .comparisonData(convertToJson(comparisonData))
                .comparisonSummary(convertMapToJson(aiComparisonResult))
                .build();
        
        PresentationComparison savedComparison = comparisonRepository.save(comparison);
        log.info("발표 비교 완료, 결과 저장됨 - ID: {}", savedComparison.getId());
        
        return convertToResponseDto(savedComparison);
    }
    
    /**
     * 두 음성 분석 데이터를 비교하여 ComparisonDataDto 생성
     */
    private ComparisonDataDto createComparisonData(VoiceAnalysis analysis1, VoiceAnalysis analysis2) {
        log.debug("비교 데이터 생성 시작");
        
        Presentation p1 = analysis1.getPresentation();
        Presentation p2 = analysis2.getPresentation();
        
        // STT 결과에서 발음 점수 가져오기
        Float pronunciationScore1 = null;
        Float pronunciationScore2 = null;
        SttResult stt1 = null;
        SttResult stt2 = null;
        
        try {
            stt1 = getSttResult(p1.getId());
            pronunciationScore1 = stt1.getPronunciationScore();
            log.info("발표1의 발음 점수: {}", pronunciationScore1);
        } catch (Exception e) {
            log.warn("발표1의 발음 점수를 가져올 수 없습니다: {}", e.getMessage());
        }
        
        try {
            stt2 = getSttResult(p2.getId());
            pronunciationScore2 = stt2.getPronunciationScore();
            log.info("발표2의 발음 점수: {}", pronunciationScore2);
        } catch (Exception e) {
            log.warn("발표2의 발음 점수를 가져올 수 없습니다: {}", e.getMessage());
        }
        
        // 발표1의 메트릭스 생성
        ComparisonDataDto.PresentationMetrics metrics1 = ComparisonDataDto.PresentationMetrics.builder()
                .presentationId(p1.getId().toString())
                .title(p1.getTitle())
                // 수치 데이터
                .intensityDb(analysis1.getIntensityDb())
                .pitchAvg(analysis1.getPitchAvg())
                .wpmAvg(analysis1.getWpmAvg())
                // 등급 데이터 (백엔드에서 계산된 등급)
                .intensityGrade(analysis1.getIntensityGrade())
                .pitchGrade(analysis1.getPitchGrade())
                .wpmGrade(analysis1.getWpmGrade())
                .anxietyGrade(analysis1.getAnxietyGrade())
                .anxietyRatio(analysis1.getAnxietyRatio())
                .anxietyComment(analysis1.getAnxietyComment())
                .pronunciationScore(pronunciationScore1)
                .pronunciationGrade(stt1 != null ? stt1.getPronunciationGrade() : null)
                .pronunciationComment(stt1 != null ? stt1.getPronunciationComment() : null)
                .build();
                
        // 발표2의 메트릭스 생성
        ComparisonDataDto.PresentationMetrics metrics2 = ComparisonDataDto.PresentationMetrics.builder()
                .presentationId(p2.getId().toString())
                .title(p2.getTitle())
                // 수치 데이터
                .intensityDb(analysis2.getIntensityDb())
                .pitchAvg(analysis2.getPitchAvg())
                .wpmAvg(analysis2.getWpmAvg())
                // 등급 데이터 (백엔드에서 계산된 등급)
                .intensityGrade(analysis2.getIntensityGrade())
                .pitchGrade(analysis2.getPitchGrade())
                .wpmGrade(analysis2.getWpmGrade())
                .anxietyGrade(analysis2.getAnxietyGrade())
                .anxietyRatio(analysis2.getAnxietyRatio())
                .anxietyComment(analysis2.getAnxietyComment())
                .pronunciationScore(pronunciationScore2)
                .pronunciationGrade(stt2 != null ? stt2.getPronunciationGrade() : null)
                .pronunciationComment(stt2 != null ? stt2.getPronunciationComment() : null)
                .build();
        
        return ComparisonDataDto.builder()
                .presentation1(metrics1)
                .presentation2(metrics2)
                .build();
    }
    
    
    /**
     * AI 기반 최적화된 대본 비교 분석 (구조화된 결과 반환)
     */
    private Map<String, Object> generateAiComparisonResult(Presentation p1, Presentation p2) {
        log.debug("AI 대본 비교 분석 시작 - '{}' vs '{}'", p1.getTitle(), p2.getTitle());

        try {
            // 1. 최적화된 대본 조회
            SttResult sttResult1 = getSttResult(p1.getId());
            SttResult sttResult2 = getSttResult(p2.getId());

            // 2. 최적화된 대본 추출 (adjustedScript 우선, 없으면 correctedScript)
            String optimizedScript1 = getOptimizedScript(sttResult1);
            String optimizedScript2 = getOptimizedScript(sttResult2);

            // 3. FastAPI에 대본 비교 요청
            Map<String, Object> comparisonResult = fastApiService.compareOptimizedScripts(
                optimizedScript1, optimizedScript2);

            // 4. FastAPI 응답을 그대로 반환 (구조화된 데이터)
            return comparisonResult;

        } catch (Exception e) {
            log.error("AI 대본 비교 분석 실패: {}", e.getMessage(), e);
            // 실패 시 기본 구조 반환
            Map<String, Object> fallbackResult = new HashMap<>();
            fallbackResult.put("strengths_comparison", "AI 대본 비교 분석을 수행할 수 없습니다.");
            fallbackResult.put("improvement_suggestions", "대본 데이터를 확인해주세요.");
            fallbackResult.put("overall_feedback", "분석 결과를 불러올 수 없습니다.");
            return fallbackResult;
        }
    }

    /**
     * AI 기반 최적화된 대본 비교 분석 (텍스트 요약 반환 - 하위 호환성을 위해 유지)
     */
    private String generateAiComparisonSummary(Presentation p1, Presentation p2) {
        Map<String, Object> result = generateAiComparisonResult(p1, p2);
        return extractComparisonSummary(result);
    }

    /**
     * STT 결과에서 최적화된 대본 추출
     */
    private String getOptimizedScript(SttResult sttResult) {
        if (sttResult.getAdjustedScript() != null && !sttResult.getAdjustedScript().trim().isEmpty()) {
            return sttResult.getAdjustedScript();
        } else if (sttResult.getCorrectedScript() != null && !sttResult.getCorrectedScript().trim().isEmpty()) {
            return sttResult.getCorrectedScript();
        } else {
            throw new RuntimeException("최적화된 대본을 찾을 수 없습니다");
        }
    }

    /**
     * FastAPI 응답에서 비교 요약 추출
     */
    private String extractComparisonSummary(Map<String, Object> comparisonResult) {
        StringBuilder summary = new StringBuilder();

        // 강점 비교
        String strengths = (String) comparisonResult.get("strengths_comparison");
        if (strengths != null) {
            summary.append("【강점 비교】\n").append(strengths).append("\n\n");
        }

        // 개선 제안
        String improvements = (String) comparisonResult.get("improvement_suggestions");
        if (improvements != null) {
            summary.append("【개선 제안】\n").append(improvements).append("\n\n");
        }

        // 전반적 피드백
        String feedback = (String) comparisonResult.get("overall_feedback");
        if (feedback != null) {
            summary.append("【종합 평가】\n").append(feedback);
        }

        return summary.toString().trim();
    }
    
    // === 유틸리티 메서드들 ===
    
    private User getUserById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + userId));
    }
    
    private Presentation getPresentationById(UUID presentationId) {
        return presentationRepository.findById(presentationId)
                .orElseThrow(() -> new RuntimeException("발표를 찾을 수 없습니다: " + presentationId));
    }
    
    private VoiceAnalysis getVoiceAnalysis(UUID presentationId) {
        return voiceAnalysisRepository.findByPresentationId(presentationId)
                .orElseThrow(() -> new RuntimeException("음성 분석 데이터를 찾을 수 없습니다: " + presentationId));
    }

    private SttResult getSttResult(UUID presentationId) {
        return sttResultRepository.findByPresentationId(presentationId)
                .orElseThrow(() -> new RuntimeException("STT 결과 데이터를 찾을 수 없습니다: " + presentationId));
    }
    
    private void validateUserOwnership(User user, Presentation p1, Presentation p2) {
        UUID userId = user.getUserId();
        
        // 발표1의 소유자 확인
        if (!p1.getTopic().getUser().getUserId().equals(userId)) {
            throw new RuntimeException("발표에 대한 권한이 없습니다: " + p1.getId());
        }
        
        // 발표2의 소유자 확인
        if (!p2.getTopic().getUser().getUserId().equals(userId)) {
            throw new RuntimeException("발표에 대한 권한이 없습니다: " + p2.getId());
        }
    }
    
    private String convertToJson(ComparisonDataDto comparisonData) {
        try {
            return objectMapper.writeValueAsString(comparisonData);
        } catch (JsonProcessingException e) {
            log.error("JSON 변환 실패", e);
            throw new RuntimeException("비교 데이터 저장 중 오류가 발생했습니다", e);
        }
    }
    
    private String convertMapToJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.error("Map JSON 변환 실패", e);
            throw new RuntimeException("AI 분석 결과 저장 중 오류가 발생했습니다", e);
        }
    }
    
    /**
     * 기존 비교 기록 조회
     */
    @Transactional(readOnly = true)
    public List<ComparisonResponseDto> getUserComparisons(UUID userId) {
        List<PresentationComparison> comparisons = comparisonRepository.findByUserUserIdOrderByCreatedAtDesc(userId);
        return comparisons.stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());
    }
    
    /**
     * 특정 발표와 관련된 모든 비교 기록 조회
     */
    @Transactional(readOnly = true)
    public List<ComparisonResponseDto> getComparisonsInvolving(UUID userId, UUID presentationId) {
        List<PresentationComparison> comparisons = comparisonRepository.findComparisonsInvolving(userId, presentationId);
        return comparisons.stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());
    }
    
    /**
     * PresentationComparison 엔티티를 ComparisonResponseDto로 변환
     */
    private ComparisonResponseDto convertToResponseDto(PresentationComparison comparison) {
        ComparisonDataDto comparisonData = null;
        
        // JSON 문자열을 ComparisonDataDto로 변환
        try {
            if (comparison.getComparisonData() != null) {
                comparisonData = objectMapper.readValue(
                    comparison.getComparisonData(), 
                    ComparisonDataDto.class
                );
            }
        } catch (JsonProcessingException e) {
            log.error("비교 데이터 파싱 실패: {}", e.getMessage());
        }
        
        // AI 비교 결과 파싱
        Map<String, Object> aiComparisonResult = null;
        try {
            if (comparison.getComparisonSummary() != null) {
                aiComparisonResult = objectMapper.readValue(comparison.getComparisonSummary(), Map.class);
            }
        } catch (JsonProcessingException e) {
            log.error("AI 비교 결과 파싱 실패: {}", e.getMessage());
        }

        return ComparisonResponseDto.builder()
                .id(comparison.getId())
                .presentation1(ComparisonResponseDto.PresentationInfo.builder()
                        .id(comparison.getPresentation1().getId())
                        .title(comparison.getPresentation1().getTitle())
                        .createdAt(comparison.getPresentation1().getCreatedAt())
                        .build())
                .presentation2(ComparisonResponseDto.PresentationInfo.builder()
                        .id(comparison.getPresentation2().getId())
                        .title(comparison.getPresentation2().getTitle())
                        .createdAt(comparison.getPresentation2().getCreatedAt())
                        .build())
                .comparisonData(comparisonData)
                .comparisonSummary(comparison.getComparisonSummary())
                .strengthsComparison(aiComparisonResult != null ? (String) aiComparisonResult.get("strengths_comparison") : null)
                .improvementSuggestions(aiComparisonResult != null ? (String) aiComparisonResult.get("improvement_suggestions") : null)
                .overallFeedback(aiComparisonResult != null ? (String) aiComparisonResult.get("overall_feedback") : null)
                .createdAt(comparison.getCreatedAt())
                .build();
    }
}
package com.example.ddorang.presentation.service;

import com.example.ddorang.presentation.entity.Presentation;
import com.example.ddorang.presentation.entity.VoiceAnalysis;
import com.example.ddorang.presentation.entity.SttResult;
import com.example.ddorang.presentation.entity.PresentationFeedback;
import com.example.ddorang.presentation.repository.VoiceAnalysisRepository;
import com.example.ddorang.presentation.repository.SttResultRepository;
import com.example.ddorang.presentation.repository.PresentationRepository;
import com.example.ddorang.presentation.repository.PresentationFeedbackRepository;
import com.example.ddorang.presentation.dto.VoiceAnalysisResponse;
import com.example.ddorang.presentation.dto.SttResultResponse;
import com.example.ddorang.presentation.dto.PresentationFeedbackResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class VoiceAnalysisService {

    private final VoiceAnalysisRepository voiceAnalysisRepository;
    private final SttResultRepository sttResultRepository;
    private final PresentationRepository presentationRepository;
    private final PresentationFeedbackRepository presentationFeedbackRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * FastAPI 응답 데이터를 받아 VoiceAnalysis, SttResult, PresentationFeedback 저장
     */
    @Transactional
    public void saveAnalysisResults(UUID presentationId, Map<String, Object> fastApiResponse) {

        try {
        Presentation presentation = presentationRepository.findById(presentationId)
                .orElseThrow(() -> new RuntimeException("프레젠테이션을 찾을 수 없습니다: " + presentationId));

            log.info("프레젠테이션 조회 성공: {}", presentation.getTitle());

            // FastAPI 응답 구조 확인 및 처리
            Map<String, Object> analysisResult;
            
            // result 객체가 있는 경우 (새로운 구조)
            if (fastApiResponse.containsKey("result")) {
                analysisResult = (Map<String, Object>) fastApiResponse.get("result");
            } 
            // result 객체가 없는 경우 (기존 구조 - 직접 분석 결과)
            else {
                analysisResult = fastApiResponse;
            }
            
            if (analysisResult == null || analysisResult.isEmpty()) {
                log.error("분석 결과 데이터가 비어있습니다: {}", fastApiResponse);
                throw new RuntimeException("분석 결과 데이터가 올바르지 않습니다.");
            }

            // anxiety_analysis 확인 (문자열이면 등급, Map이면 내부에서 grade 추출)
            Object anxietyAnalysisObj = analysisResult.get("anxiety_analysis");
            if (anxietyAnalysisObj != null && !analysisResult.containsKey("anxiety_grade")) {
                if (anxietyAnalysisObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> anxietyAnalysis = (Map<String, Object>) anxietyAnalysisObj;
                    if (anxietyAnalysis.containsKey("grade")) {
                        analysisResult.put("anxiety_grade", anxietyAnalysis.get("grade"));
                    }
                } else if (anxietyAnalysisObj instanceof String) {
                    analysisResult.put("anxiety_grade", anxietyAnalysisObj);
                } else {
                    analysisResult.put("anxiety_grade", anxietyAnalysisObj.toString());
                }
            }

            // 실제 DB에 분석 결과 저장
            saveVoiceAnalysis(presentation, analysisResult);
            saveSttResult(presentation, analysisResult);
            savePresentationFeedback(presentation, analysisResult);

            // 알림은 VideoAnalysisService의 이벤트 리스너를 통해 자동으로 발송됨
        } catch (Exception e) {
            log.error("분석 결과 저장 중 오류 발생: {}", presentationId, e);
            throw e;
        }
    }

    private void saveVoiceAnalysis(Presentation presentation, Map<String, Object> response) {
        try {
        // 기존 분석 결과가 있으면 삭제
        voiceAnalysisRepository.findByPresentationId(presentation.getId())
                .ifPresent(voiceAnalysisRepository::delete);

        VoiceAnalysis voiceAnalysis = VoiceAnalysis.builder()
                .presentation(presentation)
                // 음성 강도 분석
                .intensityGrade(getStringValue(response, "intensity_grade"))
                .intensityDb(getFloatValue(response, "intensity_db"))
                .intensityText(getStringValue(response, "intensity_text"))
                // 피치 분석
                .pitchGrade(getStringValue(response, "pitch_grade"))
                .pitchAvg(getFloatValue(response, "pitch_avg"))
                .pitchText(getStringValue(response, "pitch_text"))
                // WPM 분석
                .wpmGrade(getStringValue(response, "wpm_grade"))
                .wpmAvg(getFloatValue(response, "wpm_avg"))
                .wpmComment(getStringValue(response, "wpm_comment"))
                // 불안 분석
                .anxietyGrade(getStringValue(response, "anxiety_grade"))
                .anxietyRatio(getFloatValue(response, "anxiety_ratio"))
                .anxietyComment(getStringValue(response, "anxiety_comment"))
                .build();

        voiceAnalysisRepository.save(voiceAnalysis);
        log.info("VoiceAnalysis 저장 완료 - anxiety_grade: {}, anxiety_ratio: {}", 
                voiceAnalysis.getAnxietyGrade(), voiceAnalysis.getAnxietyRatio());
        } catch (Exception e) {
            log.error("VoiceAnalysis 저장 실패: {}", presentation.getId(), e);
            throw e;
        }
    }

    private void saveSttResult(Presentation presentation, Map<String, Object> response) {
        try {
            log.info("SttResult 저장 시작 - 프레젠테이션: {}", presentation.getId());
            
        // 기존 STT 결과가 있으면 삭제
        sttResultRepository.findByPresentationId(presentation.getId())
                .ifPresent(sttResultRepository::delete);

            log.info("기존 SttResult 삭제 완료");

        SttResult sttResult = SttResult.builder()
                .presentation(presentation)
                .transcription(getStringValue(response, "transcription"))
                // pronunciation 필드 처리 (올바른 철자와 오타 모두 지원)
                .pronunciationScore(getPronunciationScore(response))
                .pronunciationGrade(getPronunciationGrade(response))
                .pronunciationComment(getPronunciationComment(response))
                    .adjustedScript(getStringValue(response, "adjusted_script")) // FastAPI에서 제공하지 않을 수 있음
                    .correctedScript(getStringValue(response, "corrected_transcription")) // corrected_transcription으로 변경
                .build();

            log.info("SttResult 객체 생성 완료");

        sttResultRepository.save(sttResult);
        log.info("SttResult 저장 완료: {}", presentation.getId());
        } catch (Exception e) {
            log.error("SttResult 저장 실패: {}", presentation.getId(), e);
            throw e;
        }
    }

    private void savePresentationFeedback(Presentation presentation, Map<String, Object> response) {
        try {
            log.info("PresentationFeedback 저장 시작 - 프레젠테이션: {}", presentation.getId());
            
        // 기존 피드백이 있으면 삭제
        presentationFeedbackRepository.findByPresentationId(presentation.getId())
                .ifPresent(presentationFeedbackRepository::delete);

            log.info("기존 PresentationFeedback 삭제 완료");

            // feedback 객체 추출
            @SuppressWarnings("unchecked")
            Map<String, Object> feedback = (Map<String, Object>) response.get("feedback");
            
            if (feedback != null) {
                log.info("feedback 객체 발견: {}", feedback.keySet());
                
                PresentationFeedback presentationFeedback = PresentationFeedback.builder()
                        .presentation(presentation)
                        .frequentWords(convertToJsonString(feedback.get("frequent_words")))
                        .awkwardSentences(convertToJsonString(feedback.get("awkward_sentences")))
                        .difficultyIssues(convertToJsonString(feedback.get("difficulty_issues")))
                        .predictedQuestions(convertToJsonString(response.get("predicted_questions")))
                        .build();

                log.info("PresentationFeedback 객체 생성 완료");

                presentationFeedbackRepository.save(presentationFeedback);
                log.info("PresentationFeedback 저장 완료: {}", presentation.getId());
            } else {
                log.warn("feedback 객체가 없습니다. 기본 피드백 생성");
                
                // 기본 피드백 생성
                PresentationFeedback presentationFeedback = PresentationFeedback.builder()
                        .presentation(presentation)
                        .frequentWords("[]")
                        .awkwardSentences("[]")
                        .difficultyIssues("[]")
                        .predictedQuestions(convertToJsonString(response.get("predicted_questions")))
                        .build();

                presentationFeedbackRepository.save(presentationFeedback);
                log.info("기본 PresentationFeedback 저장 완료: {}", presentation.getId());
            }
        } catch (Exception e) {
            log.error("PresentationFeedback 저장 실패: {}", presentation.getId(), e);
            throw e;
        }
    }

    /**
     * 프레젠테이션의 음성 분석 결과 조회
     */
    public VoiceAnalysisResponse getVoiceAnalysis(UUID presentationId) {
        return voiceAnalysisRepository.findByPresentationId(presentationId)
                .map(VoiceAnalysisResponse::from)
                .orElse(null);
    }

    /**
     * 프레젠테이션의 STT 결과 조회
     */
    public SttResultResponse getSttResult(UUID presentationId) {
        return sttResultRepository.findByPresentationId(presentationId)
                .map(SttResultResponse::from)
                .orElse(null);
    }

    /**
     * 프레젠테이션의 피드백 결과 조회
     */
    public PresentationFeedbackResponse getPresentationFeedback(UUID presentationId) {
        return presentationFeedbackRepository.findByPresentationId(presentationId)
                .map(PresentationFeedbackResponse::from)
                .orElse(null);
    }

    /**
     * 사용자의 모든 음성 분석 결과 조회
     */
    public List<VoiceAnalysisResponse> getUserVoiceAnalyses(UUID userId) {
        return voiceAnalysisRepository.findByUserId(userId).stream()
                .map(VoiceAnalysisResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 사용자의 모든 STT 결과 조회
     */
    public List<SttResultResponse> getUserSttResults(UUID userId) {
        return sttResultRepository.findByUserId(userId).stream()
                .map(SttResultResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 사용자의 모든 피드백 결과 조회
     */
    public List<PresentationFeedbackResponse> getUserPresentationFeedbacks(UUID userId) {
        return presentationFeedbackRepository.findByUserId(userId).stream()
                .map(PresentationFeedbackResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 분석 결과 존재 여부 확인
     */
    public boolean hasAnalysisResults(UUID presentationId) {
        // 하나라도 분석 결과가 있으면 true 반환
        return voiceAnalysisRepository.existsByPresentationId(presentationId) ||
               sttResultRepository.existsByPresentationId(presentationId) ||
               presentationFeedbackRepository.existsByPresentationId(presentationId);
    }

    private String getStringValue(Map<String, Object> response, String key) {
        Object value = response.get(key);
        return value != null ? value.toString() : null;
    }

    private Float getFloatValue(Map<String, Object> response, String key) {
        Object value = response.get(key);
        if (value == null) return null;
        
        try {
            if (value instanceof Number) {
                return ((Number) value).floatValue();
            }
            return Float.parseFloat(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Float 변환 실패: {} = {}", key, value);
            return null;
        }
    }

    private Double getDoubleValue(Map<String, Object> response, String key) {
        Object value = response.get(key);
        if (value == null) return 0.0;
        
        try {
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Double 변환 실패: {} = {}", key, value);
            return 0.0;
        }
    }

    private String convertToJsonString(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("JSON 변환 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * pronunciation_score 필드 추출 (올바른 철자와 오타 모두 지원)
     */
    private Float getPronunciationScore(Map<String, Object> response) {
        Float score = getFloatValue(response, "pronunciation_score");
        if (score != null) return score;
        // 오타 지원: pronounciation_score
        return getFloatValue(response, "pronounciation_score");
    }

    /**
     * pronunciation_grade 필드 추출 (올바른 철자와 오타 모두 지원)
     */
    private String getPronunciationGrade(Map<String, Object> response) {
        String grade = getStringValue(response, "pronunciation_grade");
        if (grade != null) return grade;
        // 오타 지원: pronounciation_grade
        return getStringValue(response, "pronounciation_grade");
    }

    /**
     * pronunciation_comment 필드 추출 (올바른 철자, 오타, text 필드 모두 지원)
     */
    private String getPronunciationComment(Map<String, Object> response) {
        // 1. 올바른 철자: pronunciation_comment
        String comment = getStringValue(response, "pronunciation_comment");
        if (comment != null) return comment;
        
        // 2. 올바른 철자 + text: pronunciation_text
        comment = getStringValue(response, "pronunciation_text");
        if (comment != null) return comment;
        
        // 3. 오타: pronounciation_text
        return getStringValue(response, "pronounciation_text");
    }
} 
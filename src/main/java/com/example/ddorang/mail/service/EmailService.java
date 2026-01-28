package com.example.ddorang.mail.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    
    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    public void sendEmailCode(String to, String code) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

            helper.setTo(to);
            helper.setSubject("[말콩] 이메일 인증 코드");

            String html = """
                <div style="font-family: Arial, sans-serif; padding: 20px; border: 1px solid #eee;">
                  <h2 style="color: #5A67D8;">말콩 이메일 인증</h2>
                  <p>안녕하세요! 아래 인증 코드를 입력해 주세요:</p>
                  <div style="font-size: 24px; font-weight: bold; color: #2D3748; margin: 16px 0;">
                    %s
                  </div>
                  <p style="font-size: 12px; color: gray;">이 코드는 3분 뒤 만료됩니다.</p>
                </div>
                """.formatted(code);

            helper.setText(html, true);
            mailSender.send(message);

        } catch (Exception e) {
            throw new RuntimeException("메일 전송 실패", e);
        }
    }

    /**
     * AI 분석 완료 이메일 발송
     * @param to 수신자 이메일
     * @param userName 수신자 이름
     * @param presentationTitle 발표 제목
     * @param presentationId 발표 ID
     */
    public void sendAnalysisCompleteEmail(String to, String userName, String presentationTitle, UUID presentationId) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

            helper.setTo(to);
            helper.setSubject("[말콩] AI 분석이 완료되었습니다");

            String viewUrl = String.format("%s/video-analysis/%s", frontendUrl, presentationId);
            
            String html = """
                <div style="font-family: Arial, sans-serif; padding: 20px; max-width: 600px; margin: 0 auto;">
                  <div style="background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); padding: 30px; border-radius: 10px 10px 0 0;">
                    <h2 style="color: white; margin: 0;">🎬 분석 완료 알림</h2>
                  </div>
                  <div style="padding: 30px; border: 1px solid #eee; border-top: none; border-radius: 0 0 10px 10px;">
                    <p style="color: #2D3748; font-size: 16px;">안녕하세요, %s님!</p>
                    <p style="color: #2D3748;">발표 '<strong>%s</strong>'의 AI 분석이 완료되었습니다.</p>
                    <p style="color: #718096; font-size: 14px; margin-top: 30px;">발음, 감정, 속도, 시선 등 다양한 분석 결과를 확인할 수 있습니다.</p>
                    <div style="text-align: center; margin: 40px 0;">
                      <a href="%s" style="background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: white; padding: 15px 40px; text-decoration: none; border-radius: 5px; display: inline-block; font-weight: bold;">분석 결과 확인하기</a>
                    </div>
                    <p style="color: #A0AEC0; font-size: 12px; border-top: 1px solid #eee; padding-top: 20px; margin-top: 30px;">이 이메일은 자동으로 발송되었습니다.</p>
                  </div>
                </div>
                """.formatted(userName, presentationTitle, viewUrl);

            helper.setText(html, true);
            mailSender.send(message);
            
            log.info("AI 분석 완료 이메일 발송 성공 - 수신자: {}", to);

        } catch (Exception e) {
            log.error("AI 분석 완료 이메일 발송 실패 - 수신자: {}", to, e);
        }
    }

}

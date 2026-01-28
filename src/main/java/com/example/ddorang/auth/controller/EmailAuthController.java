package com.example.ddorang.auth.controller;

import com.example.ddorang.auth.dto.EmailLoginRequest;
import com.example.ddorang.auth.dto.PasswordResetRequest;
import com.example.ddorang.auth.dto.SignupRequest;
import com.example.ddorang.auth.dto.TokenResponse;
import com.example.ddorang.auth.dto.UserInfoResponse;
import com.example.ddorang.auth.entity.User;
import com.example.ddorang.auth.service.AuthService;
import com.example.ddorang.auth.service.TokenService;
import com.example.ddorang.auth.security.JwtTokenProvider;
import com.example.ddorang.common.ApiPaths;
import com.example.ddorang.mail.service.VerificationCodeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping({ApiPaths.AUTH, "/auth"})  // /api/auth와 /auth 모두 지원
public class EmailAuthController {

    private final AuthService authService;
    private final VerificationCodeService verificationCodeService;
    private final TokenService tokenService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody @Valid EmailLoginRequest request) {
        TokenResponse tokens = authService.login(request);
        return ResponseEntity.ok(tokens);
    }

    @PostMapping(ApiPaths.TOKEN_REFRESH)
    public ResponseEntity<?> refresh(@RequestParam String email) {
        try {
            String newAccessToken = authService.reissueAccessTokenByEmail(email);
            return ResponseEntity.ok(Map.of("accessToken", newAccessToken));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "refresh_token_expired", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "internal_error", "message", "토큰 재발급 중 오류가 발생했습니다."));
        }
    }

    @PostMapping(ApiPaths.TOKEN_LOGOUT)
    public ResponseEntity<?> logout(@RequestParam String email) {
        try {
            authService.logoutByEmail(email);
            return ResponseEntity.ok("로그아웃 되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "logout_failed", "message", "로그아웃 중 오류가 발생했습니다."));
        }
    }


    @PostMapping("/email/code/signup")
    public ResponseEntity<Void> sendVerificationCode(@RequestBody Map<String, String> body) {
        authService.requestSignupCode(body.get("email"));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/email/code/signup/verify")
    public ResponseEntity<?> verifyEmail(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String code = body.get("code");

        boolean result = verificationCodeService.verifyCode(email, code);
        if (!result) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("인증 실패");
        }

        return ResponseEntity.ok("인증 성공");
    }

    @PostMapping("/signup")
    public ResponseEntity<Void> signup(@RequestBody @Valid SignupRequest request) {
        authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/email/code/reset")
    public ResponseEntity<Void> requestPasswordReset(@RequestBody Map<String, String> body) {
        authService.requestResetCode(body.get("email"));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/email/code/reset/verify")
    public ResponseEntity<?> verifyResetCode(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String code = body.get("code");

        boolean verified = verificationCodeService.verifyResetCode(email, code);
        if (!verified) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("인증 실패");
        }
        return ResponseEntity.ok("인증 성공");
    }

    @PatchMapping("/password/reset")
    public ResponseEntity<?> confirmNewPassword(@RequestBody @Valid PasswordResetRequest request) {
        if (!verificationCodeService.isResetVerified(request.getEmail())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("이메일 인증이 필요합니다.");
        }

        authService.updatePassword(request.getEmail(), request.getNewPassword());
        return ResponseEntity.ok("비밀번호가 재설정되었습니다.");
    }

    // 현재 사용자 정보 조회
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@RequestHeader("Authorization") String authHeader) {
        try {
            // Bearer 토큰에서 실제 토큰 추출
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "missing_token", "message", "인증 토큰이 필요합니다."));
            }

            String token = authHeader.substring(7);

            // 토큰 유효성 검사
            if (!jwtTokenProvider.validateToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "invalid_token", "message", "유효하지 않은 토큰입니다."));
            }

            // 토큰에서 이메일 추출
            String email = jwtTokenProvider.getUserEmailFromToken(token);

            // 사용자 정보 조회
            User user = authService.getUserByEmail(email);

            // 응답 DTO 생성
            UserInfoResponse userInfo = new UserInfoResponse(
                    user.getUserId(),
                    user.getEmail(),
                    user.getName(),
                    user.getProvider().toString(),
                    user.getProfileImage(),
                    user.getNotificationEnabled()
            );

            return ResponseEntity.ok(userInfo);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "internal_error", "message", "사용자 정보 조회 중 오류가 발생했습니다."));
        }
    }
}
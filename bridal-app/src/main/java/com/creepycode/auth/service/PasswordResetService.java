package com.creepycode.auth.service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.creepycode.auth.entity.ResetToken;
import com.creepycode.auth.entity.User;
import com.creepycode.auth.repository.ResetTokenRepository;
import com.creepycode.auth.repository.UserRepository;

/**
 * パスワードリセット申請（P-04）および再設定（P-05）の処理。
 * 基本設計書 6-2・基本設計書 v1.6 に基づく。
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    // トークンの有効期間（基本設計書 6-2：申請から30分後）
    public static final int EXPIRATION_MINUTES = 30;

    private final UserRepository userRepository;
    private final ResetTokenRepository resetTokenRepository;
    private final PasswordEncoder passwordEncoder;

    public PasswordResetService(UserRepository userRepository,
            ResetTokenRepository resetTokenRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * パスワードリセットを申請する。
     * メールアドレスが登録されている場合はトークンを発行して保存し、基本設計書 v1.6 に従ってログに出力する。
     * 登録されていないメールアドレスの場合でも、登録の有無が画面で分からないようエラーにせず安全に終了する。
     * 
     * @param email 申請されたメールアドレス
     * @return 発行されたトークン（未登録の場合は empty）
     */
    @Transactional
    public Optional<String> requestPasswordReset(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        String normalizedEmail = normalizeEmail(email);
        Optional<User> userOpt = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(normalizedEmail);

        if (userOpt.isEmpty()) {
            // セキュリティ要件：登録の有無を外部に推測させないため、エラーにせず成功と同じように扱う
            log.info("パスワード再設定が申請されましたが、該当する有効な会員はいませんでした");
            return Optional.empty();
        }

        User user = userOpt.get();
        LocalDateTime now = LocalDateTime.now();
        String token = generateToken();

        ResetToken resetToken = new ResetToken();
        resetToken.setUserId(user.getUserId());
        resetToken.setToken(token);
        resetToken.setCreatedAt(now);
        resetToken.setExpiresAt(now.plusMinutes(EXPIRATION_MINUTES));
        resetTokenRepository.save(resetToken);

        // 基本設計書 v1.6：ローカル環境では本物のメールは送らず、リセットURLをログに出力して代替する
        log.info("【パスワード再設定】URL: /auth/password-reset/confirm?token={}", token);

        return Optional.of(token);
    }

    /**
     * トークンが現在有効（存在する・未使用・有効期限内）かどうかを検証する。
     * 
     * @param token 検証するトークン文字列
     * @return 有効な場合は true、それ以外は false
     */
    @Transactional(readOnly = true)
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        return resetTokenRepository.findByToken(token.strip())
                .map(t -> t.isValid(now))
                .orElse(false);
    }

    /**
     * パスワードを再設定する。
     * トークンが有効な場合のみ、パスワードをハッシュ化して USERS に保存し、トークンを使用済みに更新する。
     * 
     * @param token トークン文字列
     * @param newPassword 新しいパスワード（平文）
     * @return 成功した場合は true、トークンが無効または失敗した場合は false
     */
    @Transactional
    public boolean resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank() || newPassword == null || newPassword.isBlank()) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        Optional<ResetToken> tokenOpt = resetTokenRepository.findByToken(token.strip());

        if (tokenOpt.isEmpty() || !tokenOpt.get().isValid(now)) {
            log.warn("無効または期限切れのトークンでパスワード再設定が試行されました");
            return false;
        }

        ResetToken resetToken = tokenOpt.get();
        Optional<User> userOpt = userRepository.findById(resetToken.getUserId());
        if (userOpt.isEmpty() || userOpt.get().getDeletedAt() != null) {
            log.warn("トークンに該当する会員が存在しないか、退会済みです（USER_ID={}）", resetToken.getUserId());
            return false;
        }

        User user = userOpt.get();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(now);
        userRepository.save(user);

        resetToken.setUsedAt(now);
        resetTokenRepository.save(resetToken);

        log.info("パスワードを正常に再設定しました（USER_ID={}）", user.getUserId());
        return true;
    }

    /** 32文字のランダムな使い捨てトークン文字列を生成する。 */
    private String generateToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** メールアドレスの前後の空白を取り、小文字にそろえる。 */
    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}


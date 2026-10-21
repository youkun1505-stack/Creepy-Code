package com.creepycode.auth.service;

import java.time.LocalDateTime;
import java.util.Locale;

import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.creepycode.auth.dto.RegisterForm;
import com.creepycode.auth.entity.User;
import com.creepycode.auth.entity.UserRole;
import com.creepycode.auth.repository.UserRepository;
import com.creepycode.common.exception.EmailAlreadyRegisteredException;

/**
 * 会員登録（P-02）の処理。USERS に一般会員（CUSTOMER）として保存する（基本設計書 6-2）。
 */
@Service
public class RegisterService {

    private static final Logger log = LoggerFactory.getLogger(RegisterService.class);

    // USERS.EMAIL の UNIQUE 制約の名前（db/01_create_tables.sql。PostgreSQL では小文字になる）
    private static final String EMAIL_UNIQUE_CONSTRAINT = "uk_users_email";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public RegisterService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * そのメールアドレスが、すでに登録されているかを返す（大文字・小文字は区別しない）。
     * 削除済み（DELETED_AT あり）の会員も「登録済み」として扱う。USERS.EMAIL は UNIQUE なので、削除済みでも同じアドレスでは登録できないため。
     */
    @Transactional(readOnly = true)
    public boolean isEmailRegistered(String email) {
        return userRepository.existsByEmailIgnoreCase(normalizeEmail(email));
    }

    /**
     * 会員を登録し、登録した会員を返す（このあと LoginService でログインさせる）。
     * パスワードは BCrypt でハッシュ化し、メールアドレスは小文字にそろえて保存する。
     * 同じメールアドレスが同時に登録されたとき（UK_USERS_EMAIL に当たったとき）は EmailAlreadyRegisteredException を投げる。
     */
    @Transactional
    public LoginUser register(RegisterForm form) {
        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setEmail(normalizeEmail(form.getEmail()));
        user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        user.setName(form.getName().strip());
        user.setRole(UserRole.CUSTOMER);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        User saved;
        try {
            // すぐに INSERT して、UNIQUE 制約に当たったらここで分かるようにする
            saved = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // メールアドレスの UNIQUE 制約に当たったときだけ「登録済み」にする。ほかの原因はそのまま投げる（原因と違う案内を出さないため）
            if (isEmailUniqueViolation(e)) {
                throw new EmailAlreadyRegisteredException(e);
            }
            throw e;
        }
        // 個人情報なので、氏名・メールアドレスはログに出さない
        log.info("会員を登録しました（USER_ID={}）", saved.getUserId());
        return LoginUser.from(saved);
    }

    /** DB のエラーが、USERS.EMAIL の UNIQUE 制約（UK_USERS_EMAIL）に当たったものかを返す。 */
    private boolean isEmailUniqueViolation(DataIntegrityViolationException e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                return EMAIL_UNIQUE_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName());
            }
            cause = cause.getCause();
        }
        // 制約名が取れないときは、DB のメッセージに制約名が含まれるかで判断する
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains(EMAIL_UNIQUE_CONSTRAINT);
    }

    /** メールアドレスの前後の空白を取り、小文字にそろえる。 */
    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}

package com.example.movierecommendation.service;

import com.example.movierecommendation.dto.RegisterRequest;
import com.example.movierecommendation.entity.User;
import com.example.movierecommendation.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private VerificationService verificationService;

    public User getCurrentUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Transactional
    public User register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already taken");
        }
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole("USER");
        user.setIsActive(true);
        user.setIsEmailVerified(false);
        User savedUser = userRepository.save(user);
        verificationService.sendCode(savedUser, VerificationPurpose.EMAIL_VERIFY);
        return savedUser;
    }

    @Transactional
    public User updateProfile(Integer userId, String username, String email) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (!user.getUsername().equals(username) && userRepository.existsByUsername(username)) {
            throw new RuntimeException("Username already taken");
        }
        if (!user.getEmail().equals(email) && userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already registered");
        }
        user.setUsername(username);
        if (!user.getEmail().equals(email)) {
            if (Boolean.TRUE.equals(user.getIsEmailVerified())) {
                user.setPreviousEmail(user.getEmail());
            }
            user.setEmail(email);
            user.setIsEmailVerified(false);
        }
        return userRepository.save(user);
    }

    @Transactional
    public void changePassword(Integer userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public void changePassword(Integer userId, String currentPassword, String newPassword) {
        boolean isVi = "vi".equalsIgnoreCase(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage());
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException(isVi ? "Mật khẩu mới phải có ít nhất 6 ký tự" : "New password must be at least 6 characters");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException(isVi ? "Sai mật khẩu hiện tại. Hãy thử lại." : "Incorrect current password. Please try again.");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException(isVi ? "Mật khẩu mới phải khác mật khẩu hiện tại." : "New password must be different from the current password.");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public Page<User> getAllUsersPaged(int page, int size) {
        return getAllUsersPaged(null, page, size);
    }

    public Page<User> getAllUsersPaged(String keyword, int page, int size) {
        if (keyword != null && !keyword.trim().isEmpty()) {
            return userRepository.searchUsersPaged(keyword.trim(), PageRequest.of(page, size, Sort.by("userId").ascending()));
        }
        return userRepository.findByDeletedAtIsNull(PageRequest.of(page, size, Sort.by("userId").ascending()));
    }

    @Transactional
    public void toggleUserStatus(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setIsActive(!user.getIsActive());
        userRepository.save(user);
    }

    @Transactional
    public void deleteUser(Integer userId) {
        userRepository.deleteById(userId);
    }

    public long countUsers() {
        return userRepository.count();
    }

    public Optional<User> findById(Integer userId) {
        return userRepository.findById(userId);
    }

    @Transactional
    public void changePasswordWithVerification(Integer userId, String currentPassword,
                                               String newPassword, String verificationCode) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("New password must be at least 6 characters");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        verificationService.verifyOrThrow(user, verificationCode, VerificationPurpose.PASSWORD_CHANGE);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setIsEmailVerified(true);
        userRepository.save(user);
    }

    @Transactional
    public void sendEmailVerification(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (Boolean.TRUE.equals(user.getIsEmailVerified())) {
            throw new RuntimeException("Email is already verified.");
        }
        verificationService.sendCode(user, VerificationPurpose.EMAIL_VERIFY);
    }

    @Transactional
    public void sendPasswordChangeCode(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (Boolean.FALSE.equals(user.getIsEmailVerified())) {
            throw new EmailNotVerifiedException("Email is not verified", user.getEmail());
        }
        verificationService.sendCode(user, VerificationPurpose.PASSWORD_CHANGE);
    }

    @Transactional
    public void confirmEmail(Integer userId, String code) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        verificationService.verifyOrThrow(user, code, VerificationPurpose.EMAIL_VERIFY);
        user.setIsEmailVerified(true);
        userRepository.save(user);
    }

    @Transactional
    public String sendPasswordReset(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Account with this email was not found"));
        if (Boolean.FALSE.equals(user.getIsEmailVerified())) {
            throw new EmailNotVerifiedException("Email is not verified", email);
        }
        verificationService.sendCode(user, VerificationPurpose.PASSWORD_RESET);
        return verificationService.maskEmail(user.getEmail());
    }

    @Transactional
    public void resetPassword(String email, String code, String newPassword) {
        boolean isVi = "vi".equalsIgnoreCase(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage());
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException(isVi ? "Mật khẩu mới phải có ít nhất 6 ký tự" : "New password must be at least 6 characters");
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Account with this email was not found"));
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException(isVi ? "Mật khẩu mới phải khác mật khẩu hiện tại." : "New password must be different from the current password.");
        }
        verificationService.verifyOrThrow(user, code, VerificationPurpose.PASSWORD_RESET);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setIsEmailVerified(true);
        userRepository.save(user);
    }

    public void verifyResetCodeOnly(String email, String code) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Account with this email was not found"));
        verificationService.checkCodeOnlyOrThrow(user, code, VerificationPurpose.PASSWORD_RESET);
    }

    @Transactional
    public String revertEmailChange(String currentEmail) {
        User user = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        String previous = user.getPreviousEmail();
        if (previous == null || previous.trim().isEmpty()) {
            throw new IllegalStateException("No previous email found to revert to.");
        }
        
        // Safety check
        Optional<User> existing = userRepository.findByEmail(previous);
        if (existing.isPresent() && !existing.get().getUserId().equals(user.getUserId())) {
            throw new IllegalStateException("The original email is now registered to another account.");
        }
        
        user.setEmail(previous);
        user.setIsEmailVerified(true);
        user.setPreviousEmail(null);
        userRepository.save(user);
        return previous;
    }

}

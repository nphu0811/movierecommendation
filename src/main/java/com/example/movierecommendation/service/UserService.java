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
        return userRepository.save(user);
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

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public Page<User> getAllUsersPaged(int page, int size) {
        // Sort by userId ascending so the smallest IDs appear first in the admin table
        return userRepository.findAll(PageRequest.of(page, size, Sort.by("userId").ascending()));
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
            throw new RuntimeException("Email đã được xác thực.");
        }
        verificationService.sendCode(user, VerificationPurpose.EMAIL_VERIFY);
    }

    @Transactional
    public void sendPasswordChangeCode(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
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
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản với email này"));
        verificationService.sendCode(user, VerificationPurpose.PASSWORD_RESET);
        return verificationService.maskEmail(user.getEmail());
    }

    @Transactional
    public void resetPassword(String email, String code, String newPassword) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("Mật khẩu mới phải có ít nhất 6 ký tự");
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản với email này"));
        verificationService.verifyOrThrow(user, code, VerificationPurpose.PASSWORD_RESET);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setIsEmailVerified(true);
        userRepository.save(user);
    }

}

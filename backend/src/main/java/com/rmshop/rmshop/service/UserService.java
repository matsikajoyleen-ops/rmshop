package com.rmshop.rmshop.service;

import com.rmshop.rmshop.exception.ApiException;
import com.rmshop.rmshop.exception.ForbiddenException;
import com.rmshop.rmshop.model.AttendanceLog;
import com.rmshop.rmshop.model.User;
import com.rmshop.rmshop.repository.AttendanceLogRepository;
import com.rmshop.rmshop.repository.UserRepository;
import com.rmshop.rmshop.validation.InputValidator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    private final LoginAttemptService loginAttemptService;

    public UserService(UserRepository userRepository, AttendanceLogRepository attendanceLogRepository,
                       LoginAttemptService loginAttemptService) {
        this.userRepository = userRepository;
        this.attendanceLogRepository = attendanceLogRepository;
        this.loginAttemptService = loginAttemptService;
    }

    public User createUser(String fullName, User.Role role, String rawAccessCode, String rawEmail) {
        String hashedCode = hashAccessCode(rawAccessCode);
        if (userRepository.existsByAccessCode(hashedCode)) {
            throw new IllegalArgumentException("That access code is already assigned to another account.");
        }
        User user = new User(fullName, role, hashedCode);
        if (rawEmail != null && !rawEmail.isBlank()) {
            user.setEmail(requireUnusedEmail(rawEmail, null));
        }
        return userRepository.save(user);
    }

    public void deactivateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No user with id " + id));
        user.setActive(false);
        userRepository.save(user);
    }

    public void changeAccessCode(Long id, String newRawAccessCode) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No user with id " + id));
        String hashedCode = hashAccessCode(newRawAccessCode);
        if (userRepository.existsByAccessCode(hashedCode)) {
            throw new IllegalArgumentException("That access code is already assigned to another account.");
        }
        user.setAccessCode(hashedCode);
        userRepository.save(user);
    }

    public User updateEmail(Long id, String rawEmail) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No user with id " + id));
        user.setEmail(requireUnusedEmail(rawEmail, id));
        return userRepository.save(user);
    }

    /**
     * Logs in by access code. {@code clientKey} identifies the device, so
     * repeated wrong codes from it lead to a temporary lockout. Mistyped
     * input (too short, letters...) is rejected without counting as an attempt.
     */
    @Transactional
    public User login(String rawAccessCode, String clientKey) {
        loginAttemptService.checkNotLocked(clientKey);
        InputValidator.validateSixDigitCode(rawAccessCode, "accessCode", "access code");

        User user = userRepository.findByAccessCode(hashAccessCode(rawAccessCode))
                .orElseThrow(() -> loginAttemptService.recordFailure(clientKey));
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DEACTIVATED",
                    "This account has been deactivated. Ask your manager to reactivate it.", "accessCode")
                    .with("severity", "danger");
        }
        loginAttemptService.recordSuccess(clientKey);
        attendanceLogRepository.save(new AttendanceLog(user));
        return user;
    }

    // For clock-ins made by mistake. The log must belong to this user, so a
    // wrong id in the URL can't remove someone else's record.
    public void deleteAttendance(Long userId, Long logId) {
        AttendanceLog log = attendanceLogRepository.findById(logId)
                .filter(entry -> entry.getEmployee().getId().equals(userId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ATTENDANCE_NOT_FOUND",
                        "That clock-in record doesn't exist for this staff member. It may already have been removed."));
        attendanceLogRepository.delete(log);
    }

    public List<User> listUsers() {
        return userRepository.findAll();
    }

    // Every manager-only endpoint calls this first. Throws if the requester
    // isn't a logged-in, active manager; ApiExceptionHandler turns that into a 403.
    public User requireManager(Long userId) {
        if (userId == null) {
            throw new ForbiddenException("Log in required.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ForbiddenException("Log in required."));
        if (!user.isActive() || user.getRole() != User.Role.MANAGER) {
            throw new ForbiddenException("Manager access required.");
        }
        return user;
    }

    static String hashAccessCode(String rawAccessCode) {
        return Hashing.sha256(rawAccessCode);
    }

    private String requireUnusedEmail(String rawEmail, Long ownerId) {
        String email = InputValidator.validateEmail(rawEmail);
        userRepository.findByEmail(email)
                .filter(existing -> !existing.getId().equals(ownerId))
                .ifPresent(existing -> {
                    throw new ApiException(HttpStatus.CONFLICT, "EMAIL_IN_USE",
                            "That email already belongs to another staff account.", "email");
                });
        return email;
    }
}

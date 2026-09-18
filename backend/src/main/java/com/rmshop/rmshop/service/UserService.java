package com.rmshop.rmshop.service;

import com.rmshop.rmshop.model.AttendanceLog;
import com.rmshop.rmshop.model.User;
import com.rmshop.rmshop.repository.AttendanceLogRepository;
import com.rmshop.rmshop.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final AttendanceLogRepository attendanceLogRepository;

    public UserService(UserRepository userRepository, AttendanceLogRepository attendanceLogRepository) {
        this.userRepository = userRepository;
        this.attendanceLogRepository = attendanceLogRepository;
    }

    public User createUser(String fullName, User.Role role, String rawAccessCode) {
        String hashedCode = hashAccessCode(rawAccessCode);
        if (userRepository.existsByAccessCode(hashedCode)) {
            throw new IllegalArgumentException("That access code is already assigned to another account.");
        }
        User user = new User(fullName, role, hashedCode);
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

    @Transactional
    public Optional<User> authenticate(String rawAccessCode) {
        Optional<User> found = userRepository.findByAccessCodeAndActiveTrue(hashAccessCode(rawAccessCode));
        found.ifPresent(user -> attendanceLogRepository.save(new AttendanceLog(user)));
        return found;
    }

    public List<User> listUsers() {
        return userRepository.findAll();
    }

    private String hashAccessCode(String rawAccessCode) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawAccessCode.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
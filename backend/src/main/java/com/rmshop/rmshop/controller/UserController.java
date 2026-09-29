package com.rmshop.rmshop.controller;

import com.rmshop.rmshop.model.User;
import com.rmshop.rmshop.repository.AttendanceLogRepository;
import com.rmshop.rmshop.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final AttendanceLogRepository attendanceLogRepository;

    public UserController(UserService userService, AttendanceLogRepository attendanceLogRepository) {
        this.userService = userService;
        this.attendanceLogRepository = attendanceLogRepository;
    }

    // Open: this IS the login step, there's no logged-in user yet to check
    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@RequestBody LoginRequest request) {
        return userService.authenticate(request.accessCode())
                .map(user -> ResponseEntity.ok(UserResponse.from(user)))
                .orElse(ResponseEntity.status(401).build());
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@RequestHeader(value = "X-User-Id", required = false) Long requesterId, @RequestBody CreateUserRequest request) {
        userService.requireManager(requesterId);
        User user = userService.createUser(request.fullName(), request.role(), request.accessCode());
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @PutMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateUser(@RequestHeader(value = "X-User-Id", required = false) Long requesterId, @PathVariable Long id) {
        userService.requireManager(requesterId);
        userService.deactivateUser(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/access-code")
    public ResponseEntity<Void> changeAccessCode(@RequestHeader(value = "X-User-Id", required = false) Long requesterId, @PathVariable Long id, @RequestBody ChangeAccessCodeRequest request) {
        userService.requireManager(requesterId);
        userService.changeAccessCode(id, request.newAccessCode());
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public List<UserResponse> listUsers(@RequestHeader(value = "X-User-Id", required = false) Long requesterId) {
        userService.requireManager(requesterId);
        return userService.listUsers().stream().map(UserResponse::from).toList();
    }

    @GetMapping("/{id}/attendance")
    public List<AttendanceLogResponse> getAttendance(@RequestHeader(value = "X-User-Id", required = false) Long requesterId, @PathVariable Long id) {
        userService.requireManager(requesterId);
        return attendanceLogRepository.findByEmployeeIdOrderByLoginTimeDesc(id).stream()
                .map(log -> new AttendanceLogResponse(log.getId(), log.getLoginTime()))
                .toList();
    }

    public record LoginRequest(String accessCode) {}
    public record CreateUserRequest(String fullName, User.Role role, String accessCode) {}
    public record ChangeAccessCodeRequest(String newAccessCode) {}
    public record AttendanceLogResponse(Long id, LocalDateTime loginTime) {}

    public record UserResponse(Long id, String fullName, User.Role role, boolean active) {
        static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getFullName(), user.getRole(), user.isActive());
        }
    }
}
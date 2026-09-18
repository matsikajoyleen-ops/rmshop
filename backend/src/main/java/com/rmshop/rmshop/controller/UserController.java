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

    // FR-1, FR-13: manager and employee login share this endpoint
    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@RequestBody LoginRequest request) {
        return userService.authenticate(request.accessCode())
                .map(user -> ResponseEntity.ok(UserResponse.from(user)))
                .orElse(ResponseEntity.status(401).build());
    }

    // FR-2: manager adds a new employee (or manager) account
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@RequestBody CreateUserRequest request) {
        User user = userService.createUser(request.fullName(), request.role(), request.accessCode());
        return ResponseEntity.ok(UserResponse.from(user));
    }

    // FR-3: manager deactivates an employee instead of deleting them
    @PutMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateUser(@PathVariable Long id) {
        userService.deactivateUser(id);
        return ResponseEntity.noContent().build();
    }

    // FR-4: manager changes an employee's access code
    @PutMapping("/{id}/access-code")
    public ResponseEntity<Void> changeAccessCode(@PathVariable Long id, @RequestBody ChangeAccessCodeRequest request) {
        userService.changeAccessCode(id, request.newAccessCode());
        return ResponseEntity.noContent().build();
    }

    // Staff Management screen: list all users
    @GetMapping
    public List<UserResponse> listUsers() {
        return userService.listUsers().stream().map(UserResponse::from).toList();
    }

    // FR-14: manager views an employee's login history
    @GetMapping("/{id}/attendance")
    public List<AttendanceLogResponse> getAttendance(@PathVariable Long id) {
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
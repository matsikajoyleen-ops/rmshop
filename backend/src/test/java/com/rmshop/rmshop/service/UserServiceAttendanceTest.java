package com.rmshop.rmshop.service;

import com.rmshop.rmshop.exception.ApiException;
import com.rmshop.rmshop.model.AttendanceLog;
import com.rmshop.rmshop.model.User;
import com.rmshop.rmshop.repository.AttendanceLogRepository;
import com.rmshop.rmshop.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// White-box: each branch of UserService.deleteAttendance.
class UserServiceAttendanceTest {

    private AttendanceLogRepository logs;
    private UserService service;
    private AttendanceLog tinosLog;

    @BeforeEach
    void setUp() {
        logs = mock(AttendanceLogRepository.class);
        service = new UserService(mock(UserRepository.class), logs, mock(LoginAttemptService.class));

        User tino = new User("Tino", User.Role.EMPLOYEE, "x");
        tino.setId(3L);
        tinosLog = new AttendanceLog(tino);
        tinosLog.setId(46L);
        when(logs.findById(46L)).thenReturn(Optional.of(tinosLog));
        when(logs.findById(99L)).thenReturn(Optional.empty());
    }

    @Test
    void deletesWhenTheLogBelongsToTheUser() {
        service.deleteAttendance(3L, 46L);
        verify(logs).delete(tinosLog);
    }

    @Test
    void refusesWhenTheLogBelongsToSomeoneElse() {
        ApiException error = catchThrowableOfType(ApiException.class, () -> service.deleteAttendance(4L, 46L));
        assertThat(error.getCode()).isEqualTo("ATTENDANCE_NOT_FOUND");
        assertThat(error.getStatus().value()).isEqualTo(404);
        verify(logs, never()).delete(any());
    }

    @Test
    void refusesWhenTheLogDoesNotExist() {
        ApiException error = catchThrowableOfType(ApiException.class, () -> service.deleteAttendance(3L, 99L));
        assertThat(error.getCode()).isEqualTo("ATTENDANCE_NOT_FOUND");
        verify(logs, never()).delete(any());
    }
}

package com.rmshop.rmshop.api;

import com.rmshop.rmshop.model.AttendanceLog;
import com.rmshop.rmshop.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// Black-box: removing a mistaken clock-in through DELETE /api/users/{id}/attendance/{logId}.
class AttendanceApiTest extends ApiTestBase {

    private User manager;
    private User cashier;
    private User otherCashier;

    @BeforeEach
    void givenStaffWithClockIns() {
        manager = givenUser("Joy Manager", User.Role.MANAGER, "482913", "manager@shop.co.zw");
        cashier = givenUser("Tino Cashier", User.Role.EMPLOYEE, "905172", null);
        otherCashier = givenUser("Rudo Cashier", User.Role.EMPLOYEE, "730461", null);
        // Clock-ins happen by logging in
        post("/api/users/login", Map.of("accessCode", "905172"));
        post("/api/users/login", Map.of("accessCode", "905172"));
        post("/api/users/login", Map.of("accessCode", "730461"));
    }

    private Long firstLogId(User user) {
        return attendanceLogRepository.findByEmployeeIdOrderByLoginTimeDesc(user.getId()).get(0).getId();
    }

    private int logCount(User user) {
        return attendanceLogRepository.findByEmployeeIdOrderByLoginTimeDesc(user.getId()).size();
    }

    private Response delete(User owner, Long logId, Long asUserId) {
        return send("DELETE", "/api/users/" + owner.getId() + "/attendance/" + logId, null, asUserId);
    }

    @Test
    void managerRemovesOneClockInAndOnlyThatOne() {
        Long logId = firstLogId(cashier);
        Response response = delete(cashier, logId, manager.getId());

        assertThat(response.status()).isEqualTo(204);
        assertThat(logCount(cashier)).isEqualTo(1);
        assertThat(attendanceLogRepository.findByEmployeeIdOrderByLoginTimeDesc(cashier.getId()))
                .extracting(AttendanceLog::getId).doesNotContain(logId);
        assertThat(logCount(otherCashier)).isEqualTo(1);
    }

    @Test
    void recordCannotBeRemovedThroughAnotherStaffMember() {
        Long rudosLog = firstLogId(otherCashier);
        Response response = delete(cashier, rudosLog, manager.getId());

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.get("code")).isEqualTo("ATTENDANCE_NOT_FOUND");
        assertThat(logCount(otherCashier)).isEqualTo(1);
    }

    @Test
    void removingTwiceSaysItIsAlreadyGone() {
        Long logId = firstLogId(cashier);
        delete(cashier, logId, manager.getId());
        Response again = delete(cashier, logId, manager.getId());
        assertThat(again.status()).isEqualTo(404);
        assertThat(again.get("code")).isEqualTo("ATTENDANCE_NOT_FOUND");
    }

    @Test
    void cashiersCannotRemoveRecords() {
        Response response = delete(cashier, firstLogId(cashier), cashier.getId());
        assertThat(response.status()).isEqualTo(403);
        assertThat(logCount(cashier)).isEqualTo(2);
    }

    @Test
    void notLoggedInCannotRemoveRecords() {
        Response response = delete(cashier, firstLogId(cashier), null);
        assertThat(response.status()).isEqualTo(403);
        assertThat(logCount(cashier)).isEqualTo(2);
    }
}

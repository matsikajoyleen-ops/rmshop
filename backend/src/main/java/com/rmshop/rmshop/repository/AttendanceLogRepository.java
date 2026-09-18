package com.rmshop.rmshop.repository;

import com.rmshop.rmshop.model.AttendanceLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttendanceLogRepository extends JpaRepository<AttendanceLog, Long> {
    List<AttendanceLog> findByEmployeeIdOrderByLoginTimeDesc(Long employeeId);
}
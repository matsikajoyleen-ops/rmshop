package com.rmshop.rmshop.repository;

import com.rmshop.rmshop.model.RestockLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface RestockLogRepository extends JpaRepository<RestockLog, Long> {
    List<RestockLog> findByRestockedAtBetween(LocalDateTime start, LocalDateTime end);
}

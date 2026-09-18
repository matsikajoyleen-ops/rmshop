package com.rmshop.rmshop.repository;

import com.rmshop.rmshop.model.RestockLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestockLogRepository extends JpaRepository<RestockLog, Long> {
}
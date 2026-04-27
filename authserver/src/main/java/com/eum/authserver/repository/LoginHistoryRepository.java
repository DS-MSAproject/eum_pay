package com.eum.authserver.repository;

import com.eum.authserver.entity.LoginHistory;
import com.eum.authserver.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {
    // 최근 로그인 이력 조회 (최신순)
    List<LoginHistory> findTop10ByUserOrderByLoginAtDesc(User user);
}
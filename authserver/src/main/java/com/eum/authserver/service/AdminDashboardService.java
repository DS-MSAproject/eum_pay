package com.eum.authserver.service;

import com.eum.authserver.dto.admin.DashboardSummaryResponse;
import com.eum.authserver.dto.admin.ServiceHealthResponse;
import com.eum.authserver.repository.AdminAuditLogRepository;
import com.eum.authserver.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    private final UserRepository userRepository;
    private final AdminAuditLogRepository adminAuditLogRepository;
    private final AdminMonitoringService adminMonitoringService;

    public DashboardSummaryResponse getSummary() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        long totalUsers       = userRepository.count();
        long todayNewUsers    = userRepository.countByCreatedAtGreaterThanEqual(todayStart);
        long adminActionsToday = adminAuditLogRepository.countByCreatedAtGreaterThanEqual(todayStart);

        List<ServiceHealthResponse> healthList = adminMonitoringService.getServicesHealth();
        int servicesUp = (int) healthList.stream()
                .filter(s -> "UP".equals(s.getStatus())).count();

        return DashboardSummaryResponse.builder()
                .totalUsers(totalUsers)
                .todayNewUsers(todayNewUsers)
                .adminActionsToday(adminActionsToday)
                .servicesUp(servicesUp)
                .servicesTotal(healthList.size())
                .serviceHealthList(healthList)
                .build();
    }
}

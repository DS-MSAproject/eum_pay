package com.eum.authserver.interceptor;

import com.eum.authserver.entity.AdminAuditLog;
import com.eum.authserver.repository.AdminAuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAuditInterceptor implements HandlerInterceptor {

    private final AdminAuditLogRepository adminAuditLogRepository;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        try {
            String uri = request.getRequestURI();
            String method = request.getMethod();
            String adminEmail = request.getHeader("X-User-Email");
            int status = response.getStatus();
            String result = (status >= 200 && status < 400) ? "SUCCESS" : "FAILED";

            adminAuditLogRepository.save(AdminAuditLog.of(adminEmail, method, uri, result));
        } catch (Exception e) {
            log.warn("[AuditInterceptor] 감사 로그 저장 실패: {}", e.getMessage());
        }
    }
}

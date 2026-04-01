package com.photoexhibition.config;

import com.photoexhibition.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final AuthService authService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 允许OPTIONS请求（CORS预检）
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();
        
        // 公开接口不需要认证（必须放在最前面）
        if (path.equals("/api/auth/login") || 
            path.equals("/api/auth/register") ||
            path.equals("/api/auth/send-code") ||
            path.equals("/api/auth/email/send-code") ||
            path.equals("/api/auth/public-settings") ||
            path.equals("/api/auth/public-user") ||
            path.startsWith("/api/albums") ||
            path.startsWith("/api/photos") ||
            path.startsWith("/api/tags") ||
            path.startsWith("/api/files") ||
            path.equals("/api/admin/init-admin") ||
            path.startsWith("/api/admin/test-password")) {
            return true;
        }

        // 管理接口需要认证（除了上面已经排除的）
        if (path.startsWith("/api/admin") || 
            (path.startsWith("/api/auth") && !path.equals("/api/auth/login"))) {
            String token = request.getHeader("Authorization");
            if (token == null || !token.startsWith("Bearer ")) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                try {
                    response.getWriter().write("{\"error\":\"未授权，请先登录\"}");
                } catch (Exception e) {
                    // ignore
                }
                return false;
            }

            token = token.substring(7);
            if (!authService.validateToken(token)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                try {
                    response.getWriter().write("{\"error\":\"Token无效或已过期\"}");
                } catch (Exception e) {
                    // ignore
                }
                return false;
            }
        }

        return true;
    }
}

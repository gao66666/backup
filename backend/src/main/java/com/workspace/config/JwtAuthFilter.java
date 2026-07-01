package com.workspace.config;

import com.workspace.util.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * JWT 鉴权 Filter:
 *   1. 从 Authorization: Bearer <token> 抽 token
 *   2. JwtService 校验 + 拿 userId
 *   3. 把 userId 塞进 SecurityContext(Monsora 风格: principal = UUID)
 *
 * token 无 / 失效 / 伪造:不报错,只是不塞 SecurityContext(让请求继续走,
 * 由 SecurityConfig 的 anyRequest permitAll + SpaceAuthInterceptor 自己判 401)。
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest req,
                                    @NonNull HttpServletResponse res,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring("Bearer ".length()).trim();
            try {
                UUID userId = jwtService.verify(token);
                var auth = new UsernamePasswordAuthenticationToken(
                        userId, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception e) {
                // 无效 token,清 SecurityContext,让拦截器自己处理
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(req, res);
    }
}
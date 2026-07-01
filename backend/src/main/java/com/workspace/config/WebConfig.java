package com.workspace.config;

import com.workspace.service.SpaceAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 路由分组 + 拦截器注册。
 *
 * 路由组 A(不需要空间鉴权):
 *   - /api/health
 *   - /api/spaces(创建空间 + 列当前用户空间)
 *
 * 路由组 B(需要空间鉴权):
 *   - /api/nodes/**
 *   - /api/spaces/{id}/**(单空间操作)
 *   - /api/space-members/**
 *
 * ⚠️ 路由组 B 的所有端点必须能从请求里解析出 spaceId(query 参数 或
 *    path 段 /api/spaces/{id})。否则会 400。这是约定,要记到 MEMORY。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final SpaceAuthInterceptor spaceAuthInterceptor;

    public WebConfig(SpaceAuthInterceptor spaceAuthInterceptor) {
        this.spaceAuthInterceptor = spaceAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(spaceAuthInterceptor)
                .addPathPatterns(
                    "/api/nodes/**",
                    "/api/spaces/*",          // 单空间操作(getById/update/delete)
                    "/api/space-members/**"
                );
    }
}
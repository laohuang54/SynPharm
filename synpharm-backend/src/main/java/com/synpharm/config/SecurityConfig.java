package com.synpharm.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Spring Security 安全配置
 *
 * <p>核心配置：
 * <ol>
 *   <li>禁用CSRF（JWT无状态认证不需要）</li>
 *   <li>配置CORS跨域</li>
 *   <li>无状态会话管理（不创建Session）</li>
 *   <li>请求权限控制</li>
 *   <li>添加JWT认证过滤器</li>
 * </ol>
 *
 * @author SynPharm Team
 * @version 2.0.0
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /** JWT认证过滤器 */
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /** CORS配置源（由 CorsConfig 提供） */
    private final CorsConfigurationSource corsConfigurationSource;

    /**
     * 配置安全过滤链
     * <p>这是Spring Security 6.x的推荐写法。
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // ========== 1. 禁用CSRF ==========
            // JWT是无状态的，每个请求都带Token，不需要CSRF保护
            .csrf(AbstractHttpConfigurer::disable)

            // ========== 2. 配置CORS跨域 ==========
            .cors(cors -> cors.configurationSource(corsConfigurationSource))

            // ========== 3. 无状态会话管理 ==========
            // JWT无状态认证，不创建也不使用HttpSession
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // ========== 4. 请求权限控制 ==========
            .authorizeHttpRequests(auth -> auth
                // ---- 公开接口（无需认证） ----
                .requestMatchers(
                    "/api/auth/login",         // 登录
                    "/api/auth/register",      // 注册
                    "/api/auth/captcha/send",  // 发送验证码
                    "/api/auth/password/reset",// 忘记密码
                    "/actuator/health",        // 健康检查（Docker healthcheck 使用）
                    "/api/system/algorithm-health", // 算法引擎健康检查（监控探测，修复方案 5.7）
                    "/swagger-ui/**",          // Swagger UI
                    "/v3/api-docs/**",         // Swagger API文档
                    "/doc.html",               // Knife4j文档
                    "/webjars/**",             // Knife4j静态资源
                    "/favicon.ico"             // 网站图标
                ).permitAll()

                // ---- 管理员接口（需要admin角色） ----
                // 注意：Spring 6 PathPatternParser 中 `**` 只能位于路径末尾，
                // 故 /api/users/{id}/status 用单段通配 `*` 表达
                .requestMatchers(
                    "/api/users",
                    "/api/users/*/status"
                ).hasRole("admin")

                // ---- 其他所有请求都需要认证（包括登出接口） ----
                .anyRequest().authenticated()
            )

            // ========== 4.5 认证入口点：未登录/Token失效统一返回 401 ==========
            .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"message\":\"未登录或登录已失效\"}");
            }))

            // ========== 5. 添加JWT认证过滤器 ==========
            // 在UsernamePasswordAuthenticationFilter之前执行
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 配置密码编码器
     * <p>使用BCrypt算法，强度设为10。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}

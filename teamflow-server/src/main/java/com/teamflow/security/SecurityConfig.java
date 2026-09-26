package com.teamflow.security;

import com.teamflow.audit.RequestTraceFilter;
import com.teamflow.ratelimit.RequestRateLimitFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 配置。
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    private static final int BCRYPT_STRENGTH = 12;

    /**
     * 配置无状态 REST 权限规则和 JWT 过滤器。
     *
     * @param http Security 配置入口
     * @param jwtAuthenticationFilter JWT 认证过滤器
     * @param authenticationEntryPoint 未认证响应入口
     * @param accessDeniedHandler 权限不足响应处理器
     * @return 安全过滤器链
     * @throws Exception 配置失败
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            ObjectProvider<RequestRateLimitFilter> rateLimitFilterProvider,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS
                ))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/refresh"
                        ).permitAll()
                        .requestMatchers(
                                "/api/v1/system/ping",
                                "/actuator/health",
                                "/actuator/health/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/error"
                        ).permitAll()
                        .requestMatchers(
                                "/actuator/info",
                                "/actuator/metrics/**"
                        ).authenticated()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll()
                )
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        RequestRateLimitFilter rateLimitFilter = rateLimitFilterProvider
                .getIfAvailable();
        if (rateLimitFilter != null) {
            http.addFilterAfter(
                    rateLimitFilter,
                    JwtAuthenticationFilter.class
            );
        }

        return http.build();
    }

    /**
     * 创建只对白名单前端开放的跨域规则。
     *
     * <p>JWT 放在 {@code Authorization} 请求头中，所以浏览器跨域请求会先发送
     * CORS 预检。这里显式允许该请求头，但不允许携带 Cookie。</p>
     *
     * @param properties 跨域白名单配置
     * @return Spring Security 使用的跨域配置源
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            CorsProperties properties
    ) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.PUT.name(),
                HttpMethod.PATCH.name(),
                HttpMethod.DELETE.name(),
                HttpMethod.OPTIONS.name()
        ));
        configuration.setAllowedHeaders(List.of(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE,
                HttpHeaders.ACCEPT,
                "Last-Event-ID"
        ));
        configuration.setExposedHeaders(List.of(
                HttpHeaders.CONTENT_DISPOSITION,
                HttpHeaders.RETRY_AFTER,
                RequestTraceFilter.TRACE_ID_HEADER
        ));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    /**
     * 创建 BCrypt 密码编码器。
     *
     * @return 密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    /**
     * 创建先验证密码、后检查账号状态的数据库认证器。
     *
     * @param userDetailsService 用户认证数据加载服务
     * @param passwordEncoder BCrypt 密码编码器
     * @param accountStatusChecker 密码验证后的账号状态检查器
     * @return 数据库认证器
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder,
            PasswordVerifiedAccountStatusChecker accountStatusChecker
    ) {
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        provider.setHideUserNotFoundExceptions(true);
        provider.setPreAuthenticationChecks(ignored -> {
            // 账号状态统一延后到密码验证成功之后检查。
        });
        provider.setPostAuthenticationChecks(accountStatusChecker);
        return provider;
    }

    /**
     * 创建只使用 TeamFlow 数据库认证器的认证管理器。
     *
     * @param authenticationProvider TeamFlow 数据库认证器
     * @return 认证管理器
     */
    @Bean
    public AuthenticationManager authenticationManager(
            DaoAuthenticationProvider authenticationProvider
    ) {
        ProviderManager providerManager = new ProviderManager(
                authenticationProvider
        );
        providerManager.setEraseCredentialsAfterAuthentication(true);
        return providerManager;
    }
}

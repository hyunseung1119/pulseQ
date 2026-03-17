package com.pulseq.api.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository

/**
 * Spring Security 설정 — WebFlux 기반 보안 구성.
 * JWT 인증 필터를 AUTHENTICATION 순서에 등록하고,
 * 공개 엔드포인트(회원가입/로그인/헬스체크)를 제외한 모든 요청에 인증을 요구한다.
 */
@Configuration
@EnableWebFluxSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter  // JWT 인증 필터
) {
    @Bean
    fun securityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        return http
            .csrf { it.disable() }       // CSRF 비활성화 — SPA + JWT 조합에서는 CSRF 불필요
            .httpBasic { it.disable() }   // HTTP Basic 인증 비활성화
            .formLogin { it.disable() }   // 폼 로그인 비활성화 (REST API이므로)
            // NoOp: 세션을 사용하지 않음 — JWT는 Stateless 인증
            .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
            .authorizeExchange { exchanges ->
                exchanges
                    // 공개 엔드포인트 — 인증 불필요
                    .pathMatchers(HttpMethod.POST, "/api/v1/tenants/signup").permitAll()  // 회원가입
                    .pathMatchers(HttpMethod.POST, "/api/v1/tenants/login").permitAll()   // 로그인
                    .pathMatchers("/api/v1/health").permitAll()                            // 헬스체크
                    .pathMatchers("/actuator/**").permitAll()                              // Prometheus 메트릭
                    // 나머지 모든 요청은 인증 필요
                    .anyExchange().authenticated()
            }
            // JWT 인증 필터를 Spring Security 필터 체인의 AUTHENTICATION 위치에 추가
            .addFilterBefore(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .build()
    }
}

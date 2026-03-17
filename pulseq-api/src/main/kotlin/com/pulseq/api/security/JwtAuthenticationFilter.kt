package com.pulseq.api.security

import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * JWT 인증 필터 — 모든 요청에서 JWT 토큰을 추출하고 검증한다.
 * WebFlux의 WebFilter를 구현하여 SecurityConfig에서 AUTHENTICATION 순서에 등록.
 *
 * 인증 흐름:
 * 1. Authorization: Bearer {token} 헤더 또는 X-API-Key 헤더에서 토큰 추출
 * 2. JwtProvider로 토큰 검증 + tenantId 추출
 * 3. SecurityContext에 인증 정보 설정 → @AuthenticationPrincipal로 접근 가능
 */
@Component
class JwtAuthenticationFilter(
    private val jwtProvider: JwtProvider  // JWT 검증 컴포넌트
) : WebFilter {

    /**
     * 필터 실행 — 요청마다 호출된다.
     * 토큰이 없거나 검증 실패 시 chain.filter()만 호출 → 인증 없이 진행.
     * SecurityConfig의 authorizeExchange 설정에 따라 인증 필요 여부가 결정됨.
     */
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        // 1. 요청 헤더에서 토큰 추출
        val token = extractToken(exchange)
            ?: return chain.filter(exchange)  // 토큰 없음 → 인증 없이 통과

        // 2. 토큰 검증 + tenantId 추출
        val tenantId = jwtProvider.validateAndGetTenantId(token)
            ?: return chain.filter(exchange)  // 검증 실패 → 인증 없이 통과

        // 3. Spring Security Authentication 객체 생성
        val auth = UsernamePasswordAuthenticationToken(
            tenantId.toString(),                          // principal: tenantId 문자열
            null,                                         // credentials: 비밀번호 불필요
            listOf(SimpleGrantedAuthority("ROLE_TENANT")) // authorities: 테넌트 역할
        )

        // 4. Reactor Context에 인증 정보 설정 (WebFlux는 ThreadLocal 대신 Context 사용)
        return chain.filter(exchange)
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
    }

    /**
     * 토큰 추출 — 두 가지 인증 방식 지원:
     * 1. Bearer Token: Authorization: Bearer {jwt_token} (대시보드 사용)
     * 2. API Key: X-API-Key: {api_key} (외부 API 연동 시 사용)
     */
    private fun extractToken(exchange: ServerWebExchange): String? {
        // Bearer 토큰 확인
        val authHeader = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7)  // "Bearer " 접두사 제거
        }

        // API Key 확인 (Bearer 토큰 없을 때 fallback)
        return exchange.request.headers.getFirst("X-API-Key")
    }
}

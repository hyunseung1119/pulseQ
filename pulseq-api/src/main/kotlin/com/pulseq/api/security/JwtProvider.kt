package com.pulseq.api.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Date
import java.util.UUID

/**
 * JWT 토큰 생성 및 검증 컴포넌트.
 * HMAC-SHA 알고리즘으로 서명하며, subject에 tenantId를 담는다.
 */
@Component
class JwtProvider(
    @Value("\${pulseq.jwt.secret}") private val secret: String,        // 환경변수에서 주입받는 JWT 시크릿 키
    @Value("\${pulseq.jwt.expiry-ms:86400000}") private val expiryMs: Long  // 만료 시간 (기본 24시간)
) {
    // lazy 초기화: secret 문자열을 HMAC-SHA 키 객체로 변환 (최초 1회)
    private val key by lazy { Keys.hmacShaKeyFor(secret.toByteArray()) }

    /**
     * Access Token 생성 — 24시간 유효.
     * subject = tenantId (UUID 문자열)
     */
    fun generateAccessToken(tenantId: UUID): String {
        val now = Date()
        return Jwts.builder()
            .subject(tenantId.toString())         // 페이로드: 테넌트 ID
            .issuedAt(now)                        // 발급 시각
            .expiration(Date(now.time + expiryMs)) // 만료 시각
            .signWith(key)                        // HMAC-SHA 서명
            .compact()                            // JWT 문자열로 직렬화
    }

    /**
     * Refresh Token 생성 — Access Token의 7배 수명 (기본 7일).
     * Access Token 만료 시 재발급에 사용.
     */
    fun generateRefreshToken(tenantId: UUID): String {
        val now = Date()
        return Jwts.builder()
            .subject(tenantId.toString())
            .issuedAt(now)
            .expiration(Date(now.time + expiryMs * 7))  // 7일 유효
            .signWith(key)
            .compact()
    }

    /**
     * 토큰 검증 + tenantId 추출.
     * 서명 검증 실패, 만료, 형식 오류 등 모든 예외에서 null 반환.
     * null 반환 시 인증 실패 → 401 Unauthorized.
     */
    fun validateAndGetTenantId(token: String): UUID? {
        return try {
            val claims: Claims = Jwts.parser()
                .verifyWith(key)           // 서명 검증 키 설정
                .build()
                .parseSignedClaims(token)  // 토큰 파싱 + 서명 검증 + 만료 체크
                .payload                  // Claims 페이로드 추출
            UUID.fromString(claims.subject) // subject에서 tenantId 추출
        } catch (e: Exception) {
            null  // 검증 실패 시 null 반환 (예외 종류 무관)
        }
    }

    /** 만료 시간을 초 단위로 반환 (프론트엔드 캐시 설정용) */
    fun getExpirySeconds(): Long = expiryMs / 1000
}

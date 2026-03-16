package com.pulseq.api.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Date
import java.util.UUID

@Component
class JwtProvider(
    @Value("\${pulseq.jwt.secret}") private val secret: String,
    @Value("\${pulseq.jwt.expiry-ms:86400000}") private val expiryMs: Long
) {
    private val key by lazy { Keys.hmacShaKeyFor(secret.toByteArray()) }

    fun generateAccessToken(tenantId: UUID): String {
        val now = Date()
        return Jwts.builder()
            .subject(tenantId.toString())
            .issuedAt(now)
            .expiration(Date(now.time + expiryMs))
            .signWith(key)
            .compact()
    }

    fun generateRefreshToken(tenantId: UUID): String {
        val now = Date()
        return Jwts.builder()
            .subject(tenantId.toString())
            .issuedAt(now)
            .expiration(Date(now.time + expiryMs * 7))
            .signWith(key)
            .compact()
    }

    fun validateAndGetTenantId(token: String): UUID? {
        return try {
            val claims: Claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload
            UUID.fromString(claims.subject)
        } catch (e: Exception) {
            null
        }
    }

    fun getExpirySeconds(): Long = expiryMs / 1000
}

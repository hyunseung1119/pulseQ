package com.pulseq.core.service

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64

@Component
class ApiKeyGenerator {

    private val passwordEncoder = BCryptPasswordEncoder()
    private val secureRandom = SecureRandom()

    fun generate(): ApiKeyPair {
        val rawBytes = ByteArray(32)
        secureRandom.nextBytes(rawBytes)
        val rawKey = "pq_live_${Base64.getUrlEncoder().withoutPadding().encodeToString(rawBytes)}"
        val hash = passwordEncoder.encode(rawKey)
        return ApiKeyPair(rawKey = rawKey, hash = hash)
    }

    fun verify(rawKey: String, hash: String): Boolean =
        passwordEncoder.matches(rawKey, hash)
}

data class ApiKeyPair(
    val rawKey: String,
    val hash: String
)

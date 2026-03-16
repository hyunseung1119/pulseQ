package com.pulseq.core.service

import com.pulseq.core.domain.Plan
import com.pulseq.core.domain.Tenant
import com.pulseq.core.exception.InvalidCredentialsException
import com.pulseq.core.exception.TenantAlreadyExistsException
import com.pulseq.core.exception.TenantNotFoundException
import com.pulseq.core.port.TenantRepository
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class TenantService(
    private val tenantRepository: TenantRepository,
    private val apiKeyGenerator: ApiKeyGenerator
) {
    private val passwordEncoder = BCryptPasswordEncoder()

    suspend fun signup(email: String, password: String, companyName: String, plan: Plan = Plan.FREE): TenantWithRawApiKey {
        if (tenantRepository.existsByEmail(email)) {
            throw TenantAlreadyExistsException(email)
        }

        val apiKeyPair = apiKeyGenerator.generate()
        val tenant = Tenant(
            email = email,
            passwordHash = passwordEncoder.encode(password),
            companyName = companyName,
            plan = plan,
            apiKey = maskApiKey(apiKeyPair.rawKey),
            apiKeyHash = apiKeyPair.hash,
            rateLimitPerSecond = plan.rateLimitPerSecond,
            monthlyQuota = plan.monthlyQuota
        )

        val saved = tenantRepository.save(tenant)
        return TenantWithRawApiKey(tenant = saved, rawApiKey = apiKeyPair.rawKey)
    }

    suspend fun login(email: String, password: String): Tenant {
        val tenant = tenantRepository.findByEmail(email)
            ?: throw InvalidCredentialsException()

        if (!passwordEncoder.matches(password, tenant.passwordHash)) {
            throw InvalidCredentialsException()
        }

        return tenant
    }

    suspend fun findById(id: UUID): Tenant =
        tenantRepository.findById(id) ?: throw TenantNotFoundException()

    suspend fun findByApiKeyHash(apiKeyHash: String): Tenant? =
        tenantRepository.findByApiKeyHash(apiKeyHash)

    suspend fun rotateApiKey(tenantId: UUID): TenantWithRawApiKey {
        val tenant = findById(tenantId)
        val newApiKeyPair = apiKeyGenerator.generate()

        val updated = tenant.copy(
            apiKey = maskApiKey(newApiKeyPair.rawKey),
            apiKeyHash = newApiKeyPair.hash,
            updatedAt = Instant.now()
        )

        val saved = tenantRepository.update(updated)
        return TenantWithRawApiKey(tenant = saved, rawApiKey = newApiKeyPair.rawKey)
    }

    private fun maskApiKey(rawKey: String): String {
        val prefix = rawKey.take(12)
        return "${prefix}${"•".repeat(20)}"
    }
}

data class TenantWithRawApiKey(
    val tenant: Tenant,
    val rawApiKey: String
)

package com.pulseq.core.domain

import java.time.Instant
import java.util.UUID

data class Tenant(
    val id: UUID = UUID.randomUUID(),
    val email: String,
    val passwordHash: String,
    val companyName: String,
    val plan: Plan = Plan.FREE,
    val apiKey: String,
    val apiKeyHash: String,
    val status: TenantStatus = TenantStatus.ACTIVE,
    val rateLimitPerSecond: Int = 10,
    val monthlyQuota: Int = 10_000,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

enum class Plan(val rateLimitPerSecond: Int, val monthlyQuota: Int) {
    FREE(rateLimitPerSecond = 10, monthlyQuota = 10_000),
    PRO(rateLimitPerSecond = 1_000, monthlyQuota = 1_000_000),
    ENTERPRISE(rateLimitPerSecond = 10_000, monthlyQuota = 10_000_000)
}

enum class TenantStatus {
    ACTIVE, SUSPENDED, DELETED
}

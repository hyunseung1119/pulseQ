package com.pulseq.infra.persistence.entity

import com.pulseq.core.domain.Plan
import com.pulseq.core.domain.Tenant
import com.pulseq.core.domain.TenantStatus
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "tenants")
class TenantEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false, unique = true)
    val email: String,

    @Column(name = "password_hash", nullable = false)
    val passwordHash: String,

    @Column(name = "company_name", nullable = false)
    val companyName: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val plan: Plan = Plan.FREE,

    @Column(name = "api_key", nullable = false)
    val apiKey: String,

    @Column(name = "api_key_hash", nullable = false, unique = true)
    val apiKeyHash: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: TenantStatus = TenantStatus.ACTIVE,

    @Column(name = "rate_limit_per_second", nullable = false)
    val rateLimitPerSecond: Int = 10,

    @Column(name = "monthly_quota", nullable = false)
    val monthlyQuota: Int = 10_000,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now()
) {
    fun toDomain(): Tenant = Tenant(
        id = id,
        email = email,
        passwordHash = passwordHash,
        companyName = companyName,
        plan = plan,
        apiKey = apiKey,
        apiKeyHash = apiKeyHash,
        status = status,
        rateLimitPerSecond = rateLimitPerSecond,
        monthlyQuota = monthlyQuota,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun from(tenant: Tenant): TenantEntity = TenantEntity(
            id = tenant.id,
            email = tenant.email,
            passwordHash = tenant.passwordHash,
            companyName = tenant.companyName,
            plan = tenant.plan,
            apiKey = tenant.apiKey,
            apiKeyHash = tenant.apiKeyHash,
            status = tenant.status,
            rateLimitPerSecond = tenant.rateLimitPerSecond,
            monthlyQuota = tenant.monthlyQuota,
            createdAt = tenant.createdAt,
            updatedAt = tenant.updatedAt
        )
    }
}

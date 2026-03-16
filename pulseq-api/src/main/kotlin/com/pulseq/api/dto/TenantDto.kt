package com.pulseq.api.dto

import com.pulseq.core.domain.Plan
import com.pulseq.core.domain.Tenant
import com.pulseq.core.domain.TenantStatus
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class SignupRequest(
    @field:Email(message = "Valid email is required")
    @field:NotBlank(message = "Email is required")
    val email: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 8, max = 100, message = "Password must be 8-100 characters")
    val password: String,

    @field:NotBlank(message = "Company name is required")
    val companyName: String,

    val plan: Plan = Plan.FREE
)

data class LoginRequest(
    @field:Email(message = "Valid email is required")
    @field:NotBlank(message = "Email is required")
    val email: String,

    @field:NotBlank(message = "Password is required")
    val password: String
)

data class TenantResponse(
    val tenantId: UUID,
    val email: String,
    val companyName: String,
    val plan: Plan,
    val status: TenantStatus,
    val apiKey: String,
    val usage: UsageInfo? = null,
    val createdAt: Instant
) {
    companion object {
        fun from(tenant: Tenant, rawApiKey: String? = null): TenantResponse = TenantResponse(
            tenantId = tenant.id,
            email = tenant.email,
            companyName = tenant.companyName,
            plan = tenant.plan,
            status = tenant.status,
            apiKey = rawApiKey ?: tenant.apiKey,
            createdAt = tenant.createdAt
        )
    }
}

data class UsageInfo(
    val currentMonth: Long,
    val limit: Int
)

data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long
)

data class ApiKeyRotateResponse(
    val apiKey: String,
    val previousKeyExpiresAt: Instant
)

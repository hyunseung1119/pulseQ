package com.pulseq.api.controller

import com.pulseq.api.dto.*
import com.pulseq.api.security.JwtProvider
import com.pulseq.core.service.TenantService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/tenants")
class TenantController(
    private val tenantService: TenantService,
    private val jwtProvider: JwtProvider
) {
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun signup(@Valid @RequestBody request: SignupRequest): ApiResponse<TenantResponse> {
        val result = tenantService.signup(
            email = request.email,
            password = request.password,
            companyName = request.companyName,
            plan = request.plan
        )
        return ApiResponse.ok(TenantResponse.from(result.tenant, result.rawApiKey))
    }

    @PostMapping("/login")
    suspend fun login(@Valid @RequestBody request: LoginRequest): ApiResponse<LoginResponse> {
        val tenant = tenantService.login(request.email, request.password)
        val accessToken = jwtProvider.generateAccessToken(tenant.id)
        val refreshToken = jwtProvider.generateRefreshToken(tenant.id)

        return ApiResponse.ok(
            LoginResponse(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresIn = jwtProvider.getExpirySeconds()
            )
        )
    }

    @GetMapping("/me")
    suspend fun getMe(@AuthenticationPrincipal tenantId: String): ApiResponse<TenantResponse> {
        val tenant = tenantService.findById(UUID.fromString(tenantId))
        return ApiResponse.ok(TenantResponse.from(tenant))
    }

    @PostMapping("/api-keys/rotate")
    suspend fun rotateApiKey(@AuthenticationPrincipal tenantId: String): ApiResponse<ApiKeyRotateResponse> {
        val result = tenantService.rotateApiKey(UUID.fromString(tenantId))
        return ApiResponse.ok(
            ApiKeyRotateResponse(
                apiKey = result.rawApiKey,
                previousKeyExpiresAt = Instant.now().plusSeconds(86400)
            )
        )
    }
}

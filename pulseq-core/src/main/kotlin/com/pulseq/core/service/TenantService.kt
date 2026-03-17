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

/**
 * 테넌트(고객사) 서비스 — 회원가입, 로그인, 프로필 조회, API 키 관리.
 * 멀티테넌트 SaaS 구조에서 각 고객사를 관리한다.
 */
@Service
class TenantService(
    private val tenantRepository: TenantRepository,  // 테넌트 저장소 포트
    private val apiKeyGenerator: ApiKeyGenerator       // API 키 생성 유틸
) {
    // BCrypt 해싱 — 솔트 자동 생성, 기본 강도(10)
    private val passwordEncoder = BCryptPasswordEncoder()

    /**
     * 회원가입 — 이메일 중복 확인 후 테넌트 생성.
     * API 키를 발급하되, 저장 시에는 해시만 저장 (원본은 응답에서 1회만 노출).
     */
    suspend fun signup(email: String, password: String, companyName: String, plan: Plan = Plan.FREE): TenantWithRawApiKey {
        // 이메일 중복 확인
        if (tenantRepository.existsByEmail(email)) {
            throw TenantAlreadyExistsException(email)
        }

        // API 키 생성: rawKey(노출용) + hash(저장용)
        val apiKeyPair = apiKeyGenerator.generate()
        val tenant = Tenant(
            email = email,
            passwordHash = passwordEncoder.encode(password),  // BCrypt 해시
            companyName = companyName,
            plan = plan,
            apiKey = maskApiKey(apiKeyPair.rawKey),  // 마스킹된 키 (UI 표시용)
            apiKeyHash = apiKeyPair.hash,             // SHA-256 해시 (인증 비교용)
            rateLimitPerSecond = plan.rateLimitPerSecond,  // 플랜별 초당 요청 제한
            monthlyQuota = plan.monthlyQuota               // 플랜별 월간 쿼터
        )

        val saved = tenantRepository.save(tenant)
        // 원본 API 키는 이 시점에서만 반환 (이후 조회 불가)
        return TenantWithRawApiKey(tenant = saved, rawApiKey = apiKeyPair.rawKey)
    }

    /**
     * 로그인 — 이메일 + 비밀번호 검증.
     * 이메일이 없거나 비밀번호 불일치 시 동일한 에러 반환 (이메일 존재 여부 노출 방지).
     */
    suspend fun login(email: String, password: String): Tenant {
        val tenant = tenantRepository.findByEmail(email)
            ?: throw InvalidCredentialsException()  // 이메일 없음 → 일반 에러

        // BCrypt의 matches로 해시 비교 (타이밍 공격 방지 내장)
        if (!passwordEncoder.matches(password, tenant.passwordHash)) {
            throw InvalidCredentialsException()  // 비밀번호 불일치 → 동일한 에러
        }

        return tenant
    }

    /** ID로 테넌트 조회 — 존재하지 않으면 예외 */
    suspend fun findById(id: UUID): Tenant =
        tenantRepository.findById(id) ?: throw TenantNotFoundException()

    /** API 키 해시로 테넌트 조회 — X-API-Key 인증에 사용 */
    suspend fun findByApiKeyHash(apiKeyHash: String): Tenant? =
        tenantRepository.findByApiKeyHash(apiKeyHash)

    /**
     * API 키 재발급 — 기존 키를 폐기하고 새 키를 생성.
     * 보안 사고 시 또는 키 노출 시 사용.
     */
    suspend fun rotateApiKey(tenantId: UUID): TenantWithRawApiKey {
        val tenant = findById(tenantId)
        val newApiKeyPair = apiKeyGenerator.generate()

        val updated = tenant.copy(
            apiKey = maskApiKey(newApiKeyPair.rawKey),  // 새 마스킹된 키
            apiKeyHash = newApiKeyPair.hash,             // 새 해시
            updatedAt = Instant.now()
        )

        val saved = tenantRepository.update(updated)
        return TenantWithRawApiKey(tenant = saved, rawApiKey = newApiKeyPair.rawKey)
    }

    /**
     * API 키 마스킹 — 앞 12자만 노출하고 나머지는 • 로 가림.
     * 대시보드 UI에서 키의 일부만 보여주기 위함.
     */
    private fun maskApiKey(rawKey: String): String {
        val prefix = rawKey.take(12)
        return "${prefix}${"•".repeat(20)}"
    }
}

/** 회원가입/키 재발급 시 원본 API 키를 함께 반환하기 위한 래퍼 */
data class TenantWithRawApiKey(
    val tenant: Tenant,
    val rawApiKey: String  // 이 값은 1회만 노출되고 이후 조회 불가
)

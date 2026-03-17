import apiClient from '@/shared/lib/api-client'

/** 이벤트 응답 DTO — 백엔드 EventResponse와 1:1 매핑 */
export interface EventResponse {
  eventId: string
  name: string
  slug: string
  status: 'SCHEDULED' | 'ACTIVE' | 'PAUSED' | 'COMPLETED' | 'CANCELLED'  // 이벤트 상태 enum
  maxCapacity: number           // 최대 수용 인원
  rateLimit: number             // 초당 처리 속도 (명/초)
  entryTokenTtlSeconds: number  // 입장 토큰 유효 시간 (초)
  startAt: string               // 이벤트 시작 시각 (ISO 8601)
  endAt: string                 // 이벤트 종료 시각
  botDetectionEnabled: boolean  // 봇 탐지 활성화 여부
  botScoreThreshold?: number    // 봇 판정 임계값 (0.0~1.0)
  totalEntered: number          // 총 입장 수
  totalProcessed: number        // 총 처리 완료 수
  totalAbandoned: number        // 총 이탈 수
  totalBotBlocked: number       // 총 봇 차단 수
  createdAt: string
}

/** 이벤트 생성 요청 DTO */
export interface CreateEventRequest {
  name: string
  slug: string
  maxCapacity: number
  rateLimit: number
  startAt: string
  endAt: string
  botDetectionEnabled: boolean
  botScoreThreshold?: number
}

/** 큐 상태 응답 DTO — 대시보드 실시간 모니터링용 */
export interface QueueStatusResponse {
  eventId: string
  status: string
  totalWaiting: number              // 현재 대기 인원
  totalProcessed: number            // 처리 완료 인원
  totalAbandoned: number            // 이탈 인원
  currentRatePerSecond: number      // 현재 초당 처리 속도
  estimatedClearTimeSeconds: number // 예상 대기열 소진 시간 (초)
  botBlocked: number                // 봇 차단 수
}

/** 이벤트 통계 응답 DTO — 집계된 수치 */
export interface EventStatsResponse {
  totals: {
    entered: number      // 총 입장
    granted: number      // 총 입장 허가
    verified: number     // 총 토큰 검증
    left: number         // 총 퇴장
    botBlocked: number   // 총 봇 차단
  }
  rates: {
    enteredPerMinute: number   // 분당 입장 수
    grantedPerMinute: number   // 분당 허가 수
    enteredPerHour: number     // 시간당 입장 수
  }
  percentages: {
    abandonRate: number     // 이탈률 (%)
    conversionRate: number  // 전환율 (%)
  }
}

/** 이벤트 로그 엔트리 — Kafka 소비 후 DB에 저장된 로그 */
export interface EventLogEntry {
  id: string
  eventId: string
  userId: string
  eventType: string                     // QUEUE_ENTERED, ENTRY_GRANTED, BOT_BLOCKED 등
  payload: Record<string, unknown>      // JSONB 페이로드 (botScore, reasons 등)
  createdAt: string
}

/**
 * 이벤트 목록 조회 — GET /events
 * 백엔드가 페이지네이션 응답({ content: [...] })과 배열 응답([...]) 두 형태를 반환할 수 있어
 * 두 형태 모두 처리하도록 방어적으로 구현.
 */
export async function listEvents(status?: string) {
  const params = status ? { status } : {}
  const res = await apiClient.get<{ success: boolean; data: { content: EventResponse[] } | EventResponse[] }>('/events', { params })
  const data = res.data.data
  // 배열이면 그대로, 페이지네이션 객체면 content 필드 추출
  return Array.isArray(data) ? data : data.content
}

/** 이벤트 상세 조회 — GET /events/{eventId} */
export async function getEvent(eventId: string) {
  const res = await apiClient.get<{ success: boolean; data: EventResponse }>(`/events/${eventId}`)
  return res.data.data
}

/** 이벤트 생성 — POST /events */
export async function createEvent(data: CreateEventRequest) {
  const res = await apiClient.post<{ success: boolean; data: EventResponse }>('/events', data)
  return res.data.data
}

/** 이벤트 수정 — PATCH /events/{eventId} (부분 업데이트) */
export async function updateEvent(eventId: string, data: Partial<CreateEventRequest>) {
  const res = await apiClient.patch<{ success: boolean; data: EventResponse }>(`/events/${eventId}`, data)
  return res.data.data
}

/** 이벤트 삭제 — DELETE /events/{eventId} */
export async function deleteEvent(eventId: string) {
  await apiClient.delete(`/events/${eventId}`)
}

/** 큐 상태 조회 — GET /queues/{eventId}/status (3초 자동 갱신) */
export async function getQueueStatus(eventId: string) {
  const res = await apiClient.get<{ success: boolean; data: QueueStatusResponse }>(`/queues/${eventId}/status`)
  return res.data.data
}

/** 이벤트 통계 조회 — GET /stats/{eventId} (5초 자동 갱신) */
export async function getEventStats(eventId: string) {
  const res = await apiClient.get<{ success: boolean; data: EventStatsResponse }>(`/stats/${eventId}`)
  return res.data.data
}

/** 이벤트 로그 조회 — GET /stats/{eventId}/logs (최근 N건) */
export async function getEventLogs(eventId: string, limit = 50) {
  const res = await apiClient.get<{ success: boolean; data: EventLogEntry[] }>(`/stats/${eventId}/logs`, {
    params: { limit },
  })
  return res.data.data
}

/** 큐 수동 처리 — POST /queues/{eventId}/process (상위 N명 입장 토큰 발급) */
export async function processQueue(eventId: string) {
  const res = await apiClient.post<{ success: boolean; data: { processed: number } }>(`/queues/${eventId}/process`)
  return res.data.data
}

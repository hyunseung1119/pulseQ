import apiClient from '@/shared/lib/api-client'

export interface EventResponse {
  eventId: string
  name: string
  slug: string
  status: 'SCHEDULED' | 'ACTIVE' | 'PAUSED' | 'COMPLETED' | 'CANCELLED'
  maxCapacity: number
  rateLimit: number
  entryTokenTtlSeconds: number
  startAt: string
  endAt: string
  botDetectionEnabled: boolean
  botScoreThreshold?: number
  totalEntered: number
  totalProcessed: number
  totalAbandoned: number
  totalBotBlocked: number
  createdAt: string
}

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

export interface QueueStatusResponse {
  eventId: string
  status: string
  totalWaiting: number
  totalProcessed: number
  totalAbandoned: number
  currentRatePerSecond: number
  estimatedClearTimeSeconds: number
  botBlocked: number
}

export interface EventStatsResponse {
  totals: {
    entered: number
    granted: number
    verified: number
    left: number
    botBlocked: number
  }
  rates: {
    enteredPerMinute: number
    grantedPerMinute: number
    enteredPerHour: number
  }
  percentages: {
    abandonRate: number
    conversionRate: number
  }
}

export interface EventLogEntry {
  id: string
  eventId: string
  userId: string
  eventType: string
  payload: Record<string, unknown>
  createdAt: string
}

export async function listEvents(status?: string) {
  const params = status ? { status } : {}
  const res = await apiClient.get<{ success: boolean; data: { content: EventResponse[] } | EventResponse[] }>('/events', { params })
  const data = res.data.data
  return Array.isArray(data) ? data : data.content
}

export async function getEvent(eventId: string) {
  const res = await apiClient.get<{ success: boolean; data: EventResponse }>(`/events/${eventId}`)
  return res.data.data
}

export async function createEvent(data: CreateEventRequest) {
  const res = await apiClient.post<{ success: boolean; data: EventResponse }>('/events', data)
  return res.data.data
}

export async function updateEvent(eventId: string, data: Partial<CreateEventRequest>) {
  const res = await apiClient.patch<{ success: boolean; data: EventResponse }>(`/events/${eventId}`, data)
  return res.data.data
}

export async function deleteEvent(eventId: string) {
  await apiClient.delete(`/events/${eventId}`)
}

export async function getQueueStatus(eventId: string) {
  const res = await apiClient.get<{ success: boolean; data: QueueStatusResponse }>(`/queues/${eventId}/status`)
  return res.data.data
}

export async function getEventStats(eventId: string) {
  const res = await apiClient.get<{ success: boolean; data: EventStatsResponse }>(`/stats/${eventId}`)
  return res.data.data
}

export async function getEventLogs(eventId: string, limit = 50) {
  const res = await apiClient.get<{ success: boolean; data: EventLogEntry[] }>(`/stats/${eventId}/logs`, {
    params: { limit },
  })
  return res.data.data
}

export async function processQueue(eventId: string) {
  const res = await apiClient.post<{ success: boolean; data: { processed: number } }>(`/queues/${eventId}/process`)
  return res.data.data
}

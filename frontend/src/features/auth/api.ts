import apiClient from '@/shared/lib/api-client'

interface LoginRequest {
  email: string
  password: string
}

interface SignupRequest {
  email: string
  password: string
  companyName: string
}

interface AuthResponse {
  accessToken: string
}

interface TenantInfo {
  tenantId: string
  email: string
  companyName: string
  plan: string
  status: string
  apiKey: string
  usage: { currentMonth: number; limit: number }
  createdAt: string
}

export async function login(data: LoginRequest) {
  const res = await apiClient.post<{ success: boolean; data: AuthResponse }>('/tenants/login', data)
  return res.data.data
}

export async function signup(data: SignupRequest) {
  const res = await apiClient.post<{ success: boolean; data: AuthResponse }>('/tenants/signup', data)
  return res.data.data
}

export async function getMe() {
  const res = await apiClient.get<{ success: boolean; data: TenantInfo }>('/tenants/me')
  return res.data.data
}

export async function rotateApiKey() {
  const res = await apiClient.post<{ success: boolean; data: { apiKey: string } }>('/tenants/api-keys/rotate')
  return res.data.data
}

export type { TenantInfo }

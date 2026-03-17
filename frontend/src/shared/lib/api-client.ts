import axios from 'axios'
import { AUTH_TOKEN_KEY } from './constants'

/**
 * Axios API 클라이언트 — JWT 인증 자동 주입 + 401 자동 처리.
 * baseURL: '/api/v1' → Vite 프록시가 localhost:8080으로 포워딩.
 */
const apiClient = axios.create({
  baseURL: '/api/v1',
  headers: { 'Content-Type': 'application/json' },
})

/**
 * 요청 인터셉터 — 모든 API 요청에 JWT 토큰을 자동 주입.
 * localStorage에서 토큰을 읽어 Authorization: Bearer {token} 헤더에 추가.
 */
apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem(AUTH_TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/**
 * 응답 인터셉터 — 에러 응답을 공통 처리.
 *
 * 401 Unauthorized: 토큰 만료/무효 → localStorage에서 토큰 삭제 + 로그인 페이지 리다이렉트.
 * 이 방식으로 라우터의 beforeLoad에서는 토큰 존재 여부만 체크하면 됨.
 *
 * 서버 에러 메시지 추출: RFC 9457 응답의 message 또는 detail 필드를 우선 사용.
 * 서버 메시지가 없으면 Axios 기본 에러 메시지 사용.
 */
apiClient.interceptors.response.use(
  (res) => res,  // 성공 응답은 그대로 통과
  (error) => {
    // 401 → 토큰 만료 또는 무효화 → 강제 로그아웃
    if (error.response?.status === 401) {
      localStorage.removeItem(AUTH_TOKEN_KEY)
      window.location.href = '/login'
    }
    // 서버 에러 메시지 추출 (RFC 9457: message 또는 detail 필드)
    const serverMessage = error.response?.data?.message ?? error.response?.data?.detail
    return Promise.reject(new Error(serverMessage ?? error.message))
  },
)

export default apiClient

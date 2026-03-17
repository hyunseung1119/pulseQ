import { useEffect, useRef, useState } from 'react'

/** WebSocket 재접속 최대 시도 횟수 */
const MAX_RETRIES = 10

interface UseWebSocketOptions {
  url: string                          // WebSocket 서버 URL (ws:// 또는 wss://)
  onMessage?: (data: unknown) => void  // 메시지 수신 콜백
  enabled?: boolean                    // false면 연결하지 않음 (조건부 연결)
}

/**
 * WebSocket 커스텀 훅 — 연결 관리 + 지수 백오프 재접속.
 *
 * 설계 결정:
 * - onMessage를 ref로 관리 → 콜백 변경 시 재연결 방지 (useEffect 의존성에서 제외)
 * - 지수 백오프: 1s → 2s → 4s → 8s → ... → 최대 30s (서버 부하 방지)
 * - MAX_RETRIES 초과 시 재접속 중단 (무한 루프 방지)
 */
export function useWebSocket({ url, onMessage, enabled = true }: UseWebSocketOptions) {
  const wsRef = useRef<WebSocket | null>(null)             // WebSocket 인스턴스 참조
  const onMessageRef = useRef(onMessage)                   // 콜백 ref (재렌더링 시에도 최신 함수 유지)
  const retryCountRef = useRef(0)                          // 현재 재접속 시도 횟수
  const retryTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)  // 재접속 타이머 ID
  const [connected, setConnected] = useState(false)        // 연결 상태 (UI 표시용)

  // 매 렌더링마다 최신 onMessage 콜백을 ref에 갱신
  onMessageRef.current = onMessage

  useEffect(() => {
    // enabled=false면 연결하지 않음 (예: 이벤트 ID가 아직 없을 때)
    if (!enabled) return

    const connect = () => {
      const ws = new WebSocket(url)
      wsRef.current = ws

      // 연결 성공 시 재접속 카운터 초기화
      ws.onopen = () => {
        setConnected(true)
        retryCountRef.current = 0  // 연결 성공 → 카운터 리셋
      }

      // 연결 끊김 시 지수 백오프로 재접속
      ws.onclose = () => {
        setConnected(false)
        if (retryCountRef.current < MAX_RETRIES) {
          // 지수 백오프: 1s, 2s, 4s, 8s, 16s, 30s(max), 30s, ...
          const delay = Math.min(30_000, 1000 * Math.pow(2, retryCountRef.current))
          retryCountRef.current++
          retryTimerRef.current = setTimeout(connect, delay)
        }
        // MAX_RETRIES 초과 시 재접속 중단
      }

      // 메시지 수신 시 JSON 파싱 후 콜백 호출
      ws.onmessage = (e) => {
        try {
          const data: unknown = JSON.parse(e.data as string)
          onMessageRef.current?.(data)  // ref를 통해 항상 최신 콜백 호출
        } catch {
          // JSON이 아닌 메시지는 무시 (ping/pong 등)
        }
      }
    }

    connect()  // 최초 연결

    // 클린업: 컴포넌트 언마운트 시 타이머 정리 + 연결 종료
    return () => {
      if (retryTimerRef.current) clearTimeout(retryTimerRef.current)
      wsRef.current?.close()
    }
  }, [url, enabled])  // url 또는 enabled 변경 시 재연결

  return { connected }
}

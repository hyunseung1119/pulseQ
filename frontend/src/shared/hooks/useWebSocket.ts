import { useEffect, useRef, useState } from 'react'

const MAX_RETRIES = 10

interface UseWebSocketOptions {
  url: string
  onMessage?: (data: unknown) => void
  enabled?: boolean
}

export function useWebSocket({ url, onMessage, enabled = true }: UseWebSocketOptions) {
  const wsRef = useRef<WebSocket | null>(null)
  const onMessageRef = useRef(onMessage)
  const retryCountRef = useRef(0)
  const retryTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const [connected, setConnected] = useState(false)

  onMessageRef.current = onMessage

  useEffect(() => {
    if (!enabled) return

    const connect = () => {
      const ws = new WebSocket(url)
      wsRef.current = ws

      ws.onopen = () => {
        setConnected(true)
        retryCountRef.current = 0
      }
      ws.onclose = () => {
        setConnected(false)
        if (retryCountRef.current < MAX_RETRIES) {
          const delay = Math.min(30_000, 1000 * Math.pow(2, retryCountRef.current))
          retryCountRef.current++
          retryTimerRef.current = setTimeout(connect, delay)
        }
      }
      ws.onmessage = (e) => {
        try {
          const data: unknown = JSON.parse(e.data as string)
          onMessageRef.current?.(data)
        } catch {
          // ignore non-JSON messages
        }
      }
    }

    connect()

    return () => {
      if (retryTimerRef.current) clearTimeout(retryTimerRef.current)
      wsRef.current?.close()
    }
  }, [url, enabled])

  return { connected }
}

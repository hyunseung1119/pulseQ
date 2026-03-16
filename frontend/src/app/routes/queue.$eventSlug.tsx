import { useParams } from '@tanstack/react-router'
import { useState, useCallback } from 'react'
import { useWebSocket } from '@/shared/hooks/useWebSocket'
import { formatNumber, formatDuration } from '@/shared/lib/format'
import { Clock, Users, Zap } from 'lucide-react'

interface QueueMessage {
  type: 'POSITION_UPDATE' | 'ENTRY_GRANTED' | 'EVENT_ENDED'
  position?: number
  estimatedWaitSeconds?: number
  totalWaiting?: number
  processedPerSecond?: number
  totalProcessed?: number
  entryToken?: string
  redirectUrl?: string
  reason?: string
}

const VALID_TYPES = new Set(['POSITION_UPDATE', 'ENTRY_GRANTED', 'EVENT_ENDED'])

function isQueueMessage(data: unknown): data is QueueMessage {
  return typeof data === 'object' && data !== null && 'type' in data && VALID_TYPES.has((data as QueueMessage).type)
}

function sanitizeUrl(url: string | undefined): string | undefined {
  if (!url) return undefined
  try {
    const parsed = new URL(url, window.location.origin)
    if (parsed.protocol === 'https:' || parsed.protocol === 'http:') return parsed.href
  } catch {
    // invalid URL
  }
  return undefined
}

export function QueueWaitingPage() {
  const { eventSlug } = useParams({ strict: false }) as { eventSlug: string }
  const [state, setState] = useState<QueueMessage>({
    type: 'POSITION_UPDATE',
    position: undefined,
  })

  const onMessage = useCallback((data: unknown) => {
    if (isQueueMessage(data)) setState(data)
  }, [])

  const ticket = new URLSearchParams(window.location.search).get('ticket') ?? ''
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
  const wsUrl = `${protocol}://${window.location.host}/ws/queues/${eventSlug}?ticket=${ticket}`

  const { connected } = useWebSocket({ url: wsUrl, onMessage, enabled: !!ticket })

  if (state.type === 'ENTRY_GRANTED') {
    const safeUrl = sanitizeUrl(state.redirectUrl)
    return (
      <div className="flex min-h-screen items-center justify-center bg-gradient-to-b from-primary/5 to-background">
        <div className="w-full max-w-sm space-y-6 text-center">
          <div className="mx-auto flex h-20 w-20 items-center justify-center rounded-full bg-success/10">
            <Zap className="h-10 w-10 text-success" />
          </div>
          <h1 className="text-3xl font-bold text-foreground">Your turn!</h1>
          <p className="text-muted-foreground">You can now proceed.</p>
          {safeUrl && (
            <a
              href={safeUrl}
              className="inline-block rounded-md bg-primary px-6 py-3 font-medium text-primary-foreground"
            >
              Continue
            </a>
          )}
        </div>
      </div>
    )
  }

  if (state.type === 'EVENT_ENDED') {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background">
        <div className="text-center">
          <h1 className="text-2xl font-bold text-foreground">Event Ended</h1>
          <p className="mt-2 text-muted-foreground">{state.reason ?? 'This event has ended.'}</p>
        </div>
      </div>
    )
  }

  const position = state.position ?? 0
  const totalWaiting = state.totalWaiting ?? 0
  const progressPct = totalWaiting > 0 ? Math.round(((totalWaiting - position) / totalWaiting) * 100) : 0

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-b from-primary/5 to-background">
      <div className="w-full max-w-sm space-y-8 text-center">
        <h1 className="text-2xl font-bold text-foreground">{eventSlug.replace(/-/g, ' ')}</h1>

        <div className="rounded-2xl border-2 border-primary/20 bg-card p-8 shadow-lg">
          <p className="text-sm text-muted-foreground">Your Position</p>
          <p className="text-6xl font-bold text-primary">
            #{formatNumber(position)}
          </p>
        </div>

        <div className="space-y-2">
          <div className="flex items-center justify-center gap-2 text-muted-foreground">
            <Users className="h-4 w-4" />
            <span>{formatNumber(position - 1)} people ahead</span>
          </div>
          <div className="flex items-center justify-center gap-2 text-muted-foreground">
            <Clock className="h-4 w-4" />
            <span>Est. wait: {state.estimatedWaitSeconds ? formatDuration(state.estimatedWaitSeconds) : 'Calculating...'}</span>
          </div>
        </div>

        <div>
          <div className="mb-2 flex justify-between text-sm text-muted-foreground">
            <span>Progress</span>
            <span>{progressPct}%</span>
          </div>
          <div className="h-3 w-full overflow-hidden rounded-full bg-muted">
            <div
              className="h-full rounded-full bg-primary transition-all duration-1000"
              style={{ width: `${progressPct}%` }}
            />
          </div>
        </div>

        <div className="grid grid-cols-2 gap-4 rounded-lg border border-border bg-muted/30 p-4 text-sm">
          <div>
            <p className="font-medium text-foreground">{state.processedPerSecond ?? '-'}/s</p>
            <p className="text-muted-foreground">Speed</p>
          </div>
          <div>
            <p className="font-medium text-foreground">{formatNumber(state.totalProcessed ?? 0)}</p>
            <p className="text-muted-foreground">Processed</p>
          </div>
        </div>

        <p className="text-sm text-muted-foreground">
          {connected ? 'Connected — stay on this page' : 'Connecting...'}
        </p>
      </div>
    </div>
  )
}

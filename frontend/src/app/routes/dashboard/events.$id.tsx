import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from '@tanstack/react-router'
import { getEvent, getQueueStatus, getEventStats, getEventLogs } from '@/features/events/api'
import type { EventLogEntry } from '@/features/events/api'
import { StatCard } from '@/shared/components/ui/StatCard'
import { Badge } from '@/shared/components/ui/Badge'
import { Card, CardHeader, CardTitle } from '@/shared/components/ui/Card'
import { Button } from '@/shared/components/ui/Button'
import { useInterval } from '@/shared/hooks/useInterval'
import { formatNumber, formatDuration, formatPercent } from '@/shared/lib/format'
import { MAX_CHART_POINTS, DEFAULT_LOG_LIMIT, CHART_COLORS } from '@/shared/lib/constants'
import { Users, CheckCircle, XCircle, ShieldBan, ArrowLeft, Activity } from 'lucide-react'
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  BarChart,
  Bar,
} from 'recharts'
import { useState, useCallback } from 'react'

interface ChartPoint {
  time: string
  entered: number
  granted: number
  botBlocked: number
}

export function EventDetailPage() {
  const { id } = useParams({ strict: false }) as { id: string }
  const queryClient = useQueryClient()
  const [chartData, setChartData] = useState<ChartPoint[]>([])

  const { data: event } = useQuery({
    queryKey: ['event', id],
    queryFn: () => getEvent(id),
  })

  const { data: queueStatus } = useQuery({
    queryKey: ['queueStatus', id],
    queryFn: () => getQueueStatus(id),
    refetchInterval: 3000,
  })

  const { data: stats } = useQuery({
    queryKey: ['eventStats', id],
    queryFn: () => getEventStats(id),
    refetchInterval: 5000,
  })

  const { data: logs = [] } = useQuery({
    queryKey: ['eventLogs', id],
    queryFn: () => getEventLogs(id, DEFAULT_LOG_LIMIT),
    refetchInterval: 5000,
  })

  const updateChart = useCallback(() => {
    if (!stats) return
    setChartData((prev) => {
      const now = new Date().toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
      const next = [
        ...prev,
        {
          time: now,
          entered: stats.rates.enteredPerMinute,
          granted: stats.rates.grantedPerMinute,
          botBlocked: stats.totals.botBlocked,
        },
      ]
      return next.slice(-MAX_CHART_POINTS)
    })
  }, [stats])

  useInterval(updateChart, 5000)

  const botLogs = logs.filter((l: EventLogEntry) => l.eventType === 'BOT_DETECTED' || l.eventType === 'BOT_BLOCKED')

  const botDistribution = [
    { range: '0.0-0.3', count: 0, label: 'Low' },
    { range: '0.3-0.7', count: 0, label: 'Medium' },
    { range: '0.7-1.0', count: 0, label: 'High' },
  ]
  botLogs.forEach((l: EventLogEntry) => {
    const score = (l.payload?.botScore as number) ?? 0
    if (score < 0.3) botDistribution[0].count++
    else if (score < 0.7) botDistribution[1].count++
    else botDistribution[2].count++
  })

  if (!event) return <div className="py-10 text-center text-muted-foreground">Loading...</div>

  const progressPct = event.maxCapacity > 0
    ? Math.min(100, Math.round((event.totalProcessed / event.maxCapacity) * 100))
    : 0

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Link to="/dashboard/events">
            <Button variant="ghost" size="sm"><ArrowLeft className="h-4 w-4" /></Button>
          </Link>
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-2xl font-bold text-foreground">{event.name}</h1>
              <Badge variant={event.status}>{event.status}</Badge>
            </div>
            <p className="text-sm text-muted-foreground">/{event.slug}</p>
          </div>
        </div>
        <Button
          variant="outline"
          size="sm"
          onClick={() => queryClient.invalidateQueries({ queryKey: ['queueStatus', id] })}
        >
          <Activity className="mr-2 h-4 w-4" /> Refresh
        </Button>
      </div>

      <div className="grid gap-4 sm:grid-cols-4">
        <StatCard
          title="Waiting"
          value={formatNumber(queueStatus?.totalWaiting ?? 0)}
          icon={Users}
          trend={queueStatus?.currentRatePerSecond ? 'down' : undefined}
          trendValue={queueStatus?.currentRatePerSecond ? `${queueStatus.currentRatePerSecond}/s` : undefined}
        />
        <StatCard title="Processed" value={formatNumber(queueStatus?.totalProcessed ?? 0)} icon={CheckCircle} />
        <StatCard
          title="Abandoned"
          value={formatNumber(queueStatus?.totalAbandoned ?? 0)}
          icon={XCircle}
          subtitle={stats ? formatPercent(stats.percentages.abandonRate) : ''}
        />
        <StatCard
          title="Bot Blocked"
          value={formatNumber(queueStatus?.botBlocked ?? 0)}
          icon={ShieldBan}
        />
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader><CardTitle>Throughput (Real-time)</CardTitle></CardHeader>
          <div className="h-64">
            {chartData.length > 1 ? (
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={chartData}>
                  <CartesianGrid strokeDasharray="3 3" stroke={CHART_COLORS.grid} />
                  <XAxis dataKey="time" tick={{ fontSize: 11 }} />
                  <YAxis tick={{ fontSize: 11 }} />
                  <Tooltip />
                  <Line type="monotone" dataKey="entered" stroke={CHART_COLORS.primary} name="Entered/min" strokeWidth={2} dot={false} />
                  <Line type="monotone" dataKey="granted" stroke={CHART_COLORS.success} name="Granted/min" strokeWidth={2} dot={false} />
                </LineChart>
              </ResponsiveContainer>
            ) : (
              <div className="flex h-full items-center justify-center text-muted-foreground">
                Collecting data...
              </div>
            )}
          </div>
        </Card>

        <Card>
          <CardHeader><CardTitle>Queue Progress</CardTitle></CardHeader>
          <div className="flex flex-col items-center justify-center space-y-4 py-6">
            <div className="w-full">
              <div className="mb-2 flex justify-between text-sm">
                <span className="text-muted-foreground">Progress</span>
                <span className="font-medium text-foreground">{progressPct}%</span>
              </div>
              <div className="h-4 w-full overflow-hidden rounded-full bg-muted">
                <div
                  className="h-full rounded-full bg-primary transition-all duration-500"
                  style={{ width: `${progressPct}%` }}
                />
              </div>
            </div>
            <div className="grid w-full grid-cols-2 gap-4 text-center">
              <div>
                <p className="text-2xl font-bold text-foreground">{formatNumber(event.totalProcessed)}</p>
                <p className="text-sm text-muted-foreground">/ {formatNumber(event.maxCapacity)}</p>
              </div>
              <div>
                <p className="text-2xl font-bold text-foreground">
                  {queueStatus?.estimatedClearTimeSeconds
                    ? formatDuration(queueStatus.estimatedClearTimeSeconds)
                    : '-'}
                </p>
                <p className="text-sm text-muted-foreground">Est. clear time</p>
              </div>
            </div>
          </div>
        </Card>
      </div>

      {event.botDetectionEnabled && (
        <div className="grid gap-6 lg:grid-cols-2">
          <Card>
            <CardHeader><CardTitle>Bot Score Distribution</CardTitle></CardHeader>
            <div className="h-48">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={botDistribution}>
                  <CartesianGrid strokeDasharray="3 3" stroke={CHART_COLORS.grid} />
                  <XAxis dataKey="range" tick={{ fontSize: 12 }} />
                  <YAxis tick={{ fontSize: 12 }} />
                  <Tooltip />
                  <Bar dataKey="count" fill={CHART_COLORS.primary} radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </Card>

          <Card>
            <CardHeader><CardTitle>Recent Bot Blocks</CardTitle></CardHeader>
            <div className="max-h-48 overflow-auto">
              {botLogs.length === 0 ? (
                <p className="py-4 text-center text-sm text-muted-foreground">No bot blocks recorded</p>
              ) : (
                <table className="w-full text-left text-sm">
                  <thead>
                    <tr className="border-b border-border">
                      <th className="pb-2 font-medium text-muted-foreground">Time</th>
                      <th className="pb-2 font-medium text-muted-foreground">User</th>
                      <th className="pb-2 font-medium text-muted-foreground">Score</th>
                      <th className="pb-2 font-medium text-muted-foreground">Type</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border">
                    {botLogs.slice(0, 10).map((log: EventLogEntry) => (
                      <tr key={log.id}>
                        <td className="py-2 text-muted-foreground">
                          {new Date(log.createdAt).toLocaleTimeString('ko-KR')}
                        </td>
                        <td className="py-2 font-mono text-xs">{log.userId}</td>
                        <td className="py-2">
                          <span className="font-medium text-destructive">
                            {((log.payload?.botScore as number) ?? 0).toFixed(2)}
                          </span>
                        </td>
                        <td className="py-2">
                          <Badge variant="CANCELLED">{log.eventType}</Badge>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </Card>
        </div>
      )}
    </div>
  )
}

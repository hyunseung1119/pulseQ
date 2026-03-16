import { useQuery } from '@tanstack/react-query'
import { getMe } from '@/features/auth/api'
import { listEvents } from '@/features/events/api'
import { Card, CardHeader, CardTitle } from '@/shared/components/ui/Card'
import { formatNumber } from '@/shared/lib/format'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'

export function UsagePage() {
  const { data: tenant } = useQuery({ queryKey: ['me'], queryFn: getMe })
  const { data: events = [] } = useQuery({ queryKey: ['events'], queryFn: () => listEvents() })

  const usageCount = tenant?.usage?.currentMonth ?? 0
  const usageLimit = tenant?.usage?.limit ?? 1
  const usagePct = Math.min(100, Math.round((usageCount / usageLimit) * 100))

  const eventUsage = events
    .map((e) => ({
      name: e.name.length > 15 ? e.name.slice(0, 15) + '...' : e.name,
      processed: e.totalProcessed,
      botBlocked: e.totalBotBlocked,
    }))
    .sort((a, b) => b.processed - a.processed)
    .slice(0, 10)

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-foreground">Usage & Billing</h1>

      <Card>
        <CardHeader><CardTitle>Monthly API Usage</CardTitle></CardHeader>
        <div className="space-y-4">
          <div className="flex items-end justify-between">
            <div>
              <p className="text-4xl font-bold text-foreground">{formatNumber(usageCount)}</p>
              <p className="text-sm text-muted-foreground">/ {formatNumber(usageLimit)} requests</p>
            </div>
            <div className="text-right">
              <p className="text-2xl font-bold text-foreground">{usagePct}%</p>
              <p className="text-sm text-muted-foreground">of quota</p>
            </div>
          </div>
          <div className="h-3 w-full overflow-hidden rounded-full bg-muted">
            <div
              className={`h-full rounded-full transition-all duration-500 ${
                usagePct > 90 ? 'bg-destructive' : usagePct > 70 ? 'bg-warning' : 'bg-primary'
              }`}
              style={{ width: `${usagePct}%` }}
            />
          </div>
          <div className="flex justify-between text-sm text-muted-foreground">
            <span>Plan: {tenant?.plan ?? 'FREE'}</span>
            <span>
              {usagePct > 90 ? 'Consider upgrading' : `${formatNumber(usageLimit - usageCount)} remaining`}
            </span>
          </div>
        </div>
      </Card>

      <Card>
        <CardHeader><CardTitle>Usage by Event</CardTitle></CardHeader>
        <div className="h-64">
          {eventUsage.length > 0 ? (
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={eventUsage} layout="vertical">
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                <XAxis type="number" tick={{ fontSize: 11 }} />
                <YAxis dataKey="name" type="category" tick={{ fontSize: 11 }} width={120} />
                <Tooltip />
                <Bar dataKey="processed" fill="#6366f1" name="Processed" radius={[0, 4, 4, 0]} />
                <Bar dataKey="botBlocked" fill="#ef4444" name="Bot Blocked" radius={[0, 4, 4, 0]} />
              </BarChart>
            </ResponsiveContainer>
          ) : (
            <div className="flex h-full items-center justify-center text-muted-foreground">No events yet</div>
          )}
        </div>
      </Card>
    </div>
  )
}

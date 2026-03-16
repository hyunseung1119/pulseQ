import { useQuery } from '@tanstack/react-query'
import { Link } from '@tanstack/react-router'
import { listEvents } from '@/features/events/api'
import { getMe } from '@/features/auth/api'
import { StatCard } from '@/shared/components/ui/StatCard'
import { Badge } from '@/shared/components/ui/Badge'
import { Button } from '@/shared/components/ui/Button'
import { formatNumber, formatDateTime } from '@/shared/lib/format'
import { Calendar, Users, BarChart3, Plus, ArrowRight } from 'lucide-react'

export function DashboardIndex() {
  const { data: events = [] } = useQuery({ queryKey: ['events'], queryFn: () => listEvents() })
  const { data: tenant } = useQuery({ queryKey: ['me'], queryFn: getMe })

  const activeEvents = events.filter((e) => e.status === 'ACTIVE')
  const todayProcessed = events.reduce((sum, e) => sum + e.totalProcessed, 0)
  const usageCount = tenant?.usage?.currentMonth ?? 0
  const usageLimit = tenant?.usage?.limit ?? 0

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-foreground">Dashboard</h1>
        <Link to="/dashboard/events/new">
          <Button size="sm">
            <Plus className="mr-2 h-4 w-4" /> New Event
          </Button>
        </Link>
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <StatCard title="Active Events" value={activeEvents.length} icon={Calendar} subtitle={`${events.length} total`} />
        <StatCard title="Total Processed" value={formatNumber(todayProcessed)} icon={Users} />
        <StatCard
          title="API Usage (Month)"
          value={formatNumber(usageCount)}
          icon={BarChart3}
          subtitle={`/ ${formatNumber(usageLimit)}`}
        />
      </div>

      <div className="rounded-lg border border-border bg-card shadow-sm">
        <div className="flex items-center justify-between border-b border-border px-6 py-4">
          <h2 className="text-lg font-semibold text-card-foreground">Recent Events</h2>
          <Link to="/dashboard/events" className="text-sm font-medium text-primary hover:underline">
            View all <ArrowRight className="ml-1 inline h-3 w-3" />
          </Link>
        </div>
        <div className="divide-y divide-border">
          {events.length === 0 && (
            <p className="px-6 py-8 text-center text-muted-foreground">No events yet. Create your first event.</p>
          )}
          {events.slice(0, 5).map((event) => (
            <Link
              key={event.eventId}
              to="/dashboard/events/$id"
              params={{ id: event.eventId }}
              className="flex items-center justify-between px-6 py-4 transition-colors hover:bg-accent/50"
            >
              <div>
                <p className="font-medium text-card-foreground">{event.name}</p>
                <p className="text-sm text-muted-foreground">{formatDateTime(event.startAt)}</p>
              </div>
              <div className="flex items-center gap-4">
                <span className="text-sm text-muted-foreground">{formatNumber(event.totalProcessed)} processed</span>
                <Badge variant={event.status}>{event.status}</Badge>
              </div>
            </Link>
          ))}
        </div>
      </div>
    </div>
  )
}

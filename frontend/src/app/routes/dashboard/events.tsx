import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from '@tanstack/react-router'
import { listEvents, deleteEvent } from '@/features/events/api'
import { Badge } from '@/shared/components/ui/Badge'
import { Button } from '@/shared/components/ui/Button'
import { formatNumber, formatDateTime } from '@/shared/lib/format'
import { Plus, Trash2, Eye, Edit } from 'lucide-react'

export function EventsPage() {
  const queryClient = useQueryClient()
  const { data: events = [], isLoading } = useQuery({ queryKey: ['events'], queryFn: () => listEvents() })

  const deleteMutation = useMutation({
    mutationFn: deleteEvent,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['events'] }),
  })

  if (isLoading) return <div className="py-10 text-center text-muted-foreground">Loading...</div>

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-foreground">Events</h1>
        <Link to="/dashboard/events/new">
          <Button size="sm">
            <Plus className="mr-2 h-4 w-4" /> New Event
          </Button>
        </Link>
      </div>

      <div className="overflow-hidden rounded-lg border border-border bg-card shadow-sm">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-border bg-muted/50">
            <tr>
              <th className="px-6 py-3 font-medium text-muted-foreground">Name</th>
              <th className="px-6 py-3 font-medium text-muted-foreground">Status</th>
              <th className="px-6 py-3 font-medium text-muted-foreground">Capacity</th>
              <th className="px-6 py-3 font-medium text-muted-foreground">Processed</th>
              <th className="px-6 py-3 font-medium text-muted-foreground">Bot</th>
              <th className="px-6 py-3 font-medium text-muted-foreground">Start</th>
              <th className="px-6 py-3 font-medium text-muted-foreground">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {events.map((event) => (
              <tr key={event.eventId} className="transition-colors hover:bg-accent/30">
                <td className="px-6 py-4">
                  <p className="font-medium text-card-foreground">{event.name}</p>
                  <p className="text-xs text-muted-foreground">/{event.slug}</p>
                </td>
                <td className="px-6 py-4">
                  <Badge variant={event.status}>{event.status}</Badge>
                </td>
                <td className="px-6 py-4 text-muted-foreground">{formatNumber(event.maxCapacity)}</td>
                <td className="px-6 py-4 text-muted-foreground">{formatNumber(event.totalProcessed)}</td>
                <td className="px-6 py-4">
                  {event.botDetectionEnabled ? (
                    <span className="text-xs font-medium text-success">ON</span>
                  ) : (
                    <span className="text-xs text-muted-foreground">OFF</span>
                  )}
                </td>
                <td className="px-6 py-4 text-muted-foreground">{formatDateTime(event.startAt)}</td>
                <td className="px-6 py-4">
                  <div className="flex items-center gap-2">
                    <Link to="/dashboard/events/$id" params={{ id: event.eventId }}>
                      <Button variant="ghost" size="sm"><Eye className="h-4 w-4" /></Button>
                    </Link>
                    <Link to="/dashboard/events/$id/edit" params={{ id: event.eventId }}>
                      <Button variant="ghost" size="sm"><Edit className="h-4 w-4" /></Button>
                    </Link>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => {
                        if (confirm('Delete this event?')) deleteMutation.mutate(event.eventId)
                      }}
                    >
                      <Trash2 className="h-4 w-4 text-destructive" />
                    </Button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {events.length === 0 && (
          <p className="py-8 text-center text-muted-foreground">No events yet</p>
        )}
      </div>
    </div>
  )
}

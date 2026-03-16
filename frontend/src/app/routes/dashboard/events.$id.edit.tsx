import { useNavigate, useParams, Link } from '@tanstack/react-router'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getEvent, updateEvent } from '@/features/events/api'
import { EventForm, type EventFormValues } from '@/features/events/EventForm'
import { Button } from '@/shared/components/ui/Button'
import { ArrowLeft } from 'lucide-react'

export function EventEditPage() {
  const { id } = useParams({ strict: false }) as { id: string }
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const { data: event } = useQuery({
    queryKey: ['event', id],
    queryFn: () => getEvent(id),
  })

  const mutation = useMutation({
    mutationFn: (data: Partial<EventFormValues>) => updateEvent(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['event', id] })
      navigate({ to: '/dashboard/events/$id', params: { id } })
    },
  })

  if (!event) return <div className="py-10 text-center text-muted-foreground">Loading...</div>

  const initialValues: EventFormValues = {
    name: event.name,
    slug: event.slug,
    maxCapacity: event.maxCapacity,
    rateLimit: event.rateLimit,
    startAt: '',
    endAt: '',
    botDetectionEnabled: event.botDetectionEnabled,
    botScoreThreshold: event.botScoreThreshold ?? 0.8,
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div className="flex items-center gap-3">
        <Link to="/dashboard/events/$id" params={{ id }}>
          <Button variant="ghost" size="sm"><ArrowLeft className="h-4 w-4" /></Button>
        </Link>
        <h1 className="text-2xl font-bold text-foreground">Edit: {event.name}</h1>
      </div>

      {mutation.isError && (
        <p className="text-sm text-destructive">{(mutation.error as Error)?.message || 'Failed to save'}</p>
      )}

      <EventForm
        initialValues={initialValues}
        onSubmit={(values) => mutation.mutate(values)}
        isSubmitting={mutation.isPending}
        submitLabel="Save Changes"
        showSlugAndDates={false}
      />
    </div>
  )
}

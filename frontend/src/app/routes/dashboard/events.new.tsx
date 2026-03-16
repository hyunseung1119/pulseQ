import { useNavigate, Link } from '@tanstack/react-router'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { createEvent } from '@/features/events/api'
import { EventForm, type EventFormValues } from '@/features/events/EventForm'
import { Button } from '@/shared/components/ui/Button'
import { ArrowLeft } from 'lucide-react'

const initialValues: EventFormValues = {
  name: '',
  slug: '',
  maxCapacity: 10000,
  rateLimit: 100,
  startAt: '',
  endAt: '',
  botDetectionEnabled: true,
  botScoreThreshold: 0.8,
}

export function EventNewPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const mutation = useMutation({
    mutationFn: createEvent,
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['events'] })
      navigate({ to: '/dashboard/events/$id', params: { id: data.eventId } })
    },
  })

  const handleSubmit = (values: EventFormValues) => {
    mutation.mutate({
      ...values,
      startAt: new Date(values.startAt).toISOString(),
      endAt: new Date(values.endAt).toISOString(),
    })
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div className="flex items-center gap-3">
        <Link to="/dashboard/events">
          <Button variant="ghost" size="sm"><ArrowLeft className="h-4 w-4" /></Button>
        </Link>
        <h1 className="text-2xl font-bold text-foreground">New Event</h1>
      </div>

      {mutation.isError && (
        <p className="text-sm text-destructive">{(mutation.error as Error)?.message || 'Failed to create event'}</p>
      )}

      <EventForm
        initialValues={initialValues}
        onSubmit={handleSubmit}
        isSubmitting={mutation.isPending}
        submitLabel="Create Event"
      />
    </div>
  )
}

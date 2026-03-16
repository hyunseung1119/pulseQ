import { useState, type FormEvent } from 'react'
import { Button } from '@/shared/components/ui/Button'
import { Input } from '@/shared/components/ui/Input'
import { Card, CardHeader, CardTitle } from '@/shared/components/ui/Card'

export interface EventFormValues {
  name: string
  slug: string
  maxCapacity: number
  rateLimit: number
  startAt: string
  endAt: string
  botDetectionEnabled: boolean
  botScoreThreshold: number
}

interface EventFormProps {
  initialValues: EventFormValues
  onSubmit: (values: EventFormValues) => void
  isSubmitting: boolean
  submitLabel: string
  showSlugAndDates?: boolean
}

export function EventForm({ initialValues, onSubmit, isSubmitting, submitLabel, showSlugAndDates = true }: EventFormProps) {
  const [form, setForm] = useState(initialValues)

  const update = <K extends keyof EventFormValues>(field: K, value: EventFormValues[K]) =>
    setForm((prev) => ({ ...prev, [field]: value }))

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (showSlugAndDates && form.endAt && form.startAt && new Date(form.endAt) <= new Date(form.startAt)) {
      alert('End time must be after start time')
      return
    }
    onSubmit(form)
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-6">
      <Card>
        <CardHeader><CardTitle>Basic Info</CardTitle></CardHeader>
        <div className="space-y-4">
          <div>
            <label htmlFor="event-name" className="mb-1.5 block text-sm font-medium">Event Name</label>
            <Input id="event-name" value={form.name} onChange={(e) => update('name', e.target.value)} placeholder="BTS Seoul Concert 2026" required />
          </div>
          {showSlugAndDates && (
            <>
              <div>
                <label htmlFor="event-slug" className="mb-1.5 block text-sm font-medium">Slug (URL)</label>
                <Input id="event-slug" value={form.slug} onChange={(e) => update('slug', e.target.value)} placeholder="bts-seoul-2026" required />
              </div>
            </>
          )}
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label htmlFor="max-capacity" className="mb-1.5 block text-sm font-medium">Max Capacity</label>
              <Input id="max-capacity" type="number" value={form.maxCapacity} onChange={(e) => update('maxCapacity', parseInt(e.target.value) || 0)} min={1} required />
            </div>
            <div>
              <label htmlFor="rate-limit" className="mb-1.5 block text-sm font-medium">Rate Limit (/s)</label>
              <Input id="rate-limit" type="number" value={form.rateLimit} onChange={(e) => update('rateLimit', parseInt(e.target.value) || 0)} min={1} required />
            </div>
          </div>
          {showSlugAndDates && (
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label htmlFor="start-at" className="mb-1.5 block text-sm font-medium">Start Time</label>
                <Input id="start-at" type="datetime-local" value={form.startAt} onChange={(e) => update('startAt', e.target.value)} required />
              </div>
              <div>
                <label htmlFor="end-at" className="mb-1.5 block text-sm font-medium">End Time</label>
                <Input id="end-at" type="datetime-local" value={form.endAt} onChange={(e) => update('endAt', e.target.value)} required />
              </div>
            </div>
          )}
        </div>
      </Card>

      <Card>
        <CardHeader><CardTitle>Bot Detection</CardTitle></CardHeader>
        <div className="space-y-4">
          <label className="flex items-center gap-3">
            <input
              type="checkbox"
              checked={form.botDetectionEnabled}
              onChange={(e) => update('botDetectionEnabled', e.target.checked)}
              className="h-4 w-4 rounded border-border text-primary accent-primary"
            />
            <span className="text-sm font-medium">Enable Bot Detection</span>
          </label>
          {form.botDetectionEnabled && (
            <div>
              <label htmlFor="bot-threshold" className="mb-1.5 block text-sm font-medium">Score Threshold (0.5 - 1.0)</label>
              <Input
                id="bot-threshold"
                type="number"
                step={0.05}
                min={0.5}
                max={1.0}
                value={form.botScoreThreshold}
                onChange={(e) => update('botScoreThreshold', parseFloat(e.target.value) || 0.8)}
              />
            </div>
          )}
        </div>
      </Card>

      <div className="flex justify-end">
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? 'Saving...' : submitLabel}
        </Button>
      </div>
    </form>
  )
}

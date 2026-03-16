import type { HTMLAttributes } from 'react'
import { cn } from '@/shared/lib/cn'

const statusColors: Record<string, string> = {
  ACTIVE: 'bg-success/10 text-success border-success/20',
  SCHEDULED: 'bg-primary/10 text-primary border-primary/20',
  PAUSED: 'bg-warning/10 text-warning border-warning/20',
  COMPLETED: 'bg-muted text-muted-foreground border-border',
  CANCELLED: 'bg-destructive/10 text-destructive border-destructive/20',
}

interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  variant?: string
}

export function Badge({ variant = 'default', className, ...props }: BadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-medium',
        statusColors[variant] ?? 'bg-secondary text-secondary-foreground border-border',
        className,
      )}
      {...props}
    />
  )
}

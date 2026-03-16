import { cn } from '@/shared/lib/cn'
import type { LucideIcon } from 'lucide-react'

interface StatCardProps {
  title: string
  value: string | number
  subtitle?: string
  icon: LucideIcon
  trend?: 'up' | 'down'
  trendValue?: string
  className?: string
}

export function StatCard({ title, value, subtitle, icon: Icon, trend, trendValue, className }: StatCardProps) {
  return (
    <div className={cn('rounded-lg border border-border bg-card p-5 shadow-sm', className)}>
      <div className="flex items-center justify-between">
        <p className="text-sm font-medium text-muted-foreground">{title}</p>
        <Icon className="h-5 w-5 text-muted-foreground" />
      </div>
      <p className="mt-2 text-3xl font-bold text-card-foreground">{value}</p>
      {(subtitle || trendValue) && (
        <p className="mt-1 text-sm text-muted-foreground">
          {trend && (
            <span className={cn('font-medium', trend === 'up' ? 'text-success' : 'text-destructive')}>
              {trend === 'up' ? '\u25b2' : '\u25bc'} {trendValue}
            </span>
          )}{' '}
          {subtitle}
        </p>
      )}
    </div>
  )
}

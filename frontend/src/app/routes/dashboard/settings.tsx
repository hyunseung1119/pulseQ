import { useQuery } from '@tanstack/react-query'
import { getMe } from '@/features/auth/api'
import { Card, CardHeader, CardTitle } from '@/shared/components/ui/Card'
import { formatDateTime } from '@/shared/lib/format'
import { Badge } from '@/shared/components/ui/Badge'

export function SettingsPage() {
  const { data: tenant } = useQuery({ queryKey: ['me'], queryFn: getMe })

  if (!tenant) return <div className="py-10 text-center text-muted-foreground">Loading...</div>

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <h1 className="text-2xl font-bold text-foreground">Settings</h1>

      <Card>
        <CardHeader><CardTitle>Account Info</CardTitle></CardHeader>
        <div className="space-y-3">
          <div className="flex justify-between">
            <span className="text-sm text-muted-foreground">Email</span>
            <span className="text-sm font-medium text-foreground">{tenant.email}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-sm text-muted-foreground">Company</span>
            <span className="text-sm font-medium text-foreground">{tenant.companyName}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-sm text-muted-foreground">Plan</span>
            <Badge variant="ACTIVE">{tenant.plan}</Badge>
          </div>
          <div className="flex justify-between">
            <span className="text-sm text-muted-foreground">Status</span>
            <Badge variant={tenant.status}>{tenant.status}</Badge>
          </div>
          <div className="flex justify-between">
            <span className="text-sm text-muted-foreground">Created</span>
            <span className="text-sm text-foreground">{formatDateTime(tenant.createdAt)}</span>
          </div>
        </div>
      </Card>

      <Card>
        <CardHeader><CardTitle>Rate Limits</CardTitle></CardHeader>
        <div className="space-y-3">
          <div className="flex justify-between">
            <span className="text-sm text-muted-foreground">Monthly quota</span>
            <span className="text-sm font-medium text-foreground">{(tenant.usage?.limit ?? 0).toLocaleString()}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-sm text-muted-foreground">Used this month</span>
            <span className="text-sm font-medium text-foreground">{(tenant.usage?.currentMonth ?? 0).toLocaleString()}</span>
          </div>
        </div>
      </Card>
    </div>
  )
}

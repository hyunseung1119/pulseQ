import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getMe, rotateApiKey } from '@/features/auth/api'
import { Card, CardHeader, CardTitle } from '@/shared/components/ui/Card'
import { Button } from '@/shared/components/ui/Button'
import { Copy, RefreshCw, Key } from 'lucide-react'
import { useState } from 'react'

export function ApiKeysPage() {
  const queryClient = useQueryClient()
  const { data: tenant } = useQuery({ queryKey: ['me'], queryFn: getMe })
  const [copied, setCopied] = useState(false)

  const rotateMutation = useMutation({
    mutationFn: rotateApiKey,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['me'] }),
  })

  const handleCopy = () => {
    if (tenant?.apiKey) {
      navigator.clipboard.writeText(tenant.apiKey)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    }
  }

  const maskedKey = tenant?.apiKey
    ? `${tenant.apiKey.slice(0, 12)}${'*'.repeat(20)}`
    : ''

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <h1 className="text-2xl font-bold text-foreground">API Keys</h1>

      <Card>
        <CardHeader><CardTitle>Current API Key</CardTitle></CardHeader>
        <div className="flex items-center gap-3">
          <div className="flex flex-1 items-center gap-3 rounded-md border border-border bg-muted/50 px-4 py-3">
            <Key className="h-4 w-4 text-muted-foreground" />
            <code className="flex-1 text-sm font-mono">{maskedKey}</code>
          </div>
          <Button variant="outline" size="sm" onClick={handleCopy}>
            <Copy className="mr-2 h-4 w-4" />
            {copied ? 'Copied!' : 'Copy'}
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              if (confirm('Rotate API key? The current key will be invalidated.')) {
                rotateMutation.mutate()
              }
            }}
            disabled={rotateMutation.isPending}
          >
            <RefreshCw className="mr-2 h-4 w-4" />
            Rotate
          </Button>
        </div>
      </Card>

      <Card>
        <CardHeader><CardTitle>Integration Guide</CardTitle></CardHeader>
        <div className="rounded-md bg-foreground/5 p-4">
          <pre className="overflow-x-auto text-sm text-foreground"><code>{`# Enter queue
curl -X POST https://api.pulseq.io/v1/queues/{eventId}/enter \\
  -H "X-API-Key: ${tenant?.apiKey?.slice(0, 12) ?? 'pq_live_xxx'}..." \\
  -H "Content-Type: application/json" \\
  -d '{"userId": "user_1"}'

# Check position
curl https://api.pulseq.io/v1/queues/{eventId}/position/{ticket} \\
  -H "X-API-Key: ${tenant?.apiKey?.slice(0, 12) ?? 'pq_live_xxx'}..."

# WebSocket (real-time)
ws://api.pulseq.io/ws/queues/{eventId}?ticket={ticket}&apiKey={key}`}</code></pre>
        </div>
      </Card>
    </div>
  )
}

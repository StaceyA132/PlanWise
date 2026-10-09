import { statusLabel } from '../format'
import type { PaymentStatus, PlanStatus } from '../types'

export function StatusBadge({ status }: { status: PlanStatus | PaymentStatus }) {
  return <span className={`badge badge-${status.toLowerCase()}`}>{statusLabel(status)}</span>
}

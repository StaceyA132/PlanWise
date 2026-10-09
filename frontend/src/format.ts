import type { Frequency, PaymentStatus, PlanStatus } from './types'

const usd = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' })

export function formatMoney(amount: number): string {
  return usd.format(amount)
}

/**
 * "2026-10-23" -> "Oct 23, 2026".
 * Careful: new Date("2026-10-23") means midnight UTC, which is still Oct 22 in the US.
 * Building the date from its parts keeps it in the user's local time zone.
 */
export function formatDate(isoDate: string): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  return new Date(year, month - 1, day).toLocaleDateString('en-US', {
    month: 'short', day: 'numeric', year: 'numeric',
  })
}

export function formatApr(apr: number): string {
  return `${Number(apr)}% APR`
}

export function planName(numPayments: number, frequency: Frequency): string {
  return frequency === 'BIWEEKLY' ? `Pay in ${numPayments}` : `${numPayments} monthly payments`
}

export function frequencyLabel(frequency: Frequency): string {
  return frequency === 'BIWEEKLY' ? 'every 2 weeks' : 'per month'
}

const statusLabels: Record<PlanStatus | PaymentStatus, string> = {
  ACTIVE: 'Active',
  PAID_OFF: 'Paid off',
  UPCOMING: 'Upcoming',
  PAID: 'Paid',
  LATE: 'Late',
}

export function statusLabel(status: PlanStatus | PaymentStatus): string {
  return statusLabels[status]
}

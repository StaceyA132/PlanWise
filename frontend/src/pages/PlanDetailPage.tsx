import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { api } from '../api'
import { ScheduleTable } from '../components/ScheduleTable'
import { StatusBadge } from '../components/StatusBadge'
import { formatApr, formatDate, formatMoney, planName } from '../format'
import type { Payment, Plan } from '../types'

export function PlanDetailPage() {
  const { id } = useParams()
  const [plan, setPlan] = useState<Plan | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [payingId, setPayingId] = useState<number | null>(null)

  useEffect(() => {
    let cancelled = false
    api.getPlan(Number(id))
      .then((data) => !cancelled && setPlan(data))
      .catch((err: Error) => !cancelled && setError(err.message))
    return () => { cancelled = true }
  }, [id])

  async function pay(payment: Payment) {
    if (!window.confirm(`Pay ${formatMoney(payment.amount)} due ${formatDate(payment.dueDate)}?`)) return
    setError(null)
    setPayingId(payment.id)
    try {
      setPlan(await api.pay(payment.id)) // the API returns the updated plan
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Payment failed')
    } finally {
      setPayingId(null)
    }
  }

  if (!plan) {
    return error
      ? <><p className="error" role="alert">{error}</p><Link to="/">Back to dashboard</Link></>
      : <p className="muted">Loading…</p>
  }

  return (
    <>
      <Link to="/" className="back-link">← Dashboard</Link>
      <div className="page-title">
        <h1>{plan.itemName}</h1>
        <StatusBadge status={plan.status} />
      </div>
      <p className="muted">
        {planName(plan.numPayments, plan.frequency)} · {formatApr(plan.apr)} · purchase price{' '}
        {formatMoney(plan.purchaseAmount)}
      </p>

      <section className="stats">
        <div className="card stat">
          <span className="stat-label">Total cost</span>
          <span className="stat-value">{formatMoney(plan.totalCost)}</span>
          <span className="stat-detail">includes {formatMoney(plan.totalInterest)} interest</span>
        </div>
        <div className="card stat">
          <span className="stat-label">Paid</span>
          <span className="stat-value">{formatMoney(plan.amountPaid)}</span>
        </div>
        <div className="card stat">
          <span className="stat-label">Remaining</span>
          <span className="stat-value">{formatMoney(plan.amountRemaining)}</span>
        </div>
      </section>

      {error && <p className="error" role="alert">{error}</p>}

      <h2>Payment schedule</h2>
      <ScheduleTable payments={plan.payments} onPay={pay} payingId={payingId} />
    </>
  )
}

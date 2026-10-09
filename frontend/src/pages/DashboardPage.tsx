import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { api } from '../api'
import { StatusBadge } from '../components/StatusBadge'
import { formatDate, formatMoney, planName } from '../format'
import type { Dashboard, Plan } from '../types'

export function DashboardPage() {
  const [dashboard, setDashboard] = useState<Dashboard | null>(null)
  const [plans, setPlans] = useState<Plan[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false // ignore the result if the user leaves the page before it arrives
    Promise.all([api.dashboard(), api.listPlans()])
      .then(([dashboardData, planData]) => {
        if (cancelled) return
        setDashboard(dashboardData)
        setPlans(planData)
      })
      .catch((err: Error) => !cancelled && setError(err.message))
    return () => { cancelled = true }
  }, [])

  if (error) return <p className="error" role="alert">{error}</p>
  if (!dashboard) return <p className="muted">Loading…</p>

  const next = dashboard.nextPayment
  return (
    <>
      <div className="page-title">
        <h1>Dashboard</h1>
        <Link to="/purchases/new" className="button">New purchase</Link>
      </div>

      <section className="stats">
        <div className="card stat">
          <span className="stat-label">Total owed</span>
          <span className="stat-value">{formatMoney(dashboard.totalOwed)}</span>
        </div>
        <div className="card stat">
          <span className="stat-label">Next payment</span>
          {next ? (
            <>
              <span className="stat-value">{formatMoney(next.amount)}</span>
              <span className="stat-detail">
                {formatDate(next.dueDate)} · <Link to={`/plans/${next.planId}`}>{next.itemName}</Link>
                {next.status === 'LATE' && <> <StatusBadge status="LATE" /></>}
              </span>
            </>
          ) : (
            <span className="stat-value muted">None</span>
          )}
        </div>
        <div className="card stat">
          <span className="stat-label">Active plans</span>
          <span className="stat-value">{dashboard.activePlans}</span>
        </div>
        <div className="card stat">
          <span className="stat-label">Paid off</span>
          <span className="stat-value">{dashboard.paidOffPlans}</span>
        </div>
      </section>

      <section>
        <h2>Your plans</h2>
        {plans.length === 0 ? (
          <div className="card empty">
            <p>No plans yet.</p>
            <Link to="/purchases/new" className="button">Start a purchase</Link>
          </div>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Item</th>
                  <th>Plan</th>
                  <th className="num">Total cost</th>
                  <th className="num">Remaining</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {plans.map((plan) => (
                  <tr key={plan.id}>
                    <td><Link to={`/plans/${plan.id}`}>{plan.itemName}</Link></td>
                    <td>{planName(plan.numPayments, plan.frequency)}</td>
                    <td className="num">{formatMoney(plan.totalCost)}</td>
                    <td className="num">{formatMoney(plan.amountRemaining)}</td>
                    <td><StatusBadge status={plan.status} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </>
  )
}

import { useEffect, useState } from 'react'
import { api } from '../api'
import { formatMoney } from '../format'
import type { MonthlyInsights } from '../types'

/** "2026-10" for the current month, in the user's local time. */
function currentMonth(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

function monthName(month: string): string {
  const [year, m] = month.split('-').map(Number)
  return new Date(year, m - 1, 1).toLocaleDateString('en-US', { month: 'long', year: 'numeric' })
}

/** Spending by category for a month, with an AI-written summary of those totals. */
export function InsightsPage() {
  const [month, setMonth] = useState(currentMonth)
  const [insights, setInsights] = useState<MonthlyInsights | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!month) return // the month input is empty while the user is clearing it
    let cancelled = false
    api.monthlyInsights(month)
      .then((data) => !cancelled && setInsights(data))
      .catch((err: Error) => !cancelled && setError(err.message))
    return () => { cancelled = true }
  }, [month])

  return (
    <>
      <div className="page-title">
        <h1>Monthly insights</h1>
        <label className="month-picker">
          <span className="muted">Month</span>
          <input type="month" value={month} max={currentMonth()} onChange={(e) => { setError(null); setMonth(e.target.value) }} />
        </label>
      </div>

      {error && <p className="error" role="alert">{error}</p>}
      {!insights && !error && <p className="muted">Loading…</p>}

      {insights && insights.purchaseCount === 0 && (
        <div className="card empty"><p>No purchases in {monthName(insights.month)}.</p></div>
      )}

      {insights && insights.purchaseCount > 0 && (
        <>
          <section className="stats">
            <div className="card stat">
              <span className="stat-label">Spent in {monthName(insights.month)}</span>
              <span className="stat-value">{formatMoney(insights.totalSpent)}</span>
              <span className="stat-detail">
                {insights.purchaseCount} purchase{insights.purchaseCount === 1 ? '' : 's'}
              </span>
            </div>
          </section>

          <div className="card answer insights-summary">
            <h2>Summary</h2>
            {insights.aiAvailable && insights.summary ? (
              <p className="answer-text">{insights.summary}</p>
            ) : (
              <p className="muted">The AI summary isn't available right now. The totals below are complete.</p>
            )}
            <p className="fine-print">Categories are suggested by AI; totals are calculated by PlanWise.</p>
          </div>

          <h2>By category</h2>
          <ul className="card category-list">
            {insights.categories.map((c) => (
              <li key={c.category}>
                <div className="category-row">
                  <span className="strong">{c.category}</span>
                  <span>{formatMoney(c.total)} <span className="muted">· {c.percentOfTotal}%</span></span>
                </div>
                {/* The bar width comes straight from the server's percentage. */}
                <div className="bar" aria-hidden="true"><div style={{ width: `${c.percentOfTotal}%` }} /></div>
                <span className="muted small">{c.purchaseCount} purchase{c.purchaseCount === 1 ? '' : 's'}</span>
              </li>
            ))}
          </ul>
        </>
      )}
    </>
  )
}

import { useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { api } from '../api'
import { PlanOptionCard } from '../components/PlanOptionCard'
import { formatMoney, planName } from '../format'
import type { QuoteResponse } from '../types'

/** Enter an item and amount, compare the plan options side by side, and pick one. */
export function NewPurchasePage() {
  const navigate = useNavigate()
  const [itemName, setItemName] = useState('')
  const [amount, setAmount] = useState('')
  const [quote, setQuote] = useState<QuoteResponse | null>(null)
  const [selected, setSelected] = useState<number | null>(null) // numPayments of the chosen option
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  // Editing the amount makes the current quote out of date, so clear it.
  function changeAmount(value: string) {
    setAmount(value)
    setQuote(null)
    setSelected(null)
  }

  async function getQuote(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      setQuote(await api.quote(amount))
      setSelected(null)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong')
    } finally {
      setBusy(false)
    }
  }

  async function confirmPlan() {
    if (selected === null) return
    setError(null)
    setBusy(true)
    try {
      const plan = await api.createPlan(itemName, amount, selected)
      navigate(`/plans/${plan.id}`)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong')
      setBusy(false)
    }
  }

  const chosen = quote?.options.find((option) => option.numPayments === selected)
  return (
    <>
      <h1>New purchase</h1>

      <form className="card form inline-form" onSubmit={getQuote}>
        <label>
          What are you buying?
          <input value={itemName} onChange={(e) => setItemName(e.target.value)} required maxLength={200}
                 placeholder="Laptop" />
        </label>
        <label>
          Amount
          <input type="number" min="0.01" max="10000" step="0.01" inputMode="decimal" value={amount}
                 onChange={(e) => changeAmount(e.target.value)} required placeholder="1200.00" />
        </label>
        <button type="submit" className="button" disabled={busy}>See plans</button>
      </form>

      {error && <p className="error" role="alert">{error}</p>}

      {quote && (
        <>
          <p className="notice">
            No hidden fees. Interest is simple interest on the remaining balance and is already included
            in every number below.
          </p>
          <section className="options">
            {quote.options.map((option) => (
              <PlanOptionCard key={option.numPayments} option={option}
                              selected={option.numPayments === selected}
                              onSelect={() => setSelected(option.numPayments)} />
            ))}
          </section>

          <div className="confirm-bar">
            {chosen ? (
              <>
                <span>
                  {itemName || 'Purchase'}: {planName(chosen.numPayments, chosen.frequency)},{' '}
                  <strong>{formatMoney(chosen.totalCost)}</strong> total
                </span>
                <button type="button" className="button" onClick={confirmPlan} disabled={busy || !itemName.trim()}>
                  {busy ? 'Saving…' : 'Confirm plan'}
                </button>
              </>
            ) : (
              <span className="muted">Choose a plan to continue.</span>
            )}
          </div>
        </>
      )}
    </>
  )
}

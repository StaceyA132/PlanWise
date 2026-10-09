import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router'
import { api } from '../api'
import { formatApr, formatMoney, frequencyLabel, planName } from '../format'
import type { AssistantResponse } from '../types'

/** Ask whether a purchase fits your budget. The AI's answer sits next to the real numbers it was given. */
export function AssistantPage() {
  const [itemName, setItemName] = useState('')
  const [amount, setAmount] = useState('')
  const [question, setQuestion] = useState('')
  const [result, setResult] = useState<AssistantResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      setResult(await api.ask(itemName, amount, question))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong')
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <h1>Plan assistant</h1>
      <p className="muted">Ask whether a purchase fits your budget. PlanWise calculates every number; the AI only explains them.</p>

      <form className="card form" onSubmit={handleSubmit}>
        <div className="form-row">
          <label>
            Item
            <input value={itemName} onChange={(e) => setItemName(e.target.value)} maxLength={200} placeholder="Laptop" />
          </label>
          <label>
            Amount
            <input type="number" min="0.01" max="10000" step="0.01" inputMode="decimal" required value={amount}
                   onChange={(e) => setAmount(e.target.value)} placeholder="1200.00" />
          </label>
        </div>
        <label>
          <span>Your question <span className="muted">(optional)</span></span>
          <textarea value={question} onChange={(e) => setQuestion(e.target.value)} maxLength={500} rows={2}
                    placeholder="Which plan fits my budget best?" />
        </label>
        <div>
          <button type="submit" className="button" disabled={busy}>{busy ? 'Thinking…' : 'Ask'}</button>
        </div>
      </form>

      {error && <p className="error" role="alert">{error}</p>}
      {result && <AssistantResult result={result} />}
    </>
  )
}

function AssistantResult({ result }: { result: AssistantResponse }) {
  const { budget } = result
  return (
    <section className="assistant-result">
      <div className="card answer">
        <h2>Assistant</h2>
        {result.aiAvailable && result.explanation ? (
          <p className="answer-text">{result.explanation}</p>
        ) : (
          <p className="muted">
            The assistant isn't available right now. The plan numbers on this page are complete and accurate on their own.
          </p>
        )}
        <p className="fine-print">AI-written summary of the numbers shown. General information, not financial advice.</p>
      </div>

      <div className="numbers">
        <h2>The numbers</h2>
        <dl className="facts card">
          <dt>Monthly income</dt>
          <dd>{budget.monthlyIncome === null ? 'Not provided' : formatMoney(budget.monthlyIncome)}</dd>
          <dt>Due within a month from your {budget.activePlanCount} active plan{budget.activePlanCount === 1 ? '' : 's'}</dt>
          <dd>{formatMoney(budget.existingDueWithinMonth)}</dd>
        </dl>

        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Plan</th>
                <th className="num">Payment</th>
                <th className="num">Total cost</th>
                <th className="num">Due within a month*</th>
              </tr>
            </thead>
            <tbody>
              {result.options.map((option, i) => {
                const optionBudget = budget.options[i]
                return (
                  <tr key={option.numPayments}>
                    <td>{planName(option.numPayments, option.frequency)}<br />
                      <span className="muted">{formatApr(option.apr)}</span></td>
                    <td className="num">{formatMoney(option.paymentAmount)}<br />
                      <span className="muted">{frequencyLabel(option.frequency)}</span></td>
                    <td className="num">{formatMoney(option.totalCost)}<br />
                      <span className="muted">{formatMoney(option.totalInterest)} interest</span></td>
                    <td className="num">{formatMoney(optionBudget.totalDueWithinMonth)}<br />
                      <span className="muted">
                        {optionBudget.percentOfIncome === null ? 'income unknown' : `${optionBudget.percentOfIncome}% of income`}
                      </span></td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
        <p className="fine-print">* This plan's payments due within a month, plus what your current plans already have due.</p>
        <Link to="/purchases/new" className="button secondary">Start this purchase</Link>
      </div>
    </section>
  )
}

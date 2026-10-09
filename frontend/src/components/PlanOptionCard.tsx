import { formatApr, formatDate, formatMoney, frequencyLabel, planName } from '../format'
import type { PlanOption } from '../types'

interface Props {
  option: PlanOption
  selected: boolean
  onSelect: () => void
}

/** One plan option with every cost shown upfront, plus its full schedule. */
export function PlanOptionCard({ option, selected, onSelect }: Props) {
  return (
    <article className={`card option-card${selected ? ' selected' : ''}`}>
      <h3>{planName(option.numPayments, option.frequency)}</h3>
      <p className="option-payment">
        {formatMoney(option.paymentAmount)}
        <span> {frequencyLabel(option.frequency)}</span>
      </p>

      <dl className="facts">
        <dt>APR</dt>
        <dd>{formatApr(option.apr)}</dd>
        <dt>Total interest</dt>
        <dd>{formatMoney(option.totalInterest)}</dd>
        <dt>Total cost</dt>
        <dd className="strong">{formatMoney(option.totalCost)}</dd>
      </dl>

      <details>
        <summary>Full schedule ({option.schedule.length} payments)</summary>
        <ol className="mini-schedule">
          {option.schedule.map((payment) => (
            <li key={payment.number}>
              <span>{formatDate(payment.dueDate)}</span>
              <span>{formatMoney(payment.amount)}</span>
            </li>
          ))}
        </ol>
      </details>

      <button type="button" className={selected ? 'button' : 'button secondary'} onClick={onSelect}
              aria-pressed={selected}>
        {selected ? 'Selected' : 'Choose this plan'}
      </button>
    </article>
  )
}

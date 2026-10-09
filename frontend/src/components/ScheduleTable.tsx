import { formatDate, formatMoney } from '../format'
import type { Payment } from '../types'
import { StatusBadge } from './StatusBadge'

interface Props {
  payments: Payment[]
  onPay: (payment: Payment) => void
  payingId: number | null
}

export function ScheduleTable({ payments, onPay, payingId }: Props) {
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>#</th>
            <th>Due date</th>
            <th className="num">Amount</th>
            <th>Status</th>
            <th aria-label="Action" />
          </tr>
        </thead>
        <tbody>
          {payments.map((payment) => (
            <tr key={payment.id}>
              <td>{payment.number}</td>
              <td>{formatDate(payment.dueDate)}</td>
              <td className="num">{formatMoney(payment.amount)}</td>
              <td><StatusBadge status={payment.status} /></td>
              <td className="num">
                {payment.status !== 'PAID' && (
                  <button type="button" className="button small" disabled={payingId !== null}
                          onClick={() => onPay(payment)}>
                    {payingId === payment.id ? 'Paying…' : 'Pay'}
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

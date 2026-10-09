// TypeScript shapes of the JSON the backend sends. They mirror the Java records.
// Money arrives as JSON numbers. The frontend only DISPLAYS money; it never does money math.

export type Frequency = 'BIWEEKLY' | 'MONTHLY'
export type PlanStatus = 'ACTIVE' | 'PAID_OFF'
export type PaymentStatus = 'UPCOMING' | 'PAID' | 'LATE'

export interface AuthResponse {
  token: string
  tokenType: string
  expiresInSeconds: number
}

export interface Me {
  id: number
  email: string
  monthlyIncome: number | null
}

export interface ScheduledPayment {
  number: number
  dueDate: string // "2026-10-23"
  amount: number
}

export interface PlanOption {
  numPayments: number
  frequency: Frequency
  apr: number
  paymentAmount: number
  totalInterest: number
  totalCost: number
  schedule: ScheduledPayment[]
}

export interface QuoteResponse {
  amount: number
  options: PlanOption[]
}

export interface Payment {
  id: number
  number: number
  dueDate: string
  amount: number
  status: PaymentStatus
  paidAt: string | null
}

export interface Plan {
  id: number
  itemName: string
  purchaseAmount: number
  createdAt: string
  numPayments: number
  frequency: Frequency
  apr: number
  totalInterest: number
  totalCost: number
  amountPaid: number
  amountRemaining: number
  status: PlanStatus
  payments: Payment[]
}

export interface Dashboard {
  totalOwed: number
  nextPayment: {
    paymentId: number
    planId: number
    itemName: string
    dueDate: string
    amount: number
    status: PaymentStatus
  } | null
  activePlans: number
  paidOffPlans: number
}

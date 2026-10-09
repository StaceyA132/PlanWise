import type { AssistantResponse, AuthResponse, Dashboard, Me, MonthlyInsights, Plan, QuoteResponse } from './types'

const TOKEN_KEY = 'planwise.token'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string | null): void {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  else localStorage.removeItem(TOKEN_KEY)
}

/** An error response from the API. `message` is the backend's ProblemDetail "detail" when there is one. */
export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

// Called when a logged-in request gets 401 (e.g. the token expired). The auth provider sets it.
let onUnauthorized: (() => void) | null = null
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  onUnauthorized = handler
}

/** Every API call goes through here: adds the JSON header and the bearer token, and turns errors into ApiError. */
async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const token = getToken()
  const headers: Record<string, string> = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers['Authorization'] = `Bearer ${token}`

  const response = await fetch(`/api${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })

  if (!response.ok) {
    let message = `Something went wrong (${response.status})`
    try {
      const problem = await response.json()
      if (problem?.detail) message = problem.detail
    } catch {
      // the body wasn't JSON; keep the generic message
    }
    if (response.status === 401 && token) onUnauthorized?.()
    throw new ApiError(response.status, message)
  }
  return response.json() as Promise<T>
}

export const api = {
  register: (email: string, password: string, monthlyIncome: string | null) =>
    request<AuthResponse>('POST', '/auth/register', { email, password, monthlyIncome }),
  login: (email: string, password: string) =>
    request<AuthResponse>('POST', '/auth/login', { email, password }),
  me: () => request<Me>('GET', '/me'),

  // Amounts are sent as strings ("199.99") so the exact digits the user typed reach Java's BigDecimal.
  quote: (amount: string) => request<QuoteResponse>('POST', '/quotes', { amount }),
  createPlan: (itemName: string, amount: string, numPayments: number) =>
    request<Plan>('POST', '/plans', { itemName, amount, numPayments }),
  listPlans: () => request<Plan[]>('GET', '/plans'),
  getPlan: (id: number) => request<Plan>('GET', `/plans/${id}`),
  pay: (paymentId: number) => request<Plan>('POST', `/payments/${paymentId}/pay`),
  dashboard: () => request<Dashboard>('GET', '/dashboard'),
  ask: (itemName: string, amount: string, question: string) =>
    request<AssistantResponse>('POST', '/assistant/ask', { itemName, amount, question }),
  monthlyInsights: (month: string) => request<MonthlyInsights>('GET', `/insights/monthly?month=${encodeURIComponent(month)}`),
}

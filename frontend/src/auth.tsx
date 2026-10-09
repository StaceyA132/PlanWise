import { useCallback, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { api, getToken, setToken, setUnauthorizedHandler } from './api'
import { AuthContext, useAuth } from './authContext'
import type { AuthContextValue } from './authContext'

/** Holds "am I logged in?" for the whole app. The token itself lives in localStorage. */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setTokenState] = useState<string | null>(getToken)

  const saveToken = useCallback((value: string | null) => {
    setToken(value)
    setTokenState(value)
  }, [])

  const logout = useCallback(() => saveToken(null), [saveToken])

  // If any request comes back 401 (expired or invalid token), log out.
  useEffect(() => {
    setUnauthorizedHandler(logout)
    return () => setUnauthorizedHandler(null)
  }, [logout])

  const value = useMemo<AuthContextValue>(() => ({
    isLoggedIn: token !== null,
    login: async (email, password) => saveToken((await api.login(email, password)).token),
    register: async (email, password, income) => saveToken((await api.register(email, password, income)).token),
    logout,
  }), [token, saveToken, logout])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

/** Wraps pages that need a login. Sends logged-out users to /login, remembering where they were going. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { isLoggedIn } = useAuth()
  const location = useLocation()
  if (!isLoggedIn) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  return children
}

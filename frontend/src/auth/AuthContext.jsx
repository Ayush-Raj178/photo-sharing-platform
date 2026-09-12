import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api, configureStaffSession, getApiError } from '../lib/api'

export const AuthContext = createContext(null)
const STAFF_SESSION_KEY = 'photoshare.staffSession'

function readStoredSession() {
  try {
    const stored = window.sessionStorage.getItem(STAFF_SESSION_KEY)
    if (!stored) return null
    const session = JSON.parse(stored)
    if (!session.accessToken || !session.user || !session.expiresAt || session.expiresAt <= Date.now()) {
      window.sessionStorage.removeItem(STAFF_SESSION_KEY)
      return null
    }
    return session
  } catch {
    window.sessionStorage.removeItem(STAFF_SESSION_KEY)
    return null
  }
}

function storeSession(session) {
  if (session) window.sessionStorage.setItem(STAFF_SESSION_KEY, JSON.stringify(session))
  else window.sessionStorage.removeItem(STAFF_SESSION_KEY)
}

export function AuthProvider({ children }) {
  const [session, setSession] = useState(readStoredSession)
  const [restoring, setRestoring] = useState(true)
  const [notice, setNotice] = useState('')

  const logout = useCallback((message = '') => {
    configureStaffSession(null, null)
    storeSession(null)
    setSession(null)
    setNotice(message)
  }, [])

  const clearNotice = useCallback(() => setNotice(''), [])

  useEffect(() => {
    configureStaffSession(session?.accessToken, () => logout('Your session expired. Please sign in again.'))
  }, [session?.accessToken, logout])

  useEffect(() => {
    if (!session?.accessToken) {
      setRestoring(false)
      return undefined
    }
    let active = true
    api.get('/auth/me')
      .then(({ data }) => {
        if (!active) return
        const restored = { ...session, user: data }
        storeSession(restored)
        setSession(restored)
      })
      .catch(() => {
        if (active) logout('Your saved session is no longer valid. Please sign in again.')
      })
      .finally(() => { if (active) setRestoring(false) })
    return () => { active = false }
  }, [])

  const authenticate = useCallback(async (mode, values) => {
    try {
      const { data } = await api.post(`/auth/${mode}`, values)
      const nextSession = { ...data, expiresAt: Date.now() + data.expiresIn * 1000 }
      setNotice('')
      storeSession(nextSession)
      setSession(nextSession)
      return data.user
    } catch (error) {
      throw getApiError(error, mode === 'login' ? 'Unable to sign in.' : 'Unable to create the account.')
    }
  }, [])

  const value = useMemo(() => ({
    user: session?.user || null,
    token: session?.accessToken || null,
    restoring,
    notice,
    clearNotice,
    login: (values) => authenticate('login', values),
    register: (values) => authenticate('register', values),
    logout,
  }), [session, restoring, notice, authenticate, clearNotice, logout])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth must be used within AuthProvider')
  return value
}

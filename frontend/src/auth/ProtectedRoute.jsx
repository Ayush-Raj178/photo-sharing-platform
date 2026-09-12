import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './AuthContext'

export function ProtectedRoute({ role }) {
  const { user } = useAuth()
  const location = useLocation()
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  if (user.role !== role) {
    return <Navigate to={user.role === 'ADMIN' ? '/admin/events' : '/team/events'} replace />
  }
  return <Outlet />
}

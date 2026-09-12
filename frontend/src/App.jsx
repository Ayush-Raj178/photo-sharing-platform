import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth/AuthContext'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { EventsPage } from './pages/EventsPage'
import { AdminEventPage } from './pages/AdminEventPage'
import { TeamEventPage } from './pages/TeamEventPage'
import { PublicGalleryPage } from './pages/PublicGalleryPage'
import { NotFoundPage } from './pages/NotFoundPage'

function HomeRedirect() {
  const { user } = useAuth()
  if (!user) return <Navigate to="/login" replace />
  return <Navigate to={user.role === 'ADMIN' ? '/admin/events' : '/team/events'} replace />
}

export function App() {
  return (
    <Routes>
      <Route path="/" element={<HomeRedirect />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/gallery/:publicId" element={<PublicGalleryPage />} />
      <Route element={<ProtectedRoute role="ADMIN" />}>
        <Route path="/admin/events" element={<EventsPage role="ADMIN" />} />
        <Route path="/admin/events/:eventId" element={<AdminEventPage />} />
      </Route>
      <Route element={<ProtectedRoute role="TEAM_MEMBER" />}>
        <Route path="/team/events" element={<EventsPage role="TEAM_MEMBER" />} />
        <Route path="/team/events/:eventId" element={<TeamEventPage />} />
      </Route>
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}

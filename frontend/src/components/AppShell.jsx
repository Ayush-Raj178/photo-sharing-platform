import { CalendarDays, LogOut, Menu, X } from 'lucide-react'
import { useState } from 'react'
import { NavLink } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { Brand } from './Brand'

export function AppShell({ children, role }) {
  const { user, logout } = useAuth()
  const [open, setOpen] = useState(false)
  const eventsPath = role === 'ADMIN' ? '/admin/events' : '/team/events'

  return (
    <div className="app-frame">
      <header className="mobile-header">
        <Brand compact />
        <button className="icon-button" onClick={() => setOpen((value) => !value)} aria-expanded={open} aria-label="Toggle navigation">
          {open ? <X /> : <Menu />}
        </button>
      </header>
      <aside className={`sidebar ${open ? 'sidebar-open' : ''}`}>
        <Brand />
        <nav aria-label="Staff navigation">
          <NavLink to={eventsPath} onClick={() => setOpen(false)} className={({ isActive }) => isActive ? 'nav-link nav-link-active' : 'nav-link'}>
            <CalendarDays aria-hidden="true" size={20} />
            {role === 'ADMIN' ? 'Events' : 'Assigned events'}
          </NavLink>
        </nav>
        <div className="sidebar-footer">
          <div className="profile-summary">
            <span className="profile-avatar" aria-hidden="true">{user?.displayName?.slice(0, 1).toUpperCase()}</span>
            <span><strong>{user?.displayName}</strong><small>{role === 'ADMIN' ? 'Admin' : 'Team Member'}</small></span>
          </div>
          <button className="nav-link logout-button" onClick={() => logout()}>
            <LogOut aria-hidden="true" size={20} /> Logout
          </button>
        </div>
      </aside>
      {open ? <button className="nav-scrim" aria-label="Close navigation" onClick={() => setOpen(false)} /> : null}
      <main className="app-main">{children}</main>
    </div>
  )
}

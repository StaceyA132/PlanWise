import { NavLink, Outlet } from 'react-router'
import { useAuth } from '../authContext'

/** The frame around every logged-in page: header, nav, and the current page below. */
export function Layout() {
  const { logout } = useAuth()
  return (
    <>
      <header className="site-header">
        <div className="container header-inner">
          <NavLink to="/" className="brand">PlanWise</NavLink>
          <nav className="nav">
            <NavLink to="/" end>Dashboard</NavLink>
            <NavLink to="/purchases/new">New purchase</NavLink>
            <NavLink to="/assistant">Assistant</NavLink>
            <NavLink to="/insights">Insights</NavLink>
            <button type="button" className="link-button" onClick={logout}>Log out</button>
          </nav>
        </div>
      </header>
      <main className="container page">
        <Outlet />
      </main>
    </>
  )
}

import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function Layout({ children }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate('/login');
  }

  return (
    <div>
      <header className="topbar">
        <Link className="brand" to="/">Expense Claims</Link>
        {user && (
          <nav className="nav">
            {(user.role === 'STAFF' || user.role === 'MANAGER') && (
              <>
                <Link to="/claims">My claims</Link>
                <Link to="/claims/new">File a claim</Link>
              </>
            )}
            {user.role === 'MANAGER' && <Link to="/manager/queue">Team approvals</Link>}
            {user.role === 'FINANCE' && (
              <>
                <Link to="/manager/queue">Escalated approvals</Link>
                <Link to="/finance/queue">Pay out</Link>
                <Link to="/finance/report">Monthly report</Link>
              </>
            )}
            <span className="whoami">
              {user.name} &middot; <span className={`role-pill role-${user.role.toLowerCase()}`}>{user.role}</span>
            </span>
            <button className="link-btn" onClick={handleLogout}>Log out</button>
          </nav>
        )}
      </header>
      <main className="wrap">{children}</main>
    </div>
  );
}

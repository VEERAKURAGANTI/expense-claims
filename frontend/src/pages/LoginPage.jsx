import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { authApi, apiErrorMessage } from '../api/endpoints';

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [demoUsers, setDemoUsers] = useState([]);
  const { login } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    authApi.demoUsers().then((res) => setDemoUsers(res.data)).catch(() => { });
  }, []);

  async function handleSubmit(e) {
    e.preventDefault();
    setError('');
    try {
      await login(email, password);
      navigate('/');
    } catch (err) {
      setError(apiErrorMessage(err));
    }
  }

  return (
    <div className="card" style={{ maxWidth: 420, margin: '40px auto 0' }}>
      <h1>Log in</h1>
      <p className="lede">Staff, managers and finance all sign in here.</p>

      {error && <div className="alert error">{error}</div>}

      <form className="stacked" onSubmit={handleSubmit}>
        <label htmlFor="email">Email</label>
        <input id="email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoFocus />
        <label htmlFor="password">Password</label>
        <div className="password-wrapper">
          <input
            id="password"
            type={showPassword ? 'text' : 'password'}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />

          <button
            type="button"
            className="password-toggle"
            onClick={() => setShowPassword((prev) => !prev)}
            aria-label={showPassword ? 'Hide password' : 'Show password'}
          >
            {showPassword ? 'Hide' : 'Show'}
          </button>
        </div>
        <div className="actions-row">
          <button className="btn" type="submit">Log in</button>
        </div>
      </form>

      {demoUsers.length > 0 && (
        <div className="demo-users">
          Demo data &mdash; every account uses the password <code>password123</code>.
          <table>
            <tbody>
              {demoUsers.map((u) => (
                <tr key={u.id}>
                  <td>{u.name}</td>
                  <td>{u.email}</td>
                  <td>{u.role}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

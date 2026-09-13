import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { claimsApi } from '../api/endpoints';

const inr = (n) => `₹${Number(n ?? 0).toLocaleString('en-IN')}`;

export default function StaffDashboard() {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');

  function load() {
    claimsApi.dashboard().then((res) => setData(res.data)).catch(() => setError('Could not load your claims.'));
  }
  useEffect(load, []);

  if (error) return <div className="alert error">{error}</div>;
  if (!data) return <p className="empty">Loading&hellip;</p>;

  const over = Number(data.monthSpend) > Number(data.monthlyLimit);

  return (
    <>
      <h1>My claims</h1>
      <p className="lede">What you've filed, what's still waiting on someone, and what's already landed in your account.</p>

      <div className="grid-stats">
        <div className="stat"><div className="n">{inr(data.unpaidTotal)}</div><div className="l">Unpaid, in flight</div></div>
        <div className={`stat ${over ? 'over' : ''}`}>
          <div className="n">{inr(data.monthSpend)} / {inr(data.monthlyLimit)}</div>
          <div className="l">Spent this month ({data.monthKey})</div>
        </div>
        <div className="stat"><div className="n">{data.drafts.length}</div><div className="l">Drafts to finish</div></div>
      </div>

      {over && (
        <div className="alert warn">
          You're over your monthly limit of {inr(data.monthlyLimit)}. Claims still get filed and reviewed as normal &mdash; finance just sees the flag on their end.
        </div>
      )}

      <div className="actions-row"><Link className="btn" to="/claims/new">+ File a new claim</Link></div>

      {data.drafts.length > 0 && (
        <>
          <h2>Drafts &mdash; not sent to your manager yet</h2>
          <table>
            <thead><tr><th>Merchant</th><th>Amount</th><th>Category</th><th>Date</th><th></th></tr></thead>
            <tbody>
              {data.drafts.map((c) => (
                <tr key={c.id}>
                  <td>{c.merchant || '—'}</td>
                  <td className="amount">{inr(c.amount)}</td>
                  <td>{c.category}</td>
                  <td>{c.expenseDate}</td>
                  <td><Link to={`/claims/${c.id}/edit`}>Review &amp; send →</Link></td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}

      <h2>Waiting on approval or payout</h2>
      {data.inFlight.length === 0 ? (
        <p className="empty">Nothing in flight right now.</p>
      ) : (
        <table>
          <thead><tr><th>Merchant</th><th>Amount</th><th>Category</th><th>Date</th><th>Status</th></tr></thead>
          <tbody>
            {data.inFlight.map((c) => (
              <tr key={c.id}>
                <td><Link to={`/claims/${c.id}`}>{c.merchant || '—'}</Link></td>
                <td className="amount">{inr(c.amount)}</td>
                <td>{c.category}</td>
                <td>{c.expenseDate}</td>
                <td><span className={`badge ${c.status.toLowerCase()}`}>{c.status.replace('_', ' ')}</span></td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <h2>History</h2>
      {data.history.length === 0 ? (
        <p className="empty">Nothing paid or rejected yet.</p>
      ) : (
        <table>
          <thead><tr><th>Merchant</th><th>Amount</th><th>Category</th><th>Date</th><th>Status</th></tr></thead>
          <tbody>
            {data.history.map((c) => (
              <tr key={c.id}>
                <td><Link to={`/claims/${c.id}`}>{c.merchant || '—'}</Link></td>
                <td className="amount">{inr(c.amount)}</td>
                <td>{c.category}</td>
                <td>{c.expenseDate}</td>
                <td><span className={`badge ${c.status.toLowerCase()}`}>{c.status}</span></td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  );
}

import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { managerApi, apiErrorMessage } from '../api/endpoints';

const inr = (n) => `₹${Number(n ?? 0).toLocaleString('en-IN')}`;

export default function ManagerQueuePage() {
  const [pending, setPending] = useState([]);
  const [decided, setDecided] = useState([]);
  const [notes, setNotes] = useState({});
  const [error, setError] = useState('');
  const [busyId, setBusyId] = useState(null);

  function load() {
    managerApi.queue()
      .then((res) => { setPending(res.data.pending); setDecided(res.data.decided); })
      .catch((err) => setError(apiErrorMessage(err)));
  }
  useEffect(load, []);

  async function approve(id) {
    setBusyId(id); setError('');
    try {
      await managerApi.approve(id);
      load();
    } catch (err) {
      setError(apiErrorMessage(err));
    } finally {
      setBusyId(null);
    }
  }

  async function reject(id) {
    setBusyId(id); setError('');
    try {
      await managerApi.reject(id, notes[id] || '');
      load();
    } catch (err) {
      setError(apiErrorMessage(err));
    } finally {
      setBusyId(null);
    }
  }

  return (
    <>
      <h1>Team approvals</h1>
      <p className="lede">Claims from your team (and your own claims, escalated elsewhere) waiting on a decision.</p>

      {error && <div className="alert error">{error}</div>}

      {pending.length === 0 ? (
        <p className="empty">Nothing waiting on you right now.</p>
      ) : (
        pending.map((c) => (
          <div className="card" key={c.id}>
            <div className="actions-row" style={{ justifyContent: 'space-between', marginTop: 0 }}>
              <div>
                <strong>{c.ownerName}</strong> &middot; {c.merchant || 'unlabelled'} &middot;{' '}
                <span className="amount">{inr(c.amount)}</span> &middot; {c.category} &middot; {c.expenseDate}
              </div>
              <Link to={`/claims/${c.id}`}>Full details →</Link>
            </div>
            {c.possibleDuplicateOf && (
              <div className="alert warn">
                Submitter flagged this as a possible duplicate of claim #{c.possibleDuplicateOf} but confirmed it's separate.
              </div>
            )}
            {c.description && <p className="muted">{c.description}</p>}
            <div className="actions-row">
              <button className="btn good" onClick={() => approve(c.id)} disabled={busyId === c.id}>Approve</button>
              <input
                type="text"
                placeholder="Reason (shown to them)"
                value={notes[c.id] || ''}
                onChange={(e) => setNotes({ ...notes, [c.id]: e.target.value })}
                style={{ padding: '6px 9px', border: '1px solid var(--line)', borderRadius: 6, fontSize: '0.85rem' }}
              />
              <button className="btn danger" onClick={() => reject(c.id)} disabled={busyId === c.id}>Reject</button>
            </div>
          </div>
        ))
      )}

      <h2>Recently decided</h2>
      {decided.length === 0 ? (
        <p className="empty">Nothing yet.</p>
      ) : (
        <table>
          <thead><tr><th>Who</th><th>Merchant</th><th>Amount</th><th>Status</th><th>When</th></tr></thead>
          <tbody>
            {decided.map((c) => (
              <tr key={c.id}>
                <td>{c.ownerName}</td>
                <td><Link to={`/claims/${c.id}`}>{c.merchant || '—'}</Link></td>
                <td className="amount">{inr(c.amount)}</td>
                <td><span className={`badge ${c.status.toLowerCase()}`}>{c.status}</span></td>
                <td>{new Date(c.updatedAt).toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  );
}

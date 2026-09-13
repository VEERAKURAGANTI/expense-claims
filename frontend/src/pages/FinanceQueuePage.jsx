import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { financeApi, apiErrorMessage } from '../api/endpoints';

const inr = (n) => `₹${Number(n ?? 0).toLocaleString('en-IN')}`;

export default function FinanceQueuePage() {
  const [rows, setRows] = useState([]);
  const [overrideNotes, setOverrideNotes] = useState({});
  const [error, setError] = useState('');
  const [busyId, setBusyId] = useState(null);

  function load() {
    financeApi.queue().then((res) => setRows(res.data)).catch((err) => setError(apiErrorMessage(err)));
  }
  useEffect(load, []);

  async function pay(id, note) {
    setBusyId(id); setError('');
    try {
      await financeApi.pay(id, note);
      load();
    } catch (err) {
      setError(apiErrorMessage(err));
    } finally {
      setBusyId(null);
    }
  }

  return (
    <>
      <h1>Approved, ready to pay</h1>
      <p className="lede">
        Manager sign-off is done &mdash; these are ready for the payout run. Paying is simulated: no real money
        moves, but once marked paid a claim can't be reopened.
      </p>

      {error && <div className="alert error">{error}</div>}

      {rows.length === 0 ? (
        <p className="empty">Nothing approved and waiting right now.</p>
      ) : (
        rows.map(({ claim: c, duplicateAlreadyPaid }) => (
          <div className="card" key={c.id}>
            <div className="actions-row" style={{ justifyContent: 'space-between', marginTop: 0 }}>
              <div>
                <strong>{c.ownerName}</strong> &middot; {c.merchant || 'unlabelled'} &middot;{' '}
                <span className="amount">{inr(c.amount)}</span> &middot; {c.category} &middot; {c.expenseDate}
              </div>
              <Link to={`/claims/${c.id}`}>Full details →</Link>
            </div>

            {duplicateAlreadyPaid ? (
              <>
                <div className="alert error">
                  Claim #{duplicateAlreadyPaid.id} looks like the same expense and was already paid on{' '}
                  {new Date(duplicateAlreadyPaid.paidAt).toLocaleDateString()}. Add a note to pay this one anyway,
                  or leave it &mdash; it's most likely a duplicate that finance has caught before payout.
                </div>
                <div className="actions-row">
                  <input
                    type="text"
                    placeholder="Why pay it anyway?"
                    value={overrideNotes[c.id] || ''}
                    onChange={(e) => setOverrideNotes({ ...overrideNotes, [c.id]: e.target.value })}
                    style={{ flex: 1, padding: '6px 9px', border: '1px solid var(--line)', borderRadius: 6, fontSize: '0.85rem' }}
                  />
                  <button
                    className="btn danger"
                    disabled={busyId === c.id || !(overrideNotes[c.id] || '').trim()}
                    onClick={() => pay(c.id, overrideNotes[c.id])}
                  >
                    Pay anyway
                  </button>
                </div>
              </>
            ) : (
              <div className="actions-row">
                <button className="btn good" disabled={busyId === c.id} onClick={() => pay(c.id, null)}>Mark paid</button>
              </div>
            )}
          </div>
        ))
      )}
    </>
  );
}

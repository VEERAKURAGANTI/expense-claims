import { useEffect, useState } from 'react';
import { financeApi, apiErrorMessage } from '../api/endpoints';

const inr = (n) => `₹${Number(n ?? 0).toLocaleString('en-IN')}`;

export default function FinanceReportPage() {
  const [report, setReport] = useState(null);
  const [month, setMonth] = useState('');
  const [error, setError] = useState('');

  function load(m) {
    financeApi.report(m).then((res) => {
      setReport(res.data);
      setMonth(res.data.monthKey);
    }).catch((err) => setError(apiErrorMessage(err)));
  }
  useEffect(() => load(), []);

  function handleMonthChange(e) {
    const m = e.target.value;
    setMonth(m);
    load(m);
  }

  if (error) return <div className="alert error">{error}</div>;
  if (!report) return <p className="empty">Loading&hellip;</p>;

  return (
    <>
      <h1>Monthly report &mdash; {report.monthKey}</h1>
      <p className="lede">
        Who spent what, under which category, and who's gone over their limit. Counts submitted, approved and paid
        claims &mdash; rejected and unsent drafts don't count as spend.
      </p>

      <div className="actions-row">
        <label htmlFor="month" className="muted" style={{ alignSelf: 'center' }}>Month:</label>
        <input id="month" type="month" value={month} onChange={handleMonthChange} />
      </div>

      <div className="grid-stats">
        <div className="stat"><div className="n">{inr(report.totalSpend)}</div><div className="l">Total spend</div></div>
        <div className={`stat ${report.overLimit.length ? 'over' : ''}`}>
          <div className="n">{report.overLimit.length}</div><div className="l">People over their limit</div>
        </div>
      </div>

      {report.overLimit.length > 0 && (
        <div className="alert warn">
          Over limit this month:
          <ul style={{ margin: '8px 0 0' }}>
            {report.overLimit.map((u, i) => (
              <li key={i}>{u.name} &mdash; {inr(u.spend)} of a {inr(u.monthlyLimit)} limit</li>
            ))}
          </ul>
        </div>
      )}

      <h2>By category</h2>
      {report.byCategory.length === 0 ? (
        <p className="empty">No spend recorded for this month.</p>
      ) : (
        <table>
          <thead><tr><th>Category</th><th>Claims</th><th>Total</th></tr></thead>
          <tbody>
            {report.byCategory.map((r, i) => (
              <tr key={i}><td>{r.category}</td><td>{r.count}</td><td className="amount">{inr(r.total)}</td></tr>
            ))}
          </tbody>
        </table>
      )}

      <h2>By person, by category</h2>
      {report.byUserCategory.length === 0 ? (
        <p className="empty">No spend recorded for this month.</p>
      ) : (
        <table>
          <thead><tr><th>Person</th><th>Category</th><th>Total</th></tr></thead>
          <tbody>
            {report.byUserCategory.map((r, i) => (
              <tr key={i}><td>{r.userName}</td><td>{r.category}</td><td className="amount">{inr(r.total)}</td></tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  );
}

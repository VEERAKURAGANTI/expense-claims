import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { claimsApi, apiErrorMessage } from '../api/endpoints';
import ReceiptLink from '../components/ReceiptLink';

const inr = (n) => `₹${Number(n ?? 0).toLocaleString('en-IN')}`;

export default function ClaimDetailPage() {
  const { id } = useParams();
  const [claim, setClaim] = useState(null);
  const [dup, setDup] = useState(null);
  const [audit, setAudit] = useState([]);
  const [error, setError] = useState('');

  useEffect(() => {
    claimsApi.detail(id).then((res) => {
      setClaim(res.data.claim);
      setDup(res.data.duplicate);
      setAudit(res.data.audit);
    }).catch((err) => setError(apiErrorMessage(err)));
  }, [id]);

  if (error) return <div className="alert error">{error}</div>;
  if (!claim) return <p className="empty">Loading&hellip;</p>;

  return (
    <>
      <h1>Claim #{claim.id} <span className={`badge ${claim.status.toLowerCase()}`}>{claim.status.replace('_', ' ')}</span></h1>
      <p className="lede">Filed by {claim.ownerName}</p>

      <div className="card">
        <table>
          <tbody>
            <tr><th>Merchant</th><td>{claim.merchant || '—'}</td></tr>
            <tr><th>Amount</th><td className="amount">{inr(claim.amount)}</td></tr>
            <tr><th>Category</th><td>{claim.category}</td></tr>
            <tr><th>Date</th><td>{claim.expenseDate}</td></tr>
            <tr><th>Note</th><td>{claim.description || '—'}</td></tr>
            {claim.hasAttachment && (
              <tr><th>Photo</th><td><ReceiptLink claimId={id}>View attached receipt</ReceiptLink></td></tr>
            )}
          </tbody>
        </table>
        {claim.rawText && (
          <>
            <label style={{ display: 'block', marginTop: 10, fontWeight: 600, fontSize: '0.85rem' }}>Original text</label>
            <div className="receipt-raw">{claim.rawText}</div>
          </>
        )}
      </div>

      {dup && (
        <div className="alert warn">
          Flagged as a possible duplicate of <Link to={`/claims/${dup.claimId}`}>claim #{dup.claimId}</Link> when it was submitted.
        </div>
      )}

      <h2>History</h2>
      <table>
        <thead><tr><th>When</th><th>Who</th><th>What</th><th>Note</th></tr></thead>
        <tbody>
          {audit.map((a, i) => (
            <tr key={i}>
              <td>{new Date(a.createdAt).toLocaleString()}</td>
              <td>{a.actorName || 'System'}</td>
              <td>{a.action.replace('_', ' ')}</td>
              <td>{a.note || '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}

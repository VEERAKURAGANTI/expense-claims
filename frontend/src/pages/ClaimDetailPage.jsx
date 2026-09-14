import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { claimsApi, apiErrorMessage } from '../api/endpoints';

const inr = (n) => `₹${Number(n ?? 0).toLocaleString('en-IN')}`;

export default function ClaimDetailPage() {
  const { id } = useParams();

  const [claim, setClaim] = useState(null);
  const [dup, setDup] = useState(null);
  const [audit, setAudit] = useState([]);
  const [error, setError] = useState('');

  const [attachmentUrl, setAttachmentUrl] = useState('');
  const [loadingAttachment, setLoadingAttachment] = useState(false);

  useEffect(() => {
    claimsApi.detail(id)
      .then((res) => {
        setClaim(res.data.claim);
        setDup(res.data.duplicate);
        setAudit(res.data.audit);
      })
      .catch((err) => setError(apiErrorMessage(err)));
  }, [id]);

  // Clean up the temporary blob URL when leaving the page
  useEffect(() => {
    return () => {
      if (attachmentUrl) {
        URL.revokeObjectURL(attachmentUrl);
      }
    };
  }, [attachmentUrl]);

  const handleViewAttachment = async () => {
    try {
      setError('');
      setLoadingAttachment(true);

      const response = await claimsApi.attachment(id);

      const url = URL.createObjectURL(response.data);

      setAttachmentUrl(url);
    } catch (err) {
      setError(apiErrorMessage(err));
    } finally {
      setLoadingAttachment(false);
    }
  };

  if (error && !claim) {
    return <div className="alert error">{error}</div>;
  }

  if (!claim) {
    return <p className="empty">Loading&hellip;</p>;
  }

  return (
    <>
      <h1>
        Claim #{claim.id}{' '}
        <span className={`badge ${claim.status.toLowerCase()}`}>
          {claim.status.replace('_', ' ')}
        </span>
      </h1>

      <p className="lede">Filed by {claim.ownerName}</p>

      {error && (
        <div className="alert error">
          {error}
        </div>
      )}

      <div className="card">
        <table>
          <tbody>
            <tr>
              <th>Merchant</th>
              <td>{claim.merchant || '—'}</td>
            </tr>

            <tr>
              <th>Amount</th>
              <td className="amount">{inr(claim.amount)}</td>
            </tr>

            <tr>
              <th>Category</th>
              <td>{claim.category}</td>
            </tr>

            <tr>
              <th>Date</th>
              <td>{claim.expenseDate}</td>
            </tr>

            <tr>
              <th>Note</th>
              <td>{claim.description || '—'}</td>
            </tr>

            {claim.hasAttachment && (
              <tr>
                <th>Photo</th>
                <td>
                  <button
                    type="button"
                    className="btn secondary"
                    onClick={handleViewAttachment}
                    disabled={loadingAttachment}
                  >
                    {loadingAttachment
                      ? 'Loading receipt...'
                      : 'View attached receipt'}
                  </button>

                  {attachmentUrl && (
                    <div style={{ marginTop: 15 }}>
                      <img
                        src={attachmentUrl}
                        alt={`Receipt for claim #${claim.id}`}
                        style={{
                          maxWidth: '100%',
                          maxHeight: '600px',
                          objectFit: 'contain',
                          border: '1px solid #ddd',
                          borderRadius: '8px',
                          display: 'block',
                        }}
                      />
                    </div>
                  )}
                </td>
              </tr>
            )}
          </tbody>
        </table>

        {claim.rawText && (
          <>
            <label
              style={{
                display: 'block',
                marginTop: 10,
                fontWeight: 600,
                fontSize: '0.85rem',
              }}
            >
              Original text
            </label>

            <div className="receipt-raw">
              {claim.rawText}
            </div>
          </>
        )}
      </div>

      {dup && (
        <div className="alert warn">
          Flagged as a possible duplicate of{' '}
          <Link to={`/claims/${dup.claimId}`}>
            claim #{dup.claimId}
          </Link>{' '}
          when it was submitted.
        </div>
      )}

      <h2>History</h2>

      <table>
        <thead>
          <tr>
            <th>When</th>
            <th>Who</th>
            <th>What</th>
            <th>Note</th>
          </tr>
        </thead>

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
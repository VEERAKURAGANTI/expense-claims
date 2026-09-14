import { useState } from 'react';
import { claimsApi, apiErrorMessage } from '../api/endpoints';

/**
 * The attachment endpoint requires a JWT, so it can't be opened with a plain
 * <a href> (a browser navigation never sends the Authorization header - only
 * the axios interceptor does that). Instead we fetch it as an authenticated
 * blob and open that.
 */
export default function ReceiptLink({ claimId, children }) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  async function handleClick(e) {
    e.preventDefault();
    if (loading) return;
    setLoading(true);
    setError('');
    try {
      const res = await claimsApi.attachment(claimId);
      const url = window.URL.createObjectURL(res.data);
      window.open(url, '_blank', 'noopener,noreferrer');
      // Give the new tab a moment to load the blob before releasing it.
      setTimeout(() => window.URL.revokeObjectURL(url), 60_000);
    } catch (err) {
      setError(apiErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }

  return (
    <>
      <a href="#" onClick={handleClick} aria-disabled={loading}>
        {loading ? 'Opening…' : children}
      </a>
      {error && <span className="alert error" style={{ marginLeft: 8 }}>{error}</span>}
    </>
  );
}

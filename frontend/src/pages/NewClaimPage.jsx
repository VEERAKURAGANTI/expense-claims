import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { claimsApi, apiErrorMessage } from '../api/endpoints';

export default function NewClaimPage() {
  const [rawText, setRawText] = useState('');
  const [file, setFile] = useState(null);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const navigate = useNavigate();

  async function handleSubmit(e) {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      const formData = new FormData();
      if (rawText.trim()) formData.append('rawText', rawText);
      if (file) formData.append('receiptPhoto', file);
      const { data } = await claimsApi.createDraft(formData);
      navigate(`/claims/${data.id}/edit`);
    } catch (err) {
      setError(apiErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      <h1>File a claim</h1>
      <p className="lede">
        Paste whatever's on the receipt &mdash; an SMS, an email confirmation, a screenshot's text, however it's
        written &mdash; or attach a photo. We'll turn it into a claim and show it back to you before it goes anywhere.
      </p>

      {error && <div className="alert error">{error}</div>}

      <div className="card">
        <form className="stacked" onSubmit={handleSubmit}>
          <label htmlFor="rawText">Paste the receipt text</label>
          <textarea
            id="rawText"
            value={rawText}
            onChange={(e) => setRawText(e.target.value)}
            placeholder={'e.g.\nOla Auto\nTrip fare Rs 180\n14/08/2026 09:12 AM'}
          />
          <div className="hint">Doesn't need to be tidy &mdash; typos, line breaks, extra junk are fine.</div>

          <label htmlFor="receiptPhoto">Or attach a photo / screenshot</label>
          <input id="receiptPhoto" type="file" accept="image/*" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
          <div className="hint">
            If you attach a photo without pasting the text, you'll fill in the details by hand on the next screen.
          </div>

          <div className="actions-row">
            <button className="btn" type="submit" disabled={submitting || (!rawText.trim() && !file)}>
              {submitting ? 'Working…' : 'Turn this into a claim →'}
            </button>
          </div>
        </form>
      </div>
    </>
  );
}

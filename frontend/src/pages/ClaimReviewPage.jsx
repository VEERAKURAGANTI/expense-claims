import { useEffect, useState } from 'react';
import { useNavigate, useParams, Link } from 'react-router-dom';
import { claimsApi, apiErrorMessage, CATEGORIES } from '../api/endpoints';

const inr = (n) => `₹${Number(n ?? 0).toLocaleString('en-IN')}`;

export default function ClaimReviewPage() {
  const { id } = useParams();
  const navigate = useNavigate();

  const [claim, setClaim] = useState(null);
  const [form, setForm] = useState(null);
  const [dup, setDup] = useState(null);
  const [confirmNotDup, setConfirmNotDup] = useState(false);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  function load() {
    claimsApi.detail(id)
      .then((res) => {
        setClaim(res.data.claim);
        setDup(res.data.duplicate);

        setForm({
          merchant: res.data.claim.merchant || '',
          amount: res.data.claim.amount ?? '',
          category: res.data.claim.category || 'OTHER',
          expenseDate: res.data.claim.expenseDate || '',
          description: res.data.claim.description || '',
        });
      })
      .catch((err) => setError(apiErrorMessage(err)));
  }

  useEffect(load, [id]);

  async function handleSave(e) {
    e.preventDefault();
    setError('');

    if (Number(form.amount) <= 0) {
      setError('Amount must be greater than zero.');
      return;
    }

    setSaving(true);

    try {
      await claimsApi.edit(id, {
        merchant: form.merchant,
        amount: Number(form.amount),
        category: form.category,
        expenseDate: form.expenseDate,
        description: form.description,
      });

      load();
    } catch (err) {
      setError(apiErrorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  async function handleSubmit() {
    setError('');

    // Validate the manually entered amount before submitting
    if (Number(form.amount) <= 0) {
      setError('Amount must be greater than zero.');
      return;
    }

    setSaving(true);

    try {
      // IMPORTANT:
      // Save the current form values first.
      // This is required when the receipt was uploaded without text
      // and the user manually enters the claim details.
      await claimsApi.edit(id, {
        merchant: form.merchant,
        amount: Number(form.amount),
        category: form.category,
        expenseDate: form.expenseDate,
        description: form.description,
      });

      // Now submit the updated claim
      const { data } = await claimsApi.submit(id, confirmNotDup);

      if (data.duplicate) {
        setDup(data.duplicate);
        setError(
          'Tick the box to confirm this is a separate expense before sending it.'
        );
      } else {
        navigate('/claims');
      }
    } catch (err) {
      setError(apiErrorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  async function handleDiscard() {
    if (!window.confirm('Discard this draft?')) return;

    try {
      await claimsApi.discard(id);
      navigate('/claims');
    } catch (err) {
      setError(apiErrorMessage(err));
    }
  }

  if (error && !claim) {
    return <div className="alert error">{error}</div>;
  }

  if (!claim || !form) {
    return <p className="empty">Loading&hellip;</p>;
  }

  return (
    <>
      <h1>Here's what we read off that</h1>

      <p className="lede">
        Check it over &mdash; fix anything that's wrong, then send it to your
        manager.
      </p>

      {error && <div className="alert error">{error}</div>}

      {dup && (
        <div className="alert warn">
          This looks a lot like claim #{dup.claimId} &mdash;{' '}
          {inr(dup.amount)} at {dup.merchant || 'the same place'} on{' '}
          {dup.expenseDate} ({dup.daysApart} day
          {dup.daysApart === 1 ? '' : 's'} apart, filed as{' '}
          {dup.status.toLowerCase().replace('_', ' ')}). If this is genuinely
          a separate expense, tick the box below before sending it.{' '}
          <Link to={`/claims/${dup.claimId}`}>View that claim →</Link>
        </div>
      )}

      <div className="card">
        <form className="stacked" onSubmit={handleSave}>
          <label htmlFor="amount">Amount (₹)</label>

          <input
            id="amount"
            type="number"
            step="0.01"
            min="0.01"
            value={form.amount}
            onChange={(e) =>
              setForm({
                ...form,
                amount: e.target.value,
              })
            }
            required
          />

          <label htmlFor="category">Category</label>

          <select
            id="category"
            value={form.category}
            onChange={(e) =>
              setForm({
                ...form,
                category: e.target.value,
              })
            }
          >
            {CATEGORIES.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>

          <label htmlFor="merchant">Merchant / vendor</label>

          <input
            id="merchant"
            type="text"
            value={form.merchant}
            onChange={(e) =>
              setForm({
                ...form,
                merchant: e.target.value,
              })
            }
          />

          <label htmlFor="expenseDate">Date on the receipt</label>

          <input
            id="expenseDate"
            type="date"
            value={form.expenseDate}
            onChange={(e) =>
              setForm({
                ...form,
                expenseDate: e.target.value,
              })
            }
            required
          />

          <label htmlFor="description">Note (optional)</label>

          <input
            id="description"
            type="text"
            value={form.description}
            onChange={(e) =>
              setForm({
                ...form,
                description: e.target.value,
              })
            }
          />

          <div className="actions-row">
            <button
              className="btn secondary"
              type="submit"
              disabled={saving}
            >
              {saving ? 'Saving…' : 'Save changes'}
            </button>
          </div>
        </form>

        {claim.rawText && (
          <>
            <label
              style={{
                display: 'block',
                marginTop: 18,
                fontWeight: 600,
                fontSize: '0.88rem',
              }}
            >
              Original text
            </label>

            <div className="receipt-raw">{claim.rawText}</div>
          </>
        )}

        {claim.hasAttachment && (
          <p className="hint" style={{ marginTop: 10 }}>
            <a
              href={claimsApi.attachmentUrl(id)}
              target="_blank"
              rel="noreferrer"
            >
              View attached photo
            </a>
          </p>
        )}
      </div>

      <div className="card">
        {dup && (
          <label className="check-row">
            <input
              type="checkbox"
              checked={confirmNotDup}
              onChange={(e) => setConfirmNotDup(e.target.checked)}
            />

            This is a separate expense from claim #{dup.claimId}, not the same
            receipt filed again.
          </label>
        )}

        <div className="actions-row">
          <button
            className="btn good"
            onClick={handleSubmit}
            disabled={saving || Number(form.amount) <= 0}
          >
            {saving ? 'Sending…' : 'Send to my manager'}
          </button>

          <button
            className="btn secondary"
            onClick={handleDiscard}
            disabled={saving}
          >
            Discard draft
          </button>
        </div>
      </div>
    </>
  );
}
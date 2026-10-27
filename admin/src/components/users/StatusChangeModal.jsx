import React, { useState } from 'react';
import { adminService } from '../../services/adminService';
import { X, AlertTriangle, ShieldAlert, CheckCircle, Loader2 } from 'lucide-react';

export const StatusChangeModal = ({ user, onClose, onSuccess }) => {
  const [targetStatus, setTargetStatus] = useState(
    user.status === 'ACTIVE' ? 'DEACTIVATED' : 'ACTIVE'
  );
  const [reason, setReason] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!reason.trim()) {
      setError('A documented compliance reason is mandatory for all status transitions.');
      return;
    }

    setError(null);
    setLoading(true);

    try {
      await adminService.updateUserStatus(user.userId, {
        status: targetStatus,
        reason: reason.trim(),
      });
      onSuccess();
    } catch (err) {
      setError(err.message || 'Failed to update account status.');
    } finally {
      setLoading(false);
    }
  };

  const isDeactivating = targetStatus === 'DEACTIVATED';
  const isSuspending = targetStatus === 'SUSPENDED';

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <div
              style={{
                width: '32px',
                height: '32px',
                borderRadius: 'var(--radius-sm)',
                background: isDeactivating ? 'rgba(244, 63, 94, 0.15)' : isSuspending ? 'rgba(245, 158, 11, 0.15)' : 'rgba(16, 185, 129, 0.15)',
                display: 'grid',
                placeItems: 'center',
                color: isDeactivating ? '#FB7185' : isSuspending ? '#FBBF24' : '#34D399',
              }}
            >
              <AlertTriangle size={18} />
            </div>
            <div>
              <h2 style={{ fontSize: '16px', fontWeight: '800', color: '#FFFFFF' }}>
                Account Lifecycle &amp; Status Management
              </h2>
              <p style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                Target: {user.fullName} ({user.userId})
              </p>
            </div>
          </div>
          <button className="btn-icon" onClick={onClose}>
            <X size={18} />
          </button>
        </div>

        {error && (
          <div style={{ margin: '16px 24px 0', padding: '10px 14px', background: 'rgba(244, 63, 94, 0.15)', border: '1px solid rgba(244, 63, 94, 0.3)', borderRadius: 'var(--radius-md)', color: '#FB7185', fontSize: '12px', display: 'flex', alignItems: 'center', gap: '8px' }}>
            <ShieldAlert size={16} style={{ flexShrink: 0 }} />
            <span>{error}</span>
          </div>
        )}

        <form onSubmit={handleSubmit}>
          <div className="modal-body" style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            {/* Consequence Notification Box */}
            <div
              style={{
                padding: '14px',
                borderRadius: 'var(--radius-md)',
                background: isDeactivating
                  ? 'rgba(244, 63, 94, 0.1)'
                  : isSuspending
                  ? 'rgba(245, 158, 11, 0.1)'
                  : 'rgba(16, 185, 129, 0.1)',
                border: `1px solid ${
                  isDeactivating
                    ? 'rgba(244, 63, 94, 0.25)'
                    : isSuspending
                    ? 'rgba(245, 158, 11, 0.25)'
                    : 'rgba(16, 185, 129, 0.25)'
                }`,
              }}
            >
              <div style={{ fontSize: '12px', fontWeight: '700', color: isDeactivating ? '#FB7185' : isSuspending ? '#FBBF24' : '#34D399', marginBottom: '4px' }}>
                {isDeactivating
                  ? 'Consequences of Deactivation (Soft Deletion)'
                  : isSuspending
                  ? 'Consequences of Temporary Suspension'
                  : 'Consequences of Account Reactivation'}
              </div>
              <ul style={{ fontSize: '12px', color: 'var(--text-main)', paddingLeft: '16px', lineHeight: 1.5 }}>
                {isDeactivating && (
                  <>
                    <li><strong>Immediate Token Revocation:</strong> All active bearer sessions are immediately invalidated.</li>
                    <li><strong>Access Blocked:</strong> All subsequent API requests will receive 403 Forbidden.</li>
                    <li><strong>Audit Integrity:</strong> Historical approval decisions, scrutiny reports, and visit records are permanently preserved.</li>
                  </>
                )}
                {isSuspending && (
                  <>
                    <li><strong>Temporary Lock:</strong> User cannot sign in until reactivated by an authorized administrator.</li>
                    <li><strong>Session Termination:</strong> Existing bearer tokens are invalidated in active memory.</li>
                  </>
                )}
                {targetStatus === 'ACTIVE' && (
                  <>
                    <li><strong>Access Restored:</strong> User will be permitted to authenticate and access assigned workspaces.</li>
                    <li><strong>Audit Event:</strong> Account reactivation will be recorded in the persistent compliance log.</li>
                  </>
                )}
              </ul>
            </div>

            {/* Target Status Select */}
            <div>
              <label style={{ display: 'block', fontSize: '11px', fontWeight: '700', color: 'var(--text-muted)', marginBottom: '6px', textTransform: 'uppercase' }}>
                Select Target Status
              </label>
              <select
                value={targetStatus}
                onChange={(e) => setTargetStatus(e.target.value)}
                className="select-field"
              >
                <option value="ACTIVE">ACTIVE (Authorized Full Access)</option>
                <option value="SUSPENDED">SUSPENDED (Temporary Operational Hold)</option>
                <option value="DEACTIVATED">DEACTIVATED (Soft Deletion / Deprovisioned)</option>
              </select>
            </div>

            {/* Compliance Reason Field */}
            <div>
              <label style={{ display: 'block', fontSize: '11px', fontWeight: '700', color: 'var(--text-muted)', marginBottom: '6px', textTransform: 'uppercase' }}>
                Regulatory / Compliance Justification *
              </label>
              <textarea
                rows={3}
                required
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="Document the official justification for this status modification (e.g. Committee inquiry, reassignment, completion of term)..."
                className="input-field"
                style={{ resize: 'vertical' }}
              />
              <span style={{ fontSize: '10px', color: 'var(--text-dim)', marginTop: '4px', display: 'block' }}>
                This statement is written to the immutable audit trail and cannot be altered or deleted.
              </span>
            </div>
          </div>

          <div className="modal-footer">
            <button type="button" className="btn btn-secondary" onClick={onClose} disabled={loading}>
              Cancel
            </button>
            <button
              type="submit"
              className={isDeactivating ? 'btn btn-danger' : isSuspending ? 'btn btn-secondary' : 'btn btn-primary'}
              disabled={loading}
            >
              {loading ? (
                <Loader2 size={16} className="animate-spin" />
              ) : isDeactivating ? (
                <ShieldAlert size={16} />
              ) : (
                <CheckCircle size={16} />
              )}
              <span>Confirm Status Transition</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

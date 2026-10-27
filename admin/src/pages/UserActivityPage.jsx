import React, { useEffect, useState } from 'react';
import { adminService } from '../services/adminService';
import { Activity, Clock, ShieldCheck, UserCheck, AlertCircle, RefreshCw, Key } from 'lucide-react';

export const UserActivityPage = () => {
  const [activity, setActivity] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchActivity = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await adminService.getActivity();
      setActivity(data);
    } catch (err) {
      setError(err.message || 'Failed to load user activity data.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchActivity();
  }, []);

  const recentLogins = activity?.recentLogins || [];
  const recentAudits = activity?.recentAuditEvents || [];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      {/* Title */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <div style={{ fontSize: '11px', fontWeight: '800', letterSpacing: '0.08em', color: 'var(--accent-cyan)', textTransform: 'uppercase' }}>
            Telemetry &amp; Audit
          </div>
          <h1 style={{ fontSize: '26px', fontWeight: '800', color: '#FFFFFF', letterSpacing: '-0.02em', marginTop: '2px' }}>
            User Activity &amp; Authentication Log
          </h1>
          <p style={{ fontSize: '13px', color: 'var(--text-muted)', marginTop: '4px' }}>
            Real-time tracking of user authentications, login timestamps, and sensitive administrative operations.
          </p>
        </div>

        <button onClick={fetchActivity} className="btn btn-secondary">
          <RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
          <span>Refresh</span>
        </button>
      </div>

      {error && (
        <div style={{ padding: '12px 16px', background: 'rgba(244, 63, 94, 0.15)', border: '1px solid rgba(244, 63, 94, 0.3)', borderRadius: 'var(--radius-md)', color: '#FB7185', fontSize: '13px', display: 'flex', alignItems: 'center', gap: '8px' }}>
          <AlertCircle size={16} />
          <span>{error}</span>
        </div>
      )}

      {/* Two Grid Layout */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px' }}>
        {/* Recent Authentications Card */}
        <div className="glass-card">
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '16px' }}>
            <div>
              <h2 style={{ fontSize: '16px', fontWeight: '800', color: '#FFFFFF' }}>Recent Authentications</h2>
              <p style={{ fontSize: '12px', color: 'var(--text-muted)' }}>Identities with confirmed login timestamps</p>
            </div>
            <Key size={18} color="var(--accent-cyan)" />
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {recentLogins.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '32px', color: 'var(--text-dim)', fontSize: '13px' }}>
                No authentication sessions recorded yet.
              </div>
            ) : (
              recentLogins.map((u) => (
                <div
                  key={u.id}
                  style={{
                    padding: '12px 16px',
                    borderRadius: 'var(--radius-md)',
                    background: 'rgba(15, 23, 42, 0.5)',
                    border: '1px solid var(--border-subtle)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                  }}
                >
                  <div>
                    <div style={{ fontWeight: '700', fontSize: '13px', color: '#FFFFFF' }}>{u.fullName}</div>
                    <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                      {u.userId} · {u.role}
                    </div>
                  </div>

                  <div style={{ textAlign: 'right' }}>
                    <div style={{ fontSize: '12px', fontWeight: '600', color: 'var(--accent-cyan)' }}>
                      {u.lastLoginAt ? new Date(u.lastLoginAt).toLocaleTimeString() : 'N/A'}
                    </div>
                    <div style={{ fontSize: '10px', color: 'var(--text-dim)' }}>
                      {u.lastLoginAt ? new Date(u.lastLoginAt).toLocaleDateString() : ''}
                    </div>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        {/* Recent Admin Actions on Users */}
        <div className="glass-card">
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '16px' }}>
            <div>
              <h2 style={{ fontSize: '16px', fontWeight: '800', color: '#FFFFFF' }}>Administrative Modifications</h2>
              <p style={{ fontSize: '12px', color: 'var(--text-muted)' }}>Audited operations affecting user records</p>
            </div>
            <ShieldCheck size={18} color="#34D399" />
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {recentAudits.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '32px', color: 'var(--text-dim)', fontSize: '13px' }}>
                No recent administrative actions recorded.
              </div>
            ) : (
              recentAudits.slice(0, 7).map((audit) => (
                <div
                  key={audit.id}
                  style={{
                    padding: '12px 16px',
                    borderRadius: 'var(--radius-md)',
                    background: 'rgba(15, 23, 42, 0.5)',
                    border: '1px solid var(--border-subtle)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                  }}
                >
                  <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <span className="badge badge-admin" style={{ fontSize: '10px' }}>{audit.eventType}</span>
                      <span style={{ fontSize: '12px', fontWeight: '700', color: '#FFFFFF' }}>
                        {audit.targetEntityType} #{audit.targetEntityId}
                      </span>
                    </div>
                    <div style={{ fontSize: '11px', color: 'var(--text-dim)', marginTop: '4px' }}>
                      Actor: {audit.actorId} · ID #{audit.id}
                    </div>
                  </div>

                  <div style={{ textAlign: 'right' }}>
                    <div style={{ fontSize: '11px', color: '#94A3B8' }}>
                      {new Date(audit.createdAt).toLocaleDateString()}
                    </div>
                    <div style={{ fontSize: '10px', color: 'var(--text-dim)' }}>
                      {new Date(audit.createdAt).toLocaleTimeString()}
                    </div>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

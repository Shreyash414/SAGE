import React, { useEffect, useState } from 'react';
import { adminService } from '../../services/adminService';
import { X, UserCheck, Shield, Calendar, Building, Mail, Clock, Loader2, AlertCircle } from 'lucide-react';

export const UserDetailModal = ({ userId, onClose }) => {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let isMounted = true;
    const fetchDetail = async () => {
      try {
        const res = await adminService.getUserDetail(userId);
        if (isMounted) setData(res);
      } catch (err) {
        if (isMounted) setError(err.message || 'Failed to fetch user details.');
      } finally {
        if (isMounted) setLoading(false);
      }
    };
    fetchDetail();
    return () => { isMounted = false; };
  }, [userId]);

  const user = data?.user;
  const recentAudits = data?.recentAuditEvents || [];

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content" style={{ maxWidth: '680px' }} onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <div
              style={{
                width: '36px',
                height: '36px',
                borderRadius: 'var(--radius-sm)',
                background: 'rgba(56, 189, 248, 0.15)',
                display: 'grid',
                placeItems: 'center',
                color: '#38BDF8',
              }}
            >
              <UserCheck size={20} />
            </div>
            <div>
              <h2 style={{ fontSize: '16px', fontWeight: '800', color: '#FFFFFF' }}>User Directory Profile</h2>
              <p style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Identifier: {userId}</p>
            </div>
          </div>
          <button className="btn-icon" onClick={onClose}>
            <X size={18} />
          </button>
        </div>

        <div className="modal-body">
          {loading ? (
            <div style={{ textAlign: 'center', padding: '40px 0' }}>
              <Loader2 size={32} className="animate-spin" color="var(--accent-cyan)" />
              <p style={{ fontSize: '13px', color: 'var(--text-muted)', marginTop: '12px' }}>Loading persistent profile...</p>
            </div>
          ) : error ? (
            <div style={{ padding: '20px', background: 'rgba(244, 63, 94, 0.15)', borderRadius: 'var(--radius-md)', color: '#FB7185', display: 'flex', alignItems: 'center', gap: '10px' }}>
              <AlertCircle size={20} />
              <span>{error}</span>
            </div>
          ) : user ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
              {/* Profile Card Header */}
              <div
                style={{
                  padding: '18px 20px',
                  background: 'rgba(15, 23, 42, 0.6)',
                  border: '1px solid var(--border-subtle)',
                  borderRadius: 'var(--radius-md)',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                }}
              >
                <div>
                  <h3 style={{ fontSize: '18px', fontWeight: '800', color: '#FFFFFF' }}>{user.fullName}</h3>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '4px' }}>
                    <Mail size={13} color="var(--text-muted)" />
                    <span style={{ fontSize: '13px', color: 'var(--text-muted)' }}>{user.email}</span>
                  </div>
                </div>

                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: '6px' }}>
                  <span className={`badge ${
                    user.status === 'ACTIVE'
                      ? 'badge-active'
                      : user.status === 'SUSPENDED'
                      ? 'badge-suspended'
                      : 'badge-deactivated'
                  }`}>
                    {user.status}
                  </span>
                  <span className="badge badge-role">{user.role}</span>
                </div>
              </div>

              {/* Attributes Grid */}
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                <div style={{ padding: '12px', background: 'rgba(0, 0, 0, 0.2)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)' }}>
                  <span style={{ fontSize: '10px', color: 'var(--text-dim)', textTransform: 'uppercase', fontWeight: '700' }}>Designation</span>
                  <p style={{ fontSize: '13px', fontWeight: '600', color: 'var(--text-main)', marginTop: '2px' }}>
                    {user.designation || 'Not specified'}
                  </p>
                </div>

                <div style={{ padding: '12px', background: 'rgba(0, 0, 0, 0.2)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)' }}>
                  <span style={{ fontSize: '10px', color: 'var(--text-dim)', textTransform: 'uppercase', fontWeight: '700' }}>Assigned Region</span>
                  <p style={{ fontSize: '13px', fontWeight: '600', color: 'var(--text-main)', marginTop: '2px' }}>
                    {user.region || 'Platform-wide'}
                  </p>
                </div>

                <div style={{ padding: '12px', background: 'rgba(0, 0, 0, 0.2)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)' }}>
                  <span style={{ fontSize: '10px', color: 'var(--text-dim)', textTransform: 'uppercase', fontWeight: '700' }}>Affiliated Institution</span>
                  <p style={{ fontSize: '13px', fontWeight: '600', color: 'var(--text-main)', marginTop: '2px' }}>
                    {user.institutionName ? `${user.institutionName} (${user.institutionId || ''})` : 'Regulatory Staff'}
                  </p>
                </div>

                <div style={{ padding: '12px', background: 'rgba(0, 0, 0, 0.2)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)' }}>
                  <span style={{ fontSize: '10px', color: 'var(--text-dim)', textTransform: 'uppercase', fontWeight: '700' }}>Last Authentication</span>
                  <p style={{ fontSize: '13px', fontWeight: '600', color: 'var(--text-main)', marginTop: '2px' }}>
                    {user.lastLoginAt ? new Date(user.lastLoginAt).toLocaleString() : 'No recorded login'}
                  </p>
                </div>
              </div>

              {/* Recent Audit Events for this User */}
              <div>
                <h4 style={{ fontSize: '12px', fontWeight: '800', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: '8px' }}>
                  Target Compliance Audit Trail ({recentAudits.length})
                </h4>

                {recentAudits.length === 0 ? (
                  <div style={{ padding: '16px', background: 'rgba(0, 0, 0, 0.2)', borderRadius: 'var(--radius-sm)', fontSize: '12px', color: 'var(--text-dim)', textAlign: 'center' }}>
                    No audit records recorded for this target identity.
                  </div>
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', maxHeight: '200px', overflowY: 'auto' }}>
                    {recentAudits.map((audit) => (
                      <div
                        key={audit.id}
                        style={{
                          padding: '10px 14px',
                          background: 'rgba(15, 23, 42, 0.4)',
                          border: '1px solid var(--border-subtle)',
                          borderRadius: 'var(--radius-sm)',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'space-between',
                          fontSize: '12px',
                        }}
                      >
                        <div>
                          <div style={{ fontWeight: '700', color: '#FFFFFF' }}>{audit.eventType}</div>
                          <div style={{ fontSize: '10px', color: 'var(--text-dim)' }}>
                            Actor: {audit.actorId} · ID: #{audit.id}
                          </div>
                        </div>
                        <div style={{ textAlign: 'right' }}>
                          <span style={{ fontSize: '11px', color: '#38BDF8' }}>
                            {new Date(audit.createdAt).toLocaleDateString()}
                          </span>
                          <div style={{ fontSize: '10px', color: 'var(--text-dim)' }}>
                            {new Date(audit.createdAt).toLocaleTimeString()}
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          ) : null}
        </div>

        <div className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Close
          </button>
        </div>
      </div>
    </div>
  );
};

import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { adminService } from '../services/adminService';
import {
  Users,
  UserCheck,
  UserX,
  ShieldAlert,
  FileText,
  Activity,
  ArrowRight,
  Shield,
  Server,
  RefreshCw,
  Clock,
  CheckCircle2,
  AlertCircle
} from 'lucide-react';

export const OverviewPage = () => {
  const [metrics, setMetrics] = useState(null);
  const [activity, setActivity] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const navigate = useNavigate();

  const loadData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [m, a] = await Promise.all([
        adminService.getMetrics(),
        adminService.getActivity(),
      ]);
      setMetrics(m);
      setActivity(a);
    } catch (err) {
      setError(err.message || 'Failed to load platform metrics.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '28px' }}>
      {/* Top Banner */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <div style={{ fontSize: '11px', fontWeight: '800', letterSpacing: '0.08em', color: 'var(--accent-cyan)', textTransform: 'uppercase' }}>
            Platform Overview &amp; Control
          </div>
          <h1 style={{ fontSize: '26px', fontWeight: '800', color: '#FFFFFF', letterSpacing: '-0.02em', marginTop: '2px' }}>
            SAGE Governance Dashboard
          </h1>
          <p style={{ fontSize: '13px', color: 'var(--text-muted)', marginTop: '4px' }}>
            Institutional Approval &amp; Compliance Platform · Administrative Operations &amp; Audit Telemetry
          </p>
        </div>

        <button onClick={loadData} className="btn btn-secondary" title="Refresh Live Metrics">
          <RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
          <span>Refresh</span>
        </button>
      </div>

      {error && (
        <div style={{ padding: '16px', background: 'rgba(244, 63, 94, 0.15)', border: '1px solid rgba(244, 63, 94, 0.3)', borderRadius: 'var(--radius-md)', color: '#FB7185', display: 'flex', alignItems: 'center', gap: '10px' }}>
          <AlertCircle size={20} />
          <span>{error}</span>
        </div>
      )}

      {/* Metrics Row */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(210px, 1fr))', gap: '16px' }}>
        <div className="stat-card">
          <div>
            <div className="stat-label">Total Users</div>
            <div className="stat-val">{metrics?.totalUsers ?? '—'}</div>
            <div style={{ fontSize: '11px', color: 'var(--text-dim)', marginTop: '4px' }}>Registered in Directory</div>
          </div>
          <Users size={24} color="var(--accent-cyan)" />
        </div>

        <div className="stat-card emerald">
          <div>
            <div className="stat-label">Active Users</div>
            <div className="stat-val" style={{ color: '#34D399' }}>{metrics?.activeUsers ?? '—'}</div>
            <div style={{ fontSize: '11px', color: 'var(--text-dim)', marginTop: '4px' }}>Authorized for Access</div>
          </div>
          <UserCheck size={24} color="#10B981" />
        </div>

        <div className="stat-card amber">
          <div>
            <div className="stat-label">Suspended Users</div>
            <div className="stat-val" style={{ color: '#FBBF24' }}>{metrics?.suspendedUsers ?? '—'}</div>
            <div style={{ fontSize: '11px', color: 'var(--text-dim)', marginTop: '4px' }}>Temporary Hold</div>
          </div>
          <ShieldAlert size={24} color="#F59E0B" />
        </div>

        <div className="stat-card rose">
          <div>
            <div className="stat-label">Deactivated Users</div>
            <div className="stat-val" style={{ color: '#FB7185' }}>{metrics?.deactivatedUsers ?? '—'}</div>
            <div style={{ fontSize: '11px', color: 'var(--text-dim)', marginTop: '4px' }}>Soft-Deleted Accounts</div>
          </div>
          <UserX size={24} color="#F43F5E" />
        </div>

        <div className="stat-card indigo">
          <div>
            <div className="stat-label">Audit Events</div>
            <div className="stat-val" style={{ color: '#A5B4FC' }}>{metrics?.totalAuditEvents ?? '—'}</div>
            <div style={{ fontSize: '11px', color: 'var(--text-dim)', marginTop: '4px' }}>Immutable Records</div>
          </div>
          <FileText size={24} color="#6366F1" />
        </div>
      </div>

      {/* Two Column Section: Quick Actions & Role Distribution */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px' }}>
        {/* Quick Governance Actions */}
        <div className="glass-card">
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '16px' }}>
            <h2 style={{ fontSize: '15px', fontWeight: '800', color: '#FFFFFF' }}>Governance Shortcuts</h2>
            <Shield size={16} color="var(--accent-cyan)" />
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            <div
              onClick={() => navigate('/users')}
              style={{
                padding: '14px 18px',
                background: 'rgba(255, 255, 255, 0.03)',
                border: '1px solid var(--border-subtle)',
                borderRadius: 'var(--radius-md)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                cursor: 'pointer',
                transition: 'background 0.2s',
              }}
              onMouseEnter={(e) => (e.currentTarget.style.background = 'rgba(255, 255, 255, 0.06)')}
              onMouseLeave={(e) => (e.currentTarget.style.background = 'rgba(255, 255, 255, 0.03)')}
            >
              <div>
                <div style={{ fontSize: '14px', fontWeight: '700', color: '#FFFFFF' }}>Manage User Directory</div>
                <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>Search, add, update, and manage account statuses</div>
              </div>
              <ArrowRight size={16} color="var(--accent-cyan)" />
            </div>

            <div
              onClick={() => navigate('/audit-logs')}
              style={{
                padding: '14px 18px',
                background: 'rgba(255, 255, 255, 0.03)',
                border: '1px solid var(--border-subtle)',
                borderRadius: 'var(--radius-md)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                cursor: 'pointer',
                transition: 'background 0.2s',
              }}
              onMouseEnter={(e) => (e.currentTarget.style.background = 'rgba(255, 255, 255, 0.06)')}
              onMouseLeave={(e) => (e.currentTarget.style.background = 'rgba(255, 255, 255, 0.03)')}
            >
              <div>
                <div style={{ fontSize: '14px', fontWeight: '700', color: '#FFFFFF' }}>Inspect Audit Trail</div>
                <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>Filter administrative and state transition events</div>
              </div>
              <ArrowRight size={16} color="var(--accent-cyan)" />
            </div>

            <div
              onClick={() => navigate('/activity')}
              style={{
                padding: '14px 18px',
                background: 'rgba(255, 255, 255, 0.03)',
                border: '1px solid var(--border-subtle)',
                borderRadius: 'var(--radius-md)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                cursor: 'pointer',
                transition: 'background 0.2s',
              }}
              onMouseEnter={(e) => (e.currentTarget.style.background = 'rgba(255, 255, 255, 0.06)')}
              onMouseLeave={(e) => (e.currentTarget.style.background = 'rgba(255, 255, 255, 0.03)')}
            >
              <div>
                <div style={{ fontSize: '14px', fontWeight: '700', color: '#FFFFFF' }}>Live User Activity</div>
                <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>Track authentication timestamps and recent logins</div>
              </div>
              <ArrowRight size={16} color="var(--accent-cyan)" />
            </div>
          </div>
        </div>

        {/* Roles Distribution */}
        <div className="glass-card">
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '16px' }}>
            <h2 style={{ fontSize: '15px', fontWeight: '800', color: '#FFFFFF' }}>Role Distribution</h2>
            <Server size={16} color="var(--accent-cyan)" />
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            {metrics?.roleCounts &&
              Object.entries(metrics.roleCounts).map(([role, count]) => (
                <div
                  key={role}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '8px 12px',
                    borderRadius: 'var(--radius-sm)',
                    background: 'rgba(0, 0, 0, 0.2)',
                    fontSize: '12px',
                  }}
                >
                  <span style={{ color: 'var(--text-main)', fontWeight: '600' }}>{role}</span>
                  <span className="badge badge-role">{count} accounts</span>
                </div>
              ))}
          </div>
        </div>
      </div>

      {/* Recent Audit Activity Stream */}
      <div className="glass-card">
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '16px' }}>
          <div>
            <h2 style={{ fontSize: '15px', fontWeight: '800', color: '#FFFFFF' }}>Recent Administrative Events</h2>
            <p style={{ fontSize: '12px', color: 'var(--text-muted)' }}>Persistent audit log records across the platform</p>
          </div>
          <button onClick={() => navigate('/audit-logs')} className="btn btn-secondary" style={{ fontSize: '12px', padding: '6px 12px' }}>
            View Full Trail
          </button>
        </div>

        <div className="data-table-container">
          <table className="data-table">
            <thead>
              <tr>
                <th>Event ID</th>
                <th>Type</th>
                <th>Actor</th>
                <th>Target</th>
                <th>Outcome</th>
                <th>Timestamp</th>
              </tr>
            </thead>
            <tbody>
              {!activity?.recentAuditEvents || activity.recentAuditEvents.length === 0 ? (
                <tr>
                  <td colSpan={6} style={{ textAlign: 'center', padding: '24px', color: 'var(--text-dim)' }}>
                    No audit events recorded yet.
                  </td>
                </tr>
              ) : (
                activity.recentAuditEvents.slice(0, 5).map((evt) => (
                  <tr key={evt.id}>
                    <td style={{ fontFamily: 'var(--font-mono)', fontSize: '11px', color: 'var(--accent-cyan)' }}>
                      #{evt.id}
                    </td>
                    <td>
                      <span className="badge badge-admin">{evt.eventType}</span>
                    </td>
                    <td style={{ fontWeight: '600' }}>{evt.actorId || 'SYSTEM'}</td>
                    <td>{evt.targetEntityType}: {evt.targetEntityId}</td>
                    <td>
                      <span className="badge badge-active">{evt.outcome || 'SUCCESS'}</span>
                    </td>
                    <td style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                      {new Date(evt.createdAt).toLocaleString()}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

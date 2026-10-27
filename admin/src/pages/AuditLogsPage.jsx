import React, { useEffect, useState, useCallback } from 'react';
import { adminService } from '../services/adminService';
import {
  FileText,
  Search,
  Filter,
  RefreshCw,
  ChevronLeft,
  ChevronRight,
  Eye,
  X,
  ShieldCheck,
  AlertCircle
} from 'lucide-react';

export const AuditLogsPage = () => {
  const [logs, setLogs] = useState([]);
  const [pageInfo, setPageInfo] = useState({
    page: 0,
    size: 15,
    totalElements: 0,
    totalPages: 0,
    first: true,
    last: true,
  });

  const [filters, setFilters] = useState({
    eventType: '',
    actorId: '',
    targetEntityType: '',
    targetEntityId: '',
  });

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [selectedAudit, setSelectedAudit] = useState(null);

  const fetchLogs = useCallback(async (page = 0) => {
    setLoading(true);
    setError(null);
    try {
      const res = await adminService.getAuditLogs({
        eventType: filters.eventType,
        actorId: filters.actorId,
        targetEntityType: filters.targetEntityType,
        targetEntityId: filters.targetEntityId,
        page,
        size: pageInfo.size,
      });

      setLogs(res.content || []);
      setPageInfo({
        page: res.page,
        size: res.size,
        totalElements: res.totalElements,
        totalPages: res.totalPages,
        first: res.first,
        last: res.last,
      });
    } catch (err) {
      setError(err.message || 'Failed to fetch audit logs.');
    } finally {
      setLoading(false);
    }
  }, [filters, pageInfo.size]);

  useEffect(() => {
    fetchLogs(0);
  }, [fetchLogs]);

  const handleFilterChange = (key, val) => {
    setFilters((prev) => ({ ...prev, [key]: val }));
  };

  const parseMetadata = (jsonStr) => {
    try {
      return JSON.parse(jsonStr);
    } catch {
      return { raw: jsonStr };
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      {/* Title */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <div style={{ fontSize: '11px', fontWeight: '800', letterSpacing: '0.08em', color: 'var(--accent-cyan)', textTransform: 'uppercase' }}>
            Compliance &amp; Traceability
          </div>
          <h1 style={{ fontSize: '26px', fontWeight: '800', color: '#FFFFFF', letterSpacing: '-0.02em', marginTop: '2px' }}>
            Administrative Audit Trail
          </h1>
          <p style={{ fontSize: '13px', color: 'var(--text-muted)', marginTop: '4px' }}>
            Immutable, database-persisted records capturing all consequential administrative mutations and lifecycle actions.
          </p>
        </div>

        <button onClick={() => fetchLogs(0)} className="btn btn-secondary">
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

      {/* Filter Bar */}
      <div className="glass-card" style={{ padding: '18px 24px' }}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr) auto', gap: '12px', alignItems: 'center' }}>
          <input
            type="text"
            value={filters.eventType}
            onChange={(e) => handleFilterChange('eventType', e.target.value)}
            placeholder="Event Type (e.g. USER_CREATED)..."
            className="input-field"
          />

          <input
            type="text"
            value={filters.actorId}
            onChange={(e) => handleFilterChange('actorId', e.target.value)}
            placeholder="Actor ID (e.g. USR-ADM-01)..."
            className="input-field"
          />

          <input
            type="text"
            value={filters.targetEntityType}
            onChange={(e) => handleFilterChange('targetEntityType', e.target.value)}
            placeholder="Target Type (e.g. USER)..."
            className="input-field"
          />

          <input
            type="text"
            value={filters.targetEntityId}
            onChange={(e) => handleFilterChange('targetEntityId', e.target.value)}
            placeholder="Target ID (e.g. USR-SO-01)..."
            className="input-field"
          />

          <button onClick={() => fetchLogs(0)} className="btn btn-secondary" title="Search Audit Records">
            <RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
            <span>Filter</span>
          </button>
        </div>
      </div>

      {/* Audit Logs Table */}
      <div className="data-table-container">
        <table className="data-table">
          <thead>
            <tr>
              <th>Event ID</th>
              <th>Action / Event Type</th>
              <th>Acting Administrator</th>
              <th>Target Entity</th>
              <th>Target ID</th>
              <th>Outcome</th>
              <th>Recorded At</th>
              <th style={{ textAlign: 'right' }}>Payload Diff</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={8} style={{ textAlign: 'center', padding: '40px', color: 'var(--text-muted)' }}>
                  Loading audit events...
                </td>
              </tr>
            ) : logs.length === 0 ? (
              <tr>
                <td colSpan={8} style={{ textAlign: 'center', padding: '40px', color: 'var(--text-dim)' }}>
                  No audit log entries matching criteria.
                </td>
              </tr>
            ) : (
              logs.map((audit) => (
                <tr key={audit.id}>
                  <td style={{ fontFamily: 'var(--font-mono)', fontSize: '11px', color: 'var(--accent-cyan)' }}>
                    #{audit.id}
                  </td>
                  <td>
                    <span className="badge badge-admin">{audit.eventType}</span>
                  </td>
                  <td style={{ fontWeight: '700' }}>{audit.actorId || 'SYSTEM'}</td>
                  <td>{audit.targetEntityType}</td>
                  <td style={{ fontFamily: 'var(--font-mono)', fontSize: '12px' }}>{audit.targetEntityId}</td>
                  <td>
                    <span className="badge badge-active">{audit.outcome || 'SUCCESS'}</span>
                  </td>
                  <td style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                    {new Date(audit.createdAt).toLocaleString()}
                  </td>
                  <td style={{ textAlign: 'right' }}>
                    <button
                      onClick={() => setSelectedAudit(audit)}
                      className="btn btn-secondary"
                      style={{ padding: '4px 10px', fontSize: '11px' }}
                    >
                      <Eye size={13} />
                      <span>Inspect</span>
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Pagination Footer */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 4px' }}>
        <div style={{ fontSize: '13px', color: 'var(--text-muted)' }}>
          Showing <strong>{logs.length}</strong> of <strong>{pageInfo.totalElements}</strong> total audit records
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <button
            onClick={() => fetchLogs(pageInfo.page - 1)}
            disabled={pageInfo.first || loading}
            className="btn btn-secondary"
            style={{ padding: '6px 12px' }}
          >
            <ChevronLeft size={16} />
            <span>Previous</span>
          </button>

          <span style={{ fontSize: '13px', color: 'var(--text-dim)', margin: '0 8px' }}>
            Page {pageInfo.page + 1} of {Math.max(1, pageInfo.totalPages)}
          </span>

          <button
            onClick={() => fetchLogs(pageInfo.page + 1)}
            disabled={pageInfo.last || loading}
            className="btn btn-secondary"
            style={{ padding: '6px 12px' }}
          >
            <span>Next</span>
            <ChevronRight size={16} />
          </button>
        </div>
      </div>

      {/* Audit Payload Inspection Modal */}
      {selectedAudit && (
        <div className="modal-overlay" onClick={() => setSelectedAudit(null)}>
          <div className="modal-content" style={{ maxWidth: '640px' }} onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <div
                  style={{
                    width: '32px',
                    height: '32px',
                    borderRadius: 'var(--radius-sm)',
                    background: 'rgba(56, 189, 248, 0.15)',
                    display: 'grid',
                    placeItems: 'center',
                    color: '#38BDF8',
                  }}
                >
                  <FileText size={18} />
                </div>
                <div>
                  <h2 style={{ fontSize: '16px', fontWeight: '800', color: '#FFFFFF' }}>
                    Audit Event Payload #{selectedAudit.id}
                  </h2>
                  <p style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                    Type: {selectedAudit.eventType} · Actor: {selectedAudit.actorId}
                  </p>
                </div>
              </div>
              <button className="btn-icon" onClick={() => setSelectedAudit(null)}>
                <X size={18} />
              </button>
            </div>

            <div className="modal-body">
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', marginBottom: '16px' }}>
                <div style={{ padding: '10px 14px', background: 'rgba(0, 0, 0, 0.3)', borderRadius: 'var(--radius-sm)' }}>
                  <span style={{ fontSize: '10px', color: 'var(--text-dim)', textTransform: 'uppercase', fontWeight: '700' }}>Target Entity</span>
                  <p style={{ fontSize: '13px', fontWeight: '700', color: '#FFFFFF', marginTop: '2px' }}>
                    {selectedAudit.targetEntityType}: {selectedAudit.targetEntityId}
                  </p>
                </div>
                <div style={{ padding: '10px 14px', background: 'rgba(0, 0, 0, 0.3)', borderRadius: 'var(--radius-sm)' }}>
                  <span style={{ fontSize: '10px', color: 'var(--text-dim)', textTransform: 'uppercase', fontWeight: '700' }}>Timestamp</span>
                  <p style={{ fontSize: '13px', fontWeight: '700', color: '#FFFFFF', marginTop: '2px' }}>
                    {new Date(selectedAudit.createdAt).toLocaleString()}
                  </p>
                </div>
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '11px', fontWeight: '700', color: 'var(--text-muted)', marginBottom: '6px', textTransform: 'uppercase' }}>
                  Metadata JSON / Field Diffs (Sanitized)
                </label>
                <pre
                  style={{
                    background: '#0B1120',
                    border: '1px solid var(--border-subtle)',
                    borderRadius: 'var(--radius-md)',
                    padding: '14px',
                    color: '#38BDF8',
                    fontFamily: 'var(--font-mono)',
                    fontSize: '12px',
                    overflowX: 'auto',
                    maxHeight: '300px',
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-word',
                  }}
                >
                  {JSON.stringify(parseMetadata(selectedAudit.metadataJson), null, 2)}
                </pre>
              </div>
            </div>

            <div className="modal-footer">
              <button className="btn btn-secondary" onClick={() => setSelectedAudit(null)}>
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

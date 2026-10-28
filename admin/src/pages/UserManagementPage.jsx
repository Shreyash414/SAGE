import React, { useEffect, useState, useCallback } from 'react';
import { adminService } from '../services/adminService';
import { CreateUserModal } from '../components/users/CreateUserModal';
import { EditUserModal } from '../components/users/EditUserModal';
import { StatusChangeModal } from '../components/users/StatusChangeModal';
import { UserDetailModal } from '../components/users/UserDetailModal';
import {
  Users,
  UserPlus,
  Search,
  Filter,
  Edit2,
  ShieldAlert,
  Eye,
  ChevronLeft,
  ChevronRight,
  RefreshCw,
  AlertCircle,
  CheckCircle2,
  Clock,
  Shield
} from 'lucide-react';

const ROLES = [
  'Administrator',
  'Institution Representative',
  'Regional Officer',
  'Scrutiny Officer',
  'EVC Coordinator',
  'EVC Member',
  'Compliance Reviewer',
  'Senior Management'
];

export const UserManagementPage = () => {
  const [users, setUsers] = useState([]);
  const [pageInfo, setPageInfo] = useState({
    page: 0,
    size: 10,
    totalElements: 0,
    totalPages: 0,
    first: true,
    last: true,
  });

  const [filters, setFilters] = useState({
    search: '',
    role: '',
    status: '',
    region: '',
  });

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [toast, setToast] = useState(null);

  // Modal states
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [editingUser, setEditingUser] = useState(null);
  const [statusUser, setStatusUser] = useState(null);
  const [detailUserId, setDetailUserId] = useState(null);

  const fetchUsers = useCallback(async (page = 0) => {
    setLoading(true);
    setError(null);
    try {
      const res = await adminService.getUsers({
        search: filters.search,
        role: filters.role,
        status: filters.status,
        region: filters.region,
        page,
        size: pageInfo.size,
      });

      setUsers(res.content || []);
      setPageInfo({
        page: res.page,
        size: res.size,
        totalElements: res.totalElements,
        totalPages: res.totalPages,
        first: res.first,
        last: res.last,
      });
    } catch (err) {
      setError(err.message || 'Failed to retrieve user directory.');
    } finally {
      setLoading(false);
    }
  }, [filters, pageInfo.size]);

  useEffect(() => {
    fetchUsers(0);
  }, [fetchUsers]);

  const handleFilterChange = (key, val) => {
    setFilters((prev) => ({ ...prev, [key]: val }));
  };

  const showNotification = (msg) => {
    setToast(msg);
    setTimeout(() => setToast(null), 5000);
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      {/* Page Title & Add Button */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <div style={{ fontSize: '11px', fontWeight: '800', letterSpacing: '0.08em', color: 'var(--accent-cyan)', textTransform: 'uppercase' }}>
            Identity &amp; Access Governance
          </div>
          <h1 style={{ fontSize: '26px', fontWeight: '800', color: '#FFFFFF', letterSpacing: '-0.02em', marginTop: '2px' }}>
            User Management Directory
          </h1>
          <p style={{ fontSize: '13px', color: 'var(--text-muted)', marginTop: '4px' }}>
            Search registered accounts, assign roles, enforce account status transitions, and audit changes.
          </p>
        </div>

        <button onClick={() => setShowCreateModal(true)} className="btn btn-primary">
          <UserPlus size={16} />
          <span>Provision User</span>
        </button>
      </div>

      {toast && (
        <div style={{ padding: '12px 16px', background: 'rgba(16, 185, 129, 0.15)', border: '1px solid rgba(16, 185, 129, 0.3)', borderRadius: 'var(--radius-md)', color: '#34D399', fontSize: '13px', display: 'flex', alignItems: 'center', gap: '8px' }}>
          <CheckCircle2 size={16} />
          <span>{toast}</span>
        </div>
      )}

      {error && (
        <div style={{ padding: '12px 16px', background: 'rgba(244, 63, 94, 0.15)', border: '1px solid rgba(244, 63, 94, 0.3)', borderRadius: 'var(--radius-md)', color: '#FB7185', fontSize: '13px', display: 'flex', alignItems: 'center', gap: '8px' }}>
          <AlertCircle size={16} />
          <span>{error}</span>
        </div>
      )}

      {/* Filter and Search Bar */}
      <div className="glass-card" style={{ padding: '18px 24px' }}>
        <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr 1fr 1fr auto', gap: '12px', alignItems: 'center' }}>
          {/* Search */}
          <div style={{ position: 'relative' }}>
            <Search size={16} color="var(--text-dim)" style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)' }} />
            <input
              type="text"
              value={filters.search}
              onChange={(e) => handleFilterChange('search', e.target.value)}
              placeholder="Search by name, email, user ID, institution..."
              className="input-field"
              style={{ paddingLeft: '36px' }}
            />
          </div>

          {/* Role Filter */}
          <select
            value={filters.role}
            onChange={(e) => handleFilterChange('role', e.target.value)}
            className="select-field"
          >
            <option value="">All Roles</option>
            {ROLES.map((r) => (
              <option key={r} value={r}>{r}</option>
            ))}
          </select>

          {/* Status Filter */}
          <select
            value={filters.status}
            onChange={(e) => handleFilterChange('status', e.target.value)}
            className="select-field"
          >
            <option value="">All Statuses</option>
            <option value="ACTIVE">ACTIVE</option>
            <option value="SUSPENDED">SUSPENDED</option>
            <option value="DEACTIVATED">DEACTIVATED</option>
          </select>

          {/* Region Filter */}
          <input
            type="text"
            value={filters.region}
            onChange={(e) => handleFilterChange('region', e.target.value)}
            placeholder="Filter region..."
            className="input-field"
          />

          <button onClick={() => fetchUsers(0)} className="btn btn-secondary" title="Apply Filters">
            <RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
            <span>Apply</span>
          </button>
        </div>
      </div>

      {/* Users Data Table */}
      <div className="data-table-container">
        <table className="data-table">
          <thead>
            <tr>
              <th>User ID</th>
              <th>Full Name</th>
              <th>Email</th>
              <th>Role</th>
              <th>Status</th>
              <th>Region / Entity</th>
              <th>Last Login</th>
              <th style={{ textAlign: 'right' }}>Actions</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={8} style={{ textAlign: 'center', padding: '40px', color: 'var(--text-muted)' }}>
                  Loading registered users...
                </td>
              </tr>
            ) : users.length === 0 ? (
              <tr>
                <td colSpan={8} style={{ textAlign: 'center', padding: '40px', color: 'var(--text-dim)' }}>
                  No users found matching current filter criteria.
                </td>
              </tr>
            ) : (
              users.map((u) => (
                <tr key={u.id}>
                  <td style={{ fontFamily: 'var(--font-mono)', fontSize: '12px', fontWeight: '600', color: 'var(--accent-cyan)' }}>
                    {u.userId}
                  </td>
                  <td style={{ fontWeight: '700' }}>{u.fullName}</td>
                  <td style={{ color: 'var(--text-muted)', fontSize: '12px' }}>{u.email}</td>
                  <td>
                    <span className={`badge ${u.role === 'Administrator' ? 'badge-admin' : 'badge-role'}`}>
                      {u.role}
                    </span>
                  </td>
                  <td>
                    <span
                      className={`badge ${
                        u.status === 'ACTIVE'
                          ? 'badge-active'
                          : u.status === 'SUSPENDED'
                          ? 'badge-suspended'
                          : 'badge-deactivated'
                      }`}
                    >
                      {u.status}
                    </span>
                  </td>
                  <td style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                    {u.institutionName || u.region || 'Platform-wide'}
                  </td>
                  <td style={{ fontSize: '12px', color: 'var(--text-dim)' }}>
                    {u.lastLoginAt ? (
                      <span title={new Date(u.lastLoginAt).toLocaleString()}>
                        {new Date(u.lastLoginAt).toLocaleDateString()}
                      </span>
                    ) : (
                      'Never'
                    )}
                  </td>
                  <td style={{ textAlign: 'right' }}>
                    <div style={{ display: 'inline-flex', gap: '4px' }}>
                      <button
                        onClick={() => setDetailUserId(u.userId)}
                        className="btn-icon"
                        title="View Full Profile & Audits"
                      >
                        <Eye size={15} color="var(--accent-cyan)" />
                      </button>
                      <button
                        onClick={() => setEditingUser(u)}
                        className="btn-icon"
                        title="Edit User Profile"
                      >
                        <Edit2 size={15} color="#A5B4FC" />
                      </button>
                      <button
                        onClick={() => setStatusUser(u)}
                        className="btn-icon"
                        title="Change Status (Activate / Suspend / Deactivate)"
                      >
                        <ShieldAlert
                          size={15}
                          color={u.status === 'ACTIVE' ? '#FB7185' : '#34D399'}
                        />
                      </button>
                    </div>
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
          Showing <strong>{users.length}</strong> of <strong>{pageInfo.totalElements}</strong> total users
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <button
            onClick={() => fetchUsers(pageInfo.page - 1)}
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
            onClick={() => fetchUsers(pageInfo.page + 1)}
            disabled={pageInfo.last || loading}
            className="btn btn-secondary"
            style={{ padding: '6px 12px' }}
          >
            <span>Next</span>
            <ChevronRight size={16} />
          </button>
        </div>
      </div>

      {/* Modals */}
      {showCreateModal && (
        <CreateUserModal
          onClose={() => setShowCreateModal(false)}
          onSuccess={() => {
            setShowCreateModal(false);
            showNotification('User account successfully provisioned and logged to audit trail.');
            fetchUsers(0);
          }}
        />
      )}

      {editingUser && (
        <EditUserModal
          user={editingUser}
          onClose={() => setEditingUser(null)}
          onSuccess={() => {
            setEditingUser(null);
            showNotification('User profile successfully updated and logged to audit trail.');
            fetchUsers(pageInfo.page);
          }}
        />
      )}

      {statusUser && (
        <StatusChangeModal
          user={statusUser}
          onClose={() => setStatusUser(null)}
          onSuccess={() => {
            setStatusUser(null);
            showNotification('Account lifecycle status changed and session tokens invalidated.');
            fetchUsers(pageInfo.page);
          }}
        />
      )}

      {detailUserId && (
        <UserDetailModal
          userId={detailUserId}
          onClose={() => setDetailUserId(null)}
        />
      )}
    </div>
  );
};

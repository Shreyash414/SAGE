import React, { useEffect, useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { adminService } from '../../services/adminService';
import {
  Shield,
  Users,
  Activity,
  FileText,
  Sliders,
  LogOut,
  LayoutDashboard,
  CheckCircle,
  AlertCircle,
  ExternalLink
} from 'lucide-react';

export const AdminLayout = () => {
  const { admin, logout } = useAuth();
  const navigate = useNavigate();
  const [backendHealthy, setBackendHealthy] = useState(null);

  useEffect(() => {
    let isMounted = true;
    const checkHealth = async () => {
      try {
        const res = await adminService.getHealth();
        if (isMounted) setBackendHealthy(res?.status === 'UP');
      } catch {
        if (isMounted) setBackendHealthy(false);
      }
    };
    checkHealth();
    const interval = setInterval(checkHealth, 30000);
    return () => {
      isMounted = false;
      clearInterval(interval);
    };
  }, []);

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <div className="admin-layout">
      {/* Sidebar */}
      <aside className="admin-sidebar">
        {/* Branding */}
        <div style={{ padding: '24px 20px', borderBottom: '1px solid var(--border-subtle)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <div
              style={{
                width: '40px',
                height: '40px',
                borderRadius: 'var(--radius-md)',
                background: 'linear-gradient(135deg, #0284C7, #4F46E5)',
                display: 'grid',
                placeItems: 'center',
                color: '#FFFFFF',
                fontWeight: '900',
                fontSize: '20px',
                boxShadow: '0 0 15px rgba(56, 189, 248, 0.3)',
              }}
            >
              S
            </div>
            <div>
              <div style={{ fontSize: '15px', fontWeight: '800', letterSpacing: '0.04em', color: '#FFFFFF' }}>
                SAGE
              </div>
              <div style={{ fontSize: '10px', fontWeight: '700', letterSpacing: '0.06em', color: '#38BDF8', textTransform: 'uppercase' }}>
                Admin Console
              </div>
            </div>
          </div>
          <div style={{ fontSize: '10px', color: 'var(--text-dim)', marginTop: '8px', lineHeight: 1.3 }}>
            Institutional Approval &amp; Compliance Platform
          </div>
        </div>

        {/* Navigation */}
        <div style={{ padding: '16px 0', flex: 1, display: 'flex', flexDirection: 'column' }}>
          <div style={{ fontSize: '10px', fontWeight: '800', letterSpacing: '0.08em', color: 'var(--text-dim)', padding: '0 24px 8px', textTransform: 'uppercase' }}>
            Governance
          </div>
          <NavLink to="/overview" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
            <LayoutDashboard size={18} />
            <span>Overview</span>
          </NavLink>
          <NavLink to="/users" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
            <Users size={18} />
            <span>User Management</span>
          </NavLink>
          <NavLink to="/activity" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
            <Activity size={18} />
            <span>User Activity</span>
          </NavLink>
          <NavLink to="/audit-logs" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
            <FileText size={18} />
            <span>Audit Logs</span>
          </NavLink>
          <NavLink to="/settings" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
            <Sliders size={18} />
            <span>Platform &amp; Security</span>
          </NavLink>

          <div style={{ marginTop: 'auto', padding: '16px' }}>
            {/* User portal bridge link */}
            <a
              href="http://localhost:5173"
              target="_blank"
              rel="noreferrer"
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '10px 14px',
                borderRadius: 'var(--radius-md)',
                background: 'rgba(255, 255, 255, 0.03)',
                border: '1px solid var(--border-subtle)',
                color: 'var(--text-muted)',
                fontSize: '12px',
                textDecoration: 'none',
                fontWeight: '600',
              }}
            >
              <span>User Portal (:5173)</span>
              <ExternalLink size={14} />
            </a>
          </div>
        </div>

        {/* Administrator Footer */}
        <div style={{ padding: '16px 20px', borderTop: '1px solid var(--border-subtle)', background: 'rgba(0, 0, 0, 0.2)' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <div style={{ minWidth: 0 }}>
              <div style={{ fontSize: '13px', fontWeight: '700', color: '#FFFFFF', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {admin?.fullName || 'System Admin'}
              </div>
              <div style={{ fontSize: '11px', color: 'var(--accent-cyan)', fontWeight: '600' }}>
                {admin?.id || 'USR-ADM-01'} · Admin
              </div>
            </div>
            <button
              onClick={handleLogout}
              className="btn-icon"
              title="Sign Out"
              style={{ color: '#F43F5E' }}
            >
              <LogOut size={16} />
            </button>
          </div>
        </div>
      </aside>

      {/* Main Content Area */}
      <div className="admin-main">
        {/* Header */}
        <header className="admin-header">
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <span className="badge badge-admin">
              <Shield size={12} />
              ADMIN PRIVILEGES ENFORCED
            </span>
            <span style={{ fontSize: '12px', color: 'var(--text-dim)' }}>
              Port 5174 · Strict Architectural Separation
            </span>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
            {/* Backend API Health Status */}
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '6px',
                fontSize: '12px',
                fontWeight: '600',
                color: backendHealthy ? '#34D399' : backendHealthy === false ? '#FB7185' : 'var(--text-muted)',
              }}
            >
              {backendHealthy ? (
                <>
                  <CheckCircle size={14} />
                  <span>API :8080 Active</span>
                </>
              ) : backendHealthy === false ? (
                <>
                  <AlertCircle size={14} />
                  <span>API Disconnected</span>
                </>
              ) : (
                <span>Checking API...</span>
              )}
            </div>
          </div>
        </header>

        {/* Content Outlet */}
        <main className="admin-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
};

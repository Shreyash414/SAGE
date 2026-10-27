import React from 'react';
import { Sliders, Shield, Network, Database, Lock, CheckCircle2, AlertTriangle, ExternalLink } from 'lucide-react';

export const SettingsPage = () => {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      {/* Title */}
      <div>
        <div style={{ fontSize: '11px', fontWeight: '800', letterSpacing: '0.08em', color: 'var(--accent-cyan)', textTransform: 'uppercase' }}>
          Platform Architecture &amp; Security Perimeter
        </div>
        <h1 style={{ fontSize: '26px', fontWeight: '800', color: '#FFFFFF', letterSpacing: '-0.02em', marginTop: '2px' }}>
          Platform Settings &amp; Compliance Configuration
        </h1>
        <p style={{ fontSize: '13px', color: 'var(--text-muted)', marginTop: '4px' }}>
          SAGE Architecture configuration, operational port separation, and cryptographic verification policies.
        </p>
      </div>

      {/* Security Clarification Alert */}
      <div
        style={{
          padding: '16px 20px',
          borderRadius: 'var(--radius-lg)',
          background: 'rgba(56, 189, 248, 0.08)',
          border: '1px solid rgba(56, 189, 248, 0.3)',
          display: 'flex',
          alignItems: 'flex-start',
          gap: '14px',
        }}
      >
        <Shield size={24} color="#38BDF8" style={{ marginTop: '2px', flexShrink: 0 }} />
        <div>
          <div style={{ fontSize: '14px', fontWeight: '800', color: '#FFFFFF' }}>
            Security Perimeter: Port Separation vs. Authorization
          </div>
          <p style={{ fontSize: '13px', color: 'var(--text-main)', marginTop: '4px', lineHeight: 1.5 }}>
            The SAGE Admin Panel runs on dedicated port <strong>5174</strong> with <code>strictPort: true</code> to achieve architectural separation from the user-facing portal (port <strong>5173</strong>). Port separation is an operational division, not a security boundary: every sensitive administrative endpoint is strictly protected by backend authentication and persisted <code>ROLE_ADMIN</code> / <code>ROLE_ADMINISTRATOR</code> authorization.
          </p>
        </div>
      </div>

      {/* Grid of Configurations */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px' }}>
        {/* Network & Ports Card */}
        <div className="glass-card">
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '16px' }}>
            <Network size={18} color="var(--accent-cyan)" />
            <h2 style={{ fontSize: '16px', fontWeight: '800', color: '#FFFFFF' }}>Network &amp; Port Topology</h2>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            <div style={{ padding: '12px 14px', background: 'rgba(15, 23, 42, 0.6)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <div style={{ fontSize: '13px', fontWeight: '700', color: '#FFFFFF' }}>SAGE Admin Console</div>
                <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Dedicated administrative UI entry point</div>
              </div>
              <span className="badge badge-admin">Port 5174</span>
            </div>

            <div style={{ padding: '12px 14px', background: 'rgba(15, 23, 42, 0.6)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <div style={{ fontSize: '13px', fontWeight: '700', color: '#FFFFFF' }}>SAGE User-Facing Portal</div>
                <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Institution &amp; bureau officer workspaces</div>
              </div>
              <span className="badge badge-role">Port 5173</span>
            </div>

            <div style={{ padding: '12px 14px', background: 'rgba(15, 23, 42, 0.6)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <div style={{ fontSize: '13px', fontWeight: '700', color: '#FFFFFF' }}>Spring Boot REST API</div>
                <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Central backend &amp; OpenAPI swagger UI</div>
              </div>
              <span className="badge badge-active">Port 8080</span>
            </div>

            <div style={{ padding: '12px 14px', background: 'rgba(15, 23, 42, 0.6)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <div style={{ fontSize: '13px', fontWeight: '700', color: '#FFFFFF' }}>PostgreSQL Database</div>
                <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Relational persistent compliance store</div>
              </div>
              <span className="badge badge-role">Port 5432</span>
            </div>
          </div>
        </div>

        {/* Database & Flyway Status Card */}
        <div className="glass-card">
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '16px' }}>
            <Database size={18} color="#34D399" />
            <h2 style={{ fontSize: '16px', fontWeight: '800', color: '#FFFFFF' }}>Database &amp; Migrations</h2>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            <div style={{ padding: '12px 14px', background: 'rgba(15, 23, 42, 0.6)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ fontSize: '13px', fontWeight: '700', color: '#FFFFFF' }}>V1__initial_schema.sql</span>
                <span className="badge badge-active">Applied</span>
              </div>
              <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '2px' }}>
                Core tables: institutions, applications, audit_events
              </div>
            </div>

            <div style={{ padding: '12px 14px', background: 'rgba(15, 23, 42, 0.6)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ fontSize: '13px', fontWeight: '700', color: '#FFFFFF' }}>V2__workflow_and_application_extensions.sql</span>
                <span className="badge badge-active">Applied</span>
              </div>
              <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '2px' }}>
                Workflow engine, scrutiny stages, workflow_transitions
              </div>
            </div>

            <div style={{ padding: '12px 14px', background: 'rgba(15, 23, 42, 0.6)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ fontSize: '13px', fontWeight: '700', color: '#FFFFFF' }}>V3__evc_operations.sql</span>
                <span className="badge badge-active">Applied</span>
              </div>
              <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '2px' }}>
                Expert Visit Committee (EVC) scheduling &amp; verification
              </div>
            </div>

            <div style={{ padding: '12px 14px', background: 'rgba(15, 23, 42, 0.6)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ fontSize: '13px', fontWeight: '700', color: '#FFFFFF' }}>V4__admin_and_user_management.sql</span>
                <span className="badge badge-active">Active</span>
              </div>
              <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '2px' }}>
                Persistent users directory, account statuses, audit extensions
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

// SIH 26262 - Role 5: EVC Member Dedicated Dashboard
import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { apiClient } from '../../services/apiClient';
import { KPICard } from '../../components/common/KPICard';
import { StatusBadge } from '../../components/common/StatusBadge';
import { DashboardSection } from '../../components/common/DashboardSection';
import { EmptyState } from '../../components/common/EmptyState';
import { Modal } from '../../components/common/Modal';
import { TabsNav } from '../../components/common/TabsNav';
import {
  ClipboardCheck,
  Calendar,
  CheckCircle2,
  Clock,
  AlertTriangle,
  Building2,
  FileText,
  UserCheck,
  Phone,
  Download,
  Eye,
  Search,
  Save,
  Loader2,
  RefreshCw,
  ShieldCheck,
  MapPin,
} from 'lucide-react';

export const EVCMemberDashboard = () => {
  const { user } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();

  // Tab State: sync with URL query param ?tab=
  const activeTab = searchParams.get('tab') || 'overview';

  const handleTabChange = (tabKey) => {
    setSearchParams({ tab: tabKey });
  };

  // State: Dashboard Data from live backend API
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [profile, setProfile] = useState(null);
  const [metrics, setMetrics] = useState({
    totalCases: 0,
    pendingScheduling: 0,
    upcomingVisits: 0,
    completedVisits: 0,
    reportsPending: 0,
    overdueTasks: 0,
  });
  const [inspections, setInspections] = useState([]);

  // Inspection search & modal state
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedInspection, setSelectedInspection] = useState(null);
  const [isDossierModalOpen, setIsDossierModalOpen] = useState(false);

  // Documents state for selected inspection
  const [selectedDocsInspection, setSelectedDocsInspection] = useState(null);
  const [documents, setDocuments] = useState([]);
  const [docsLoading, setDocsLoading] = useState(false);
  const [docsError, setDocsError] = useState(null);

  // Profile self-edit state
  const [profileForm, setProfileForm] = useState({
    availabilityStatus: 'AVAILABLE',
    phone: '',
  });
  const [savingProfile, setSavingProfile] = useState(false);
  const [profileSaveSuccess, setProfileSaveSuccess] = useState(false);
  const [profileSaveError, setProfileSaveError] = useState(null);

  // Fetch Member Dashboard Data
  const fetchDashboardData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      // 1. Fetch member dashboard bundle
      const dashData = await apiClient('/evc/member/dashboard');
      if (dashData) {
        if (dashData.profile) {
          setProfile(dashData.profile);
          setProfileForm({
            availabilityStatus: dashData.profile.availabilityStatus || 'AVAILABLE',
            phone: dashData.profile.phone || '',
          });
        }
        if (dashData.metrics) {
          setMetrics(dashData.metrics);
        }
        if (Array.isArray(dashData.assignedInspections)) {
          setInspections(dashData.assignedInspections);
        }
      }
    } catch (err) {
      console.warn('Member dashboard endpoint fallback:', err.message);
      // Fallback: fetch profile and visits directly
      try {
        const [profData, visitsData, metricsData] = await Promise.all([
          apiClient('/evc/member/profile').catch(() => null),
          apiClient('/evc').catch(() => []),
          apiClient('/evc/metrics').catch(() => null),
        ]);

        if (profData) {
          setProfile(profData);
          setProfileForm({
            availabilityStatus: profData.availabilityStatus || 'AVAILABLE',
            phone: profData.phone || '',
          });
        }
        if (Array.isArray(visitsData)) {
          setInspections(visitsData);
        }
        if (metricsData) {
          setMetrics(metricsData);
        }
      } catch (innerErr) {
        console.error('Failed to load EVC member data:', innerErr);
        setError('Unable to load inspection data from server. Please check backend connection.');
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchDashboardData();
  }, [fetchDashboardData]);

  // Fetch Documents for an assigned inspection visit
  const handleViewDocuments = async (inspection) => {
    setSelectedDocsInspection(inspection);
    handleTabChange('reports');
    setDocsLoading(true);
    setDocsError(null);
    try {
      const idOrRef = inspection.evcReference || inspection.id;
      const docsData = await apiClient(`/evc/member/inspections/${idOrRef}/documents`);
      setDocuments(Array.isArray(docsData) ? docsData : []);
    } catch (err) {
      console.error('Failed to fetch inspection documents:', err);
      setDocsError('Unable to load documents for this inspection.');
      setDocuments([]);
    } finally {
      setDocsLoading(false);
    }
  };

  // Profile Save Handler
  const handleSaveProfile = async (e) => {
    e.preventDefault();
    setSavingProfile(true);
    setProfileSaveSuccess(false);
    setProfileSaveError(null);
    try {
      const updated = await apiClient('/evc/member/profile', {
        method: 'PUT',
        body: JSON.stringify(profileForm),
      });
      if (updated) {
        setProfile(updated);
        setProfileSaveSuccess(true);
        setTimeout(() => setProfileSaveSuccess(false), 4000);
      }
    } catch (err) {
      console.error('Failed to update profile:', err);
      setProfileSaveError(err.message || 'Failed to update profile settings.');
    } finally {
      setSavingProfile(false);
    }
  };

  // Filtered Inspections
  const filteredInspections = useMemo(() => {
    if (!searchQuery.trim()) return inspections;
    const q = searchQuery.toLowerCase();
    return inspections.filter(
      (i) =>
        (i.id && i.id.toLowerCase().includes(q)) ||
        (i.evcReference && i.evcReference.toLowerCase().includes(q)) ||
        (i.applicationId && i.applicationId.toLowerCase().includes(q)) ||
        (i.institutionName && i.institutionName.toLowerCase().includes(q)) ||
        (i.status && i.status.toLowerCase().includes(q)) ||
        (i.region && i.region.toLowerCase().includes(q))
    );
  }, [inspections, searchQuery]);

  // Scheduled Visits for Schedule Tab
  const scheduledVisits = useMemo(() => {
    return inspections
      .filter((i) => i.visitDate && (i.status === 'Scheduled' || i.status === 'Completed'))
      .sort((a, b) => new Date(a.visitDate) - new Date(b.visitDate));
  }, [inspections]);

  // Unique Institutions from assigned visits
  const assignedInstitutions = useMemo(() => {
    const map = new Map();
    inspections.forEach((i) => {
      if (i.institutionId || i.institutionName) {
        const key = i.institutionId || i.institutionName;
        if (!map.has(key)) {
          map.set(key, {
            id: i.institutionId || 'N/A',
            name: i.institutionName || 'Unknown Institute',
            region: i.region || 'Northern Region',
            applicationId: i.applicationId,
            inspectionRef: i.evcReference || i.id,
            inspectionType: i.inspectionType,
            status: i.status,
            visitDate: i.visitDate,
          });
        }
      }
    });
    return Array.from(map.values());
  }, [inspections]);

  // Next Upcoming Visit
  const nextVisit = useMemo(() => {
    const todayTimestamp = new Date().setHours(0, 0, 0, 0);
    const upcoming = inspections
      .filter((i) => i.visitDate && i.status === 'Scheduled' && new Date(i.visitDate).getTime() >= todayTimestamp)
      .sort((a, b) => new Date(a.visitDate) - new Date(b.visitDate));
    return upcoming.length > 0 ? upcoming[0] : null;
  }, [inspections]);

  const tabs = [
    { key: 'overview', label: 'Overview' },
    { key: 'inspections', label: `My Inspections (${inspections.length})` },
    { key: 'schedule', label: `Visit Schedule (${scheduledVisits.length})` },
    { key: 'institutes', label: `Institute Details (${assignedInstitutions.length})` },
    { key: 'reports', label: 'Scrutiny & Reports' },
    { key: 'profile', label: 'Member Profile' },
  ];

  if (loading) {
    return (
      <div style={{ display: 'grid', placeItems: 'center', minHeight: '60vh', textAlign: 'center' }}>
        <div>
          <Loader2 size={36} color="var(--color-blue)" className="animate-spin" style={{ margin: '0 auto 16px' }} />
          <h3 style={{ fontSize: '16px', fontWeight: '700', color: '#1E293B', marginBottom: '6px' }}>
            Loading EVC Member Workspace...
          </h3>
          <p style={{ fontSize: '13px', color: '#64748B' }}>
            Retrieving assigned inspection committee records and verified credentials.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="evc-member-dashboard" style={{ padding: '24px 28px', maxWidth: '1440px', margin: '0 auto' }}>
      {/* Platform Header / Member Banner */}
      <div
        style={{
          background: 'linear-gradient(135deg, var(--color-navy) 0%, #1A365D 100%)',
          color: '#FFFFFF',
          borderRadius: 'var(--radius-md)',
          padding: '24px 28px',
          marginBottom: '24px',
          boxShadow: '0 4px 12px rgba(10, 37, 64, 0.12)',
          display: 'flex',
          flexWrap: 'wrap',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: '20px',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '18px' }}>
          <div
            style={{
              width: '56px',
              height: '56px',
              borderRadius: '50%',
              backgroundColor: '#FFFFFF',
              color: 'var(--color-navy)',
              display: 'grid',
              placeItems: 'center',
              fontSize: '22px',
              fontWeight: '900',
              flexShrink: 0,
              boxShadow: '0 2px 8px rgba(0, 0, 0, 0.15)',
            }}
          >
            {profile?.fullName ? profile.fullName.charAt(0) : 'E'}
          </div>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
              <h1 style={{ fontSize: '20px', fontWeight: '800', margin: 0, letterSpacing: '-0.01em', color: '#FFFFFF' }}>
                {profile?.fullName || user?.name || 'Dr. Expert Evaluator'}
              </h1>
              <span
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '4px',
                  backgroundColor: '#059669',
                  color: '#FFFFFF',
                  padding: '2px 8px',
                  borderRadius: '12px',
                  fontSize: '11px',
                  fontWeight: '700',
                  letterSpacing: '0.04em',
                }}
              >
                <ShieldCheck size={13} /> AICTE VERIFIED EXPERT
              </span>
            </div>
            <div style={{ fontSize: '13px', color: '#CBD5E1', marginTop: '4px', display: 'flex', alignItems: 'center', gap: '14px', flexWrap: 'wrap' }}>
              <span>{profile?.designation || 'Expert Committee Evaluator'}</span>
              <span>•</span>
              <span>{profile?.organization || 'AICTE Expert Panel'}</span>
              <span>•</span>
              <span style={{ color: '#93C5FD' }}>Member ID: {profile?.memberId || 'MEM-EVCM-01'}</span>
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '12px', flexWrap: 'wrap' }}>
          <div
            style={{
              backgroundColor: 'rgba(255, 255, 255, 0.12)',
              borderRadius: 'var(--radius-sm)',
              padding: '8px 14px',
              textAlign: 'right',
            }}
          >
            <div style={{ fontSize: '10px', textTransform: 'uppercase', letterSpacing: '0.08em', color: '#94A3B8', fontWeight: '700' }}>
              Availability Status
            </div>
            <div style={{ fontSize: '13px', fontWeight: '700', color: profile?.availabilityStatus === 'AVAILABLE' ? '#34D399' : '#FCD34D' }}>
              ● {profile?.availabilityStatus || 'AVAILABLE'}
            </div>
          </div>
          <button
            onClick={fetchDashboardData}
            className="btn btn-outline-light btn-sm"
            style={{
              backgroundColor: 'rgba(255, 255, 255, 0.15)',
              border: '1px solid rgba(255, 255, 255, 0.25)',
              color: '#FFFFFF',
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              padding: '8px 12px',
              borderRadius: 'var(--radius-sm)',
              cursor: 'pointer',
              fontSize: '12px',
              fontWeight: '600',
            }}
            title="Refresh Workspace"
          >
            <RefreshCw size={14} /> Refresh
          </button>
        </div>
      </div>

      {/* Navigation Tabs matching SAGE design */}
      <div style={{ marginBottom: '22px' }}>
        <TabsNav
          tabs={tabs}
          activeTab={activeTab}
          onTabChange={handleTabChange}
        />
      </div>

      {error && (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '10px',
            backgroundColor: '#FEF2F2',
            border: '1px solid #FCA5A5',
            color: '#991B1B',
            padding: '12px 16px',
            borderRadius: 'var(--radius-sm)',
            marginBottom: '20px',
            fontSize: '13px',
          }}
        >
          <AlertTriangle size={18} />
          <span>{error}</span>
        </div>
      )}

      {/* =================================================================== */}
      {/* TAB 1: OVERVIEW */}
      {/* =================================================================== */}
      {activeTab === 'overview' && (
        <div>
          {/* KPI Summary Cards */}
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
              gap: '16px',
              marginBottom: '24px',
            }}
          >
            <KPICard
              title="Assigned Inspections"
              value={metrics.totalCases}
              subtext="Total committee assignments"
              icon="ClipboardCheck"
              color="primary"
            />
            <KPICard
              title="Upcoming Visits"
              value={metrics.upcomingVisits}
              subtext="Scheduled on-site inspections"
              icon="Calendar"
              color="info"
            />
            <KPICard
              title="Completed Inspections"
              value={metrics.completedVisits}
              subtext="Concluded site assessments"
              icon="CheckCircle"
              color="success"
            />
            <KPICard
              title="Pending Inspection Tasks"
              value={metrics.reportsPending + metrics.overdueTasks}
              subtext="Reports / actions pending"
              icon="Clock"
              color={metrics.overdueTasks > 0 ? 'critical' : 'warning'}
            />
          </div>

          {/* Next Scheduled Inspection Callout */}
          {nextVisit ? (
            <div
              style={{
                backgroundColor: '#EFF6FF',
                border: '1px solid #BFDBFE',
                borderRadius: 'var(--radius-md)',
                padding: '18px 22px',
                marginBottom: '24px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                flexWrap: 'wrap',
                gap: '16px',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
                <div
                  style={{
                    width: '44px',
                    height: '44px',
                    borderRadius: 'var(--radius-sm)',
                    backgroundColor: '#1D4ED8',
                    color: '#FFFFFF',
                    display: 'grid',
                    placeItems: 'center',
                  }}
                >
                  <Calendar size={22} />
                </div>
                <div>
                  <div style={{ fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.06em', color: '#1E40AF', fontWeight: '800' }}>
                    Next Scheduled Inspection Visit
                  </div>
                  <div style={{ fontSize: '15px', fontWeight: '700', color: '#1E293B', marginTop: '2px' }}>
                    {nextVisit.institutionName} ({nextVisit.region})
                  </div>
                  <div style={{ fontSize: '12px', color: '#475569', marginTop: '2px' }}>
                    Inspection Date: <strong>{nextVisit.visitDate}</strong> • Mode: {nextVisit.inspectionType} • Coordinator: {nextVisit.coordinator}
                  </div>
                </div>
              </div>
              <div style={{ display: 'flex', gap: '10px' }}>
                <button
                  onClick={() => {
                    setSelectedInspection(nextVisit);
                    setIsDossierModalOpen(true);
                  }}
                  className="btn btn-outline-primary btn-sm"
                  style={{
                    backgroundColor: '#FFFFFF',
                    border: '1px solid #2563EB',
                    color: '#2563EB',
                    padding: '8px 14px',
                    borderRadius: 'var(--radius-sm)',
                    fontWeight: '600',
                    cursor: 'pointer',
                  }}
                >
                  View Details
                </button>
                <button
                  onClick={() => handleViewDocuments(nextVisit)}
                  className="btn btn-primary btn-sm"
                  style={{
                    backgroundColor: '#2563EB',
                    color: '#FFFFFF',
                    border: 'none',
                    padding: '8px 14px',
                    borderRadius: 'var(--radius-sm)',
                    fontWeight: '600',
                    cursor: 'pointer',
                  }}
                >
                  Inspection Documents
                </button>
              </div>
            </div>
          ) : (
            <div
              style={{
                backgroundColor: '#F8FAFC',
                border: '1px dashed #CBD5E1',
                borderRadius: 'var(--radius-md)',
                padding: '16px 20px',
                marginBottom: '24px',
                display: 'flex',
                alignItems: 'center',
                gap: '12px',
                color: '#64748B',
                fontSize: '13px',
              }}
            >
              <Calendar size={18} color="#94A3B8" />
              <span>No immediate inspection visit scheduled for the next 7 days. Check the Visit Schedule tab for upcoming calendar dates.</span>
            </div>
          )}

          {/* Quick Active Inspections Overview */}
          <DashboardSection title="Recent Assigned Inspections" icon="ClipboardCheck">
            {inspections.length === 0 ? (
              <EmptyState
                message="No inspections are currently assigned to your committee profile. Official assignments from the AICTE EVC Coordinator will appear here."
                iconName="ClipboardCheck"
              />
            ) : (
              <div style={{ overflowX: 'auto' }}>
                <table className="table" style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px' }}>
                  <thead>
                    <tr style={{ borderBottom: '2px solid #E2E8F0', textAlign: 'left', color: '#64748B' }}>
                      <th style={{ padding: '10px 12px' }}>Inspection Ref</th>
                      <th style={{ padding: '10px 12px' }}>Institution</th>
                      <th style={{ padding: '10px 12px' }}>Application Ref</th>
                      <th style={{ padding: '10px 12px' }}>Visit Date</th>
                      <th style={{ padding: '10px 12px' }}>Status</th>
                      <th style={{ padding: '10px 12px', textAlign: 'right' }}>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {inspections.slice(0, 5).map((visit) => (
                      <tr key={visit.id} style={{ borderBottom: '1px solid #F1F5F9' }}>
                        <td style={{ padding: '12px', fontWeight: '700', color: 'var(--color-navy)' }}>
                          {visit.evcReference || visit.id}
                        </td>
                        <td style={{ padding: '12px' }}>
                          <div style={{ fontWeight: '600', color: '#1E293B' }}>{visit.institutionName}</div>
                          <div style={{ fontSize: '11px', color: '#64748B' }}>{visit.region}</div>
                        </td>
                        <td style={{ padding: '12px', color: '#475569' }}>
                          {visit.applicationId || 'N/A'}
                        </td>
                        <td style={{ padding: '12px', color: visit.visitDate ? '#1E293B' : '#94A3B8' }}>
                          {visit.visitDate || 'Awaiting Schedule'}
                        </td>
                        <td style={{ padding: '12px' }}>
                          <StatusBadge status={visit.status} />
                        </td>
                        <td style={{ padding: '12px', textAlign: 'right' }}>
                          <button
                            onClick={() => {
                              setSelectedInspection(visit);
                              setIsDossierModalOpen(true);
                            }}
                            className="btn btn-sm"
                            style={{
                              padding: '5px 10px',
                              fontSize: '12px',
                              marginRight: '6px',
                              backgroundColor: '#F1F5F9',
                              border: '1px solid #CBD5E1',
                              borderRadius: 'var(--radius-sm)',
                              cursor: 'pointer',
                            }}
                          >
                            <Eye size={12} style={{ display: 'inline', marginRight: '4px' }} /> Details
                          </button>
                          <button
                            onClick={() => handleViewDocuments(visit)}
                            className="btn btn-sm btn-primary"
                            style={{
                              padding: '5px 10px',
                              fontSize: '12px',
                              backgroundColor: 'var(--color-navy)',
                              color: '#FFFFFF',
                              border: 'none',
                              borderRadius: 'var(--radius-sm)',
                              cursor: 'pointer',
                            }}
                          >
                            <FileText size={12} style={{ display: 'inline', marginRight: '4px' }} /> Dossier
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </DashboardSection>
        </div>
      )}

      {/* =================================================================== */}
      {/* TAB 2: MY INSPECTIONS */}
      {/* =================================================================== */}
      {activeTab === 'inspections' && (
        <div>
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginBottom: '18px',
              flexWrap: 'wrap',
              gap: '12px',
            }}
          >
            <div>
              <h2 style={{ fontSize: '18px', fontWeight: '800', color: 'var(--color-navy)', margin: 0 }}>
                My Assigned Inspections
              </h2>
              <p style={{ fontSize: '13px', color: '#64748B', marginTop: '2px' }}>
                All official physical and virtual inspection visits assigned to your peer review committee.
              </p>
            </div>

            <div style={{ position: 'relative', width: '320px', maxWidth: '100%' }}>
              <Search
                size={16}
                color="#94A3B8"
                style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)' }}
              />
              <input
                type="text"
                placeholder="Search by institute, reference, region..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                style={{
                  width: '100%',
                  padding: '9px 12px 9px 36px',
                  borderRadius: 'var(--radius-sm)',
                  border: '1px solid #CBD5E1',
                  fontSize: '13px',
                  outline: 'none',
                }}
              />
            </div>
          </div>

          {filteredInspections.length === 0 ? (
            <EmptyState
              message={
                searchQuery
                  ? `No inspections matching search term "${searchQuery}".`
                  : 'No inspections are currently assigned to your profile.'
              }
              iconName="ClipboardCheck"
            />
          ) : (
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fill, minmax(380px, 1fr))',
                gap: '18px',
              }}
            >
              {filteredInspections.map((visit) => (
                <div
                  key={visit.id}
                  style={{
                    backgroundColor: '#FFFFFF',
                    border: '1px solid #E2E8F0',
                    borderRadius: 'var(--radius-md)',
                    padding: '20px',
                    boxShadow: '0 2px 6px rgba(0,0,0,0.04)',
                    display: 'flex',
                    flexDirection: 'column',
                    justifyContent: 'space-between',
                  }}
                >
                  <div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '10px' }}>
                      <span
                        style={{
                          fontSize: '11px',
                          fontWeight: '800',
                          backgroundColor: '#F1F5F9',
                          color: 'var(--color-navy)',
                          padding: '3px 8px',
                          borderRadius: 'var(--radius-sm)',
                          letterSpacing: '0.04em',
                        }}
                      >
                        {visit.evcReference || visit.id}
                      </span>
                      <StatusBadge status={visit.status} />
                    </div>

                    <h3 style={{ fontSize: '16px', fontWeight: '700', color: '#1E293B', marginBottom: '6px' }}>
                      {visit.institutionName}
                    </h3>

                    <div style={{ fontSize: '12px', color: '#64748B', display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '14px' }}>
                      <MapPin size={13} color="#94A3B8" />
                      <span>{visit.region}</span>
                    </div>

                    <div
                      style={{
                        backgroundColor: '#F8FAFC',
                        borderRadius: 'var(--radius-sm)',
                        padding: '12px',
                        fontSize: '12px',
                        marginBottom: '16px',
                        display: 'grid',
                        gridTemplateColumns: '1fr 1fr',
                        gap: '8px',
                      }}
                    >
                      <div>
                        <div style={{ color: '#94A3B8', fontSize: '10px', textTransform: 'uppercase', fontWeight: '700' }}>
                          Application Ref
                        </div>
                        <div style={{ fontWeight: '600', color: '#334155' }}>{visit.applicationId || 'N/A'}</div>
                      </div>
                      <div>
                        <div style={{ color: '#94A3B8', fontSize: '10px', textTransform: 'uppercase', fontWeight: '700' }}>
                          Inspection Mode
                        </div>
                        <div style={{ fontWeight: '600', color: '#334155' }}>{visit.inspectionType}</div>
                      </div>
                      <div>
                        <div style={{ color: '#94A3B8', fontSize: '10px', textTransform: 'uppercase', fontWeight: '700' }}>
                          Scheduled Date
                        </div>
                        <div style={{ fontWeight: '700', color: visit.visitDate ? '#1D4ED8' : '#64748B' }}>
                          {visit.visitDate || 'Unscheduled'}
                        </div>
                      </div>
                      <div>
                        <div style={{ color: '#94A3B8', fontSize: '10px', textTransform: 'uppercase', fontWeight: '700' }}>
                          Coordinator
                        </div>
                        <div style={{ fontWeight: '600', color: '#334155' }}>{visit.coordinator}</div>
                      </div>
                    </div>
                  </div>

                  <div style={{ display: 'flex', gap: '8px', paddingTop: '12px', borderTop: '1px solid #F1F5F9' }}>
                    <button
                      onClick={() => {
                        setSelectedInspection(visit);
                        setIsDossierModalOpen(true);
                      }}
                      className="btn btn-outline-secondary btn-sm"
                      style={{
                        flex: 1,
                        padding: '7px 10px',
                        fontSize: '12px',
                        borderRadius: 'var(--radius-sm)',
                        border: '1px solid #CBD5E1',
                        backgroundColor: '#FFFFFF',
                        cursor: 'pointer',
                        fontWeight: '600',
                      }}
                    >
                      View Details
                    </button>
                    <button
                      onClick={() => handleViewDocuments(visit)}
                      className="btn btn-primary btn-sm"
                      style={{
                        flex: 1,
                        padding: '7px 10px',
                        fontSize: '12px',
                        borderRadius: 'var(--radius-sm)',
                        backgroundColor: 'var(--color-navy)',
                        color: '#FFFFFF',
                        border: 'none',
                        cursor: 'pointer',
                        fontWeight: '600',
                      }}
                    >
                      Scrutiny Documents
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* =================================================================== */}
      {/* TAB 3: VISIT SCHEDULE */}
      {/* =================================================================== */}
      {activeTab === 'schedule' && (
        <div>
          <div style={{ marginBottom: '20px' }}>
            <h2 style={{ fontSize: '18px', fontWeight: '800', color: 'var(--color-navy)', margin: 0 }}>
              Official Inspection Visit Schedule
            </h2>
            <p style={{ fontSize: '13px', color: '#64748B', marginTop: '2px' }}>
              Chronological schedule of confirmed on-site and virtual visits assigned to you by AICTE operations.
            </p>
          </div>

          {scheduledVisits.length === 0 ? (
            <EmptyState
              message="No visits are currently scheduled on your inspection calendar. Inspection dates confirmed by the EVC Operations Coordinator will appear here automatically."
              iconName="Calendar"
            />
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
              {scheduledVisits.map((visit) => (
                <div
                  key={visit.id}
                  style={{
                    backgroundColor: '#FFFFFF',
                    border: '1px solid #E2E8F0',
                    borderRadius: 'var(--radius-md)',
                    padding: '18px 22px',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    flexWrap: 'wrap',
                    gap: '16px',
                    boxShadow: '0 2px 4px rgba(0,0,0,0.02)',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '18px' }}>
                    <div
                      style={{
                        backgroundColor: '#EFF6FF',
                        border: '1px solid #BFDBFE',
                        borderRadius: 'var(--radius-sm)',
                        padding: '10px 14px',
                        textAlign: 'center',
                        minWidth: '85px',
                      }}
                    >
                      <div style={{ fontSize: '11px', textTransform: 'uppercase', color: '#1D4ED8', fontWeight: '800' }}>
                        Date
                      </div>
                      <div style={{ fontSize: '14px', fontWeight: '800', color: '#1E293B', marginTop: '2px' }}>
                        {visit.visitDate}
                      </div>
                    </div>

                    <div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <span style={{ fontSize: '11px', fontWeight: '700', color: 'var(--color-navy)' }}>
                          {visit.evcReference || visit.id}
                        </span>
                        <StatusBadge status={visit.status} />
                      </div>
                      <div style={{ fontSize: '15px', fontWeight: '700', color: '#1E293B', marginTop: '3px' }}>
                        {visit.institutionName}
                      </div>
                      <div style={{ fontSize: '12px', color: '#64748B', marginTop: '2px' }}>
                        {visit.region} • {visit.inspectionType} • Coordinator: {visit.coordinator}
                      </div>
                    </div>
                  </div>

                  <div style={{ display: 'flex', gap: '8px' }}>
                    <button
                      onClick={() => {
                        setSelectedInspection(visit);
                        setIsDossierModalOpen(true);
                      }}
                      className="btn btn-outline-secondary btn-sm"
                      style={{
                        padding: '7px 12px',
                        fontSize: '12px',
                        borderRadius: 'var(--radius-sm)',
                        border: '1px solid #CBD5E1',
                        backgroundColor: '#FFFFFF',
                        cursor: 'pointer',
                        fontWeight: '600',
                      }}
                    >
                      View Details
                    </button>
                    <button
                      onClick={() => handleViewDocuments(visit)}
                      className="btn btn-primary btn-sm"
                      style={{
                        padding: '7px 12px',
                        fontSize: '12px',
                        borderRadius: 'var(--radius-sm)',
                        backgroundColor: 'var(--color-navy)',
                        color: '#FFFFFF',
                        border: 'none',
                        cursor: 'pointer',
                        fontWeight: '600',
                      }}
                    >
                      Inspection Documents
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* =================================================================== */}
      {/* TAB 4: INSTITUTE DETAILS */}
      {/* =================================================================== */}
      {activeTab === 'institutes' && (
        <div>
          <div style={{ marginBottom: '20px' }}>
            <h2 style={{ fontSize: '18px', fontWeight: '800', color: 'var(--color-navy)', margin: 0 }}>
              Assigned Technical Institutions
            </h2>
            <p style={{ fontSize: '13px', color: '#64748B', marginTop: '2px' }}>
              Institutional directories and statutory campus profiles subject to committee verification.
            </p>
          </div>

          {assignedInstitutions.length === 0 ? (
            <EmptyState
              message="No technical institutions are currently associated with your active committee assignments."
              iconName="Building2"
            />
          ) : (
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fill, minmax(360px, 1fr))',
                gap: '18px',
              }}
            >
              {assignedInstitutions.map((inst, index) => (
                <div
                  key={`${inst.id}-${index}`}
                  style={{
                    backgroundColor: '#FFFFFF',
                    border: '1px solid #E2E8F0',
                    borderRadius: 'var(--radius-md)',
                    padding: '20px',
                    boxShadow: '0 2px 4px rgba(0,0,0,0.03)',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '12px' }}>
                    <div
                      style={{
                        width: '40px',
                        height: '40px',
                        borderRadius: 'var(--radius-sm)',
                        backgroundColor: '#F1F5F9',
                        color: 'var(--color-navy)',
                        display: 'grid',
                        placeItems: 'center',
                      }}
                    >
                      <Building2 size={20} />
                    </div>
                    <div>
                      <div style={{ fontSize: '15px', fontWeight: '700', color: '#1E293B' }}>{inst.name}</div>
                      <div style={{ fontSize: '12px', color: '#64748B' }}>Code: {inst.id}</div>
                    </div>
                  </div>

                  <div
                    style={{
                      borderTop: '1px solid #F1F5F9',
                      paddingTop: '12px',
                      fontSize: '12px',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: '8px',
                      color: '#475569',
                    }}
                  >
                    <div>
                      <strong style={{ color: '#1E293B' }}>Administrative Region:</strong> {inst.region}
                    </div>
                    <div>
                      <strong style={{ color: '#1E293B' }}>Linked Application:</strong> {inst.applicationId || 'N/A'}
                    </div>
                    <div>
                      <strong style={{ color: '#1E293B' }}>Inspection Reference:</strong> {inst.inspectionRef}
                    </div>
                    <div>
                      <strong style={{ color: '#1E293B' }}>Inspection Mode:</strong> {inst.inspectionType}
                    </div>
                    <div>
                      <strong style={{ color: '#1E293B' }}>Visit Status:</strong>{' '}
                      <StatusBadge status={inst.status} />
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* =================================================================== */}
      {/* TAB 5: SCRUTINY AND DEFICIENCY REPORTS */}
      {/* =================================================================== */}
      {activeTab === 'reports' && (
        <div>
          <div style={{ marginBottom: '20px' }}>
            <h2 style={{ fontSize: '18px', fontWeight: '800', color: 'var(--color-navy)', margin: 0 }}>
              Scrutiny &amp; Statutory Deficiency Reports
            </h2>
            <p style={{ fontSize: '13px', color: '#64748B', marginTop: '2px' }}>
              Statutory documents, prior scrutiny observations, and deficiency items for authorized inspection dossiers.
            </p>
          </div>

          {/* Inspection Selector Header */}
          <div
            style={{
              backgroundColor: '#FFFFFF',
              border: '1px solid #E2E8F0',
              borderRadius: 'var(--radius-md)',
              padding: '16px 20px',
              marginBottom: '20px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              flexWrap: 'wrap',
              gap: '12px',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
              <span style={{ fontSize: '13px', fontWeight: '700', color: '#334155' }}>
                Select Inspection Dossier:
              </span>
              <select
                value={selectedDocsInspection?.id || ''}
                onChange={(e) => {
                  const found = inspections.find((i) => i.id === e.target.value);
                  if (found) handleViewDocuments(found);
                }}
                style={{
                  padding: '7px 12px',
                  borderRadius: 'var(--radius-sm)',
                  border: '1px solid #CBD5E1',
                  fontSize: '13px',
                  outline: 'none',
                  minWidth: '280px',
                }}
              >
                {inspections.map((i) => (
                  <option key={i.id} value={i.id}>
                    {i.evcReference || i.id} — {i.institutionName}
                  </option>
                ))}
              </select>
            </div>

            {selectedDocsInspection && (
              <div style={{ fontSize: '12px', color: '#64748B' }}>
                Mode: <strong>{selectedDocsInspection.inspectionType}</strong> • Status:{' '}
                <StatusBadge status={selectedDocsInspection.status} />
              </div>
            )}
          </div>

          {/* Document Content View */}
          {docsLoading ? (
            <div style={{ textAlign: 'center', padding: '40px' }}>
              <Loader2 size={28} className="animate-spin" color="var(--color-blue)" style={{ margin: '0 auto 10px' }} />
              <p style={{ fontSize: '13px', color: '#64748B' }}>Fetching verified statutory documents from secure storage...</p>
            </div>
          ) : docsError ? (
            <div
              style={{
                backgroundColor: '#FEF2F2',
                border: '1px solid #FCA5A5',
                color: '#991B1B',
                padding: '14px 18px',
                borderRadius: 'var(--radius-sm)',
                fontSize: '13px',
              }}
            >
              {docsError}
            </div>
          ) : !selectedDocsInspection ? (
            <EmptyState
              message="Please select an assigned inspection above to examine its scrutiny reports and statutory documentation."
              iconName="FileText"
            />
          ) : documents.length === 0 ? (
            <EmptyState
              message={`No statutory documents are currently uploaded for inspection dossier ${selectedDocsInspection.evcReference || selectedDocsInspection.id}.`}
              iconName="FolderArchive"
            />
          ) : (
            <div style={{ backgroundColor: '#FFFFFF', border: '1px solid #E2E8F0', borderRadius: 'var(--radius-md)', overflow: 'hidden' }}>
              <div style={{ padding: '16px 20px', borderBottom: '1px solid #E2E8F0', backgroundColor: '#F8FAFC' }}>
                <h3 style={{ fontSize: '14px', fontWeight: '800', color: 'var(--color-navy)', margin: 0 }}>
                  Statutory Application Dossier Documents ({documents.length})
                </h3>
                <p style={{ fontSize: '12px', color: '#64748B', marginTop: '2px', margin: 0 }}>
                  Documents submitted by the institution and verified during scrutiny.
                </p>
              </div>

              <div style={{ overflowX: 'auto' }}>
                <table className="table" style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px' }}>
                  <thead>
                    <tr style={{ borderBottom: '1px solid #E2E8F0', textAlign: 'left', color: '#64748B' }}>
                      <th style={{ padding: '10px 14px' }}>Document Name</th>
                      <th style={{ padding: '10px 14px' }}>Requirement Code</th>
                      <th style={{ padding: '10px 14px' }}>File Details</th>
                      <th style={{ padding: '10px 14px' }}>Verification Status</th>
                      <th style={{ padding: '10px 14px', textAlign: 'right' }}>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {documents.map((doc) => (
                      <tr key={doc.id} style={{ borderBottom: '1px solid #F1F5F9' }}>
                        <td style={{ padding: '12px 14px' }}>
                          <div style={{ fontWeight: '600', color: '#1E293B' }}>{doc.documentName || doc.originalFileName}</div>
                          <div style={{ fontSize: '11px', color: '#94A3B8' }}>{doc.originalFileName}</div>
                        </td>
                        <td style={{ padding: '12px 14px' }}>
                          <span
                            style={{
                              fontSize: '11px',
                              fontFamily: 'monospace',
                              backgroundColor: '#F1F5F9',
                              padding: '2px 6px',
                              borderRadius: '4px',
                            }}
                          >
                            {doc.requirementCode}
                          </span>
                        </td>
                        <td style={{ padding: '12px 14px', color: '#64748B', fontSize: '12px' }}>
                          {doc.fileSize ? `${Math.round(doc.fileSize / 1024)} KB` : 'N/A'} • {doc.contentType || 'PDF'}
                        </td>
                        <td style={{ padding: '12px 14px' }}>
                          <span
                            style={{
                              fontSize: '11px',
                              fontWeight: '700',
                              color: '#059669',
                              backgroundColor: '#ECFDF5',
                              padding: '2px 8px',
                              borderRadius: '12px',
                            }}
                          >
                            VERIFIED
                          </span>
                        </td>
                        <td style={{ padding: '12px 14px', textAlign: 'right' }}>
                          {doc.downloadUrl ? (
                            <a
                              href={doc.downloadUrl}
                              target="_blank"
                              rel="noreferrer"
                              className="btn btn-sm"
                              style={{
                                display: 'inline-flex',
                                alignItems: 'center',
                                gap: '4px',
                                padding: '6px 12px',
                                fontSize: '12px',
                                backgroundColor: '#EFF6FF',
                                color: '#1D4ED8',
                                border: '1px solid #BFDBFE',
                                borderRadius: 'var(--radius-sm)',
                                textDecoration: 'none',
                                fontWeight: '600',
                              }}
                            >
                              <Download size={13} /> View
                            </a>
                          ) : (
                            <span style={{ fontSize: '11px', color: '#94A3B8' }}>Available on-site</span>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      )}

      {/* =================================================================== */}
      {/* TAB 6: MEMBER PROFILE */}
      {/* =================================================================== */}
      {activeTab === 'profile' && (
        <div style={{ maxWidth: '840px' }}>
          <div style={{ marginBottom: '20px' }}>
            <h2 style={{ fontSize: '18px', fontWeight: '800', color: 'var(--color-navy)', margin: 0 }}>
              EVC Expert Profile &amp; Accreditation Details
            </h2>
            <p style={{ fontSize: '13px', color: '#64748B', marginTop: '2px' }}>
              Official verified accreditation credentials on the AICTE national expert database.
            </p>
          </div>

          {profileSaveSuccess && (
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                backgroundColor: '#ECFDF5',
                border: '1px solid #A7F3D0',
                color: '#065F46',
                padding: '12px 16px',
                borderRadius: 'var(--radius-sm)',
                marginBottom: '16px',
                fontSize: '13px',
              }}
            >
              <CheckCircle2 size={16} />
              <span>Profile availability and contact settings updated successfully.</span>
            </div>
          )}

          {profileSaveError && (
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                backgroundColor: '#FEF2F2',
                border: '1px solid #FCA5A5',
                color: '#991B1B',
                padding: '12px 16px',
                borderRadius: 'var(--radius-sm)',
                marginBottom: '16px',
                fontSize: '13px',
              }}
            >
              <AlertTriangle size={16} />
              <span>{profileSaveError}</span>
            </div>
          )}

          <div
            style={{
              backgroundColor: '#FFFFFF',
              border: '1px solid #E2E8F0',
              borderRadius: 'var(--radius-md)',
              padding: '24px',
              boxShadow: '0 2px 6px rgba(0,0,0,0.03)',
            }}
          >
            {/* Read-Only Governance Credentials */}
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
                gap: '16px',
                paddingBottom: '20px',
                marginBottom: '20px',
                borderBottom: '1px solid #E2E8F0',
              }}
            >
              <div>
                <div style={{ fontSize: '11px', textTransform: 'uppercase', color: '#64748B', fontWeight: '700' }}>
                  Member Identifier
                </div>
                <div style={{ fontSize: '14px', fontWeight: '700', color: 'var(--color-navy)', marginTop: '2px' }}>
                  {profile?.memberId || 'MEM-EVCM-01'}
                </div>
              </div>

              <div>
                <div style={{ fontSize: '11px', textTransform: 'uppercase', color: '#64748B', fontWeight: '700' }}>
                  User Account ID
                </div>
                <div style={{ fontSize: '14px', fontWeight: '700', color: '#1E293B', marginTop: '2px' }}>
                  {profile?.userId || user?.id || 'USR-EVCM-01'}
                </div>
              </div>

              <div>
                <div style={{ fontSize: '11px', textTransform: 'uppercase', color: '#64748B', fontWeight: '700' }}>
                  Full Name
                </div>
                <div style={{ fontSize: '14px', fontWeight: '700', color: '#1E293B', marginTop: '2px' }}>
                  {profile?.fullName || user?.name || 'Dr. Arvind Saxena'}
                </div>
              </div>

              <div>
                <div style={{ fontSize: '11px', textTransform: 'uppercase', color: '#64748B', fontWeight: '700' }}>
                  Official Email
                </div>
                <div style={{ fontSize: '14px', fontWeight: '600', color: '#1E293B', marginTop: '2px' }}>
                  {profile?.email || user?.email || 'member.evc@aicte-india.gov.in'}
                </div>
              </div>

              <div>
                <div style={{ fontSize: '11px', textTransform: 'uppercase', color: '#64748B', fontWeight: '700' }}>
                  Professional Designation
                </div>
                <div style={{ fontSize: '14px', fontWeight: '600', color: '#1E293B', marginTop: '2px' }}>
                  {profile?.designation || 'Professor & Subject Specialist'}
                </div>
              </div>

              <div>
                <div style={{ fontSize: '11px', textTransform: 'uppercase', color: '#64748B', fontWeight: '700' }}>
                  Parent Organization / Affiliation
                </div>
                <div style={{ fontSize: '14px', fontWeight: '600', color: '#1E293B', marginTop: '2px' }}>
                  {profile?.organization || 'Indian Institute of Technology Delhi'}
                </div>
              </div>

              <div style={{ gridColumn: '1 / -1' }}>
                <div style={{ fontSize: '11px', textTransform: 'uppercase', color: '#64748B', fontWeight: '700' }}>
                  Academic Discipline &amp; Technical Expertise
                </div>
                <div style={{ fontSize: '14px', fontWeight: '600', color: '#1E293B', marginTop: '2px' }}>
                  {profile?.expertise || 'Computer Science & Engineering, Artificial Intelligence, High Performance Computing'}
                </div>
              </div>

              <div>
                <div style={{ fontSize: '11px', textTransform: 'uppercase', color: '#64748B', fontWeight: '700' }}>
                  AICTE Verification Status
                </div>
                <div style={{ marginTop: '4px' }}>
                  <span
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '4px',
                      backgroundColor: '#DEF7EC',
                      color: '#03543F',
                      padding: '3px 8px',
                      borderRadius: '12px',
                      fontSize: '11px',
                      fontWeight: '700',
                    }}
                  >
                    <ShieldCheck size={13} /> {profile?.verificationStatus || 'VERIFIED'}
                  </span>
                </div>
              </div>

              <div>
                <div style={{ fontSize: '11px', textTransform: 'uppercase', color: '#64748B', fontWeight: '700' }}>
                  Account Status
                </div>
                <div style={{ marginTop: '4px' }}>
                  <span
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '4px',
                      backgroundColor: '#E1EFFE',
                      color: '#1E429F',
                      padding: '3px 8px',
                      borderRadius: '12px',
                      fontSize: '11px',
                      fontWeight: '700',
                    }}
                  >
                    ● {profile?.status || 'ACTIVE'}
                  </span>
                </div>
              </div>
            </div>

            {/* Self-Editable Permitted Settings */}
            <form onSubmit={handleSaveProfile}>
              <h3 style={{ fontSize: '14px', fontWeight: '800', color: 'var(--color-navy)', marginBottom: '14px' }}>
                Self-Service Availability &amp; Contact Settings
              </h3>

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: '16px', marginBottom: '20px' }}>
                <div>
                  <label style={{ display: 'block', fontSize: '12px', fontWeight: '700', color: '#334155', marginBottom: '6px' }}>
                    Availability Status for EVC Committee Roster
                  </label>
                  <select
                    value={profileForm.availabilityStatus}
                    onChange={(e) => setProfileForm({ ...profileForm, availabilityStatus: e.target.value })}
                    style={{
                      width: '100%',
                      padding: '9px 12px',
                      borderRadius: 'var(--radius-sm)',
                      border: '1px solid #CBD5E1',
                      fontSize: '13px',
                      outline: 'none',
                    }}
                  >
                    <option value="AVAILABLE">AVAILABLE — Open for Inspection Visit Scheduling</option>
                    <option value="UNAVAILABLE">UNAVAILABLE — Temporarily Unavailable for Visits</option>
                    <option value="ON_LEAVE">ON_LEAVE — Statutory Academic Leave</option>
                  </select>
                </div>

                <div>
                  <label style={{ display: 'block', fontSize: '12px', fontWeight: '700', color: '#334155', marginBottom: '6px' }}>
                    Official Contact Phone
                  </label>
                  <input
                    type="text"
                    value={profileForm.phone}
                    onChange={(e) => setProfileForm({ ...profileForm, phone: e.target.value })}
                    placeholder="+91 98765 43210"
                    style={{
                      width: '100%',
                      padding: '9px 12px',
                      borderRadius: 'var(--radius-sm)',
                      border: '1px solid #CBD5E1',
                      fontSize: '13px',
                      outline: 'none',
                    }}
                  />
                </div>
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                <button
                  type="submit"
                  disabled={savingProfile}
                  className="btn btn-primary"
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '6px',
                    padding: '9px 20px',
                    backgroundColor: 'var(--color-navy)',
                    color: '#FFFFFF',
                    border: 'none',
                    borderRadius: 'var(--radius-sm)',
                    fontWeight: '700',
                    fontSize: '13px',
                    cursor: savingProfile ? 'not-allowed' : 'pointer',
                  }}
                >
                  {savingProfile ? (
                    <>
                      <Loader2 size={15} className="animate-spin" /> Saving...
                    </>
                  ) : (
                    <>
                      <Save size={15} /> Save Profile Changes
                    </>
                  )}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* =================================================================== */}
      {/* INSPECTION DOSSIER MODAL */}
      {/* =================================================================== */}
      {isDossierModalOpen && selectedInspection && (
        <Modal
          title={`EVC Inspection Dossier — ${selectedInspection.evcReference || selectedInspection.id}`}
          isOpen={isDossierModalOpen}
          onClose={() => {
            setIsDossierModalOpen(false);
            setSelectedInspection(null);
          }}
        >
          <div style={{ fontSize: '13px', color: '#334155' }}>
            <div style={{ marginBottom: '16px' }}>
              <div style={{ fontSize: '16px', fontWeight: '700', color: 'var(--color-navy)' }}>
                {selectedInspection.institutionName}
              </div>
              <div style={{ fontSize: '12px', color: '#64748B', marginTop: '2px' }}>
                {selectedInspection.region} • Application Ref: {selectedInspection.applicationId || 'N/A'}
              </div>
            </div>

            <div
              style={{
                backgroundColor: '#F8FAFC',
                border: '1px solid #E2E8F0',
                borderRadius: 'var(--radius-sm)',
                padding: '14px',
                display: 'grid',
                gridTemplateColumns: '1fr 1fr',
                gap: '12px',
                marginBottom: '18px',
              }}
            >
              <div>
                <div style={{ fontSize: '11px', textTransform: 'uppercase', color: '#64748B', fontWeight: '700' }}>
                  Inspection Mode
                </div>
                <div style={{ fontWeight: '600', marginTop: '2px' }}>{selectedInspection.inspectionType}</div>
              </div>

              <div>
                <div style={{ fontSize: '11px', textTransform: 'uppercase', color: '#64748B', fontWeight: '700' }}>
                  Current Status
                </div>
                <div style={{ marginTop: '2px' }}>
                  <StatusBadge status={selectedInspection.status} />
                </div>
              </div>

              <div>
                <div style={{ fontSize: '11px', textTransform: 'uppercase', color: '#64748B', fontWeight: '700' }}>
                  Confirmed Visit Date
                </div>
                <div style={{ fontWeight: '700', color: '#1D4ED8', marginTop: '2px' }}>
                  {selectedInspection.visitDate || 'Awaiting scheduling'}
                </div>
              </div>

              <div>
                <div style={{ fontSize: '11px', textTransform: 'uppercase', color: '#64748B', fontWeight: '700' }}>
                  Compliance Deadline
                </div>
                <div style={{ fontWeight: '600', marginTop: '2px' }}>
                  {selectedInspection.deadline || 'Statutory AICTE Window'}
                </div>
              </div>

              <div style={{ gridColumn: '1 / -1' }}>
                <div style={{ fontSize: '11px', textTransform: 'uppercase', color: '#64748B', fontWeight: '700' }}>
                  Assigned Operations Coordinator
                </div>
                <div style={{ fontWeight: '600', marginTop: '2px' }}>
                  {selectedInspection.coordinator} ({selectedInspection.coordinatorId || 'USR-EVC-01'})
                </div>
              </div>
            </div>

            {selectedInspection.completionNotes && (
              <div style={{ marginBottom: '16px' }}>
                <div style={{ fontSize: '12px', fontWeight: '700', color: '#1E293B', marginBottom: '4px' }}>
                  Inspection Completion Notes:
                </div>
                <div style={{ backgroundColor: '#F1F5F9', padding: '10px 12px', borderRadius: '4px', fontSize: '12px' }}>
                  {selectedInspection.completionNotes}
                </div>
              </div>
            )}

            {selectedInspection.reportOutcome && (
              <div style={{ marginBottom: '16px' }}>
                <div style={{ fontSize: '12px', fontWeight: '700', color: '#1E293B', marginBottom: '4px' }}>
                  Committee Recommendation Outcome:
                </div>
                <div
                  style={{
                    backgroundColor: '#ECFDF5',
                    color: '#065F46',
                    padding: '10px 12px',
                    borderRadius: '4px',
                    fontSize: '12px',
                    fontWeight: '700',
                  }}
                >
                  {selectedInspection.reportOutcome}
                </div>
              </div>
            )}

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '20px' }}>
              <button
                onClick={() => {
                  const visit = selectedInspection;
                  setIsDossierModalOpen(false);
                  handleViewDocuments(visit);
                }}
                className="btn btn-primary btn-sm"
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px',
                  backgroundColor: 'var(--color-navy)',
                  color: '#FFFFFF',
                  padding: '8px 16px',
                  borderRadius: 'var(--radius-sm)',
                  border: 'none',
                  fontWeight: '600',
                  cursor: 'pointer',
                }}
              >
                <FileText size={14} /> Open Scrutiny Documents
              </button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
};

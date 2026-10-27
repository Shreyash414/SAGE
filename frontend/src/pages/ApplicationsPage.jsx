// SIH 26262 - Application Explorer Module (/applications)

import React, { useState, useMemo, useEffect, useCallback, useRef } from 'react';
import { Modal } from '../components/common/Modal';
import { apiClient } from '../services/apiClient';
import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useData } from '../context/DataContext';
import { ROLES } from '../constants/roles';
import { StatusBadge } from '../components/common/StatusBadge';
import { EmptyState } from '../components/common/EmptyState';
import {
  Search,
  Filter,
  RotateCcw,
  ArrowUpDown,
  ChevronLeft,
  ChevronRight,
  Eye,
  FileText,
  SlidersHorizontal,
  Plus,
  AlertCircle,
  CheckCircle2,
  Loader2,
  Trash2,
  Send,
} from 'lucide-react';

export const ApplicationsPage = () => {
  const { user, role } = useAuth();
  const { applications, fetchApplicationsFromBackend, loading, error } = useData();
  const location = useLocation();

  // Initial filter overrides from location state (e.g. from KPICard clicks)
  const initialStatus = location.state?.status || 'All';
  const initialStage = location.state?.stage || 'All';

  // State for search and filters
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedRegion, setSelectedRegion] = useState(
    role === ROLES.REGIONAL_OFFICER ? user?.region || 'Northern Region' : 'All'
  );
  const [selectedStage, setSelectedStage] = useState(initialStage);
  const [selectedType, setSelectedType] = useState('All');
  const [selectedStatus, setSelectedStatus] = useState(initialStatus);
  const [selectedOfficer, setSelectedOfficer] = useState('All');

  // Sorting: 'asc' | 'desc'
  const [sortDeadlineOrder, setSortDeadlineOrder] = useState('asc');

  // Promotion Success Banner State
  const [promotionSuccess, setPromotionSuccess] = useState(
    location.state?.promotedAppRef
      ? {
          reference: location.state.promotedAppRef,
          message:
            location.state.successMessage ||
            `Application ${location.state.promotedAppRef} successfully submitted and registered for AICTE scrutiny.`,
        }
      : null
  );

  useEffect(() => {
    if (location.state?.promotedAppRef) {
      setPromotionSuccess({
        reference: location.state.promotedAppRef,
        message:
          location.state.successMessage ||
          `Application ${location.state.promotedAppRef} successfully submitted and registered for AICTE scrutiny.`,
      });
      // Clear location state history so it doesn't re-trigger on subsequent internal re-renders
      window.history.replaceState({}, document.title);
    }
  }, [location.state]);

  // Pending Drafts State (strictly institution-scoped from /api/v1/application-drafts)
  const [pendingDrafts, setPendingDrafts] = useState([]);
  const [loadingDrafts, setLoadingDrafts] = useState(false);
  const [promotingDraftId, setPromotingDraftId] = useState(null);
  const latestDraftsRef = useRef(0);

  const fetchDrafts = useCallback(async () => {
    if (role !== ROLES.INSTITUTION_REP) return;
    const fetchId = Date.now();
    latestDraftsRef.current = fetchId;
    setLoadingDrafts(true);
    try {
      const data = await apiClient(`/application-drafts?_t=${fetchId}`);
      if (fetchId < latestDraftsRef.current) return;
      setPendingDrafts(Array.isArray(data) ? data : []);
    } catch (err) {
      if (fetchId < latestDraftsRef.current) return;
      console.warn('Could not fetch institution drafts:', err);
    } finally {
      if (fetchId >= latestDraftsRef.current) {
        setLoadingDrafts(false);
      }
    }
  }, [role]);

  // Synchronize both official applications and pending drafts on mount and navigation
  useEffect(() => {
    fetchApplicationsFromBackend();
    fetchDrafts();
  }, [fetchApplicationsFromBackend, fetchDrafts, location.key]);

  const handlePromoteDraft = async (draftId, draftRef) => {
    setPromotingDraftId(draftId);
    try {
      const res = await apiClient(`/application-drafts/${draftId}/promote`, {
        method: 'POST',
      });

      // 1. Immediately drop promoted draft from pendingDrafts list (no browser refresh needed)
      setPendingDrafts((prev) => prev.filter((d) => d.id !== draftId && d.draftReference !== draftRef));

      // 2. Refresh official applications and pending drafts from backend
      await Promise.all([
        fetchApplicationsFromBackend({ silent: true }),
        fetchDrafts(),
      ]);

      // 3. Display success notification containing official application reference
      const officialRef =
        res.officialApplicationReference ||
        res.applicationReference ||
        (res.officialApplicationId || res.applicationId ? `APP-${res.officialApplicationId || res.applicationId}` : 'APP-SUBMITTED');

      setPromotionSuccess({
        reference: officialRef,
        message: `Draft ${draftRef} was successfully promoted to official application ${officialRef}.`,
      });
      setCurrentPage(1);
    } catch (err) {
      console.error('Failed to promote draft:', err);
      alert(`Promotion failed: ${err.message || 'Please ensure all mandatory requirements are satisfied.'}`);
    } finally {
      setPromotingDraftId(null);
    }
  };

  const handleDeleteDraft = async (draftId, draftRef) => {
    if (!window.confirm(`Are you sure you want to discard draft ${draftRef}? All staged document attachments will be deleted.`)) {
      return;
    }
    try {
      await apiClient(`/application-drafts/${draftId}`, { method: 'DELETE' });
      fetchDrafts();
    } catch (err) {
      alert(`Failed to delete draft: ${err.message}`);
    }
  };

  // Create Application Modal State (Institution Representative Only)
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [institutionsList, setInstitutionsList] = useState([]);
  const [isLoadingInstitutions, setIsLoadingInstitutions] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [createdAppResult, setCreatedAppResult] = useState(null);
  const [apiError, setApiError] = useState(null);
  const [fieldErrors, setFieldErrors] = useState({});

  const [formInstitutionId, setFormInstitutionId] = useState('');
  const [formAppReference, setFormAppReference] = useState('');
  const [formAppType, setFormAppType] = useState('Extension of Approval (EoA)');
  const [formAcademicYear, setFormAcademicYear] = useState('2026-2027');

  // Fetch canonical institutions directory when modal opens
  useEffect(() => {
    if (isCreateModalOpen) {
      setIsLoadingInstitutions(true);
      setApiError(null);
      setFieldErrors({});
      setCreatedAppResult(null);

      // Generate suggested unique reference
      const randomSuffix = Math.floor(1000 + Math.random() * 9000);
      setFormAppReference(`APP-2026-${randomSuffix}`);

      apiClient('/institutions')
        .then((data) => {
          const items = data?.content || (Array.isArray(data) ? data : []);
          setInstitutionsList(items);

          if (items.length > 0) {
            // Match current logged-in user's institutionIdentifier if present
            const matched = items.find((inst) => inst.institutionalIdentifier === user?.institutionId);
            if (matched) {
              setFormInstitutionId(String(matched.id));
            } else {
              setFormInstitutionId(String(items[0].id));
            }
          }
        })
        .catch((err) => {
          console.error('Failed to load institutions:', err);
          setApiError('Unable to load canonical institutions directory from backend.');
        })
        .finally(() => {
          setIsLoadingInstitutions(false);
        });
    }
  }, [isCreateModalOpen, user]);

  const handleCreateSubmit = async (e) => {
    e.preventDefault();
    setApiError(null);
    setFieldErrors({});

    const errors = {};
    if (!formInstitutionId) {
      errors.institutionId = 'Please select a canonical institution.';
    }
    if (!formAppReference.trim()) {
      errors.applicationReference = 'Application reference is required.';
    }
    if (!formAppType.trim()) {
      errors.applicationType = 'Application type is required.';
    }
    if (!formAcademicYear.trim()) {
      errors.academicYear = 'Academic year is required.';
    }

    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }

    setIsSubmitting(true);
    try {
      const payload = {
        institutionId: Number(formInstitutionId),
        applicationReference: formAppReference.trim(),
        applicationType: formAppType.trim(),
        academicYear: formAcademicYear.trim(),
      };

      const response = await apiClient('/applications', {
        method: 'POST',
        body: JSON.stringify(payload),
      });

      // Synchronize application list from backend API
      if (fetchApplicationsFromBackend) {
        await fetchApplicationsFromBackend();
      }

      setCreatedAppResult(response);
    } catch (err) {
      console.error('Create application error:', err);
      if (err.data?.invalidFields) {
        setFieldErrors(err.data.invalidFields);
      }
      setApiError(err.data?.detail || err.message || 'Failed to register application on server.');
    } finally {
      setIsSubmitting(false);
    }
  };

  // Pagination
  const [currentPage, setCurrentPage] = useState(1);
  const pageSize = 5;

  // 1. Role-based scoping
  const baseScopedApps = useMemo(() => {
    if (role === ROLES.INSTITUTION_REP) {
      if (user?.institutionId) {
        return applications.filter((app) => app.institutionId === user.institutionId || app.institutionName === user?.institutionName);
      }
      if (user?.institutionName) {
        return applications.filter((app) => app.institutionName === user.institutionName);
      }
      return applications;
    }
    return applications;
  }, [applications, role, user]);

  // Extract distinct filter values
  const uniqueStages = useMemo(() => ['All', ...new Set(baseScopedApps.map((a) => a.currentStage))], [baseScopedApps]);
  const uniqueRegions = useMemo(() => ['All', ...new Set(baseScopedApps.map((a) => a.region))], [baseScopedApps]);
  const uniqueTypes = useMemo(() => ['All', ...new Set(baseScopedApps.map((a) => a.applicationType))], [baseScopedApps]);
  const uniqueStatuses = useMemo(() => ['All', 'Draft', 'In progress', 'Due soon', 'Overdue', 'Returned', 'Completed'], []);
  const uniqueOfficers = useMemo(() => ['All', ...new Set(baseScopedApps.map((a) => a.owner))], [baseScopedApps]);

  // 2. Apply filters
  const filteredApps = useMemo(() => {
    return baseScopedApps.filter((app) => {
      // Search by ID or Institution
      if (searchTerm.trim() !== '') {
        const q = searchTerm.toLowerCase();
        const matchesId = app.id.toLowerCase().includes(q);
        const matchesInst = app.institutionName.toLowerCase().includes(q);
        if (!matchesId && !matchesInst) return false;
      }

      // Region Filter
      if (selectedRegion !== 'All' && app.region !== selectedRegion) {
        return false;
      }

      // Stage Filter
      if (selectedStage !== 'All' && app.currentStage !== selectedStage) {
        return false;
      }

      // Type Filter
      if (selectedType !== 'All' && app.applicationType !== selectedType) {
        return false;
      }

      // Status Filter
      if (selectedStatus !== 'All' && app.status !== selectedStatus) {
        return false;
      }

      // Officer Filter
      if (selectedOfficer !== 'All' && app.owner !== selectedOfficer) {
        return false;
      }

      return true;
    });
  }, [baseScopedApps, searchTerm, selectedRegion, selectedStage, selectedType, selectedStatus, selectedOfficer]);

  // 3. Sort by deadline
  const sortedApps = useMemo(() => {
    return [...filteredApps].sort((a, b) => {
      const dateA = new Date(a.dueDate || '9999-12-31').getTime();
      const dateB = new Date(b.dueDate || '9999-12-31').getTime();
      return sortDeadlineOrder === 'asc' ? dateA - dateB : dateB - dateA;
    });
  }, [filteredApps, sortDeadlineOrder]);

  // 4. Pagination slicing
  const totalRecords = sortedApps.length;
  const totalPages = Math.ceil(totalRecords / pageSize) || 1;
  const paginatedApps = useMemo(() => {
    const start = (currentPage - 1) * pageSize;
    return sortedApps.slice(start, start + pageSize);
  }, [sortedApps, currentPage, pageSize]);

  // Handle Reset Filters
  const handleResetFilters = () => {
    setSearchTerm('');
    setSelectedRegion(role === ROLES.REGIONAL_OFFICER ? user?.region || 'Northern Region' : 'All');
    setSelectedStage('All');
    setSelectedType('All');
    setSelectedStatus('All');
    setSelectedOfficer('All');
    setSortDeadlineOrder('asc');
    setCurrentPage(1);
  };

  const toggleSortDeadline = () => {
    setSortDeadlineOrder((prev) => (prev === 'asc' ? 'desc' : 'asc'));
  };

  return (
    <div>
      {/* Page Heading */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '16px', flexWrap: 'wrap', gap: '12px' }}>
        <div>
          <div style={{ fontSize: '10px', textTransform: 'uppercase', letterSpacing: '0.08em', color: 'var(--color-teal)', fontWeight: '800' }}>
            APPROVAL PROCESS HANDBOOK (APH) · DOSSIER EXPLORER
          </div>
          <h1 style={{ fontSize: '22px', fontWeight: '800', color: 'var(--color-navy)', margin: '4px 0 2px' }}>
            Application Explorer
          </h1>
          <p style={{ color: 'var(--color-text-muted)', fontSize: 'var(--font-size-sm)' }}>
            Search, filter, and inspect institutional approval applications within your authorized access scope.
          </p>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          {role === ROLES.INSTITUTION_REP && (
            <Link
              to="/applications/new"
              id="btnNewApplication"
              className="btn btn-primary btn-sm"
              style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
            >
              <Plus size={14} /> New Application
            </Link>
          )}
          <span className="badge badge-blue">
            Scope: {role === ROLES.INSTITUTION_REP ? user?.institutionName : role}
          </span>
          <button onClick={handleResetFilters} className="btn btn-outline btn-sm">
            <RotateCcw size={13} /> Reset Filters
          </button>
        </div>
      </div>

      {/* Promotion Success Notification Banner */}
      {promotionSuccess && (
        <div
          id="promotionSuccessBanner"
          style={{
            background: '#F6FFED',
            border: '1px solid #B7EB8F',
            borderRadius: '8px',
            padding: '12px 18px',
            marginBottom: '16px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            boxShadow: '0 2px 8px rgba(82, 196, 26, 0.12)',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <CheckCircle2 size={22} color="#52C41A" style={{ flexShrink: 0 }} />
            <div>
              <div style={{ fontWeight: '800', color: '#135200', fontSize: '14px' }}>
                Promotion &amp; Submission Successful!
              </div>
              <div style={{ color: '#237804', fontSize: '13px', marginTop: '2px' }}>
                Application <strong style={{ color: '#092B00', textDecoration: 'underline' }}>{promotionSuccess.reference}</strong> has been officially created and queued for technical scrutiny.
              </div>
            </div>
          </div>
          <button
            id="btnDismissPromotionBanner"
            onClick={() => setPromotionSuccess(null)}
            className="btn btn-outline btn-sm"
            style={{ borderColor: '#B7EB8F', color: '#389E0D', background: '#FFFFFF' }}
          >
            Dismiss
          </button>
        </div>
      )}

      {/* Pending Draft Applications Panel (Strictly Institution-Isolated) */}
      {role === ROLES.INSTITUTION_REP && (
        <div className="panel" style={{ marginBottom: '16px', padding: '16px', background: '#FAFCFF', border: '1px solid #D6E4FF' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px', flexWrap: 'wrap', gap: '8px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <div style={{ width: '8px', height: '8px', borderRadius: '50%', background: '#1677FF' }} />
              <h2 style={{ fontSize: '15px', fontWeight: '800', color: 'var(--color-navy)', margin: 0 }}>
                Pending Draft Applications (In Progress)
              </h2>
              <span className="badge badge-blue">
                {pendingDrafts.length} {pendingDrafts.length === 1 ? 'Draft' : 'Drafts'}
              </span>
            </div>
            <Link to="/applications/new" className="btn btn-outline btn-sm" style={{ gap: '4px' }}>
              <Plus size={12} /> Start Another Application
            </Link>
          </div>

          {loadingDrafts ? (
            <div style={{ padding: '16px', textAlign: 'center', color: 'var(--color-navy)', fontSize: '12px' }}>
              <Loader2 size={16} className="animate-spin" style={{ margin: '0 auto 6px', display: 'block' }} />
              Loading saved drafts from server...
            </div>
          ) : pendingDrafts.length === 0 ? (
            <div style={{ padding: '14px', background: '#FFFFFF', borderRadius: '6px', border: '1px dashed #ADC6FF', textAlign: 'center', color: 'var(--color-text-muted)', fontSize: '12px' }}>
              No in-progress drafts. When you save an application as a draft, your form inputs and uploaded MinIO documents are safely preserved here.
            </div>
          ) : (
            <div className="table-container" style={{ border: '1px solid #E2E8F0', borderRadius: '6px', background: '#FFFFFF' }}>
              <table className="gov-table" style={{ margin: 0 }}>
                <thead>
                  <tr>
                    <th>Draft Reference</th>
                    <th>Application Type</th>
                    <th>Academic Year</th>
                    <th>Requirement Progress</th>
                    <th>Readiness Status</th>
                    <th>Last Saved</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {pendingDrafts.map((d) => (
                    <tr key={d.id}>
                      <td>
                        <strong style={{ color: 'var(--color-navy)' }}>{d.draftReference}</strong>
                        <div style={{ fontSize: '10px', color: 'var(--color-text-muted)' }}>ID: {d.id}</div>
                      </td>
                      <td style={{ fontSize: '12px', fontWeight: '600' }}>
                        {d.applicationType}
                      </td>
                      <td style={{ fontSize: '12px' }}>{d.academicYear}</td>
                      <td style={{ minWidth: '180px' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '11px', fontWeight: '700', marginBottom: '3px' }}>
                          <span>{d.completionPercentage}% Complete</span>
                          <span style={{ fontSize: '10px', color: 'var(--color-text-muted)' }}>
                            Form: {d.formCompletionPercentage}% · Docs: {d.documentCompletionPercentage}%
                          </span>
                        </div>
                        <div style={{ height: '6px', background: '#F0F0F0', borderRadius: '3px', overflow: 'hidden' }}>
                          <div
                            style={{
                              height: '100%',
                              width: `${d.completionPercentage}%`,
                              background: d.submittable ? '#52C41A' : '#1677FF',
                              borderRadius: '3px',
                              transition: 'width 0.3s ease',
                            }}
                          />
                        </div>
                      </td>
                      <td>
                        {d.submittable ? (
                          <span className="badge badge-green">Ready to Submit</span>
                        ) : (
                          <span className="badge badge-amber">Requirements Incomplete</span>
                        )}
                      </td>
                      <td style={{ fontSize: '11px', color: 'var(--color-text-muted)' }}>
                        {new Date(d.updatedAt).toLocaleString()}
                      </td>
                      <td>
                        <div style={{ display: 'flex', gap: '6px' }}>
                          {d.submittable && (
                            <button
                              id={`btnPromoteDraft-${d.id}`}
                              onClick={() => handlePromoteDraft(d.id, d.draftReference)}
                              disabled={promotingDraftId === d.id}
                              className="btn btn-primary btn-sm"
                              style={{ gap: '4px', background: '#52C41A', borderColor: '#52C41A', whiteSpace: 'nowrap' }}
                              title="Promote and formally submit complete draft"
                            >
                              {promotingDraftId === d.id ? (
                                <Loader2 size={12} className="animate-spin" />
                              ) : (
                                <Send size={12} />
                              )}
                              Submit
                            </button>
                          )}
                          <Link
                            to={`/applications/new?draftId=${d.id}`}
                            className="btn btn-primary btn-sm"
                            style={{ gap: '4px', whiteSpace: 'nowrap' }}
                            title="Resume draft and continue editing"
                          >
                            <RotateCcw size={12} /> Resume
                          </Link>
                          <button
                            onClick={() => handleDeleteDraft(d.id, d.draftReference)}
                            className="btn btn-outline btn-sm"
                            style={{ color: '#CF1322', borderColor: '#FFA39E', gap: '4px' }}
                            title="Discard unsubmitted draft and staged documents"
                          >
                            <Trash2 size={12} />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Filter Toolbar Panel */}
      <div className="panel" style={{ marginBottom: '16px', padding: '14px', background: '#FCFDFE' }}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '10px' }}>
          {/* Search Input */}
          <div style={{ gridColumn: 'span 2' }}>
            <label className="form-label" htmlFor="appSearchInput">
              Search by ID or Institution
            </label>
            <div style={{ position: 'relative' }}>
              <input
                id="appSearchInput"
                type="text"
                className="form-input"
                placeholder="e.g. APP-2026-1042 or ABC Institute..."
                value={searchTerm}
                onChange={(e) => {
                  setSearchTerm(e.target.value);
                  setCurrentPage(1);
                }}
                style={{ paddingLeft: '32px' }}
              />
              <Search size={15} style={{ position: 'absolute', left: '10px', top: '10px', color: '#899BAA' }} />
            </div>
          </div>

          {/* Region Filter */}
          {role !== ROLES.INSTITUTION_REP && (
            <div>
              <label className="form-label" htmlFor="regionFilter">
                Region
              </label>
              <select
                id="regionFilter"
                className="form-select"
                value={selectedRegion}
                onChange={(e) => {
                  setSelectedRegion(e.target.value);
                  setCurrentPage(1);
                }}
              >
                {uniqueRegions.map((reg) => (
                  <option key={reg} value={reg}>
                    {reg}
                  </option>
                ))}
              </select>
            </div>
          )}

          {/* Workflow Stage Filter */}
          <div>
            <label className="form-label" htmlFor="stageFilter">
              Workflow Stage
            </label>
            <select
              id="stageFilter"
              className="form-select"
              value={selectedStage}
              onChange={(e) => {
                setSelectedStage(e.target.value);
                setCurrentPage(1);
              }}
            >
              {uniqueStages.map((stg) => (
                <option key={stg} value={stg}>
                  {stg}
                </option>
              ))}
            </select>
          </div>

          {/* Application Type Filter */}
          <div>
            <label className="form-label" htmlFor="typeFilter">
              Application Type
            </label>
            <select
              id="typeFilter"
              className="form-select"
              value={selectedType}
              onChange={(e) => {
                setSelectedType(e.target.value);
                setCurrentPage(1);
              }}
            >
              {uniqueTypes.map((typ) => (
                <option key={typ} value={typ}>
                  {typ}
                </option>
              ))}
            </select>
          </div>

          {/* Overdue / Status Filter */}
          <div>
            <label className="form-label" htmlFor="statusFilter">
              SLA / Status
            </label>
            <select
              id="statusFilter"
              className="form-select"
              value={selectedStatus}
              onChange={(e) => {
                setSelectedStatus(e.target.value);
                setCurrentPage(1);
              }}
            >
              {uniqueStatuses.map((st) => (
                <option key={st} value={st}>
                  {st}
                </option>
              ))}
            </select>
          </div>

          {/* Assigned Officer Filter */}
          {role !== ROLES.INSTITUTION_REP && (
            <div>
              <label className="form-label" htmlFor="officerFilter">
                Assigned Desk Owner
              </label>
              <select
                id="officerFilter"
                className="form-select"
                value={selectedOfficer}
                onChange={(e) => {
                  setSelectedOfficer(e.target.value);
                  setCurrentPage(1);
                }}
              >
                {uniqueOfficers.map((off) => (
                  <option key={off} value={off}>
                    {off}
                  </option>
                ))}
              </select>
            </div>
          )}
        </div>
      </div>

      {/* Main Table Panel */}
      <div className="panel">
        <div className="panel-header">
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <span style={{ fontSize: 'var(--font-size-sm)', fontWeight: '700', color: 'var(--color-navy)' }}>
              Showing {totalRecords === 0 ? 0 : (currentPage - 1) * pageSize + 1} to{' '}
              {Math.min(currentPage * pageSize, totalRecords)} of {totalRecords} Records
            </span>
            {(searchTerm || selectedStage !== 'All' || selectedStatus !== 'All') && (
              <span className="badge badge-amber">Filters Applied</span>
            )}
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <button
              onClick={toggleSortDeadline}
              className="btn btn-outline btn-sm"
              title="Toggle sorting by deadline date"
            >
              <ArrowUpDown size={12} /> Sort by Deadline: {sortDeadlineOrder.toUpperCase()}
            </button>
          </div>
        </div>

        <div className="panel-body" style={{ padding: 0 }}>
          {loading ? (
            <div style={{ padding: '48px 24px', textAlign: 'center', color: 'var(--color-navy)' }}>
              <Loader2 size={24} className="animate-spin" style={{ margin: '0 auto 8px', display: 'block' }} />
              <div style={{ fontWeight: '700', fontSize: '13px' }}>Loading Applications from Server...</div>
              <div style={{ fontSize: '11px', color: 'var(--color-text-muted)' }}>Retrieving records from /api/v1/applications</div>
            </div>
          ) : error ? (
            <div style={{ padding: '36px 24px', textAlign: 'center' }}>
              <AlertCircle size={28} color="var(--color-critical)" style={{ margin: '0 auto 8px', display: 'block' }} />
              <div style={{ fontWeight: '700', color: 'var(--color-critical)', fontSize: '14px', marginBottom: '4px' }}>
                Failed to Load Applications
              </div>
              <div style={{ fontSize: '12px', color: 'var(--color-text-muted)', marginBottom: '14px' }}>
                {error}
              </div>
              <button onClick={() => fetchApplicationsFromBackend()} className="btn btn-outline btn-sm">
                <RotateCcw size={12} /> Retry Server Connection
              </button>
            </div>
          ) : paginatedApps.length === 0 ? (
            <EmptyState
              title={applications.length === 0 ? 'No Applications in Database' : 'No Matching Applications'}
              message={
                applications.length === 0
                  ? 'No applications have been registered in the database yet.'
                  : 'No applications match the current filter criteria.'
              }
              actionLabel={applications.length === 0 ? undefined : 'Clear All Filters'}
              onAction={applications.length === 0 ? undefined : handleResetFilters}
            />
          ) : (
            <div className="table-container" style={{ border: 'none' }}>
              <table className="gov-table">
                <thead>
                  <tr>
                    <th>Application ID</th>
                    <th>Institution</th>
                    <th>Application Type</th>
                    <th>Region</th>
                    <th>Current Stage</th>
                    <th>Assigned Owner</th>
                    <th>Deadline</th>
                    <th>Ageing</th>
                    <th>Status</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {paginatedApps.map((app) => (
                    <tr key={app.id}>
                      <td>
                        <Link to={`/applications/${app.id}`} style={{ color: 'var(--color-blue)', fontWeight: '700' }}>
                          {app.id}
                        </Link>
                      </td>
                      <td>
                        <strong>{app.institutionName}</strong>
                        <div style={{ fontSize: '10px', color: 'var(--color-text-muted)' }}>
                          ID: {app.institutionId}
                        </div>
                      </td>
                      <td style={{ maxWidth: '180px', whiteSpace: 'normal', fontSize: '11px' }}>
                        {app.applicationType}
                      </td>
                      <td>{app.region}</td>
                      <td>
                        <span style={{ fontWeight: '600', color: 'var(--color-navy)' }}>
                          {app.currentStage}
                        </span>
                      </td>
                      <td style={{ fontSize: '11px', color: 'var(--color-text-muted)' }}>
                        {app.owner}
                      </td>
                      <td>
                        <span style={{ fontWeight: '700' }}>{app.dueDate}</span>
                      </td>
                      <td style={{ fontSize: '11px' }}>
                        {app.ageDays ? `${app.ageDays} days` : '—'}
                      </td>
                      <td>
                        <StatusBadge status={app.status} />
                      </td>
                      <td>
                        <Link to={`/applications/${app.id}`} className="btn btn-primary btn-sm" title="View Application Dossier">
                          <Eye size={12} /> View Application
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Pagination Footer */}
        {totalRecords > 0 && (
          <div
            style={{
              padding: '10px 16px',
              borderTop: '1px solid var(--color-border-subtle)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              background: '#F9FBFC',
              fontSize: 'var(--font-size-xs)',
              color: 'var(--color-text-muted)',
            }}
          >
            <span>
              Page {currentPage} of {totalPages}
            </span>

            <div style={{ display: 'flex', gap: '6px' }}>
              <button
                onClick={() => setCurrentPage((p) => Math.max(p - 1, 1))}
                disabled={currentPage === 1}
                className="btn btn-outline btn-sm"
              >
                <ChevronLeft size={13} /> Previous
              </button>
              <button
                onClick={() => setCurrentPage((p) => Math.min(p + 1, totalPages))}
                disabled={currentPage === totalPages}
                className="btn btn-outline btn-sm"
              >
                Next <ChevronRight size={13} />
              </button>
            </div>
          </div>
        )}
      </div>
      {/* Create Application Modal (Institution Representative Only) */}
      {role === ROLES.INSTITUTION_REP && (
        <Modal
          isOpen={isCreateModalOpen}
          onClose={() => {
            if (!isSubmitting) {
              setIsCreateModalOpen(false);
              setCreatedAppResult(null);
              setApiError(null);
              setFieldErrors({});
            }
          }}
          title="Create New Application Dossier"
          subtitle="Register an approval dossier in AICTE National Registry"
          footer={
            !createdAppResult && (
              <>
                <button
                  type="button"
                  onClick={() => setIsCreateModalOpen(false)}
                  className="btn btn-outline btn-sm"
                  disabled={isSubmitting}
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={handleCreateSubmit}
                  className="btn btn-primary btn-sm"
                  id="btnSubmitCreateApplication"
                  disabled={isSubmitting || isLoadingInstitutions}
                >
                  {isSubmitting ? 'Registering in PostgreSQL...' : 'Register Application'}
                </button>
              </>
            )
          }
        >
          {createdAppResult ? (
            <div style={{ textAlign: 'center', padding: '16px 8px' }}>
              <CheckCircle2 size={42} color="var(--color-success)" style={{ marginBottom: '12px' }} />
              <h3 style={{ fontSize: '17px', color: 'var(--color-navy)', margin: '0 0 6px' }}>
                Application Registered Successfully!
              </h3>
              <p style={{ color: 'var(--color-text-muted)', fontSize: '13px', marginBottom: '18px' }}>
                Dossier <strong>{createdAppResult.applicationReference}</strong> has been persisted directly to PostgreSQL with initial status <strong>{createdAppResult.status}</strong> under stage <strong>{createdAppResult.currentStage}</strong>.
              </p>
              <div style={{ display: 'flex', justifyContent: 'center', gap: '10px' }}>
                <Link
                  to={`/applications/${createdAppResult.applicationReference}`}
                  className="btn btn-primary btn-sm"
                  id="btnViewCreatedDossier"
                  onClick={() => {
                    setIsCreateModalOpen(false);
                    setCreatedAppResult(null);
                  }}
                >
                  <Eye size={13} /> Open Application Dossier
                </Link>
                <button
                  onClick={() => {
                    setCreatedAppResult(null);
                    setIsCreateModalOpen(false);
                  }}
                  className="btn btn-outline btn-sm"
                >
                  Close
                </button>
              </div>
            </div>
          ) : (
            <form onSubmit={handleCreateSubmit}>
              {apiError && (
                <div className="callout callout-critical" style={{ marginBottom: '14px', fontSize: '12px' }}>
                  <AlertCircle size={15} style={{ display: 'inline', verticalAlign: 'text-bottom', marginRight: '6px' }} />
                  <strong>Error: </strong> {apiError}
                </div>
              )}

              <div className="callout callout-amber" style={{ fontSize: '11px', marginBottom: '14px' }}>
                Applications are registered in <strong>Draft / Scrutiny</strong> status in the AICTE national registry and assigned to the Regional Bureau for documentary review.
              </div>

              {/* Institution Selection */}
              <div className="form-group" style={{ marginBottom: '12px' }}>
                <label className="form-label" htmlFor="newAppInstitution">
                  Canonical Institution *
                </label>
                {isLoadingInstitutions ? (
                  <div style={{ fontSize: '12px', color: 'var(--color-text-muted)', padding: '6px 0' }}>
                    Loading institutional directory from backend...
                  </div>
                ) : (
                  <select
                    id="newAppInstitution"
                    className="form-select"
                    value={formInstitutionId}
                    onChange={(e) => {
                      setFormInstitutionId(e.target.value);
                      if (fieldErrors.institutionId) {
                        setFieldErrors((prev) => ({ ...prev, institutionId: null }));
                      }
                    }}
                    required
                  >
                    {institutionsList.length === 0 ? (
                      <option value="">No institutions found</option>
                    ) : (
                      institutionsList.map((inst) => (
                        <option key={inst.id} value={inst.id}>
                          {inst.name} ({inst.institutionalIdentifier})
                        </option>
                      ))
                    )}
                  </select>
                )}
                {fieldErrors.institutionId && (
                  <div style={{ color: 'var(--color-critical)', fontSize: '11px', marginTop: '4px' }}>
                    {fieldErrors.institutionId}
                  </div>
                )}
              </div>

              {/* Application Reference */}
              <div className="form-group" style={{ marginBottom: '12px' }}>
                <label className="form-label" htmlFor="newAppReference">
                  Application Reference Number *
                </label>
                <input
                  id="newAppReference"
                  type="text"
                  className="form-input"
                  value={formAppReference}
                  onChange={(e) => {
                    setFormAppReference(e.target.value);
                    if (fieldErrors.applicationReference) {
                      setFieldErrors((prev) => ({ ...prev, applicationReference: null }));
                    }
                  }}
                  placeholder="e.g. APP-2026-1045"
                  maxLength={64}
                  required
                />
                <span style={{ fontSize: '11px', color: 'var(--color-text-muted)' }}>
                  Must be unique across the national AICTE registry (max 64 alphanumeric chars).
                </span>
                {fieldErrors.applicationReference && (
                  <div style={{ color: 'var(--color-critical)', fontSize: '11px', marginTop: '4px' }}>
                    {fieldErrors.applicationReference}
                  </div>
                )}
              </div>

              {/* Application Type */}
              <div className="form-group" style={{ marginBottom: '12px' }}>
                <label className="form-label" htmlFor="newAppType">
                  Application Type *
                </label>
                <select
                  id="newAppType"
                  className="form-select"
                  value={formAppType}
                  onChange={(e) => {
                    setFormAppType(e.target.value);
                    if (fieldErrors.applicationType) {
                      setFieldErrors((prev) => ({ ...prev, applicationType: null }));
                    }
                  }}
                  required
                >
                  <option value="Extension of Approval (EoA)">Extension of Approval (EoA)</option>
                  <option value="New Technical Institution Setup">New Technical Institution Setup</option>
                  <option value="Approval of New Engineering Department">Approval of New Engineering Department</option>
                  <option value="Increase in Intake / Additional Course">Increase in Intake / Additional Course</option>
                  <option value="Closure of Program / Institution">Closure of Program / Institution</option>
                </select>
                {fieldErrors.applicationType && (
                  <div style={{ color: 'var(--color-critical)', fontSize: '11px', marginTop: '4px' }}>
                    {fieldErrors.applicationType}
                  </div>
                )}
              </div>

              {/* Academic Year */}
              <div className="form-group" style={{ marginBottom: '12px' }}>
                <label className="form-label" htmlFor="newAppAcademicYear">
                  Academic Approval Year *
                </label>
                <select
                  id="newAppAcademicYear"
                  className="form-select"
                  value={formAcademicYear}
                  onChange={(e) => {
                    setFormAcademicYear(e.target.value);
                    if (fieldErrors.academicYear) {
                      setFieldErrors((prev) => ({ ...prev, academicYear: null }));
                    }
                  }}
                  required
                >
                  <option value="2026-2027">2026-2027 (Current Approval Cycle)</option>
                  <option value="2027-2028">2027-2028 (Advance Approval Cycle)</option>
                  <option value="2025-2026">2025-2026 (Retrospective Compliance Cycle)</option>
                </select>
                {fieldErrors.academicYear && (
                  <div style={{ color: 'var(--color-critical)', fontSize: '11px', marginTop: '4px' }}>
                    {fieldErrors.academicYear}
                  </div>
                )}
              </div>
            </form>
          )}
        </Modal>
      )}
    </div>
  );
};

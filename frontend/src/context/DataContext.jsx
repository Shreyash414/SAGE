// SIH 26262 - DataContext: Live API Integration & Workflow Engine
import React, { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import { ROLES } from '../constants/roles';
import { apiClient } from '../services/apiClient';

const DataContext = createContext(null);

const mapBackendStatus = (status) => {
  if (!status) return 'In progress';
  const s = String(status).toUpperCase();
  if (s === 'IN_PROGRESS' || s === 'IN PROGRESS') return 'In progress';
  if (s === 'DUE_SOON' || s === 'DUE SOON') return 'Due soon';
  if (s === 'OVERDUE') return 'Overdue';
  if (s === 'RETURNED') return 'Returned';
  if (s === 'COMPLETED' || s === 'APPROVED') return 'Completed';
  if (s === 'DRAFT') return 'Draft';
  return status;
};

const mapBackendApplicationToFrontend = (apiApp) => {
  const appId = apiApp.applicationReference || (apiApp.id != null ? `APP-${apiApp.id}` : 'APP-UNKNOWN');
  return {
    id: appId,
    backendId: apiApp.id,
    applicationReference: apiApp.applicationReference || appId,
    institutionId: apiApp.institution?.institutionalIdentifier || apiApp.institution?.institutionCode || (apiApp.institution?.id ? `INST-${apiApp.institution.id}` : ''),
    institutionName: apiApp.institution?.name || apiApp.institution?.legalName || 'Unassigned Institution',
    applicationType: apiApp.applicationType || 'Extension of Approval (EoA)',
    academicYear: apiApp.academicYear || '2026-2027',
    region: apiApp.institution?.region || 'Northern Region',
    currentStage: apiApp.currentStage || 'Scrutiny',
    owner: apiApp.ownerName || 'Unassigned Desk',
    ownerId: apiApp.ownerId || '',
    dueDate: apiApp.dueDate ? String(apiApp.dueDate) : null,
    submissionDate: apiApp.createdAt ? String(apiApp.createdAt).substring(0, 10) : '',
    status: mapBackendStatus(apiApp.status),
    ageDays: apiApp.ageDays != null ? apiApp.ageDays : (apiApp.createdAt ? Math.max(0, Math.floor((Date.now() - new Date(apiApp.createdAt).getTime()) / (1000 * 60 * 60 * 24))) : 0),
    workflowVersion: apiApp.version != null ? `APH 2026-27 v${apiApp.version}` : 'APH 2026-27 v1',
    deficiencies: Array.isArray(apiApp.deficiencies) ? apiApp.deficiencies : [],
    observations: apiApp.observations || '',
    checklistProgress: { completed: 0, total: 4 },
    timeline: Array.isArray(apiApp.timeline) && apiApp.timeline.length > 0
      ? apiApp.timeline.map((t, idx) => ({
          id: `TL-${idx}-${t.timestamp || Date.now()}`,
          title: t.title || t.action || 'Workflow Event',
          actor: `${t.actorName || 'Official'} (${t.actorRole || 'System'})`,
          timestamp: t.timestamp ? new Date(t.timestamp).toLocaleString('en-GB', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '',
          description: t.description || t.notes || ''
        }))
      : (apiApp.createdAt ? [{
          id: `TL-REG-${apiApp.id}`,
          title: 'Application Dossier Submitted & Registered',
          actor: 'Institution Authorized Representative',
          timestamp: new Date(apiApp.createdAt).toLocaleString('en-GB', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }),
          description: `Official application registered under APH workflow. Assigned tracking reference ${appId}.`
        }] : []),
    submissions: []
  };
};

const mapBackendEvcToFrontend = (apiEvc) => {
  const evcId = apiEvc.id || apiEvc.evcReference || (apiEvc.backendId != null ? `EVC-${apiEvc.backendId}` : 'EVC-UNKNOWN');
  return {
    id: evcId,
    evcReference: apiEvc.evcReference || evcId,
    backendId: apiEvc.backendId,
    applicationId: apiEvc.applicationId || (apiEvc.applicationBackendId ? `APP-${apiEvc.applicationBackendId}` : ''),
    applicationBackendId: apiEvc.applicationBackendId,
    institutionId: apiEvc.institutionId || '',
    institutionName: apiEvc.institutionName || 'Unassigned Institution',
    region: apiEvc.region || 'Northern Region',
    inspectionType: apiEvc.inspectionType || 'Physical On-Site Verification',
    status: apiEvc.status || 'Awaiting scheduling',
    urgency: apiEvc.urgency || 'In progress',
    visitDate: apiEvc.visitDate || null,
    coordinator: apiEvc.coordinator || 'C. Verma (EVC Operations)',
    coordinatorId: apiEvc.coordinatorId || 'USR-EVC-01',
    deadline: apiEvc.deadline || null,
    experts: Array.isArray(apiEvc.experts) ? apiEvc.experts : [],
    reportStatus: apiEvc.reportStatus || 'Not Applicable',
    reportOutcome: apiEvc.reportOutcome || null,
    reportNotes: apiEvc.reportNotes || '',
    completionNotes: apiEvc.completionNotes || '',
    version: apiEvc.version || 0,
    createdAt: apiEvc.createdAt,
    updatedAt: apiEvc.updatedAt,
  };
};

export const DataProvider = ({ children }) => {
  const [applications, setApplications] = useState([]);
  const [institutions, setInstitutions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Live EVC state populated from backend
  const [evc, setEvc] = useState([]);
  const [compliance, setCompliance] = useState([]);
  const [documents, setDocuments] = useState([]);
  const [alerts, setAlerts] = useState([]);
  const [activity, setActivity] = useState([]);

  const latestFetchAppsRef = useRef(0);
  const hasLoadedInitialAppsRef = useRef(false);

  // Fetch applications from Spring Boot / PostgreSQL backend with stale-data guard
  const fetchApplicationsFromBackend = useCallback(async (options = {}) => {
    const silent = options?.silent ?? hasLoadedInitialAppsRef.current;
    const fetchId = Date.now();
    latestFetchAppsRef.current = fetchId;

    try {
      if (!silent) {
        setLoading(true);
      }
      setError(null);
      const data = await apiClient(`/applications?size=100&_t=${fetchId}`);
      // Drop response if a newer fetch request was initiated
      if (fetchId < latestFetchAppsRef.current) {
        return [];
      }
      const backendItems = data?.content || (Array.isArray(data) ? data : (data?.items || []));
      const mapped = backendItems.map(mapBackendApplicationToFrontend);
      setApplications(mapped);
      hasLoadedInitialAppsRef.current = true;
      return mapped;
    } catch (err) {
      if (fetchId < latestFetchAppsRef.current) {
        return [];
      }
      console.error('Failed to fetch applications from backend API:', err);
      setError(err.message || 'Unable to load applications from server.');
      setApplications([]);
      return [];
    } finally {
      if (fetchId >= latestFetchAppsRef.current && !silent) {
        setLoading(false);
      }
    }
  }, []);

  // Synchronize an officially promoted application immediately into local state
  const registerPromotedApplication = useCallback((promotedAppDto) => {
    if (!promotedAppDto) return;
    const mapped = mapBackendApplicationToFrontend(promotedAppDto);
    setApplications((prev) => {
      const exists = prev.some(
        (a) =>
          a.id === mapped.id ||
          a.applicationReference === mapped.applicationReference ||
          (mapped.backendId && a.backendId === mapped.backendId)
      );
      if (exists) {
        return prev.map((a) =>
          a.id === mapped.id || a.applicationReference === mapped.applicationReference ? { ...a, ...mapped } : a
        );
      }
      return [mapped, ...prev];
    });
  }, []);

  // Fetch institutions from backend
  const fetchInstitutionsFromBackend = useCallback(async () => {
    try {
      const data = await apiClient('/institutions?size=100');
      const items = data?.content || (Array.isArray(data) ? data : []);
      setInstitutions(items);
      return items;
    } catch (err) {
      console.error('Failed to load institutions from backend:', err);
      setInstitutions([]);
      return [];
    }
  }, []);

  // Fetch EVC visits from backend
  const fetchEVCFromBackend = useCallback(async () => {
    try {
      const data = await apiClient('/evc');
      const items = Array.isArray(data) ? data : (data?.content || data?.items || []);
      const mapped = items.map(mapBackendEvcToFrontend);
      setEvc(mapped);
      return mapped;
    } catch (err) {
      console.error('Failed to load EVC visits from backend:', err);
      setEvc([]);
      return [];
    }
  }, []);

  useEffect(() => {
    fetchApplicationsFromBackend();
    fetchInstitutionsFromBackend();
    fetchEVCFromBackend();
  }, [fetchApplicationsFromBackend, fetchInstitutionsFromBackend, fetchEVCFromBackend]);

  const getApplicationById = (id) => {
    if (!id) return null;
    return applications.find((a) => a.id === id || a.applicationReference === id || String(a.backendId) === String(id)) || null;
  };

  /**
   * Core Functional Workflow Transition Engine
   * Connected directly to POST /api/v1/applications/{id}/actions
   */
  const performWorkflowAction = async (actionType, payload) => {
    const { applicationId, actorName, actorRole, reason, observations, newDeficiencies, nextStage } = payload;
    const targetApp = applications.find((a) => a.id === applicationId || a.applicationReference === applicationId || String(a.backendId) === String(applicationId));

    if (!targetApp) {
      return { success: false, error: 'Application not found in current store.' };
    }

    const idOrRef = targetApp.backendId || targetApp.applicationReference || targetApp.id;

    try {
      const apiResponse = await apiClient(`/applications/${idOrRef}/actions`, {
        method: 'POST',
        body: JSON.stringify({
          action: actionType,
          actorName: actorName || 'Authorized Officer',
          actorRole: actorRole || 'Scrutiny Officer',
          reason: reason || observations || 'Workflow state transition',
          observations: observations || '',
          deficiencies: newDeficiencies || targetApp.deficiencies || [],
          nextStage: nextStage || targetApp.currentStage,
        }),
      });

      if (apiResponse && (apiResponse.id != null || apiResponse.applicationReference)) {
        const updatedMapped = mapBackendApplicationToFrontend(apiResponse);
        setApplications((prev) =>
          prev.map((app) =>
            app.id === targetApp.id || app.applicationReference === targetApp.applicationReference
              ? { ...app, ...updatedMapped }
              : app
          )
        );

        // Record to real activity audit trail
        const now = new Date();
        const timestampStr = `${now.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' })} ${now.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' })}`;
        setActivity((prev) => [
          {
            id: `ACT-${Date.now().toString().slice(-4)}`,
            timestamp: timestampStr,
            actor: `${actorName} (${actorRole})`,
            action: actionType,
            details: reason || observations || `Transitioned to ${nextStage || updatedMapped.currentStage}`,
            relatedRecordId: targetApp.id,
            institutionId: targetApp.institutionId,
            category: 'Workflow',
          },
          ...prev,
        ]);

        if (actionType === 'COMPLETE_SCRUTINY') {
          await fetchEVCFromBackend();
        }

        return { success: true, application: updatedMapped };
      }

      // If backend succeeded but returned without body, reload
      await Promise.all([fetchApplicationsFromBackend(), fetchEVCFromBackend()]);
      return { success: true };
    } catch (err) {
      console.error(`Workflow action ${actionType} failed on backend:`, err);
      return {
        success: false,
        error: err.data?.detail || err.message || 'Workflow transition failed on backend server.',
      };
    }
  };

  // EVC actions connected to Spring Boot REST endpoints
  const scheduleEVC = async (arg1, arg2) => {
    const payload = typeof arg1 === 'object' && arg1 !== null && !arg2 ? arg1 : { ...(arg2 || {}), evcId: arg1 };
    const evcId = payload.evcId || payload.id;
    if (!evcId) {
      return { success: false, error: 'EVC identifier is required for scheduling.' };
    }

    try {
      const response = await apiClient(`/evc/${encodeURIComponent(evcId)}/schedule`, {
        method: 'POST',
        body: JSON.stringify({
          visitDate: payload.visitDate,
          coordinator: payload.coordinator,
          inspectionType: payload.inspectionType,
          experts: Array.isArray(payload.experts) ? payload.experts : [],
          actorName: payload.actorName || 'C. Verma',
          actorRole: payload.actorRole || 'EVC Coordinator',
        }),
      });

      const updated = mapBackendEvcToFrontend(response);
      setEvc((prev) => prev.map((item) => (item.id === updated.id || item.evcReference === updated.evcReference ? updated : item)));

      // Refresh applications list
      fetchApplicationsFromBackend();

      const now = new Date();
      const timestampStr = `${now.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' })} ${now.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' })}`;
      setActivity((prev) => [
        {
          id: `ACT-${Date.now().toString().slice(-4)}`,
          timestamp: timestampStr,
          actor: `${payload.actorName || 'C. Verma'} (${payload.actorRole || 'EVC Coordinator'})`,
          action: 'EVC_SCHEDULED',
          details: `Inspection visit for ${updated.institutionName} scheduled for ${updated.visitDate}.`,
          relatedRecordId: updated.id,
          category: 'EVC Operations',
        },
        ...prev,
      ]);

      return { success: true, visit: updated };
    } catch (err) {
      console.error('Failed to schedule EVC visit:', err);
      return {
        success: false,
        error: err.data?.detail || err.message || 'Failed to schedule EVC visit on backend server.',
      };
    }
  };

  const markEVCCompleted = async (arg1, arg2) => {
    const payload = typeof arg1 === 'object' && arg1 !== null && !arg2 ? arg1 : { ...(arg2 || {}), evcId: arg1 };
    const evcId = payload.evcId || payload.id;
    if (!evcId) {
      return { success: false, error: 'EVC identifier is required to complete visit.' };
    }

    try {
      const response = await apiClient(`/evc/${encodeURIComponent(evcId)}/complete`, {
        method: 'POST',
        body: JSON.stringify({
          completionNotes: payload.completionNotes || 'Inspection concluded successfully.',
          actorName: payload.actorName || 'C. Verma',
          actorRole: payload.actorRole || 'EVC Coordinator',
        }),
      });

      const updated = mapBackendEvcToFrontend(response);
      setEvc((prev) => prev.map((item) => (item.id === updated.id || item.evcReference === updated.evcReference ? updated : item)));

      const now = new Date();
      const timestampStr = `${now.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' })} ${now.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' })}`;
      setActivity((prev) => [
        {
          id: `ACT-${Date.now().toString().slice(-4)}`,
          timestamp: timestampStr,
          actor: `${payload.actorName || 'C. Verma'} (${payload.actorRole || 'EVC Coordinator'})`,
          action: 'EVC_VISIT_COMPLETED',
          details: `Inspection visit concluded for ${updated.institutionName}. Inspection report pending.`,
          relatedRecordId: updated.id,
          category: 'EVC Operations',
        },
        ...prev,
      ]);

      return { success: true, visit: updated };
    } catch (err) {
      console.error('Failed to mark EVC visit completed:', err);
      return {
        success: false,
        error: err.data?.detail || err.message || 'Failed to record visit completion on backend.',
      };
    }
  };

  const submitEVCReport = async (arg1, arg2) => {
    const payload = typeof arg1 === 'object' && arg1 !== null && !arg2 ? arg1 : { ...(arg2 || {}), evcId: arg1 };
    const evcId = payload.evcId || payload.id;
    if (!evcId) {
      return { success: false, error: 'EVC identifier is required to submit report.' };
    }

    try {
      const response = await apiClient(`/evc/${encodeURIComponent(evcId)}/report`, {
        method: 'POST',
        body: JSON.stringify({
          reportOutcome: payload.reportOutcome || 'Recommended Approval',
          reportNotes: payload.reportNotes || '',
          actorName: payload.actorName || 'C. Verma',
          actorRole: payload.actorRole || 'EVC Coordinator',
        }),
      });

      const updated = mapBackendEvcToFrontend(response);
      setEvc((prev) => prev.map((item) => (item.id === updated.id || item.evcReference === updated.evcReference ? updated : item)));

      // Re-fetch applications because linked application advanced to 'Committee review'
      await fetchApplicationsFromBackend();

      const now = new Date();
      const timestampStr = `${now.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' })} ${now.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' })}`;
      setActivity((prev) => [
        {
          id: `ACT-${Date.now().toString().slice(-4)}`,
          timestamp: timestampStr,
          actor: `${payload.actorName || 'C. Verma'} (${payload.actorRole || 'EVC Coordinator'})`,
          action: 'EVC_REPORT_SUBMITTED',
          details: `EVC report submitted for ${updated.institutionName} (${updated.reportOutcome}). Application advanced to Committee Review.`,
          relatedRecordId: updated.id,
          category: 'EVC Operations',
        },
        ...prev,
      ]);

      return { success: true, visit: updated };
    } catch (err) {
      console.error('Failed to submit EVC report:', err);
      return {
        success: false,
        error: err.data?.detail || err.message || 'Failed to submit inspection report on backend.',
      };
    }
  };

  const rescheduleEVC = async (arg1, arg2) => {
    const payload = typeof arg1 === 'object' && arg1 !== null && !arg2 ? arg1 : { ...(arg2 || {}), evcId: arg1 };
    const evcId = payload.evcId || payload.id;
    if (!evcId) {
      return { success: false, error: 'EVC identifier is required to reschedule.' };
    }

    try {
      const response = await apiClient(`/evc/${encodeURIComponent(evcId)}/reschedule`, {
        method: 'POST',
        body: JSON.stringify({
          newVisitDate: payload.visitDate || payload.newVisitDate || null,
          actorName: payload.actorName || 'C. Verma',
          actorRole: payload.actorRole || 'EVC Coordinator',
        }),
      });

      const updated = mapBackendEvcToFrontend(response);
      setEvc((prev) => prev.map((item) => (item.id === updated.id || item.evcReference === updated.evcReference ? updated : item)));

      return { success: true, visit: updated };
    } catch (err) {
      console.error('Failed to reschedule EVC:', err);
      return {
        success: false,
        error: err.data?.detail || err.message || 'Failed to reschedule EVC visit on backend.',
      };
    }
  };

  // Compliance actions
  const submitComplianceEvidence = (id, payload) => {
    setCompliance((prev) =>
      prev.map((item) => (item.id === id ? { ...item, ...payload, status: 'Evidence submitted' } : item))
    );
  };

  const markComplianceUnderVerification = (id) => {
    setCompliance((prev) =>
      prev.map((item) => (item.id === id ? { ...item, status: 'Verification pending' } : item))
    );
  };

  const verifyComplianceEvidence = (id, payload) => {
    setCompliance((prev) =>
      prev.map((item) => (item.id === id ? { ...item, ...payload } : item))
    );
  };

  // Document actions
  const uploadDocument = (doc) => {
    setDocuments((prev) => [doc, ...prev]);
  };

  const submitDocumentRenewal = (id, renewalData) => {
    setDocuments((prev) =>
      prev.map((doc) => (doc.id === id ? { ...doc, ...renewalData } : doc))
    );
  };

  const updateDocumentVerification = (id, payload) => {
    setDocuments((prev) =>
      prev.map((doc) => (doc.id === id ? { ...doc, ...payload } : doc))
    );
  };

  // Alert actions
  const markAlertRead = (id) => {
    setAlerts((prev) =>
      prev.map((alt) => (alt.id === id ? { ...alt, read: true } : alt))
    );
  };

  const acknowledgeAlert = (id) => {
    setAlerts((prev) =>
      prev.map((alt) => (alt.id === id ? { ...alt, acknowledged: true } : alt))
    );
  };

  const retryNotification = (id) => {
    setAlerts((prev) =>
      prev.map((alt) => (alt.id === id ? { ...alt, deliveryStatus: 'Delivered' } : alt))
    );
  };

  // ---------------------------------------------------------------------------
  // Automated Scrutiny & Re-Scrutiny Engine API Integrations
  // ---------------------------------------------------------------------------
  const runAutomatedScrutiny = async (applicationId) => {
    try {
      const result = await apiClient(`/applications/${applicationId}/scrutiny/run`, {
        method: 'POST',
      });
      return { success: true, data: result };
    } catch (err) {
      console.error('Failed to run automated scrutiny:', err);
      return { success: false, error: err.message || 'Scrutiny run failed' };
    }
  };

  const runAutomatedRescrutiny = async (applicationId) => {
    try {
      const result = await apiClient(`/applications/${applicationId}/scrutiny/rescrutiny`, {
        method: 'POST',
      });
      return { success: true, data: result };
    } catch (err) {
      console.error('Failed to run automated re-scrutiny:', err);
      return { success: false, error: err.message || 'Re-scrutiny run failed' };
    }
  };

  const fetchScrutinyReport = async (applicationId) => {
    try {
      const result = await apiClient(`/applications/${applicationId}/scrutiny/report`);
      return { success: true, data: result };
    } catch (err) {
      console.error('Failed to fetch scrutiny report:', err);
      return { success: false, error: err.message || 'Failed to fetch report' };
    }
  };

  const fetchScrutinyExtractions = async (applicationId) => {
    try {
      const result = await apiClient(`/applications/${applicationId}/scrutiny/extractions`);
      return { success: true, data: result };
    } catch (err) {
      console.error('Failed to fetch scrutiny extractions:', err);
      return { success: false, error: err.message || 'Failed to fetch extractions' };
    }
  };

  const fetchScrutinyAssessments = async (applicationId) => {
    try {
      const result = await apiClient(`/applications/${applicationId}/scrutiny/assessments`);
      return { success: true, data: result };
    } catch (err) {
      console.error('Failed to fetch scrutiny assessments:', err);
      return { success: false, error: err.message || 'Failed to fetch assessments' };
    }
  };

  const reviewScrutinyFinding = async (applicationId, findingId, payload) => {
    try {
      const result = await apiClient(`/applications/${applicationId}/scrutiny/findings/${findingId}/review`, {
        method: 'POST',
        body: JSON.stringify(payload),
      });
      return { success: true, data: result };
    } catch (err) {
      console.error('Failed to review scrutiny finding:', err);
      return { success: false, error: err.message || 'Failed to record finding review' };
    }
  };

  const fetchDeficiencies = async (applicationId) => {
    try {
      const result = await apiClient(`/applications/${applicationId}/deficiencies`);
      return { success: true, data: result };
    } catch (err) {
      console.error('Failed to fetch deficiencies:', err);
      return { success: false, error: err.message || 'Failed to fetch deficiencies' };
    }
  };

  const issueDeficiencyNotice = async (applicationId, payload) => {
    try {
      const result = await apiClient(`/applications/${applicationId}/deficiencies/issue`, {
        method: 'POST',
        body: JSON.stringify(payload),
      });
      await fetchApplicationsFromBackend();
      return { success: true, data: result };
    } catch (err) {
      console.error('Failed to issue deficiency notice:', err);
      return { success: false, error: err.message || 'Failed to issue deficiency notice' };
    }
  };

  const uploadCorrectedDocument = async (applicationId, deficiencyId, file) => {
    try {
      const formData = new FormData();
      formData.append('file', file);
      const result = await apiClient(`/applications/${applicationId}/deficiencies/${deficiencyId}/documents`, {
        method: 'POST',
        body: formData,
      });
      return { success: true, data: result };
    } catch (err) {
      console.error('Failed to upload corrected document:', err);
      return { success: false, error: err.message || 'Document upload failed' };
    }
  };

  const submitDeficiencyResponses = async (applicationId, payload) => {
    try {
      const result = await apiClient(`/applications/${applicationId}/deficiencies/submit-responses`, {
        method: 'POST',
        body: JSON.stringify(payload),
      });
      await fetchApplicationsFromBackend();
      return { success: true, data: result };
    } catch (err) {
      console.error('Failed to submit deficiency responses:', err);
      return { success: false, error: err.message || 'Response submission failed' };
    }
  };

  const reviewDeficiency = async (applicationId, deficiencyId, payload) => {
    try {
      const result = await apiClient(`/applications/${applicationId}/deficiencies/${deficiencyId}/review`, {
        method: 'POST',
        body: JSON.stringify(payload),
      });
      return { success: true, data: result };
    } catch (err) {
      console.error('Failed to review deficiency:', err);
      return { success: false, error: err.message || 'Deficiency review failed' };
    }
  };

  const fetchRescrutinyComparisons = async (applicationId) => {
    try {
      const result = await apiClient(`/applications/${applicationId}/scrutiny/comparison`);
      return { success: true, data: result };
    } catch (err) {
      console.error('Failed to fetch re-scrutiny comparisons:', err);
      return { success: false, error: err.message || 'Failed to fetch comparison' };
    }
  };

  const refreshAllData = async () => {
    await Promise.all([
      fetchApplicationsFromBackend(),
      fetchInstitutionsFromBackend(),
      fetchEVCFromBackend(),
    ]);
  };

  return (
    <DataContext.Provider
      value={{
        applications,
        institutions,
        loading,
        error,
        evc,
        compliance,
        documents,
        alerts,
        activity,
        fetchApplicationsFromBackend,
        registerPromotedApplication,
        fetchInstitutionsFromBackend,
        fetchEVCFromBackend,
        refreshAllData,
        getApplicationById,
        performWorkflowAction,
        scheduleEVC,
        markEVCCompleted,
        submitEVCReport,
        rescheduleEVC,
        submitComplianceEvidence,
        markComplianceUnderVerification,
        verifyComplianceEvidence,
        uploadDocument,
        submitDocumentRenewal,
        updateDocumentVerification,
        markAlertRead,
        acknowledgeAlert,
        retryNotification,
        runAutomatedScrutiny,
        runAutomatedRescrutiny,
        fetchScrutinyReport,
        fetchScrutinyExtractions,
        fetchScrutinyAssessments,
        reviewScrutinyFinding,
        fetchDeficiencies,
        issueDeficiencyNotice,
        uploadCorrectedDocument,
        submitDeficiencyResponses,
        reviewDeficiency,
        fetchRescrutinyComparisons,
      }}
    >
      {children}
    </DataContext.Provider>
  );
};

export const useData = () => {
  const context = useContext(DataContext);
  if (!context) {
    throw new Error('useData must be used within a DataProvider');
  }
  return context;
};

// SAGE - Institutional Approval & Compliance Platform
// Automated Scrutiny & Re-Scrutiny Intelligent Panel
// Features: OCR/PDFBox Extraction, Regulatory Validation Engine, Deficiency Management, Re-Scrutiny Diffs

import React, { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../../context/AuthContext';
import { useData } from '../../context/DataContext';
import { ROLES } from '../../constants/roles';
import {
  FileText,
  Search,
  CheckCircle2,
  AlertTriangle,
  AlertCircle,
  Clock,
  Shield,
  Upload,
  ArrowRight,
  Eye,
  RefreshCw,
  FileCheck,
  Send,
  XCircle,
  Layers,
  ChevronDown,
  ChevronRight,
  Download,
  Check,
  X,
  FileDiff,
  Calendar,
  ExternalLink,
  Copy,
} from 'lucide-react';

export const AutomatedScrutinyPanel = ({ application, onScrutinyComplete }) => {
  const { user, role } = useAuth();
  const {
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
  } = useData();

  const appId = application?.backendId || application?.applicationReference || application?.id;

  // Active sub-tab
  const [activeSubTab, setActiveSubTab] = useState('findings'); // 'findings' | 'extractions' | 'deficiencies' | 'rescrutiny'
  const [findingFilter, setFindingFilter] = useState('ALL'); // 'ALL' | 'POTENTIAL_DEFICIENCY' | 'NEEDS_MANUAL_REVIEW' | 'PASS'

  // Loading & Action states
  const [loading, setLoading] = useState(false);
  const [runningScrutiny, setRunningScrutiny] = useState(false);
  const [statusMessage, setStatusMessage] = useState(null);

  // Data states
  const [report, setReport] = useState(null);
  const [deficiencies, setDeficiencies] = useState([]);
  const [comparisons, setComparisons] = useState([]);
  const [expandedExtraction, setExpandedExtraction] = useState(null);
  const [copiedExtractionId, setCopiedExtractionId] = useState(null);

  // Modal / Form states
  const [isIssueModalOpen, setIsIssueModalOpen] = useState(false);
  const [isIssuingNotice, setIsIssuingNotice] = useState(false);
  const [issueDueDate, setIssueDueDate] = useState(
    new Date(Date.now() + 15 * 24 * 60 * 60 * 1000).toISOString().substring(0, 10)
  );
  const [issueObservation, setIssueObservation] = useState('');
  const [selectedFindingIds, setSelectedFindingIds] = useState([]);

  // Institution Response state
  const [responseExplanations, setResponseExplanations] = useState({});
  const [selectedFiles, setSelectedFiles] = useState({});
  const [uploadingDefId, setUploadingDefId] = useState(null);

  // Officer Review finding modal
  const [reviewingFinding, setReviewingFinding] = useState(null);
  const [findingReviewDecision, setFindingReviewDecision] = useState('CONFIRMED_DEFICIENCY');
  const [findingReviewRemarks, setFindingReviewRemarks] = useState('');
  const [applyToAllDuplicates, setApplyToAllDuplicates] = useState(true);
  const [isSavingReview, setIsSavingReview] = useState(false);

  // Grouped Findings View Mode & Expansion State
  const [viewMode, setViewMode] = useState('GROUPED'); // 'GROUPED' | 'ALL_EVALUATIONS'
  const [expandedGroupIds, setExpandedGroupIds] = useState({});

  const toggleGroupExpand = (id) => {
    setExpandedGroupIds((prev) => ({
      ...prev,
      [id]: !prev[id],
    }));
  };

  // Officer Review deficiency modal
  const [reviewingDeficiency, setReviewingDeficiency] = useState(null);
  const [defReviewDecision, setDefReviewDecision] = useState('VERIFIED_RESOLVED');
  const [defReviewRemarks, setDefReviewRemarks] = useState('');

  const isOfficer = role === ROLES.SCRUTINY_OFFICER || role === ROLES.REGIONAL_OFFICER || role === ROLES.SUPER_ADMIN || role === ROLES.ADMIN;
  const isInstitution = role === ROLES.INSTITUTION || role === ROLES.INSTITUTION_REPRESENTATIVE || role === ROLES.INSTITUTE || role === ROLES.ADMIN;

  const loadScrutinyData = useCallback(async () => {
    if (!appId) return;
    setLoading(true);
    try {
      const [reportRes, defRes, compRes, extRes] = await Promise.all([
        fetchScrutinyReport(appId),
        fetchDeficiencies(appId),
        fetchRescrutinyComparisons(appId),
        fetchScrutinyExtractions ? fetchScrutinyExtractions(appId) : Promise.resolve({ success: false }),
      ]);

      let loadedReport = null;
      if (reportRes.success && reportRes.data) {
        loadedReport = { ...reportRes.data };
      }
      if (extRes && extRes.success && Array.isArray(extRes.data) && extRes.data.length > 0) {
        if (!loadedReport) {
          loadedReport = { extractions: extRes.data };
        } else if (!loadedReport.extractions || loadedReport.extractions.length === 0) {
          loadedReport.extractions = extRes.data;
        }
      }
      if (loadedReport) {
        setReport(loadedReport);
      }
      if (defRes.success && Array.isArray(defRes.data)) {
        setDeficiencies(defRes.data);
      }
      if (compRes.success && Array.isArray(compRes.data)) {
        setComparisons(compRes.data);
      }
    } catch (err) {
      console.error('Error loading scrutiny data:', err);
    } finally {
      setLoading(false);
    }
  }, [appId, fetchScrutinyReport, fetchDeficiencies, fetchRescrutinyComparisons, fetchScrutinyExtractions]);

  useEffect(() => {
    loadScrutinyData();
  }, [loadScrutinyData]);

  // Handler: Start Automated Scrutiny
  const handleRunScrutiny = async () => {
    setRunningScrutiny(true);
    setStatusMessage({ type: 'info', text: 'Executing PDFBox text extraction and statutory compliance checks...' });
    try {
      const res = await runAutomatedScrutiny(appId);
      if (res.success) {
        setStatusMessage({ type: 'success', text: 'Automated scrutiny completed successfully. Preliminary findings generated.' });
        await loadScrutinyData();
      } else {
        setStatusMessage({ type: 'error', text: res.error || 'Failed to complete automated scrutiny.' });
      }
    } catch (err) {
      setStatusMessage({ type: 'error', text: err.message || 'Error executing scrutiny.' });
    } finally {
      setRunningScrutiny(false);
    }
  };

  // Handler: Run Automated Re-Scrutiny
  const handleRunRescrutiny = async () => {
    setRunningScrutiny(true);
    setStatusMessage({ type: 'info', text: 'Executing automated re-scrutiny comparative assessment...' });
    try {
      const res = await runAutomatedRescrutiny(appId);
      if (res.success) {
        setStatusMessage({ type: 'success', text: 'Automated re-scrutiny completed. Comparative diffs updated.' });
        await loadScrutinyData();
        if (onScrutinyComplete) onScrutinyComplete();
      } else {
        setStatusMessage({ type: 'error', text: res.error || 'Re-scrutiny execution failed' });
      }
    } catch (err) {
      setStatusMessage({ type: 'error', text: err.message || 'Failed to run re-scrutiny' });
    } finally {
      setRunningScrutiny(false);
    }
  };

  // Helper: Open document directly by document ID from MinIO
  const handleOpenDocumentById = async (docId) => {
    if (!docId) return;
    try {
      const token = localStorage.getItem('aicte_auth_token');
      const apiBase = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';
      const url = `${apiBase}/applications/${appId}/documents/${docId}?inline=true`;
      const res = await fetch(url, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      });
      if (!res.ok) {
        throw new Error(`Failed to stream document from MinIO (HTTP ${res.status})`);
      }
      const blob = await res.blob();
      const objectUrl = URL.createObjectURL(blob);
      window.open(objectUrl, '_blank', 'noopener,noreferrer');
    } catch (err) {
      alert(`Could not open document from MinIO: ${err.message}`);
    }
  };

  // Handler: Officer review of preliminary finding
  const handleConfirmFindingReview = async (e) => {
    e.preventDefault();
    if (!reviewingFinding) return;

    setIsSavingReview(true);
    try {
      const res = await reviewScrutinyFinding(appId, reviewingFinding.id, {
        decision: findingReviewDecision,
        remarks: findingReviewRemarks || `Reviewed as ${findingReviewDecision}`,
        applyToAllDuplicates: (reviewingFinding.isGrouped || reviewingFinding.ruleCode?.startsWith('RULE_DUPLICATE_FILE_UPLOAD_')) ? applyToAllDuplicates : false,
      });

      if (res.success) {
        setStatusMessage({ type: 'success', text: `Finding #${reviewingFinding.id} recorded as ${findingReviewDecision}.` });
        setReviewingFinding(null);
        setFindingReviewRemarks('');
        await loadScrutinyData();
        if (onScrutinyComplete) {
          onScrutinyComplete();
        }
      } else {
        alert(res.error || 'Failed to update finding decision.');
      }
    } catch (err) {
      alert(err.message || 'Error recording review.');
    } finally {
      setIsSavingReview(false);
    }
  };

  // Handler: Issue Formal Deficiency Notice
  const handleIssueNotice = async (e) => {
    e.preventDefault();
    if (selectedFindingIds.length === 0) {
      alert('Please select at least one confirmed finding to include in the deficiency notice.');
      return;
    }

    setIsIssuingNotice(true);
    try {
      const res = await issueDeficiencyNotice(appId, {
        findingIds: selectedFindingIds,
        formalNoticeTitle: 'Official AICTE Scrutiny Deficiency Notice',
        generalObservations: issueObservation || 'Mandatory rectifications required as per AICTE Approval Process Handbook.',
        officerObservation: issueObservation || 'Mandatory rectifications required as per AICTE Approval Process Handbook.',
        responseDueDate: issueDueDate,
        dueDate: issueDueDate,
        returnApplicationForCorrection: true,
        blocking: true,
      });

      if (res.success) {
        setStatusMessage({ type: 'success', text: 'Formal deficiency notice issued and transmitted to institution.' });
        setIsIssueModalOpen(false);
        setSelectedFindingIds([]);
        setIssueObservation('');
        await loadScrutinyData();
        if (onScrutinyComplete) {
          onScrutinyComplete();
        }
        setActiveSubTab('deficiencies');
      } else {
        alert(res.error || 'Failed to issue deficiency notice.');
      }
    } catch (err) {
      alert(err.message || 'Error issuing notice.');
    } finally {
      setIsIssuingNotice(false);
    }
  };

  // Handler: Upload corrected document revision
  const handleUploadCorrectedFile = async (deficiencyId) => {
    const file = selectedFiles[deficiencyId];
    if (!file) {
      alert('Please select a file to upload.');
      return;
    }

    setUploadingDefId(deficiencyId);
    try {
      const res = await uploadCorrectedDocument(appId, deficiencyId, file);
      if (res.success) {
        setStatusMessage({ type: 'success', text: `Corrected document (${file.name}) uploaded and versioned successfully.` });
        setSelectedFiles((prev) => ({ ...prev, [deficiencyId]: null }));
        await loadScrutinyData();
      } else {
        alert(res.error || 'Failed to upload document.');
      }
    } catch (err) {
      alert(err.message || 'Error during file upload.');
    } finally {
      setUploadingDefId(null);
    }
  };

  // Handler: Securely stream and open original document from MinIO
  const handleOpenOriginalDocument = async (ext) => {
    try {
      const docId = ext.documentId;
      const targetAppId = ext.applicationId || appId;
      if (!docId) {
        alert('Document database ID is missing from this extraction record.');
        return;
      }
      const token = localStorage.getItem('aicte_auth_token');
      const apiBase = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';
      const url = `${apiBase}/applications/${targetAppId}/documents/${docId}?inline=true`;

      // Fetch with auth header
      const res = await fetch(url, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      });
      if (!res.ok) {
        throw new Error(`Failed to stream document from MinIO (HTTP ${res.status})`);
      }
      const blob = await res.blob();
      const objectUrl = URL.createObjectURL(blob);
      window.open(objectUrl, '_blank', 'noopener,noreferrer');
    } catch (err) {
      alert(`Could not open document from MinIO: ${err.message}`);
    }
  };

  // Handler: Copy extracted text to clipboard
  const handleCopyExtractedText = (extId, text) => {
    if (!text) return;
    navigator.clipboard.writeText(text);
    setCopiedExtractionId(extId);
    setTimeout(() => setCopiedExtractionId(null), 2500);
  };

  // Handler: Submit Institution Responses
  const handleSubmitResponses = async (deficiencyId) => {
    const explanation = responseExplanations[deficiencyId];
    if (!explanation || !explanation.trim()) {
      alert('Please enter an explanatory note detailing the rectification made.');
      return;
    }

    try {
      const res = await submitDeficiencyResponses(appId, {
        explanations: { [deficiencyId]: explanation.trim() },
        responses: [
          {
            deficiencyId: deficiencyId,
            explanation: explanation.trim(),
          },
        ],
        submissionNote: explanation.trim(),
      });

      if (res.success) {
        setStatusMessage({ type: 'success', text: 'Rectification response submitted. Automated re-scrutiny initiated.' });
        await loadScrutinyData();
        setActiveSubTab('rescrutiny');
      } else {
        alert(res.error || 'Failed to submit response.');
      }
    } catch (err) {
      alert(err.message || 'Error submitting response.');
    }
  };

  // Handler: Officer review of corrected deficiency
  const handleReviewDeficiency = async (e) => {
    e.preventDefault();
    if (!reviewingDeficiency) return;

    try {
      const res = await reviewDeficiency(appId, reviewingDeficiency.id, {
        decision: defReviewDecision,
        remarks: defReviewRemarks || `Deficiency reviewed as ${defReviewDecision}`,
      });

      if (res.success) {
        setStatusMessage({ type: 'success', text: `Deficiency marked as ${defReviewDecision}.` });
        setReviewingDeficiency(null);
        setDefReviewRemarks('');
        await loadScrutinyData();
      } else {
        alert(res.error || 'Failed to record deficiency decision.');
      }
    } catch (err) {
      alert(err.message || 'Error reviewing deficiency.');
    }
  };

  // Helper counts
  const consolidatedFindings = report?.findings || [];
  const underlyingFindings = report?.allUnderlyingFindings && report.allUnderlyingFindings.length > 0
    ? report.allUnderlyingFindings
    : consolidatedFindings;

  // Crucial: ensure 'findings' is always bound to the full list of evaluations
  const findings = underlyingFindings;

  const activeFindingsList = viewMode === 'GROUPED' ? consolidatedFindings : underlyingFindings;

  const filteredFindings = activeFindingsList.filter((f) => {
    if (findingFilter === 'ALL') return true;
    return f.outcome === findingFilter;
  });

  // Distinct verified counts
  const potentialDeficienciesCount = findings.filter(
    (f) => f.outcome === 'POTENTIAL_DEFICIENCY' && (!f.officerDecision || f.officerDecision === 'PENDING')
  ).length;

  const confirmedFindingsList = findings.filter(
    (f) => f.officerDecision === 'CONFIRMED' || f.officerDecision === 'CONFIRMED_DEFICIENCY'
  );
  const confirmedDeficienciesCount = confirmedFindingsList.length;

  const issuedDeficienciesList = deficiencies.filter(
    (d) => d.status === 'ISSUED' || d.status === 'RESPONSE_SUBMITTED' || d.status === 'RECTIFIED_VERIFIED'
  );
  const formalNoticesIssuedCount = issuedDeficienciesList.length;

  const blockingDeficienciesCount = deficiencies.filter(
    (d) => d.blocking && (d.status === 'ISSUED' || d.status === 'RESPONSE_SUBMITTED')
  ).length;

  return (
    <div style={{ marginTop: '16px' }}>
      {/* Top Banner & Control Bar */}
      <div
        style={{
          background: 'linear-gradient(135deg, #0F172A 0%, #1E293B 100%)',
          color: '#FFFFFF',
          borderRadius: '8px',
          padding: '20px',
          marginBottom: '20px',
          boxShadow: '0 4px 12px rgba(15, 23, 42, 0.15)',
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '6px' }}>
              <span style={{ fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.08em', background: 'rgba(56, 189, 248, 0.2)', color: '#38BDF8', padding: '2px 8px', borderRadius: '4px', fontWeight: '700' }}>
                AICTE STATUTORY REGULATORY AUTOMATION
              </span>
              <span style={{ fontSize: '12px', color: '#94A3B8' }}>
                Rules: {report?.rulesVersion || 'APH 2026-27 v1'}
              </span>
            </div>
            <h2 style={{ fontSize: '20px', fontWeight: '800', margin: '0 0 6px 0', letterSpacing: '-0.01em' }}>
              Automated Scrutiny &amp; Re-Scrutiny Intelligence System
            </h2>
            <p style={{ fontSize: '13px', color: '#CBD5E1', margin: 0, maxWidth: '650px' }}>
              Autonomous PDFBox searchable text parsing engine with statutory rule checking and multi-version document comparison.
            </p>
          </div>

          <div style={{ display: 'flex', gap: '10px', alignItems: 'center', flexWrap: 'wrap' }}>
            <button
              onClick={handleRunScrutiny}
              disabled={runningScrutiny}
              className="btn btn-primary"
              style={{
                backgroundColor: '#0284C7',
                borderColor: '#0284C7',
                display: 'inline-flex',
                alignItems: 'center',
                gap: '8px',
                padding: '10px 16px',
                fontSize: '13px',
                fontWeight: '700',
              }}
            >
              <RefreshCw size={15} className={runningScrutiny ? 'animate-spin' : ''} />
              {runningScrutiny ? 'Analyzing Dossier...' : 'Start Automated Scrutiny'}
            </button>

            {report && (
              <button
                onClick={() => {
                  const printContent = `AICTE AUTOMATED SCRUTINY REPORT\nApplication: ${application?.applicationReference}\nInstitution: ${application?.institutionName}\nRules: ${report.rulesVersion}\nOutcome: ${report.summaryOutcome}\nPassed: ${report.passedChecks}/${report.totalChecks}\nPotential Deficiencies: ${report.deficiencyChecks}`;
                  const blob = new Blob([printContent], { type: 'text/plain' });
                  const url = URL.createObjectURL(blob);
                  const a = document.createElement('a');
                  a.href = url;
                  a.download = `Scrutiny_Report_${application?.applicationReference}.txt`;
                  a.click();
                }}
                className="btn btn-outline"
                style={{
                  color: '#FFFFFF',
                  borderColor: 'rgba(255,255,255,0.3)',
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '6px',
                  padding: '10px 14px',
                  fontSize: '13px',
                }}
              >
                <Download size={14} /> Export Report
              </button>
            )}
          </div>
        </div>

        {/* Metrics Row */}
        {report && (
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))',
              gap: '12px',
              marginTop: '18px',
              paddingTop: '16px',
              borderTop: '1px solid rgba(255, 255, 255, 0.1)',
            }}
          >
            <div style={{ background: 'rgba(255, 255, 255, 0.05)', padding: '10px 14px', borderRadius: '6px' }}>
              <div style={{ fontSize: '11px', color: '#94A3B8' }}>Evaluated Checks</div>
              <div style={{ fontSize: '20px', fontWeight: '800', color: '#FFFFFF' }}>
                {report.totalGroupedChecks ?? consolidatedFindings.length}
                <span style={{ fontSize: '11px', fontWeight: '500', color: '#94A3B8', marginLeft: '4px' }}>Grouped</span>
              </div>
              <div style={{ fontSize: '10px', color: '#94A3B8', marginTop: '2px' }}>
                {report.totalChecks} underlying evaluations
              </div>
            </div>
            <div style={{ background: 'rgba(34, 197, 94, 0.1)', padding: '10px 14px', borderRadius: '6px', borderLeft: '3px solid #22C55E' }}>
              <div style={{ fontSize: '11px', color: '#86EFAC' }}>Statutory Compliance PASS</div>
              <div style={{ fontSize: '20px', fontWeight: '800', color: '#4ADE80' }}>
                {report.passedStatutoryChecks ?? (report.passedChecks && report.passedChecks > 0 ? 0 : 0)}
              </div>
            </div>
            <div style={{ background: 'rgba(56, 189, 248, 0.1)', padding: '10px 14px', borderRadius: '6px', borderLeft: '3px solid #38BDF8' }}>
              <div style={{ fontSize: '11px', color: '#7DD3FC' }}>Technical Format PASS</div>
              <div style={{ fontSize: '20px', fontWeight: '800', color: '#38BDF8' }}>
                {report.passedTechnicalChecks ?? report.passedChecks ?? 0}
              </div>
              <div style={{ fontSize: '9px', color: '#94A3B8', marginTop: '2px' }}>Non-Statutory Container Only</div>
            </div>
            <div style={{ background: 'rgba(239, 68, 68, 0.1)', padding: '10px 14px', borderRadius: '6px', borderLeft: '3px solid #EF4444' }}>
              <div style={{ fontSize: '11px', color: '#FCA5A5' }}>Potential Deficiencies</div>
              <div style={{ fontSize: '20px', fontWeight: '800', color: '#F87171' }}>
                {report.groupedPotentialDeficiencies ?? report.potentialDeficiencies ?? 0}
                <span style={{ fontSize: '11px', fontWeight: '500', color: '#FCA5A5', marginLeft: '4px' }}>Grouped</span>
              </div>
              <div style={{ fontSize: '10px', color: '#FCA5A5', marginTop: '2px' }}>
                {report.potentialDeficiencies ?? report.deficiencyChecks ?? 0} underlying evaluations
              </div>
            </div>
            <div style={{ background: 'rgba(245, 158, 11, 0.1)', padding: '10px 14px', borderRadius: '6px', borderLeft: '3px solid #F59E0B' }}>
              <div style={{ fontSize: '11px', color: '#FCD34D' }}>Manual Review Needed</div>
              <div style={{ fontSize: '20px', fontWeight: '800', color: '#FBBF24' }}>
                {report.manualReviewRequired ?? report.reviewChecks ?? 0}
              </div>
            </div>
            <div style={{ background: 'rgba(148, 163, 184, 0.08)', padding: '10px 14px', borderRadius: '6px' }}>
              <div style={{ fontSize: '11px', color: '#CBD5E1' }}>Assessment Cycle</div>
              <div style={{ fontSize: '20px', fontWeight: '800', color: '#E2E8F0' }}>Cycle #{report.cycleNumber}</div>
            </div>
          </div>
        )}
      </div>

      {/* Status Message Notification */}
      {statusMessage && (
        <div
          className={`callout ${
            statusMessage.type === 'success'
              ? 'callout-blue'
              : statusMessage.type === 'error'
              ? 'callout-amber'
              : 'callout-blue'
          }`}
          style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}
        >
          <span>{statusMessage.text}</span>
          <button onClick={() => setStatusMessage(null)} className="btn btn-sm" style={{ padding: '2px 8px' }}>
            Dismiss
          </button>
        </div>
      )}

      {/* Blocking Deficiency Safeguard Banner */}
      {blockingDeficienciesCount > 0 && (
        <div
          style={{
            background: '#FEF2F2',
            border: '1px solid #FCA5A5',
            borderRadius: '6px',
            padding: '12px 16px',
            marginBottom: '16px',
            display: 'flex',
            alignItems: 'center',
            gap: '12px',
          }}
        >
          <AlertCircle size={20} color="#DC2626" style={{ flexShrink: 0 }} />
          <div style={{ flex: 1, fontSize: '13px', color: '#991B1B' }}>
            <strong>Scrutiny Safeguard Active:</strong> Application has <strong>{blockingDeficienciesCount}</strong> unresolved blocking deficiency(ies). Statutory scrutiny clearance is locked until all rectifications are uploaded by the institution and verified by the authorized Scrutiny Officer.
          </div>
        </div>
      )}

      {/* Sub-Tabs Navigation */}
      <div
        style={{
          display: 'flex',
          gap: '8px',
          borderBottom: '2px solid var(--color-border)',
          marginBottom: '16px',
          flexWrap: 'wrap',
        }}
      >
        <button
          onClick={() => setActiveSubTab('findings')}
          style={{
            padding: '10px 16px',
            fontSize: '13px',
            fontWeight: '600',
            border: 'none',
            background: 'none',
            cursor: 'pointer',
            borderBottom: activeSubTab === 'findings' ? '3px solid #0284C7' : '3px solid transparent',
            color: activeSubTab === 'findings' ? '#0284C7' : 'var(--color-text-muted)',
            display: 'inline-flex',
            alignItems: 'center',
            gap: '6px',
          }}
        >
          <AlertTriangle size={15} /> Preliminary Findings ({findings.length})
        </button>

        <button
          onClick={() => setActiveSubTab('extractions')}
          style={{
            padding: '10px 16px',
            fontSize: '13px',
            fontWeight: '600',
            border: 'none',
            background: 'none',
            cursor: 'pointer',
            borderBottom: activeSubTab === 'extractions' ? '3px solid #0284C7' : '3px solid transparent',
            color: activeSubTab === 'extractions' ? '#0284C7' : 'var(--color-text-muted)',
            display: 'inline-flex',
            alignItems: 'center',
            gap: '6px',
          }}
        >
          <FileText size={15} /> Document &amp; Extracted Evidence ({report?.extractions?.length || 0})
        </button>

        <button
          onClick={() => setActiveSubTab('deficiencies')}
          style={{
            padding: '10px 16px',
            fontSize: '13px',
            fontWeight: '600',
            border: 'none',
            background: 'none',
            cursor: 'pointer',
            borderBottom: activeSubTab === 'deficiencies' ? '3px solid #0284C7' : '3px solid transparent',
            color: activeSubTab === 'deficiencies' ? '#0284C7' : 'var(--color-text-muted)',
            display: 'inline-flex',
            alignItems: 'center',
            gap: '6px',
          }}
        >
          <Shield size={15} /> Deficiencies &amp; Notice ({deficiencies.length})
        </button>

        <button
          onClick={() => setActiveSubTab('rescrutiny')}
          style={{
            padding: '10px 16px',
            fontSize: '13px',
            fontWeight: '600',
            border: 'none',
            background: 'none',
            cursor: 'pointer',
            borderBottom: activeSubTab === 'rescrutiny' ? '3px solid #0284C7' : '3px solid transparent',
            color: activeSubTab === 'rescrutiny' ? '#0284C7' : 'var(--color-text-muted)',
            display: 'inline-flex',
            alignItems: 'center',
            gap: '6px',
          }}
        >
          <FileDiff size={15} /> Automated Re-Scrutiny Diffs ({comparisons.length})
        </button>
      </div>

      {/* --------------------------------------------------------------------- */}
      {/* SUB-TAB 1: PRELIMINARY FINDINGS                                      */}
      {/* --------------------------------------------------------------------- */}
      {activeSubTab === 'findings' && (
        <div>
          {/* Filter Bar, View Mode & Batch Action */}
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginBottom: '14px',
              flexWrap: 'wrap',
              gap: '10px',
            }}
          >
            <div style={{ display: 'flex', gap: '10px', alignItems: 'center', flexWrap: 'wrap' }}>
              {/* View Mode Toggle: Grouped vs All Evaluations */}
              <div style={{ display: 'flex', gap: '3px', background: '#F1F5F9', padding: '3px', borderRadius: '6px', border: '1px solid #E2E8F0' }}>
                <button
                  type="button"
                  onClick={() => setViewMode('GROUPED')}
                  style={{
                    fontSize: '11px',
                    padding: '4px 10px',
                    border: 'none',
                    borderRadius: '4px',
                    fontWeight: '700',
                    cursor: 'pointer',
                    background: viewMode === 'GROUPED' ? '#0284C7' : 'transparent',
                    color: viewMode === 'GROUPED' ? '#FFFFFF' : '#475569',
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: '4px',
                  }}
                >
                  <Layers size={13} /> Grouped View ({report?.totalGroupedChecks || consolidatedFindings.length})
                </button>
                <button
                  type="button"
                  onClick={() => setViewMode('ALL_EVALUATIONS')}
                  style={{
                    fontSize: '11px',
                    padding: '4px 10px',
                    border: 'none',
                    borderRadius: '4px',
                    fontWeight: '700',
                    cursor: 'pointer',
                    background: viewMode === 'ALL_EVALUATIONS' ? '#0284C7' : 'transparent',
                    color: viewMode === 'ALL_EVALUATIONS' ? '#FFFFFF' : '#475569',
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: '4px',
                  }}
                >
                  <FileText size={13} /> All Evaluations ({report?.totalChecks || underlyingFindings.length})
                </button>
              </div>

              {/* Status Outcome Filter */}
              <div style={{ display: 'flex', gap: '4px', alignItems: 'center' }}>
                <span style={{ fontSize: '11px', color: 'var(--color-text-muted)', fontWeight: '600' }}>Filter:</span>
                {['ALL', 'POTENTIAL_DEFICIENCY', 'NEEDS_MANUAL_REVIEW', 'PASS'].map((st) => (
                  <button
                    key={st}
                    onClick={() => setFindingFilter(st)}
                    className={`btn btn-sm ${findingFilter === st ? 'btn-primary' : 'btn-outline'}`}
                    style={{ fontSize: '11px', padding: '3px 8px' }}
                  >
                    {st.replace('_', ' ')}
                  </button>
                ))}
              </div>
            </div>

            {isOfficer && (
              <button
                onClick={() => {
                  const confirmedIds = activeFindingsList
                    .filter((f) => f.officerDecision === 'CONFIRMED' || f.outcome === 'POTENTIAL_DEFICIENCY')
                    .map((f) => f.id);
                  setSelectedFindingIds(confirmedIds);
                  setIsIssueModalOpen(true);
                }}
                className="btn btn-danger btn-sm"
                style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
              >
                <Shield size={14} /> Issue Formal Deficiency Notice
              </button>
            )}
          </div>

          {filteredFindings.length === 0 ? (
            <div className="panel" style={{ padding: '30px', textAlign: 'center', color: 'var(--color-text-muted)' }}>
              <FileCheck size={32} style={{ margin: '0 auto 10px', color: '#10B981' }} />
              <div>No preliminary findings match the selected filter.</div>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
              {filteredFindings.map((finding) => {
                const isDuplicateGroup = finding.isGrouped || finding.ruleCode === 'RULE_DUPLICATE_DOCUMENT_HASH';
                const isDeficiency = finding.outcome === 'POTENTIAL_DEFICIENCY';
                const isReview = finding.outcome === 'NEEDS_MANUAL_REVIEW';
                const isPass = finding.outcome === 'PASS';
                const isTechnical = (finding.findingType === 'TECHNICAL_VALIDATION' || finding.findingType === 'TECHNICAL_FORMAT');

                const badgeBg = isDeficiency ? '#FEF2F2' : isReview ? '#FFFBEB' : '#F0FDF4';
                const badgeColor = isDeficiency ? '#DC2626' : isReview ? '#D97706' : '#16A34A';
                const borderColor = isDeficiency ? '#FECACA' : isReview ? '#FDE68A' : isTechnical ? '#BAE6FD' : '#BBF7D0';

                const docName = finding.documentName || finding.sourceDocument || 'Uploaded Document';
                const ruleName = finding.ruleName || finding.findingName || finding.ruleCode;
                const ruleCode = finding.ruleCode || finding.id;
                const ruleRef = finding.ruleReference || finding.regulatoryRuleReference || 'APH Norms';
                const explanation = finding.explanation || finding.findingDescription || 'No explanation provided.';

                // Render consolidated Duplicate Document Detected card
                if (isDuplicateGroup) {
                  const isExpanded = !!expandedGroupIds[finding.id || finding.ruleCode];
                  const categoryList = (finding.affectedCategoryNames && finding.affectedCategoryNames.length > 0)
                    ? finding.affectedCategoryNames
                    : (finding.affectedCategories || []);

                  return (
                    <div
                      key={`grouped-${finding.id || finding.ruleCode}`}
                      style={{
                        background: '#FFFFFF',
                        border: '1px solid #FCA5A5',
                        borderLeft: '5px solid #DC2626',
                        borderRadius: '8px',
                        padding: '18px',
                        boxShadow: '0 2px 8px rgba(220, 38, 38, 0.08)',
                      }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '10px' }}>
                        <div style={{ flex: 1, minWidth: '280px' }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px', flexWrap: 'wrap' }}>
                            <span style={{ fontSize: '11px', fontWeight: '700', padding: '2px 8px', borderRadius: '4px', background: '#FEF2F2', color: '#DC2626', border: '1px solid #FECACA' }}>
                              POTENTIAL DEFICIENCY
                            </span>
                            <span style={{ fontSize: '11px', fontWeight: '700', padding: '2px 8px', borderRadius: '4px', background: '#EDE9FE', color: '#6D28D9', border: '1px solid #DDD6FE' }}>
                              CONSOLIDATED FINDING
                            </span>
                            <span style={{ fontSize: '10px', background: '#DC2626', color: '#FFFFFF', padding: '1px 6px', borderRadius: '3px', fontWeight: '700' }}>
                              BLOCKING
                            </span>
                            <span style={{ fontSize: '11px', color: '#64748B', fontWeight: '600' }}>
                              {categoryList.length} Categories Affected
                            </span>
                          </div>

                          <h3 style={{ fontSize: '16px', fontWeight: '800', color: '#991B1B', margin: '0 0 6px 0', display: 'flex', alignItems: 'center', gap: '8px' }}>
                            <AlertTriangle size={18} color="#DC2626" />
                            Duplicate Document Detected
                          </h3>

                          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '13px', color: '#1E293B', marginBottom: '10px' }}>
                            <FileText size={15} color="#0284C7" />
                            <span>File:</span>
                            <strong style={{ color: '#0284C7', fontFamily: 'monospace' }}>{docName}</strong>
                          </div>

                          {/* Affected categories pills */}
                          <div style={{ marginBottom: '10px' }}>
                            <div style={{ fontSize: '12px', fontWeight: '700', color: '#475569', marginBottom: '6px' }}>
                              Affected categories:
                            </div>
                            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
                              {categoryList.map((cat, idx) => (
                                <span
                                  key={idx}
                                  style={{
                                    fontSize: '11px',
                                    fontWeight: '600',
                                    padding: '3px 10px',
                                    borderRadius: '12px',
                                    background: '#FEF3C7',
                                    color: '#92400E',
                                    border: '1px solid #FCD34D',
                                  }}
                                >
                                  {cat}
                                </span>
                              ))}
                            </div>
                          </div>
                        </div>

                        {/* Officer Status and Consolidated Review */}
                        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: '8px' }}>
                          <span
                            style={{
                              fontSize: '11px',
                              padding: '3px 8px',
                              borderRadius: '4px',
                              background: (finding.officerDecision === 'CONFIRMED' || finding.officerDecision === 'CONFIRMED_DEFICIENCY') ? '#FEF2F2'
                                : finding.officerDecision === 'DISMISSED' ? '#F1F5F9'
                                : finding.officerDecision === 'REQUEST_MANUAL_VERIFICATION' ? '#FEF3C7'
                                : finding.officerDecision === 'MANUAL_VERIFIED' ? '#ECFDF5'
                                : '#FFFBEB',
                              color: (finding.officerDecision === 'CONFIRMED' || finding.officerDecision === 'CONFIRMED_DEFICIENCY') ? '#DC2626'
                                : finding.officerDecision === 'DISMISSED' ? '#475569'
                                : finding.officerDecision === 'REQUEST_MANUAL_VERIFICATION' ? '#D97706'
                                : finding.officerDecision === 'MANUAL_VERIFIED' ? '#059669'
                                : '#B45309',
                              fontWeight: '700',
                              border: '1px solid rgba(0,0,0,0.06)',
                            }}
                          >
                            Officer: {finding.officerDecision || 'PENDING'}
                          </span>

                          {isOfficer && (
                            <button
                              onClick={() => {
                                setReviewingFinding(finding);
                                const initDec = (!finding.officerDecision || finding.officerDecision === 'PENDING')
                                  ? 'CONFIRMED_DEFICIENCY'
                                  : (finding.officerDecision === 'CONFIRMED' ? 'CONFIRMED_DEFICIENCY' : finding.officerDecision);
                                setFindingReviewDecision(initDec);
                                setFindingReviewRemarks(finding.officerRemarks || '');
                                setApplyToAllDuplicates(true);
                              }}
                              className="btn btn-outline btn-sm"
                              style={{ fontSize: '11px', padding: '4px 10px', borderColor: '#DC2626', color: '#DC2626', fontWeight: '700' }}
                            >
                              Review Consolidated
                            </button>
                          )}
                        </div>
                      </div>

                      {/* Explanation & Recommended Action block */}
                      <div
                        style={{
                          background: '#FFF5F5',
                          border: '1px solid #FED7D7',
                          borderRadius: '6px',
                          padding: '12px 14px',
                          marginTop: '6px',
                          fontSize: '12px',
                          lineHeight: '1.5',
                        }}
                      >
                        <div style={{ color: '#742A2A', marginBottom: '6px' }}>
                          <strong>Explanation:</strong> {explanation}
                        </div>
                        <div style={{ color: '#742A2A' }}>
                          <strong>Recommended action:</strong> {finding.recommendedAction || 'Review whether independent category-specific evidence is required.'}
                        </div>
                      </div>

                      {/* Expandable Drill-down for Traceability */}
                      <div style={{ marginTop: '12px', paddingTop: '10px', borderTop: '1px dashed #E2E8F0' }}>
                        <button
                          onClick={() => toggleGroupExpand(finding.id || finding.ruleCode)}
                          style={{
                            background: 'none',
                            border: 'none',
                            color: '#0284C7',
                            cursor: 'pointer',
                            fontSize: '12px',
                            fontWeight: '700',
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: '6px',
                            padding: 0,
                          }}
                        >
                          {isExpanded ? <ChevronDown size={15} /> : <ChevronRight size={15} />}
                          {isExpanded
                            ? 'Hide Underlying Requirement Audits'
                            : `View Underlying Requirement Audits (${(finding.underlyingFindings || []).length} Traceable Records)`}
                        </button>

                        {isExpanded && (
                          <div style={{ marginTop: '10px', background: '#F8FAFC', borderRadius: '6px', padding: '12px', border: '1px solid #E2E8F0' }}>
                            <div style={{ fontSize: '11px', color: '#64748B', marginBottom: '8px' }}>
                              Underlying per-document audit references preserved for individual statutory investigation:
                            </div>
                            <div style={{ overflowX: 'auto' }}>
                              <table style={{ width: '100%', fontSize: '12px', borderCollapse: 'collapse', textAlign: 'left' }}>
                                <thead>
                                  <tr style={{ borderBottom: '2px solid #CBD5E1', color: '#475569', fontSize: '11px' }}>
                                    <th style={{ padding: '6px 8px' }}>Requirement</th>
                                    <th style={{ padding: '6px 8px' }}>Category Name</th>
                                    <th style={{ padding: '6px 8px' }}>Document Name</th>
                                    <th style={{ padding: '6px 8px' }}>Rule Evaluation</th>
                                    <th style={{ padding: '6px 8px' }}>Decision</th>
                                    {isOfficer && <th style={{ padding: '6px 8px', textAlign: 'right' }}>Requirement Action</th>}
                                  </tr>
                                </thead>
                                <tbody>
                                  {(finding.underlyingFindings || []).map((uf) => (
                                    <tr key={uf.id} style={{ borderBottom: '1px solid #E2E8F0' }}>
                                      <td style={{ padding: '8px', fontWeight: '700', color: '#1E293B' }}>
                                        {uf.requirementCode}
                                      </td>
                                      <td style={{ padding: '8px', color: '#475569' }}>
                                        {uf.requirementCode?.replace('DOC_', '').replace(/_/g, ' ')}
                                      </td>
                                      <td style={{ padding: '8px', color: '#0284C7' }}>{uf.documentName}</td>
                                      <td style={{ padding: '8px', color: '#64748B', fontSize: '11px' }}>{uf.ruleCode}</td>
                                      <td style={{ padding: '8px' }}>
                                        <span style={{
                                          fontSize: '10px',
                                          padding: '2px 6px',
                                          borderRadius: '3px',
                                          fontWeight: '700',
                                          background: (uf.officerDecision === 'CONFIRMED' || uf.officerDecision === 'CONFIRMED_DEFICIENCY') ? '#FEE2E2'
                                            : uf.officerDecision === 'DISMISSED' ? '#F1F5F9'
                                            : uf.officerDecision === 'REQUEST_MANUAL_VERIFICATION' ? '#FEF3C7'
                                            : uf.officerDecision === 'MANUAL_VERIFIED' ? '#ECFDF5'
                                            : '#FFFBEB',
                                          color: (uf.officerDecision === 'CONFIRMED' || uf.officerDecision === 'CONFIRMED_DEFICIENCY') ? '#DC2626'
                                            : uf.officerDecision === 'DISMISSED' ? '#475569'
                                            : uf.officerDecision === 'REQUEST_MANUAL_VERIFICATION' ? '#D97706'
                                            : uf.officerDecision === 'MANUAL_VERIFIED' ? '#059669'
                                            : '#B45309',
                                        }}>
                                          {uf.officerDecision || 'PENDING'}
                                        </span>
                                      </td>
                                      {isOfficer && (
                                        <td style={{ padding: '8px', textAlign: 'right' }}>
                                          <button
                                            onClick={() => {
                                              setReviewingFinding(uf);
                                              const initDec = (!uf.officerDecision || uf.officerDecision === 'PENDING')
                                                ? 'CONFIRMED_DEFICIENCY'
                                                : (uf.officerDecision === 'CONFIRMED' ? 'CONFIRMED_DEFICIENCY' : uf.officerDecision);
                                              setFindingReviewDecision(initDec);
                                              setFindingReviewRemarks(uf.officerRemarks || '');
                                              setApplyToAllDuplicates(false);
                                            }}
                                            className="btn btn-outline btn-sm"
                                            style={{ fontSize: '11px', padding: '2px 8px' }}
                                          >
                                            Review
                                          </button>
                                        </td>
                                      )}
                                    </tr>
                                  ))}
                                </tbody>
                              </table>
                            </div>
                          </div>
                        )}
                      </div>
                    </div>
                  );
                }

                // Render standard individual finding card
                return (
                  <div
                    key={finding.id}
                    style={{
                      background: '#FFFFFF',
                      border: `1px solid ${borderColor}`,
                      borderLeft: `4px solid ${isTechnical && isPass ? '#0284C7' : badgeColor}`,
                      borderRadius: '6px',
                      padding: '16px',
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '8px' }}>
                      <div style={{ flex: 1, minWidth: '280px' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '6px', flexWrap: 'wrap' }}>
                          <span
                            style={{
                              fontSize: '11px',
                              fontWeight: '700',
                              padding: '2px 8px',
                              borderRadius: '4px',
                              background: badgeBg,
                              color: badgeColor,
                            }}
                          >
                            {finding.outcome.replace('_', ' ')}
                          </span>

                          {isTechnical ? (
                            <span style={{ fontSize: '10px', background: '#E0F2FE', color: '#0369A1', padding: '2px 8px', borderRadius: '4px', fontWeight: '700', border: '1px solid #BAE6FD' }}>
                              TECHNICAL VALIDATION (NON-STATUTORY)
                            </span>
                          ) : (
                            <span style={{ fontSize: '10px', background: '#F1F5F9', color: '#334155', padding: '2px 8px', borderRadius: '4px', fontWeight: '700', border: '1px solid #CBD5E1' }}>
                              STATUTORY COMPLIANCE
                            </span>
                          )}

                          <span style={{ fontSize: '11px', color: 'var(--color-text-muted)', fontWeight: '600' }}>
                            {finding.requirementCode || finding.documentRequirementCode}
                          </span>

                          {finding.severity === 'BLOCKING' && (
                            <span style={{ fontSize: '10px', background: '#DC2626', color: '#FFFFFF', padding: '1px 6px', borderRadius: '3px', fontWeight: '700' }}>
                              BLOCKING
                            </span>
                          )}
                        </div>

                        <h4 style={{ fontSize: '14px', fontWeight: '700', color: 'var(--color-navy)', margin: '0 0 4px 0' }}>
                          {ruleName}
                        </h4>

                        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '12px', fontSize: '12px', color: '#64748B', marginBottom: '8px' }}>
                          <div>
                            Rule Code: <strong style={{ color: '#0F172A' }}>{ruleCode}</strong>
                          </div>
                          <div>
                            Reference: <strong>{ruleRef}</strong>
                          </div>
                          <div style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', color: '#0284C7' }}>
                            <FileText size={13} />
                            Source Document: <strong style={{ color: '#0284C7' }}>{docName}</strong>
                          </div>
                        </div>
                      </div>

                      {/* Officer Decision status / action */}
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <span
                          style={{
                            fontSize: '11px',
                            padding: '3px 8px',
                            borderRadius: '4px',
                            background:
                              (finding.officerDecision === 'CONFIRMED' || finding.officerDecision === 'CONFIRMED_DEFICIENCY')
                                ? '#FEF2F2'
                                : finding.officerDecision === 'DISMISSED'
                                ? '#F1F5F9'
                                : (finding.officerDecision === 'REQUEST_MANUAL_VERIFICATION' || finding.officerDecision === 'MANUAL_VERIFIED')
                                ? '#EFF6FF'
                                : '#FFFBEB',
                            color:
                              (finding.officerDecision === 'CONFIRMED' || finding.officerDecision === 'CONFIRMED_DEFICIENCY')
                                ? '#DC2626'
                                : finding.officerDecision === 'DISMISSED'
                                ? '#475569'
                                : (finding.officerDecision === 'REQUEST_MANUAL_VERIFICATION' || finding.officerDecision === 'MANUAL_VERIFIED')
                                ? '#1D4ED8'
                                : '#B45309',
                            fontWeight: '600',
                          }}
                        >
                          Officer: {finding.officerDecision || 'PENDING'}
                        </span>

                        {isOfficer && (
                          <button
                            onClick={() => {
                              setReviewingFinding(finding);
                              const initDec = (finding.officerDecision && finding.officerDecision !== 'PENDING')
                                ? (finding.officerDecision === 'CONFIRMED' ? 'CONFIRMED_DEFICIENCY' : finding.officerDecision)
                                : 'CONFIRMED_DEFICIENCY';
                              setFindingReviewDecision(initDec);
                              setFindingReviewRemarks(finding.officerRemarks || '');
                              setApplyToAllDuplicates(false);
                            }}
                            className="btn btn-outline btn-sm"
                            style={{ fontSize: '11px', padding: '4px 8px' }}
                          >
                            Review Finding
                          </button>
                        )}
                      </div>
                    </div>

                    {/* Value comparison table */}
                    <div
                      style={{
                        display: 'grid',
                        gridTemplateColumns: '1fr 1fr',
                        gap: '10px',
                        background: '#F8FAFC',
                        padding: '10px 14px',
                        borderRadius: '4px',
                        marginTop: '8px',
                        fontSize: '12px',
                        border: '1px solid #E2E8F0',
                      }}
                    >
                      <div>
                        <div style={{ color: '#64748B', fontSize: '11px', fontWeight: '600' }}>EXPECTED STATUTORY CONDITION</div>
                        <div style={{ color: '#0F172A', marginTop: '2px' }}>{finding.expectedCondition || 'N/A'}</div>
                      </div>
                      <div>
                        <div style={{ color: '#64748B', fontSize: '11px', fontWeight: '600' }}>EXTRACTED EVIDENCE VALUE</div>
                        <div style={{ color: isDeficiency ? '#DC2626' : '#0F172A', fontWeight: isDeficiency ? '700' : '500', marginTop: '2px' }}>
                          {finding.actualValue || 'Not detected'}
                        </div>
                      </div>
                    </div>

                    <div style={{ fontSize: '12px', color: '#334155', marginTop: '10px', lineHeight: '1.5' }}>
                      <strong>Statutory Explanation:</strong> {explanation}
                    </div>

                    {isTechnical && isPass && (
                      <div style={{ fontSize: '11px', color: '#0369A1', background: '#F0F9FF', padding: '6px 10px', borderRadius: '4px', marginTop: '8px', borderLeft: '3px solid #0284C7' }}>
                        ℹ️ <strong>Scope Notice:</strong> Technical file format and readable stream verified. This technical check validates container structure only and does NOT establish substantive statutory compliance.
                      </div>
                    )}

                    {finding.recommendedAction && (
                      <div style={{ fontSize: '11px', color: '#0284C7', marginTop: '6px' }}>
                        <strong>Recommended Officer Action:</strong> {finding.recommendedAction}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* --------------------------------------------------------------------- */}
      {/* --------------------------------------------------------------------- */}
      {/* SUB-TAB 2: DOCUMENT OCR & EXTRACTIONS                                */}
      {/* --------------------------------------------------------------------- */}
      {activeSubTab === 'extractions' && (
        <div>
          <div
            style={{
              marginBottom: '16px',
              padding: '12px 16px',
              background: '#F0F9FF',
              border: '1px solid #BAE6FD',
              borderRadius: '6px',
              fontSize: '12px',
              color: '#0369A1',
              lineHeight: '1.5',
            }}
          >
            <div style={{ fontWeight: '700', marginBottom: '4px', display: 'flex', alignItems: 'center', gap: '6px', color: '#0369A1' }}>
              <AlertCircle size={15} /> Document Extraction &amp; Verification Transparency
            </div>
            <div>
              <strong>Digital PDFBox Extraction:</strong> Directly parses native font character streams embedded in searchable PDFs. No optical recognition is performed; confidence is reported as <em>N/A (Embedded Text)</em> without artificial score fabrication.
            </div>
            <div style={{ marginTop: '2px' }}>
              <strong>Scanned / Image Documents:</strong> The legacy Tess4J OCR engine has been retired. Scanned documents or images lacking extractable digital text are flagged as <em>UNAVAILABLE</em> for manual Scrutiny Officer inspection.
            </div>
            <div style={{ marginTop: '4px', fontSize: '11px', color: '#0284C7', fontStyle: 'italic' }}>
              * Regulatory Note: Digital extraction legibility does not independently substantiate legal authenticity or statutory compliance.
            </div>
          </div>

          {/* Empty State when no extractions exist */}
          {(!report?.extractions || report.extractions.length === 0) ? (
            <div
              style={{
                textAlign: 'center',
                padding: '48px 24px',
                background: '#FFFFFF',
                border: '1px solid var(--color-border)',
                borderRadius: '8px',
              }}
            >
              <FileText size={40} color="#94A3B8" style={{ marginBottom: '12px' }} />
              <h4 style={{ fontSize: '15px', fontWeight: '700', color: 'var(--color-navy)', margin: '0 0 6px 0' }}>
                No Document Extraction Records Found
              </h4>
              <p style={{ fontSize: '13px', color: '#64748B', maxWidth: '440px', margin: '0 auto 16px auto', lineHeight: '1.5' }}>
                No statutory documents have been processed for automated extraction in this assessment cycle. Upload documents and run automated scrutiny to inspect OCR evidence.
              </p>
              <button
                onClick={handleRunAutomatedScrutiny}
                disabled={runningScrutiny}
                className="btn btn-primary btn-sm"
                style={{ gap: '6px' }}
              >
                <RefreshCw size={14} className={runningScrutiny ? 'spin' : ''} />
                {runningScrutiny ? 'Processing Documents...' : 'Run Automated Scrutiny'}
              </button>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
              {(report?.extractions || []).map((ext) => {
                const isExpanded = expandedExtraction === ext.id;
                const isPdfBox = ext.extractionMethod === 'PDFBOX_SEARCHABLE_TEXT' || ext.extractionMethod === 'APACHE_PDFBOX_TEXT';
                const isRetiredOcr = ext.extractionMethod === 'OCR_RETIRED_UNAVAILABLE' || ext.extractionMethod === 'OCR_UNAVAILABLE_FALLBACK';
                const isOcr = ext.extractionMethod === 'TESSERACT_OCR_SCANNED' || ext.extractionMethod === 'TESSERACT_OCR_IMAGE' || isRetiredOcr;
                const isFailed = ext.ocrStatus === 'FAILED';
                const isBlank = ext.ocrStatus === 'EMPTY' || ext.extractionMethod === 'EMPTY_DOCUMENT';
                const isUncertain = ext.ocrStatus === 'UNCERTAIN';
                const isUnavailable = ext.ocrStatus === 'UNAVAILABLE';

                let rawFields = {};
                try {
                  rawFields = JSON.parse(ext.extractedFieldsJson || '{}');
                } catch (e) {
                  rawFields = {};
                }

                // Retrieve field sources mapping from DTO fieldSources or embedded _fieldSources
                const sourcesMap = ext.fieldSources || rawFields._fieldSources || {};
                
                // Collect visible fields excluding internal _fieldSources
                const visibleFieldEntries = Object.entries(rawFields).filter(([k]) => k !== '_fieldSources');

                // Helper for badge styling based on source category
                const getSourceBadgeStyle = (sourceType) => {
                  switch (sourceType) {
                    case 'ACTUAL_DOCUMENT_CONTENT':
                      return {
                        background: '#DCFCE7',
                        color: '#15803D',
                        border: '1px solid #86EFAC',
                        label: 'Actual Document Content',
                      };
                    case 'FILE_METADATA':
                      return {
                        background: '#E0F2FE',
                        color: '#0369A1',
                        border: '1px solid #7DD3FC',
                        label: 'File Metadata',
                      };
                    case 'APPLICATION_METADATA':
                      return {
                        background: '#F3E8FF',
                        color: '#7E22CE',
                        border: '1px solid #D8B4FE',
                        label: 'Application Metadata',
                      };
                    case 'DERIVED_VALUE':
                    default:
                      return {
                        background: '#FEF3C7',
                        color: '#B45309',
                        border: '1px solid #FCD34D',
                        label: 'Derived Value',
                      };
                  }
                };

                return (
                  <div
                    key={ext.id}
                    style={{
                      background: '#FFFFFF',
                      border: isFailed ? '1px solid #FCA5A5' : '1px solid var(--color-border)',
                      borderRadius: '8px',
                      overflow: 'hidden',
                      boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
                    }}
                  >
                    {/* Header Row */}
                    <div
                      onClick={() => setExpandedExtraction(isExpanded ? null : ext.id)}
                      style={{
                        padding: '14px 18px',
                        cursor: 'pointer',
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        backgroundColor: isExpanded ? '#F8FAFC' : '#FFFFFF',
                        borderLeft: isFailed
                          ? '4px solid #EF4444'
                          : isBlank
                          ? '4px solid #F59E0B'
                          : '4px solid #0284C7',
                      }}
                    >
                      <div style={{ flex: 1, marginRight: '16px' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
                          <span
                            style={{
                              fontSize: '11px',
                              fontWeight: '700',
                              padding: '2px 8px',
                              borderRadius: '4px',
                              background: '#E2E8F0',
                              color: '#334155',
                              letterSpacing: '0.3px',
                            }}
                          >
                            {ext.requirementCode}
                          </span>
                          <h4 style={{ fontSize: '14px', fontWeight: '700', color: 'var(--color-navy)', margin: 0 }}>
                            {ext.documentName}
                          </h4>
                          <span
                            style={{
                              fontSize: '11px',
                              fontWeight: '600',
                              padding: '1px 6px',
                              borderRadius: '3px',
                              background: '#F1F5F9',
                              color: '#64748B',
                            }}
                          >
                            v{ext.documentVersion}
                          </span>
                        </div>

                        {/* Metadata summary line */}
                        <div
                          style={{
                            fontSize: '11px',
                            color: '#64748B',
                            marginTop: '6px',
                            display: 'flex',
                            flexWrap: 'wrap',
                            gap: '8px',
                            alignItems: 'center',
                          }}
                        >
                          {ext.originalFileName && (
                            <span style={{ display: 'flex', alignItems: 'center', gap: '4px', color: '#334155', fontWeight: '500' }}>
                              <FileText size={12} color="#0284C7" /> {ext.originalFileName}
                            </span>
                          )}
                          <span>·</span>
                          <span>
                            Method: <strong>{isPdfBox ? 'PDFBox (Digital Text)' : isRetiredOcr ? 'Legacy OCR Retired (Manual Review)' : isOcr ? (ext.extractionMethod === 'TESSERACT_OCR_SCANNED' ? 'Legacy OCR (Scanned)' : 'Legacy OCR (Image)') : ext.extractionMethod}</strong>
                          </span>
                          <span>·</span>
                          <span>
                            Status:{' '}
                            <strong
                              style={{
                                color:
                                  ext.ocrStatus === 'SUCCESS' ? '#16A34A' : ext.ocrStatus === 'UNCERTAIN' ? '#D97706' : ext.ocrStatus === 'UNAVAILABLE' ? '#D97706' : '#DC2626',
                              }}
                            >
                              {isPdfBox && ext.ocrStatus === 'SUCCESS' ? 'SUCCESS (Native Text)' : isUnavailable ? 'UNAVAILABLE (Manual Review Required)' : ext.ocrStatus}
                            </strong>
                          </span>
                          <span>·</span>
                          <span>
                            Confidence:{' '}
                            {ext.confidenceScore != null ? (
                              <strong style={{ color: ext.confidenceScore < 0.60 ? '#D97706' : '#0F172A' }}>
                                {Math.round(ext.confidenceScore * 1000) / 10}% (OCR Measured)
                              </strong>
                            ) : (
                              <span style={{ color: '#64748B', fontStyle: 'italic', background: '#F1F5F9', padding: '1px 6px', borderRadius: '3px' }}>
                                {isPdfBox ? 'N/A (Embedded Text)' : isRetiredOcr ? 'N/A (OCR Retired)' : isBlank ? 'N/A (Empty Document)' : ext.validationMetric || 'N/A'}
                              </span>
                            )}
                          </span>
                          {(ext.textCharacterCount != null || ext.extractedTextSnippet) && (
                            <>
                              <span>·</span>
                              <span style={{ color: '#475569' }}>
                                {(ext.textCharacterCount != null ? ext.textCharacterCount : (ext.extractedTextSnippet?.length || 0)).toLocaleString()} chars
                                {ext.pageCount ? ` (${ext.pageCount} ${ext.pageCount === 1 ? 'page' : 'pages'})` : ''}
                              </span>
                            </>
                          )}
                        </div>
                      </div>

                      <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                        <span className="badge badge-blue">
                          {visibleFieldEntries.length} Fields Extracted
                        </span>
                        {isExpanded ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                      </div>
                    </div>

                    {/* Expanded Detail Panel */}
                    {isExpanded && (
                      <div style={{ padding: '16px 20px', borderTop: '1px solid var(--color-border-subtle)', background: '#FFFFFF' }}>
                        
                        {/* 1. MinIO Object Traceability & Document Access */}
                        <div
                          style={{
                            background: '#F8FAFC',
                            border: '1px solid #E2E8F0',
                            borderRadius: '6px',
                            padding: '12px 16px',
                            marginBottom: '16px',
                            display: 'flex',
                            justifyContent: 'space-between',
                            alignItems: 'center',
                            flexWrap: 'wrap',
                            gap: '12px',
                          }}
                        >
                          <div>
                            <div style={{ fontSize: '11px', fontWeight: '700', color: '#0F172A', display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '4px' }}>
                              <Shield size={14} color="#0284C7" /> Storage & Audit Traceability (PostgreSQL ↔ MinIO S3 Object)
                            </div>
                            <div style={{ fontSize: '11px', color: '#475569', display: 'flex', flexWrap: 'wrap', gap: '14px' }}>
                              <span>
                                <strong>MinIO Key:</strong>{' '}
                                <code style={{ background: '#E2E8F0', padding: '1px 6px', borderRadius: '3px', color: '#0F172A', fontSize: '11px' }}>
                                  {ext.storagePath || `sage-application-documents/APP-${ext.applicationId}/${ext.requirementCode}/${ext.originalFileName || 'doc.pdf'}`}
                                </code>
                              </span>
                              {ext.fileHash && (
                                <span>
                                  <strong>SHA-256 Digest:</strong>{' '}
                                  <code style={{ background: '#E2E8F0', padding: '1px 6px', borderRadius: '3px', color: '#0F172A', fontSize: '10px' }} title={ext.fileHash}>
                                    {ext.fileHash.substring(0, 16)}...
                                  </code>
                                </span>
                              )}
                              <span>
                                <strong>Assessment Cycle:</strong>{' '}
                                <span style={{ color: '#0F172A' }}>
                                  {ext.assessmentReference || `Assessment #${ext.assessmentId}`} (Cycle {ext.cycleNumber || 1})
                                </span>
                              </span>
                            </div>
                          </div>

                          {/* Open Original Document Secure Action */}
                          <div style={{ display: 'flex', gap: '8px' }}>
                            <button
                              onClick={(e) => {
                                e.stopPropagation();
                                handleOpenOriginalDocument(ext);
                              }}
                              className="btn btn-outline btn-sm"
                              style={{
                                borderColor: '#0284C7',
                                color: '#0284C7',
                                gap: '6px',
                                background: '#F0F9FF',
                                fontWeight: '600',
                              }}
                              title="Securely stream original PDF bytes stored in MinIO"
                            >
                              <ExternalLink size={14} /> Open Original Document (MinIO)
                            </button>
                          </div>
                        </div>

                        {/* Explicit Failure and Empty States */}
                        {isFailed && (
                          <div
                            style={{
                              marginBottom: '16px',
                              padding: '12px 16px',
                              background: '#FEF2F2',
                              border: '1px solid #FECACA',
                              borderRadius: '6px',
                              fontSize: '12px',
                              color: '#DC2626',
                            }}
                          >
                            <div style={{ fontWeight: '700', marginBottom: '3px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                              <XCircle size={15} /> Document Extraction Failed
                            </div>
                            <div>
                              {ext.errorMessage || 'The document stream could not be parsed by PDFBox. The binary file may be corrupted, password-protected, or in an unsupported format.'}
                            </div>
                          </div>
                        )}

                        {isBlank && (
                          <div
                            style={{
                              marginBottom: '16px',
                              padding: '12px 16px',
                              background: '#FFFBEB',
                              border: '1px solid #FDE68A',
                              borderRadius: '6px',
                              fontSize: '12px',
                              color: '#B45309',
                            }}
                          >
                            <div style={{ fontWeight: '700', marginBottom: '3px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                              <AlertTriangle size={15} /> Empty Document Detected
                            </div>
                            <div>
                              The uploaded document payload contains 0 bytes or has 0 extractable text characters across all pages. Flagged for institutional re-upload or officer manual review.
                            </div>
                          </div>
                        )}

                        {isUncertain && (
                          <div
                            style={{
                              marginBottom: '16px',
                              padding: '12px 16px',
                              background: '#FFFBEB',
                              border: '1px solid #FDE68A',
                              borderRadius: '6px',
                              fontSize: '12px',
                              color: '#B45309',
                            }}
                          >
                            <div style={{ fontWeight: '700', marginBottom: '3px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                              <AlertCircle size={15} /> Minimal Text Content (&lt; 40 characters)
                            </div>
                            <div>
                              Document has insufficient character density for definitive automated validation. OCR confidence cannot be safely inferred.
                            </div>
                          </div>
                        )}

                        {/* Extraction Diagnostics Grid */}
                        <h5 style={{ fontSize: '11px', fontWeight: '700', color: '#475569', marginBottom: '8px', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
                          Extraction Pipeline Diagnostics
                        </h5>
                        <div
                          style={{
                            display: 'grid',
                            gridTemplateColumns: 'repeat(auto-fit, minmax(170px, 1fr))',
                            gap: '10px',
                            marginBottom: '16px',
                            background: '#F8FAFC',
                            padding: '12px',
                            borderRadius: '6px',
                            border: '1px solid #E2E8F0',
                          }}
                        >
                          <div>
                            <div style={{ fontSize: '10px', color: '#64748B', fontWeight: '700', textTransform: 'uppercase' }}>Extraction Engine</div>
                            <div style={{ fontSize: '12px', color: '#0F172A', fontWeight: '600', marginTop: '2px' }}>
                              {ext.extractionMethod}
                            </div>
                          </div>
                          <div>
                            <div style={{ fontSize: '10px', color: '#64748B', fontWeight: '700', textTransform: 'uppercase' }}>Extraction Status</div>
                            <div style={{ fontSize: '12px', fontWeight: '700', marginTop: '2px', color: ext.ocrStatus === 'SUCCESS' ? '#16A34A' : ext.ocrStatus === 'UNAVAILABLE' ? '#D97706' : '#DC2626' }}>
                              {ext.ocrStatus}
                            </div>
                          </div>
                          <div>
                            <div style={{ fontSize: '10px', color: '#64748B', fontWeight: '700', textTransform: 'uppercase' }}>Optical Reliability</div>
                            <div style={{ fontSize: '12px', color: '#0F172A', fontWeight: '600', marginTop: '2px' }}>
                              {ext.confidenceScore != null ? `${Math.round(ext.confidenceScore * 1000) / 10}% (Measured)` : (isPdfBox ? 'N/A (Direct Digital Extraction)' : 'N/A (OCR Engine Retired)')}
                            </div>
                          </div>
                          <div>
                            <div style={{ fontSize: '10px', color: '#64748B', fontWeight: '700', textTransform: 'uppercase' }}>Validation Metric</div>
                            <div style={{ fontSize: '12px', color: '#0F172A', fontWeight: '600', marginTop: '2px' }}>
                              {ext.validationMetric || (ext.confidenceScore != null ? 'OCR_MEASURED' : (isPdfBox ? 'N/A (Embedded Text)' : 'N/A (OCR Retired)'))}
                            </div>
                          </div>
                          <div>
                            <div style={{ fontSize: '10px', color: '#64748B', fontWeight: '700', textTransform: 'uppercase' }}>Character Count</div>
                            <div style={{ fontSize: '12px', color: '#0F172A', fontWeight: '600', marginTop: '2px' }}>
                              {(ext.textCharacterCount != null ? ext.textCharacterCount : (ext.extractedTextSnippet?.length || 0)).toLocaleString()} chars · {ext.pageCount || 1} {ext.pageCount === 1 ? 'page' : 'pages'}
                            </div>
                          </div>
                        </div>

                        {/* Extracted Structured Fields with Explicit Provenance Sources */}
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                          <h5 style={{ fontSize: '12px', fontWeight: '700', color: '#475569', margin: 0, textTransform: 'uppercase' }}>
                            Extracted Fields & Source Provenance
                          </h5>
                          <div style={{ fontSize: '11px', color: '#64748B', display: 'flex', gap: '8px', alignItems: 'center' }}>
                            <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                              <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: '#16A34A' }}></span> Document Content
                            </span>
                            <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                              <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: '#0284C7' }}></span> File Metadata
                            </span>
                            <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                              <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: '#D97706' }}></span> Derived Value
                            </span>
                          </div>
                        </div>

                        {visibleFieldEntries.length > 0 ? (
                          <div
                            style={{
                              display: 'grid',
                              gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
                              gap: '12px',
                              marginBottom: '16px',
                            }}
                          >
                            {visibleFieldEntries.map(([k, v]) => {
                              const sourceInfo = sourcesMap[k] || {};
                              const badgeStyle = getSourceBadgeStyle(sourceInfo.source);

                              return (
                                <div
                                  key={k}
                                  style={{
                                    background: '#F8FAFC',
                                    border: '1px solid #E2E8F0',
                                    borderRadius: '6px',
                                    padding: '10px 14px',
                                    display: 'flex',
                                    flexDirection: 'column',
                                    justifyContent: 'space-between',
                                  }}
                                >
                                  <div>
                                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '6px', marginBottom: '4px' }}>
                                      <div style={{ fontSize: '11px', color: '#64748B', textTransform: 'uppercase', fontWeight: '700' }}>
                                        {sourceInfo.fieldLabel || k}
                                      </div>
                                      <span
                                        style={{
                                          fontSize: '10px',
                                          fontWeight: '700',
                                          padding: '2px 6px',
                                          borderRadius: '3px',
                                          background: badgeStyle.background,
                                          color: badgeStyle.color,
                                          border: badgeStyle.border,
                                          whiteSpace: 'nowrap',
                                        }}
                                      >
                                        {sourceInfo.sourceLabel || badgeStyle.label}
                                      </span>
                                    </div>

                                    <div style={{ fontSize: '13px', color: '#0F172A', fontWeight: '700', marginBottom: '6px' }}>
                                      {String(v)}
                                    </div>
                                  </div>

                                  {(sourceInfo.ruleOrPattern || sourceInfo.evidenceSnippet) && (
                                    <div style={{ fontSize: '10px', color: '#64748B', background: '#FFFFFF', padding: '6px 8px', borderRadius: '4px', border: '1px solid #E2E8F0', marginTop: '4px' }}>
                                      {sourceInfo.ruleOrPattern && (
                                        <div>
                                          <strong>Rule:</strong> {sourceInfo.ruleOrPattern}
                                        </div>
                                      )}
                                      {sourceInfo.evidenceSnippet && (
                                        <div style={{ marginTop: '2px', color: '#334155' }}>
                                          <strong>Evidence:</strong> {sourceInfo.evidenceSnippet}
                                        </div>
                                      )}
                                    </div>
                                  )}
                                </div>
                              );
                            })}
                          </div>
                        ) : (
                          <div style={{ fontSize: '12px', color: '#64748B', fontStyle: 'italic', marginBottom: '16px', background: '#F8FAFC', padding: '12px', borderRadius: '6px', border: '1px solid #E2E8F0' }}>
                            No structured statutory fields (such as certificate number, issue/expiry dates) matched the extracted text. No placeholder or artificial certificate data was fabricated.
                          </div>
                        )}

                        {/* Extracted Raw Text Preview */}
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '6px' }}>
                          <h5 style={{ fontSize: '12px', fontWeight: '700', color: '#475569', margin: 0, textTransform: 'uppercase' }}>
                            Actual Extracted Text Stream ({ext.extractedTextSnippet ? `${ext.extractedTextSnippet.length.toLocaleString()} characters` : '0 characters'})
                          </h5>
                          {ext.extractedTextSnippet && (
                            <button
                              onClick={() => handleCopyExtractedText(ext.id, ext.extractedTextSnippet)}
                              className="btn btn-outline btn-xs"
                              style={{ gap: '4px', fontSize: '11px', padding: '2px 8px' }}
                            >
                              <Copy size={12} />
                              {copiedExtractionId === ext.id ? 'Copied to Clipboard!' : 'Copy Text'}
                            </button>
                          )}
                        </div>

                        <pre
                          style={{
                            background: '#0F172A',
                            color: '#E2E8F0',
                            padding: '14px',
                            borderRadius: '6px',
                            fontSize: '11px',
                            lineHeight: '1.6',
                            maxHeight: '200px',
                            overflowY: 'auto',
                            whiteSpace: 'pre-wrap',
                            wordBreak: 'break-word',
                            border: '1px solid #1E293B',
                            margin: 0,
                          }}
                        >
                          {ext.extractedTextSnippet || 'No readable text content was detected in this document byte stream.'}
                        </pre>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* --------------------------------------------------------------------- */}
      {/* SUB-TAB 3: DEFICIENCIES & FORMAL NOTICE                              */}
      {/* --------------------------------------------------------------------- */}
      {activeSubTab === 'deficiencies' && (
        <div>
          {/* Deficiency Stages Breakdown Cards */}
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
              gap: '12px',
              marginBottom: '16px',
            }}
          >
            <div style={{ background: '#FFFFFF', border: '1px solid #FCA5A5', borderRadius: '6px', padding: '12px 16px', borderLeft: '4px solid #EF4444' }}>
              <div style={{ fontSize: '11px', color: '#991B1B', fontWeight: '700', textTransform: 'uppercase' }}>Potential Deficiencies</div>
              <div style={{ fontSize: '22px', fontWeight: '800', color: '#DC2626', marginTop: '2px' }}>
                {potentialDeficienciesCount}
              </div>
              <div style={{ fontSize: '11px', color: '#64748B', marginTop: '2px' }}>Automated findings pending review</div>
            </div>

            <div style={{ background: '#FFFFFF', border: '1px solid #FED7AA', borderRadius: '6px', padding: '12px 16px', borderLeft: '4px solid #F97316' }}>
              <div style={{ fontSize: '11px', color: '#9A3412', fontWeight: '700', textTransform: 'uppercase' }}>Confirmed Deficiencies</div>
              <div style={{ fontSize: '22px', fontWeight: '800', color: '#EA580C', marginTop: '2px' }}>
                {confirmedDeficienciesCount}
              </div>
              <div style={{ fontSize: '11px', color: '#64748B', marginTop: '2px' }}>Officer-confirmed statutory defects</div>
            </div>

            <div style={{ background: '#FFFFFF', border: '1px solid #BAE6FD', borderRadius: '6px', padding: '12px 16px', borderLeft: '4px solid #0284C7' }}>
              <div style={{ fontSize: '11px', color: '#075985', fontWeight: '700', textTransform: 'uppercase' }}>Formal Notices Issued</div>
              <div style={{ fontSize: '22px', fontWeight: '800', color: '#0284C7', marginTop: '2px' }}>
                {formalNoticesIssuedCount}
              </div>
              <div style={{ fontSize: '11px', color: '#64748B', marginTop: '2px' }}>Official notices sent to institution</div>
            </div>
          </div>

          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginBottom: '14px',
              flexWrap: 'wrap',
              gap: '10px',
            }}
          >
            <div style={{ fontSize: '13px', color: 'var(--color-text-muted)' }}>
              Officially issued deficiency records. Outstanding deficiencies block application scrutiny completion.
            </div>

            {isOfficer && (
              <button
                onClick={() => {
                  const confirmedIds = confirmedFindingsList.map((f) => f.id);
                  setSelectedFindingIds(confirmedIds);
                  setIsIssueModalOpen(true);
                }}
                className="btn btn-danger btn-sm"
              >
                + Issue New Deficiency Notice
              </button>
            )}
          </div>

          {deficiencies.length === 0 ? (
            <div className="panel" style={{ padding: '30px', textAlign: 'center', color: 'var(--color-text-muted)' }}>
              <CheckCircle2 size={32} style={{ margin: '0 auto 10px', color: '#0284C7' }} />
              <div style={{ fontWeight: '600', marginBottom: '6px' }}>No formal deficiency notices issued yet.</div>
              {findings.some((f) => f.outcome === 'POTENTIAL_DEFICIENCY') && (
                <div style={{ fontSize: '12px', color: '#DC2626' }}>
                  There are {findings.filter((f) => f.outcome === 'POTENTIAL_DEFICIENCY').length} automated potential deficiency finding(s) in the Preliminary Findings tab. The Scrutiny Officer can review them and click <strong>&quot;+ Issue New Deficiency Notice&quot;</strong> to formally transmit the notice.
                </div>
              )}
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
              {deficiencies.map((def) => {
                const isResolved = def.status === 'RECTIFIED_VERIFIED';
                const isResponded = def.status === 'RESPONSE_SUBMITTED';

                return (
                  <div
                    key={def.id}
                    style={{
                      background: '#FFFFFF',
                      border: `1px solid ${isResolved ? '#BBF7D0' : '#FECACA'}`,
                      borderRadius: '8px',
                      padding: '18px',
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '8px' }}>
                      <div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
                          <span style={{ fontSize: '11px', fontWeight: '800', color: '#0284C7' }}>
                            {def.deficiencyReference}
                          </span>
                          <span
                            style={{
                              fontSize: '11px',
                              fontWeight: '700',
                              padding: '2px 8px',
                              borderRadius: '4px',
                              background: isResolved ? '#F0FDF4' : isResponded ? '#EFF6FF' : '#FEF2F2',
                              color: isResolved ? '#16A34A' : isResponded ? '#1D4ED8' : '#DC2626',
                            }}
                          >
                            {def.status.replace('_', ' ')}
                          </span>
                          {def.blocking && (
                            <span style={{ fontSize: '10px', background: '#DC2626', color: '#FFFFFF', padding: '1px 6px', borderRadius: '3px', fontWeight: '700' }}>
                              BLOCKING
                            </span>
                          )}
                        </div>

                        <h4 style={{ fontSize: '15px', fontWeight: '700', color: 'var(--color-navy)', margin: '0 0 4px 0' }}>
                          {def.title}
                        </h4>
                        <div style={{ fontSize: '12px', color: '#64748B', display: 'flex', flexWrap: 'wrap', gap: '12px', marginTop: '4px' }}>
                          <div>Rule: <strong>{def.ruleReference}</strong></div>
                          <div>Affected Document: <strong style={{ color: '#0284C7' }}>{def.associatedDocumentName || def.requirementCode || 'Mandatory Statutory Document'} (v{def.associatedDocumentVersion || 1})</strong></div>
                          <div>Rectification Deadline: <strong style={{ color: '#DC2626' }}>{def.dueDate || 'Standard 15 Days'}</strong></div>
                        </div>
                      </div>

                      {isOfficer && (
                        <button
                          onClick={() => {
                            setReviewingDeficiency(def);
                            setDefReviewDecision(def.reviewerDecision || 'VERIFIED_RESOLVED');
                            setDefReviewRemarks(def.reviewerRemarks || '');
                          }}
                          className="btn btn-outline btn-sm"
                        >
                          Verify Rectification
                        </button>
                      )}
                    </div>

                    <div style={{ fontSize: '12px', color: '#334155', marginTop: '10px' }}>
                      <strong>Deficiency Explanation:</strong> {def.description}
                    </div>

                    {def.officerObservation && (
                      <div style={{ fontSize: '12px', color: '#1E293B', marginTop: '6px', background: '#FEF3C7', padding: '8px 12px', borderRadius: '4px', borderLeft: '3px solid #F59E0B' }}>
                        <strong>Officer Formal Instructions:</strong> {def.officerObservation}
                      </div>
                    )}

                    {/* Original Evidence Text */}
                    <div style={{ background: '#F8FAFC', padding: '8px 12px', borderRadius: '4px', marginTop: '8px', fontSize: '12px' }}>
                      <span style={{ color: '#64748B', fontWeight: '600' }}>Original Extracted Evidence: </span>
                      <span style={{ color: '#0F172A' }}>{def.originalEvidenceText || 'Not available'}</span>
                    </div>

                    {/* Institution Response & Correction Upload Box */}
                    <div
                      style={{
                        marginTop: '12px',
                        padding: '14px',
                        background: '#F1F5F9',
                        borderRadius: '6px',
                        borderLeft: '3px solid #0284C7',
                      }}
                    >
                      <h5 style={{ fontSize: '12px', fontWeight: '700', color: '#1E293B', margin: '0 0 8px 0' }}>
                        Institution Rectification Submission
                      </h5>

                      {def.institutionExplanation ? (
                        <div style={{ fontSize: '12px', color: '#0F172A', marginBottom: '8px' }}>
                          <strong>Explanation:</strong> {def.institutionExplanation}
                          {def.correctedDocumentVersion && (
                            <div style={{ fontSize: '11px', color: '#0284C7', marginTop: '2px' }}>
                              Attached Revised Evidence: Version {def.correctedDocumentVersion}
                            </div>
                          )}
                        </div>
                      ) : (
                        <div style={{ fontSize: '12px', color: '#64748B', marginBottom: '8px' }}>
                          No explanation submitted yet. Institution must upload corrected evidence and submit explanation below.
                        </div>
                      )}

                      {/* Institution Controls */}
                      {isInstitution && def.status !== 'RECTIFIED_VERIFIED' && (
                        <div style={{ marginTop: '10px', display: 'flex', flexDirection: 'column', gap: '8px' }}>
                          <div style={{ display: 'flex', gap: '8px', alignItems: 'center', flexWrap: 'wrap' }}>
                            <input
                              type="file"
                              accept=".pdf,.png,.jpg,.jpeg"
                              onChange={(e) => {
                                const file = e.target.files[0];
                                setSelectedFiles((prev) => ({ ...prev, [def.id]: file }));
                              }}
                              style={{ fontSize: '12px' }}
                            />
                            <button
                              onClick={() => handleUploadCorrectedFile(def.id)}
                              disabled={uploadingDefId === def.id || !selectedFiles[def.id]}
                              className="btn btn-outline btn-sm"
                            >
                              <Upload size={13} /> {uploadingDefId === def.id ? 'Uploading...' : 'Upload Corrected Document'}
                            </button>
                          </div>

                          <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                            <input
                              type="text"
                              className="form-input"
                              placeholder="Explain rectifications made (e.g. Renewed Fire NOC attached, address updated in Affidavit 2)..."
                              value={responseExplanations[def.id] || ''}
                              onChange={(e) => {
                                const val = e.target.value;
                                setResponseExplanations((prev) => ({ ...prev, [def.id]: val }));
                              }}
                              style={{ fontSize: '12px', flex: 1 }}
                            />
                            <button
                              onClick={() => handleSubmitResponses(def.id)}
                              className="btn btn-primary btn-sm"
                              style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
                            >
                              <Send size={13} /> Submit for Re-Scrutiny
                            </button>
                          </div>
                        </div>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* --------------------------------------------------------------------- */}
      {/* SUB-TAB 4: RE-SCRUTINY COMPARISON DIFFS                               */}
      {/* --------------------------------------------------------------------- */}
      {activeSubTab === 'rescrutiny' && (
        <div>
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginBottom: '16px',
              flexWrap: 'wrap',
              gap: '12px',
            }}
          >
            <div>
              <div style={{ fontSize: '14px', fontWeight: '700', color: 'var(--color-navy)' }}>
                Automated Re-Scrutiny Evidence Comparisons
              </div>
              <div style={{ fontSize: '12px', color: 'var(--color-text-muted)' }}>
                Side-by-side automated comparative evaluation between original and corrected evidence revisions.
              </div>
            </div>

            {isOfficer && (
              <button
                type="button"
                onClick={handleRunRescrutiny}
                className="btn btn-primary"
                disabled={runningScrutiny}
                style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', fontSize: '12px' }}
              >
                <RefreshCw size={14} className={runningScrutiny ? 'animate-spin' : ''} />
                {runningScrutiny ? 'Evaluating Re-Scrutiny...' : 'Run Automated Re-Scrutiny'}
              </button>
            )}
          </div>

          {/* Quick Metrics Bar */}
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))',
              gap: '12px',
              marginBottom: '18px',
            }}
          >
            <div style={{ background: '#F8FAFC', border: '1px solid var(--color-border)', borderRadius: '6px', padding: '12px 14px' }}>
              <div style={{ fontSize: '11px', color: '#64748B', fontWeight: '700', textTransform: 'uppercase' }}>Comparisons Evaluated</div>
              <div style={{ fontSize: '20px', fontWeight: '800', color: '#1E293B', marginTop: '4px' }}>{comparisons.length}</div>
            </div>

            <div style={{ background: '#F0FDF4', border: '1px solid #BBF7D0', borderRadius: '6px', padding: '12px 14px' }}>
              <div style={{ fontSize: '11px', color: '#166534', fontWeight: '700', textTransform: 'uppercase' }}>Rectified (Pending Confirmation)</div>
              <div style={{ fontSize: '20px', fontWeight: '800', color: '#16A34A', marginTop: '4px' }}>
                {comparisons.filter((c) => c.comparisonStatus === 'RECTIFIED_PENDING_CONFIRMATION' || c.comparisonStatus === 'RESOLVED').length}
              </div>
            </div>

            <div style={{ background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: '6px', padding: '12px 14px' }}>
              <div style={{ fontSize: '11px', color: '#991B1B', fontWeight: '700', textTransform: 'uppercase' }}>Persisting Deficiencies</div>
              <div style={{ fontSize: '20px', fontWeight: '800', color: '#DC2626', marginTop: '4px' }}>
                {comparisons.filter((c) => c.comparisonStatus === 'DEFICIENCY_PERSISTS').length}
              </div>
            </div>

            <div style={{ background: '#FFFBEB', border: '1px solid #FDE68A', borderRadius: '6px', padding: '12px 14px' }}>
              <div style={{ fontSize: '11px', color: '#B45309', fontWeight: '700', textTransform: 'uppercase' }}>Manual Review / New Issues</div>
              <div style={{ fontSize: '20px', fontWeight: '800', color: '#D97706', marginTop: '4px' }}>
                {comparisons.filter((c) => c.comparisonStatus === 'NEEDS_MANUAL_REVIEW' || c.comparisonStatus === 'NEW_DEFICIENCY_DETECTED').length}
              </div>
            </div>
          </div>

          {comparisons.length === 0 ? (
            <div className="panel" style={{ padding: '36px', textAlign: 'center', color: 'var(--color-text-muted)' }}>
              <FileDiff size={36} style={{ margin: '0 auto 12px', color: '#0284C7' }} />
              <div style={{ fontSize: '14px', fontWeight: '600', color: '#1E293B', marginBottom: '6px' }}>
                No Re-Scrutiny Comparisons Generated Yet
              </div>
              <div style={{ fontSize: '12px', maxWidth: '480px', margin: '0 auto' }}>
                When an institution uploads corrected documents in response to an official deficiency notice, automated re-scrutiny extracts the new evidence, re-runs rule validation, and presents side-by-side diffs here.
              </div>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              {comparisons.map((comp) => {
                const status = comp.comparisonStatus || '';
                let statusBg = '#FEF2F2';
                let statusColor = '#DC2626';
                let statusBorder = '#FECACA';

                if (status === 'RECTIFIED_PENDING_CONFIRMATION' || status === 'RESOLVED') {
                  statusBg = '#F0FDF4';
                  statusColor = '#16A34A';
                  statusBorder = '#BBF7D0';
                } else if (status === 'NEEDS_MANUAL_REVIEW') {
                  statusBg = '#FFFBEB';
                  statusColor = '#D97706';
                  statusBorder = '#FDE68A';
                } else if (status === 'NEW_DEFICIENCY_DETECTED') {
                  statusBg = '#F5F3FF';
                  statusColor = '#7C3AED';
                  statusBorder = '#DDD6FE';
                }

                return (
                  <div
                    key={comp.id}
                    style={{
                      background: '#FFFFFF',
                      border: '1px solid var(--color-border)',
                      borderRadius: '8px',
                      overflow: 'hidden',
                      boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
                    }}
                  >
                    {/* Header Bar */}
                    <div
                      style={{
                        padding: '12px 18px',
                        background: '#F8FAFC',
                        borderBottom: '1px solid var(--color-border)',
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        flexWrap: 'wrap',
                        gap: '8px',
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
                        <span style={{ fontSize: '12px', fontWeight: '800', color: '#0284C7' }}>
                          {comp.deficiencyReference || `Deficiency #${comp.deficiencyId}`}
                        </span>
                        {comp.requirementCode && (
                          <span
                            style={{
                              fontSize: '10px',
                              fontWeight: '700',
                              padding: '2px 6px',
                              borderRadius: '4px',
                              background: '#E0F2FE',
                              color: '#0369A1',
                            }}
                          >
                            {comp.requirementCode}
                          </span>
                        )}
                        <span style={{ fontSize: '13px', fontWeight: '700', color: '#1E293B' }}>
                          {comp.deficiencyTitle}
                        </span>
                      </div>

                      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                        <span
                          style={{
                            fontSize: '11px',
                            fontWeight: '700',
                            padding: '3px 10px',
                            borderRadius: '4px',
                            background: statusBg,
                            color: statusColor,
                            border: `1px solid ${statusBorder}`,
                          }}
                        >
                          {status.replace(/_/g, ' ')}
                        </span>
                        {comp.assessmentReference && (
                          <span style={{ fontSize: '11px', color: '#64748B' }}>
                            Cycle: <strong>{comp.assessmentReference}</strong>
                          </span>
                        )}
                      </div>
                    </div>

                    {/* Side-by-side Diff Split */}
                    <div
                      style={{
                        display: 'grid',
                        gridTemplateColumns: '1fr 1fr',
                        gap: '1px',
                        background: 'var(--color-border)',
                      }}
                    >
                      {/* Left: Original Evidence */}
                      <div style={{ background: '#FFFDFD', padding: '16px' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                          <span style={{ fontSize: '11px', fontWeight: '800', color: '#DC2626' }}>
                            ORIGINAL SUBMISSION (v{comp.originalDocumentVersion})
                          </span>
                          {comp.originalDocumentId && (
                            <button
                              type="button"
                              onClick={() => handleOpenDocumentById(comp.originalDocumentId)}
                              className="btn btn-outline"
                              style={{ padding: '2px 8px', fontSize: '10px', display: 'inline-flex', alignItems: 'center', gap: '4px' }}
                            >
                              <ExternalLink size={11} /> View v{comp.originalDocumentVersion}
                            </button>
                          )}
                        </div>
                        {comp.originalDocumentName && (
                          <div style={{ fontSize: '11px', color: '#64748B', marginBottom: '6px' }}>
                            File: <strong>{comp.originalDocumentName}</strong>
                          </div>
                        )}
                        <div
                          style={{
                            fontSize: '12px',
                            color: '#1E293B',
                            background: '#FFFFFF',
                            border: '1px solid #FECACA',
                            borderRadius: '4px',
                            padding: '10px',
                            fontFamily: 'monospace',
                            whiteSpace: 'pre-wrap',
                            wordBreak: 'break-word',
                          }}
                        >
                          {comp.originalExtractedValue || 'Non-compliant or missing original document evidence'}
                        </div>
                      </div>

                      {/* Right: Corrected Evidence */}
                      <div style={{ background: '#F8FFF9', padding: '16px' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                          <span style={{ fontSize: '11px', fontWeight: '800', color: '#16A34A' }}>
                            CORRECTED REVISION (v{comp.correctedDocumentVersion})
                          </span>
                          {comp.correctedDocumentId && (
                            <button
                              type="button"
                              onClick={() => handleOpenDocumentById(comp.correctedDocumentId)}
                              className="btn btn-outline"
                              style={{ padding: '2px 8px', fontSize: '10px', display: 'inline-flex', alignItems: 'center', gap: '4px' }}
                            >
                              <ExternalLink size={11} /> View v{comp.correctedDocumentVersion}
                            </button>
                          )}
                        </div>
                        {comp.correctedDocumentName && (
                          <div style={{ fontSize: '11px', color: '#64748B', marginBottom: '6px' }}>
                            File: <strong>{comp.correctedDocumentName}</strong>
                          </div>
                        )}
                        <div
                          style={{
                            fontSize: '12px',
                            color: '#1E293B',
                            background: '#FFFFFF',
                            border: '1px solid #BBF7D0',
                            borderRadius: '4px',
                            padding: '10px',
                            fontFamily: 'monospace',
                            whiteSpace: 'pre-wrap',
                            wordBreak: 'break-word',
                          }}
                        >
                          {comp.correctedExtractedValue || 'Corrected document attached'}
                        </div>
                      </div>
                    </div>

                    {/* Comparison Finding Summary */}
                    <div style={{ padding: '14px 18px', background: '#FFFFFF' }}>
                      <div style={{ fontSize: '12px', color: '#334155', lineHeight: '1.5' }}>
                        <strong>Automated Comparison Finding:</strong> {comp.comparisonSummary}
                      </div>

                      {comp.remainingIssues && (
                        <div
                          style={{
                            background: '#FEF2F2',
                            border: '1px solid #FECACA',
                            borderRadius: '4px',
                            padding: '8px 12px',
                            marginTop: '10px',
                            fontSize: '12px',
                            color: '#991B1B',
                          }}
                        >
                          <AlertTriangle size={14} style={{ display: 'inline', marginRight: '6px', verticalAlign: '-2px' }} />
                          <strong>Remaining Issues:</strong> {comp.remainingIssues}
                        </div>
                      )}

                      {comp.officerActionRecommendation && (
                        <div style={{ fontSize: '11px', color: '#0284C7', marginTop: '8px' }}>
                          <strong>Action Recommendation:</strong> {comp.officerActionRecommendation}
                        </div>
                      )}

                      {/* Officer Review Action Bar */}
                      {isOfficer && (
                        <div
                          style={{
                            marginTop: '12px',
                            paddingTop: '12px',
                            borderTop: '1px solid #E2E8F0',
                            display: 'flex',
                            justifyContent: 'flex-end',
                            gap: '10px',
                            alignItems: 'center',
                          }}
                        >
                          <span style={{ fontSize: '11px', color: '#64748B' }}>Scrutiny Officer Review:</span>
                          <button
                            type="button"
                            className="btn btn-outline"
                            style={{ color: '#DC2626', borderColor: '#FECACA', fontSize: '11px', padding: '4px 10px' }}
                            onClick={() => {
                              setReviewingDeficiency({
                                id: comp.deficiencyId,
                                deficiencyReference: comp.deficiencyReference,
                                title: comp.deficiencyTitle,
                              });
                              setDefReviewDecision('REJECTED_FURTHER_CORRECTION');
                              setDefReviewRemarks('Deficiency remains unresolved or corrected evidence is non-compliant.');
                            }}
                          >
                            Reject Correction
                          </button>
                          <button
                            type="button"
                            className="btn btn-primary"
                            style={{ background: '#16A34A', borderColor: '#16A34A', fontSize: '11px', padding: '4px 12px' }}
                            onClick={() => {
                              setReviewingDeficiency({
                                id: comp.deficiencyId,
                                deficiencyReference: comp.deficiencyReference,
                                title: comp.deficiencyTitle,
                              });
                              setDefReviewDecision('VERIFIED_RESOLVED');
                              setDefReviewRemarks('Substantiated correction verified via automated re-scrutiny comparative evidence. Formal deficiency resolved.');
                            }}
                          >
                            Verify &amp; Resolve Deficiency
                          </button>
                        </div>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* --------------------------------------------------------------------- */}
      {/* MODAL: ISSUE FORMAL DEFICIENCY NOTICE                                 */}
      {/* --------------------------------------------------------------------- */}
      {isIssueModalOpen && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0,0,0,0.5)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1000,
            padding: '20px',
          }}
        >
          <div
            style={{
              background: '#FFFFFF',
              borderRadius: '8px',
              maxWidth: '600px',
              width: '100%',
              padding: '24px',
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1)',
            }}
          >
            <h3 style={{ fontSize: '18px', fontWeight: '800', color: 'var(--color-navy)', margin: '0 0 12px 0' }}>
              Issue Official Deficiency Notice
            </h3>
            <p style={{ fontSize: '12px', color: 'var(--color-text-muted)', marginBottom: '16px' }}>
              Select preliminary findings to formally issue to the institution. The institution will be required to upload corrected evidence by the statutory remediation deadline.
            </p>

            <form onSubmit={handleIssueNotice}>
              <div className="form-group" style={{ marginBottom: '14px' }}>
                <label className="form-label">Confirmed Findings Included ({selectedFindingIds.length} Selected)</label>
                {confirmedFindingsList.length === 0 ? (
                  <div style={{ background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: '4px', padding: '10px 12px', fontSize: '12px', color: '#991B1B' }}>
                    <AlertTriangle size={14} style={{ display: 'inline', marginRight: '6px', verticalAlign: '-2px' }} />
                    No confirmed deficiencies found. An authorized Scrutiny Officer must review preliminary findings and mark them as <strong>CONFIRMED</strong> before an official notice can be issued.
                  </div>
                ) : (
                  <div style={{ maxHeight: '180px', overflowY: 'auto', border: '1px solid var(--color-border)', borderRadius: '4px', padding: '8px' }}>
                    {confirmedFindingsList.map((f) => (
                      <label key={f.id} style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '4px 0', fontSize: '12px' }}>
                        <input
                          type="checkbox"
                          checked={selectedFindingIds.includes(f.id)}
                          onChange={(e) => {
                            if (e.target.checked) {
                              setSelectedFindingIds((prev) => [...prev, f.id]);
                            } else {
                              setSelectedFindingIds((prev) => prev.filter((id) => id !== f.id));
                            }
                          }}
                        />
                        <span>
                          <strong>{f.documentRequirementCode || f.requirementCode}:</strong> {f.findingName || f.ruleName}
                        </span>
                      </label>
                    ))}
                  </div>
                )}
              </div>

              <div className="form-group" style={{ marginBottom: '14px' }}>
                <label className="form-label">Statutory Rectification Due Date</label>
                <input
                  type="date"
                  className="form-input"
                  value={issueDueDate}
                  onChange={(e) => setIssueDueDate(e.target.value)}
                  required
                />
              </div>

              <div className="form-group" style={{ marginBottom: '16px' }}>
                <label className="form-label">Officer Formal Instructions &amp; Observations</label>
                <textarea
                  className="form-textarea"
                  rows={3}
                  placeholder="Provide explicit instructions for document re-upload and statutory rectification..."
                  value={issueObservation}
                  onChange={(e) => setIssueObservation(e.target.value)}
                  required
                />
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
                <button type="button" onClick={() => setIsIssueModalOpen(false)} className="btn btn-outline" disabled={isIssuingNotice}>
                  Cancel
                </button>
                <button
                  type="submit"
                  className="btn btn-danger"
                  disabled={selectedFindingIds.length === 0 || isIssuingNotice}
                >
                  {isIssuingNotice ? 'Issuing Notice...' : 'Issue Deficiency Notice'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* --------------------------------------------------------------------- */}
      {/* MODAL: OFFICER REVIEW PRELIMINARY FINDING                             */}
      {/* --------------------------------------------------------------------- */}
      {reviewingFinding && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0,0,0,0.5)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1000,
            padding: '20px',
          }}
        >
          <div
            style={{
              background: '#FFFFFF',
              borderRadius: '8px',
              maxWidth: '520px',
              width: '100%',
              padding: '22px',
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1)',
            }}
          >
            <h3 style={{ fontSize: '16px', fontWeight: '800', color: 'var(--color-navy)', margin: '0 0 10px 0' }}>
              {reviewingFinding.isGrouped ? 'Review Consolidated Duplicate Finding' : `Review Automated Finding #${reviewingFinding.id}`}
            </h3>
            <div style={{ fontSize: '12px', color: '#64748B', marginBottom: '14px' }}>
              <strong>{reviewingFinding.ruleName || reviewingFinding.findingName}</strong> ({reviewingFinding.requirementCode || reviewingFinding.documentRequirementCode})
            </div>

            {reviewingFinding.isGrouped && (
              <div style={{ background: '#EFF6FF', border: '1px solid #BFDBFE', padding: '10px 12px', borderRadius: '6px', marginBottom: '14px', fontSize: '12px' }}>
                <div style={{ fontWeight: '700', color: '#1E40AF', marginBottom: '4px' }}>Affected Document Categories:</div>
                <div style={{ color: '#1E3A8A', marginBottom: '8px' }}>
                  {(reviewingFinding.affectedCategoryNames || reviewingFinding.affectedCategories || []).join(', ')}
                </div>
                <div>
                  <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer', fontWeight: '600', color: '#1E40AF' }}>
                    <input
                      type="checkbox"
                      checked={applyToAllDuplicates}
                      onChange={(e) => setApplyToAllDuplicates(e.target.checked)}
                    />
                    Synchronize decision across all {reviewingFinding.affectedCategories?.length || 'underlying'} duplicate category evaluations
                  </label>
                </div>
              </div>
            )}

            <form onSubmit={handleConfirmFindingReview}>
              <div className="form-group" style={{ marginBottom: '14px' }}>
                <label className="form-label">Officer Decision</label>
                <select
                  className="form-select"
                  value={findingReviewDecision}
                  onChange={(e) => setFindingReviewDecision(e.target.value)}
                >
                  <option value="CONFIRMED_DEFICIENCY">CONFIRMED (Valid Statutory Deficiency)</option>
                  <option value="DISMISSED">DISMISSED (False Alarm / Acceptable Variation)</option>
                  <option value="REQUEST_MANUAL_VERIFICATION">REQUEST MANUAL VERIFICATION (Requires Desk / Physical Check)</option>
                  <option value="MANUAL_VERIFIED">MANUAL VERIFIED (Verified Compliant upon Inspection)</option>
                </select>
              </div>

              <div className="form-group" style={{ marginBottom: '16px' }}>
                <label className="form-label">Reviewer Justification / Remarks</label>
                <textarea
                  className="form-textarea"
                  rows={3}
                  placeholder="Record justification for confirming or dismissing automated finding..."
                  value={findingReviewRemarks}
                  onChange={(e) => setFindingReviewRemarks(e.target.value)}
                  required
                />
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
                <button type="button" onClick={() => setReviewingFinding(null)} className="btn btn-outline" disabled={isSavingReview}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary" disabled={isSavingReview}>
                  {isSavingReview ? 'Saving...' : 'Save Decision'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* --------------------------------------------------------------------- */}
      {/* MODAL: OFFICER REVIEW DEFICIENCY RECTIFICATION                         */}
      {/* --------------------------------------------------------------------- */}
      {reviewingDeficiency && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0,0,0,0.5)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1000,
            padding: '20px',
          }}
        >
          <div
            style={{
              background: '#FFFFFF',
              borderRadius: '8px',
              maxWidth: '520px',
              width: '100%',
              padding: '22px',
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1)',
            }}
          >
            <h3 style={{ fontSize: '16px', fontWeight: '800', color: 'var(--color-navy)', margin: '0 0 10px 0' }}>
              Verify Rectification: {reviewingDeficiency.deficiencyReference}
            </h3>
            <div style={{ fontSize: '12px', color: '#64748B', marginBottom: '14px' }}>
              <strong>{reviewingDeficiency.title}</strong>
            </div>

            <form onSubmit={handleReviewDeficiency}>
              <div className="form-group" style={{ marginBottom: '14px' }}>
                <label className="form-label">Verification Outcome</label>
                <select
                  className="form-select"
                  value={defReviewDecision}
                  onChange={(e) => setDefReviewDecision(e.target.value)}
                >
                  <option value="VERIFIED_RESOLVED">VERIFIED &amp; RESOLVED (Rectification Accepted)</option>
                  <option value="REJECTED_FURTHER_CORRECTION">REJECTED (Requires Further Correction)</option>
                </select>
              </div>

              <div className="form-group" style={{ marginBottom: '16px' }}>
                <label className="form-label">Reviewer Official Remarks</label>
                <textarea
                  className="form-textarea"
                  rows={3}
                  placeholder="Record official verification remarks before clearing deficiency..."
                  value={defReviewRemarks}
                  onChange={(e) => setDefReviewRemarks(e.target.value)}
                  required
                />
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
                <button type="button" onClick={() => setReviewingDeficiency(null)} className="btn btn-outline">
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary">
                  Save Verification
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

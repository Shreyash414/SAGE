// SAGE - Complete AICTE Approval Application Submission Workflow
// Platform: SAGE — Institutional Approval & Compliance Platform

import React, { useState, useEffect, useMemo } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useData } from '../context/DataContext';
import { apiClient } from '../services/apiClient';
import {
  FileText,
  Upload,
  CheckCircle2,
  AlertTriangle,
  ArrowRight,
  ArrowLeft,
  Save,
  Send,
  Building2,
  ShieldCheck,
  FileCheck,
  Search,
  Download,
  Trash2,
  Loader2,
  RotateCcw,
  Check,
  Layers
} from 'lucide-react';

export const NewApplicationPage = () => {
  const { user } = useAuth();
  const { fetchApplicationsFromBackend, registerPromotedApplication } = useData();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const existingAppId = searchParams.get('id');
  const existingDraftId = searchParams.get('draftId') || searchParams.get('draftRef');

  // Multi-step workflow state (1 to 5)
  const [currentStep, setCurrentStep] = useState(1);
  const [loading, setLoading] = useState(true);
  const [savingDraft, setSavingDraft] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [uploadingDocCode, setUploadingDocCode] = useState(null);
  const [saveMessage, setSaveMessage] = useState(null);
  const [errorMessage, setErrorMessage] = useState(null);

  // Application / Draft Data State
  const [applicationTypes, setApplicationTypes] = useState([]);
  const [selectedTypeCode, setSelectedTypeCode] = useState('');
  const [typeDetail, setTypeDetail] = useState(null);
  const [draftId, setDraftId] = useState(existingDraftId ? Number(existingDraftId) : null);
  const [draftReference, setDraftReference] = useState(null);
  const [applicationId, setApplicationId] = useState(existingAppId ? Number(existingAppId) : null);
  const [application, setApplication] = useState(null);
  const [checklist, setChecklist] = useState(null);
  const [submissionResult, setSubmissionResult] = useState(null);

  // Form inputs state
  const [formData, setFormData] = useState({});
  const [academicYear, setAcademicYear] = useState('2026-2027');
  const [typeSearchTerm, setTypeSearchTerm] = useState('');

  // 1. Initialize catalogue and restore saved draft from backend or load official application
  useEffect(() => {
    const init = async () => {
      setLoading(true);
      setErrorMessage(null);
      try {
        const types = await apiClient('/application-types');
        setApplicationTypes(types || []);

        const targetDraftParam = existingDraftId || existingAppId;
        let draftLoaded = false;

        // Try restoring draft from backend /api/v1/application-drafts
        if (targetDraftParam) {
          try {
            const draft = await apiClient(`/application-drafts/${targetDraftParam}`);
            if (draft && draft.id) {
              setDraftId(draft.id);
              setDraftReference(draft.draftReference);
              setSelectedTypeCode(draft.applicationType);
              setAcademicYear(draft.academicYear || '2026-2027');
              if (draft.formData) {
                setFormData(draft.formData);
              }

              const detail = await apiClient(`/application-types/${draft.applicationType}`);
              setTypeDetail(detail);

              const chk = await apiClient(`/application-drafts/${draft.id}/checklist`);
              setChecklist(chk);

              if (draft.status === 'PROMOTED') {
                setCurrentStep(5);
                setSubmissionResult({
                  success: true,
                  applicationId: draft.promotedApplicationId,
                  officialApplicationId: draft.promotedApplicationId,
                  applicationReference: draft.promotedApplicationReference,
                  officialApplicationReference: draft.promotedApplicationReference,
                  status: 'In progress',
                  currentStage: 'Scrutiny',
                  ownerName: 'B. Singh (Scrutiny Officer)',
                  submittedAt: draft.updatedAt,
                  submittedBy: draft.applicantId,
                  validationErrors: [],
                });
              } else {
                setCurrentStep(2);
                setSaveMessage(`Restored saved draft ${draft.draftReference} from server.`);
                setTimeout(() => setSaveMessage(null), 3500);
              }
              draftLoaded = true;
            }
          } catch (draftErr) {
            console.log('Not a draft identifier, checking official applications table...', draftErr.message);
          }
        }

        // If not a draft, try loading as an official submitted application
        if (!draftLoaded && existingAppId) {
          try {
            const app = await apiClient(`/applications/${existingAppId}`);
            setApplication(app);
            setApplicationId(app.id);
            setSelectedTypeCode(app.applicationType);
            setAcademicYear(app.academicYear || '2026-2027');
            if (app.formData) {
              setFormData(app.formData);
            }

            const detail = await apiClient(`/application-types/${app.applicationType}`);
            setTypeDetail(detail);

            const chk = await apiClient(`/applications/${app.id}/checklist`);
            setChecklist(chk);

            if (app.submittedAt || app.status !== 'DRAFT') {
              setCurrentStep(5);
              setSubmissionResult({
                success: true,
                applicationId: app.id,
                officialApplicationId: app.id,
                applicationReference: app.applicationReference,
                officialApplicationReference: app.applicationReference,
                status: app.status,
                currentStage: app.currentStage,
                ownerName: app.ownerName,
                submittedAt: app.submittedAt,
                submittedBy: app.submittedBy,
                validationErrors: [],
              });
            } else {
              setCurrentStep(2);
            }
          } catch (appErr) {
            console.error('Could not load application dossier:', appErr);
            setErrorMessage(appErr.message || `Application not found with reference: ${existingAppId}`);
          }
        }
      } catch (err) {
        console.error('Failed to initialize application wizard:', err);
        setErrorMessage(err.message || 'Unable to load application information');
      } finally {
        setLoading(false);
      }
    };

    init();
  }, [existingAppId, existingDraftId, user]);

  // Handle selecting an application category in Step 1 (Persists draft row on backend immediately)
  const handleSelectType = async (code) => {
    setSelectedTypeCode(code);
    setLoading(true);
    setErrorMessage(null);
    try {
      const detail = await apiClient(`/application-types/${code}`);
      setTypeDetail(detail);

      const initialFields = {
        institutionName: user?.institutionName || '',
        contactEmail: user?.email || '',
        contactPerson: user?.name || '',
      };

      // Create persistent draft on backend
      const newDraft = await apiClient('/application-drafts', {
        method: 'POST',
        body: JSON.stringify({
          applicationType: code,
          academicYear,
          initialFormData: initialFields,
        }),
      });

      setDraftId(newDraft.id);
      setDraftReference(newDraft.draftReference);
      setFormData(newDraft.formData || initialFields);
      setSearchParams({ draftId: String(newDraft.id) });

      const chk = await apiClient(`/application-drafts/${newDraft.id}/checklist`);
      setChecklist(chk);

      setCurrentStep(2);
    } catch (err) {
      console.error('Failed to initialize application draft:', err);
      setErrorMessage(err.message || 'Failed to initialize draft on server.');
    } finally {
      setLoading(false);
    }
  };

  // Handle Form Field Changes
  const handleFieldChange = (fieldName, value) => {
    setFormData((prev) => ({
      ...prev,
      [fieldName]: value,
    }));
  };

  // Explicit Save Draft Button (Saves form data to PostgreSQL draft)
  const handleSaveDraft = async () => {
    if (!draftId) return;
    setSavingDraft(true);
    setSaveMessage(null);
    setErrorMessage(null);

    try {
      const updated = await apiClient(`/application-drafts/${draftId}`, {
        method: 'PUT',
        body: JSON.stringify({
          formData,
          academicYear,
        }),
      });

      setFormData(updated.formData || formData);
      const chk = await apiClient(`/application-drafts/${draftId}/checklist`);
      setChecklist(chk);

      const timeStr = new Date().toLocaleTimeString();
      setSaveMessage(`Draft successfully saved on server (${draftReference}) at ${timeStr}`);
      setTimeout(() => setSaveMessage(null), 3500);
    } catch (err) {
      console.error('Save draft failed:', err);
      setErrorMessage(err.message || 'Failed to save draft to server.');
    } finally {
      setSavingDraft(false);
    }
  };

  // Discard draft on server and start fresh
  const handleDiscardDraft = async () => {
    if (window.confirm('Are you sure you want to discard this draft? All uploaded statutory documents will be permanently removed.')) {
      if (draftId) {
        try {
          await apiClient(`/application-drafts/${draftId}`, { method: 'DELETE' });
        } catch (err) {
          console.warn('Failed to delete draft on server:', err);
        }
      }
      setFormData({});
      setSelectedTypeCode('');
      setTypeDetail(null);
      setDraftId(null);
      setDraftReference(null);
      setApplicationId(null);
      setApplication(null);
      setChecklist(null);
      setSearchParams({});
      setCurrentStep(1);
    }
  };

  // Upload file immediately to backend MinIO & PostgreSQL linked to this draft
  const handleFileSelect = async (requirementCode, file) => {
    if (!file || !draftId) return;
    if (file.size > 25 * 1024 * 1024) {
      setErrorMessage('File size exceeds maximum permitted limit of 25MB.');
      return;
    }

    setUploadingDocCode(requirementCode);
    setErrorMessage(null);

    try {
      const uploadData = new FormData();
      uploadData.append('file', file);

      await apiClient(`/application-drafts/${draftId}/documents/${requirementCode}`, {
        method: 'POST',
        body: uploadData,
      });

      const chk = await apiClient(`/application-drafts/${draftId}/checklist`);
      setChecklist(chk);
      setSaveMessage(`Document '${file.name}' securely uploaded to storage.`);
      setTimeout(() => setSaveMessage(null), 3000);
    } catch (err) {
      console.error('Document upload failed:', err);
      setErrorMessage(err.message || 'Upload failed. Please check file format.');
    } finally {
      setUploadingDocCode(null);
    }
  };

  // Delete previously uploaded document from draft
  const handleDeleteBackendDocument = async (documentId) => {
    if (!documentId || !draftId) return;
    if (!window.confirm('Are you sure you want to remove this document from the server?')) return;

    try {
      await apiClient(`/application-drafts/${draftId}/documents/${documentId}`, {
        method: 'DELETE',
      });
      const chk = await apiClient(`/application-drafts/${draftId}/checklist`);
      setChecklist(chk);
      setSaveMessage('Document removed from storage.');
      setTimeout(() => setSaveMessage(null), 3000);
    } catch (err) {
      console.error('Delete failed:', err);
      setErrorMessage(err.message || 'Failed to delete document.');
    }
  };

  // Real-time completeness evaluation (authoritative check derived from requirements & checklist)
  const evaluation = useMemo(() => {
    if (!typeDetail) {
      return {
        submittable: false,
        completionPercentage: 0,
        formCompletionPercentage: 0,
        documentCompletionPercentage: 0,
        blockingDeficiencies: ['No application type selected'],
        sections: [],
      };
    }

    const missingFields = [];
    const missingDocs = [];
    let totalRequiredFields = 0;
    let completedFields = 0;

    const sectionsStatus = typeDetail.sections.map((sec) => {
      let secReq = 0;
      let secDone = 0;
      const secMissing = [];

      sec.fields.forEach((f) => {
        if (f.required) {
          totalRequiredFields++;
          secReq++;
          const val = formData[f.name];
          let ok = false;
          if (val !== undefined && val !== null && String(val).trim() !== '') {
            if (f.type === 'checkbox') {
              ok = Boolean(val);
            } else if (f.type === 'number') {
              ok = Number(val) > 0;
            } else {
              ok = true;
            }
          }
          if (ok) {
            completedFields++;
            secDone++;
          } else {
            secMissing.push(f.label);
            missingFields.push(`[${sec.title}] ${f.label}`);
          }
        }
      });

      return {
        id: sec.id,
        title: sec.title,
        complete: secMissing.length === 0,
        totalFields: secReq,
        completedFields: secDone,
        missingFields: secMissing,
      };
    });

    let totalMandatoryDocs = 0;
    let satisfiedMandatoryDocs = 0;

    typeDetail.documentRequirements.forEach((req) => {
      if (req.mandatory) {
        totalMandatoryDocs++;
        const isUploadedOnBackend = checklist?.documents?.some(
          (d) => d.requirementCode === req.code && d.uploaded
        );

        if (isUploadedOnBackend) {
          satisfiedMandatoryDocs++;
        } else {
          missingDocs.push(`Missing document: ${req.name} (${req.aphClause})`);
        }
      }
    });

    const formPct = totalRequiredFields > 0 ? Math.round((completedFields * 100) / totalRequiredFields) : 100;
    const docPct = totalMandatoryDocs > 0 ? Math.round((satisfiedMandatoryDocs * 100) / totalMandatoryDocs) : 100;
    const overallPct = Math.round((formPct + docPct) / 2);
    const blocking = [...missingFields.map((f) => `Incomplete field: ${f}`), ...missingDocs];

    return {
      submittable: blocking.length === 0,
      completionPercentage: overallPct,
      formCompletionPercentage: formPct,
      documentCompletionPercentage: docPct,
      blockingDeficiencies: blocking,
      sections: sectionsStatus,
    };
  }, [typeDetail, formData, checklist]);

  // Final Application Submission Handler (Promotes complete draft into official applications)
  const handleSubmitApplication = async () => {
    if (!evaluation.submittable) {
      setErrorMessage('Application cannot be submitted: please satisfy all mandatory requirements.');
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);

    try {
      // 1. Sync latest form inputs to draft
      await apiClient(`/application-drafts/${draftId}`, {
        method: 'PUT',
        body: JSON.stringify({
          formData,
          academicYear,
        }),
      });

      // 2. Authoritative Promotion Gate
      const res = await apiClient(`/application-drafts/${draftId}/promote`, {
        method: 'POST',
      });

      setSubmissionResult(res);
      const officialId = res.officialApplicationId || res.applicationId;
      setApplicationId(officialId);
      setCurrentStep(5);

      // Immediately synchronize newly promoted application into frontend state and backend cache
      if (registerPromotedApplication) {
        registerPromotedApplication(res);
      }
      if (fetchApplicationsFromBackend) {
        await fetchApplicationsFromBackend({ silent: true });
      }
    } catch (err) {
      console.error('Submission failed:', err);
      const errors = err.data?.validationErrors || [];
      if (errors.length > 0) {
        setErrorMessage(`Submission blocked: ${errors.join('; ')}`);
      } else {
        setErrorMessage(err.message || 'Submission could not be completed.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  // Filtered types for Step 1
  const filteredTypes = useMemo(() => {
    if (!typeSearchTerm.trim()) return applicationTypes;
    const term = typeSearchTerm.toLowerCase();
    return applicationTypes.filter(
      (t) =>
        t.name.toLowerCase().includes(term) ||
        t.code.toLowerCase().includes(term) ||
        t.category.toLowerCase().includes(term) ||
        t.aphClause.toLowerCase().includes(term)
    );
  }, [applicationTypes, typeSearchTerm]);

  if (loading && !applicationTypes.length) {
    return (
      <div style={{ padding: '60px', textAlign: 'center' }}>
        <Loader2 className="animate-spin" size={32} style={{ color: 'var(--color-blue)', margin: '0 auto 12px' }} />
        <p style={{ color: 'var(--color-text-muted)' }}>Loading Approval Process Handbook application framework...</p>
      </div>
    );
  }

  return (
    <div style={{ maxWidth: '1200px', margin: '0 auto', paddingBottom: '40px' }}>
      {/* Header with Title and Action Bar */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'flex-start',
          marginBottom: '20px',
          flexWrap: 'wrap',
          gap: '12px',
        }}
      >
        <div>
          <div style={{ fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.08em', color: 'var(--color-teal)', fontWeight: '800' }}>
            APPROVAL PROCESS HANDBOOK (APH) · FORMAL SUBMISSION WORKFLOW
          </div>
          <h1 style={{ fontSize: '24px', fontWeight: '800', color: 'var(--color-navy)', margin: '4px 0 2px' }}>
            {typeDetail ? typeDetail.name : 'AICTE Institutional Approval Application'}
          </h1>
          <p style={{ color: 'var(--color-text-muted)', fontSize: '13px' }}>
            {applicationId
              ? `Official Dossier: ${application?.applicationReference || 'In Progress'} (${academicYear}) · Status: ${application?.status || 'Submitted'}`
              : draftReference
              ? `Draft Reference: ${draftReference} (${academicYear}) · Status: Saved to Server (DRAFT)`
              : selectedTypeCode
              ? `Category: ${selectedTypeCode} · Initializing draft on server...`
              : 'Complete statutory institutional filing according to applicable AICTE norms.'}
          </p>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          {selectedTypeCode && currentStep < 5 && (
            <>
              <button
                id="btnSaveDraftTop"
                onClick={handleSaveDraft}
                disabled={savingDraft}
                className="btn btn-outline btn-sm"
                style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
                title="Save progress in browser storage"
              >
                {savingDraft ? <Loader2 size={13} className="animate-spin" /> : <Save size={13} />}
                Save Draft
              </button>

              <button
                onClick={handleDiscardDraft}
                className="btn btn-outline btn-sm"
                style={{ color: '#CF1322', borderColor: '#FFA39E', gap: '4px' }}
                title="Discard browser draft and choose another category"
              >
                <RotateCcw size={13} /> Reset Draft
              </button>
            </>
          )}

          <Link to="/applications" className="btn btn-outline btn-sm">
            Exit to Applications
          </Link>
        </div>
      </div>

      {/* Save Notification / Error Banner */}
      {saveMessage && (
        <div
          style={{
            padding: '10px 16px',
            marginBottom: '16px',
            borderRadius: '6px',
            background: '#EDFDF5',
            border: '1px solid #B7EB8F',
            color: '#135200',
            fontSize: '13px',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
          }}
        >
          <CheckCircle2 size={16} /> {saveMessage}
        </div>
      )}

      {errorMessage && (
        <div
          style={{
            padding: '12px 16px',
            marginBottom: '16px',
            borderRadius: '6px',
            background: '#FFF1F0',
            border: '1px solid #FFA39E',
            color: '#CF1322',
            fontSize: '13px',
            display: 'flex',
            alignItems: 'flex-start',
            gap: '8px',
          }}
        >
          <AlertTriangle size={16} style={{ flexShrink: 0, marginTop: '2px' }} />
          <div>
            <strong>Notice:</strong>
            <div>{errorMessage}</div>
          </div>
        </div>
      )}

      {/* Step Progress Stepper */}
      <div
        className="panel"
        style={{
          padding: '16px 20px',
          marginBottom: '24px',
          background: '#FFFFFF',
          borderRadius: '8px',
          border: '1px solid var(--color-border)',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', position: 'relative' }}>
          {[
            { step: 1, label: 'Application Category', icon: Layers },
            { step: 2, label: 'Form Entry', icon: Building2 },
            { step: 3, label: 'Statutory Documents', icon: Upload },
            { step: 4, label: 'Completeness Check', icon: FileCheck },
            { step: 5, label: 'Formal Submission', icon: ShieldCheck },
          ].map((item, idx) => {
            const isCompleted = currentStep > item.step || (currentStep === 5 && item.step === 5);
            const isCurrent = currentStep === item.step;
            const IconComponent = item.icon;

            return (
              <React.Fragment key={item.step}>
                <div
                  onClick={() => {
                    if (selectedTypeCode && item.step <= 4) {
                      setCurrentStep(item.step);
                    }
                  }}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '10px',
                    cursor: selectedTypeCode && item.step <= 4 ? 'pointer' : 'default',
                    opacity: (isCurrent || isCompleted) ? 1 : 0.5,
                  }}
                >
                  <div
                    style={{
                      width: '32px',
                      height: '32px',
                      borderRadius: '50%',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontSize: '13px',
                      fontWeight: '700',
                      background: isCompleted ? '#52C41A' : isCurrent ? 'var(--color-blue)' : '#E2E8F0',
                      color: isCompleted || isCurrent ? '#FFFFFF' : '#475569',
                      transition: 'all 0.2s',
                    }}
                  >
                    {isCompleted && item.step !== 5 ? <Check size={16} /> : <IconComponent size={15} />}
                  </div>
                  <div>
                    <div style={{ fontSize: '10px', textTransform: 'uppercase', color: 'var(--color-text-muted)', fontWeight: '700' }}>
                      Step {item.step}
                    </div>
                    <div style={{ fontSize: '13px', fontWeight: isCurrent ? '700' : '500', color: isCurrent ? 'var(--color-navy)' : 'inherit' }}>
                      {item.label}
                    </div>
                  </div>
                </div>

                {idx < 4 && (
                  <div
                    style={{
                      flex: 1,
                      height: '2px',
                      background: currentStep > item.step ? '#52C41A' : '#E2E8F0',
                      margin: '0 12px',
                    }}
                  />
                )}
              </React.Fragment>
            );
          })}
        </div>
      </div>

      {/* ========================================================================= */}
      {/* STEP 1: Application Category Selection (NO DATABASE ROW CREATED) */}
      {/* ========================================================================= */}
      {currentStep === 1 && (
        <div>
          <div className="panel" style={{ padding: '20px', marginBottom: '20px', background: '#FCFDFE' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px' }}>
              <div>
                <h2 style={{ fontSize: '16px', fontWeight: '800', color: 'var(--color-navy)' }}>
                  Select AICTE Approval Category
                </h2>
                <p style={{ color: 'var(--color-text-muted)', fontSize: '13px', marginTop: '2px' }}>
                  All 12 statutory categories from the AICTE Approval Process Handbook (APH) are available. Application dossier is staged in your browser until final submission.
                </p>
              </div>

              <div style={{ position: 'relative', width: '300px' }}>
                <Search size={14} style={{ position: 'absolute', left: '10px', top: '10px', color: '#94A3B8' }} />
                <input
                  type="text"
                  placeholder="Search application type or clause..."
                  value={typeSearchTerm}
                  onChange={(e) => setTypeSearchTerm(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '8px 10px 8px 32px',
                    borderRadius: '6px',
                    border: '1px solid var(--color-border)',
                    fontSize: '13px',
                  }}
                />
              </div>
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(360px, 1fr))', gap: '16px' }}>
            {filteredTypes.map((type) => {
              const isSelected = selectedTypeCode === type.code;
              return (
                <div
                  key={type.code}
                  onClick={() => handleSelectType(type.code)}
                  style={{
                    background: '#FFFFFF',
                    border: isSelected ? '2px solid var(--color-blue)' : '1px solid var(--color-border)',
                    borderRadius: '8px',
                    padding: '18px',
                    cursor: 'pointer',
                    display: 'flex',
                    flexDirection: 'column',
                    justifyContent: 'space-between',
                    boxShadow: isSelected ? '0 4px 12px rgba(24, 144, 255, 0.15)' : '0 1px 3px rgba(0,0,0,0.05)',
                    transition: 'all 0.15s ease',
                  }}
                >
                  <div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '8px', marginBottom: '8px' }}>
                      <span className="badge badge-blue" style={{ fontSize: '10px' }}>
                        {type.aphClause}
                      </span>
                      <span style={{ fontSize: '11px', color: 'var(--color-text-muted)', fontWeight: '600' }}>
                        {type.documentCount} Required Docs
                      </span>
                    </div>

                    <h3 style={{ fontSize: '15px', fontWeight: '700', color: 'var(--color-navy)', marginBottom: '6px' }}>
                      {type.name}
                    </h3>

                    <p style={{ color: 'var(--color-text-muted)', fontSize: '12px', lineHeight: '1.4', marginBottom: '12px' }}>
                      {type.description}
                    </p>

                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px', marginBottom: '14px' }}>
                      {type.applicableLevels.map((lvl) => (
                        <span
                          key={lvl}
                          style={{
                            fontSize: '10px',
                            background: '#F1F5F9',
                            color: '#334155',
                            padding: '2px 6px',
                            borderRadius: '4px',
                            fontWeight: '600',
                          }}
                        >
                          {lvl}
                        </span>
                      ))}
                    </div>
                  </div>

                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', paddingTop: '10px', borderTop: '1px solid #F1F5F9' }}>
                    <span style={{ fontSize: '11px', color: 'var(--color-teal)', fontWeight: '700' }}>
                      {type.category}
                    </span>
                    <button
                      className={isSelected ? 'btn btn-primary btn-sm' : 'btn btn-outline btn-sm'}
                      style={{ gap: '4px' }}
                    >
                      Select <ArrowRight size={12} />
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* ========================================================================= */}
      {/* STEP 2: Institutional & Statutory Form Entry (Saved in Browser) */}
      {/* ========================================================================= */}
      {currentStep === 2 && typeDetail && (
        <div>
          <div className="panel" style={{ padding: '16px 20px', marginBottom: '16px', background: '#F8FAFC' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '8px' }}>
              <div>
                <span className="badge badge-blue" style={{ marginRight: '8px' }}>
                  {typeDetail.aphClause}
                </span>
                <strong style={{ color: 'var(--color-navy)', fontSize: '14px' }}>{typeDetail.name}</strong>
              </div>
              <div style={{ fontSize: '12px', color: 'var(--color-text-muted)' }}>
                Field entries are automatically preserved in your browser.
              </div>
            </div>
          </div>

          {typeDetail.sections.map((section) => (
            <div
              key={section.id}
              className="panel"
              style={{
                marginBottom: '20px',
                padding: '20px',
                background: '#FFFFFF',
                borderRadius: '8px',
                border: '1px solid var(--color-border)',
              }}
            >
              <div style={{ marginBottom: '16px', borderBottom: '1px solid #F1F5F9', paddingBottom: '10px' }}>
                <h3 style={{ fontSize: '16px', fontWeight: '800', color: 'var(--color-navy)', margin: '0 0 4px' }}>
                  {section.title}
                </h3>
                <p style={{ fontSize: '12px', color: 'var(--color-text-muted)', margin: 0 }}>
                  {section.description}
                </p>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '16px' }}>
                {section.fields.map((field) => {
                  const val = formData[field.name] ?? '';

                  if (field.type === 'checkbox') {
                    return (
                      <div
                        key={field.name}
                        style={{
                          gridColumn: '1 / -1',
                          padding: '12px 14px',
                          background: '#F8FAFC',
                          borderRadius: '6px',
                          border: '1px solid #E2E8F0',
                          display: 'flex',
                          alignItems: 'flex-start',
                          gap: '10px',
                        }}
                      >
                        <input
                          type="checkbox"
                          id={field.name}
                          checked={Boolean(val)}
                          onChange={(e) => handleFieldChange(field.name, e.target.checked)}
                          style={{ marginTop: '3px', cursor: 'pointer', width: '16px', height: '16px' }}
                        />
                        <label htmlFor={field.name} style={{ cursor: 'pointer', fontSize: '13px' }}>
                          <span style={{ fontWeight: '700', color: 'var(--color-navy)' }}>
                            {field.label} {field.required && <span style={{ color: '#E53E3E' }}>*</span>}
                          </span>
                          {field.helperText && (
                            <div style={{ fontSize: '11px', color: 'var(--color-text-muted)', marginTop: '2px' }}>
                              {field.helperText}
                            </div>
                          )}
                        </label>
                      </div>
                    );
                  }

                  if (field.type === 'select') {
                    return (
                      <div key={field.name}>
                        <label htmlFor={field.name} className="form-label" style={{ fontWeight: '600', fontSize: '12px' }}>
                          {field.label} {field.required && <span style={{ color: '#E53E3E' }}>*</span>}
                        </label>
                        <select
                          id={field.name}
                          className="form-select"
                          value={val}
                          onChange={(e) => handleFieldChange(field.name, e.target.value)}
                        >
                          <option value="">-- Select {field.label} --</option>
                          {field.options?.map((opt) => (
                            <option key={opt} value={opt}>
                              {opt}
                            </option>
                          ))}
                        </select>
                        {field.helperText && (
                          <div style={{ fontSize: '11px', color: 'var(--color-text-muted)', marginTop: '4px' }}>
                            {field.helperText}
                          </div>
                        )}
                      </div>
                    );
                  }

                  return (
                    <div key={field.name}>
                      <label htmlFor={field.name} className="form-label" style={{ fontWeight: '600', fontSize: '12px' }}>
                        {field.label} {field.required && <span style={{ color: '#E53E3E' }}>*</span>}
                      </label>
                      <input
                        type={field.type === 'number' ? 'number' : 'text'}
                        id={field.name}
                        placeholder={field.placeholder || ''}
                        className="form-input"
                        value={val}
                        onChange={(e) => handleFieldChange(field.name, e.target.value)}
                        style={{
                          width: '100%',
                          padding: '8px 10px',
                          borderRadius: '6px',
                          border: '1px solid var(--color-border)',
                          fontSize: '13px',
                        }}
                      />
                      {field.helperText && (
                        <div style={{ fontSize: '11px', color: 'var(--color-text-muted)', marginTop: '4px' }}>
                          {field.helperText}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          ))}

          {/* Stepper Navigation Buttons */}
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '24px' }}>
            <button
              onClick={() => setCurrentStep(1)}
              className="btn btn-outline"
              style={{ gap: '6px' }}
            >
              <ArrowLeft size={14} /> Back to Type Selection
            </button>

            <div style={{ display: 'flex', gap: '10px' }}>
              <button
                onClick={handleSaveDraft}
                disabled={savingDraft}
                className="btn btn-outline"
                style={{ gap: '6px' }}
              >
                {savingDraft ? <Loader2 size={14} className="animate-spin" /> : <Save size={14} />}
                Save Draft
              </button>

              <button
                onClick={() => setCurrentStep(3)}
                className="btn btn-primary"
                style={{ gap: '6px' }}
              >
                Continue to Document Checklist <ArrowRight size={14} />
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ========================================================================= */}
      {/* STEP 3: Mandatory Statutory Documents (Staged Locally in Browser) */}
      {/* ========================================================================= */}
      {currentStep === 3 && typeDetail && (
        <div>
          <div className="panel" style={{ padding: '16px 20px', marginBottom: '20px', background: '#FCFDFE' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '10px' }}>
              <div>
                <h2 style={{ fontSize: '16px', fontWeight: '800', color: 'var(--color-navy)', margin: '0 0 4px' }}>
                  Statutory Document Checklist ({typeDetail.aphClause})
                </h2>
                <p style={{ color: 'var(--color-text-muted)', fontSize: '12px', margin: 0 }}>
                  Select official PDF files for each statutory requirement. Files remain staged in your browser until formal submission.
                </p>
              </div>

              <div style={{ textAlign: 'right' }}>
                <div style={{ fontSize: '11px', color: 'var(--color-text-muted)', fontWeight: '600' }}>
                  Document Readiness: {evaluation.documentCompletionPercentage}%
                </div>
                <div
                  style={{
                    width: '140px',
                    height: '6px',
                    background: '#E2E8F0',
                    borderRadius: '3px',
                    overflow: 'hidden',
                    marginTop: '4px',
                  }}
                >
                  <div
                    style={{
                      width: `${evaluation.documentCompletionPercentage}%`,
                      height: '100%',
                      background: evaluation.documentCompletionPercentage === 100 ? '#52C41A' : 'var(--color-blue)',
                      transition: 'width 0.3s ease',
                    }}
                  />
                </div>
              </div>
            </div>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            {typeDetail.documentRequirements.map((req) => {
              const backendDoc = checklist?.documents?.find((d) => d.requirementCode === req.code);
              const isUploaded = Boolean(backendDoc?.uploaded);
              const isUploadingThis = uploadingDocCode === req.code;

              return (
                <div
                  key={req.code}
                  className="panel"
                  style={{
                    padding: '16px 20px',
                    background: '#FFFFFF',
                    borderRadius: '8px',
                    border: isUploaded ? '1px solid #B7EB8F' : '1px solid var(--color-border)',
                    boxShadow: '0 1px 3px rgba(0,0,0,0.03)',
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '12px' }}>
                    <div style={{ flex: 1, minWidth: '280px' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
                        {isUploaded ? (
                          <CheckCircle2 size={18} style={{ color: '#52C41A', flexShrink: 0 }} />
                        ) : (
                          <div
                            style={{
                              width: '18px',
                              height: '18px',
                              borderRadius: '50%',
                              border: '2px solid #CBD5E1',
                              flexShrink: 0,
                            }}
                          />
                        )}
                        <h4 style={{ fontSize: '14px', fontWeight: '700', color: 'var(--color-navy)', margin: 0 }}>
                          {req.name}
                        </h4>
                        <span className="badge badge-blue" style={{ fontSize: '10px' }}>
                          {req.aphClause}
                        </span>
                        {req.mandatory && (
                          <span style={{ fontSize: '11px', color: '#E53E3E', fontWeight: '700' }}>
                            Mandatory
                          </span>
                        )}
                      </div>

                      <p style={{ fontSize: '12px', color: 'var(--color-text-muted)', margin: '0 0 8px 26px' }}>
                        {req.description}
                      </p>

                      {/* Display Persisted Server Document */}
                      {isUploaded && (
                        <div
                          style={{
                            marginLeft: '26px',
                            padding: '8px 12px',
                            background: '#F6FFED',
                            border: '1px solid #D9F7BE',
                            borderRadius: '6px',
                            display: 'flex',
                            alignItems: 'center',
                            flexWrap: 'wrap',
                            gap: '12px',
                            fontSize: '11px',
                          }}
                        >
                          <span style={{ fontWeight: '700', color: '#237804' }}>
                            ✓ {backendDoc.fileName} (Stored in MinIO)
                          </span>
                          <span style={{ color: '#595959' }}>
                            Size: {(backendDoc.fileSize / 1024).toFixed(1)} KB
                          </span>
                          {backendDoc.fileHash && (
                            <span style={{ color: '#8C8C8C', fontSize: '10px', fontFamily: 'monospace' }}>
                              SHA: {backendDoc.fileHash.substring(0, 12)}...
                            </span>
                          )}
                        </div>
                      )}
                    </div>

                    {/* Document Upload & View Actions */}
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      {isUploaded && (
                        <>
                          <a
                            href={
                              draftId
                                ? `http://localhost:8080/api/v1/application-drafts/${draftId}/documents/${backendDoc.documentId}`
                                : `http://localhost:8080/api/v1/applications/${applicationId}/documents/${backendDoc.documentId}`
                            }
                            target="_blank"
                            rel="noopener noreferrer"
                            className="btn btn-outline btn-sm"
                            style={{ gap: '4px' }}
                          >
                            <Download size={13} /> View
                          </a>

                          <button
                            onClick={() => handleDeleteBackendDocument(backendDoc.documentId)}
                            className="btn btn-outline btn-sm"
                            style={{ color: '#CF1322', borderColor: '#FFA39E', gap: '4px' }}
                          >
                            <Trash2 size={13} /> Remove
                          </button>
                        </>
                      )}

                      <label
                        className="btn btn-primary btn-sm"
                        style={{
                          cursor: isUploadingThis ? 'not-allowed' : 'pointer',
                          gap: '6px',
                          opacity: isUploadingThis ? 0.7 : 1,
                        }}
                      >
                        {isUploadingThis ? (
                          <>
                            <Loader2 size={13} className="animate-spin" /> Uploading...
                          </>
                        ) : (
                          <>
                            <Upload size={13} /> {isUploaded ? 'Replace File' : 'Upload PDF'}
                          </>
                        )}
                        <input
                          type="file"
                          accept=".pdf,application/pdf"
                          disabled={isUploadingThis}
                          onChange={(e) => {
                            if (e.target.files?.[0]) {
                              handleFileSelect(req.code, e.target.files[0]);
                              e.target.value = '';
                            }
                          }}
                          style={{ display: 'none' }}
                        />
                      </label>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>

          {/* Stepper Navigation Buttons */}
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '24px' }}>
            <button
              onClick={() => setCurrentStep(2)}
              className="btn btn-outline"
              style={{ gap: '6px' }}
            >
              <ArrowLeft size={14} /> Back to Form Entry
            </button>

            <button
              onClick={() => setCurrentStep(4)}
              className="btn btn-primary"
              style={{ gap: '6px' }}
            >
              Review Completeness & Verification <ArrowRight size={14} />
            </button>
          </div>
        </div>
      )}

      {/* ========================================================================= */}
      {/* STEP 4: Review & Completeness Check (Real-Time In-Browser Gate) */}
      {/* ========================================================================= */}
      {currentStep === 4 && (
        <div>
          <div className="panel" style={{ padding: '20px', marginBottom: '20px', background: '#FFFFFF' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px', marginBottom: '16px' }}>
              <div>
                <h2 style={{ fontSize: '18px', fontWeight: '800', color: 'var(--color-navy)', margin: '0 0 4px' }}>
                  Authoritative Readiness Check & Verification
                </h2>
                <p style={{ color: 'var(--color-text-muted)', fontSize: '13px', margin: 0 }}>
                  System gate validation against AICTE Approval Process Handbook mandatory clauses.
                </p>
              </div>

              <div
                style={{
                  padding: '8px 16px',
                  borderRadius: '20px',
                  background: evaluation.submittable ? '#F6FFED' : '#FFF7E6',
                  border: evaluation.submittable ? '1px solid #B7EB8F' : '1px solid #FFD591',
                  color: evaluation.submittable ? '#237804' : '#D46B08',
                  fontWeight: '700',
                  fontSize: '13px',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px',
                }}
              >
                {evaluation.submittable ? <CheckCircle2 size={16} /> : <AlertTriangle size={16} />}
                {evaluation.submittable ? 'Application Ready for Submission' : 'Incomplete Requirements Blocking Submission'}
              </div>
            </div>

            {/* Overall Progress Metrics */}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '12px' }}>
              <div style={{ background: '#F8FAFC', padding: '14px', borderRadius: '6px', border: '1px solid #E2E8F0' }}>
                <div style={{ fontSize: '11px', color: 'var(--color-text-muted)', fontWeight: '600' }}>Overall Readiness</div>
                <div style={{ fontSize: '22px', fontWeight: '800', color: 'var(--color-navy)', margin: '4px 0' }}>
                  {evaluation.completionPercentage}%
                </div>
                <div style={{ height: '4px', background: '#CBD5E1', borderRadius: '2px', overflow: 'hidden' }}>
                  <div style={{ width: `${evaluation.completionPercentage}%`, height: '100%', background: 'var(--color-blue)' }} />
                </div>
              </div>

              <div style={{ background: '#F8FAFC', padding: '14px', borderRadius: '6px', border: '1px solid #E2E8F0' }}>
                <div style={{ fontSize: '11px', color: 'var(--color-text-muted)', fontWeight: '600' }}>Form Fields Complete</div>
                <div style={{ fontSize: '22px', fontWeight: '800', color: 'var(--color-navy)', margin: '4px 0' }}>
                  {evaluation.formCompletionPercentage}%
                </div>
                <div style={{ height: '4px', background: '#CBD5E1', borderRadius: '2px', overflow: 'hidden' }}>
                  <div style={{ width: `${evaluation.formCompletionPercentage}%`, height: '100%', background: '#13C2C2' }} />
                </div>
              </div>

              <div style={{ background: '#F8FAFC', padding: '14px', borderRadius: '6px', border: '1px solid #E2E8F0' }}>
                <div style={{ fontSize: '11px', color: 'var(--color-text-muted)', fontWeight: '600' }}>Statutory Documents</div>
                <div style={{ fontSize: '22px', fontWeight: '800', color: 'var(--color-navy)', margin: '4px 0' }}>
                  {evaluation.documentCompletionPercentage}%
                </div>
                <div style={{ height: '4px', background: '#CBD5E1', borderRadius: '2px', overflow: 'hidden' }}>
                  <div style={{ width: `${evaluation.documentCompletionPercentage}%`, height: '100%', background: '#52C41A' }} />
                </div>
              </div>
            </div>
          </div>

          {/* Blocking Requirements Notice */}
          {evaluation.blockingDeficiencies.length > 0 && (
            <div
              className="panel"
              style={{
                padding: '16px 20px',
                marginBottom: '20px',
                background: '#FFF1F0',
                border: '1px solid #FFA39E',
                borderRadius: '8px',
              }}
            >
              <h3 style={{ fontSize: '14px', fontWeight: '700', color: '#CF1322', margin: '0 0 8px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                <AlertTriangle size={16} /> Mandatory Requirements to Satisfy Before Submission ({evaluation.blockingDeficiencies.length})
              </h3>
              <ul style={{ margin: 0, paddingLeft: '20px', color: '#434343', fontSize: '13px', lineHeight: '1.6' }}>
                {evaluation.blockingDeficiencies.map((item, idx) => (
                  <li key={idx}>{item}</li>
                ))}
              </ul>
            </div>
          )}

          {/* Form Sections Review Breakdown */}
          <div className="panel" style={{ padding: '20px', marginBottom: '20px', background: '#FFFFFF' }}>
            <h3 style={{ fontSize: '15px', fontWeight: '800', color: 'var(--color-navy)', marginBottom: '14px' }}>
              Form Sections Status
            </h3>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '12px' }}>
              {evaluation.sections.map((sec) => (
                <div
                  key={sec.id}
                  style={{
                    padding: '12px 16px',
                    borderRadius: '6px',
                    background: sec.complete ? '#F6FFED' : '#FFFBE6',
                    border: sec.complete ? '1px solid #B7EB8F' : '1px solid #FFE58F',
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                  }}
                >
                  <div>
                    <div style={{ fontSize: '13px', fontWeight: '700', color: 'var(--color-navy)' }}>
                      {sec.title}
                    </div>
                    <div style={{ fontSize: '11px', color: 'var(--color-text-muted)' }}>
                      {sec.completedFields} / {sec.totalFields} fields completed
                    </div>
                  </div>
                  <div>
                    {sec.complete ? (
                      <span className="badge badge-green" style={{ fontSize: '11px' }}>
                        Complete
                      </span>
                    ) : (
                      <button
                        onClick={() => setCurrentStep(2)}
                        className="btn btn-outline btn-sm"
                        style={{ fontSize: '11px', padding: '3px 8px' }}
                      >
                        Fix Missing
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Stepper Navigation Buttons */}
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '24px' }}>
            <button
              onClick={() => setCurrentStep(3)}
              className="btn btn-outline"
              style={{ gap: '6px' }}
            >
              <ArrowLeft size={14} /> Back to Document Checklist
            </button>

            <button
              id="btnProceedToDeclaration"
              disabled={!evaluation.submittable}
              onClick={() => setCurrentStep(5)}
              className="btn btn-primary"
              style={{
                gap: '6px',
                opacity: evaluation.submittable ? 1 : 0.5,
                cursor: evaluation.submittable ? 'pointer' : 'not-allowed',
              }}
            >
              Proceed to Final Submission <ArrowRight size={14} />
            </button>
          </div>
        </div>
      )}

      {/* ========================================================================= */}
      {/* STEP 5: Formal Submission & Confirmation View */}
      {/* ========================================================================= */}
      {currentStep === 5 && (
        <div>
          {!submissionResult ? (
            /* Pre-submission declaration panel */
            <div className="panel" style={{ padding: '24px', background: '#FFFFFF', borderRadius: '8px' }}>
              <div style={{ textAlign: 'center', maxWidth: '640px', margin: '0 auto 24px' }}>
                <ShieldCheck size={48} style={{ color: 'var(--color-blue)', margin: '0 auto 12px' }} />
                <h2 style={{ fontSize: '20px', fontWeight: '800', color: 'var(--color-navy)', margin: '0 0 6px' }}>
                  Statutory Undertaking &amp; Final Submission
                </h2>
                <p style={{ color: 'var(--color-text-muted)', fontSize: '13px' }}>
                  All statutory mandatory requirements have been satisfied. You are ready to formally submit the dossier for AICTE technical scrutiny.
                </p>
              </div>

              <div
                style={{
                  background: '#F8FAFC',
                  border: '1px solid #E2E8F0',
                  borderRadius: '6px',
                  padding: '16px',
                  marginBottom: '20px',
                  fontSize: '12px',
                  lineHeight: '1.6',
                  color: '#334155',
                }}
              >
                <div style={{ fontWeight: '700', marginBottom: '8px', color: 'var(--color-navy)' }}>
                  Statutory Legal Undertaking by Head of Institution:
                </div>
                <p style={{ margin: '0 0 8px' }}>
                  1. I hereby certify that the information, data, and statutory documents uploaded herein are true, correct, and authentic.
                </p>
                <p style={{ margin: '0 0 8px' }}>
                  2. I understand that upon submission, an immutable snapshot of all uploaded documents and form fields will be locked and audited in the AICTE national repository.
                </p>
                <p style={{ margin: 0 }}>
                  3. If any document is found forged, incorrect, or violating the AICTE Approval Process Handbook, the Council reserves the right to initiate penal action under Section 10(k) of the AICTE Act.
                </p>
              </div>

              <div style={{ display: 'flex', justifyContent: 'center', gap: '12px' }}>
                <button
                  onClick={() => setCurrentStep(4)}
                  className="btn btn-outline"
                  style={{ gap: '6px' }}
                >
                  <ArrowLeft size={14} /> Back to Checklist
                </button>

                <button
                  id="btnFinalSubmit"
                  onClick={handleSubmitApplication}
                  disabled={submitting}
                  className="btn btn-primary"
                  style={{
                    background: '#1677FF',
                    padding: '9px 24px',
                    fontSize: '14px',
                    fontWeight: '700',
                    gap: '8px',
                  }}
                >
                  {submitting ? (
                    <>
                      <Loader2 size={16} className="animate-spin" /> Submitting Application &amp; Documents...
                    </>
                  ) : (
                    <>
                      <Send size={15} /> Confirm &amp; Submit Application
                    </>
                  )}
                </button>
              </div>
            </div>
          ) : (
            /* Successful submission confirmation card */
            <div
              className="panel"
              style={{
                padding: '32px 24px',
                background: '#FFFFFF',
                borderRadius: '8px',
                textAlign: 'center',
                maxWidth: '680px',
                margin: '0 auto',
                boxShadow: '0 4px 16px rgba(0,0,0,0.06)',
              }}
            >
              <div
                style={{
                  width: '64px',
                  height: '64px',
                  borderRadius: '50%',
                  background: '#EDFDF5',
                  color: '#52C41A',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  margin: '0 auto 16px',
                }}
              >
                <CheckCircle2 size={36} />
              </div>

              <h2 style={{ fontSize: '22px', fontWeight: '800', color: 'var(--color-navy)', margin: '0 0 8px' }}>
                Application Formally Submitted for Scrutiny
              </h2>

              <p style={{ color: 'var(--color-text-muted)', fontSize: '13px', marginBottom: '24px' }}>
                Your dossier has been recorded with an immutable snapshot in the SAGE platform and queued for authoritative technical scrutiny.
              </p>

              <div
                style={{
                  background: '#F8FAFC',
                  border: '1px solid #E2E8F0',
                  borderRadius: '8px',
                  padding: '18px',
                  textAlign: 'left',
                  marginBottom: '24px',
                  display: 'grid',
                  gridTemplateColumns: '1fr 1fr',
                  gap: '12px',
                  fontSize: '13px',
                }}
              >
                <div>
                  <div style={{ fontSize: '11px', color: 'var(--color-text-muted)', fontWeight: '600' }}>
                    Application Reference
                  </div>
                  <div style={{ fontWeight: '800', color: 'var(--color-navy)' }}>
                    {submissionResult.applicationReference}
                  </div>
                </div>

                <div>
                  <div style={{ fontSize: '11px', color: 'var(--color-text-muted)', fontWeight: '600' }}>
                    Workflow Stage
                  </div>
                  <div style={{ fontWeight: '800', color: '#1677FF' }}>
                    {submissionResult.currentStage}
                  </div>
                </div>

                <div>
                  <div style={{ fontSize: '11px', color: 'var(--color-text-muted)', fontWeight: '600' }}>
                    Current Dossier Owner
                  </div>
                  <div style={{ fontWeight: '700', color: '#1E293B' }}>
                    {submissionResult.ownerName}
                  </div>
                </div>

                <div>
                  <div style={{ fontSize: '11px', color: 'var(--color-text-muted)', fontWeight: '600' }}>
                    Submission Timestamp
                  </div>
                  <div style={{ fontWeight: '600', color: '#1E293B' }}>
                    {new Date(submissionResult.submittedAt || Date.now()).toLocaleString()}
                  </div>
                </div>
              </div>

              <div style={{ display: 'flex', justifyContent: 'center', gap: '12px' }}>
                <Link to={`/applications/${applicationId}`} className="btn btn-primary" style={{ gap: '6px' }}>
                  <FileText size={14} /> View Dossier Details
                </Link>
                <Link
                  to="/applications"
                  state={{
                    promotedAppRef:
                      submissionResult.officialApplicationReference || submissionResult.applicationReference,
                    officialApplicationId:
                      submissionResult.officialApplicationId || submissionResult.applicationId,
                    successMessage: `Draft ${submissionResult.draftReference || ''} was successfully promoted to official application ${
                      submissionResult.officialApplicationReference || submissionResult.applicationReference
                    }.`,
                  }}
                  className="btn btn-outline"
                  style={{ gap: '6px' }}
                >
                  Return to Applications Explorer
                </Link>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

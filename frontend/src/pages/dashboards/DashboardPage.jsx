// SIH 26262 - Master Dashboard Page (Role Dispatcher under single /dashboard route)

import React from 'react';
import { useAuth } from '../../context/AuthContext';
import { ROLES } from '../../constants/roles';

// Role-Specific Dashboards
import { InstitutionDashboard } from './InstitutionDashboard';
import { RegionalOfficerDashboard } from './RegionalOfficerDashboard';
import { ScrutinyOfficerDashboard } from './ScrutinyOfficerDashboard';
import { EVCCoordinatorDashboard } from './EVCCoordinatorDashboard';
import { ComplianceReviewerDashboard } from './ComplianceReviewerDashboard';
import { SeniorManagementDashboard } from './SeniorManagementDashboard';
import { EVCMemberDashboard } from './EVCMemberDashboard';

export const DashboardPage = () => {
  const { role, logout } = useAuth();

  React.useEffect(() => {
    if (role === ROLES.ADMINISTRATOR || role === 'Administrator' || role === 'Admin') {
      logout();
    }
  }, [role, logout]);

  if (role === ROLES.ADMINISTRATOR || role === 'Administrator' || role === 'Admin') {
    return (
      <div style={{ padding: '30px', textAlign: 'center' }}>
        <p style={{ color: 'var(--color-critical)', fontWeight: '700' }}>
          Administrative access is not permitted through this portal.
        </p>
      </div>
    );
  }

  switch (role) {
    case ROLES.INSTITUTION_REP:
      return <InstitutionDashboard />;

    case ROLES.REGIONAL_OFFICER:
      return <RegionalOfficerDashboard />;

    case ROLES.SCRUTINY_OFFICER:
      return <ScrutinyOfficerDashboard />;

    case ROLES.EVC_COORDINATOR:
      return <EVCCoordinatorDashboard />;

    case ROLES.EVC_MEMBER:
      return <EVCMemberDashboard />;

    case ROLES.COMPLIANCE_REVIEWER:
      return <ComplianceReviewerDashboard />;

    case ROLES.SENIOR_MANAGEMENT:
      return <SeniorManagementDashboard />;

    default:
      return <RegionalOfficerDashboard />;
  }
};

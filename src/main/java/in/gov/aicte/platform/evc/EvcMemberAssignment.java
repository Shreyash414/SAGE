package in.gov.aicte.platform.evc;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "evc_member_assignments")
public class EvcMemberAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evc_visit_id", nullable = false)
    private EvcVisit evcVisit;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private EvcMemberProfile member;

    @Column(name = "committee_role", nullable = false, length = 64)
    private String committeeRole = "MEMBER";

    @Column(name = "notes", length = 255)
    private String notes;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    public EvcMemberAssignment() {
    }

    public EvcMemberAssignment(EvcVisit evcVisit, EvcMemberProfile member, String committeeRole, String notes) {
        this.evcVisit = evcVisit;
        this.member = member;
        this.committeeRole = committeeRole != null ? committeeRole : "MEMBER";
        this.notes = notes;
    }

    @PrePersist
    protected void onCreate() {
        if (this.assignedAt == null) {
            this.assignedAt = Instant.now();
        }
        if (this.committeeRole == null) {
            this.committeeRole = "MEMBER";
        }
    }

    public Long getId() {
        return id;
    }

    public EvcVisit getEvcVisit() {
        return evcVisit;
    }

    public void setEvcVisit(EvcVisit evcVisit) {
        this.evcVisit = evcVisit;
    }

    public EvcMemberProfile getMember() {
        return member;
    }

    public void setMember(EvcMemberProfile member) {
        this.member = member;
    }

    public String getCommitteeRole() {
        return committeeRole;
    }

    public void setCommitteeRole(String committeeRole) {
        this.committeeRole = committeeRole;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }
}


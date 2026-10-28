package in.gov.aicte.platform.evc;

import in.gov.aicte.platform.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "evc_member_profiles")
public class EvcMemberProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, unique = true, length = 64)
    private String memberId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "full_name", nullable = false, length = 128)
    private String fullName;

    @Column(name = "designation", nullable = false, length = 128)
    private String designation;

    @Column(name = "organization", nullable = false, length = 255)
    private String organization;

    @Column(name = "expertise", nullable = false, length = 255)
    private String expertise;

    @Column(name = "verification_status", nullable = false, length = 32)
    private String verificationStatus = "VERIFIED";

    @Column(name = "availability_status", nullable = false, length = 64)
    private String availabilityStatus = "AVAILABLE";

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public EvcMemberProfile() {
    }

    public EvcMemberProfile(String memberId, User user, String fullName, String designation,
                            String organization, String expertise, String verificationStatus,
                            String availabilityStatus, String phone) {
        this.memberId = memberId;
        this.user = user;
        this.fullName = fullName;
        this.designation = designation;
        this.organization = organization;
        this.expertise = expertise;
        this.verificationStatus = verificationStatus != null ? verificationStatus : "VERIFIED";
        this.availabilityStatus = availabilityStatus != null ? availabilityStatus : "AVAILABLE";
        this.phone = phone;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.verificationStatus == null) {
            this.verificationStatus = "VERIFIED";
        }
        if (this.availabilityStatus == null) {
            this.availabilityStatus = "AVAILABLE";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public String getExpertise() {
        return expertise;
    }

    public void setExpertise(String expertise) {
        this.expertise = expertise;
    }

    public String getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(String verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public String getAvailabilityStatus() {
        return availabilityStatus;
    }

    public void setAvailabilityStatus(String availabilityStatus) {
        this.availabilityStatus = availabilityStatus;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}


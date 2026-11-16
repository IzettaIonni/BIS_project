package kz.gymbooking.domain;

import java.time.LocalDate;

public class Membership {

    private Long id;
    private Long clientId;
    private Long planId;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer visitsLeft;
    private MembershipStatus status;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getClientId() {
        return clientId;
    }

    public void setClientId(Long clientId) {
        this.clientId = clientId;
    }

    public Long getPlanId() {
        return planId;
    }

    public void setPlanId(Long planId) {
        this.planId = planId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public Integer getVisitsLeft() {
        return visitsLeft;
    }

    public void setVisitsLeft(Integer visitsLeft) {
        this.visitsLeft = visitsLeft;
    }

    public MembershipStatus getStatus() {
        return status;
    }

    public void setStatus(MembershipStatus status) {
        this.status = status;
    }

    public boolean coversDate(LocalDate d) {
        return status == MembershipStatus.ACTIVE && !d.isBefore(startDate) && !d.isAfter(endDate);
    }

    public boolean hasVisitsLeft() {
        return visitsLeft == null || visitsLeft > 0;
    }
}

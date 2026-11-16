package kz.gymbooking.domain;

public class MembershipPlan {

    private Long id;
    private String name;
    private int durationDays;
    private Integer visitLimit;
    private int price;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getDurationDays() {
        return durationDays;
    }

    public void setDurationDays(int durationDays) {
        this.durationDays = durationDays;
    }

    public Integer getVisitLimit() {
        return visitLimit;
    }

    public void setVisitLimit(Integer visitLimit) {
        this.visitLimit = visitLimit;
    }

    public int getPrice() {
        return price;
    }

    public void setPrice(int price) {
        this.price = price;
    }
}

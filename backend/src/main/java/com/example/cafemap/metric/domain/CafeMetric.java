package com.example.cafemap.metric.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "cafe_metrics")
public class CafeMetric {

    @Id
    private Long cafeId;

    @Column(nullable = false)
    private long viewCountDaily;

    @Column(nullable = false)
    private long viewCountWeekly;

    @Column(nullable = false)
    private long viewCountTotal;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal ratingAverage = BigDecimal.ZERO;

    @Column(nullable = false)
    private long ratingCount;

    @Column(nullable = false, precision = 8, scale = 3)
    private BigDecimal ratingScore = BigDecimal.ZERO;

    private Instant lastViewedAt;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal hotScore = BigDecimal.ZERO;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected CafeMetric() {
    }

    public CafeMetric(Long cafeId) {
        this.cafeId = cafeId;
    }

    public Long getCafeId() {
        return cafeId;
    }

    public long getViewCountTotal() {
        return viewCountTotal;
    }

    public BigDecimal getRatingAverage() {
        return ratingAverage;
    }

    public long getRatingCount() {
        return ratingCount;
    }

    public BigDecimal getHotScore() {
        return hotScore;
    }

    public void viewed() {
        this.viewCountDaily++;
        this.viewCountWeekly++;
        this.viewCountTotal++;
        this.lastViewedAt = Instant.now();
        this.hotScore = BigDecimal.valueOf(viewCountWeekly + viewCountDaily * 2.5d);
        this.updatedAt = Instant.now();
    }

    public void updateRating(BigDecimal average, long count) {
        this.ratingAverage = average;
        this.ratingCount = count;
        this.ratingScore = count < 5 ? average.multiply(BigDecimal.valueOf(0.8d)) : average;
        this.updatedAt = Instant.now();
    }
}

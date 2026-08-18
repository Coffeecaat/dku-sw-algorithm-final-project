package com.example.cafemap.menu.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "menu_items")
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long cafeId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false)
    private String imageOriginalUrl;

    @Column(nullable = false)
    private String imageThumbnailUrl;

    private String imageBlurhash;

    @Column(nullable = false)
    private boolean representative;

    private LocalDate releasedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MenuStatus status = MenuStatus.PENDING;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected MenuItem() {
    }

    public MenuItem(Long cafeId, String name, BigDecimal price, String description, String imageOriginalUrl,
                    String imageThumbnailUrl, LocalDate releasedAt) {
        this.cafeId = cafeId;
        this.name = name;
        this.price = price;
        this.description = description;
        this.imageOriginalUrl = imageOriginalUrl;
        this.imageThumbnailUrl = imageThumbnailUrl;
        this.releasedAt = releasedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getCafeId() {
        return cafeId;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getDescription() {
        return description;
    }

    public String getImageThumbnailUrl() {
        return imageThumbnailUrl;
    }

    public boolean isRepresentative() {
        return representative;
    }

    public LocalDate getReleasedAt() {
        return releasedAt;
    }

    public MenuStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setRepresentative(boolean representative) {
        this.representative = representative;
        this.updatedAt = Instant.now();
    }

    public void approve() {
        this.status = MenuStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void reject() {
        this.status = MenuStatus.REJECTED;
        this.updatedAt = Instant.now();
    }
}

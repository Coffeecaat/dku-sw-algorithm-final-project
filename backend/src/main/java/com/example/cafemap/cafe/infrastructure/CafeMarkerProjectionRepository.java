package com.example.cafemap.cafe.infrastructure;

import com.example.cafemap.cafe.domain.CafeStatus;
import com.example.cafemap.menu.domain.MenuStatus;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class CafeMarkerProjectionRepository {

    private final EntityManager entityManager;

    public CafeMarkerProjectionRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<MarkerProjectionRow> findHotRankedMarkers(
            double minLatitude,
            double maxLatitude,
            double minLongitude,
            double maxLongitude,
            int limit) {
        return entityManager.createQuery("""
                        select new com.example.cafemap.cafe.infrastructure.MarkerProjectionRow(
                            c.id,
                            c.latitude,
                            c.longitude,
                            m.imageThumbnailUrl,
                            cm.hotScore,
                            cm.ratingAverage,
                            cm.ratingCount,
                            c.activatedAt,
                            m.createdAt,
                            m.releasedAt
                        )
                        from Cafe c
                        join MenuItem m on m.cafeId = c.id
                        left join CafeMetric cm on cm.cafeId = c.id
                        where c.status = :cafeStatus
                          and m.status = :menuStatus
                          and m.representative = true
                          and c.latitude between :minLatitude and :maxLatitude
                          and c.longitude between :minLongitude and :maxLongitude
                        order by cm.hotScore desc, cm.ratingAverage desc, cm.ratingCount desc, c.id asc
                        """, MarkerProjectionRow.class)
                .setParameter("cafeStatus", CafeStatus.ACTIVE)
                .setParameter("menuStatus", MenuStatus.ACTIVE)
                .setParameter("minLatitude", minLatitude)
                .setParameter("maxLatitude", maxLatitude)
                .setParameter("minLongitude", minLongitude)
                .setParameter("maxLongitude", maxLongitude)
                .setMaxResults(limit)
                .getResultList();
    }
}

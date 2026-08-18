package com.example.cafemap.search.infrastructure;

import com.example.cafemap.cafe.domain.CafeStatus;
import com.example.cafemap.menu.domain.MenuStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class SearchCafeRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public List<CafeSearchRow> findActiveCafeRows() {
        return entityManager.createQuery("""
                        select new com.example.cafemap.search.infrastructure.CafeSearchRow(c, m, cm)
                        from Cafe c
                        join MenuItem m on m.cafeId = c.id
                        left join CafeMetric cm on cm.cafeId = c.id
                        where c.status = :cafeStatus
                          and m.status = :menuStatus
                          and m.representative = true
                        """, CafeSearchRow.class)
                .setParameter("cafeStatus", CafeStatus.ACTIVE)
                .setParameter("menuStatus", MenuStatus.ACTIVE)
                .getResultList();
    }

    public List<CafeSearchRow> findActiveCafeRowsByLike(
            String normalizedQuery,
            boolean bounded,
            double minLatitude,
            double maxLatitude,
            double minLongitude,
            double maxLongitude) {
        String pattern = "%" + normalizedQuery + "%";
        return entityManager.createQuery("""
                        select new com.example.cafemap.search.infrastructure.CafeSearchRow(c, m, cm)
                        from Cafe c
                        join MenuItem m on m.cafeId = c.id
                        left join CafeMetric cm on cm.cafeId = c.id
                        where c.status = :cafeStatus
                          and m.status = :menuStatus
                          and m.representative = true
                          and (:bounded = false or (
                            c.latitude between :minLatitude and :maxLatitude
                            and c.longitude between :minLongitude and :maxLongitude
                          ))
                          and (
                            lower(c.name) like :pattern
                            or lower(c.address) like :pattern
                            or lower(c.roadAddress) like :pattern
                            or lower(m.name) like :pattern
                          )
                        """, CafeSearchRow.class)
                .setParameter("cafeStatus", CafeStatus.ACTIVE)
                .setParameter("menuStatus", MenuStatus.ACTIVE)
                .setParameter("bounded", bounded)
                .setParameter("minLatitude", minLatitude)
                .setParameter("maxLatitude", maxLatitude)
                .setParameter("minLongitude", minLongitude)
                .setParameter("maxLongitude", maxLongitude)
                .setParameter("pattern", pattern)
                .getResultList();
    }
}

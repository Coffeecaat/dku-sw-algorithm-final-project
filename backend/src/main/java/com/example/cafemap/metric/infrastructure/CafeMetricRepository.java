package com.example.cafemap.metric.infrastructure;

import com.example.cafemap.metric.domain.CafeMetric;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CafeMetricRepository extends JpaRepository<CafeMetric, Long> {

    List<CafeMetric> findByCafeIdIn(Collection<Long> cafeIds);
}

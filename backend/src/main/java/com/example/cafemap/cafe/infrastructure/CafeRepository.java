package com.example.cafemap.cafe.infrastructure;

import com.example.cafemap.cafe.domain.Cafe;
import com.example.cafemap.cafe.domain.CafeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CafeRepository extends JpaRepository<Cafe, Long> {

    List<Cafe> findByStatusAndLatitudeBetweenAndLongitudeBetween(
            CafeStatus status,
            double minLatitude,
            double maxLatitude,
            double minLongitude,
            double maxLongitude);
}

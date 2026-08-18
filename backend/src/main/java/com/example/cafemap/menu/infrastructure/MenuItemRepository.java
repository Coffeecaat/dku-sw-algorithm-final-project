package com.example.cafemap.menu.infrastructure;

import com.example.cafemap.menu.domain.MenuItem;
import com.example.cafemap.menu.domain.MenuStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    List<MenuItem> findByCafeId(Long cafeId);

    List<MenuItem> findByCafeIdInAndRepresentativeTrueAndStatus(Collection<Long> cafeIds, MenuStatus status);

    Optional<MenuItem> findByCafeIdAndRepresentativeTrueAndStatus(Long cafeId, MenuStatus status);
}

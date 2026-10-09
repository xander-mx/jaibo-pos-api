package com.eljaibo.pos.catalog.persistence;

import com.eljaibo.pos.catalog.domain.Unit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UnitRepository extends JpaRepository<Unit, UUID> {

    Optional<Unit> findByCodeIgnoreCase(String code);

    List<Unit> findAllByActiveTrueOrderByNameAsc();
}
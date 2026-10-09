package com.eljaibo.pos.catalog.application;

import com.eljaibo.pos.business.domain.Business;
import com.eljaibo.pos.business.persistence.BusinessRepository;
import com.eljaibo.pos.catalog.api.CategoryResponse;
import com.eljaibo.pos.catalog.api.CreateCategoryRequest;
import com.eljaibo.pos.catalog.api.UnitResponse;
import com.eljaibo.pos.catalog.domain.Category;
import com.eljaibo.pos.catalog.domain.Unit;
import com.eljaibo.pos.catalog.persistence.CategoryRepository;
import com.eljaibo.pos.catalog.persistence.UnitRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class CatalogService {

    private final BusinessRepository businessRepository;
    private final CategoryRepository categoryRepository;
    private final UnitRepository unitRepository;

    public CatalogService(
            BusinessRepository businessRepository,
            CategoryRepository categoryRepository,
            UnitRepository unitRepository
    ) {
        this.businessRepository = businessRepository;
        this.categoryRepository = categoryRepository;
        this.unitRepository = unitRepository;
    }

    @Transactional(readOnly = true)
    public List<UnitResponse> findActiveUnits() {
        return unitRepository
                .findAllByActiveTrueOrderByNameAsc()
                .stream()
                .map(this::toUnitResponse)
                .toList();
    }

    @Transactional
    public CategoryResponse createCategory(
            UUID businessId,
            CreateCategoryRequest request
    ) {
        Business business = findActiveBusiness(businessId);

        if (categoryRepository.existsByBusinessIdAndNameIgnoreCase(
                businessId,
                request.name()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A category with this name already exists"
            );
        }

        int displayOrder = request.displayOrder() == null
                ? 0
                : request.displayOrder();

        Category category = categoryRepository.save(
                new Category(
                        business,
                        request.name(),
                        displayOrder
                )
        );

        return toCategoryResponse(category);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> findActiveCategories(
            UUID businessId
    ) {
        return categoryRepository
                .findAllByBusinessIdAndActiveTrueOrderByDisplayOrderAscNameAsc(
                        businessId
                )
                .stream()
                .map(this::toCategoryResponse)
                .toList();
    }

    private Business findActiveBusiness(UUID businessId) {
        return businessRepository.findById(businessId)
                .filter(Business::isActive)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Active business not found"
                ));
    }

    private UnitResponse toUnitResponse(Unit unit) {
        return new UnitResponse(
                unit.getId(),
                unit.getCode(),
                unit.getName(),
                unit.getSymbol(),
                unit.getDecimalPlaces()
        );
    }

    private CategoryResponse toCategoryResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDisplayOrder(),
                category.isActive()
        );
    }
}
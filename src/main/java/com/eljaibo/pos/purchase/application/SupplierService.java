package com.eljaibo.pos.purchase.application;

import com.eljaibo.pos.business.domain.Business;
import com.eljaibo.pos.business.persistence.BusinessRepository;
import com.eljaibo.pos.purchase.api.request.CreateSupplierRequest;
import com.eljaibo.pos.purchase.api.response.SupplierResponse;
import com.eljaibo.pos.purchase.domain.Supplier;
import com.eljaibo.pos.purchase.persistence.SupplierRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class SupplierService {

    private static final Logger log =
            LoggerFactory.getLogger(SupplierService.class);

    private final SupplierRepository supplierRepository;
    private final BusinessRepository businessRepository;

    public SupplierService(
            SupplierRepository supplierRepository,
            BusinessRepository businessRepository
    ) {
        this.supplierRepository = supplierRepository;
        this.businessRepository = businessRepository;
    }

    @Transactional
    public SupplierResponse create(
            UUID businessId,
            CreateSupplierRequest request
    ) {

        Business business =
                businessRepository.findById(businessId)
                        .filter(Business::isActive)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Active business not found"
                                )
                        );

        String name = request.name()
                .trim()
                .replaceAll("\\s+", " ");

        if (supplierRepository
                .existsByBusinessIdAndNameIgnoreCase(
                        businessId,
                        name
                )) {

            log.warn(
                    "Duplicate supplier. businessId={}, name={}",
                    businessId,
                    name
            );

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El proveedor ya se encuentra registrado"
            );
        }

        Supplier supplier =
                supplierRepository.save(
                        new Supplier(
                                business,
                                name,
                                request.phone()
                        )
                );

        log.info(
                "Supplier created. supplierId={}, businessId={}",
                supplier.getId(),
                businessId
        );

        return SupplierResponse.from(supplier);
    }

    @Transactional(readOnly = true)
    public List<SupplierResponse> findActive(
            UUID businessId
    ) {

        return supplierRepository
                .findAllByBusinessIdAndActiveTrueOrderByNameAsc(
                        businessId
                )
                .stream()
                .map(SupplierResponse::from)
                .toList();
    }
}
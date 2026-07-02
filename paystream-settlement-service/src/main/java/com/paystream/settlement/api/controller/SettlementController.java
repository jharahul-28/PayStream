package com.paystream.settlement.api.controller;

import com.paystream.common.api.ApiResponse;
import com.paystream.common.util.IdGenerator;
import com.paystream.settlement.application.service.SettlementScheduler;
import com.paystream.settlement.infrastructure.persistence.entity.SettlementBatchEntity;
import com.paystream.settlement.infrastructure.persistence.repository.SettlementBatchJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/settlements")
public class SettlementController {

    private final SettlementBatchJpaRepository batchRepo;
    private final SettlementScheduler          scheduler;

    public SettlementController(SettlementBatchJpaRepository batchRepo,
                                SettlementScheduler scheduler) {
        this.batchRepo = batchRepo;
        this.scheduler = scheduler;
    }

    @GetMapping("/{batchId}")
    public ResponseEntity<ApiResponse<SettlementBatchEntity>> getBatch(
            @PathVariable String batchId) {
        return batchRepo.findById(batchId)
                .map(b -> ResponseEntity.ok(ApiResponse.success(b)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<SettlementBatchEntity>>> listBatches(
            @RequestParam(required = false) String merchantId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<SettlementBatchEntity> page = merchantId != null
                ? batchRepo.findByMerchantId(merchantId, pageable)
                : batchRepo.findAll(pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    /** Manual trigger for FINANCE_OPS to force settlement processing. */
    @PostMapping("/trigger/{batchId}")
    @PreAuthorize("hasRole('FINANCE_OPS') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> triggerBatch(@PathVariable String batchId) {
        scheduler.triggerBatch(batchId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}

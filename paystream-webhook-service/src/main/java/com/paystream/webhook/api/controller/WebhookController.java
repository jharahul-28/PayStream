package com.paystream.webhook.api.controller;

import com.paystream.common.api.ApiResponse;
import com.paystream.common.util.IdGenerator;
import com.paystream.webhook.infrastructure.persistence.entity.WebhookDeliveryEntity;
import com.paystream.webhook.infrastructure.persistence.entity.WebhookEndpointEntity;
import com.paystream.webhook.infrastructure.persistence.repository.WebhookDeliveryJpaRepository;
import com.paystream.webhook.infrastructure.persistence.repository.WebhookEndpointJpaRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    private final WebhookEndpointJpaRepository endpointRepo;
    private final WebhookDeliveryJpaRepository deliveryRepo;

    public WebhookController(WebhookEndpointJpaRepository endpointRepo,
                             WebhookDeliveryJpaRepository deliveryRepo) {
        this.endpointRepo = endpointRepo;
        this.deliveryRepo = deliveryRepo;
    }

    public record CreateEndpointRequest(
            @NotBlank String url,
            @NotEmpty String[] events
    ) {}

    @PostMapping("/endpoints")
    public ResponseEntity<ApiResponse<WebhookEndpointEntity>> createEndpoint(
            @RequestHeader("X-User-Id") String merchantId,
            @Valid @RequestBody CreateEndpointRequest request) {

        String secret = UUID.randomUUID().toString().replace("-", "");
        WebhookEndpointEntity endpoint = new WebhookEndpointEntity(
                IdGenerator.generate(), merchantId, request.url(), secret, request.events());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(endpointRepo.save(endpoint)));
    }

    @GetMapping("/endpoints")
    public ResponseEntity<ApiResponse<java.util.List<WebhookEndpointEntity>>> listEndpoints(
            @RequestHeader("X-User-Id") String merchantId) {
        return ResponseEntity.ok(ApiResponse.success(
                endpointRepo.findByMerchantIdAndActive(merchantId, true)));
    }

    @DeleteMapping("/endpoints/{endpointId}")
    public ResponseEntity<ApiResponse<Void>> deleteEndpoint(
            @PathVariable String endpointId,
            @RequestHeader("X-User-Id") String merchantId) {
        endpointRepo.findById(endpointId).ifPresent(e -> {
            if (e.getMerchantId().equals(merchantId)) {
                e.deactivate();
                endpointRepo.save(e);
            }
        });
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/deliveries")
    public ResponseEntity<ApiResponse<Page<WebhookDeliveryEntity>>> listDeliveries(
            @RequestParam String endpointId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                deliveryRepo.findByEndpointId(endpointId, pageable)));
    }
}

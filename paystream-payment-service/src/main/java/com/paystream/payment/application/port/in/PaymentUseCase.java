package com.paystream.payment.application.port.in;

import com.paystream.payment.api.dto.request.InitiatePaymentRequest;
import com.paystream.payment.api.dto.request.RefundRequest;
import com.paystream.payment.api.dto.response.PaymentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Input port — all payment use-cases. */
public interface PaymentUseCase {

    PaymentResponse initiatePayment(String userId, String idempotencyKey, InitiatePaymentRequest request);

    PaymentResponse getPayment(String paymentId, String userId);

    Page<PaymentResponse> listPayments(String userId, Pageable pageable);

    PaymentResponse refund(String paymentId, String userId, RefundRequest request);

    Page<PaymentResponse> listAllPayments(Pageable pageable);
}

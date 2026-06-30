package com.paystream.wallet.application.service;

import com.paystream.common.constant.ErrorCode;
import com.paystream.common.event.wallet.WalletCreditedEvent;
import com.paystream.common.event.wallet.WalletDebitedEvent;
import com.paystream.common.exception.DomainException;
import com.paystream.common.exception.ResourceNotFoundException;
import com.paystream.common.util.IdGenerator;
import com.paystream.wallet.api.dto.request.CreateWalletRequest;
import com.paystream.wallet.api.dto.request.DebitCreditRequest;
import com.paystream.wallet.api.dto.response.WalletResponse;
import com.paystream.wallet.application.port.in.WalletUseCase;
import com.paystream.wallet.application.port.out.WalletRepository;
import com.paystream.wallet.domain.exception.WalletAlreadyExistsException;
import com.paystream.wallet.domain.model.Wallet;
import com.paystream.wallet.domain.model.WalletStatus;
import com.paystream.wallet.infrastructure.messaging.producer.WalletEventProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;

/**
 * Orchestrates all wallet use-cases.
 *
 * Optimistic locking retry: on {@link OptimisticLockingFailureException} we retry up to 3 times
 * with exponential backoff (50ms, 100ms) before propagating {@code CONCURRENT_MODIFICATION}.
 */
@Service
public class WalletApplicationService implements WalletUseCase {

    private static final Logger log = LoggerFactory.getLogger(WalletApplicationService.class);

    private static final int  MAX_RETRY_ATTEMPTS  = 3;
    private static final long RETRY_BASE_DELAY_MS = 50L;

    private final WalletRepository     walletRepository;
    private final WalletEventProducer  eventProducer;

    public WalletApplicationService(WalletRepository walletRepository,
                                    WalletEventProducer eventProducer) {
        this.walletRepository = walletRepository;
        this.eventProducer    = eventProducer;
    }

    // -------------------------------------------------------------------------
    // Create
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public WalletResponse createWallet(String userId, CreateWalletRequest request) {
        String currency = request.currency();
        if (walletRepository.existsByUserIdAndCurrency(userId, currency)) {
            log.warn("Duplicate wallet creation userId={} currency={} correlationId={}",
                    userId, currency, MDC.get("correlationId"));
            throw new WalletAlreadyExistsException(userId, currency);
        }

        Wallet wallet = new Wallet(
                IdGenerator.generate(), userId, 0L, currency,
                WalletStatus.ACTIVE, 0, Instant.now(), Instant.now()
        );

        Wallet saved = walletRepository.save(wallet);
        log.info("Wallet created walletId={} userId={} currency={} correlationId={}",
                saved.getId(), userId, currency, MDC.get("correlationId"));
        return toResponse(saved);
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public WalletResponse getMyWallet(String userId, String currency) {
        Wallet wallet = walletRepository.findByUserIdAndCurrency(userId, currency)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", "userId=" + userId + ",currency=" + currency));
        return toResponse(wallet);
    }

    @Override
    @Transactional(readOnly = true)
    public WalletResponse getWalletById(String walletId) {
        return toResponse(requireWallet(walletId));
    }

    // -------------------------------------------------------------------------
    // Debit — with optimistic lock retry
    // -------------------------------------------------------------------------

    @Override
    public WalletResponse debit(String walletId, DebitCreditRequest request) {
        int attempt = 0;
        while (true) {
            try {
                return debitTransactional(walletId, request);
            } catch (OptimisticLockingFailureException e) {
                attempt++;
                if (attempt >= MAX_RETRY_ATTEMPTS) {
                    log.error("Optimistic lock exhausted after {} retries walletId={} correlationId={}",
                            MAX_RETRY_ATTEMPTS, walletId, MDC.get("correlationId"));
                    throw new DomainException(ErrorCode.CONCURRENT_MODIFICATION,
                            "Wallet concurrently modified — please retry");
                }
                log.warn("Optimistic lock conflict attempt={} walletId={} correlationId={}",
                        attempt, walletId, MDC.get("correlationId"));
                try {
                    Thread.sleep(RETRY_BASE_DELAY_MS * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new DomainException(ErrorCode.INTERNAL_ERROR, "Interrupted during retry");
                }
            }
        }
    }

    @Transactional
    protected WalletResponse debitTransactional(String walletId, DebitCreditRequest request) {
        Wallet wallet = requireWallet(walletId);
        wallet.debit(request.amount());
        Wallet saved = walletRepository.save(wallet);
        log.info("Wallet debited walletId={} amount={} currency={} newBalance={} correlationId={}",
                walletId, request.amount(), request.currency(), saved.getBalance(), MDC.get("correlationId"));

        // Publish event AFTER transaction commits — never inside the transactional boundary
        // referenceId extracted from reason field (payment-service sets it to "Payment {paymentId}")
        WalletDebitedEvent event = new WalletDebitedEvent(
                saved.getId(), saved.getUserId(), request.amount(), request.currency(),
                saved.getBalance(), null, "PAYMENT", MDC.get("correlationId"));
        registerAfterCommit(() -> eventProducer.publishDebited(event));

        return toResponse(saved);
    }

    // -------------------------------------------------------------------------
    // Credit — with optimistic lock retry
    // -------------------------------------------------------------------------

    @Override
    public WalletResponse credit(String walletId, DebitCreditRequest request) {
        int attempt = 0;
        while (true) {
            try {
                return creditTransactional(walletId, request);
            } catch (OptimisticLockingFailureException e) {
                attempt++;
                if (attempt >= MAX_RETRY_ATTEMPTS) {
                    log.error("Optimistic lock exhausted after {} retries walletId={} correlationId={}",
                            MAX_RETRY_ATTEMPTS, walletId, MDC.get("correlationId"));
                    throw new DomainException(ErrorCode.CONCURRENT_MODIFICATION,
                            "Wallet concurrently modified — please retry");
                }
                log.warn("Optimistic lock conflict attempt={} walletId={} correlationId={}",
                        attempt, walletId, MDC.get("correlationId"));
                try {
                    Thread.sleep(RETRY_BASE_DELAY_MS * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new DomainException(ErrorCode.INTERNAL_ERROR, "Interrupted during retry");
                }
            }
        }
    }

    @Transactional
    protected WalletResponse creditTransactional(String walletId, DebitCreditRequest request) {
        Wallet wallet = requireWallet(walletId);
        wallet.credit(request.amount());
        Wallet saved = walletRepository.save(wallet);
        log.info("Wallet credited walletId={} amount={} currency={} newBalance={} correlationId={}",
                walletId, request.amount(), request.currency(), saved.getBalance(), MDC.get("correlationId"));

        // Publish event AFTER transaction commits
        WalletCreditedEvent event = new WalletCreditedEvent(
                saved.getId(), saved.getUserId(), request.amount(), request.currency(),
                saved.getBalance(), null, "PAYMENT", MDC.get("correlationId"));
        registerAfterCommit(() -> eventProducer.publishCredited(event));

        return toResponse(saved);
    }

    // -------------------------------------------------------------------------
    // Status transitions
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public WalletResponse freezeWallet(String walletId) {
        Wallet wallet = requireWallet(walletId);
        wallet.freeze();
        Wallet saved = walletRepository.save(wallet);
        log.info("Wallet frozen walletId={} correlationId={}", walletId, MDC.get("correlationId"));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public WalletResponse unfreezeWallet(String walletId) {
        Wallet wallet = requireWallet(walletId);
        wallet.unfreeze();
        Wallet saved = walletRepository.save(wallet);
        log.info("Wallet unfrozen walletId={} correlationId={}", walletId, MDC.get("correlationId"));
        return toResponse(saved);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void registerAfterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private Wallet requireWallet(String walletId) {
        return walletRepository.findById(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", walletId));
    }

    private WalletResponse toResponse(Wallet w) {
        return new WalletResponse(
                w.getId(), w.getUserId(), w.getBalance(), w.getCurrency(),
                w.getStatus(), w.getVersion(), w.getCreatedAt(), w.getUpdatedAt()
        );
    }
}

package com.carddemo.service;

import com.carddemo.common.CobolText;
import com.carddemo.domain.IdSequence;
import com.carddemo.repository.IdSequenceRepository;
import com.carddemo.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Allocates transaction identifiers atomically.
 *
 * <p>FR-TRAN-009: {@code COTRN02C} and {@code COBIL00C} browsed to the highest transaction id and
 * added one, which collides when two tasks add at the same time. A locked counter row gives the
 * same sixteen character zero padded presentation without the race.</p>
 */
@Service
public class SequenceService {

    private final IdSequenceRepository sequences;
    private final TransactionRepository transactions;

    public SequenceService(IdSequenceRepository sequences, TransactionRepository transactions) {
        this.sequences = sequences;
        this.transactions = transactions;
    }

    /**
     * Returns the next transaction id, formatted as sixteen zero padded digits.
     *
     * <p>The counter is seeded above the highest identifier already in the transaction master, and
     * any candidate that nevertheless collides with an existing row is skipped, so a migrated or
     * batch-generated identifier can never be reused.</p>
     */
    @Transactional
    public String nextTransactionId() {
        IdSequence sequence = sequences.findForUpdate(IdSequence.TRANSACTION_ID)
                .orElseGet(this::createFromExistingData);

        long value = Math.max(sequence.getNextValue(), highestExistingId() + 1);
        String candidate = CobolText.padLeftZero(Long.toString(value), 16);
        while (transactions.existsById(candidate)) {
            value++;
            candidate = CobolText.padLeftZero(Long.toString(value), 16);
        }

        sequence.setNextValue(value + 1);
        sequences.save(sequence);
        return candidate;
    }

    /**
     * First-use seeding: continue above the highest identifier already in the transaction master so
     * migrated legacy identifiers are never reused.
     */
    private IdSequence createFromExistingData() {
        IdSequence created = new IdSequence(IdSequence.TRANSACTION_ID, highestExistingId() + 1);
        return sequences.save(created);
    }

    /** The greatest numeric transaction id currently stored, or zero when the master is empty. */
    private long highestExistingId() {
        return transactions.findFirstByOrderByTransactionIdDesc()
                .map(t -> parse(t.getTransactionId()))
                .orElse(0L);
    }

    private static long parse(String transactionId) {
        String digits = CobolText.trim(transactionId);
        if (!CobolText.isAllDigits(digits)) {
            return 0L;
        }
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}

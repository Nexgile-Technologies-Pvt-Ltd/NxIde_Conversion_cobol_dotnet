package com.carddemo.batch;

import com.carddemo.domain.BatchRun;
import com.carddemo.dto.OperationsDtos.BatchRunDto;
import com.carddemo.repository.BatchRunRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Batch run bookkeeping (FR-BATCH-017): allocate a durable run id, record counts and the COBOL
 * completion code, and expose the history the operator used to read from JES output.
 */
@Service
public class BatchRunService {

    private final BatchRunRepository runs;

    public BatchRunService(BatchRunRepository runs) {
        this.runs = runs;
    }

    /** Starts a run in its own transaction so the record survives a rolled back job body. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BatchRun start(String jobName, String parameters, String startedBy) {
        BatchRun run = new BatchRun();
        run.setJobName(jobName);
        run.setParameters(parameters);
        run.setStartedBy(startedBy);
        run.setStatus(BatchRun.STATUS_RUNNING);
        return runs.save(run);
    }

    /** Completes a run with the counts and the COBOL return code. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BatchRun finish(Long runId, int returnCode, int read, int accepted, int rejected, String message) {
        BatchRun run = runs.findById(runId).orElseThrow();
        run.setStatus(returnCode >= 8 ? BatchRun.STATUS_FAILED : BatchRun.STATUS_COMPLETED);
        run.setReturnCode(returnCode);
        run.setRecordsRead(read);
        run.setRecordsAccepted(accepted);
        run.setRecordsRejected(rejected);
        run.setMessage(message);
        run.setFinishedAt(LocalDateTime.now());
        return runs.save(run);
    }

    /** Marks a run failed after an unexpected error. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long runId, String message) {
        runs.findById(runId).ifPresent(run -> {
            run.setStatus(BatchRun.STATUS_FAILED);
            run.setReturnCode(12);
            run.setMessage(message);
            run.setFinishedAt(LocalDateTime.now());
            runs.save(run);
        });
    }

    @Transactional(readOnly = true)
    public List<BatchRunDto> history(int limit) {
        return runs.findAllByOrderByStartedAtDesc(PageRequest.of(0, limit)).stream()
                .map(BatchRunService::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public BatchRunDto get(Long id) {
        return runs.findById(id).map(BatchRunService::toDto).orElseThrow();
    }

    public static BatchRunDto toDto(BatchRun run) {
        return new BatchRunDto(run.getId(), run.getJobName(), run.getParameters(), run.getStatus(),
                run.getReturnCode(), run.getRecordsRead(), run.getRecordsAccepted(), run.getRecordsRejected(),
                run.getMessage(), run.getStartedBy(), run.getStartedAt(), run.getFinishedAt());
    }
}

package ru.smetrix.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.smetrix.entity.FgisImportState;
import ru.smetrix.fgis.FgisFileImportResult;
import ru.smetrix.repository.FgisImportStateRepository;

import java.util.List;

@Service
public class FgisImportStateService {

    public static final String RUNNING = "RUNNING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";

    private final FgisImportStateRepository repository;

    public FgisImportStateService(FgisImportStateRepository repository) {
        this.repository = repository;
    }

    public boolean isFresh(String regionCode, long freshAfter) {
        return repository.existsByRegionCodeAndStatusAndLastSuccessAtGreaterThan(
                regionCode, SUCCESS, freshAfter
        );
    }

    public List<FgisImportState> findAll() {
        return repository.findAll();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRunning(String regionCode) {
        FgisImportState state = getOrCreate(regionCode);
        state.setStatus(RUNNING);
        state.setLastAttemptAt(System.currentTimeMillis());
        state.setLastError(null);
        repository.save(state);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSuccess(String regionCode, FgisFileImportResult result) {
        FgisImportState state = getOrCreate(regionCode);
        long now = System.currentTimeMillis();
        state.setStatus(SUCCESS);
        state.setLastAttemptAt(now);
        state.setLastSuccessAt(now);
        state.setLastError(null);
        state.setCreatedCount(result.database().created());
        state.setUpdatedCount(result.database().updated());
        state.setUnchangedCount(result.database().unchanged());
        state.setRejectedCount(result.database().rejected());
        repository.save(state);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String regionCode, Exception error) {
        FgisImportState state = getOrCreate(regionCode);
        state.setStatus(FAILED);
        state.setLastAttemptAt(System.currentTimeMillis());
        state.setLastError(limit(error.getMessage()));
        repository.save(state);
    }

    private FgisImportState getOrCreate(String regionCode) {
        return repository.findById(regionCode).orElseGet(() -> {
            FgisImportState state = new FgisImportState();
            state.setRegionCode(regionCode);
            state.setStatus(RUNNING);
            state.setLastAttemptAt(System.currentTimeMillis());
            return state;
        });
    }

    private String limit(String message) {
        if (message == null || message.isBlank()) {
            return "Unknown import error";
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}

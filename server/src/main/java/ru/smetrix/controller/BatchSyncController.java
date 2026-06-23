package ru.smetrix.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import ru.smetrix.dto.SyncBatchRequest;
import ru.smetrix.dto.SyncBatchResponse;
import ru.smetrix.dto.SyncItemResult;
import ru.smetrix.entity.EstimateItem;
import ru.smetrix.entity.Opening;
import ru.smetrix.entity.Project;
import ru.smetrix.entity.ProjectRoom;
import ru.smetrix.entity.User;
import ru.smetrix.entity.Worker;
import ru.smetrix.entity.WorkTask;
import ru.smetrix.repository.EstimateItemRepository;
import ru.smetrix.repository.OpeningRepository;
import ru.smetrix.repository.ProjectRepository;
import ru.smetrix.repository.ProjectRoomRepository;
import ru.smetrix.repository.UserRepository;
import ru.smetrix.repository.WorkerRepository;
import ru.smetrix.repository.WorkTaskRepository;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
public class BatchSyncController {

    private static final Logger LOG = LoggerFactory.getLogger(BatchSyncController.class);

    private final EstimateItemRepository estimateItemRepository;
    private final ProjectRoomRepository projectRoomRepository;
    private final ProjectRepository projectRepository;
    private final OpeningRepository openingRepository;
    private final WorkerRepository workerRepository;
    private final WorkTaskRepository workTaskRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public BatchSyncController(EstimateItemRepository estimateItemRepository,
                               ProjectRoomRepository projectRoomRepository,
                               ProjectRepository projectRepository,
                               OpeningRepository openingRepository,
                               WorkerRepository workerRepository,
                               WorkTaskRepository workTaskRepository,
                               UserRepository userRepository,
                               ObjectMapper objectMapper) {
        this.estimateItemRepository = estimateItemRepository;
        this.projectRoomRepository = projectRoomRepository;
        this.projectRepository = projectRepository;
        this.openingRepository = openingRepository;
        this.workerRepository = workerRepository;
        this.workTaskRepository = workTaskRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/api/v1/estimate-items/sync")
    public SyncBatchResponse syncEstimateItems(@RequestBody SyncBatchRequest request, Principal principal) {
        User user = currentUser(principal);
        List<Map<String, Object>> items = validatedItems(request);

        Set<String> roomIds = items.stream()
                .map(item -> getString(item, "project_room_id"))
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());

        Set<String> ownedRoomIds = roomIds.isEmpty()
                ? new HashSet<>()
                : projectRoomRepository.findIdsByIdInAndUserId(roomIds, user.getId().toString());

        List<SyncItemResult> results = new ArrayList<>();
        for (Map<String, Object> item : items) {
            results.add(syncEstimateItem(item, user, ownedRoomIds));
        }

        return new SyncBatchResponse(results, results.size(), nowIso());
    }

    @PostMapping("/api/v1/project-rooms/sync")
    public SyncBatchResponse syncProjectRooms(@RequestBody SyncBatchRequest request, Principal principal) {
        User user = currentUser(principal);
        List<Map<String, Object>> items = validatedItems(request);

        Set<String> projectIds = items.stream()
                .map(item -> getString(item, "project_id"))
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());

        Set<String> ownedProjectIds = projectIds.isEmpty()
                ? new HashSet<>()
                : projectRepository.findIdsByIdInAndUserId(projectIds, user.getId().toString());

        List<SyncItemResult> results = new ArrayList<>();
        for (Map<String, Object> item : items) {
            results.add(syncProjectRoom(item, user, ownedProjectIds));
        }

        return new SyncBatchResponse(results, results.size(), nowIso());
    }

    @PostMapping("/api/v1/work-tasks/sync")
    public SyncBatchResponse syncWorkTasks(@RequestBody SyncBatchRequest request, Principal principal) {
        User user = currentUser(principal);
        List<Map<String, Object>> items = validatedItems(request);

        Set<String> roomIds = items.stream()
                .map(item -> getString(item, "project_room_id"))
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());

        Set<String> ownedRoomIds = roomIds.isEmpty()
                ? new HashSet<>()
                : projectRoomRepository.findIdsByIdInAndUserId(roomIds, user.getId().toString());

        List<SyncItemResult> results = new ArrayList<>();
        for (Map<String, Object> item : items) {
            results.add(syncWorkTask(item, user, ownedRoomIds));
        }

        return new SyncBatchResponse(results, results.size(), nowIso());
    }

    @PostMapping("/api/v1/openings/sync")
    public SyncBatchResponse syncOpenings(@RequestBody SyncBatchRequest request, Principal principal) {
        User user = currentUser(principal);
        List<Map<String, Object>> items = validatedItems(request);

        Set<String> roomIds = items.stream()
                .map(item -> getString(item, "project_room_id"))
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());

        Set<String> ownedRoomIds = roomIds.isEmpty()
                ? new HashSet<>()
                : projectRoomRepository.findIdsByIdInAndUserId(roomIds, user.getId().toString());

        List<SyncItemResult> results = new ArrayList<>();
        for (Map<String, Object> item : items) {
            results.add(syncOpening(item, user, ownedRoomIds));
        }

        return new SyncBatchResponse(results, results.size(), nowIso());
    }

    @PostMapping("/api/v1/workers/sync")
    public SyncBatchResponse syncWorkers(@RequestBody SyncBatchRequest request, Principal principal) {
        User user = currentUser(principal);
        List<Map<String, Object>> items = validatedItems(request);

        List<SyncItemResult> results = new ArrayList<>();
        for (Map<String, Object> item : items) {
            results.add(syncWorker(item, user));
        }

        return new SyncBatchResponse(results, results.size(), nowIso());
    }

    private SyncItemResult syncEstimateItem(Map<String, Object> dto, User user, Set<String> ownedRoomIds) {
        String id = getString(dto, "id");
        String operation = getString(dto, "operation");
        long clientVersion = getLong(dto, "version");
        long now = System.currentTimeMillis();

        if (id == null || id.isBlank() || !isValidOperation(operation)) {
            return invalidItem(id, clientVersion);
        }

        UUID uuid = toUuid(id);
        if (uuid == null) return invalidItem(id, clientVersion);

        try {
            EstimateItem entity = estimateItemRepository.findById(uuid).orElse(null);

            if (entity != null && !ownedRoomIds.contains(entity.getProjectRoomId().toString())) {
                return accessDenied(id, clientVersion);
            }

            String roomId = getString(dto, "project_room_id");
            if (roomId == null || roomId.isBlank() || !ownedRoomIds.contains(roomId)) {
                return accessDenied(id, clientVersion);
            }

            UUID roomUuid = toUuid(roomId);
            if (roomUuid == null) return accessDenied(id, clientVersion);

            if (entity != null && "CREATE".equalsIgnoreCase(operation) && clientVersion == 0L) {
                return ok(id, 200, safeVersion(entity.getVersion()));
            }

            if (entity != null && clientVersion != safeVersion(entity.getVersion())) {
                return conflict(id, entity);
            }

            if ("DELETE".equalsIgnoreCase(operation)) {
                if (entity != null) {
                    entity.setDeletedAt(now);
                    entity.setUpdatedAt(now);
                    entity.setVersion(safeVersion(entity.getVersion()) + 1);
                    estimateItemRepository.save(entity);
                }

                return ok(id, 200, entity != null ? safeVersion(entity.getVersion()) : clientVersion + 1);
            }

            boolean created = entity == null;
            if (entity == null) {
                entity = new EstimateItem();
                entity.setId(uuid);
                entity.setCreatedAt(now);
            }

            entity.setProjectRoomId(roomUuid);
            entity.setFgisCode(getString(dto, "fgis_code"));
            entity.setName(getString(dto, "name"));
            entity.setUnitMeasure(getString(dto, "unit_measure"));
            entity.setUnit(getString(dto, "unit_measure"));

            BigDecimal basePrice = requireNonNegative(getDecimal(dto, "base_price"), "base_price");

            BigDecimal consumptionRate = getDecimal(dto, "consumption_rate");
            if (consumptionRate != null) {
                consumptionRate = requireNonNegative(consumptionRate, "consumption_rate");
            }

            BigDecimal quantity = requireNonNegative(getDecimal(dto, "quantity"), "quantity");
            BigDecimal finalPrice = calculateFinalPrice(roomUuid, basePrice);
            BigDecimal totalPrice = finalPrice.multiply(quantity)
                    .setScale(2, java.math.RoundingMode.HALF_UP);

            entity.setBasePrice(basePrice);
            entity.setPrice(basePrice);
            entity.setFinalPrice(finalPrice);
            entity.setConsumptionRate(consumptionRate);
            entity.setQuantity(quantity);
            entity.setTotalPrice(totalPrice);
            entity.setTotal(totalPrice);
            entity.setStatus(getString(dto, "status"));
            entity.setType("MATERIAL");

            entity.setCalculationMethod(getString(dto, "calculation_method"));
            entity.setWastePercent(getDecimal(dto, "waste_percent"));
            entity.setLayers(getInteger(dto, "layers"));
            entity.setThicknessMeters(getDecimal(dto, "thickness_meters"));
            entity.setManualQuantity(getDecimal(dto, "manual_quantity"));
            entity.setCoveragePerPiece(getDecimal(dto, "coverage_per_piece"));
            entity.setCoveragePerPackage(getDecimal(dto, "coverage_per_package"));
            entity.setPackageSize(getDecimal(dto, "package_size"));
            entity.setFormulaDescription(getString(dto, "formula_description"));

            entity.setUpdatedAt(now);
            entity.setVersion(created ? 1L : safeVersion(entity.getVersion()) + 1);

            EstimateItem saved = estimateItemRepository.save(entity);
            return ok(id, created ? 201 : 200, safeVersion(saved.getVersion()));
        } catch (IllegalArgumentException e) {
            return new SyncItemResult(id, 400, clientVersion, nowIso(),
                    "INVALID_ITEM", e.getMessage());
        } catch (Exception e) {
            LOG.error("Estimate item sync failed for id={}", id, e);
            return internalSyncError(id, clientVersion);
        }
    }

    private SyncItemResult syncProjectRoom(Map<String, Object> dto, User user, Set<String> ownedProjectIds) {
        String id = getString(dto, "id");
        String operation = getString(dto, "operation");
        long clientVersion = getLong(dto, "version");
        long now = System.currentTimeMillis();

        if (id == null || id.isBlank() || !isValidOperation(operation)) {
            return invalidItem(id, clientVersion);
        }

        UUID uuid = toUuid(id);
        if (uuid == null) return invalidItem(id, clientVersion);

        try {
            ProjectRoom entity = projectRoomRepository.findById(uuid).orElse(null);

            if (entity != null && !ownedProjectIds.contains(entity.getProjectId().toString())) {
                return accessDenied(id, clientVersion);
            }

            String projectId = getString(dto, "project_id");
            if (projectId == null || projectId.isBlank() || !ownedProjectIds.contains(projectId)) {
                return accessDenied(id, clientVersion);
            }

            UUID projectUuid = toUuid(projectId);
            if (projectUuid == null) return accessDenied(id, clientVersion);

            if (entity != null && "CREATE".equalsIgnoreCase(operation) && clientVersion == 0L) {
                return ok(id, 200, safeVersion(entity.getVersion()));
            }

            if (entity != null && clientVersion != safeVersion(entity.getVersion())) {
                return conflict(id, entity);
            }

            if ("DELETE".equalsIgnoreCase(operation)) {
                if (entity != null) {
                    entity.setDeletedAt(now);
                    entity.setUpdatedAt(now);
                    entity.setVersion(safeVersion(entity.getVersion()) + 1);
                    projectRoomRepository.save(entity);
                }

                return ok(id, 200, entity != null ? safeVersion(entity.getVersion()) : clientVersion + 1);
            }

            boolean created = entity == null;
            if (entity == null) {
                entity = new ProjectRoom();
                entity.setId(uuid);
                entity.setCreatedAt(now);
            }

            entity.setProjectId(projectUuid);
            entity.setName(getString(dto, "name"));
            entity.setLength(requireNonNegative(getDecimal(dto, "length"), "length"));
            entity.setWidth(requireNonNegative(getDecimal(dto, "width"), "width"));
            entity.setHeight(requireNonNegative(getDecimal(dto, "height"), "height"));

            BigDecimal manualArea = getDecimal(dto, "manual_area_override");
            entity.setManualAreaOverride(manualArea == null ? null
                    : requireNonNegative(manualArea, "manual_area_override"));

            entity.setUpdatedAt(now);
            entity.setVersion(created ? 1L : safeVersion(entity.getVersion()) + 1);

            ProjectRoom saved = projectRoomRepository.save(entity);
            return ok(id, created ? 201 : 200, safeVersion(saved.getVersion()));
        } catch (IllegalArgumentException e) {
            return new SyncItemResult(id, 400, clientVersion, nowIso(),
                    "INVALID_ITEM", e.getMessage());
        } catch (Exception e) {
            LOG.error("Project room sync failed for id={}", id, e);
            return internalSyncError(id, clientVersion);
        }
    }

    private SyncItemResult syncWorkTask(Map<String, Object> dto, User user, Set<String> ownedRoomIds) {
        String id = getString(dto, "id");
        String operation = getString(dto, "operation");
        long clientVersion = getLong(dto, "version");
        long now = System.currentTimeMillis();

        if (id == null || id.isBlank() || !isValidOperation(operation)) {
            return invalidItem(id, clientVersion);
        }

        UUID uuid = toUuid(id);
        if (uuid == null) return invalidItem(id, clientVersion);

        try {
            WorkTask entity = workTaskRepository.findById(uuid).orElse(null);

            if (entity != null && !ownedRoomIds.contains(entity.getProjectRoomId().toString())) {
                return accessDenied(id, clientVersion);
            }

            String roomId = getString(dto, "project_room_id");
            if (roomId == null || roomId.isBlank() || !ownedRoomIds.contains(roomId)) {
                return accessDenied(id, clientVersion);
            }

            UUID roomUuid = toUuid(roomId);
            if (roomUuid == null) return accessDenied(id, clientVersion);

            if (entity != null && "CREATE".equalsIgnoreCase(operation) && clientVersion == 0L) {
                return ok(id, 200, safeVersion(entity.getVersion()));
            }

            if (entity != null && clientVersion != safeVersion(entity.getVersion())) {
                return conflict(id, entity);
            }

            if ("DELETE".equalsIgnoreCase(operation)) {
                if (entity != null) {
                    entity.setDeletedAt(now);
                    entity.setUpdatedAt(now);
                    entity.setVersion(safeVersion(entity.getVersion()) + 1);
                    workTaskRepository.save(entity);
                }

                return ok(id, 200, entity != null ? safeVersion(entity.getVersion()) : clientVersion + 1);
            }

            boolean created = entity == null;
            if (entity == null) {
                entity = new WorkTask();
                entity.setId(uuid);
                entity.setCreatedAt(now);
            }

            entity.setProjectRoomId(roomUuid);
            entity.setWorkerId(toUuid(getString(dto, "worker_id")));
            entity.setTaskName(getString(dto, "task_name"));
            entity.setRateType(getString(dto, "rate_type"));
            entity.setRateValue(requireNonNegative(getDecimal(dto, "rate_value"), "rate_value"));
            entity.setTotalPayment(requireNonNegative(getDecimal(dto, "total_payment"), "total_payment"));
            entity.setUpdatedAt(now);
            entity.setVersion(created ? 1L : safeVersion(entity.getVersion()) + 1);

            WorkTask saved = workTaskRepository.save(entity);
            return ok(id, created ? 201 : 200, safeVersion(saved.getVersion()));
        } catch (IllegalArgumentException e) {
            return new SyncItemResult(id, 400, clientVersion, nowIso(),
                    "INVALID_ITEM", e.getMessage());
        } catch (Exception e) {
            LOG.error("Work task sync failed for id={}", id, e);
            return internalSyncError(id, clientVersion);
        }
    }

    private SyncItemResult syncOpening(Map<String, Object> dto, User user, Set<String> ownedRoomIds) {
        String id = getString(dto, "id");
        String operation = getString(dto, "operation");
        long clientVersion = getLong(dto, "version");
        long now = System.currentTimeMillis();

        if (id == null || id.isBlank() || !isValidOperation(operation)) {
            return invalidItem(id, clientVersion);
        }

        UUID uuid = toUuid(id);
        if (uuid == null) return invalidItem(id, clientVersion);

        try {
            Opening entity = openingRepository.findById(uuid).orElse(null);

            if (entity != null && !ownedRoomIds.contains(entity.getProjectRoomId().toString())) {
                return accessDenied(id, clientVersion);
            }

            String roomId = getString(dto, "project_room_id");
            if (roomId == null || roomId.isBlank() || !ownedRoomIds.contains(roomId)) {
                return accessDenied(id, clientVersion);
            }

            UUID roomUuid = toUuid(roomId);
            if (roomUuid == null) return accessDenied(id, clientVersion);

            if (entity != null && "CREATE".equalsIgnoreCase(operation) && clientVersion == 0L) {
                return ok(id, 200, safeVersion(entity.getVersion()));
            }

            if (entity != null && clientVersion != safeVersion(entity.getVersion())) {
                return conflict(id, entity);
            }

            if ("DELETE".equalsIgnoreCase(operation)) {
                if (entity != null) {
                    entity.setDeletedAt(now);
                    entity.setUpdatedAt(now);
                    entity.setVersion(safeVersion(entity.getVersion()) + 1);
                    openingRepository.save(entity);
                }

                return ok(id, 200, entity != null ? safeVersion(entity.getVersion()) : clientVersion + 1);
            }

            boolean created = entity == null;
            if (entity == null) {
                entity = new Opening();
                entity.setId(uuid);
                entity.setCreatedAt(now);
            }

            entity.setProjectRoomId(roomUuid);
            entity.setType(getString(dto, "type"));
            entity.setWidth(requireNonNegative(getDecimal(dto, "width"), "width"));
            entity.setHeight(requireNonNegative(getDecimal(dto, "height"), "height"));

            BigDecimal depth = getDecimal(dto, "depth");
            entity.setDepth(depth == null ? null : requireNonNegative(depth, "depth"));

            entity.setPlacementType(getString(dto, "placement_type"));
            entity.setUpdatedAt(now);
            entity.setVersion(created ? 1L : safeVersion(entity.getVersion()) + 1);

            Opening saved = openingRepository.save(entity);
            return ok(id, created ? 201 : 200, safeVersion(saved.getVersion()));
        } catch (IllegalArgumentException e) {
            return new SyncItemResult(id, 400, clientVersion, nowIso(),
                    "INVALID_ITEM", e.getMessage());
        } catch (Exception e) {
            LOG.error("Opening sync failed for id={}", id, e);
            return internalSyncError(id, clientVersion);
        }
    }

    private SyncItemResult syncWorker(Map<String, Object> dto, User user) {
        String id = getString(dto, "id");
        String operation = getString(dto, "operation");
        long clientVersion = getLong(dto, "version");
        long now = System.currentTimeMillis();

        if (id == null || id.isBlank() || !isValidOperation(operation)) {
            return invalidItem(id, clientVersion);
        }

        UUID uuid = toUuid(id);
        if (uuid == null) return invalidItem(id, clientVersion);

        try {
            Worker entity = workerRepository.findById(uuid).orElse(null);

            if (entity != null && !user.getId().equals(entity.getUserId())) {
                return accessDenied(id, clientVersion);
            }

            if (entity != null && "CREATE".equalsIgnoreCase(operation) && clientVersion == 0L) {
                return ok(id, 200, safeVersion(entity.getVersion()));
            }

            if (entity != null && clientVersion != safeVersion(entity.getVersion())) {
                return conflict(id, entity);
            }

            if ("DELETE".equalsIgnoreCase(operation)) {
                if (entity != null) {
                    entity.setDeletedAt(now);
                    entity.setUpdatedAt(now);
                    entity.setVersion(safeVersion(entity.getVersion()) + 1);
                    workerRepository.save(entity);
                }

                return ok(id, 200, entity != null ? safeVersion(entity.getVersion()) : clientVersion + 1);
            }

            boolean created = entity == null;
            if (entity == null) {
                entity = new Worker();
                entity.setId(uuid);
                entity.setCreatedAt(now);
            }

            entity.setUserId(user.getId());
            entity.setFullName(getString(dto, "full_name"));
            entity.setPhone(getString(dto, "phone"));
            entity.setSpecialty(getString(dto, "specialty"));
            entity.setUpdatedAt(now);
            entity.setVersion(created ? 1L : safeVersion(entity.getVersion()) + 1);

            Worker saved = workerRepository.save(entity);
            return ok(id, created ? 201 : 200, safeVersion(saved.getVersion()));
        } catch (Exception e) {
            LOG.error("Worker sync failed for id={}", id, e);
            return internalSyncError(id, clientVersion);
        }
    }

    private SyncItemResult ok(String id, int status, long version) {
        return new SyncItemResult(id, status, version, nowIso(), null, null);
    }

    private SyncItemResult internalSyncError(String id, long clientVersion) {
        return new SyncItemResult(id, 500, clientVersion, nowIso(),
                "SERVER_ERROR", "Internal sync error");
    }

    private SyncItemResult accessDenied(String id, long clientVersion) {
        return new SyncItemResult(id, 403, clientVersion, nowIso(), "ACCESS_DENIED", null);
    }

    private SyncItemResult invalidItem(String id, long clientVersion) {
        return new SyncItemResult(id, 400, clientVersion, nowIso(), "INVALID_ITEM", null);
    }

    private List<Map<String, Object>> validatedItems(SyncBatchRequest request) {
        List<Map<String, Object>> items = request != null && request.items != null
                ? request.items : List.of();

        if (items.size() > 50) {
            throw new IllegalArgumentException("Размер batch не должен превышать 50 записей");
        }

        return items;
    }

    private boolean isValidOperation(String operation) {
        return "CREATE".equalsIgnoreCase(operation)
                || "UPDATE".equalsIgnoreCase(operation)
                || "DELETE".equalsIgnoreCase(operation);
    }

    private SyncItemResult conflict(String id, Object entity) throws JsonProcessingException {
        return new SyncItemResult(id, 409, safeVersion(readVersion(entity)), nowIso(),
                "VERSION_CONFLICT", objectMapper.writeValueAsString(entity));
    }

    private User currentUser(Principal principal) {
        return userRepository.findByEmail(principal.getName()).orElseThrow();
    }

    private Long readVersion(Object entity) {
        if (entity instanceof EstimateItem estimateItem) {
            return estimateItem.getVersion();
        }

        if (entity instanceof ProjectRoom projectRoom) {
            return projectRoom.getVersion();
        }

        if (entity instanceof Opening opening) {
            return opening.getVersion();
        }

        if (entity instanceof Worker worker) {
            return worker.getVersion();
        }

        if (entity instanceof WorkTask workTask) {
            return workTask.getVersion();
        }

        return 0L;
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);

        if (value instanceof Number number) {
            return number.longValue();
        }

        if (value == null || String.valueOf(value).isBlank()) {
            return 0L;
        }

        return Long.parseLong(String.valueOf(value));
    }

    private Integer getInteger(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private BigDecimal getDecimal(Map<String, Object> map, String key) {
        Object value = map.get(key);

        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }

        return new BigDecimal(String.valueOf(value));
    }

    private BigDecimal requireNonNegative(BigDecimal value, String field) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(field + " должен быть неотрицательным");
        }

        return value;
    }

    private BigDecimal calculateFinalPrice(UUID roomUuid, BigDecimal basePrice) {
        ProjectRoom room = projectRoomRepository.findById(roomUuid)
                .orElseThrow(() -> new IllegalArgumentException("Комната не найдена"));

        Project project = projectRepository.findById(room.getProjectId())
                .orElseThrow(() -> new IllegalArgumentException("Проект не найден"));

        BigDecimal tax = project.getTaxMultiplier() != null
                ? project.getTaxMultiplier()
                : BigDecimal.ONE;

        BigDecimal markup = project.getLogisticsMarkup() != null
                ? project.getLogisticsMarkup()
                : BigDecimal.ZERO;

        BigDecimal factor = BigDecimal.ONE.add(markup.divide(
                new BigDecimal("100"), 8, java.math.RoundingMode.HALF_UP));

        BigDecimal result = basePrice.multiply(tax).multiply(factor)
                .setScale(4, java.math.RoundingMode.HALF_UP);

        return requireNonNegative(result, "final_price");
    }

    private long safeVersion(Long version) {
        return version == null ? 0L : version;
    }

    private String nowIso() {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.now());
    }


    private UUID toUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
package ru.smetrix.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.smetrix.dto.*;
import ru.smetrix.entity.EstimateItem;
import ru.smetrix.entity.Project;
import ru.smetrix.entity.ProjectRoom;
import ru.smetrix.entity.User;
import ru.smetrix.entity.Opening;
import ru.smetrix.entity.Worker;
import ru.smetrix.entity.WorkTask;
import ru.smetrix.repository.EstimateItemRepository;
import ru.smetrix.repository.ProjectRepository;
import ru.smetrix.repository.ProjectRoomRepository;
import ru.smetrix.repository.UserRepository;
import ru.smetrix.repository.OpeningRepository;
import ru.smetrix.repository.WorkerRepository;
import ru.smetrix.repository.WorkTaskRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SyncService {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectRoomRepository projectRoomRepository;
    private final EstimateItemRepository estimateItemRepository;
    private final OpeningRepository openingRepository;
    private final WorkerRepository workerRepository;
    private final WorkTaskRepository workTaskRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public SyncPullResponse pull(String email, Long since) {
        User user = userRepository.findByEmail(email).orElseThrow();
        long serverTime = System.currentTimeMillis();

        List<Project> projects = projectRepository.findByUserIdAndUpdatedAtAfter(user.getId(), since);
        List<ProjectRoom> rooms = projectRoomRepository.findChangedForUser(user.getId(), since);
        List<EstimateItem> estimateItems = estimateItemRepository.findChangedForUser(user.getId(), since);
        List<Opening> openings = openingRepository.findChangedForUser(user.getId(), since);
        List<Worker> workers = workerRepository.findByUserIdAndUpdatedAtAfter(user.getId(), since);
        List<WorkTask> workTasks = workTaskRepository.findChangedForUser(user.getId(), since);

        List<ProjectSyncDto> projectDtos = projects.stream().map(this::toProjectSyncDto).collect(Collectors.toList());
        List<RoomSyncDto> roomDtos = rooms.stream().map(this::toRoomSyncDto).collect(Collectors.toList());
        List<EstimateItemSyncDto> itemDtos = estimateItems.stream().map(this::toEstimateItemSyncDto).collect(Collectors.toList());
        List<OpeningSyncDto> openingDtos = openings.stream().map(this::toOpeningSyncDto).collect(Collectors.toList());
        List<WorkerSyncDto> workerDtos = workers.stream().map(this::toWorkerSyncDto).collect(Collectors.toList());
        List<WorkTaskSyncDto> taskDtos = workTasks.stream().map(this::toWorkTaskSyncDto).collect(Collectors.toList());

        return new SyncPullResponse(projectDtos, roomDtos, itemDtos,
                openingDtos, workerDtos, taskDtos, serverTime);
    }

    @Transactional
    public SyncPushResponse push(String email, SyncPushRequest request) {
        User user = userRepository.findByEmail(email).orElseThrow();
        List<String> acceptedIds = new ArrayList<>();
        List<ConflictDto> conflicts = new ArrayList<>();

        if (request.getProjects() != null) {
            for (ProjectSyncDto clientDto : request.getProjects()) {
                processProject(clientDto, user, acceptedIds, conflicts);
            }
        }

        if (request.getRooms() != null) {
            for (RoomSyncDto clientDto : request.getRooms()) {
                processRoom(clientDto, user, acceptedIds, conflicts);
            }
        }

        if (request.getEstimateItems() != null) {
            for (EstimateItemSyncDto clientDto : request.getEstimateItems()) {
                processEstimateItem(clientDto, user, acceptedIds, conflicts);
            }
        }

        return new SyncPushResponse(acceptedIds, conflicts);
    }

    private void processProject(ProjectSyncDto dto, User user,
                                List<String> acceptedIds, List<ConflictDto> conflicts) {
        UUID dtoId = toUuid(dto.id);
        if (dtoId == null) {
            conflicts.add(new ConflictDto(dto.id, "invalid_id"));
            return;
        }
        Optional<Project> existing = projectRepository.findById(dtoId);
        if (existing.isPresent()) {
            Project dbProject = existing.get();
            if (!dbProject.getUserId().equals(user.getId())) {
                conflicts.add(new ConflictDto(uuidStr(dbProject.getId()), "access_denied"));
                return;
            }


            if (dto.version == 0L && dbProject.getVersion() == 1L) {
                acceptedIds.add(uuidStr(dbProject.getId()));
                return;
            }

            if (dto.version == dbProject.getVersion()) {
                dbProject.setName(dto.name);
                dbProject.setCity(dto.city);
                dbProject.setRegionCode(dto.regionCode);
                dbProject.setTaxMultiplier(parseDecimal(dto.taxMultiplier));
                dbProject.setLogisticsMarkup(parseDecimal(dto.logisticsMarkup));
                dbProject.setUpdatedAt(dto.updatedAt != null ? dto.updatedAt : System.currentTimeMillis());
                dbProject.setDeletedAt(dto.deletedAt);
                dbProject.setVersion(dbProject.getVersion() + 1);
                projectRepository.save(dbProject);
                acceptedIds.add(uuidStr(dbProject.getId()));
            } else {
                conflicts.add(new ConflictDto(uuidStr(dbProject.getId()),
                        serializeConflictSnapshot(toProjectSyncDto(dbProject), uuidStr(dbProject.getId()))));
            }
        } else {
            Project project = new Project();
            project.setId(dtoId);
            project.setUserId(user.getId());
            project.setName(dto.name);
            project.setCity(dto.city);
            project.setRegionCode(dto.regionCode);
            project.setTaxMultiplier(parseDecimal(dto.taxMultiplier));
            project.setLogisticsMarkup(parseDecimal(dto.logisticsMarkup));
            long now = System.currentTimeMillis();
            project.setCreatedAt(dto.createdAt != null ? dto.createdAt : now);
            project.setUpdatedAt(dto.updatedAt != null ? dto.updatedAt : now);
            project.setDeletedAt(dto.deletedAt);
            project.setVersion(1L);
            projectRepository.save(project);
            acceptedIds.add(uuidStr(project.getId()));
        }
    }

    private void processRoom(RoomSyncDto dto, User user,
                             List<String> acceptedIds, List<ConflictDto> conflicts) {
        UUID roomId = toUuid(dto.id);
        UUID projectId = toUuid(dto.projectId);
        if (roomId == null || projectId == null) {
            conflicts.add(new ConflictDto(dto.id, "invalid_id"));
            return;
        }
        if (!ownsProject(projectId, user.getId())) {
            conflicts.add(new ConflictDto(dto.id, "access_denied"));
            return;
        }
        Optional<ProjectRoom> existing = projectRoomRepository.findById(roomId);
        if (existing.isPresent()) {
            ProjectRoom dbRoom = existing.get();
            if (!ownsProject(dbRoom.getProjectId(), user.getId())) {
                conflicts.add(new ConflictDto(uuidStr(dbRoom.getId()), "access_denied"));
                return;
            }


            if (dto.version == 0L && dbRoom.getVersion() == 1L) {
                acceptedIds.add(uuidStr(dbRoom.getId()));
                return;
            }

            if (dto.version == dbRoom.getVersion()) {
                dbRoom.setName(dto.name);
                dbRoom.setLength(parseDecimal(dto.length));
                dbRoom.setWidth(parseDecimal(dto.width));
                dbRoom.setHeight(parseDecimal(dto.height));
                dbRoom.setManualAreaOverride(parseDecimal(dto.manualAreaOverride));

                dbRoom.setUpdatedAt(dto.updatedAt != null ? dto.updatedAt : System.currentTimeMillis());
                dbRoom.setDeletedAt(dto.deletedAt);
                dbRoom.setVersion(dbRoom.getVersion() + 1);
                projectRoomRepository.save(dbRoom);
                acceptedIds.add(uuidStr(dbRoom.getId()));
            } else {
                conflicts.add(new ConflictDto(uuidStr(dbRoom.getId()),
                        serializeConflictSnapshot(toRoomSyncDto(dbRoom), uuidStr(dbRoom.getId()))));
            }
        } else {
            ProjectRoom room = new ProjectRoom();
            room.setId(roomId);
            room.setProjectId(projectId);
            room.setName(dto.name);
            room.setLength(parseDecimal(dto.length));
            room.setWidth(parseDecimal(dto.width));
            room.setHeight(parseDecimal(dto.height));
            room.setManualAreaOverride(parseDecimal(dto.manualAreaOverride));

            long now = System.currentTimeMillis();
            room.setCreatedAt(dto.createdAt != null ? dto.createdAt : now);
            room.setUpdatedAt(dto.updatedAt != null ? dto.updatedAt : now);
            room.setDeletedAt(dto.deletedAt);
            room.setVersion(1L);
            projectRoomRepository.save(room);
            acceptedIds.add(uuidStr(room.getId()));
        }
    }

    private void processEstimateItem(EstimateItemSyncDto dto, User user,
                                     List<String> acceptedIds, List<ConflictDto> conflicts) {
        UUID itemId = toUuid(dto.id);
        UUID roomId = toUuid(dto.projectRoomId);
        if (itemId == null || roomId == null) {
            conflicts.add(new ConflictDto(dto.id, "invalid_id"));
            return;
        }
        if (!ownsRoom(roomId, user.getId())) {
            conflicts.add(new ConflictDto(dto.id, "access_denied"));
            return;
        }
        Optional<EstimateItem> existing = estimateItemRepository.findById(itemId);
        if (existing.isPresent()) {
            EstimateItem dbItem = existing.get();
            if (!ownsRoom(dbItem.getProjectRoomId(), user.getId())) {
                conflicts.add(new ConflictDto(uuidStr(dbItem.getId()), "access_denied"));
                return;
            }


            if (dto.version == 0L && dbItem.getVersion() == 1L) {
                acceptedIds.add(uuidStr(dbItem.getId()));
                return;
            }

            if (dto.version == dbItem.getVersion()) {
                dbItem.setProjectRoomId(roomId);
                dbItem.setName(dto.name);
                dbItem.setFgisCode(dto.fgisCode);
                dbItem.setUnitMeasure(dto.unitMeasure);
                dbItem.setUnit(dto.unitMeasure);
                dbItem.setBasePrice(parseDecimal(dto.basePrice));
                dbItem.setPrice(parseDecimal(dto.basePrice));
                dbItem.setFinalPrice(parseDecimal(dto.finalPrice));
                dbItem.setConsumptionRate(parseDecimal(dto.consumptionRate));
                dbItem.setQuantity(parseDecimal(dto.quantity));
                dbItem.setTotalPrice(parseDecimal(dto.totalPrice));
                dbItem.setTotal(parseDecimal(dto.totalPrice));
                dbItem.setStatus(dto.status);
                dbItem.setCalculationMethod(dto.calculationMethod);
                dbItem.setWastePercent(parseDecimal(dto.wastePercent));
                dbItem.setLayers(dto.layers);
                dbItem.setThicknessMeters(parseDecimal(dto.thicknessMeters));
                dbItem.setManualQuantity(parseDecimal(dto.manualQuantity));
                dbItem.setCoveragePerPiece(parseDecimal(dto.coveragePerPiece));
                dbItem.setCoveragePerPackage(parseDecimal(dto.coveragePerPackage));
                dbItem.setPackageSize(parseDecimal(dto.packageSize));
                dbItem.setFormulaDescription(dto.formulaDescription);
                if (dto.type != null) dbItem.setType(dto.type);
                dbItem.setUpdatedAt(dto.updatedAt != null ? dto.updatedAt : System.currentTimeMillis());
                dbItem.setDeletedAt(dto.deletedAt);
                dbItem.setVersion(dbItem.getVersion() + 1);
                estimateItemRepository.save(dbItem);
                acceptedIds.add(uuidStr(dbItem.getId()));
            } else {
                conflicts.add(new ConflictDto(uuidStr(dbItem.getId()),
                        serializeConflictSnapshot(toEstimateItemSyncDto(dbItem), uuidStr(dbItem.getId()))));
            }
        } else {
            EstimateItem item = new EstimateItem();
            item.setId(itemId);
            item.setProjectRoomId(roomId);
            item.setFgisCode(dto.fgisCode);
            item.setName(dto.name);
            item.setUnitMeasure(dto.unitMeasure);
            item.setUnit(dto.unitMeasure);
            item.setBasePrice(parseDecimal(dto.basePrice));
            item.setPrice(parseDecimal(dto.basePrice));
            item.setFinalPrice(parseDecimal(dto.finalPrice));
            item.setConsumptionRate(parseDecimal(dto.consumptionRate));
            item.setQuantity(parseDecimal(dto.quantity));
            item.setTotalPrice(parseDecimal(dto.totalPrice));
            item.setTotal(parseDecimal(dto.totalPrice));
            item.setStatus(dto.status);
            item.setCalculationMethod(dto.calculationMethod);
            item.setWastePercent(parseDecimal(dto.wastePercent));
            item.setLayers(dto.layers);
            item.setThicknessMeters(parseDecimal(dto.thicknessMeters));
            item.setManualQuantity(parseDecimal(dto.manualQuantity));
            item.setCoveragePerPiece(parseDecimal(dto.coveragePerPiece));
            item.setCoveragePerPackage(parseDecimal(dto.coveragePerPackage));
            item.setPackageSize(parseDecimal(dto.packageSize));
            item.setFormulaDescription(dto.formulaDescription);
            item.setType(dto.type != null ? dto.type : "MATERIAL");
            long now = System.currentTimeMillis();
            item.setCreatedAt(dto.createdAt != null ? dto.createdAt : now);
            item.setUpdatedAt(dto.updatedAt != null ? dto.updatedAt : now);
            item.setDeletedAt(dto.deletedAt);
            item.setVersion(1L);
            estimateItemRepository.save(item);
            acceptedIds.add(uuidStr(item.getId()));
        }
    }



    private ProjectSyncDto toProjectSyncDto(Project p) {
        ProjectSyncDto dto = new ProjectSyncDto();
        dto.id = uuidStr(p.getId());
        dto.name = p.getName();
        dto.city = p.getCity();
        dto.regionCode = p.getRegionCode();
        dto.taxMultiplier = p.getTaxMultiplier() != null ? p.getTaxMultiplier().toPlainString() : null;
        dto.logisticsMarkup = p.getLogisticsMarkup() != null ? p.getLogisticsMarkup().toPlainString() : null;
        dto.version = p.getVersion() != null ? p.getVersion() : 0L;
        dto.createdAt = p.getCreatedAt();
        dto.updatedAt = p.getUpdatedAt();
        dto.deletedAt = p.getDeletedAt();
        return dto;
    }

    private RoomSyncDto toRoomSyncDto(ProjectRoom r) {
        RoomSyncDto dto = new RoomSyncDto();
        dto.id = uuidStr(r.getId());
        dto.projectId = uuidStr(r.getProjectId());
        dto.name = r.getName();
        dto.length = toPlain(r.getLength());
        dto.width = toPlain(r.getWidth());
        dto.height = toPlain(r.getHeight());
        dto.manualAreaOverride = toPlain(r.getManualAreaOverride());

        dto.version = r.getVersion() != null ? r.getVersion() : 0L;
        dto.createdAt = r.getCreatedAt();
        dto.updatedAt = r.getUpdatedAt();
        dto.deletedAt = r.getDeletedAt();
        return dto;
    }

    private EstimateItemSyncDto toEstimateItemSyncDto(EstimateItem e) {
        EstimateItemSyncDto dto = new EstimateItemSyncDto();
        dto.id = uuidStr(e.getId());
        dto.projectRoomId = uuidStr(e.getProjectRoomId());
        dto.name = e.getName();
        dto.fgisCode = e.getFgisCode();
        dto.unitMeasure = e.getUnitMeasure();
        dto.basePrice = toPlain(e.getBasePrice());
        dto.finalPrice = toPlain(e.getFinalPrice());
        dto.consumptionRate = toPlain(e.getConsumptionRate());
        dto.quantity = toPlain(e.getQuantity());
        dto.totalPrice = toPlain(e.getTotalPrice());
        dto.type = e.getType();
        dto.status = e.getStatus();
        dto.calculationMethod = e.getCalculationMethod();
        dto.wastePercent = toPlain(e.getWastePercent());
        dto.layers = e.getLayers();
        dto.thicknessMeters = toPlain(e.getThicknessMeters());
        dto.manualQuantity = toPlain(e.getManualQuantity());
        dto.coveragePerPiece = toPlain(e.getCoveragePerPiece());
        dto.coveragePerPackage = toPlain(e.getCoveragePerPackage());
        dto.packageSize = toPlain(e.getPackageSize());
        dto.formulaDescription = e.getFormulaDescription();
        dto.version = e.getVersion() != null ? e.getVersion() : 0L;
        dto.createdAt = e.getCreatedAt();
        dto.updatedAt = e.getUpdatedAt();
        dto.deletedAt = e.getDeletedAt();
        return dto;
    }

    private OpeningSyncDto toOpeningSyncDto(Opening opening) {
        OpeningSyncDto dto = new OpeningSyncDto();
        dto.id = uuidStr(opening.getId());
        dto.projectRoomId = uuidStr(opening.getProjectRoomId());
        dto.type = opening.getType();
        dto.width = toPlain(opening.getWidth());
        dto.height = toPlain(opening.getHeight());
        dto.depth = toPlain(opening.getDepth());
        dto.placementType = opening.getPlacementType();
        dto.version = opening.getVersion() != null ? opening.getVersion() : 0L;
        dto.createdAt = opening.getCreatedAt();
        dto.updatedAt = opening.getUpdatedAt();
        dto.deletedAt = opening.getDeletedAt();
        return dto;
    }

    private WorkerSyncDto toWorkerSyncDto(Worker worker) {
        WorkerSyncDto dto = new WorkerSyncDto();
        dto.id = uuidStr(worker.getId());
        dto.userId = uuidStr(worker.getUserId());
        dto.fullName = worker.getFullName();
        dto.phone = worker.getPhone();
        dto.specialty = worker.getSpecialty();
        dto.version = worker.getVersion() != null ? worker.getVersion() : 0L;
        dto.createdAt = worker.getCreatedAt();
        dto.updatedAt = worker.getUpdatedAt();
        dto.deletedAt = worker.getDeletedAt();
        return dto;
    }

    private WorkTaskSyncDto toWorkTaskSyncDto(WorkTask task) {
        WorkTaskSyncDto dto = new WorkTaskSyncDto();
        dto.id = uuidStr(task.getId());
        dto.projectRoomId = uuidStr(task.getProjectRoomId());
        dto.workerId = uuidStr(task.getWorkerId());
        dto.taskName = task.getTaskName();
        dto.rateType = task.getRateType();
        dto.rateValue = toPlain(task.getRateValue());
        dto.totalPayment = toPlain(task.getTotalPayment());
        dto.version = task.getVersion() != null ? task.getVersion() : 0L;
        dto.createdAt = task.getCreatedAt();
        dto.updatedAt = task.getUpdatedAt();
        dto.deletedAt = task.getDeletedAt();
        return dto;
    }



    private BigDecimal parseDecimal(String value) {
        return (value == null || value.isBlank()) ? null : new BigDecimal(value);
    }

    private String toPlain(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private String serializeConflictSnapshot(Object snapshot, String entityId) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException serializationError) {
            throw new IllegalStateException(
                    "Failed to serialize sync conflict snapshot for entity " + entityId,
                    serializationError);
        }
    }

    private boolean ownsRoom(UUID roomId, UUID userId) {
        if (roomId == null) return false;
        return projectRoomRepository.findById(roomId)
                .map(ProjectRoom::getProjectId)
                .filter(pid -> ownsProject(pid, userId))
                .isPresent();
    }

    private boolean ownsProject(UUID projectId, UUID userId) {
        if (projectId == null) return false;
        return projectRepository.findById(projectId)
                .map(Project::getUserId)
                .filter(userId::equals)
                .isPresent();
    }


    private UUID toUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }


    private String uuidStr(UUID uuid) {
        return uuid == null ? null : uuid.toString();
    }
}

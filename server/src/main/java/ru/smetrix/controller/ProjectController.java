package ru.smetrix.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ru.smetrix.dto.ProjectDto;
import ru.smetrix.dto.ApiErrorResponse;
import ru.smetrix.entity.Project;
import ru.smetrix.entity.User;
import ru.smetrix.repository.EstimateItemRepository;
import ru.smetrix.repository.ProjectRepository;
import ru.smetrix.repository.ProjectRoomRepository;
import ru.smetrix.repository.UserRepository;
import ru.smetrix.repository.WorkTaskRepository;
import ru.smetrix.repository.OpeningRepository;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectRoomRepository projectRoomRepository;
    private final EstimateItemRepository estimateItemRepository;
    private final WorkTaskRepository workTaskRepository;
    private final OpeningRepository openingRepository;

    public ProjectController(ProjectRepository projectRepository,
                             UserRepository userRepository,
                             ProjectRoomRepository projectRoomRepository,
                             EstimateItemRepository estimateItemRepository,
                             WorkTaskRepository workTaskRepository,
                             OpeningRepository openingRepository) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.projectRoomRepository = projectRoomRepository;
        this.estimateItemRepository = estimateItemRepository;
        this.workTaskRepository = workTaskRepository;
        this.openingRepository = openingRepository;
    }

    @PostMapping
    public ResponseEntity<?> createOrUpdateProject(@RequestBody ProjectDto dto, Principal principal) {
        User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        long now = System.currentTimeMillis();

        UUID projectUuid = toUuid(dto.id);
        if (projectUuid == null) {
            return ResponseEntity.badRequest().body(
                    ApiErrorResponse.of("INVALID_ID", "Некорректный идентификатор проекта", dto.id));
        }

        Project project = projectRepository.findById(projectUuid).orElseGet(Project::new);
        if (project.getId() != null && !user.getId().equals(project.getUserId())) {
            return ResponseEntity.status(403).body(
                    ApiErrorResponse.of("ACCESS_DENIED", "Доступ запрещён", dto.id));
        }
        boolean created = project.getId() == null;
        if (created && dto.version != 0L) {
            return conflict(dto.id, "Новая запись должна иметь version=0");
        }
        if (!created && dto.version == 0L && project.getVersion() == 1L) {
            return ResponseEntity.ok(toDto(project));
        }
        if (!created && dto.version != project.getVersion()) {
            return conflict(dto.id, "Версия проекта устарела");
        }
        project.setId(projectUuid);
        project.setUserId(user.getId());
        project.setName(dto.name);
        project.setCity(dto.city);
        project.setRegionCode(dto.regionCode);
        project.setTaxMultiplier(parseDecimal(dto.taxMultiplier));
        project.setLogisticsMarkup(parseDecimal(dto.logisticsMarkup));
        project.setCreatedAt(project.getCreatedAt() != null ? project.getCreatedAt() : now);
        project.setUpdatedAt(now);
        project.setVersion(project.getVersion() != null ? project.getVersion() + 1 : 1L);

        Project saved = projectRepository.save(project);
        return ResponseEntity.status(created ? 201 : 200).body(toDto(saved));
    }

    @Transactional
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProject(@PathVariable String id,
                                           @RequestBody(required = false) Map<String, Long> body,
                                           Principal principal) {
        User user = userRepository.findByEmail(principal.getName()).orElseThrow();

        UUID projectUuid = toUuid(id);
        if (projectUuid == null) {
            return ResponseEntity.badRequest().body(
                    ApiErrorResponse.of("INVALID_ID", "Некорректный идентификатор проекта", id));
        }

        Project project = projectRepository.findById(projectUuid).orElseThrow();
        if (!user.getId().equals(project.getUserId())) {
            return ResponseEntity.status(403).body(
                    ApiErrorResponse.of("ACCESS_DENIED", "Доступ запрещён", id));
        }

        long clientVersion = body != null ? body.getOrDefault("version", -1L) : -1L;
        if (project.getDeletedAt() != null && clientVersion < project.getVersion()) {
            return ResponseEntity.ok().build();
        }
        if (clientVersion != project.getVersion()) {
            return conflict(id, "Версия проекта устарела");
        }

        long nowMilli = System.currentTimeMillis();


        project.setDeletedAt(nowMilli);
        project.setUpdatedAt(nowMilli);
        project.setVersion(project.getVersion() != null ? project.getVersion() + 1 : 1L);
        projectRepository.save(project);


        projectRoomRepository.softDeleteByProjectId(projectUuid, nowMilli);
        estimateItemRepository.softDeleteByProjectId(projectUuid, nowMilli);
        workTaskRepository.softDeleteByProjectId(projectUuid, nowMilli);
        openingRepository.softDeleteByProjectId(projectUuid, nowMilli);

        return ResponseEntity.ok().build();
    }

    private ProjectDto toDto(Project project) {
        ProjectDto dto = new ProjectDto();
        dto.id = project.getId() != null ? project.getId().toString() : null;
        dto.name = project.getName();
        dto.city = project.getCity();
        dto.regionCode = project.getRegionCode();
        dto.taxMultiplier = toPlain(project.getTaxMultiplier());
        dto.logisticsMarkup = toPlain(project.getLogisticsMarkup());
        dto.version = project.getVersion() != null ? project.getVersion() : 0L;
        return dto;
    }

    private BigDecimal parseDecimal(String value) {
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }

    private String toPlain(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private ResponseEntity<ApiErrorResponse> conflict(String id, String message) {
        return ResponseEntity.status(409).body(
                ApiErrorResponse.of("VERSION_CONFLICT", message, id));
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

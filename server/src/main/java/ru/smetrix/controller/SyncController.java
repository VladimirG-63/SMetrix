package ru.smetrix.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.smetrix.dto.SyncPullResponse;
import ru.smetrix.dto.SyncPushRequest;
import ru.smetrix.dto.SyncPushResponse;
import ru.smetrix.service.SyncService;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/sync")
@RequiredArgsConstructor
public class SyncController {

    private final SyncService syncService;

    @GetMapping("/pull")
    public SyncPullResponse pull(@RequestParam(defaultValue = "0") Long since, Principal principal) {
        return syncService.pull(principal.getName(), since);
    }

    @PostMapping("/push")
    public ResponseEntity<SyncPushResponse> push(@RequestBody SyncPushRequest request, Principal principal) {
        SyncPushResponse response = syncService.push(principal.getName(), request);
        if (!response.getConflicts().isEmpty()) {
            return ResponseEntity.status(409).body(response);
        }
        return ResponseEntity.ok(response);
    }
}

package com.fitconnect.backend.controller;

import com.fitconnect.backend.domain.ProgressRecord;
import com.fitconnect.backend.dto.ProgressRecordRequest;
import com.fitconnect.backend.security.CurrentUser;
import com.fitconnect.backend.service.ProgressTrackingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users/{userId}/progress")
@RequiredArgsConstructor
public class ProgressController {

    private final ProgressTrackingService progressTrackingService;

    @GetMapping
    public ResponseEntity<List<ProgressRecord>> getRecords(@PathVariable Long userId) {
        CurrentUser.requireSelf(userId);
        return ResponseEntity.ok(progressTrackingService.getRecordsForUser(userId));
    }

    @PostMapping
    public ResponseEntity<ProgressRecord> addRecord(
            @PathVariable Long userId,
            @Valid @RequestBody ProgressRecordRequest request) {
        CurrentUser.requireSelf(userId);
        ProgressRecord record = progressTrackingService.addRecord(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(record);
    }
}

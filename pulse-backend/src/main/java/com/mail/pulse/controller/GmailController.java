package com.mail.pulse.controller;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Draft;
import com.google.api.services.gmail.model.Message;
import com.mail.pulse.entity.GmailEntity;
import com.mail.pulse.service.GmailService;
import jakarta.mail.MessagingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/emails")
@CrossOrigin(origins = {"https://localhost:8080", "http://localhost:5173"})
public class GmailController {

    private final GmailService gmailService;
    private final Gmail gmailClient;

    public record EmailParams(
            String toEmailAddress,
            String subject,
            String bodyText,
            List<File> files // Note: Replace File with MultipartFile or Base64 string for proper REST uploads
    ) {}

    public GmailController(GmailService gmailService, Gmail gmailClient) {
        this.gmailService = gmailService;
        this.gmailClient = gmailClient;
    }

    @GetMapping("/{id}")
    public ResponseEntity<GmailEntity> getEmailById(@PathVariable("id") String id) {
        try {
            GmailEntity entity = gmailService.getEmailByID(id);
            return ResponseEntity.ok(entity);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PostMapping("/{id}/save")
    public ResponseEntity<String> saveEmail(@PathVariable("id") String id) {
        try {
            gmailService.saveEmail(id);
            return ResponseEntity.ok("Email saved successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to save email: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmail(@PathVariable("id") String id) {
        try {
            gmailService.deleteEmail(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    @PostMapping("/sync-new")
    public ResponseEntity<String> syncNewEmail() {
        if (gmailService.isIncrementalSyncRunning()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Incremental sync is already running.");
        }
        try {
            gmailService.syncNewEmails(); // Starts @Async background job
            return ResponseEntity.ok("Incremental sync started successfully.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to start incremental sync: " + e.getMessage());
        }
    }

    @PostMapping("/sync-new/stop")
    public ResponseEntity<String> stopIncrementalSync() {
        try {
            boolean stopped = gmailService.stopIncrementalSync();
            if (stopped) {
                return ResponseEntity.ok("Incremental sync stop requested.");
            } else {
                return ResponseEntity.ok("Incremental sync was not running.");
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to stop incremental sync: " + e.getMessage());
        }
    }

    @GetMapping("/sync-new/check")
    public ResponseEntity<Boolean> checkIncrementalSync() {
        try {
            boolean isRunning = gmailService.isIncrementalSyncRunning();
            return ResponseEntity.ok(isRunning);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/save-all")
    public ResponseEntity<String> saveAllEmails() {
        if (gmailService.isFullSyncRunning()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Full sync is already running.");
        }
        try {
            gmailService.saveAllEmails();
            return ResponseEntity.ok("Full sync started successfully.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to start full sync: " + e.getMessage());
        }
    }

    @PostMapping("/save-all/stop")
    public ResponseEntity<String> stopFullSync() {
        try {
            boolean stopped = gmailService.stopFullSync();
            if (stopped) {
                return ResponseEntity.ok("Full sync stop requested.");
            } else {
                return ResponseEntity.ok("Full sync was not running.");
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to stop full sync: " + e.getMessage());
        }
    }

    @GetMapping("/save-all/check")
    public ResponseEntity<Boolean> checkEmails() {
        try {
            boolean isRunning = gmailService.isFullSyncRunning();
            return ResponseEntity.ok(isRunning);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/drafts")
    public ResponseEntity<Draft> createDraft(@RequestBody EmailParams request) {
        try {
            String senderEmail = getCurrentUserEmail();
            Draft draft;

            if (request.files() != null && !request.files().isEmpty()) {
                draft = gmailService.createDraftWithAttachment(
                        request.toEmailAddress(),
                        senderEmail,
                        request.subject(),
                        request.bodyText(),
                        request.files()
                );
            } else {
                draft = gmailService.createDraft(
                        request.toEmailAddress(),
                        senderEmail,
                        request.subject(),
                        request.bodyText()
                );
            }
            return ResponseEntity.ok(draft);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> sendEmailDirectly(@RequestBody EmailParams request) {
        Map<String, Object> response = new HashMap<>();

        try {
            String senderEmail = getCurrentUserEmail();

            Message sentMessage = (request.files() != null && !request.files().isEmpty())
                    ? gmailService.sendEmailWithAttachmentsDirectly(
                    request.toEmailAddress(), senderEmail, request.subject(), request.bodyText(), request.files())
                    : gmailService.sendEmailDirectly(
                    request.toEmailAddress(), senderEmail, request.subject(), request.bodyText());

            response.put("success", true);
            response.put("messageId", sentMessage.getId());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/drafts/send")
    public ResponseEntity<Message> sendEmailFromDraft(@RequestBody Draft draft) {
        try {
            Message message = gmailService.sendEmailFromDraft(draft);
            return ResponseEntity.ok(message);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private String getCurrentUserEmail() throws IOException {
        return gmailClient.users().getProfile("me").execute().getEmailAddress();
    }
}


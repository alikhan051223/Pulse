package com.mail.pulse.controller;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Draft;
import com.google.api.services.gmail.model.Message;
import com.mail.pulse.dto.EmailSummary;
import com.mail.pulse.entity.GmailEntity;
import com.mail.pulse.service.GmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
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
            String bodyText
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

    @GetMapping("/{messageId}/attachments/{attachmentId}")
    public ResponseEntity<byte[]> getAttachment(
            @PathVariable("messageId") String messageId,
            @PathVariable("attachmentId") String attachmentId) {
        try {
            byte[] data = gmailService.getAttachmentData(messageId, attachmentId);
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment")
                    .body(data);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{id}/save")
    public ResponseEntity<String> saveEmail(@PathVariable("id") String id) {
        try {
            String userEmail = gmailClient.users().getProfile("me").execute().getEmailAddress();
            gmailService.saveEmail(id, userEmail);
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

    @GetMapping("/summaries")
    public Page<EmailSummary> getEmailSummaries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);

        return gmailService.getSummaries(pageable);
    }

    @PostMapping("/save-all")
    public ResponseEntity<String> saveAllEmails() {
        if (gmailService.isFullSyncRunning()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Full sync is already running.");
        }
        try {
            String userEmail = gmailClient.users().getProfile("me").execute().getEmailAddress();
            gmailService.saveAllEmails(userEmail);
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
    public ResponseEntity<Draft> createDraft(@RequestPart EmailParams request, @RequestPart List<MultipartFile> files) {
        try {
            String senderEmail = getCurrentUserEmail();
            Draft draft;

            if (files != null && !files.isEmpty()) {
                draft = gmailService.createDraftWithAttachment(
                        request.toEmailAddress(),
                        senderEmail,
                        request.subject(),
                        request.bodyText(),
                        files
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
    public ResponseEntity<Map<String, Object>> sendEmailDirectly(@RequestPart EmailParams request, @RequestPart(required = false) List<MultipartFile> files) {
        Map<String, Object> response = new HashMap<>();

        try {
            String senderEmail = getCurrentUserEmail();

            Message sentMessage = (files != null && !files.isEmpty())
                    ? gmailService.sendEmailWithAttachmentsDirectly(
                    request.toEmailAddress(), senderEmail, request.subject(), request.bodyText(), files)
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


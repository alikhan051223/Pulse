package com.mail.pulse.controller;

import com.mail.pulse.dto.EmailParams;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Draft;
import com.google.api.services.gmail.model.Message;
import com.mail.pulse.entity.Attachment;
import com.mail.pulse.entity.GmailEntity;
import com.mail.pulse.service.GmailService;
import jakarta.mail.MessagingException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/emails")
public class GmailController {

    private final GmailService gmailService;

    public GmailController(GmailService gmailService) {
        this.gmailService = gmailService;

    }

    @GetMapping("/{id}")
    public ResponseEntity<GmailEntity> getEmailById(@PathVariable String id) {
        return ResponseEntity.ok(gmailService.getEmailById(id));
    }

    @GetMapping("/drafts/{id}")
    public ResponseEntity<GmailEntity> getDraftById(@PathVariable String id) {
        return ResponseEntity.ok(gmailService.getDraftById(id));
    }

    @GetMapping("/{messageId}/attachments/{attachmentId}")
    public ResponseEntity<byte[]> getAttachment(
            @PathVariable String messageId,
            @PathVariable String attachmentId) throws IOException {
        byte[] attachment = gmailService.getAttachmentData(messageId, attachmentId);
        return ResponseEntity.ok(attachment);
    }

        @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmail(@PathVariable String id) throws IOException {
        gmailService.deleteEmail(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/draft/delete")
    public ResponseEntity<Void> deleteDraft(@PathVariable("id") String id) throws IOException {
            gmailService.deleteDraft(id);
            return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/trash")
    public ResponseEntity<Void> trashEmail(@PathVariable String id) throws IOException {
        gmailService.trashEmail(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/untrash")
    public ResponseEntity<Void> unTrashEmail(@PathVariable String id) throws IOException {
        gmailService.unTrashEmail(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/drafts/{id}/trash")
    public ResponseEntity<Void> trashDraft(@PathVariable String id) throws IOException {
        gmailService.trashDraft(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/drafts/{id}/untrash")
    public ResponseEntity<Void> unTrashDraft(@PathVariable String id) throws IOException {
        gmailService.unTrashDraft(id);
        return ResponseEntity.noContent().build();
    }


    /*
    @GetMapping("/summaries")
    public Page<EmailSummary> getEmailSummaries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);

        return gmailService.getSummaries(pageable);
    }

    @GetMapping("/draft/summaries")
    public Page<EmailSummary> getDraftSummaries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);

        return gmailService.getDraftSummaries(pageable);
    } */

    @PostMapping("/sync/initial")
    public ResponseEntity<Void> initialSync() {
        gmailService.initialSync();
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/sync/stop")
    public ResponseEntity<Boolean> stopFullSync() {
            boolean stopped = gmailService.stopFullSync();
           return ResponseEntity.ok(stopped);
    }

    @GetMapping("/sync/check")
    public ResponseEntity<Boolean> checkSync() {
            boolean isRunning = gmailService.isFullSyncRunning();
           return ResponseEntity.ok(isRunning);
    }

    @PostMapping("/draft")
    public ResponseEntity<Draft> createDraft(@RequestPart EmailParams request, @RequestPart(required = false) List<MultipartFile> files) throws MessagingException, IOException {

            Draft draft;

            if (files != null && !files.isEmpty()) {
                draft = gmailService.createDraftWithAttachment(
                        request.toEmailAddress(),
                        request.subject(),
                        request.bodyText(),
                        files
                );
            } else {
                draft = gmailService.createDraft(
                        request.toEmailAddress(),
                        request.subject(),
                        request.bodyText()
                );
            }
            return ResponseEntity.ok(draft);
    }

    @PostMapping("/send")
    public ResponseEntity<Message> sendEmailDirectly(@RequestPart EmailParams request, @RequestPart(required = false) List<MultipartFile> files) throws MessagingException, IOException {
            Message sentMessage = (files != null && !files.isEmpty())
                    ? gmailService.sendEmailWithAttachmentsDirectly(
                    request.toEmailAddress(), request.subject(), request.bodyText(), files)
                    : gmailService.sendEmailDirectly(
                    request.toEmailAddress(), request.subject(), request.bodyText());
            return ResponseEntity.ok(sentMessage);
    }

    @PostMapping("/draft/send")
    public ResponseEntity<Message> sendEmailFromDraft(@RequestBody Draft draft) throws IOException {
            Message message = gmailService.sendEmailFromDraft(draft);
            return ResponseEntity.ok(message);
    }


    @PostMapping("/sync")
    public ResponseEntity<Void> sync() throws IOException, InterruptedException {
        gmailService.sync();
        return ResponseEntity.noContent().build();
    }

}


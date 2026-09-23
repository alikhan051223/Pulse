package com.mail.pulse.controller;

import com.mail.pulse.dto.EmailFilter;
import com.mail.pulse.dto.EmailParams;
import com.google.api.services.gmail.model.Draft;
import com.google.api.services.gmail.model.Message;
import com.mail.pulse.dto.EmailSummary;
import com.mail.pulse.entity.GmailEntity;
import com.mail.pulse.service.GmailService;
import jakarta.mail.MessagingException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/emails")
public class GmailController {

    private final GmailService gmailService;

    public GmailController(GmailService gmailService) {
        this.gmailService = gmailService;

    }

    @GetMapping("/{id}") // used
    public ResponseEntity<GmailEntity> getEmailById(@PathVariable String id) throws IOException {
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

    @PostMapping("/sync/stop") // used
    public ResponseEntity<Boolean> stopFullSync() {
            boolean stopped = gmailService.stopFullSync();
           return ResponseEntity.ok(stopped);
    }

    @GetMapping("/sync/check") // used
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

    @PostMapping("/send") // used
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


    @PostMapping("/sync") // used
    public ResponseEntity<Void> sync() throws IOException, InterruptedException {
        gmailService.startSync();
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/summaries") // used
    public ResponseEntity<Page<EmailSummary>> getEmailSummaries(EmailFilter filter, Pageable pageable) throws IOException {
        Page<EmailSummary> es = gmailService.getEmailSummaries(filter, pageable);
        return ResponseEntity.ok(es);
    }

}


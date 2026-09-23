package com.mail.pulse.service;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;
import com.mail.pulse.dto.EmailFilter;
import com.mail.pulse.dto.EmailSummary;
import com.mail.pulse.entity.Attachment;
import com.mail.pulse.entity.GmailEntity;
import com.mail.pulse.entity.SyncEntity;
import com.mail.pulse.filtering.GmailSpecification;
import com.mail.pulse.repository.GmailRepository;
import com.mail.pulse.repository.SyncRepository;
import jakarta.activation.DataHandler;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.Thread;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class GmailService {

    private final GmailRepository gmailRepository;
    private final Gmail gmailClient;
    private final AtomicBoolean fullSyncRunning = new AtomicBoolean(false);
    private final SyncRepository syncRepository;
    private final ClearDatabase clearDatabase;
    public GmailService(GmailRepository gmailRepository, Gmail gmailClient, SyncRepository syncRepository, ClearDatabase clearDatabase) {
        this.gmailRepository = gmailRepository;
        this.gmailClient = gmailClient;
        this.syncRepository = syncRepository;
        this.clearDatabase = clearDatabase;
    }


    public Page<EmailSummary> getEmailSummaries(EmailFilter filter, Pageable pageable) throws IOException {
        // 1. Build the specification dynamically from the filter
        filter.setInboxOwner(gmailClient.users().getProfile("me").execute().getEmailAddress());
        Specification<GmailEntity> spec = GmailSpecification.build(filter);

        // 2. Query the repository and map entities to EmailSummary DTOs
        return gmailRepository.findAll(spec, pageable)
                .map(entity -> new EmailSummary(
                        entity.getId(),
                        entity.getSender(),
                        entity.getSubject(),
                        entity.getDateSent(),
                        entity.getSnippet(),
                        entity.getAttachments()
                ));
    }


    // =========================================================================
    // Draft Methods
    // =========================================================================

    public GmailEntity getDraftById(String id) {
        return gmailRepository.findByIdAndDraftTrue(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Draft not found"));
    }


    public void trashDraft(String id) throws IOException {
        gmailClient.users().messages().trash("me", id).execute();
    }

    public void unTrashDraft(String id) throws IOException {
        gmailClient.users().messages().untrash("me", id).execute();
    }


    public Draft createDraft(String to, String subject, String body)
            throws MessagingException, IOException {

        MimeMessage mime = createEmail(to, gmailClient.users().getProfile("me").execute().getEmailAddress(), subject, body);
        Message googleMessage = createMessageWithEmail(mime);

        try {
            Draft draft = new Draft().setMessage(googleMessage);
            return gmailClient.users().drafts().create("me", draft).execute();
        } catch (GoogleJsonResponseException e) {
            log.error("Failed to create draft: {}", e.getDetails().getMessage(), e);
            throw e;
        }
    }

    public Draft createDraftWithAttachment(String to, String subject, String body, List<MultipartFile> files)
            throws MessagingException, IOException {

        MimeMessage mime = createEmailWithAttachment(to, gmailClient.users().getProfile("me").execute().getEmailAddress(), subject, body, files);
        Message googleMessage = createMessageWithEmail(mime);

        try {
            Draft draft = new Draft().setMessage(googleMessage);
            return gmailClient.users().drafts().create("me", draft).execute();
        } catch (GoogleJsonResponseException e) {
            log.error("Failed to create draft with attachments: {}", e.getDetails().getMessage(), e);
            throw e;
        }
    }

    @Transactional
    public void saveDraft(String draftId, String inboxOwner) throws IOException {
        // 1. Fetch from API using the DRAFT ID
        Draft draft = gmailClient.users().drafts().get("me", draftId).execute();
        Message message = draft.getMessage();

        GmailEntity pulseMessage = new GmailEntity();
        List<Attachment> attachmentCollector = new ArrayList<>();

        String subject = getSubjectFromHeaders(message.getPayload().getHeaders());
        String sender = getSenderFromHeaders(message.getPayload().getHeaders());
        String cc = getCCfromHeaders(message.getPayload().getHeaders());
        String hBody = getHtmlFromMessage(message.getPayload());
        String pBody = getPlainTextFromMessage(message.getPayload());
        extractAttachments(message.getId(), message.getPayload(), attachmentCollector);
        pulseMessage.setId(draftId);
        pulseMessage.setInboxOwner(inboxOwner);
        pulseMessage.setRecipient(getRecipientFromHeaders(message.getPayload().getHeaders()));
        pulseMessage.setDateSent(message.getInternalDate() != null
                ? Instant.ofEpochMilli(message.getInternalDate())
                : null);
        pulseMessage.setThreadId(message.getThreadId());
        pulseMessage.setSubject(subject);
        pulseMessage.setSender(sender);
        pulseMessage.setSnippet(message.getSnippet());
        pulseMessage.setPlainTextBody(pBody);
        pulseMessage.setHtmlBody(hBody);
        if (cc != null && !cc.isBlank()) {
            pulseMessage.setCc(List.of(cc.split(",")));
        } else {
            pulseMessage.setCc(new ArrayList<>());
        }
        pulseMessage.setDraft(true);
        pulseMessage.setLabels(message.getLabelIds());
        parseLabels(pulseMessage, message.getLabelIds());

        for (Attachment attachment : attachmentCollector) {
            pulseMessage.addAttachment(attachment);
        }

        gmailRepository.save(pulseMessage);
    }

    /*
    @Async
    public void saveAllDrafts() throws IOException, InterruptedException {
        if (!fullSyncRunning.compareAndSet(false, true)) {
            log.info("Full sync requested, but another process is already running.");
            return;
        }

        try {
            String userEmail = gmailClient.users().getProfile("me").execute().getEmailAddress();
            fetchAndSaveAllDrafts(userEmail);
        } finally {
            fullSyncRunning.set(false);
        }
    } */

    private void fetchAndSaveAllDrafts(String userEmail) throws IOException, InterruptedException {
        String token = null;

        while (fullSyncRunning.get()) {
            var request = gmailClient.users().drafts().list("me").setMaxResults(500L);
            if (token != null) request.setPageToken(token);

            ListDraftsResponse response = request.execute();
            List<Draft> drafts = response.getDrafts();

            if (drafts != null) {
                for (Draft draft : drafts) {
                    if (!fullSyncRunning.get()) break;

                    if (gmailRepository.existsByIdAndInboxOwner(draft.getId(), userEmail)) {
                        continue;
                    }
                    try {
                        saveDraft(draft.getId(), userEmail);
                        Thread.sleep(300);
                    } catch (IOException e) {
                        log.warn("Failed to save draft {}: {}", draft.getId(), e.getMessage());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.error("Draft sync interrupted", e);
                        break;
                    }
                }
            }
            token = response.getNextPageToken();
            if (token == null) break;
        }
    }

    public Message sendEmailFromDraft(Draft draft) throws IOException {
        try {
            return gmailClient.users().drafts().send("me", draft).execute();
        } catch (GoogleJsonResponseException e) {
            log.error("Google API Error: {}", e.getDetails().getMessage(), e);
            throw e;
        }
    }

    @Transactional
    public void deleteDraft(String id) throws IOException {
        gmailClient.users().drafts().delete("me", id).execute();
        GmailEntity email = gmailRepository.findByIdAndDraftTrue(id).orElseThrow();
        gmailRepository.delete(email);
    }

    // =========================================================================
    // Email Methods
    // =========================================================================

    public List<GmailEntity> findAll() {
        return gmailRepository.findAll();
    }


    public GmailEntity getEmailById(String id) throws IOException {
        return gmailRepository.findByIdAndInboxOwnerAndDraftFalse(id, gmailClient.users().getProfile("me").execute().getEmailAddress())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Email not found"));
    }


    @Transactional
    public void saveEmail(String id, String inboxOwner) throws IOException {
        Message message;
        try {
            message = gmailClient.users().messages().get("me", id).execute();
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 404) {
                log.warn("Message {} was deleted on Gmail (404 Not Found). Skipping.", id);
                gmailRepository.deleteById(id);
                return;
            }
            throw e; // Re-throw any other errors (e.g. 500, 403, 429)
        }
        if (message.getLabelIds() != null && message.getLabelIds().contains("DRAFT")) {
            return;
        }
        GmailEntity pulseMessage = new GmailEntity();
        List<Attachment> attachmentCollector = new ArrayList<>();
        String subject = getSubjectFromHeaders(message.getPayload().getHeaders());
        String sender = getSenderFromHeaders(message.getPayload().getHeaders());
        String cc = getCCfromHeaders(message.getPayload().getHeaders());
        String hBody = getHtmlFromMessage(message.getPayload());
        String pBody = getPlainTextFromMessage(message.getPayload());
        extractAttachments(message.getId(), message.getPayload(), attachmentCollector);

        pulseMessage.setId(message.getId());
        pulseMessage.setInboxOwner(inboxOwner);
        pulseMessage.setRecipient(getRecipientFromHeaders(message.getPayload().getHeaders()));
        pulseMessage.setDateSent(message.getInternalDate() != null
                ? Instant.ofEpochMilli(message.getInternalDate())
                : null);
        pulseMessage.setThreadId(message.getThreadId());
        pulseMessage.setSubject(subject);
        pulseMessage.setSender(sender);
        pulseMessage.setSnippet(message.getSnippet());
        pulseMessage.setPlainTextBody(pBody);
        pulseMessage.setHtmlBody(hBody);
        if (cc != null && !cc.isBlank()) {
            pulseMessage.setCc(List.of(cc.split(",")));
        } else {
            pulseMessage.setCc(new ArrayList<>());
        }
        pulseMessage.setDraft(false);
        pulseMessage.setLabels(message.getLabelIds());
        parseLabels(pulseMessage,message.getLabelIds());

        for (Attachment attachment : attachmentCollector) {
            pulseMessage.addAttachment(attachment);
        }


        pulseMessage.setHistoryId(message.getHistoryId().toString());
        gmailRepository.save(pulseMessage);
    }


    private void fetchAndSaveAllEmails(String userEmail) throws IOException, InterruptedException {
        String token = null;

        while (fullSyncRunning.get()) {
            Gmail.Users.Messages.List request = gmailClient.users().messages().list("me")
                    .setQ("-label:DRAFT")
                    .setIncludeSpamTrash(true)
                    .setMaxResults(500L);

            if (token != null) request.setPageToken(token);

            ListMessagesResponse response = request.execute();
            List<Message> messages = response.getMessages();

            if (messages != null) {
                for (Message message : messages) {
                    if (!fullSyncRunning.get()) break;

                    if (gmailRepository.existsByIdAndInboxOwner(message.getId(), userEmail)) {
                        continue;
                    }
                    try {
                        saveEmail(message.getId(), userEmail);
                        Thread.sleep(300);
                    } catch (IOException e) {
                        log.warn("Failed to download email {}: {}", message.getId(), e.getMessage());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.error("Sync interrupted", e);
                        break;
                    }
                }
            }
            token = response.getNextPageToken();
            if (token == null) break;
        }
    }

    public Message sendEmailDirectly(String to, String subject, String body)
            throws MessagingException, IOException {
        String from = gmailClient.users().getProfile("me").execute().getEmailAddress();
        MimeMessage mime = createEmail(to, from, subject, body);
        Message googleMessage = createMessageWithEmail(mime);
        return sendEmail(googleMessage);
    }

    public Message sendEmailWithAttachmentsDirectly(String to, String subject, String body, List<MultipartFile> files)
            throws MessagingException, IOException {
        String from = gmailClient.users().getProfile("me").execute().getEmailAddress();
        MimeMessage mime = createEmailWithAttachment(to, from, subject, body, files);
        Message googleMessage = createMessageWithEmail(mime);
        return sendEmail(googleMessage);
    }

    public Message sendEmail(Message email) throws IOException {
        try {
            return gmailClient.users().messages().send("me", email).execute();
        } catch (GoogleJsonResponseException e) {
            log.error("Google API Error sending email", e);
            throw e;
        }
    }

    public void trashEmail(String id) throws IOException {
        gmailClient.users().messages().trash("me", id).execute();
    }

    public void unTrashEmail(String id) throws IOException {
        gmailClient.users().messages().untrash("me", id).execute();
    }

    @Transactional
    public void deleteEmail(String id) throws IOException {
        gmailClient.users().messages().delete("me", id).execute();
        GmailEntity email = gmailRepository.findByIdAndDraftFalse(id).orElseThrow();
        gmailRepository.delete(email);
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    public byte[] getAttachmentData(String messageId, String attachmentId) throws IOException {
        MessagePartBody body = gmailClient.users().messages().attachments().get("me", messageId, attachmentId).execute();
        return Base64.decodeBase64(body.getData());
    }

    public void extractAttachments(String messageID, MessagePart messagePart, List<Attachment> attachments) {
        if (messagePart == null) return;
        String n = messagePart.getFilename();

        boolean hasFilename = n != null && !n.isBlank();
        boolean hasAttachmentId = messagePart.getBody() != null && messagePart.getBody().getAttachmentId() != null && !messagePart.getBody().getAttachmentId().isBlank();
        if (hasFilename || hasAttachmentId) {

            Attachment x = new Attachment();
            String contentId = "";
            List<MessagePartHeader> headers = messagePart.getHeaders();
            boolean isInline = false;

            if (headers != null) {
                for (MessagePartHeader header : headers) {
                    if ("Content-ID".equalsIgnoreCase(header.getName()) && header.getValue() != null) {
                        contentId = header.getValue();
                        isInline = true;
                    }
                }
            }

            if (messagePart.getBody() != null) {
                if (messagePart.getBody().getSize() != null) {
                    x.setSize(messagePart.getBody().getSize().longValue());
                }
                x.setAttachmentId(messagePart.getBody().getAttachmentId());
            }

            x.setContentType(messagePart.getMimeType());

            x.setFileName(n != null ? n : "attachment");
            x.setInline(isInline);
            x.setContentId(contentId);
            x.setMessageId(messageID);
            attachments.add(x);

        }

        List<MessagePart> mp = messagePart.getParts();
        if (mp != null) {
            for (MessagePart part : mp) {
                extractAttachments(messageID, part, attachments);
            }
        }
    }

    public String getHtmlFromMessage(MessagePart messagePart){
        if (messagePart == null) {
            return "";
        }

        if ("text/html".equalsIgnoreCase(messagePart.getMimeType())) {
            if(messagePart.getBody() != null) {
                byte[] bytes = messagePart.getBody().decodeData();
                if (bytes != null) {
                    String html = new String(bytes, StandardCharsets.UTF_8);
                    return Jsoup.clean(html, safelist());
                }
            }

            return "";
        }

        List<MessagePart> parts = messagePart.getParts();

        if (parts != null) {
            for (MessagePart part : parts) {
                String html = getHtmlFromMessage(part);

                if (!html.isEmpty()) {
                    return html;
                }
            }
        }

        return "";
    }



    public String getPlainTextFromMessage(MessagePart messagePart) {
        if (messagePart == null) return "";
        if ("text/plain".equalsIgnoreCase(messagePart.getMimeType()) && messagePart.getBody() != null) {
            byte[] bytes = messagePart.getBody().decodeData();
            return bytes != null ? new String(bytes, StandardCharsets.UTF_8) : "";
        }
        if (messagePart.getParts() != null) {
            for (MessagePart part : messagePart.getParts()) {
                String text = getPlainTextFromMessage(part);
                if (!text.isEmpty()) return text;
            }
        }
        return "";
    }



    private Safelist safelist() {
        return Safelist.relaxed()
                .addAttributes(":all", "style")
                .addProtocols("img", "src", "http", "https", "cid");
    }

    public String getSubjectFromHeaders(List<MessagePartHeader> headers) {

        if (headers == null) {
            return "";
        }

        for (MessagePartHeader header : headers) {
            if ("Subject".equalsIgnoreCase(header.getName()) && header.getValue() != null) {
                return header.getValue();
            }
        }

        return "";
    }

    public String getSenderFromHeaders(List<MessagePartHeader> headers){

        if (headers == null) {
            return "";
        }
        for (MessagePartHeader header : headers) {
            if ("From".equalsIgnoreCase(header.getName()) && header.getValue() != null) {
                return header.getValue();
            }
        }
        return "";
    }

    public String getRecipientFromHeaders(List<MessagePartHeader> headers) {

        if (headers == null) {
            return "";
        }
        for (MessagePartHeader header : headers) {
            if ("To".equalsIgnoreCase(header.getName()) && header.getValue() != null) {
                return header.getValue();
            }
        }
        return "";
    }

    public String getCCfromHeaders(List<MessagePartHeader> headers) {
        if (headers == null) {
            return "";
        }
        for (MessagePartHeader header : headers) {
            if ("cc".equalsIgnoreCase(header.getName()) && header.getValue() != null) {
                return header.getValue();
            }
        }
        return "";
    }

    public static MimeMessage createEmail(String toEmailAddress,
                                          String fromEmailAddress,
                                          String subject,
                                          String bodyText)
            throws MessagingException {
        Properties props = new Properties();
        Session session = Session.getInstance(props, null);

        MimeMessage email = new MimeMessage(session);

        email.setFrom(new InternetAddress(fromEmailAddress));
        email.addRecipient(jakarta.mail.Message.RecipientType.TO,
                new InternetAddress(toEmailAddress));
        email.setSubject(subject, "utf-8");
        email.setText(bodyText, "utf-8");
        return email;
    }

    public static MimeMessage createEmailWithAttachment(String toEmailAddress,
                                                        String fromEmailAddress,
                                                        String subject,
                                                        String bodyText, List<MultipartFile> files)
            throws MessagingException, IOException {

        if (files == null) {
            throw new IllegalArgumentException("Attachment file must not be null");
        }

        Properties props = new Properties();
        Session session = Session.getInstance(props, null);
        MimeMessage email = new MimeMessage(session);

        email.setFrom(new InternetAddress(fromEmailAddress));
        email.addRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress(toEmailAddress));
        email.setSubject(subject, "utf-8");
        Multipart multipart = new MimeMultipart();

        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText(bodyText, "utf-8");
        multipart.addBodyPart(textPart);

        boolean hasAttachments = false;

        for (MultipartFile file : files) {
            if ((file == null || file.isEmpty())) {
                continue;
            }
            MimeBodyPart attachmentPart = new MimeBodyPart();

            attachmentPart.setFileName(file.getOriginalFilename());

            attachmentPart.setDataHandler(
                    new DataHandler(
                            new ByteArrayDataSource(
                                    file.getInputStream(),
                                    file.getContentType()
                            )
                    )
            );

            multipart.addBodyPart(attachmentPart);
            hasAttachments = true;
        }

        if (hasAttachments) {
            email.setContent(multipart);
        } else {
            email.setText(bodyText, "utf-8");
        }
        return email;
    }

    public static Message createMessageWithEmail(MimeMessage emailContent)
            throws MessagingException, IOException {

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        emailContent.writeTo(buffer);
        byte[] bytes = buffer.toByteArray();
        String encodedEmail = Base64.encodeBase64URLSafeString(bytes);
        Message message = new Message();
        message.setRaw(encodedEmail);
        return message;
    }

    public void parseLabels(GmailEntity pulseMessage, List<String> labels) {
        if (pulseMessage == null || labels == null || labels.isEmpty()) {
            return;
        }

        for (String label : labels) {
            switch (label) {
                case "INBOX" -> pulseMessage.setInbox(true);
                case "UNREAD" -> pulseMessage.setUnread(true);
                case "TRASH" -> pulseMessage.setTrash(true);
                case "SPAM" -> pulseMessage.setSpam(true);
                case "SENT" -> pulseMessage.setSent(true);
            }
        }
    }

    public void removeLabelsFromMessage(GmailEntity pulseMessage, List<String> labelIds) {
        if (pulseMessage == null || labelIds == null || labelIds.isEmpty()) {
            return;
        }

        for (String label : labelIds) {
            switch (label) {
                case "INBOX" -> pulseMessage.setInbox(false);
                case "UNREAD" -> pulseMessage.setUnread(false);
                case "TRASH" -> pulseMessage.setTrash(false);
                case "SPAM" -> pulseMessage.setSpam(false);
                case "SENT" -> pulseMessage.setSent(false);
            }
        }

        if (pulseMessage.getLabels() != null) {
            pulseMessage.getLabels().removeAll(labelIds);
        }
    }

    public void addLabelsToMessage(GmailEntity pulseMessage, List<String> labelIds) {
        if (pulseMessage == null || labelIds == null || labelIds.isEmpty()) {
            return;
        }
        for (String label : labelIds) {
            if(!pulseMessage.getLabels().contains(label)){
                pulseMessage.getLabels().add(label);
            }
        }
    }

// /////////////////////// History //////////////////////////////// //

    public boolean stopFullSync() {
        return fullSyncRunning.compareAndSet(true, false);
    }

    public boolean isFullSyncRunning() {
        return fullSyncRunning.get();
    }

    @Async
    public void startSync() {
        if (!fullSyncRunning.compareAndSet(false, true)) {
            log.info("Sync requested, but another sync process is already running.");
            return;
        }
        sync();
    }

    @Async
    public void sync() {

        String userEmail = null;

        try {
            Profile user = gmailClient.users().getProfile("me").execute();
            userEmail = user.getEmailAddress();

            SyncEntity syncEntity = syncRepository.findFirstByUserIdOrderByTimeSavedDesc(userEmail).orElse(null);

            // 1. Initial Full Sync Path
            if (syncEntity == null) {
                log.info("No sync history found for user {}. Triggering initial full sync.", userEmail);
                BigInteger startHistoryId = gmailClient.users().getProfile("me").execute().getHistoryId();
                fetchAndSaveAllDrafts(userEmail);
                fetchAndSaveAllEmails(userEmail);

                if(!fullSyncRunning.get()){
                    return;
                }

                SyncEntity syncE = new SyncEntity();
                syncE.setLastHistoryId(startHistoryId);
                syncE.setTimeSaved(Instant.now());
                syncE.setUserId(userEmail);
                syncRepository.save(syncE);
                return;
            }

            // 2. Incremental Sync Path
            BigInteger historyId = syncEntity.getLastHistoryId();
            String pageToken = null;
            BigInteger newHistoryId = historyId;

            while (fullSyncRunning.get()) {
                var request = gmailClient.users().history().list("me")
                        .setMaxResults(500L)
                        .setStartHistoryId(historyId);

                if (pageToken != null) {
                    request.setPageToken(pageToken);
                }

                ListHistoryResponse response;
                try {
                    response = request.execute();
                } catch (GoogleJsonResponseException e) {
                    if (e.getStatusCode() == 404 || e.getStatusCode() == 400) {
                        log.warn("History ID {} expired for user {}. Repopulating database...", historyId, userEmail);
                        repopulateDatabase(userEmail);
                        return;
                    }
                    throw e;
                }

                if (response.getHistory() != null) {
                    for (History history : response.getHistory()) {
                        if (!fullSyncRunning.get()) {
                            log.info("Incremental sync stopped mid-processing for user {}", userEmail);
                            break;
                        }
                        saveMessagesAdded(history, userEmail);
                        deleteMessagesDeleted(history);
                        addLabels(history);
                        removeLabels(history);
                    }
                }

                if (response.getHistoryId() != null) {
                    newHistoryId = response.getHistoryId();
                }

                pageToken = response.getNextPageToken();
                if (pageToken == null) {
                    break;
                }
            }

            // Persist new state if not cancelled
            if (fullSyncRunning.get()) {
                clearDatabase.clearDraftsForUser(userEmail);
                fetchAndSaveAllDrafts(userEmail);

                syncEntity.setLastHistoryId(newHistoryId);
                syncRepository.save(syncEntity);
                log.info("Incremental sync completed successfully for user {}", userEmail);
            }

        } catch (GoogleJsonResponseException e) {
            log.error("Google API error during sync for user {}: {}", userEmail, e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error during sync execution for user {}", userEmail, e);
        } finally {
            fullSyncRunning.set(false);
        }
    }

    public void repopulateDatabase(String userEmail) throws IOException, InterruptedException {
        BigInteger startHistoryId = gmailClient.users().getProfile("me").execute().getHistoryId();
        clearDatabase.clearUserData(userEmail);
        fetchAndSaveAllEmails(userEmail);
        fetchAndSaveAllDrafts(userEmail);

        SyncEntity syncE = new SyncEntity();
        syncE.setLastHistoryId(startHistoryId);
        syncE.setTimeSaved(Instant.now());
        syncE.setUserId(userEmail);
        syncRepository.save(syncE);
    }


    public void saveMessagesAdded(History history, String userEmail) throws IOException {
        List<HistoryMessageAdded> addedList = history.getMessagesAdded();
        if (addedList == null) return;

        for (HistoryMessageAdded messageAdded : addedList) {
            if (messageAdded.getMessage() != null) {
                saveEmail(messageAdded.getMessage().getId(), userEmail);
            }
        }
    }

    @Transactional
    public void deleteMessagesDeleted(History history) {
        List<HistoryMessageDeleted> deletedList = history.getMessagesDeleted();
        if (deletedList == null) return;

        for (HistoryMessageDeleted messageDeleted : deletedList) {
            if (messageDeleted.getMessage() != null) {
                gmailRepository.deleteById(messageDeleted.getMessage().getId());
            }
        }
    }

    @Transactional
    public void addLabels(History history) {
        List<HistoryLabelAdded> labelAddedList = history.getLabelsAdded();
        if (labelAddedList == null) return;

        for (HistoryLabelAdded messageAltered : labelAddedList) {
            if (messageAltered.getMessage() == null) continue;

            String id = messageAltered.getMessage().getId();
            GmailEntity message = gmailRepository.findById(id).orElse(null);
            if (message == null) {
                continue;
            }

            List<String> labelIds = messageAltered.getLabelIds();
            parseLabels(message, labelIds);
            addLabelsToMessage(message,labelIds);
            gmailRepository.save(message);
        }
    }

    @Transactional
    public void removeLabels(History history) {
        List<HistoryLabelRemoved> labelRemovedList = history.getLabelsRemoved();
        if (labelRemovedList == null) return;

        for (HistoryLabelRemoved messageAltered : labelRemovedList) {
            if (messageAltered.getMessage() == null) continue;

            String id = messageAltered.getMessage().getId();
            GmailEntity message = gmailRepository.findById(id).orElse(null);
            if (message == null) {
                continue;
            }

            List<String> labelIds = messageAltered.getLabelIds();
            removeLabelsFromMessage(message, labelIds);
            gmailRepository.save(message);
        }
    }
}
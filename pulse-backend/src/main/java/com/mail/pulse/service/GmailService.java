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
    public GmailService(GmailRepository gmailRepository, Gmail gmailClient, SyncRepository syncRepository) {
        this.gmailRepository = gmailRepository;
        this.gmailClient = gmailClient;
        this.syncRepository = syncRepository;
    }


    @Async
    public void initialSync() {
        // Attempt to acquire execution lock for the entire sync process
        if (!fullSyncRunning.compareAndSet(false, true)) {
            log.info("Full sync requested, but another sync process is already running.");
            return;
        }

        try {
            log.info("Starting initial sync (Drafts and Emails)...");

            fetchAndSaveAllDrafts();

            if (fullSyncRunning.get()) {
                fetchAndSaveAllEmails();
            }

            log.info("Initial sync completed successfully.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Initial sync interrupted", e);
        } catch (IOException e) {
            log.error("Network or API error during initial sync: {}", e.getMessage(), e);
        } finally {

            fullSyncRunning.set(false);
        }
    }

    public Page<EmailSummary> getEmailSummaries(EmailFilter filter, Pageable pageable) {
        // 1. Build the specification dynamically from the filter
        Specification<GmailEntity> spec = GmailSpecification.build(filter);

        // 2. Query the repository and map entities to EmailSummary DTOs
        return gmailRepository.findAll(spec, pageable)
                .map(entity -> new EmailSummary(
                        entity.getId(),
                        entity.getSender(),
                        entity.getSubject(),
                        entity.getDateSent(),
                        entity.getSnippet()
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
    public void saveDraft(String draftId) throws IOException {
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
        extractAttachments(message.getPayload(), attachmentCollector);
        pulseMessage.setId(message.getId());
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

    private void fetchAndSaveAllDrafts() throws IOException, InterruptedException {
        String token = null;

        while (fullSyncRunning.get()) {
            var request = gmailClient.users().drafts().list("me").setMaxResults(500L);
            if (token != null) {
                request.setPageToken(token);
            }

            ListDraftsResponse response = request.execute();
            List<Draft> drafts = response.getDrafts();

            if (drafts != null) {
                for (Draft draft : drafts) {
                    if (!fullSyncRunning.get()) break;

                    if (draft.getMessage() != null && gmailRepository.existsById(draft.getMessage().getId())) {
                        continue;
                    }
                    try {
                        saveDraft(draft.getId());
                        Thread.sleep(300);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.error("Draft sync interrupted: {}", e.getMessage(), e);
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


    public GmailEntity getEmailById(String id) {
        return gmailRepository.findByIdAndDraftFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Email not found"));
    }


    @Transactional
    public void saveEmail(String id) throws IOException {
        Message message = gmailClient.users().messages().get("me", id).execute();
        GmailEntity pulseMessage = new GmailEntity();
        List<Attachment> attachmentCollector = new ArrayList<>();
        String subject = getSubjectFromHeaders(message.getPayload().getHeaders());
        String sender = getSenderFromHeaders(message.getPayload().getHeaders());
        String cc = getCCfromHeaders(message.getPayload().getHeaders());
        String hBody = getHtmlFromMessage(message.getPayload());
        String pBody = getPlainTextFromMessage(message.getPayload());
        extractAttachments(message.getPayload(), attachmentCollector);

        pulseMessage.setId(message.getId());
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
        pulseMessage.setCc(List.of(cc.split(",")));
        pulseMessage.setDraft(false);
        pulseMessage.setLabels(message.getLabelIds());
        parseLabels(pulseMessage,message.getLabelIds());

        for (Attachment attachment : attachmentCollector) {
            pulseMessage.addAttachment(attachment);
        }


        pulseMessage.setHistoryId(message.getHistoryId().toString());
        gmailRepository.save(pulseMessage);
    }
/*
    @Async
    public void saveAllEmails() throws IOException, InterruptedException {

        if (!fullSyncRunning.compareAndSet(false, true)) {
            log.info("Full sync requested, but another process is already running.");
            return;
        }

        try {
            String userEmail = gmailClient.users().getProfile("me").execute().getEmailAddress();
           fetchAndSaveAllEmails(userEmail);
        } finally {
            fullSyncRunning.set(false);
        }
    } */

    private void fetchAndSaveAllEmails() throws IOException, InterruptedException {
        String token = null;

        while (fullSyncRunning.get()) {
            Gmail.Users.Messages.List request = gmailClient.users().messages().list("me").setQ("-label:DRAFT").setIncludeSpamTrash(true).setMaxResults(500L);
            if (token != null) {
                request.setPageToken(token);
            }

            ListMessagesResponse response = request.execute();
            List<Message> messages = response.getMessages();

            if (messages != null) {
                for (Message message : messages) {
                    if (!fullSyncRunning.get()) {
                        break;
                    }
                    if (gmailRepository.existsById(message.getId())) {
                        continue;
                    }
                    try {
                        saveEmail(message.getId());
                        Thread.sleep(300);
                    } catch (InterruptedException e) {
                        log.error(e.getMessage(), e);
                    }
                }
            }

            token = response.getNextPageToken();

            if (token == null) {
                break;
            }
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

    public void deleteEmail(String id) throws IOException {
        gmailClient.users().messages().delete("me", id).execute();
        GmailEntity email = gmailRepository.findByIdAndDraftFalse(id).orElseThrow();
        gmailRepository.delete(email);
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    public boolean stopFullSync() {
        return fullSyncRunning.compareAndSet(true, false);
    }

    public boolean isFullSyncRunning() {
        return fullSyncRunning.get();
    }

    public byte[] getAttachmentData(String messageId, String attachmentId) throws IOException {
        MessagePartBody body = gmailClient.users().messages().attachments().get("me", messageId, attachmentId).execute();
        return Base64.decodeBase64(body.getData());
    }

    public void extractAttachments(MessagePart messagePart, List<Attachment> attachments) {
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
            attachments.add(x);

        }

        List<MessagePart> mp = messagePart.getParts();
        if (mp != null) {
            for (MessagePart part : mp) {
                extractAttachments(part, attachments);
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
            pulseMessage.getLabels().add(label);
        }
    }

// /////////////////////// History //////////////////////////////// //

    public void sync() throws IOException, InterruptedException {

        if (!fullSyncRunning.compareAndSet(false, true)) {
            log.info("Incremental sync requested, but another sync process is already running.");
            return;
        }
        String userEmail = gmailClient.users().getProfile("me").execute().getEmailAddress();

        try {

            SyncEntity syncEntity = syncRepository.findFirstByUserIdOrderByTimeSavedDesc(userEmail).orElse(null);
            if (syncEntity == null) {
                log.info("No sync history found for user {}. Triggering initial full sync.", userEmail);
                fetchAndSaveAllEmails();
                return;
            }

            BigInteger historyId = syncEntity.getLastHistoryId();
            String pageToken = null;
            BigInteger newHistoryId = historyId;

            // 2. Outer loop checks fullSyncRunning on every page request
            while (fullSyncRunning.get()) {
                var request = gmailClient.users().history().list("me")
                        .setMaxResults(500L)
                        .setStartHistoryId(historyId);

                if (pageToken != null) {
                    request.setPageToken(pageToken);
                }

                ListHistoryResponse response = request.execute();

                if (response.getHistory() != null) {
                    // 3. Inner loop checks fullSyncRunning before processing each history item
                    for (History history : response.getHistory()) {
                        if (!fullSyncRunning.get()) {
                            log.info("Incremental sync stopped mid-processing for user {}", userEmail);
                            break;
                        }
                        saveMessagesAdded(history);
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

            // Only persist new state if sync wasn't stopped prematurely
            if (fullSyncRunning.get()) {
                gmailRepository.deleteByRecipientAndDraftTrue(userEmail);
                fetchAndSaveAllDrafts(); // Call internal helper (without re-locking)

                syncEntity.setLastHistoryId(newHistoryId);
                syncRepository.save(syncEntity);
                log.info("Incremental sync completed successfully for user {}", userEmail);
            }

        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 404 || e.getStatusCode() == 400) {
                log.warn("History ID expired for user {}. Resetting database and re-syncing...", userEmail);
                repopulateDatabase(userEmail);
            } else {
                log.error("Google API error during sync for user {}: {}", userEmail, e.getMessage(), e);
                throw e;
            }
        } finally {
            // 4. Always release the lock
            fullSyncRunning.set(false);
        }
    }

    @Transactional
    public void repopulateDatabase(String userEmail) throws IOException, InterruptedException {
        gmailRepository.deleteByRecipient(userEmail);
        syncRepository.deleteByUserId(userEmail);
        fetchAndSaveAllEmails();
    }

    public void saveMessagesAdded(History history) throws IOException {
        List<HistoryMessageAdded> addedList = history.getMessagesAdded();
        if (addedList == null) return;

        for (HistoryMessageAdded messageAdded : addedList) {
            if (messageAdded.getMessage() != null) {
                String id = messageAdded.getMessage().getId();
                saveEmail(id);
            }
        }
    }

    public void deleteMessagesDeleted(History history) {
        List<HistoryMessageDeleted> deletedList = history.getMessagesDeleted();
        if (deletedList == null) return;

        for (HistoryMessageDeleted messageDeleted : deletedList) {
            if (messageDeleted.getMessage() != null) {
                gmailRepository.deleteById(messageDeleted.getMessage().getId());
            }
        }
    }

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
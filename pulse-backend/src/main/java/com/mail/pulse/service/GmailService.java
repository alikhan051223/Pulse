package com.mail.pulse.service;

import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;
import com.mail.pulse.repository.GmailRepository;
import com.mail.pulse.entity.*;
import com.mail.pulse.repository.TokenRepository;
import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.activation.FileDataSource;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.MimeUtility;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.Thread;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class GmailService {

    private final GmailRepository gmailRepository;
    private final Gmail gmailClient;
    private final TokenRepository tokenRepository;



    public GmailService(GmailRepository gmailRepository, Gmail gmailClient, TokenRepository tokenRepository) {
        this.gmailRepository = gmailRepository;
        this.gmailClient = gmailClient;
        this.tokenRepository = tokenRepository;
    }



    public List<GmailEntity> findAll() {
        return gmailRepository.findAll();
    }

    public List<GmailEntity> filterMail(String sender, String subject, Instant dateSent) {
        return gmailRepository.searchInbox(sender, subject, dateSent);
    }

    public GmailEntity findByID(String id) {
        return gmailRepository.findById(id).orElseThrow();
    }


    public void deleteEmail(String id) throws IOException {
        GmailEntity email = gmailRepository.findById(id).orElseThrow();
        gmailRepository.delete(email);
        gmailClient.users().messages().delete("me", id);
    }




    public GmailEntity getEmailByID(String id) throws IOException {
        return gmailRepository.findById(id).orElseThrow();
    }

    private final AtomicBoolean incrementalSyncRunning = new AtomicBoolean(false);

    public boolean stopIncrementalSync() {
        return incrementalSyncRunning.compareAndSet(true, false);
    }

    public boolean isIncrementalSyncRunning() {
        return incrementalSyncRunning.get();
    }

    @Async
    public void syncNewEmails() throws IOException, InterruptedException {
        // Prevent concurrent execution of incremental syncs
        if (!incrementalSyncRunning.compareAndSet(false, true)) {
            log.info("Incremental sync requested, but another process is already running.");
            return;
        }

        try {
            String userEmail = gmailClient.users().getProfile("me").execute().getEmailAddress();
            String pageToken = null;
            boolean oldEmails = false;

            while (incrementalSyncRunning.get() && !oldEmails) {
                Gmail.Users.Messages.List request = gmailClient.users().messages().list(userEmail);
                if (pageToken != null) {
                    request.setPageToken(pageToken);
                }

                ListMessagesResponse response = request.execute();
                List<Message> messages = response.getMessages();

                if (messages == null || messages.isEmpty()) {
                    break;
                }

                for (Message message : messages) {
                    if (!incrementalSyncRunning.get()) {
                        break;
                    }

                    String mid = message.getId();
                    if (gmailRepository.existsById(mid)) {
                        // Reached an email that was already synced previously
                        oldEmails = true;
                        break;
                    }

                    saveEmail(mid);
                    Thread.sleep(300);
                }

                pageToken = response.getNextPageToken();
                if (pageToken == null) {
                    break;
                }
            }
        } finally {
            incrementalSyncRunning.set(false);
        }
    }
    private final AtomicBoolean fullSyncRunning = new AtomicBoolean(false);

    public boolean stopFullSync() {
        return fullSyncRunning.compareAndSet(true, false);
    }

    public boolean isFullSyncRunning() {
        return fullSyncRunning.get();
    }

    @Async
    public void saveAllEmails() throws IOException, InterruptedException {
        // Prevent running multiple background historic syncs at once
        if (!fullSyncRunning.compareAndSet(false, true)) {
            log.info("Full sync requested, but another process is already running.");
            return;
        }

        try {
            String userEmail = gmailClient.users().getProfile("me").execute().getEmailAddress();
            Token lastToken = tokenRepository.findById(userEmail).orElse(new Token(userEmail));
            String token = lastToken.getToken();

            while (fullSyncRunning.get()) {
                Gmail.Users.Messages.List request = gmailClient.users().messages().list("me");
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

                        saveEmail(message.getId());
                        Thread.sleep(300);
                    }
                }

                token = response.getNextPageToken();
                lastToken.setToken(token);
                lastToken.setSavedAt(Instant.now());
                tokenRepository.save(lastToken);

                if (token == null) {
                    break; // Reached end of mailbox
                }
            }
        } finally {
            fullSyncRunning.set(false);
        }
    }




    @Transactional
    public void saveEmail(String id) throws IOException {
        Message message = gmailClient.users().messages().get("me", id).execute();
        GmailEntity pulseMessage = new GmailEntity();
        List<Attachment> inlineCollector = new ArrayList<>();
        List<Attachment> attachmentCollector = new ArrayList<>();
        String subject = getSubjectFromHeaders(message.getPayload().getHeaders());
        String sender = getSenderFromHeaders(message.getPayload().getHeaders());
        String hBody = getHtmlFromMessage(message.getPayload());
        String pBody = getPlainTextFromMessage(message.getPayload());
        List<Attachment> attachments = getAttachmentsFromMessage(message.getPayload(), attachmentCollector);
        List<Attachment> inlineAttachments = getInlinesFromMessage(message.getPayload(), inlineCollector);

        pulseMessage.setId(message.getId());
        pulseMessage.setUserEmail(gmailClient.users().getProfile("me").execute().getEmailAddress());
        pulseMessage.setDateSent(Instant.ofEpochMilli(message.getInternalDate()));
        pulseMessage.setThreadId(message.getThreadId());
        pulseMessage.setSubject(subject);
        pulseMessage.setSender(sender);
        pulseMessage.setSnippet(message.getSnippet());
        pulseMessage.setPlainTextBody(pBody);
        pulseMessage.setHtmlBody(hBody);
        pulseMessage.setAttachments(attachments);
        pulseMessage.setInlineAttachments(inlineAttachments);

        gmailRepository.save(pulseMessage);
    }


    public String getHtmlFromMessage(MessagePart messagePart) throws IOException {

        if (messagePart == null) return "";

        if (!("text/html").equalsIgnoreCase(messagePart.getMimeType())) {
            List<MessagePart> mp = messagePart.getParts();
            if (mp != null) {
                for (MessagePart part : mp) {
                    String x = getHtmlFromMessage(part);
                    if (!x.isEmpty()) return x;
                }
            }
        }

        if (("text/html").equalsIgnoreCase(messagePart.getMimeType())) {
            byte[] bytes = messagePart.getBody().decodeData();
            return new String(bytes);
        }

        return "";

    }

    public String getPlainTextFromMessage(MessagePart messagePart) throws IOException {

        if (messagePart == null) return "";

        if (!("text/plain").equalsIgnoreCase(messagePart.getMimeType())) {
            List<MessagePart> mp = messagePart.getParts();
            if (mp != null) {
                for (MessagePart part : mp) {
                    String x = getPlainTextFromMessage(part);
                    if (!x.isEmpty()) return x;
                }
            }
        }

        if (("text/plain").equalsIgnoreCase(messagePart.getMimeType())) {
            byte[] bytes = messagePart.getBody().decodeData();
            String html = new String(bytes);

            return Jsoup.clean(html, safelist());
        }

        return "";

    }

    private Safelist safelist() {
        return Safelist.relaxed()
                .addAttributes(":all", "style")
                .addProtocols("img", "src", "http", "https", "cid");
    }

    public List<Attachment> getAttachmentsFromMessage(MessagePart messagePart, List<Attachment> attachments) throws IOException {

        if (messagePart == null) return attachments;

        List<MessagePartHeader> headers = messagePart.getHeaders();

        if (headers != null) {
            for (MessagePartHeader header : headers) {
                if ("Content-Disposition".equalsIgnoreCase(header.getName())
                        && header.getValue() != null
                        && header.getValue().contains("attachment")) {

                    Attachment x = new Attachment();
                    x.setAttachmentID(messagePart.getBody().getAttachmentId());
                    x.setFileName(messagePart.getFilename());
                    attachments.add(x);
                    break;
                }
            }
        }

        List<MessagePart> mp = messagePart.getParts();
        if (mp != null) {
            for (MessagePart part : mp) {
                getAttachmentsFromMessage(part, attachments);
            }
        }
        return attachments;
    }

    public List<Attachment> getInlinesFromMessage(MessagePart messagePart, List<Attachment> inlines) throws IOException {

        if (messagePart == null) return inlines;

        List<MessagePartHeader> headers = messagePart.getHeaders();

        if (headers != null) {
            for (MessagePartHeader header : headers) {
                if ("Content-Disposition".equalsIgnoreCase(header.getName())
                        && header.getValue() != null
                        && header.getValue().contains("inline")) {

                    Attachment x = new Attachment();
                    x.setAttachmentID(messagePart.getBody().getAttachmentId());
                    x.setFileName(messagePart.getFilename());
                    inlines.add(x);
                    break;
                }
            }
        }

        List<MessagePart> mp = messagePart.getParts();
        if (mp != null) {
            for (MessagePart part : mp) {
                getInlinesFromMessage(part, inlines);
            }
        }

        return inlines;
    }

    public String getSubjectFromHeaders(List<MessagePartHeader> headers) throws IOException {

        if (headers == null) {
            return "";
        }

        for (MessagePartHeader header : headers) {
            if ("Subject".equalsIgnoreCase(header.getName())) {
                return header.getValue();
            }
        }

        return "";
    }

    public String getSenderFromHeaders(List<MessagePartHeader> headers) throws IOException {

        if (headers == null) {
            return "";
        }
        for (MessagePartHeader header : headers) {
            if ("From".equalsIgnoreCase(header.getName())) {
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
                                                        String bodyText, List<File> files)
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

        boolean hasAttachments = false;

        for (File file : files) {
            if (file == null || !file.exists()) {
                continue;
            }
            MimeBodyPart filePart = new MimeBodyPart();
            DataSource source = new FileDataSource(file);
            filePart.setDataHandler(new DataHandler(source));
            filePart.setFileName(MimeUtility.encodeText(file.getName()));
            multipart.addBodyPart(filePart);
            hasAttachments = true;
        }

        if (hasAttachments) {
            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText(bodyText, "utf-8");
            multipart.addBodyPart(textPart);
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


    public Draft createDraft(String to, String from, String subject, String body)
            throws MessagingException, IOException {

        MimeMessage mime = createEmail(to, from, subject, body);
        Message googleMessage = createMessageWithEmail(mime);

        try {
            Draft draft = new Draft().setMessage(googleMessage);
            return gmailClient.users().drafts().create("me", draft).execute();
        } catch (GoogleJsonResponseException e) {
            log.error("Failed to create draft: {}", e.getDetails().getMessage(), e);
            throw e;
        }
    }

    public Draft createDraftWithAttachment(String to, String from, String subject, String body, List<File> files)
            throws MessagingException, IOException {

        MimeMessage mime = createEmailWithAttachment(to, from, subject, body, files);
        Message googleMessage = createMessageWithEmail(mime);

        try {
            Draft draft = new Draft().setMessage(googleMessage);
            return gmailClient.users().drafts().create("me", draft).execute();
        } catch (GoogleJsonResponseException e) {
            log.error("Failed to create draft with attachments: {}", e.getDetails().getMessage(), e);
            throw e;
        }
    }


    public Message sendEmailDirectly(String to, String from, String subject, String body)
            throws MessagingException, IOException {

        MimeMessage mime = createEmail(to, from, subject, body);
        Message googleMessage = createMessageWithEmail(mime);

        return sendEmail(googleMessage);
    }

    public Message sendEmailWithAttachmentsDirectly(String to, String from, String subject, String body, List<File> files)
            throws MessagingException, IOException {

        MimeMessage mime = createEmailWithAttachment(to, from, subject, body, files);
        Message googleMessage = createMessageWithEmail(mime);

        return sendEmail(googleMessage);
    }


    public Message sendEmailFromDraft(Draft draft) throws IOException {
        try {
            return gmailClient.users().drafts().send("me", draft).execute();
        } catch (GoogleJsonResponseException e) {
            log.error("Google API Error: {}", e.getDetails().getMessage(), e);
            throw e;
        }
    }


    public Message sendEmail(Message email) throws IOException {
        try {
            return gmailClient.users().messages().send("me", email).execute();
        } catch (GoogleJsonResponseException e) {
            log.error("Google API Error sending email", e);
            throw e;
        }
    }
}
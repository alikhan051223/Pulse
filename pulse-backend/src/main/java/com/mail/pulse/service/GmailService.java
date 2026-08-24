package com.mail.pulse.service;

import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;
import com.mail.pulse.dto.EmailSummary;
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
import jakarta.mail.util.ByteArrayDataSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.Thread;
import java.nio.charset.StandardCharsets;
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

    public Page<GmailEntity> filterMail(String sender, String subject, Instant dateSent, Pageable pageable) {
        return gmailRepository.searchInbox(sender, subject, dateSent, pageable );
    }

    public GmailEntity findByID(String id) {
        return gmailRepository.findById(id).orElseThrow();
    }

    public void trashEmail(String id, String userEmail) throws IOException {
        gmailClient.users().messages().trash(id, userEmail).execute();
    }

    public void deleteEmail(String id) throws IOException {
        GmailEntity email = gmailRepository.findById(id).orElseThrow();
        gmailRepository.delete(email);
        gmailClient.users().messages().delete("me", id).execute();
    }

    public GmailEntity getEmailByID(String id) throws IOException {
        return gmailRepository.findById(id).orElseThrow();
    }

    private final AtomicBoolean fullSyncRunning = new AtomicBoolean(false);

    public boolean stopFullSync() {
        return fullSyncRunning.compareAndSet(true, false);
    }

    public boolean isFullSyncRunning() {
        return fullSyncRunning.get();
    }

    @Async
    public void saveAllEmails(String userEmail) throws IOException, InterruptedException {

        if (!fullSyncRunning.compareAndSet(false, true)) {
            log.info("Full sync requested, but another process is already running.");
            return;
        }

        try {
            String token = null;

            while (fullSyncRunning.get()) {
                Gmail.Users.Messages.List request = gmailClient.users().messages().list("me").setMaxResults(500L);
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
                        if(gmailRepository.existsById(message.getId())) {
                            continue;
                        }
                        try {
                            saveEmail(message.getId(), userEmail);
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
        } finally {
            fullSyncRunning.set(false);
        }
    }


    @Transactional
    public void saveEmail(String id, String userEmail) throws IOException {
        Message message = gmailClient.users().messages().get("me", id).execute();
        GmailEntity pulseMessage = new GmailEntity();
        List<Attachment> attachmentCollector = new ArrayList<>();
        String subject = getSubjectFromHeaders(message.getPayload().getHeaders());
        String sender = getSenderFromHeaders(message.getPayload().getHeaders());
        String hBody = getHtmlFromMessage(message.getPayload());
        String pBody = getPlainTextFromMessage(message.getPayload());
        extractAttachments(message.getPayload(),  attachmentCollector);

        pulseMessage.setId(message.getId());
        pulseMessage.setUserEmail(userEmail);
        pulseMessage.setDateSent(message.getInternalDate() != null
                ? Instant.ofEpochMilli(message.getInternalDate())
                : null);
        pulseMessage.setThreadId(message.getThreadId());
        pulseMessage.setSubject(subject);
        pulseMessage.setSender(sender);
        pulseMessage.setSnippet(message.getSnippet());
        pulseMessage.setPlainTextBody(pBody);
        pulseMessage.setHtmlBody(hBody);

        for(Attachment attachment : attachmentCollector) {
            pulseMessage.addAttachment(attachment);
        }

        gmailRepository.save(pulseMessage);
    }

    public Page<EmailSummary> getSummaries(Pageable pageable) {
        return gmailRepository.getSummaries(pageable);
    }


    public String getHtmlFromMessage(MessagePart messagePart) throws IOException {
        if (messagePart == null) {
            return "";
        }

        if ("text/html".equalsIgnoreCase(messagePart.getMimeType())) {
            byte[] bytes = messagePart.getBody().decodeData();

            if (bytes != null) {
                String html = new String(bytes, StandardCharsets.UTF_8);
                return Jsoup.clean(html, safelist());
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
            if(bytes != null) {
                return new String(bytes, StandardCharsets.UTF_8);
            }
        }

        return "";

    }

    private Safelist safelist() {
        return Safelist.relaxed()
                .addAttributes(":all", "style")
                .addProtocols("img", "src", "http", "https", "cid");
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

            if(headers != null) {
            for (MessagePartHeader header : headers) {
                    if ("Content-ID".equalsIgnoreCase(header.getName()) && header.getValue() != null) {
                        contentId = header.getValue();
                        isInline = true;
                    }
                }
            }

            if(messagePart.getBody() != null) {
                if(messagePart.getBody().getSize() != null) {
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

    public Draft createDraftWithAttachment(String to, String from, String subject, String body, List<MultipartFile> files)
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

    public Message sendEmailWithAttachmentsDirectly(String to, String from, String subject, String body, List<MultipartFile> files)
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
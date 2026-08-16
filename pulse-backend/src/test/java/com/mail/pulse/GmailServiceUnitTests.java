package com.mail.pulse;

import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.mail.pulse.entity.Attachment;
import com.mail.pulse.repository.GmailRepository;
import com.mail.pulse.service.GmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class GmailServiceUnitTests {

    @MockitoBean
    private GmailRepository gmailRepository;

    @Autowired
    private GmailService gmailService;

    @Test
    void getHtmlFromMessageValidInputs() throws IOException {

        MessagePartBody htmlBody = new MessagePartBody().setData(Base64.getUrlEncoder().withoutPadding().encodeToString("<html><body><h2>Hello Bob,</h2></body></html>".getBytes()));

        MessagePart htmlPart = new MessagePart().setMimeType("text/html").setBody(htmlBody);

        MessagePart payload = new MessagePart().setMimeType("multipart/alternative").setParts(java.util.List.of(htmlPart));

        String expectedHtml = "<html><body><h2>Hello Bob,</h2></body></html>";

        String testHtml = gmailService.getHtmlFromMessage(payload);

        assertEquals(expectedHtml, testHtml);

    }

    @Test
    void getPlainTextFromMessageValidInputs() throws IOException {
        // Arrange
        String rawText = "Hello Bob, this is plain text.";
        MessagePartBody textBody = new MessagePartBody()
                .setData(Base64.getUrlEncoder().withoutPadding().encodeToString(rawText.getBytes()));

        MessagePart textPart = new MessagePart()
                .setMimeType("text/plain")
                .setBody(textBody);

        MessagePart payload = new MessagePart()
                .setMimeType("multipart/alternative")
                .setParts(java.util.List.of(textPart));

        String testText = gmailService.getPlainTextFromMessage(payload);

        assertEquals(rawText, testText);
    }

    @Test
    void getAttachmentsFromMessageValidInputs() throws IOException {
        // Arrange
        MessagePartHeader dispositionHeader = new MessagePartHeader()
                .setName("Content-Disposition")
                .setValue("attachment; filename=\"test_log.txt\"");

        MessagePartBody attachmentBody = new MessagePartBody()
                .setAttachmentId("attach_12345");

        MessagePart attachmentPart = new MessagePart()
                .setMimeType("text/plain")
                .setFilename("test_log.txt")
                .setHeaders(java.util.List.of(dispositionHeader))
                .setBody(attachmentBody);

        MessagePart payload = new MessagePart()
                .setMimeType("multipart/mixed")
                .setParts(java.util.List.of(attachmentPart));

        // Act
        java.util.List<Attachment> attachments = gmailService.getAttachmentsFromMessage(payload, new java.util.ArrayList<>());

        // Assert
        assertEquals(1, attachments.size());
        assertEquals("attach_12345", attachments.getFirst().getAttachmentID());
        assertEquals("test_log.txt", attachments.getFirst().getFileName());
    }

    @Test
    void getInlinesFromMessageValidInputs() throws IOException {

        MessagePartHeader dispositionHeader = new MessagePartHeader().setName("Content-Disposition")
                .setValue("inline; filename=\"logo.png\"");

        MessagePartBody inlineBody = new MessagePartBody()
                .setAttachmentId("inline_67890");

        MessagePart inlinePart = new MessagePart()
                .setMimeType("image/png")
                .setFilename("logo.png")
                .setHeaders(java.util.List.of(dispositionHeader))
                .setBody(inlineBody);

        MessagePart payload = new MessagePart()
                .setMimeType("multipart/related")
                .setParts(java.util.List.of(inlinePart));

        java.util.List<Attachment> inlines = gmailService.getInlinesFromMessage(payload, new java.util.ArrayList<>());

        assertEquals(1, inlines.size());
        assertEquals("inline_67890", inlines.getFirst().getAttachmentID());
        assertEquals("logo.png", inlines.getFirst().getFileName());
    }

    @Test
    void getSubjectFromHeadersValidInputs() throws IOException {

        MessagePartHeader subjectHeader = new MessagePartHeader()
                .setName("Subject")
                .setValue("Test MIME Message for Development");

        MessagePartHeader otherHeader = new MessagePartHeader()
                .setName("To")
                .setValue("bob@example.com");

        java.util.List<MessagePartHeader> headers = java.util.List.of(otherHeader, subjectHeader);


        String subject = gmailService.getSubjectFromHeaders(headers);


        assertEquals("Test MIME Message for Development", subject);
    }

    @Test
    void getSenderFromHeadersValidInputs() throws IOException {

        MessagePartHeader fromHeader = new MessagePartHeader()
                .setName("From")
                .setValue("Alice Sender <alice@example.com>");

        java.util.List<MessagePartHeader> headers = java.util.List.of(fromHeader);

        String sender = gmailService.getSenderFromHeaders(headers);

        assertEquals("Alice Sender <alice@example.com>", sender);
    }

    @Test
    void getHtmlFromMessageNullInput() throws IOException {
        assertEquals("", gmailService.getHtmlFromMessage(null));
    }

    @Test
    void getPlainTextFromMessageNullInput() throws IOException {
        assertEquals("", gmailService.getPlainTextFromMessage(null));
    }

    @Test
    void getAttachmentsFromMessageNullInput() throws IOException {

        java.util.List<Attachment> startingList = new java.util.ArrayList<>();


        java.util.List<Attachment> result = gmailService.getAttachmentsFromMessage(null, startingList);


        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getInlinesFromMessageNullInput() throws IOException {

        java.util.List<Attachment> startingList = new java.util.ArrayList<>();


        java.util.List<Attachment> result = gmailService.getInlinesFromMessage(null, startingList);


        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getSubjectFromHeadersNullInput() throws IOException {
        assertEquals("", gmailService.getSubjectFromHeaders(null));
    }

    @Test
    void getSenderFromHeadersNullInput() throws IOException {
        assertEquals("",  gmailService.getSenderFromHeaders(null));
    }


}


package com.mail.pulse.dto;

import com.mail.pulse.entity.Attachment;

import java.time.Instant;
import java.util.List;

public record EmailSummary(
        String emailID,
        String fromEmailAddress,
        String subject,
        Instant dateSent,
        String snippet,
        List<Attachment> attachments
) {
    // Compact constructor ensures attachments is never null for React
    public EmailSummary {
        attachments = (attachments != null) ? attachments : List.of();
    }
}
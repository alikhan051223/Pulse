package com.mail.pulse.dto;

import java.time.Instant;

    public record EmailSummary(
            String emailID,
            String fromEmailAddress,
            String subject,
            Instant dateSent,
            String snippet
    ) {}

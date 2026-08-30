package com.mail.pulse.dto;

public record EmailParams(
        String toEmailAddress,
        String subject,
        String bodyText
) {}
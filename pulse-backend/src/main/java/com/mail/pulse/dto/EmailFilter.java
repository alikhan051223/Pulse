package com.mail.pulse.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EmailFilter {

    private String folder;
    private String sender;
    private String subject;
    private String recipient;
    private Boolean unread;
    private Boolean hasAttachments;
    private String label;
    private Instant startDate;
    private Instant endDate;
    private String inboxOwner;
}
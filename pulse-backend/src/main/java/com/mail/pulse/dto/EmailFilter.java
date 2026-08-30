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

    // 1. Core Folder Navigation (e.g., "INBOX", "DRAFTS", "TRASH", "SENT", "SPAM")
    private String folder;

    // 3. Targeted Field Searches
    private String sender;
    private String subject;
    private String recipient; // Matches userEmail or CC list

    // 4. Boolean Status Flags (Using Wrapper Objects so they can be null)
    private Boolean unread;
    private Boolean hasAttachments;

    // 5. Categorization & Labels
    private String label; // Matches specific Gmail labels (e.g., "CATEGORY_PROMOTIONS")

    // 6. Range / Date Filtering
    private Instant startDate;
    private Instant endDate;
}
package com.mail.pulse.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "emails")
public class GmailEntity {



    @Id
    private String id;

    private String userEmail;

    private String sender;

    private String subject;

    private String threadId;

    private String snippet;

    private Instant dateSent;

    private String htmlBody;

    private String plainTextBody;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "email_id")
    private List<Attachment> attachments = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "inline_email_id")
    private List<Attachment> inlineAttachments = new ArrayList<>();

}
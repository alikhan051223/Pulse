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



    @Column(columnDefinition = "TEXT")
    private String htmlBody;

    @Column(columnDefinition = "TEXT")
    private String plainTextBody;

    @OneToMany(mappedBy = "email", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Attachment> attachments = new ArrayList<>();

    public void addAttachment(Attachment attachment) {
        attachments.add(attachment);
        attachment.setEmail(this);
    }


    public void removeAttachment(Attachment attachment) {
        attachments.remove(attachment);
        attachment.setEmail(null);
    }
}
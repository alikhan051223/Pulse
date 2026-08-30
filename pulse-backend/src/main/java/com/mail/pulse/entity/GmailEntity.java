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

    private String recipient;

    private String sender;

    private String subject;

    private String threadId;

    private String snippet;

    private Instant dateSent;

    private String historyId;

    private boolean draft;

    private boolean trash;

    private boolean unread;

    private boolean inbox;

    private boolean spam;

    private boolean sent;

    @ElementCollection
    @CollectionTable(name = "email_cc", joinColumns = @JoinColumn(name = "email_id"))
    private List<String> cc = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "email_labels", joinColumns = @JoinColumn(name = "email_id"))
    private List<String> labels = new ArrayList<>();

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
package com.mail.pulse.entity;


import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "attachments")
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String fileName;

    @Column(columnDefinition = "TEXT")
    private String attachmentId;

    private String messageId;

    private Long size;
    private String contentType;
    private String contentId;

    @Column(columnDefinition = "BOOLEAN DEFAULT 0")
    private boolean inline;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "email_id")
    @JsonIgnore
    private GmailEntity email;
}


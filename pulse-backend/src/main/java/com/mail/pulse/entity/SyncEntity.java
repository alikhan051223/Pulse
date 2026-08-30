package com.mail.pulse.entity;


import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigInteger;
import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "sync")
public class SyncEntity {
    @Id
    private String userId;

    private BigInteger lastHistoryId;

    private Instant timeSaved;

}

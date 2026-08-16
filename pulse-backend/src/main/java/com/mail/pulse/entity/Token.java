package com.mail.pulse.entity;


import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "token")
public class Token {


    @Id
    private String userID;

    private String token;

    private Instant savedAt;

    public Token(String userID) {
        this.userID = userID;

    }



}

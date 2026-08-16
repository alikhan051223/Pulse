package com.mail.pulse.repository;


import com.mail.pulse.entity.Token;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TokenRepository extends JpaRepository<Token, String> {


}

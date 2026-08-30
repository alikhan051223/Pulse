package com.mail.pulse.repository;

import com.mail.pulse.entity.GmailEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GmailRepository extends JpaRepository<GmailEntity, String>, JpaSpecificationExecutor<GmailEntity> {

    public void deleteByRecipient(String userEmail);

    public void deleteByRecipientAndDraftTrue(String userEmail);

    Optional<GmailEntity> findByIdAndDraftFalse(String id);
    Optional<GmailEntity> findByIdAndDraftTrue(String id);



}

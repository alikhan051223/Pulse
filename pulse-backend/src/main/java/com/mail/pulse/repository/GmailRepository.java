package com.mail.pulse.repository;

import com.mail.pulse.entity.GmailEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GmailRepository extends JpaRepository<GmailEntity, String>, JpaSpecificationExecutor<GmailEntity> {

    void deleteByInboxOwner(String inboxOwner);

    void deleteByInboxOwnerAndDraftTrue(String inboxOwner);

    boolean existsByIdAndInboxOwner(String id, String inboxOwner);

    Optional<GmailEntity> findByIdAndInboxOwnerAndDraftFalse(String id, String inboxOwner);

    public void deleteBySender(String userEmail);

    Optional<GmailEntity> findByIdAndDraftFalse(String id);
    Optional<GmailEntity> findByIdAndDraftTrue(String id);



}

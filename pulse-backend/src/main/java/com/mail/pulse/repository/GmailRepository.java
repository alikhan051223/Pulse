package com.mail.pulse.repository;

import com.mail.pulse.dto.EmailSummary;
import com.mail.pulse.entity.GmailEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface GmailRepository extends JpaRepository<GmailEntity, String>, JpaSpecificationExecutor<GmailEntity> {



    public default Page<GmailEntity> searchInbox(
            String sender,
            String subject,
            Instant dateSent,
            Pageable pageable) {

        Specification<GmailEntity> spec = Specification.where((Specification<GmailEntity>) null);

        if (sender != null && !sender.isBlank()) {
            spec = spec.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("sender")), "%" + sender.toLowerCase() + "%"));
        }

        if (subject != null && !subject.isBlank()) {
            spec = spec.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("subject")), "%" + subject.toLowerCase() + "%"));
        }

        if (dateSent != null) {
            spec = spec.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("dateSent"), dateSent));
        }

        return findAll(spec, pageable);
    }

    @Query("SELECT new com.mail.pulse.dto.EmailSummary(" +
            "g.id, g.sender, g.subject, g.dateSent, g.snippet) " +
            "FROM GmailEntity g ORDER BY g.dateSent DESC")
    Page<EmailSummary> getSummaries(Pageable pageable);
}

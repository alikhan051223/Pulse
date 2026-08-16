package com.mail.pulse.repository;

import com.mail.pulse.entity.GmailEntity;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface GmailRepository extends JpaRepository<GmailEntity, String>, JpaSpecificationExecutor<GmailEntity> {

    public default List<GmailEntity> searchInbox(String sender, String subject, Instant dateSent) {
        Specification<GmailEntity> spec = Specification.where((root, query, cb) -> cb.conjunction());

        if (sender != null && !sender.isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("sender"), sender));
        }

        if (dateSent != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThan(root.get("dateSent"), dateSent));
        }

        if (subject != null && !subject.isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("subject")), "%" + subject.toLowerCase() + "%"));
        }

        return findAll(spec);
    }
}

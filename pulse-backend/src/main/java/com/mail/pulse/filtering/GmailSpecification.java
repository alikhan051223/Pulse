package com.mail.pulse.filtering;

import com.mail.pulse.dto.EmailFilter;
import com.mail.pulse.entity.GmailEntity;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class GmailSpecification {

    public static Specification<GmailEntity> build(EmailFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getFolder() != null && !filter.getFolder().isBlank()) {
                String folder = filter.getFolder().trim().toUpperCase();
                switch (folder.toUpperCase()) {
                    case "INBOX" -> {
                        predicates.add(cb.equal(root.get("inbox"), true));
                        predicates.add(cb.equal(root.get("trash"), false));
                        predicates.add(cb.equal(root.get("draft"), false));
                        predicates.add(cb.equal(root.get("spam"), false));
                    }
                    case "DRAFTS" -> {
                        predicates.add(cb.equal(root.get("draft"), true));
                        predicates.add(cb.equal(root.get("trash"), false));
                    }
                    case "SENT" -> {
                        predicates.add(cb.equal(root.get("sent"), true));
                        predicates.add(cb.equal(root.get("trash"), false));
                        predicates.add(cb.equal(root.get("draft"), false));
                        predicates.add(cb.equal(root.get("spam"), false));
                    }
                    case "SPAM" -> {
                        predicates.add(cb.equal(root.get("spam"), true));
                        predicates.add(cb.equal(root.get("trash"), false));
                        predicates.add(cb.equal(root.get("inbox"), false));
                    }
                    case "TRASH" -> {
                        predicates.add(cb.equal(root.get("trash"), true));
                    }
                }
            }

            if (filter.getSender() != null && !filter.getSender().isBlank()) {
                String senderTerm = "%" + filter.getSender().trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("sender")), senderTerm));
            }

            if (filter.getSubject() != null && !filter.getSubject().isBlank()) {
                String subjectTerm = "%" + filter.getSubject().trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("subject")), subjectTerm));
            }

            if (filter.getRecipient() != null && !filter.getRecipient().isBlank()) {
                String recipientTerm = "%" + filter.getRecipient().trim().toLowerCase() + "%";

                Predicate userEmailMatch = cb.like(cb.lower(root.get("recipient")), recipientTerm);

                Expression<String> ccExpression = root.joinList("cc", JoinType.LEFT).as(String.class);
                Predicate ccMatch = cb.like(cb.lower(ccExpression), recipientTerm);

                predicates.add(cb.or(userEmailMatch, ccMatch));
            }

            if (filter.getUnread() != null) {
                predicates.add(cb.equal(root.get("unread"), filter.getUnread()));
            }

            if (Boolean.TRUE.equals(filter.getHasAttachments())) {
                predicates.add(cb.isNotEmpty(root.get("attachments")));
            } else if (Boolean.FALSE.equals(filter.getHasAttachments())) {
                predicates.add(cb.isEmpty(root.get("attachments")));
            }

            if (filter.getLabel() != null && !filter.getLabel().isBlank()) {
                var labelsJoin = root.joinList("labels", JoinType.LEFT);
                predicates.add(cb.equal(labelsJoin, filter.getLabel().trim()));
            }

            if (filter.getInboxOwner() != null && !filter.getInboxOwner().isBlank()) {
                predicates.add(cb.equal(root.get("inboxOwner"), filter.getInboxOwner().trim()));
            }

            // 7. Dynamic Date Range Filtering
            if (filter.getStartDate() != null && filter.getEndDate() != null) {
                predicates.add(cb.between(root.get("dateSent"), filter.getStartDate(), filter.getEndDate()));
            } else if (filter.getStartDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dateSent"), filter.getStartDate()));
            } else if (filter.getEndDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dateSent"), filter.getEndDate()));
            }

            query.distinct(true);

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
package com.mail.pulse.service;

import com.mail.pulse.repository.GmailRepository;
import com.mail.pulse.repository.SyncRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClearDatabase {

    private final GmailRepository gmailRepository;
    private final SyncRepository syncRepository;

    public ClearDatabase(GmailRepository gmailRepository, SyncRepository syncRepository) {
        this.gmailRepository = gmailRepository;
        this.syncRepository = syncRepository;
    }

    @Transactional
    public void clearUserData(String userEmail) {
        gmailRepository.deleteByInboxOwner(userEmail);
        syncRepository.deleteByUserId(userEmail);
    }

    @Transactional
    public void clearDraftsForUser(String userEmail) {
        gmailRepository.deleteByInboxOwnerAndDraftTrue(userEmail);
    }

}

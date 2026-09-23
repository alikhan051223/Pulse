package com.mail.pulse.repository;

import com.mail.pulse.entity.SyncEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SyncRepository extends JpaRepository<SyncEntity,String> {
    Optional<SyncEntity> findFirstByUserIdOrderByTimeSavedDesc(String userId);
    public void deleteByUserId(String userId);
}

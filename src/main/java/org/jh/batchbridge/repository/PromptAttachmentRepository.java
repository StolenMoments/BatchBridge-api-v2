package org.jh.batchbridge.repository;

import org.jh.batchbridge.domain.PromptAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromptAttachmentRepository extends JpaRepository<PromptAttachment, Long> {
}

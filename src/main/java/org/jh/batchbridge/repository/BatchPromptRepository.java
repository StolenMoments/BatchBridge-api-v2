package org.jh.batchbridge.repository;

import java.util.List;
import org.jh.batchbridge.domain.BatchPrompt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BatchPromptRepository extends JpaRepository<BatchPrompt, Long> {
    List<BatchPrompt> findAllByBatchId(Long batchId);
}

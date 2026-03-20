package org.jh.batchbridge.repository;

import java.util.List;
import java.util.Optional;
import org.jh.batchbridge.domain.BatchPrompt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BatchPromptRepository extends JpaRepository<BatchPrompt, Long> {
    List<BatchPrompt> findAllByBatchId(Long batchId);
    Optional<BatchPrompt> findByIdAndBatchId(Long id, Long batchId);
    long countByBatchId(Long batchId);
}

package org.jh.batchbridge.repository;

import java.util.List;
import org.jh.batchbridge.domain.PromptTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromptTemplateRepository extends JpaRepository<PromptTemplate, Long> {

    List<PromptTemplate> findAllByOrderByCreatedAtDesc();
}

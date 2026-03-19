package org.jh.batchbridge.repository;

import java.util.List;
import org.jh.batchbridge.domain.Batch;
import org.jh.batchbridge.domain.BatchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BatchRepository extends JpaRepository<Batch, Long> {

    List<Batch> findAllByStatus(BatchStatus status);

    Page<Batch> findAllByStatus(BatchStatus status, Pageable pageable);

    Page<Batch> findByStatus(BatchStatus status, Pageable pageable);
}

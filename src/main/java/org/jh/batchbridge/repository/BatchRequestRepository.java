package org.jh.batchbridge.repository;

import java.util.List;
import org.jh.batchbridge.domain.BatchRequest;
import org.jh.batchbridge.domain.BatchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BatchRequestRepository extends JpaRepository<BatchRequest, Long> {

    List<BatchRequest> findAllByStatus(BatchStatus status);

    Page<BatchRequest> findAllByStatus(BatchStatus status, Pageable pageable);
}

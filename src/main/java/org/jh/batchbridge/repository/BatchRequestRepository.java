package org.jh.batchbridge.repository;

import java.util.List;
import org.jh.batchbridge.domain.BatchRequest;
import org.jh.batchbridge.domain.BatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BatchRequestRepository extends JpaRepository<BatchRequest, Long> {

    List<BatchRequest> findAllByStatus(BatchStatus status);
}

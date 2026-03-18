package org.jh.batchbridge.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.jh.batchbridge.domain.BatchRequest;
import org.jh.batchbridge.domain.BatchStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BatchRequestRepositoryTest {

    @Autowired
    private BatchRequestRepository batchRequestRepository;

    @Test
    void findAllByStatusReturnsEmptyListWhenNoInProgressRequestsExist() {
        List<BatchRequest> result = batchRequestRepository.findAllByStatus(BatchStatus.IN_PROGRESS);
        assertThat(result).isEmpty();
    }
}

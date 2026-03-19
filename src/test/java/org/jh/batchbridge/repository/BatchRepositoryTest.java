package org.jh.batchbridge.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.jh.batchbridge.domain.Batch;
import org.jh.batchbridge.domain.BatchStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BatchRepositoryTest {

    @Autowired
    private BatchRepository batchRepository;

    @Test
    void findAllByStatusReturnsEmptyListWhenNoInProgressRequestsExist() {
        List<Batch> result = batchRepository.findAllByStatus(BatchStatus.IN_PROGRESS);
        assertThat(result).isEmpty();
    }
}

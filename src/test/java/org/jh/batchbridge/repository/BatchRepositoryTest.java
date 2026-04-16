package org.jh.batchbridge.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jh.batchbridge.domain.Batch;
import org.jh.batchbridge.domain.BatchPrompt;
import org.jh.batchbridge.domain.BatchStatus;
import org.jh.batchbridge.domain.PromptResult;
import org.jh.batchbridge.repository.BatchSummaryView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class BatchRepositoryTest {

    @Autowired
    private BatchRepository batchRepository;

    @Test
    void findAllByStatusReturnsEmptyListWhenNoInProgressRequestsExist() {
        List<Batch> result = batchRepository.findAllByStatus(BatchStatus.IN_PROGRESS);
        assertThat(result).isEmpty();
    }

    @Test
    void findBatchSummariesExcludesDeletedBatches() {
        Batch b1 = Batch.createDraft("b1", "model");
        Batch b2 = Batch.createDraft("b2", "model");
        b2.delete();

        batchRepository.save(b1);
        batchRepository.save(b2);

        var page = batchRepository.findBatchSummaries(null, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getLabel()).isEqualTo("b1");
    }

    @Test
    void findBatchSummariesReturnsAggregatedPromptCount() {
        Batch draft = Batch.createDraft("draft", "claude-3-5-sonnet-20240620");
        draft.addPrompt(BatchPrompt.create("p1", null, "u1"));
        draft.addPrompt(BatchPrompt.create("p2", null, "u2"));

        Batch inProgress = Batch.createDraft("in-progress", "claude-3-5-sonnet-20240620");
        inProgress.addPrompt(BatchPrompt.create("p3", null, "u3"));
        inProgress.submit("ext-1");

        batchRepository.save(draft);
        batchRepository.save(inProgress);

        var page = batchRepository.findBatchSummaries(BatchStatus.DRAFT, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getLabel()).isEqualTo("draft");
        assertThat(page.getContent().get(0).getPromptCount()).isEqualTo(2L);
    }

    @Test
    void findBatchSummariesReturnsSuccessAndFailedCounts() {
        Batch batch = Batch.createDraft("counts", "claude-3-5-sonnet-20240620");
        batch.addPrompt(BatchPrompt.create("p1", null, "u1"));
        batch.addPrompt(BatchPrompt.create("p2", null, "u2"));
        batch.addPrompt(BatchPrompt.create("p3", null, "u3"));
        batch.submit("ext-counts");
        batchRepository.saveAndFlush(batch);

        List<BatchPrompt> prompts = batch.getPrompts();
        Map<Long, PromptResult> results = new HashMap<>();
        results.put(prompts.get(0).getId(), new PromptResult(true, "ok1", null, null));
        results.put(prompts.get(1).getId(), new PromptResult(true, "ok2", null, null));
        // p3는 map에 없음 → fail 처리

        batch.complete(results);
        batchRepository.saveAndFlush(batch);

        var page = batchRepository.findBatchSummaries(null, PageRequest.of(0, 20));

        BatchSummaryView view = page.getContent().stream()
                .filter(v -> v.getLabel().equals("counts"))
                .findFirst().orElseThrow();
        assertThat(view.getSuccessCount()).isEqualTo(2L);
        assertThat(view.getFailedCount()).isEqualTo(1L);
        assertThat(view.getPromptCount()).isEqualTo(3L);
    }
}

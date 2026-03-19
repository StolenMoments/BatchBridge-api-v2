package org.jh.batchbridge.adapter;

import java.util.List;
import java.util.Map;
import org.jh.batchbridge.domain.BatchPrompt;
import org.jh.batchbridge.domain.PromptResult;
import org.jh.batchbridge.dto.external.BatchStatusResult;
import org.jh.batchbridge.dto.external.BatchSubmitRequest;
import org.jh.batchbridge.dto.external.ExternalBatchId;

public interface BatchApiPort {

    String getSupportedModelPrefix();

    ExternalBatchId submitBatch(BatchSubmitRequest request);

    BatchStatusResult fetchStatus(ExternalBatchId externalBatchId);

    Map<Long, PromptResult> fetchResults(ExternalBatchId externalBatchId, List<BatchPrompt> prompts);
}

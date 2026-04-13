package org.jh.batchbridge.adapter;

import java.util.List;
import java.util.Map;
import org.jh.batchbridge.domain.BatchPrompt;
import org.jh.batchbridge.domain.PromptResult;
import org.jh.batchbridge.domain.PromptType;
import org.jh.batchbridge.dto.external.BatchStatusResult;
import org.jh.batchbridge.dto.external.BatchSubmitRequest;
import org.jh.batchbridge.dto.external.ExternalBatchId;
import org.jh.batchbridge.dto.response.ModelInfo;

public interface BatchApiPort {

    String getSupportedModelPrefix();

    boolean supportsPromptType(PromptType promptType);

    ExternalBatchId submitBatch(BatchSubmitRequest request);

    BatchStatusResult fetchStatus(ExternalBatchId externalBatchId);

    Map<Long, PromptResult> fetchResults(ExternalBatchId externalBatchId, List<BatchPrompt> prompts);

    List<ModelInfo> fetchSupportedModels();
}

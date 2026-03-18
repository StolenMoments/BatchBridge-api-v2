package org.jh.batchbridge.adapter;

import org.jh.batchbridge.dto.external.BatchStatusResult;
import org.jh.batchbridge.dto.external.BatchSubmitRequest;
import org.jh.batchbridge.dto.external.ExternalBatchId;

public interface BatchApiPort {

    String getSupportedModelPrefix();

    ExternalBatchId submitBatch(BatchSubmitRequest request);

    BatchStatusResult fetchStatus(ExternalBatchId externalBatchId);

    String fetchResult(ExternalBatchId externalBatchId);
}

package org.jh.batchbridge.factory;

import java.util.List;
import java.util.Locale;
import org.jh.batchbridge.adapter.BatchApiPort;
import org.jh.batchbridge.exception.UnsupportedModelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BatchApiClientFactory {

    private static final Logger log = LoggerFactory.getLogger(BatchApiClientFactory.class);
    private final List<BatchApiPort> adapters;

    public BatchApiClientFactory(List<BatchApiPort> adapters) {
        if (adapters == null || adapters.isEmpty()) {
            log.warn("No BatchApiPort adapters found. Batch requests will fail.");
        }
        this.adapters = adapters;
    }

    public BatchApiPort getAdapter(String model) {
        String normalized = model == null ? "" : model.trim().toLowerCase(Locale.ROOT);

        return adapters.stream()
                .filter(adapter -> normalized.startsWith(adapter.getSupportedModelPrefix()))
                .findFirst()
                .orElseThrow(() -> new UnsupportedModelException(model));
    }
}

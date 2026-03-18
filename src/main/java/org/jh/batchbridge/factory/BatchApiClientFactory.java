package org.jh.batchbridge.factory;

import java.util.List;
import java.util.Locale;
import org.jh.batchbridge.adapter.BatchApiPort;
import org.jh.batchbridge.exception.UnsupportedModelException;
import org.springframework.stereotype.Component;

@Component
public class BatchApiClientFactory {

    private final List<BatchApiPort> adapters;

    public BatchApiClientFactory(List<BatchApiPort> adapters) {
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

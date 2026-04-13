package org.jh.batchbridge.exception;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExceptionTestController {

    @GetMapping("/test/batches/{id}")
    String batch(@PathVariable Long id) {
        throw new BatchNotFoundException(id);
    }

    @GetMapping("/test/results/{id}")
    String result(@PathVariable Long id) {
        throw new BatchResultNotFoundException(id);
    }

    @GetMapping("/test/models/unsupported")
    String unsupportedModel() {
        throw new UnsupportedModelException("gemini-2.0-flash");
    }

    @GetMapping("/test/batches/not-editable")
    String notEditableBatch() {
        throw new BatchNotEditableException("Batch is not editable");
    }

    @GetMapping("/test/batches/empty")
    String emptyBatch() {
        throw new BatchEmptyException("Batch has no prompts");
    }

    @GetMapping("/test/external-api")
    String externalApi() {
        throw new ExternalApiException("External API call failed.");
    }

    @GetMapping("/test/media/{batchId}/{promptId}")
    String media(@PathVariable Long batchId, @PathVariable Long promptId) {
        throw new MediaNotFoundException(batchId, promptId);
    }
}

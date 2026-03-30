package org.jh.batchbridge.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.jh.batchbridge.dto.external.BatchSubmitRequest;
import org.junit.jupiter.api.Test;

class XAiBatchAdapterTest {

    private final XAiBatchAdapter adapter = new XAiBatchAdapter(
            "test-api-key",
            "https://api.x.ai",
            10000,
            30000
    );

    @Test
    @SuppressWarnings("unchecked")
    void buildBatchRequests_correctlyMapsPrompts() {
        BatchSubmitRequest request = new BatchSubmitRequest("test-label", "grok-2", List.of(
                new BatchSubmitRequest.PromptItem(101L, "System context", "User question 1", List.of()),
                new BatchSubmitRequest.PromptItem(102L, null, "User question 2", List.of())
        ));

        List<Map<String, Object>> batchRequests = adapter.buildBatchRequests(request);

        assertThat(batchRequests).hasSize(2);

        // First prompt
        Map<String, Object> item1 = batchRequests.get(0);
        assertThat(item1.get("batch_request_id")).isEqualTo("101");
        Map<String, Object> req1 = (Map<String, Object>) item1.get("batch_request");
        Map<String, Object> completion1 = (Map<String, Object>) req1.get("chat_get_completion");
        assertThat(completion1.get("model")).isEqualTo("grok-2");
        List<Map<String, String>> messages1 = (List<Map<String, String>>) completion1.get("messages");
        assertThat(messages1).hasSize(2);
        assertThat(messages1.get(0)).containsEntry("role", "system").containsEntry("content", "System context");
        assertThat(messages1.get(1)).containsEntry("role", "user").containsEntry("content", "User question 1");

        // Second prompt
        Map<String, Object> item2 = batchRequests.get(1);
        assertThat(item2.get("batch_request_id")).isEqualTo("102");
        Map<String, Object> req2 = (Map<String, Object>) item2.get("batch_request");
        Map<String, Object> completion2 = (Map<String, Object>) req2.get("chat_get_completion");
        assertThat(completion2.get("model")).isEqualTo("grok-2");
        List<Map<String, String>> messages2 = (List<Map<String, String>>) completion2.get("messages");
        assertThat(messages2).hasSize(1);
        assertThat(messages2.get(0)).containsEntry("role", "user").containsEntry("content", "User question 2");
    }
}

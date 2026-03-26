package org.jh.batchbridge.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import org.jh.batchbridge.domain.PromptResult;
import org.junit.jupiter.api.Test;

class ClaudeBatchAdapterTest {

    private final ClaudeBatchAdapter adapter = new ClaudeBatchAdapter(
            "test-api-key",
            "message-batches-2024-09-24",
            10000,
            30000,
            1024,
            new ObjectMapper()
    );

    @Test
    void parseResults_parsesSucceededAndErroredResults() {
        String jsonlBody = """
                {"custom_id":"101","result":{"type":"succeeded","message":{"content":[{"type":"text","text":"hello 한글"}]}}}
                {"custom_id":"102","result":{"type":"errored","error":{"message":"bad request"}}}
                {"custom_id":"23","result":{"type":"errored","error":{"type":"error","error":{"details":{"error_visibility":"user_facing"},"type":"invalid_request_error","message":"max_tokens: 100000 > 64000, which is the maximum allowed number of output tokens for claude-haiku-4-5-20251001"},"request_id":null}}}
                {"custom_id":"24","result":{"type":"errored","error":{"type":"overloaded_error","message":"Overloaded"}}}
                """;

        Map<Long, PromptResult> results = adapter.parseResults(jsonlBody, Set.of(101L, 102L, 23L, 24L));

        assertThat(results).hasSize(4);
        assertThat(results.get(101L)).isEqualTo(new PromptResult(true, "hello 한글", null));
        assertThat(results.get(102L)).isEqualTo(new PromptResult(false, null, "bad request"));
        assertThat(results.get(23L)).isEqualTo(new PromptResult(false, null, "max_tokens: 100000 > 64000, which is the maximum allowed number of output tokens for claude-haiku-4-5-20251001"));
        assertThat(results.get(24L)).isEqualTo(new PromptResult(false, null, "Overloaded"));
    }

    @Test
    void getBaseName_IdentifiesBaseNameWithoutDate() {
        // 기존 테스트 케이스
        assertThat(adapter.getBaseName("claude-3-5-sonnet-20240620")).isEqualTo("claude-3-5-sonnet");
        assertThat(adapter.getBaseName("claude-3-5-sonnet-20241022")).isEqualTo("claude-3-5-sonnet");
        assertThat(adapter.getBaseName("claude-3-opus-20240229")).isEqualTo("claude-3-opus");
        assertThat(adapter.getBaseName("claude-2.1")).isEqualTo("claude-2.1");
        
        // 추가된 테스트 케이스 (사용자 리포트 기반)
        assertThat(adapter.getBaseName("claude-sonnet-4-5-20250929")).isEqualTo("claude-sonnet");
        assertThat(adapter.getBaseName("claude-sonnet-4-20250514")).isEqualTo("claude-sonnet");
        assertThat(adapter.getBaseName("claude-3-haiku-20240307")).isEqualTo("claude-3-haiku");
        assertThat(adapter.getBaseName("claude-opus-4-5-20251101")).isEqualTo("claude-opus");
        assertThat(adapter.getBaseName("claude-haiku-4-5-20251001")).isEqualTo("claude-haiku");
        assertThat(adapter.getBaseName("claude-opus-4-1-20250805")).isEqualTo("claude-opus");
        assertThat(adapter.getBaseName("claude-opus-4-20250514")).isEqualTo("claude-opus");
        
        // -latest 처리 확인
        assertThat(adapter.getBaseName("claude-3-5-sonnet-latest")).isEqualTo("claude-3-5-sonnet");
        assertThat(adapter.getBaseName("claude-3-7-sonnet-latest")).isEqualTo("claude-3-7-sonnet");
    }

    @Test
    void filterLatestModels_SelectsCorrectModels() {
        // Given: The models reported by the user
        var models = java.util.List.of(
            new ClaudeModelData("claude-sonnet-4-5-20250929", "Claude Sonnet 4.5"),
            new ClaudeModelData("claude-sonnet-4-20250514", "Claude Sonnet 4"),
            new ClaudeModelData("claude-3-haiku-20240307", "Claude Haiku 3"),
            new ClaudeModelData("claude-sonnet-4-6", "Claude Sonnet 4.6"),
            new ClaudeModelData("claude-opus-4-5-20251101", "Claude Opus 4.5"),
            new ClaudeModelData("claude-opus-4-6", "Claude Opus 4.6"),
            new ClaudeModelData("claude-haiku-4-5-20251001", "Claude Haiku 4.5"),
            new ClaudeModelData("claude-opus-4-1-20250805", "Claude Opus 4.1"),
            new ClaudeModelData("claude-opus-4-20250514", "Claude Opus 4"),
            new ClaudeModelData("grok-3", "Grok 3"),
            new ClaudeModelData("gemini-2.0-flash", "Gemini 2.0 Flash")
        );

        // When: Filtering logic
        java.util.Map<String, org.jh.batchbridge.dto.response.ModelInfo> latestModels = new java.util.HashMap<>();
        for (ClaudeModelData data : models) {
            String id = data.id();
            String baseName = adapter.getBaseName(id);
            org.jh.batchbridge.dto.response.ModelInfo current = new org.jh.batchbridge.dto.response.ModelInfo(id, data.displayName());
            org.jh.batchbridge.dto.response.ModelInfo existing = latestModels.get(baseName);
            if (existing == null || id.compareTo(existing.id()) > 0) {
                latestModels.put(baseName, current);
            }
        }

        // Then: Should only contain the latest versions
        assertThat(latestModels.get("claude-sonnet").id()).isEqualTo("claude-sonnet-4-6");
        assertThat(latestModels.get("claude-opus").id()).isEqualTo("claude-opus-4-6");
        assertThat(latestModels.get("claude-haiku").id()).isEqualTo("claude-haiku-4-5-20251001");
        assertThat(latestModels.get("claude-3-haiku").id()).isEqualTo("claude-3-haiku-20240307");
    }

    // Helper record to match the one in ClaudeBatchAdapter
    private record ClaudeModelData(String id, String displayName) {}
}

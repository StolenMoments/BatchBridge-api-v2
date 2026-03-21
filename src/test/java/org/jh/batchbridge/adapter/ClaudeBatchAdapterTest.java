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
                """;

        Map<Long, PromptResult> results = adapter.parseResults(jsonlBody, Set.of(101L, 102L));

        assertThat(results).hasSize(2);
        assertThat(results.get(101L)).isEqualTo(new PromptResult(true, "hello 한글", null));
        assertThat(results.get(102L)).isEqualTo(new PromptResult(false, null, "bad request"));
    }

    @Test
    void getBaseName_IdentifiesBaseNameWithoutDate() {
        // 기존 테스트 케이스
        assertThat(adapter.getBaseName("claude-3-5-sonnet-20240620")).isEqualTo("claude-3-5-sonnet");
        assertThat(adapter.getBaseName("claude-3-5-sonnet-20241022")).isEqualTo("claude-3-5-sonnet");
        assertThat(adapter.getBaseName("claude-3-opus-20240229")).isEqualTo("claude-3-opus");
        assertThat(adapter.getBaseName("claude-2.1")).isEqualTo("claude-2.1");
        
        // 추가된 테스트 케이스 (사용자 리포트 기반)
        assertThat(adapter.getBaseName("claude-sonnet-4-5-20250929")).isEqualTo("claude-sonnet-4-5");
        assertThat(adapter.getBaseName("claude-sonnet-4-20250514")).isEqualTo("claude-sonnet-4");
        assertThat(adapter.getBaseName("claude-3-haiku-20240307")).isEqualTo("claude-3-haiku");
        assertThat(adapter.getBaseName("claude-opus-4-5-20251101")).isEqualTo("claude-opus-4-5");
        assertThat(adapter.getBaseName("claude-haiku-4-5-20251001")).isEqualTo("claude-haiku-4-5");
        assertThat(adapter.getBaseName("claude-opus-4-1-20250805")).isEqualTo("claude-opus-4-1");
        assertThat(adapter.getBaseName("claude-opus-4-20250514")).isEqualTo("claude-opus-4");
        
        // -latest 처리 확인
        assertThat(adapter.getBaseName("claude-3-5-sonnet-latest")).isEqualTo("claude-3-5-sonnet");
        assertThat(adapter.getBaseName("claude-3-7-sonnet-latest")).isEqualTo("claude-3-7-sonnet");
    }
}

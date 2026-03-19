package org.jh.batchbridge.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.jh.batchbridge.domain.PromptResult;
import org.junit.jupiter.api.Test;

class ClaudeBatchAdapterTest {

    private final ClaudeBatchAdapter adapter = new ClaudeBatchAdapter("test-api-key", 1024);

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
    void parseResults_returnsEmptyWhenNoValidResultIsParsed() {
        String jsonlBody = """
                {"custom_id":"abc","result":{"type":"succeeded","message":{"content":[{"type":"text","text":"ignored"}]}}}
                {"custom_id":"999","result":{"type":"succeeded","message":{"content":[{"type":"text","text":"ignored"}]}}}
                """;

        Map<Long, PromptResult> results = adapter.parseResults(jsonlBody, Set.of(101L));

        assertThat(results).isEmpty();
    }
}

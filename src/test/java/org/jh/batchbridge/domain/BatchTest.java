package org.jh.batchbridge.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BatchTest {

    @DisplayName("isEditable returns true only when status is DRAFT")
    @Test
    void isEditable() {
        Batch batch = new Batch("test", "claude");
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.DRAFT);
        assertThat(batch.isEditable()).isTrue();

        batch.submit("ext-123");
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.IN_PROGRESS);
        assertThat(batch.isEditable()).isFalse();
    }

    @DisplayName("submit updates status and externalBatchId")
    @Test
    void submit() {
        Batch batch = new Batch("test", "claude");
        batch.submit("ext-123");

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.IN_PROGRESS);
        assertThat(batch.getExternalBatchId()).isEqualTo("ext-123");
        assertThat(batch.getSubmittedAt()).isNotNull();
    }

    @DisplayName("submit throws exception if not DRAFT")
    @Test
    void submit_fail() {
        Batch batch = new Batch("test", "claude");
        batch.submit("ext-1");
        
        assertThatThrownBy(() -> batch.submit("ext-2"))
                .isInstanceOf(IllegalStateException.class);
    }

    @DisplayName("complete with map updates prompts and batch status")
    @Test
    void complete_map() {
        Batch batch = new Batch("test", "claude");
        BatchPrompt prompt1 = new BatchPrompt("p1", "sys", "user");
        batch.addPrompt(prompt1);
        
        // Simulate persistence (id needed for map key)
        // Since we can't easily set ID on entity without reflection or repository, 
        // and map uses ID... wait.
        // PromptResult map is keyed by prompt ID.
        // If I can't set ID, I can't test map logic easily without mocking or reflection.
        // Let's use reflection to set ID for test.
        setField(prompt1, "id", 1L);

        batch.submit("ext-1");

        PromptResult result = new PromptResult(true, "response", null);
        Map<Long, PromptResult> results = Map.of(1L, result);

        batch.complete(results);

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(batch.getCompletedAt()).isNotNull();
        assertThat(prompt1.getStatus()).isEqualTo(PromptStatus.COMPLETED);
        assertThat(prompt1.getResponseContent()).isEqualTo("response");
    }

    @DisplayName("fail updates batch and prompts status")
    @Test
    void fail() {
        Batch batch = new Batch("test", "claude");
        BatchPrompt prompt1 = new BatchPrompt("p1", "sys", "user");
        batch.addPrompt(prompt1);
        batch.submit("ext-1");

        batch.fail("error");

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(batch.getErrorMessage()).isEqualTo("error");
        assertThat(batch.getCompletedAt()).isNotNull();
        assertThat(prompt1.getStatus()).isEqualTo(PromptStatus.FAILED);
        assertThat(prompt1.getErrorMessage()).isEqualTo("error");
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

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
        Batch batch = Batch.createDraft("test", "claude");
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.DRAFT);
        assertThat(batch.isEditable()).isTrue();

        batch.submit("ext-123");
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.IN_PROGRESS);
        assertThat(batch.isEditable()).isFalse();
    }

    @DisplayName("submit updates status and externalBatchId")
    @Test
    void submit() {
        Batch batch = Batch.createDraft("test", "claude");
        batch.submit("ext-123");

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.IN_PROGRESS);
        assertThat(batch.getExternalBatchId()).isEqualTo("ext-123");
        assertThat(batch.getSubmittedAt()).isNotNull();
    }

    @DisplayName("submit throws exception if not DRAFT")
    @Test
    void submit_fail() {
        Batch batch = Batch.createDraft("test", "claude");
        batch.submit("ext-1");
        
        assertThatThrownBy(() -> batch.submit("ext-2"))
                .isInstanceOf(IllegalStateException.class);
    }

    @DisplayName("complete with map updates prompts and batch status")
    @Test
    void complete_map() {
        Batch batch = Batch.createDraft("test", "claude");
        BatchPrompt prompt1 = BatchPrompt.create("p1", "sys", "user");
        batch.addPrompt(prompt1);
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

    @DisplayName("complete marks prompts as failed when result is missing")
    @Test
    void complete_marksPromptFailedWhenResultMissing() {
        Batch batch = Batch.createDraft("test", "claude");
        BatchPrompt prompt1 = BatchPrompt.create("p1", "sys", "user");
        BatchPrompt prompt2 = BatchPrompt.create("p2", "sys", "user");
        batch.addPrompt(prompt1);
        batch.addPrompt(prompt2);
        setField(prompt1, "id", 1L);
        setField(prompt2, "id", 2L);
        batch.submit("ext-1");

        batch.complete(Map.of(1L, new PromptResult(true, "ok", null)));

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(prompt1.getStatus()).isEqualTo(PromptStatus.COMPLETED);
        assertThat(prompt2.getStatus()).isEqualTo(PromptStatus.FAILED);
        assertThat(prompt2.getErrorMessage()).isEqualTo("No result found for prompt");
    }

    @DisplayName("complete continues processing when a prompt throws")
    @Test
    void complete_continuesWhenPromptProcessingThrows() {
        Batch batch = Batch.createDraft("test", "claude");
        BatchPrompt faultyPrompt = FaultyBatchPrompt.create("faulty", "sys", "user");
        BatchPrompt healthyPrompt = BatchPrompt.create("healthy", "sys", "user");
        batch.addPrompt(faultyPrompt);
        batch.addPrompt(healthyPrompt);
        setField(faultyPrompt, "id", 1L);
        setField(healthyPrompt, "id", 2L);
        batch.submit("ext-1");

        batch.complete(Map.of(
                1L, new PromptResult(true, "ignored", null),
                2L, new PromptResult(true, "done", null)
        ));

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(faultyPrompt.getStatus()).isEqualTo(PromptStatus.FAILED);
        assertThat(faultyPrompt.getErrorMessage()).isEqualTo("Failed to process prompt result");
        assertThat(healthyPrompt.getStatus()).isEqualTo(PromptStatus.COMPLETED);
        assertThat(healthyPrompt.getResponseContent()).isEqualTo("done");
    }

    @DisplayName("fail updates batch and prompts status")
    @Test
    void fail() {
        Batch batch = Batch.createDraft("test", "claude");
        BatchPrompt prompt1 = BatchPrompt.create("p1", "sys", "user");
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
            Class<?> type = target.getClass();
            java.lang.reflect.Field field = null;
            while (type != null) {
                try {
                    field = type.getDeclaredField(fieldName);
                    break;
                } catch (NoSuchFieldException ignored) {
                    type = type.getSuperclass();
                }
            }
            if (field == null) {
                throw new NoSuchFieldException(fieldName);
            }
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static class FaultyBatchPrompt extends BatchPrompt {

        public static FaultyBatchPrompt create(String label, String systemPrompt, String userPrompt) {
            BatchPrompt bp = BatchPrompt.builder()
                    .label(label)
                    .systemPrompt(systemPrompt)
                    .userPrompt(userPrompt)
                    .status(PromptStatus.PENDING)
                    .build();
            
            FaultyBatchPrompt fbp = new FaultyBatchPrompt();
            setFieldStatic(fbp, "label", bp.getLabel());
            setFieldStatic(fbp, "systemPrompt", bp.getSystemPrompt());
            setFieldStatic(fbp, "userPrompt", bp.getUserPrompt());
            setFieldStatic(fbp, "status", bp.getStatus());
            return fbp;
        }

        private static void setFieldStatic(Object target, String fieldName, Object value) {
            try {
                java.lang.reflect.Field field = BatchPrompt.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void complete(String responseContent) {
            throw new RuntimeException("forced test failure");
        }
    }
}

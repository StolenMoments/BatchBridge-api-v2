package org.jh.batchbridge.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BatchTest {

    @DisplayName("isEditable returns true only when status is DRAFT and not deleted")
    @Test
    void isEditable() {
        Batch batch = Batch.createDraft("test", "claude");
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.DRAFT);
        assertThat(batch.isEditable()).isTrue();

        batch.delete();
        assertThat(batch.isEditable()).isFalse();
        assertThat(batch.getDeletedAt()).isNotNull();

        Batch batch2 = Batch.createDraft("test", "claude");
        batch2.submit("ext-123");
        assertThat(batch2.getStatus()).isEqualTo(BatchStatus.IN_PROGRESS);
        assertThat(batch2.isEditable()).isFalse();
    }

    @DisplayName("delete() sets deletedAt only when DRAFT")
    @Test
    void delete() {
        Batch batch = Batch.createDraft("test", "claude");
        batch.delete();
        assertThat(batch.getDeletedAt()).isNotNull();

        Batch batchInProgress = Batch.createDraft("test", "claude");
        batchInProgress.submit("ext-123");
        assertThatThrownBy(batchInProgress::delete)
                .isInstanceOf(IllegalStateException.class);
    }

    @DisplayName("submit() throws exception if deleted")
    @Test
    void submit_deleted() {
        Batch batch = Batch.createDraft("test", "claude");
        batch.delete();

        assertThatThrownBy(() -> batch.submit("ext-123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Deleted batches cannot be submitted.");
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

        PromptResult result = new PromptResult(true, "response", null, null);
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

        batch.complete(Map.of(1L, new PromptResult(true, "ok", null, null)));

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
                1L, new PromptResult(true, "ignored", null, null),
                2L, new PromptResult(true, "done", null, null)
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

    @DisplayName("update sets label and model for DRAFT batch")
    @Test
    void update_UpdatesLabelAndModel() {
        Batch batch = Batch.createDraft("old-label", "old-model");

        batch.update("new-label", "new-model");

        assertThat(batch.getLabel()).isEqualTo("new-label");
        assertThat(batch.getModel()).isEqualTo("new-model");
    }

    @DisplayName("update with only label updates label, keeps model")
    @Test
    void update_OnlyLabel_KeepsModel() {
        Batch batch = Batch.createDraft("old-label", "keep-model");

        batch.update("new-label", null);

        assertThat(batch.getLabel()).isEqualTo("new-label");
        assertThat(batch.getModel()).isEqualTo("keep-model");
    }

    @DisplayName("update with only model updates model, keeps label")
    @Test
    void update_OnlyModel_KeepsLabel() {
        Batch batch = Batch.createDraft("keep-label", "old-model");

        batch.update(null, "new-model");

        assertThat(batch.getLabel()).isEqualTo("keep-label");
        assertThat(batch.getModel()).isEqualTo("new-model");
    }

    @DisplayName("update throws exception when batch is deleted")
    @Test
    void update_DeletedBatch_ThrowsException() {
        Batch batch = Batch.createDraft("label", "model");
        batch.delete();

        assertThatThrownBy(() -> batch.update("new", null))
                .isInstanceOf(org.jh.batchbridge.exception.BatchNotEditableException.class);
    }

    @DisplayName("update throws exception when batch is not DRAFT")
    @Test
    void update_InProgressBatch_ThrowsException() {
        Batch batch = Batch.createDraft("label", "model");
        batch.submit("ext-1");

        assertThatThrownBy(() -> batch.update("new", null))
                .isInstanceOf(org.jh.batchbridge.exception.BatchNotEditableException.class);
    }

    @DisplayName("complete with null results map marks all prompts as failed")
    @Test
    void complete_NullResultsMap_AllPromptsMarkedFailed() {
        Batch batch = Batch.createDraft("test", "claude");
        BatchPrompt prompt = BatchPrompt.create("p1", "sys", "user");
        batch.addPrompt(prompt);
        setField(prompt, "id", 1L);
        batch.submit("ext-1");

        batch.complete(null);

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(prompt.getStatus()).isEqualTo(PromptStatus.FAILED);
        assertThat(prompt.getErrorMessage()).isEqualTo("No result found for prompt");
    }

    @DisplayName("fail is idempotent when batch is already FAILED")
    @Test
    void fail_AlreadyFailed_IsIdempotent() {
        Batch batch = Batch.createDraft("test", "claude");
        batch.submit("ext-1");
        batch.fail("first error");

        batch.fail("second error");

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(batch.getErrorMessage()).isEqualTo("first error");
    }

    @DisplayName("fail is idempotent when batch is already COMPLETED")
    @Test
    void fail_AlreadyCompleted_IsIdempotent() {
        Batch batch = Batch.createDraft("test", "claude");
        BatchPrompt prompt = BatchPrompt.create("p1", "sys", "user");
        batch.addPrompt(prompt);
        setField(prompt, "id", 1L);
        batch.submit("ext-1");
        batch.complete(Map.of(1L, new PromptResult(true, "ok", null, null)));

        batch.fail("late error");

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }

    @DisplayName("delete is idempotent when already deleted")
    @Test
    void delete_AlreadyDeleted_IsIdempotent() {
        Batch batch = Batch.createDraft("test", "claude");
        batch.delete();

        batch.delete(); // 두 번 호출해도 예외 없음

        assertThat(batch.getDeletedAt()).isNotNull();
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

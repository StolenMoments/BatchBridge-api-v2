package org.jh.batchbridge.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ExceptionTestController.class)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returns404ForBatchNotFoundException() throws Exception {
        mockMvc.perform(get("/test/batches/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error.code").value("BATCH_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Batch not found: 1"));
    }

    @Test
    void returns404ForBatchResultNotFoundException() throws Exception {
        mockMvc.perform(get("/test/results/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error.code").value("BATCH_RESULT_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Batch result not found: 1"));
    }

    @Test
    void returns400ForUnsupportedModelException() throws Exception {
        mockMvc.perform(get("/test/models/unsupported"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MODEL"))
                .andExpect(jsonPath("$.error.message").value("Unsupported model: gemini-2.0-flash"));
    }

    @Test
    void returns409ForBatchNotEditableException() throws Exception {
        mockMvc.perform(get("/test/batches/not-editable"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error.code").value("BATCH_NOT_EDITABLE"))
                .andExpect(jsonPath("$.error.message").value("Batch is not editable"));
    }

    @Test
    void returns400ForBatchEmptyException() throws Exception {
        mockMvc.perform(get("/test/batches/empty"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error.code").value("BATCH_EMPTY"))
                .andExpect(jsonPath("$.error.message").value("Batch has no prompts"));
    }

    @Test
    void returns502ForExternalApiException_Korean() throws Exception {
        mockMvc.perform(get("/test/external-api").header("Accept-Language", "ko"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error.code").value("CONTEXT_FETCH_FAILED"))
                .andExpect(jsonPath("$.error.message").value("서버와의 연결에 문제가 발생했습니다. 잠시 후 다시 시도해주세요."));
    }

    @Test
    void returns502ForExternalApiException_English() throws Exception {
        mockMvc.perform(get("/test/external-api").header("Accept-Language", "en"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error.code").value("CONTEXT_FETCH_FAILED"))
                .andExpect(jsonPath("$.error.message").value("A connection error occurred. Please try again later."));
    }
}

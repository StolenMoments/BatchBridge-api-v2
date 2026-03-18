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
                .andExpect(jsonPath("$.error.code").value("BATCH_NOT_FOUND"))
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
    void returns502ForExternalApiException() throws Exception {
        mockMvc.perform(get("/test/external-api"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error.code").value("EXTERNAL_API_ERROR"))
                .andExpect(jsonPath("$.error.message").value("외부 API 호출에 실패했습니다."));
    }
}

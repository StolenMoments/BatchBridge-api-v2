package org.jh.batchbridge.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.jh.batchbridge.domain.BatchPrompt;
import org.jh.batchbridge.exception.GlobalExceptionHandler;
import org.jh.batchbridge.exception.MediaNotFoundException;
import org.jh.batchbridge.repository.BatchPromptRepository;
import org.jh.batchbridge.service.MediaStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = MediaController.class)
@Import({GlobalExceptionHandler.class})
class MediaControllerTest {

    @TempDir
    Path tempDir;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BatchPromptRepository batchPromptRepository;

    @MockBean
    private MediaStorageService mediaStorageService;

    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void getMedia_ImageFile_Returns200WithImageContentType() throws Exception {
        Path file = tempDir.resolve("1.png");
        Files.write(file, new byte[]{1, 2, 3});

        BatchPrompt prompt = BatchPrompt.builder()
                .label("label")
                .userPrompt("user")
                .resultMediaPath(file.toString())
                .build();

        when(batchPromptRepository.findByIdAndBatchId(1L, 1L)).thenReturn(Optional.of(prompt));
        when(mediaStorageService.getFilePath(1L, 1L)).thenReturn(file);

        mockMvc.perform(get("/api/media/1/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("image/png"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"1.png\""));
    }

    @Test
    void getMedia_VideoFile_Returns200WithVideoContentType() throws Exception {
        Path file = tempDir.resolve("2.mp4");
        Files.write(file, new byte[]{1, 2, 3});

        BatchPrompt prompt = BatchPrompt.builder()
                .label("label")
                .userPrompt("user")
                .resultMediaPath(file.toString())
                .build();

        when(batchPromptRepository.findByIdAndBatchId(2L, 1L)).thenReturn(Optional.of(prompt));
        when(mediaStorageService.getFilePath(1L, 2L)).thenReturn(file);

        mockMvc.perform(get("/api/media/1/2"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("video/mp4"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"2.mp4\""));
    }

    @Test
    void getMedia_ResultMediaPathNull_Returns404() throws Exception {
        BatchPrompt prompt = BatchPrompt.builder()
                .label("label")
                .userPrompt("user")
                .build();

        when(batchPromptRepository.findByIdAndBatchId(3L, 1L)).thenReturn(Optional.of(prompt));

        mockMvc.perform(get("/api/media/1/3"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("MEDIA_NOT_FOUND"));
    }

    @Test
    void getMedia_FileNotOnDisk_Returns404() throws Exception {
        BatchPrompt prompt = BatchPrompt.builder()
                .label("label")
                .userPrompt("user")
                .resultMediaPath("/some/path/4.png")
                .build();

        when(batchPromptRepository.findByIdAndBatchId(4L, 1L)).thenReturn(Optional.of(prompt));
        when(mediaStorageService.getFilePath(1L, 4L)).thenThrow(new MediaNotFoundException(1L, 4L));

        mockMvc.perform(get("/api/media/1/4"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("MEDIA_NOT_FOUND"));
    }

    @Test
    void getMedia_PromptNotFound_Returns404() throws Exception {
        when(batchPromptRepository.findByIdAndBatchId(5L, 1L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/media/1/5"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PROMPT_NOT_FOUND"));
    }
}

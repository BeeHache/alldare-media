package online.alldare.media.controller.v1;

import online.alldare.media.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StorageControllerV1Test {

    private MockMvc mockMvc;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private StorageControllerV1 storageController;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(storageController)
                .setValidator(validator)
                .build();
    }


    @Test
    void getPresignedUrl_ReturnsUrlAndFileName() throws Exception {
        UUID authorId = UUID.randomUUID();
        String extension = ".mp4";
        String contentType = "video/mp4";
        String fileName = authorId + "/test-file.mp4";
        String presignedUrl = "https://s3.example.com/test-url";

        when(storageService.generateFileName(authorId, extension)).thenReturn(fileName);
        when(storageService.generatePresignedUploadUrl(fileName, contentType)).thenReturn(presignedUrl);

        mockMvc.perform(get("/api/v1/storage/presigned-url")
                        .param("authorId", authorId.toString())
                        .param("extension", extension)
                        .param("contentType", contentType))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").value(presignedUrl))
                .andExpect(jsonPath("$.fileName").value(fileName));
    }
}

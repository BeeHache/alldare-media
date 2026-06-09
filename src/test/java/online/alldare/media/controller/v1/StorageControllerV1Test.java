package online.alldare.media.controller.v1;

import online.alldare.media.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StorageControllerV1Test {

    private MockMvc mockMvc;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private StorageControllerV1 storageController;

    private Jwt mockJwt;

    @BeforeEach
    void setUp() {
        mockJwt = mock(Jwt.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(storageController)
                .setValidator(validator)
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType().isAssignableFrom(Jwt.class);
                    }

                    @Override
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                        return mockJwt;
                    }
                })
                .build();
    }

    @Test
    void getDownloadUrl_ReturnsUrl() throws Exception {
        String s3Key = "test/file.jpg";
        String downloadUrl = "https://cdn.example.com/" + s3Key;
        UUID userId = UUID.randomUUID();

        when(mockJwt.getClaimAsString("userId")).thenReturn(userId.toString());
        when(storageService.getDownloadUrl(eq(s3Key), eq(userId))).thenReturn(downloadUrl);

        mockMvc.perform(get("/api/v1/storage/download-url")
                        .param("s3Key", s3Key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(downloadUrl));
    }

    @Test
    void getPresignedUrl_ReturnsUrlAndFileName() throws Exception {
        UUID authorId = UUID.randomUUID();
        String extension = ".mp4";
        String contentType = "video/mp4";
        String fileName = "private/" + authorId + "/test-file.mp4";
        String presignedUrl = "https://s3.example.com/test-url";

        when(storageService.generateFileName(authorId, extension, false)).thenReturn(fileName);
        when(storageService.generatePresignedUploadUrl(fileName, contentType)).thenReturn(presignedUrl);

        mockMvc.perform(get("/api/v1/storage/presigned-url")
                        .param("authorId", authorId.toString())
                        .param("extension", extension)
                        .param("contentType", contentType))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").value(presignedUrl))
                .andExpect(jsonPath("$.fileName").value(fileName));
    }

    @Test
    void deleteFile_ValidUser_DeletesFile() throws Exception {
        UUID userId = UUID.randomUUID();
        String fileName = userId.toString() + "-file.jpg";

        when(mockJwt.getClaimAsString("userId")).thenReturn(userId.toString());

        mockMvc.perform(delete("/api/v1/storage/" + fileName))
                .andExpect(status().isNoContent());
    }
}

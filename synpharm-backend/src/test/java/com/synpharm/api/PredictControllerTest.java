package com.synpharm.api;

import com.synpharm.dto.response.PredictResultResponse;
import com.synpharm.exception.GlobalExceptionHandler;
import com.synpharm.service.PredictService;
import com.synpharm.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PredictControllerTest {

    @Mock
    private PredictService predictService;

    @Mock
    private JwtUtils jwtUtils;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PredictController controller = new PredictController(predictService, jwtUtils);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void 通用预测接口_成功() throws Exception {
        when(jwtUtils.getUserIdFromToken("token")).thenReturn(1L);
        when(predictService.predict(any(), eq(1L)))
                .thenReturn(PredictResultResponse.builder().algoType("DTI").targetId("T1").build());

        mockMvc.perform(post("/api/predict/general")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inputType\":\"smiles\",\"algoType\":\"DTI\",\"inputValue\":\"CCO,SEQ\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.algoType").value("DTI"));
    }

    @Test
    void 通用预测接口_参数缺失_校验失败() throws Exception {
        mockMvc.perform(post("/api/predict/general")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("参数校验失败"));
    }

    @Test
    void 旧DTI接口_兼容可用() throws Exception {
        when(jwtUtils.getUserIdFromToken("token")).thenReturn(1L);
        when(predictService.predictDTI(any(), eq(1L)))
                .thenReturn(PredictResultResponse.builder().algoType("DTI").build());

        mockMvc.perform(post("/api/predict/dti")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"smiles\":\"CCO\",\"targetId\":\"T1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(predictService).predictDTI(any(), eq(1L));
    }
}

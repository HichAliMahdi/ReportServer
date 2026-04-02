package com.reportserver.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReportControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${reportserver.upload.dir:data/reports/}")
    private String uploadDir;

    private static final String GENERATED_DIR = "data/generated-reports/";
    private static final String TEST_REPORT_NAME = "integration_report.jrxml";

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(Paths.get(uploadDir));
        Files.createDirectories(Paths.get(GENERATED_DIR));
        Files.writeString(Paths.get(uploadDir, TEST_REPORT_NAME), minimalJrxml());
    }

    @AfterEach
    void cleanup() throws Exception {
        Files.deleteIfExists(Paths.get(uploadDir, TEST_REPORT_NAME));
    }

    @Test
    void listReports_AsAdmin_ReturnsOk() throws Exception {
        mockMvc.perform(get("/reports").with(user("apiadmin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", notNullValue()))
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(1)));
    }

    @Test
    void listReports_Unauthenticated_RedirectsToLogin() throws Exception {
        mockMvc.perform(get("/reports"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void generateReport_FileNotFound_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/generate")
                        .with(user("apioperator").roles("OPERATOR"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("reportName", "missing_file.jrxml")
                        .param("format", "pdf"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is("error")));
    }

    @Test
    void generateReport_PathTraversal_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/generate")
                        .with(user("apioperator").roles("OPERATOR"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("reportName", "../../etc/passwd")
                        .param("format", "pdf")
                        .param("useDatabase", "false"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is("error")))
                .andExpect(jsonPath("$.message", containsString("Path traversal detected")));
    }

    @Test
    void downloadReport_PathTraversal_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/download-report")
                        .with(user("apiadmin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("reportName", "../../etc/passwd")
                        .param("format", "pdf"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Path traversal detected")));
    }

    @Test
    void deleteReport_NotFound_ReturnsBadRequest() throws Exception {
        mockMvc.perform(delete("/reports/does_not_exist.jrxml")
                        .with(user("apioperator").roles("OPERATOR"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Report file not found")));
    }

    @Test
    void generateReport_ValidFile_ReturnsSuccess() throws Exception {
        MvcResult result = mockMvc.perform(post("/generate")
                        .with(user("apioperator").roles("OPERATOR"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("reportName", TEST_REPORT_NAME)
                        .param("format", "pdf")
                        .param("useDatabase", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.reportId", notNullValue()))
                .andExpect(jsonPath("$.fileName", containsString(".pdf")))
                .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        String fileName = (String) body.get("fileName");
        if (fileName != null) {
            File generated = Paths.get(GENERATED_DIR, fileName).toFile();
            if (generated.exists()) {
                generated.delete();
            }
        }
    }

    private String minimalJrxml() {
        return """
                <?xml version=\"1.0\" encoding=\"UTF-8\"?>
                <jasperReport xmlns=\"http://jasperreports.sourceforge.net/jasperreports\"
                              xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
                              xsi:schemaLocation=\"http://jasperreports.sourceforge.net/jasperreports
                              http://jasperreports.sourceforge.net/xsd/jasperreport.xsd\"
                              name=\"IntegrationReport\" pageWidth=\"595\" pageHeight=\"842\"
                              columnWidth=\"555\" leftMargin=\"20\" rightMargin=\"20\"
                              topMargin=\"20\" bottomMargin=\"20\">
                    <detail>
                        <band height=\"20\">
                            <staticText>
                                <reportElement x=\"0\" y=\"0\" width=\"120\" height=\"20\"/>
                                <text><![CDATA[Hello Integration]]></text>
                            </staticText>
                        </band>
                    </detail>
                </jasperReport>
                """;
    }
}

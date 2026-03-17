package com.reportserver.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class JrxmlEditorControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${reportserver.upload.dir:data/reports/}")
    private String uploadDir;

    private static final String TEST_FILE = "editor_test.jrxml";

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(Paths.get(uploadDir));
        Files.writeString(Paths.get(uploadDir, TEST_FILE), validJrxml());
    }

    @AfterEach
    void cleanup() throws Exception {
        Files.deleteIfExists(Paths.get(uploadDir, TEST_FILE));
    }

    @Test
    void loadJrxml_FileNotFound_Returns404() throws Exception {
        mockMvc.perform(get("/api/jrxml/load/not-found.jrxml").with(user("apiadmin").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void validateJrxml_ValidContent_ReturnsValid() throws Exception {
        mockMvc.perform(post("/api/jrxml/validate")
                        .with(user("apiadmin").roles("ADMIN"))
                        .with(csrf())
                        .param("content", validJrxml()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", is(true)));
    }

    @Test
    void validateJrxml_DangerousContent_ReturnsInvalid() throws Exception {
        mockMvc.perform(post("/api/jrxml/validate")
                        .with(user("apiadmin").roles("ADMIN"))
                        .with(csrf())
                        .param("content", dangerousJrxml()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", is(false)))
                .andExpect(jsonPath("$.issues", not(empty())));
    }

    @Test
    void saveJrxml_ValidContent_Returns200() throws Exception {
        String updated = validJrxml().replace("Sample", "Updated Sample");

        mockMvc.perform(post("/api/jrxml/save")
                        .with(user("apioperator").roles("OPERATOR"))
                        .with(csrf())
                        .param("fileName", TEST_FILE)
                        .param("content", updated))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        Path file = Paths.get(uploadDir, TEST_FILE);
        String fileContent = Files.readString(file);
        org.junit.jupiter.api.Assertions.assertTrue(fileContent.contains("Updated Sample"));
    }

    @Test
    void saveJrxml_PathTraversal_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/jrxml/save")
                        .with(user("apiadmin").roles("ADMIN"))
                        .with(csrf())
                        .param("fileName", "../etc/passwd")
                        .param("content", "x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    private String validJrxml() {
        return """
                <?xml version=\"1.0\" encoding=\"UTF-8\"?>
                <jasperReport name=\"Sample\" language=\"java\">
                    <detail>
                        <band height=\"20\">
                            <staticText>
                                <reportElement x=\"0\" y=\"0\" width=\"100\" height=\"20\"/>
                                <text><![CDATA[Hello]]></text>
                            </staticText>
                        </band>
                    </detail>
                </jasperReport>
                """;
    }

    private String dangerousJrxml() {
        return """
                <jasperReport name=\"Bad\" language=\"java\">
                    <detail>
                        <band height=\"20\">
                            <textField>
                                <textFieldExpression><![CDATA[Runtime.getRuntime()]]></textFieldExpression>
                            </textField>
                        </band>
                    </detail>
                </jasperReport>
                """;
    }
}

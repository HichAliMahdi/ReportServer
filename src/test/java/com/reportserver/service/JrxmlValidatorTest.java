package com.reportserver.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JrxmlValidatorTest {

    private JrxmlValidator validator;

    @BeforeEach
    void setUp() {
        validator = new JrxmlValidator();
        ReflectionTestUtils.setField(validator, "allowedClassesConfig",
                "java.lang.String,java.lang.Integer,java.lang.Double,java.lang.Boolean");
        ReflectionTestUtils.setField(validator, "maxJrxmlSizeBytes", 1024 * 1024);
    }

    @Test
    void validate_ValidJrxml_ReturnsValid() {
        String jrxml = """
                <?xml version=\"1.0\" encoding=\"UTF-8\"?>
                <jasperReport name=\"Safe\" language=\"java\">
                    <parameter name=\"Title\" class=\"java.lang.String\"/>
                    <detail>
                        <band height=\"20\">
                            <textField>
                                <textFieldExpression><![CDATA[$P{Title}]]></textFieldExpression>
                            </textField>
                        </band>
                    </detail>
                </jasperReport>
                """;

        JrxmlValidator.JrxmlValidationResult result = validator.validate(jrxml);

        assertTrue(result.valid);
        assertFalse(result.hasIssues());
    }

    @Test
    void validate_DangerousPattern_ReturnsInvalid() {
        String jrxml = """
                <jasperReport name=\"Danger\" language=\"java\">
                    <detail>
                        <band height=\"20\">
                            <textField>
                                <textFieldExpression><![CDATA[$P{X} + Runtime.getRuntime()]]></textFieldExpression>
                            </textField>
                        </band>
                    </detail>
                </jasperReport>
                """;

        JrxmlValidator.JrxmlValidationResult result = validator.validate(jrxml);

        assertFalse(result.valid);
        assertTrue(result.getIssues().stream().anyMatch(i -> i.contains("Runtime.getRuntime()")));
    }

    @Test
    void validate_ForbiddenTag_ReturnsInvalid() {
        String jrxml = """
                <jasperReport name=\"Bad\" language=\"java\">
                    <scriptlet class=\"com.evil.Scriptlet\"/>
                </jasperReport>
                """;

        JrxmlValidator.JrxmlValidationResult result = validator.validate(jrxml);

        assertFalse(result.valid);
        assertTrue(result.getIssues().stream().anyMatch(i -> i.contains("Forbidden JRXML tag")));
    }

    @Test
    void validate_EmptyContent_ReturnsInvalid() {
        JrxmlValidator.JrxmlValidationResult result = validator.validate("   ");

        assertFalse(result.valid);
        assertTrue(result.getIssues().stream().anyMatch(i -> i.contains("empty")));
    }

    @Test
    void validate_TooLargeContent_ReturnsInvalid() {
        ReflectionTestUtils.setField(validator, "maxJrxmlSizeBytes", 10);

        JrxmlValidator.JrxmlValidationResult result = validator.validate("<jasperReport>this-is-way-too-large</jasperReport>");

        assertFalse(result.valid);
        assertTrue(result.getIssues().stream().anyMatch(i -> i.contains("maximum allowed size")));
    }

    @Test
    void validate_ForbiddenAttribute_ReturnsInvalid() {
        String jrxml = """
                <jasperReport name=\"Bad\" language=\"java\" scriptletClass=\"com.evil.Scriptlet\" />
                """;

        JrxmlValidator.JrxmlValidationResult result = validator.validate(jrxml);

        assertFalse(result.valid);
        assertTrue(result.getIssues().stream().anyMatch(i -> i.contains("Forbidden JRXML attribute")));
    }
}

package com.reportserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Basic smoke test to verify the Spring application context loads successfully.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReportServerApplicationTests {

    @Test
    void contextLoads() {
        // This test passes if the application context loads without errors
    }
}

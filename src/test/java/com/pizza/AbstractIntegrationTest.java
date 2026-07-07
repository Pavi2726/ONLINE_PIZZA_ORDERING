package com.pizza;

import com.pizza.service.CloudinaryService;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Base class for all Spring-context-backed tests.
 *
 * <p>Boots the full application context against the in-memory H2 database
 * (see {@code application-test.properties}) instead of the live Aiven MySQL
 * instance, mocks out {@link CloudinaryService} so no real network call to
 * Cloudinary is ever made, and wraps each test method in a transaction that
 * is rolled back afterwards so tests never leak state into one another.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class AbstractIntegrationTest {

    @MockBean
    protected CloudinaryService cloudinaryService;
}

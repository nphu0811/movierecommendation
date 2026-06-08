package com.example.movierecommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class ErrorTemplateTests {

    @Test
    void errorTemplateHandlesNotFoundAndForbiddenWithoutWhitelabel() throws Exception {
        ClassPathResource resource = new ClassPathResource("templates/error.html");

        assertThat(resource.exists()).isTrue();

        String template = resource.getContentAsString(StandardCharsets.UTF_8);
        assertThat(template)
                .contains("Page not found")
                .contains("Access denied")
                .contains("MovieRec")
                .doesNotContain("Whitelabel Error Page");
    }

    @Test
    void errorTemplateRendersForNotFoundAndForbidden() {
        SpringTemplateEngine engine = templateEngine();

        assertThat(render(engine, 404, "/not-a-real-route"))
                .contains("Page not found")
                .contains("404")
                .contains("/not-a-real-route")
                .doesNotContain("Whitelabel Error Page");

        assertThat(render(engine, 403, "/admin"))
                .contains("Access denied")
                .contains("403")
                .contains("/admin")
                .doesNotContain("Whitelabel Error Page");
    }

    private static SpringTemplateEngine templateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private static String render(SpringTemplateEngine engine, int status, String path) {
        Context context = new Context();
        context.setVariable("status", status);
        context.setVariable("path", path);
        return engine.process("error", context);
    }
}

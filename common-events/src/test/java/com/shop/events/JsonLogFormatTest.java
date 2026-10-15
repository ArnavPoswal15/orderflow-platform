package com.shop.events;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.logstash.logback.composite.loggingevent.LoggingEventJsonProviders;
import net.logstash.logback.composite.loggingevent.LoggingEventPatternJsonProvider;
import net.logstash.logback.composite.loggingevent.MdcJsonProvider;
import net.logstash.logback.composite.loggingevent.MessageJsonProvider;
import net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonLogFormatTest {

    /** Same expression as {@code PlatformTestHarness} uses to find a forked Tomcat port. */
    private static final Pattern TOMCAT_PORT = Pattern.compile("Tomcat started on port (\\d+)");

    @Test
    void sampleJsonLineStillMatchesTomcatPortRegex() throws Exception {
        String xml = readLogbackConfig();
        assertTrue(xml.contains("<message/>"), "JSON encoder must keep the log message field");
        assertTrue(xml.contains("<mdc/>"), "MDC keys must be top-level JSON fields");
        assertFalse(xml.contains("includeMdcKeyName"), "nesting MDC would hide orderId from Filebeat");
        assertTrue(xml.contains("spring.application.name"));
        assertTrue(xml.contains("elk-file"));
        assertTrue(xml.contains("/var/log/shop/${serviceName}.json"));

        LoggerContext context = new LoggerContext();
        LoggingEventCompositeJsonEncoder encoder = new LoggingEventCompositeJsonEncoder();
        encoder.setContext(context);
        LoggingEventJsonProviders providers = new LoggingEventJsonProviders();
        providers.setContext(context);
        MessageJsonProvider message = new MessageJsonProvider();
        message.setContext(context);
        providers.addProvider(message);
        MdcJsonProvider mdc = new MdcJsonProvider();
        mdc.setContext(context);
        providers.addProvider(mdc);
        LoggingEventPatternJsonProvider service = new LoggingEventPatternJsonProvider();
        service.setContext(context);
        service.setPattern("{\"service\":\"order-service\"}");
        providers.addProvider(service);
        encoder.setProviders(providers);
        encoder.start();

        LoggingEvent event = new LoggingEvent(
                "org.springframework.boot.web.embedded.tomcat.TomcatWebServer",
                context.getLogger("org.springframework.boot.web.embedded.tomcat.TomcatWebServer"),
                Level.INFO,
                "Tomcat started on port 8080",
                null,
                null);
        event.setMDCPropertyMap(Map.of("orderId", "11111111-1111-1111-1111-111111111111"));
        String line = new String(encoder.encode(event), StandardCharsets.UTF_8);
        Matcher matcher = TOMCAT_PORT.matcher(line);
        assertTrue(matcher.find(), line);
        assertEquals("8080", matcher.group(1));

        JsonNode node = new ObjectMapper().readTree(line);
        assertEquals("Tomcat started on port 8080", node.get("message").asText());
        assertEquals("11111111-1111-1111-1111-111111111111", node.get("orderId").asText());
        assertEquals("order-service", node.get("service").asText());
        assertNotNull(node.get("message"));
    }

    private static String readLogbackConfig() throws Exception {
        try (InputStream in = JsonLogFormatTest.class.getResourceAsStream("/logback-spring.xml")) {
            assertNotNull(in, "logback-spring.xml must be on the common-events classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

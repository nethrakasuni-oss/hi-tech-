package com.hitech.lms.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP client for Generative Language API {@code :generateContent} (Gemini, including tuned models).
 */
@Service
public class GeminiClientService {

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.api.url:}")
    private String fullUrlOverride;

    @Value("${gemini.api.base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String baseUrl;

    @Value("${gemini.api.resource:models/gemini-flash-latest:generateContent}")
    private String supportResource;

    /**
     * Your tuned / fine-tuned model for student performance analysis (e.g. {@code tunedModels/…:generateContent}).
     * Must be set to use the performance insights chat.
     */
    @Value("${gemini.api.performance.resource:}")
    private String performanceResource;

    public GeminiClientService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Support/knowledge chat: uses {@code gemini.api.url} if set (full override), else {@code base-url + resource}.
     */
    public String resolveSupportEndpoint() {
        if (fullUrlOverride != null && !fullUrlOverride.isBlank()) {
            return fullUrlOverride.trim();
        }
        return joinBaseAndResource(supportResource);
    }

    /**
     * Trained performance model: {@code base-url + performance.resource}. If not set, falls back to the same
     * endpoint as the support chat (handy for local testing; set the tuned model id for production).
     */
    public String resolvePerformanceEndpoint() {
        if (performanceResource != null && !performanceResource.isBlank()) {
            return joinBaseAndResource(performanceResource.trim());
        }
        return resolveSupportEndpoint();
    }

    private String joinBaseAndResource(String resource) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String r = (resource == null || resource.isBlank())
                ? "models/gemini-flash-latest:generateContent"
                : resource.trim();
        if (r.startsWith("/")) {
            r = r.substring(1);
        }
        return base + "/" + r;
    }

    /**
     * @param usePerformanceModel if true, calls {@code gemini.api.performance.resource}; else support resource.
     */
    public String generateContent(
            boolean usePerformanceModel,
            String systemInstruction,
            List<Map<String, Object>> contents,
            int maxOutputTokens,
            double temperature) {

        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Set gemini.api.key in application properties.");
        }

        String endpoint = usePerformanceModel ? resolvePerformanceEndpoint() : resolveSupportEndpoint();

        Map<String, Object> system = Map.of("parts", List.of(Map.of("text", systemInstruction)));
        Map<String, Object> body = new HashMap<>();
        body.put("systemInstruction", system);
        body.put("contents", contents);
        body.put("generationConfig", Map.of("temperature", temperature, "maxOutputTokens", maxOutputTokens));

        String uri = UriComponentsBuilder.fromUriString(endpoint)
                .queryParam("key", apiKey)
                .build(true)
                .toUriString();

        String raw;
        try {
            raw = restClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "The AI service could not be reached. Try again later.");
        }

        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Empty response from AI service.");
        }

        return extractReplyText(raw);
    }

    private String extractReplyText(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode err = root.path("error");
            if (!err.isMissingNode()) {
                String msg = err.path("message").asText("AI request failed.");
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, msg);
            }
            JsonNode text = root.path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts")
                    .path(0)
                    .path("text");
            if (text.isMissingNode() || !text.isTextual()) {
                JsonNode finish = root.path("candidates").path(0).path("finishReason");
                String reason = finish.isTextual() ? finish.asText() : "unknown";
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "The response could not be generated (reason: " + reason + ").");
            }
            return text.asText();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not parse AI response.");
        }
    }
}

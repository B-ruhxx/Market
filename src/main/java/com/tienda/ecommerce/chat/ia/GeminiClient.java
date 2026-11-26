package com.tienda.ecommerce.chat.ia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);
    private static final List<String> FALLBACK_MODELS = List.of(
            "gemini-3.1-flash-lite",
            "gemini-flash-latest"
    );

    private final GeminiProperties properties;
    private final RestClient restClient;

    public GeminiClient(GeminiProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofSeconds(30).toMillis());

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    public Map<String, Object> generarContenido(
            List<Map<String, Object>> contents,
            List<Map<String, Object>> tools,
            String systemPrompt
    ) {
        if (!properties.tieneApiKeyConfigurada()) {
            throw new IllegalStateException("La API key de Gemini no está configurada");
        }

        Map<String, Object> body = new HashMap<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            body.put("systemInstruction", Map.of(
                    "parts", List.of(Map.of("text", systemPrompt))
            ));
        }
        body.put("contents", contents);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
        }
        body.put("generationConfig", Map.of(
                "temperature", properties.getTemperatura(),
                "maxOutputTokens", 900
        ));

        String primaryModel = properties.getModel();
        try {
            return llamarApi(primaryModel, body);
        } catch (RestClientResponseException ex) {
            int statusCode = ex.getStatusCode().value();
            // Si el modelo configurado devuelve 503 (alta demanda), 429 (cuota temporal) o 404 (obsoleto),
            // intentar automáticamente con modelos fallback distintos al primario
            if (statusCode == 503 || statusCode == 429 || statusCode == 404) {
                for (String fallback : FALLBACK_MODELS) {
                    if (fallback.equalsIgnoreCase(primaryModel)) {
                        continue;
                    }
                    log.warn("Modelo Gemini '{}' devolvió HTTP {}. Activando fallback automático a '{}'...",
                            primaryModel, statusCode, fallback);
                    try {
                        return llamarApi(fallback, body);
                    } catch (Exception fallbackEx) {
                        log.warn("Fallback a '{}' falló: {}", fallback, fallbackEx.getMessage());
                        ex.addSuppressed(fallbackEx);
                    }
                }
            }
            throw ex;
        }
    }

    private Map<String, Object> llamarApi(String model, Map<String, Object> body) {
        String baseUrl = properties.getBaseUrl().endsWith("/")
                ? properties.getBaseUrl().substring(0, properties.getBaseUrl().length() - 1)
                : properties.getBaseUrl();
        String url = String.format("%s/models/%s:generateContent", baseUrl, model);

        try {
            return restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-goog-api-key", properties.getApiKey().trim())
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (RestClientResponseException ex) {
            String cuerpo = ex.getResponseBodyAsString();
            String resumen = cuerpo.length() > 300 ? cuerpo.substring(0, 300) + "..." : cuerpo;
            log.warn("Error HTTP {} al consultar Gemini ({}): {}", ex.getStatusCode().value(), model, resumen);
            throw ex;
        }
    }
}

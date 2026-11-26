package com.tienda.ecommerce.chat.ia;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ia.gemini")
public class GeminiProperties {

    private boolean enabled = true;
    private String apiKey = "";
    private String model = "gemini-3.1-flash-lite";
    private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
    private double temperatura = 0.3;
    private int maxHistorial = 12;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public double getTemperatura() {
        return temperatura;
    }

    public void setTemperatura(double temperatura) {
        this.temperatura = temperatura;
    }

    public int getMaxHistorial() {
        return maxHistorial;
    }

    public void setMaxHistorial(int maxHistorial) {
        this.maxHistorial = maxHistorial;
    }

    public boolean tieneApiKeyConfigurada() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}

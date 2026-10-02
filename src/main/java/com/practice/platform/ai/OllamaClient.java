package com.practice.platform.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.practice.platform.config.AppProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

@Service
public class OllamaClient {

    private final AppProperties props;
    private final ObjectMapper mapper;
    private final RestClient rest;

    public OllamaClient(AppProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(props.getOllama().getTimeoutSeconds() * 1000);
        this.rest = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(props.getOllama().getBaseUrl())
                .build();
    }

    public String generateJson(String prompt) {
        try {
            String body = rest.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", props.getOllama().getModel(),
                            "prompt", prompt,
                            "stream", false,
                            "format", "json",
                            "options", generationOptions()
                    ))
                    .retrieve()
                    .body(String.class);
            JsonNode node = mapper.readTree(body);
            if (node.has("error")) {
                throw new IllegalStateException("Ollama error: " + node.get("error").asText());
            }
            return node.path("response").asText();
        } catch (ResourceAccessException e) {
            throw new IllegalStateException("Start Ollama on this machine (http://127.0.0.1:11434) and pull the model: "
                    + props.getOllama().getModel(), e);
        } catch (RestClientException e) {
            throw new IllegalStateException("Ollama request failed. Start Ollama and ensure model "
                    + props.getOllama().getModel() + " is pulled.", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse Ollama response.", e);
        }
    }

    /**
     * Sampling knobs that keep repeated prompts from collapsing onto the same classic problem.
     * A fresh random seed is sent on every request, so two identical user requests diverge.
     */
    private Map<String, Object> generationOptions() {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("temperature", 0.9);
        options.put("top_p", 0.95);
        options.put("seed", ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE));
        return options;
    }

    public record AiHealth(boolean ollamaUp, String model, boolean modelInstalled) {
    }

    /**
     * Probes the local Ollama server's {@code GET /api/tags} to report whether it is reachable
     * and whether the configured model has been pulled. Never throws.
     */
    public AiHealth health() {
        String model = props.getOllama().getModel();
        try {
            String body = rest.get()
                    .uri("/api/tags")
                    .retrieve()
                    .body(String.class);
            JsonNode node = mapper.readTree(body);
            boolean up = node.has("models");
            boolean installed = false;
            if (up) {
                for (JsonNode m : node.get("models")) {
                    if (model.equals(m.path("name").asText())) {
                        installed = true;
                        break;
                    }
                }
            }
            return new AiHealth(up, model, installed);
        } catch (Exception e) {
            return new AiHealth(false, model, false);
        }
    }

    /**
     * Streams a free-form completion, invoking {@code onToken} for each chunk Ollama emits.
     * Uses {@code stream:true} on {@code /api/generate}, which replies with newline-delimited
     * JSON objects carrying a {@code response} fragment each.
     */
    public void streamText(String prompt, Consumer<String> onToken) {
        try {
            rest.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", props.getOllama().getModel(),
                            "prompt", prompt,
                            "stream", true
                    ))
                    .exchange((request, response) -> {
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.isBlank()) {
                                    continue;
                                }
                                JsonNode node = mapper.readTree(line);
                                if (node.has("error")) {
                                    throw new IllegalStateException("Ollama error: " + node.get("error").asText());
                                }
                                String token = node.path("response").asText("");
                                if (!token.isEmpty()) {
                                    onToken.accept(token);
                                }
                                if (node.path("done").asBoolean(false)) {
                                    break;
                                }
                            }
                        }
                        return null;
                    });
        } catch (ResourceAccessException e) {
            throw new IllegalStateException("Start Ollama on this machine (http://127.0.0.1:11434).", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (RestClientException e) {
            throw new IllegalStateException("Ollama request failed. Start Ollama locally.", e);
        } catch (Exception e) {
            throw new IllegalStateException("Ollama stream failed. Start Ollama locally.", e);
        }
    }

    public String generateText(String prompt) {
        try {
            String body = rest.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", props.getOllama().getModel(),
                            "prompt", prompt,
                            "stream", false
                    ))
                    .retrieve()
                    .body(String.class);
            JsonNode node = mapper.readTree(body);
            if (node.has("error")) {
                throw new IllegalStateException("Ollama error: " + node.get("error").asText());
            }
            return node.path("response").asText();
        } catch (ResourceAccessException e) {
            throw new IllegalStateException("Start Ollama on this machine (http://127.0.0.1:11434).", e);
        } catch (Exception e) {
            if (e instanceof IllegalStateException ise) {
                throw ise;
            }
            throw new IllegalStateException("Ollama request failed. Start Ollama locally.", e);
        }
    }
}

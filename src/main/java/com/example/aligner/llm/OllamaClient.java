package com.example.aligner.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
// llm.providerが "ollama" の場合にのみ、このBeanを有効にする
@ConditionalOnProperty(name = "llm.provider", havingValue = "ollama")
public class OllamaClient implements LLMClient {

    private final RestTemplate restTemplate;
    private final String ollamaBaseUrl;
    private final String ollamaModel;
    private final ObjectMapper objectMapper;

    @Autowired
    public OllamaClient(RestTemplate restTemplate,
                        @Value("${ollama.base-url}") String ollamaBaseUrl,
                        @Value("${ollama.model}") String ollamaModel,
                        ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.ollamaBaseUrl = ollamaBaseUrl;
        this.ollamaModel = ollamaModel;
        this.objectMapper = objectMapper;
        System.out.println(">>> LLM Provider: OllamaClient has been activated. <<<");
        System.out.println(">>> Ollama URL: " + this.ollamaBaseUrl + ", Model: " + this.ollamaModel);
    }

    @Override
    public String generateAlignmentJson(String systemPrompt, String userPrompt) {
        // Ollama APIのエンドポイント
        String url = ollamaBaseUrl + "/api/generate";

        // リクエストボディの作成
        OllamaRequest requestPayload = new OllamaRequest(
                ollamaModel,
                systemPrompt,
                userPrompt,
                "json", // OllamaにJSON形式で出力させる
                false   // ストリーミングはしない
        );

        // HTTPヘッダーの設定
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // リクエストエンティティの作成
        HttpEntity<OllamaRequest> requestEntity = new HttpEntity<>(requestPayload, headers);

        // API呼び出し
        OllamaResponse response = restTemplate.postForObject(url, requestEntity, OllamaResponse.class);

        if (response == null || response.response == null) {
            throw new RuntimeException("Failed to get a valid response from Ollama server.");
        }

        // Ollamaの応答はJSON文字列として返ってくるので、それをそのまま返す
        return response.response;
    }

    // Ollama APIリクエスト用の内部クラス
    private static class OllamaRequest {
        public String model;
        public String system;
        public String prompt;
        public String format;
        public boolean stream;

        public OllamaRequest(String model, String system, String prompt, String format, boolean stream) {
            this.model = model;
            this.system = system;
            this.prompt = prompt;
            this.format = format;
            this.stream = stream;
        }
    }

    // Ollama APIレスポンス用の内部クラス
    private static class OllamaResponse {
        @JsonProperty("response")
        public String response;
        // 他のフィールド (model, created_at など) もあるが、今回は response のみ使用
    }
}
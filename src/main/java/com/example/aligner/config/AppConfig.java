package com.example.aligner.config;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate; // ★ 追記

@Configuration
// ファイル名を AzureOpenAIConfig.java -> AppConfig.java に変更
public class AppConfig {

    // --- Azure OpenAI 関連の設定 (変更なし) ---
    @Value("${azure.openai.endpoint}")
    private String endpoint;
    @Value("${azure.openai.api-key}")
    private String apiKey;
    @Value("${azure.openai.deployment-name}")
    private String deploymentName;
    @Value("${azure.openai.api-version}")
    private String apiVersion;

    @Bean
    public OpenAIClient openAIClient() {
        if (endpoint == null || apiKey == null || endpoint.isEmpty() || apiKey.isEmpty() || endpoint.equals("${AZURE_OPENAI_ENDPOINT}")) {
             System.err.println("!!! Azure OpenAI Endpoint/Key are not set. If you use 'azure' provider, alignment will fail. !!!");
        }
        return new OpenAIClientBuilder()
                .endpoint(endpoint)
                .credential(new AzureKeyCredential(apiKey))
                .buildClient();
    }
    public String getDeploymentName() { return deploymentName; }
    public String getApiVersion() { return apiVersion; }


    // --- (★ここから追記) Ollama 関連の設定 ---
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
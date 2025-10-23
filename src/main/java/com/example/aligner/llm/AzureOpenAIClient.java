package com.example.aligner.llm;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatRequestMessage;
import com.azure.ai.openai.models.ChatRequestSystemMessage;
import com.azure.ai.openai.models.ChatRequestUserMessage;
// ★ 修正 1/3: import を AzureOpenAIConfig -> AppConfig に変更
import com.example.aligner.config.AppConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "azure", matchIfMissing = true)
public class AzureOpenAIClient implements LLMClient {

    private final OpenAIClient client;
    // ★ 修正 2/3: フィールドの型を AppConfig に変更
    private final AppConfig config;

    @Autowired
    // ★ 修正 3/3: コンストラクタの引数の型を AppConfig に変更
    public AzureOpenAIClient(OpenAIClient client, AppConfig config) {
        this.client = client;
        this.config = config;
        System.out.println(">>> LLM Provider: AzureOpenAIClient has been activated. <<<");
    }

    @Override
    public String generateAlignmentJson(String systemPrompt, String userPrompt) {
        List<ChatRequestMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new ChatRequestSystemMessage(systemPrompt));
        chatMessages.add(new ChatRequestUserMessage(userPrompt));

        ChatCompletionsOptions options = new ChatCompletionsOptions(chatMessages)
                .setTemperature(0.0)
                .setMaxTokens(8192); // (model_63 で調整した値)

        // config.getDeploymentName() の呼び出しは AppConfig にも存在するので変更不要
        ChatCompletions completions = client.getChatCompletions(
                config.getDeploymentName(),
                options
        );

        return completions.getChoices().get(0).getMessage().getContent();
    }
}
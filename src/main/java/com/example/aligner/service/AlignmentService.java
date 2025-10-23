package com.example.aligner.service;

import com.example.aligner.llm.LLMClient; // ★ 変更
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AlignmentService {

    // 内部クラスはそのまま流用
    public static class ParagraphPair {
        private final String japanese;
        private final String english;
        public ParagraphPair(String japanese, String english) { this.japanese = japanese; this.english = english; }
        public String getJapanese() { return japanese; }
        public String getEnglish() { return english; }
    }

    // ★ 依存性を OpenAIClient -> LLMClient に変更
    private final LLMClient llmClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public AlignmentService(LLMClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    /**
     * (LLMプロバイダ切り替え対応版)
     */
    public List<ParagraphPair> alignParagraphs(List<String> jpnParagraphs, List<String> engParagraphs) {

        String jpnCorpus = String.join("\n\n", jpnParagraphs);
        String engCorpus = String.join("\n\n", engParagraphs);

        // --- プロンプト部分は変更なし (model_63 のプロンプトを使用) ---
        String systemPrompt = "あなたは、法律文書（条約）の和文と英文をアライメントする高度な専門家です。"
            + "提供される日本語の全文と英語の全文を比較し、意味的・構造的に対応するセグメント（タイトル、条、項、号、段落）ごとにペアにしてください。"
            + "日本語側が1つの段落（例：'第一条 定義'）で、英語側が2つの段落（例：'Article 1'と'Definitions'）に対応する場合も正しく処理してください。"
            + "あなたの応答は、解説や前置きを一切含まない、JSON配列「のみ」でなければなりません。"
            + "JSONの形式は `[ {\"jp\": \"日本語セグメント1\", \"en\": \"英語セグメント1\"}, {\"jp\": \"日本語セグメント2\", \"en\": \"英語セグメント2\"}, ... ]` としてください。"
            + "重要：元のテキストに含まれる全ての行や段落は、たとえ片方の言語にしか存在しない短い接続詞（例: 'and'）であっても、**省略せずに**必ずJSON配列内のペアとして表現してください。対応する言語がない場合は、必ず**空文字列 `\"\"`** を使用してください。例えば `{\"jp\": \"\", \"en\": \"and\"}` のようになります。";

        String userPrompt = "以下の日本語テキストと英語テキストをアライメントし、指定されたJSON形式で応答してください。\n\n"
            + "--- [日本語全文 START] ---\n" + jpnCorpus + "\n--- [日本語全文 END] ---\n\n"
            + "--- [英語全文 START] ---\n" + engCorpus + "\n--- [英語全文 END] ---\n\n"
            + "JSON Response:";

        try {
            // ★ LLMClientインターフェース経由で呼び出す (AzureかOllamaかは自動で切り替わる)
            String jsonResponse = llmClient.generateAlignmentJson(systemPrompt, userPrompt);

            // AIが返したJSONをパースして、そのまま返す
            return parseJsonResponse(jsonResponse);

        } catch (Exception e) {
            e.printStackTrace();
            List<ParagraphPair> errorResult = new ArrayList<>();
            String errorMessage = (e.getMessage() != null) ? e.getMessage() : "Unknown error occurred.";
            errorResult.add(new ParagraphPair("[LLM Error: " + errorMessage + "]",
                                              "Failed to process alignment. Check server logs."));
            return errorResult;
        }
    }

    /**
     * AIが生成したJSON文字列を解析し、ParagraphPairのリストに変換する
     * (このメソッドは変更なし)
     */
    private List<ParagraphPair> parseJsonResponse(String jsonResponse) throws JsonProcessingException {
         List<ParagraphPair> pairs = new ArrayList<>();
        System.out.println("--- START OF AI RAW RESPONSE (FOR DEBUGGING) ---");
        System.out.println(jsonResponse); // デバッグのため常に表示
        System.out.println("--- END OF AI RAW RESPONSE ---");
        String cleanJson = jsonResponse.trim();
        if (cleanJson.startsWith("```json")) {
            cleanJson = cleanJson.substring(7); if (cleanJson.endsWith("```")) { cleanJson = cleanJson.substring(0, cleanJson.length() - 3); }
        } else if (cleanJson.startsWith("```")) {
            cleanJson = cleanJson.substring(3); if (cleanJson.endsWith("```")) { cleanJson = cleanJson.substring(0, cleanJson.length() - 3); }
        }
        cleanJson = cleanJson.trim();
        if (!cleanJson.startsWith("[")) {
            String errorMsg = "AI did not return a JSON array. Raw response was: " + (jsonResponse.length() > 200 ? jsonResponse.substring(0, 200) + "..." : jsonResponse);
            throw new JsonProcessingException(errorMsg) {};
        }
        if (!cleanJson.endsWith("]")) {
            int lastBracketIndex = cleanJson.lastIndexOf('}');
            if(lastBracketIndex != -1) { cleanJson = cleanJson.substring(0, lastBracketIndex + 1) + "]"; }
            else { throw new JsonProcessingException("AI response appears to be a truncated or invalid JSON array.") {}; }
        }
        try {
            JsonNode rootNode = objectMapper.readTree(cleanJson);
            if (rootNode.isArray()) {
                for (JsonNode pairNode : rootNode) {
                    String jp = pairNode.has("jp") ? pairNode.get("jp").asText("") : "";
                    String en = pairNode.has("en") ? pairNode.get("en").asText("") : "";
                    pairs.add(new ParagraphPair(jp, en));
                }
            } else { throw new JsonProcessingException("AI response was not a JSON array (root node is not array).") {}; }
        } catch (JsonProcessingException e) {
            System.err.println("Failed to parse JSON string. Cleaned JSON was: " + cleanJson);
            throw new JsonProcessingException("Failed to parse AI's JSON response: " + e.getMessage()) {};
        }
        System.out.println("Successfully parsed " + pairs.size() + " alignment pairs from AI response.");
        return pairs;
    }
}
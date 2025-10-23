package com.example.aligner.llm;

// LLM呼び出しの共通インターフェース
public interface LLMClient {
    /**
     * システムプロンプトとユーザープロンプトを受け取り、
     * アライメント結果のJSON文字列を返す
     */
    String generateAlignmentJson(String systemPrompt, String userPrompt);
}
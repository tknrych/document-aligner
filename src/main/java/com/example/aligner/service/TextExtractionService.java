package com.example.aligner.service;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class TextExtractionService {

    // TextPorterの出力先 (application.propertiesから)
    private final Path textPorterOutputDir;
    
    // TextPorterの実行ディレクトリ (DockerfileのWORKDIR と合わせる)
    private final Path textPorterWorkDir = Paths.get("/app/dmc_java");

    public TextExtractionService(@Value("${textporter.output-dir}") String outputDir) {
        this.textPorterOutputDir = Paths.get(outputDir);
    }

    @PostConstruct
    public void init() {
         try {
            Files.createDirectories(this.textPorterOutputDir);
        } catch (Exception ex) {
            throw new RuntimeException("Could not create TextPorter output directory.", ex);
        }
    }


    /**
     * Word (.docx) ファイルから段落リストを抽出
     */
    public List<String> extractTextFromWord(Path docxPath) throws IOException {
        try (FileInputStream fis = new FileInputStream(docxPath.toFile());
             XWPFDocument document = new XWPFDocument(fis)) {
            
            return document.getParagraphs()
                    .stream()
                    .map(XWPFParagraph::getText)
                    .collect(Collectors.toList());
        }
    }

    /**
     * 一太郎 (.jtd) ファイルからテキストを抽出し、「段落」にまとめる
     */
    public List<String> extractTextFromIchitaro(Path jtdPath) throws IOException, InterruptedException {
        
        Path outputDir = this.textPorterOutputDir.toAbsolutePath();
        String inputFilePath = jtdPath.toAbsolutePath().toString();

        // 実行するコマンド
        ProcessBuilder pb = new ProcessBuilder(
                "java",
                "-Xss4m",
                "-cp", "dmcjava.jar:.",
                "Test",
                inputFilePath,
                "-t",
                outputDir.toString()
        );

        // 実行
        pb.directory(textPorterWorkDir.toFile());
        
        // 環境変数
        Map<String, String> env = pb.environment();
        env.put("LD_LIBRARY_PATH", "/app/Lib");
        env.put("DMC_TBLPATH", "/app/Lib/base2/");

        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);

        System.out.println("Executing command: " + String.join(" ", pb.command()));

        Process process = pb.start();
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("TextPorter process timed out.");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new IOException("TextPorter process failed with exit code: " + exitCode);
        }

        // 出力ファイルパスの特定
        String baseName = jtdPath.getFileName().toString();
        int dotIndex = baseName.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = baseName.substring(0, dotIndex);
        }
        Path expectedOutputFile = outputDir.resolve(baseName + ".txt");

        if (!Files.exists(expectedOutputFile)) {
            throw new IOException("TextPorter did not produce the expected output file: " + expectedOutputFile);
        }
        
        // ファイルから「行(Line)」のリストを読み込む (MS932で)
        List<String> lines = Files.readAllLines(expectedOutputFile, Charset.forName("MS932"));

        // 一時出力ファイルを削除
        Files.deleteIfExists(expectedOutputFile);
        
        // 「行」を「段落」にまとめるロジック
        List<String> paragraphs = new ArrayList<>();
        StringBuilder paragraphBuilder = new StringBuilder();

        for (String line : lines) {
            // ★★★ (修正点) ★★★
            // line.trim() では全角スペース(　)が削除できないため、正規表現で置換
            // ^[...]+ は先頭の、[...]+$ は末尾の空白文字(半角/全角/タブ)にマッチ
            String trimmedLine = line.replaceAll("^[\\s\\t\\u3000]+|[\\s\\t\\u3000]+$", "");
            
            if (trimmedLine.isEmpty()) {
                // 空行 = 段落の区切りとみなす
                if (paragraphBuilder.length() > 0) {
                    paragraphs.add(paragraphBuilder.toString());
                    paragraphBuilder.setLength(0); // ビルダーをリセット
                }
            } else {
                // テキスト行
                if (paragraphBuilder.length() > 0) {
                    paragraphBuilder.append("\n"); // 段落内の改行を追加
                }
                paragraphBuilder.append(trimmedLine);
            }
        }
        
        // 最後の段落が残っていれば追加
        if (paragraphBuilder.length() > 0) {
            paragraphs.add(paragraphBuilder.toString());
        }

        return paragraphs; // 「段落のリスト」を返す
    }
}
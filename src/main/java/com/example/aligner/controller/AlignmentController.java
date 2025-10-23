package com.example.aligner.controller;

import com.example.aligner.service.AlignmentService;
import com.example.aligner.service.FileStorageService;
import com.example.aligner.service.TextExtractionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.file.Path;
import java.util.List;

@Controller
public class AlignmentController {

    private final FileStorageService fileStorageService;
    private final TextExtractionService textExtractionService;
    private final AlignmentService alignmentService;

    // 必要なサービスをコンストラクタで注入
    public AlignmentController(FileStorageService fileStorageService,
                               TextExtractionService textExtractionService,
                               AlignmentService alignmentService) {
        this.fileStorageService = fileStorageService;
        this.textExtractionService = textExtractionService;
        this.alignmentService = alignmentService;
    }

    /**
     * トップページ (アップロードフォーム)
     */
    @GetMapping("/")
    public String showUploadForm() {
        return "index"; // templates/index.html を表示
    }

    /**
     * ファイルアップロードと突き合わせ処理
     */
    @PostMapping("/align")
    public String handleFileUpload(@RequestParam("jtdFile") MultipartFile jtdFile,
                                   @RequestParam("docFile") MultipartFile docFile,
                                   Model model,
                                   RedirectAttributes redirectAttributes) {

        if (jtdFile.isEmpty() || docFile.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "両方のファイルを選択してください。");
            return "redirect:/";
        }

        Path jtdPath = null;
        Path docPath = null;

        try {
            // 1. ファイルを一時保存
            jtdPath = fileStorageService.storeFile(jtdFile);
            docPath = fileStorageService.storeFile(docFile);

            // 2. 各ファイルからテキストを抽出
            // (一太郎の処理 は時間がかかる可能性がある)
            List<String> jpnParagraphs = textExtractionService.extractTextFromIchitaro(jtdPath);
            List<String> engParagraphs = textExtractionService.extractTextFromWord(docPath);

            // 3. 突き合わせ処理 (Azure OpenAI を使用)
            List<AlignmentService.ParagraphPair> alignmentResult = 
                alignmentService.alignParagraphs(jpnParagraphs, engParagraphs);

            // 4. 結果をモデルに設定
            model.addAttribute("alignmentResult", alignmentResult);
            model.addAttribute("jtdFileName", jtdFile.getOriginalFilename());
            model.addAttribute("docFileName", docFile.getOriginalFilename());

            return "result"; // templates/result.html を表示

        } catch (Exception e) {
            e.printStackTrace(); // サーバーログにスタックトレースを出力
            redirectAttributes.addFlashAttribute("errorMessage", "処理中にエラーが発生しました: " + e.getMessage());
            return "redirect:/";
        } finally {
            // 5. 処理が成功しても失敗しても、一時ファイルを削除
            if (jtdPath != null) fileStorageService.deleteFile(jtdPath);
            if (docPath != null) fileStorageService.deleteFile(docPath);
        }
    }
}
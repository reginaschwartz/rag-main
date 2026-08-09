package com.example.rag.document;

import com.example.rag.error.ApiException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

/** Turns an uploaded PDF or UTF-8 text file into plain text. */
@Component
public class ContentExtractor {

    public String extract(byte[] rawBytes, String source) {
        String name = source != null ? source.toLowerCase() : "";
        if (name.endsWith(".pdf")) {
            return extractPdf(rawBytes);
        }
        if (name.endsWith(".docx")) {
            return extractDocx(rawBytes);
        }
        return extractText(rawBytes);
    }

    private String extractDocx(byte[] rawBytes) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(rawBytes));
                XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            String content = extractor.getText();
            if (content == null || content.isBlank()) {
                throw ApiException.badRequest("Uploaded .docx has no extractable text.");
            }
            return content.strip();
        } catch (IOException exception) {
            throw ApiException.badRequest("Uploaded file could not be read as a .docx.");
        }
    }

    private String extractPdf(byte[] rawBytes) {
        try (PDDocument document = Loader.loadPDF(rawBytes)) {
            List<String> pages = new ArrayList<>(document.getNumberOfPages());
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document);
                pages.add(text != null ? text : "");
            }
            String content = String.join("\n", pages).strip();
            if (content.isEmpty()) {
                throw ApiException.badRequest("Uploaded PDF has no extractable text.");
            }
            return content;
        } catch (IOException exception) {
            throw ApiException.badRequest("Uploaded file could not be read as a PDF.");
        }
    }

    private String extractText(byte[] rawBytes) {
        String content;
        try {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            content = decoder.decode(ByteBuffer.wrap(rawBytes)).toString();
        } catch (CharacterCodingException exception) {
            throw ApiException.badRequest("Uploaded file must be UTF-8 text or PDF.");
        }
        if (content.isBlank()) {
            throw ApiException.badRequest("Uploaded file is empty.");
        }
        return content;
    }
}

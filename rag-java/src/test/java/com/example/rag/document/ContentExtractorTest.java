package com.example.rag.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.rag.error.ApiException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ContentExtractorTest {

    private final ContentExtractor extractor = new ContentExtractor();

    @Test
    void readsUtf8Text() {
        byte[] content = "Alice fell down the rabbit hole - naïvely.".getBytes(StandardCharsets.UTF_8);

        assertThat(extractor.extract(content, "alice.md")).isEqualTo("Alice fell down the rabbit hole - naïvely.");
    }

    @Test
    void rejectsEmptyText() {
        byte[] content = "   \n".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> extractor.extract(content, "empty.md"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Uploaded file is empty.");
    }

    @Test
    void rejectsNonUtf8Text() {
        byte[] content = {(byte) 0xC3, (byte) 0x28};

        assertThatThrownBy(() -> extractor.extract(content, "broken.txt"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Uploaded file must be UTF-8 text or PDF.")
                .extracting(exception -> ((ApiException) exception).status())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void readsPdfText() throws IOException {
        byte[] pdf = singlePagePdf("Interview material for the RAG service");

        assertThat(extractor.extract(pdf, "interview-material.PDF"))
                .contains("Interview material for the RAG service");
    }

    @Test
    void rejectsPdfWithoutText() throws IOException {
        byte[] pdf = emptyPdf();

        assertThatThrownBy(() -> extractor.extract(pdf, "blank.pdf"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Uploaded PDF has no extractable text.");
    }

    @Test
    void rejectsUnreadablePdf() {
        byte[] notAPdf = "this is not a pdf".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> extractor.extract(notAPdf, "broken.pdf"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Uploaded file could not be read as a PDF.");
    }

    private static byte[] singlePagePdf(String text) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(72, 700);
                contentStream.showText(text);
                contentStream.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] emptyPdf() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }
}

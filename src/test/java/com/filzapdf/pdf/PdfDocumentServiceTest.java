package com.filzapdf.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PdfDocumentServiceTest {

    @Test
    void opensPdfAndReadsPageCount() throws Exception {
        File pdf = new File("src/test/resources/sample.pdf");

        PdfDocumentService service = new PdfDocumentService();

        try (PDDocument document = service.open(pdf)) {
            assertEquals(2, document.getNumberOfPages());
        }
    }
}
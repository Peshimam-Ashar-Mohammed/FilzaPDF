package com.filzapdf.pdf;

import java.io.File;
import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

public class PdfDocumentService {

    public PDDocument open(File file) throws IOException {
        return Loader.loadPDF(file);
    }
}
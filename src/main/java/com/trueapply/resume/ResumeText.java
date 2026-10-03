package com.trueapply.resume;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Pulls plain text out of a resume file so any AI provider can read it. */
public final class ResumeText {
    private ResumeText() {
    }

    public static String extract(Path file) throws IOException {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) {
            try (PDDocument doc = Loader.loadPDF(file.toFile())) {
                return new PDFTextStripper().getText(doc).trim();
            }
        }
        if (name.endsWith(".txt") || name.endsWith(".md")) {
            return Files.readString(file).trim();
        }
        throw new IOException("Unsupported resume format: " + name + " (use PDF or TXT)");
    }
}

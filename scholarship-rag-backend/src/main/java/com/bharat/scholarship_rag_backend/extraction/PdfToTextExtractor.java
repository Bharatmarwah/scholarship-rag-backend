package com.bharat.scholarship_rag_backend.extraction;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Extracts raw text from every scholarship PDF and saves each as a .txt file.
 *
 * <p>Source: {@code src/main/resources/scholarships/**&#47;*.pdf}
 * <br>Output: mirrors the source tree under {@code src/main/resources/extracted-text/}
 *
 * <p>This is the first stage of the pipeline only: raw extraction, no cleaning,
 * no structuring, no chunking.
 */
public class PdfToTextExtractor {

    private static final String RESOURCES_DIR = "src/main/resources";
    private static final String SOURCE_DIR = RESOURCES_DIR + "/scholarships";
    private static final String OUTPUT_DIR = RESOURCES_DIR + "/extracted-text";
    private static final String PDF_EXTENSION = ".pdf";
    private static final String TEXT_EXTENSION = ".txt";

    public static void main(String[] args) throws IOException {
        List<Path> outputs = extractAll();

        System.out.println("Extracted " + outputs.size() + " PDF(s):");
        outputs.forEach(path -> System.out.println("  " + path));
    }

    /**
     * Extracts every PDF in the source directory into the output directory.
     * Returns the list of written .txt files.
     */
    public static List<Path> extractAll() throws IOException {
        List<Path> outputs = new ArrayList<>();

        for (Path pdf : findSourcePdfs()) {
            String text = extract(pdf, true);
            Path output = outputPathFor(pdf);

            Files.createDirectories(output.getParent());
            Files.writeString(output, text, StandardCharsets.UTF_8);

            outputs.add(output);
        }

        return outputs;
    }

    /**
     * Extracts the text of a single PDF, optionally tagged with page markers.
     */
    public static String extract(Path pdfPath) throws IOException {
        return extract(pdfPath, false);
    }

    public static String extract(Path pdfPath, boolean withPageMarkers) throws IOException {
        try (InputStream inputStream = Files.newInputStream(pdfPath)) {
            if (withPageMarkers) {
                return extractPerPage(inputStream);
            }
            return extractWholeDocument(inputStream);
        }
    }

    // ------------------------------------------------------- private helpers

    /** Locates the scholarships directory from the current working directory. */
    private static List<Path> findSourcePdfs() throws IOException {
        Path sourceRoot = resolveSourceRoot();
        if (!Files.isDirectory(sourceRoot)) {
            throw new IOException("Scholarships directory not found: " + sourceRoot.toAbsolutePath());
        }

        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            return stream
                    .filter(PdfToTextExtractor::isPdf)
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    private static boolean isPdf(Path path) {
        return path.toString().toLowerCase().endsWith(PDF_EXTENSION);
    }

    /**
     * Resolves src/main/resources/scholarships regardless of whether the app is
     * started from the repo root or from the module directory.
     */
    private static Path resolveSourceRoot() {
        Path workingDir = Path.of("").toAbsolutePath();
        List<Path> candidates = List.of(
                workingDir.resolve(SOURCE_DIR),
                workingDir.resolve("scholarship-rag-backend/" + SOURCE_DIR)
        );

        return candidates.stream()
                .filter(Files::isDirectory)
                .findFirst()
                .orElse(workingDir.resolve(SOURCE_DIR));
    }

    /** Maps a source PDF path to its mirrored .txt output path. */
    private static Path outputPathFor(Path pdfPath) {
        Path sourceRoot = resolveSourceRoot();
        Path relative = sourceRoot.relativize(pdfPath.toAbsolutePath());
        String relativeText = replaceExtension(relative.toString(), TEXT_EXTENSION);

        return workingDir().resolve(OUTPUT_DIR).resolve(relativeText);
    }

    private static String replaceExtension(String fileName, String newExtension) {
        if (!fileName.toLowerCase().endsWith(PDF_EXTENSION)) {
            return fileName + newExtension;
        }
        return fileName.substring(0, fileName.length() - PDF_EXTENSION.length()) + newExtension;
    }

    private static Path workingDir() {
        return Path.of("").toAbsolutePath();
    }

    /** Extracts the whole document's text with one stripper pass. */
    private static String extractWholeDocument(InputStream inputStream) throws IOException {
        try (PDDocument document = loadDocument(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    /** Extracts text page by page so each page is tagged: {@code --- Page N ---}. */
    private static String extractPerPage(InputStream inputStream) throws IOException {
        try (PDDocument document = loadDocument(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            StringBuilder result = new StringBuilder();
            int totalPages = document.getNumberOfPages();

            for (int page = 1; page <= totalPages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                result.append("--- Page ").append(page).append(" ---\n");
                result.append(stripper.getText(document));
                result.append("\n");
            }

            return result.toString();
        }
    }

    private static PDDocument loadDocument(InputStream inputStream) throws IOException {
        return Loader.loadPDF(inputStream.readAllBytes());
    }
}
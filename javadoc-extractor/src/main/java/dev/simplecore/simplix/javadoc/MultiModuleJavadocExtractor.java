package dev.simplecore.simplix.javadoc;

import java.io.IOException;
import java.util.List;

/**
 * Utility class for extracting Javadoc from a multi-module project.
 */
public class MultiModuleJavadocExtractor {

    /**
     * Main method to run the multi-module Javadoc extraction process.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: MultiModuleJavadocExtractor <project-root-dir> <output-excel-path>");
            System.exit(1);
        }

        String projectRootDir = args[0];
        String outputExcelPath = args[1];
        
        try {
            // Find all Java source directories in the project
            List<String> sourceDirectories = JavadocExtractor.findJavaSourceDirectories(projectRootDir);
            if (sourceDirectories.isEmpty()) {
                System.out.println("No Java source directories found in " + projectRootDir);
                System.exit(1);
            }
            
            System.out.println("Found " + sourceDirectories.size() + " Java source directories:");
            sourceDirectories.forEach(dir -> System.out.println("  - " + dir));
            
            // Parse Java source files and extract Javadoc
            for (String sourceDir : sourceDirectories) {
                System.out.println("Processing source directory: " + sourceDir);
                JavadocDoclet.parse(sourceDir);
            }
            
            // Get extracted Javadoc information
            List<ClassDocInfo> classDocInfos = JavadocDoclet.getClassDocInfos();
            System.out.println("Extracted Javadoc information for " + classDocInfos.size() + " classes");
            
            // Export to Excel
            JavadocExcelExporter exporter = new JavadocExcelExporter();
            exporter.exportToExcel(classDocInfos, outputExcelPath);
            
            System.out.println("Javadoc information exported to " + outputExcelPath);
            
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
package dev.simplecore.simplix.javadoc;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Main class for extracting Javadoc from source code and exporting to Excel.
 */
public class JavadocExtractor {

    /**
     * Main method to run the Javadoc extraction process.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: JavadocExtractor <source-root-dir> <output-excel-path>");
            System.exit(1);
        }

        String sourceRootDir = args[0];
        String outputExcelPath = args[1];
        
        try {
            // Find all Java source files
            List<String> sourceDirectories = findJavaSourceDirectories(sourceRootDir);
            if (sourceDirectories.isEmpty()) {
                System.out.println("No Java source directories found in " + sourceRootDir);
                System.exit(1);
            }
            
            System.out.println("Found " + sourceDirectories.size() + " Java source directories");
            
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
    
    /**
     * Find all Java source files in the given directory and its subdirectories.
     *
     * @param rootDir root directory to search
     * @return list of Java source file paths
     * @throws IOException if an I/O error occurs
     */
    public static List<String> findJavaFiles(String rootDir) throws IOException {
        List<String> javaFiles = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(Paths.get(rootDir))) {
            javaFiles = paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .map(Path::toString)
                .collect(Collectors.toList());
        }
        return javaFiles;
    }
    
    /**
     * Find all Java source directories in the project.
     *
     * @param projectRootDir project root directory
     * @return list of Java source directory paths
     * @throws IOException if an I/O error occurs
     */
    public static List<String> findJavaSourceDirectories(String projectRootDir) throws IOException {
        List<String> sourceDirectories = new ArrayList<>();
        
        // Find all src/main/java directories
        Path rootPath = Paths.get(projectRootDir);
        Files.walk(rootPath, 10)
            .filter(Files::isDirectory)
            .filter(path -> path.toString().endsWith(File.separator + "src" + File.separator + "main" + File.separator + "java"))
            .forEach(path -> sourceDirectories.add(path.toString()));
        
        return sourceDirectories;
    }
    
    /**
     * Run the Javadoc extraction process programmatically.
     *
     * @param sourceRootDir source root directory
     * @param outputExcelPath output Excel file path
     * @throws IOException if an I/O error occurs
     */
    public static void extract(String sourceRootDir, String outputExcelPath) throws IOException {
        main(new String[]{sourceRootDir, outputExcelPath});
    }
}
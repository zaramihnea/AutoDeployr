package com.analyzer.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for working with source files.
 */
public class SourceFileUtils {
    private static final Logger logger = LoggerFactory.getLogger(SourceFileUtils.class);

    /**
     * Find all Java files in a directory.
     *
     * @param directory Directory to search
     * @return List of Java files
     */
    public static List<File> findJavaFiles(String directory) {
        List<File> javaFiles = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(Paths.get(directory))) {
            javaFiles = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(Path::toFile)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            logger.error("Error finding Java files in {}: {}", directory, e.getMessage());
        }
        return javaFiles;
    }

    /**
     * Read the content of a file.
     *
     * @param file File to read
     * @return File content as string, or empty string if reading fails
     */
    public static String readFileContent(File file) {
        try {
            return Files.readString(file.toPath());
        } catch (IOException e) {
            logger.error("Error reading file {}: {}", file.getName(), e.getMessage());
            return "";
        }
    }

    /**
     * Get the relative path of a file from a base directory.
     *
     * @param baseDir Base directory
     * @param file File to get relative path for
     * @return Relative path
     */
    public static String getRelativePath(String baseDir, File file) {
        try {
            Path basePath = Paths.get(baseDir).toAbsolutePath();
            Path filePath = file.toPath().toAbsolutePath();
            return basePath.relativize(filePath).toString();
        } catch (Exception e) {
            logger.warn("Error getting relative path: {}", e.getMessage());
            return file.getName();
        }
    }

    /**
     * Create directories for a file path if they don't exist.
     *
     * @param filePath Path to file
     * @return True if directories exist or were created successfully
     */
    public static boolean ensureDirectoriesExist(String filePath) {
        try {
            Path path = Paths.get(filePath);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            return true;
        } catch (IOException e) {
            logger.error("Error creating directories for {}: {}", filePath, e.getMessage());
            return false;
        }
    }
}
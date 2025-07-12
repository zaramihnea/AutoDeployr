package com.infrastructure.services.deploy;

import com.application.dtos.response.DeploymentResponse;
import com.application.usecases.commandhandlers.DeployApplicationCommandHandler;
import com.application.usecases.commands.DeployApplicationCommand;
import com.infrastructure.exceptions.FileUploadException;
import com.infrastructure.services.config.EnvironmentVariableService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
public class FileUploadService {

    private static final Logger logger = LoggerFactory.getLogger(FileUploadService.class);
    private final DeployApplicationCommandHandler deployApplicationCommandHandler;
    private final EnvironmentVariableService environmentVariableService;

    @Value("${spring.servlet.multipart.max-file-size:50MB}")
    private String maxFileSize;

    /**
     * Process the uploaded zip file and deploy the application.
     * @param zipFile the uploaded zip file.
     * @param environmentVariables deployment environment variables.
     * @param userId current user's id.
     * @param appName custom application name (optional). If not specified, the original zip file is used as is.
     * @return DeploymentResponse from the command handler.
     */
    public DeploymentResponse processZipAndDeploy(MultipartFile zipFile,
                                                  Map<String, String> environmentVariables,
                                                  String userId,
                                                  String appName) {
        Path extractedFolderPath = null;
        Path targetDir = Path.of(System.getProperty("user.dir"), "uploaded-files");
        try {
            // Validate the uploaded file
            if (zipFile == null) {
                throw FileUploadException.operationFailed("validate", "No file provided");
            }
            if (zipFile.isEmpty()) {
                throw FileUploadException.emptyFile();
            }
            String originalFileName = zipFile.getOriginalFilename();
            if (originalFileName == null || !originalFileName.toLowerCase().endsWith(".zip")) {
                throw FileUploadException.unsupportedFileType(originalFileName != null ?
                        originalFileName.substring(originalFileName.lastIndexOf(".") + 1) : "unknown");
            }

            // Ensure the uploaded-files directory exists
            Files.createDirectories(targetDir);

            Path zipTargetPath;
            String folderName; // final folder name where content is extracted
            if (appName != null && !appName.isBlank()) {
                // Custom app name provided: rename zip file to appName.zip
                folderName = appName.replaceAll("[^a-zA-Z0-9-_]", "_");
                zipTargetPath = targetDir.resolve(folderName + ".zip");
                // Prepare final extraction directory
                extractedFolderPath = targetDir.resolve(folderName);
            } else {
                // No custom app name: leave zip as is.
                // Use original filename (without .zip extension) as folder name.
                folderName = originalFileName.replaceAll("(?i)\\.zip$", "");
                zipTargetPath = targetDir.resolve(originalFileName);
                extractedFolderPath = targetDir.resolve(folderName);
            }

            // If the target extraction folder exists, clean it up.
            if (Files.exists(extractedFolderPath)) {
                logger.info("Directory {} already exists. Cleaning it up before extraction.", extractedFolderPath);
                cleanupTempDirectory(extractedFolderPath);
            }

            // Copy the uploaded zip to the target directory (renaming if needed)
            Files.copy(zipFile.getInputStream(), zipTargetPath, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Saved ZIP file to: {}", zipTargetPath);

            if (appName != null && !appName.isBlank()) {
                Path tempExtractionDir = targetDir.resolve("temp_" + folderName);
                Files.createDirectories(tempExtractionDir);
                try {
                    extractZipFileSafely(zipTargetPath, tempExtractionDir);
                    logger.info("Extracted zip file into temporary folder: {}", tempExtractionDir);
                } catch (ZipException ze) {
                    logger.warn("Standard ZIP extraction failed: {}. Trying alternate method...", ze.getMessage());
                    extractZipFileAlternative(zipTargetPath, tempExtractionDir);
                    logger.info("Extracted zip file (alternative) into temporary folder: {}", tempExtractionDir);
                }
                Files.createDirectories(extractedFolderPath);
                Path finalExtractedFolderPath = extractedFolderPath;
                Files.list(tempExtractionDir).forEach(source -> {
                    try {
                        Files.move(source, finalExtractedFolderPath.resolve(source.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        logger.error("Failed to move {} to {}: {}", source, finalExtractedFolderPath, e.getMessage());
                    }
                });
                // Clean up the temporary extraction directory
                cleanupTempDirectory(tempExtractionDir);
            } else {
                try {
                    extractZipFileSafely(zipTargetPath, targetDir);
                    logger.info("Extracted zip file directly into: {}", targetDir);
                } catch (ZipException ze) {
                    logger.warn("Standard ZIP extraction failed: {}. Trying alternate method...", ze.getMessage());
                    extractZipFileAlternative(zipTargetPath, targetDir);
                    logger.info("Extracted zip file (alternative) directly into: {}", targetDir);
                }
                // Assume the zip file contains a folder with the same base name as the zip
                extractedFolderPath = targetDir.resolve(folderName);
            }

            // Delete the saved zip file after extraction
            Files.deleteIfExists(zipTargetPath);
            Map<String, String> processedEnvVars = processEnvironmentVariables(environmentVariables);

            // Determine the final app name for storage
            String finalAppName = appName != null && !appName.isBlank() ?
                    appName.replaceAll("[^a-zA-Z0-9-_]", "_") :
                    folderName;

            logger.info("Storing env vars with appName='{}', userId='{}'", finalAppName, userId);

            // Store environment variables securely in the database
            if (!processedEnvVars.isEmpty()) {
                int storedCount = environmentVariableService.storeEnvironmentVariables(
                        finalAppName,
                        userId,
                        processedEnvVars
                );
                logger.info("Securely stored {} environment variables for app: {}", storedCount, finalAppName);
            }

            // Build and send the deployment command via the command handler (no REST call)
            DeployApplicationCommand command = DeployApplicationCommand.builder()
                    .appPath(extractedFolderPath.toString())
                    .environmentVariables(processedEnvVars) // Pass the processed variables
                    .userId(userId)
                    .build();

            logger.info("Deploying application from path: {} for user: {} with {} environment variables",
                    extractedFolderPath, userId, processedEnvVars.size());

            DeploymentResponse response = deployApplicationCommandHandler.handle(command);

            // On successful or partial deployment, clean up the extracted folder
            if (response != null &&
                    ("success".equals(response.getStatus()) || "partial".equals(response.getStatus()))) {
                logger.info("Deployment succeeded, cleaning up directory: {}", extractedFolderPath);
                cleanupTempDirectory(extractedFolderPath);
            } else {
                logger.warn("Deployment failed, preserving directory for debugging: {}", extractedFolderPath);
            }

            return response;
        } catch (Exception e) {
            logger.error("Error processing zip file: {}", e.getMessage(), e);
            return DeploymentResponse.builder()
                    .status("error")
                    .error("Processing error: " + e.getMessage())
                    .message("Deployment failed")
                    .build();
        }
    }

    /**
     * Process environment variables from the request
     *
     * @param environmentVariables Raw environment variables from request
     * @return Processed environment variables
     */
    private Map<String, String> processEnvironmentVariables(Map<String, String> environmentVariables) {
        Map<String, String> processedEnvVars = new HashMap<>();

        // Check if environmentVariables is null and initialize if necessary
        if (environmentVariables == null) {
            logger.info("No environment variables provided");
            return processedEnvVars;
        }

        logger.info("Processing {} environment variables", environmentVariables.size());

        // Process each environment variable
        for (Map.Entry<String, String> entry : environmentVariables.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            // Skip empty keys
            if (key == null || key.trim().isEmpty()) {
                logger.warn("Skipping environment variable with empty key");
                continue;
            }
            if (value == null) {
                value = "";
            }
            processedEnvVars.put(key, value);
            logger.debug("Added environment variable: {}={}", key, value);
        }
        if (processedEnvVars.isEmpty()) {
            logger.warn("No valid environment variables processed");
        } else {
            logger.info("Successfully processed {} environment variables: {}", 
                      processedEnvVars.size(),
                      String.join(", ", processedEnvVars.keySet()));
        }

        return processedEnvVars;
    }

    /**
     * Extract a ZIP file using ZipFile (standard method).
     */
    private void extractZipFileSafely(Path zipPath, Path targetDir) throws IOException {
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            zipFile.stream().forEach(entry -> {
                try {
                    String entryName = entry.getName();
                    if (entryName.contains("..")) {
                        throw new SecurityException("Invalid path traversal: " + entryName);
                    }
                    Path entryPath = targetDir.resolve(entryName);
                    if (entry.isDirectory()) {
                        Files.createDirectories(entryPath);
                    } else {
                        if (entryPath.getParent() != null) {
                            Files.createDirectories(entryPath.getParent());
                        }
                        try (InputStream is = zipFile.getInputStream(entry);
                             OutputStream os = new FileOutputStream(entryPath.toFile())) {
                            byte[] buffer = new byte[8192];
                            int bytesRead;
                            while ((bytesRead = is.read(buffer)) != -1) {
                                os.write(buffer, 0, bytesRead);
                            }
                        }
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    /**
     * Alternative extraction using ZipInputStream.
     */
    private void extractZipFileAlternative(Path zipPath, Path targetDir) throws IOException {
        try (FileInputStream fis = new FileInputStream(zipPath.toFile());
             BufferedInputStream bis = new BufferedInputStream(fis);
             ZipInputStream zipIn = new ZipInputStream(bis)) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (entryName.contains("..")) {
                    throw new SecurityException("Invalid path traversal: " + entryName);
                }
                Path entryPath = targetDir.resolve(entryName);
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    if (entryPath.getParent() != null) {
                        Files.createDirectories(entryPath.getParent());
                    }
                    try (FileOutputStream fos = new FileOutputStream(entryPath.toFile());
                         BufferedOutputStream bos = new BufferedOutputStream(fos)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = zipIn.read(buffer)) != -1) {
                            bos.write(buffer, 0, read);
                        }
                    }
                }
                zipIn.closeEntry();
            }
        }
    }

    /**
     * Recursively delete the given directory.
     * Files that cannot be deleted immediately are scheduled for deletion on exit.
     */
    private void cleanupTempDirectory(Path directory) {
        try {
            if (Files.exists(directory)) {
                logger.info("Cleaning up directory: {}", directory);
                Files.walk(directory)
                        .sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                logger.warn("Failed to delete: {}. Scheduling deletion on exit.", path, e);
                                path.toFile().deleteOnExit();
                            }
                        });
                logger.info("Cleanup attempted for directory: {}", directory);
            }
        } catch (IOException e) {
            logger.warn("Error during cleanup of directory: {}. Exception: {}", directory, e.getMessage());
        }
    }

    /**
     * Save and extract a zip file (used by controller endpoints)
     * 
     * @param zipFile The MultipartFile containing the zip
     * @return Map containing the path to the extracted directory and the application name derived from zip filename
     * @throws IOException If file operations fail
     */
    public Map<String, String> saveAndExtractZipFile(MultipartFile zipFile) throws IOException {
        File uploadDir = new File("uploaded-files");
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }
        
        // Create a unique directory name for this upload
        String uniqueDirName = "upload_" + System.currentTimeMillis();
        File extractDir = new File(uploadDir, uniqueDirName);
        extractDir.mkdirs();
        
        // Get original filename and derive appName from it (remove .zip extension)
        String originalFilename = zipFile.getOriginalFilename();
        String appName = originalFilename;
        if (appName != null && appName.toLowerCase().endsWith(".zip")) {
            appName = appName.substring(0, appName.length() - 4);
        }
        
        // Save the zip file to disk using streams
        File zipFilePath = new File(extractDir, zipFile.getOriginalFilename());
        
        // Log file paths for debugging
        logger.info("Saving zip file to path: {}", zipFilePath.getAbsolutePath());
        
        try (InputStream inputStream = zipFile.getInputStream();
             OutputStream outputStream = new FileOutputStream(zipFilePath)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
        
        // Verify file was saved correctly
        if (!zipFilePath.exists()) {
            throw new IOException("Failed to save uploaded zip file to " + zipFilePath.getAbsolutePath());
        }
        
        // Extract the zip file
        String extractedPath = extractDir.getAbsolutePath() + File.separator + "extracted";
        File extractedDir = new File(extractedPath);
        extractedDir.mkdirs();
        
        // Use ZipFile for extraction
        try (java.util.zip.ZipFile zipArchive = new java.util.zip.ZipFile(zipFilePath)) {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zipArchive.entries();
            
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();
                
                // Skip macOS-specific files and directories
                if (entryName.startsWith("__MACOSX/") || entryName.contains("/._") || entryName.equals(".DS_Store")) {
                    logger.debug("Skipping macOS metadata file: {}", entryName);
                    continue;
                }
                
                String filePath = extractedPath + File.separator + entryName;
                
                if (entry.isDirectory()) {
                    new File(filePath).mkdirs();
                } else {
                    new File(filePath).getParentFile().mkdirs();
                    
                    try (InputStream in = zipArchive.getInputStream(entry);
                         BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath))) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            bos.write(buffer, 0, read);
                        }
                    }
                }
            }
        }
        
        logger.info("Extracted zip file to: {}", extractedPath);
        logger.info("Using application name from zip file: {}", appName);
        Map<String, String> result = new HashMap<>();
        result.put("path", extractedPath);
        result.put("appName", appName);
        return result;
    }

    /**
     * Recursively delete a directory and all its contents (alternative to cleanupTempDirectory)
     * 
     * @param directory The directory to delete
     * @return true if successful, false otherwise
     */
    public boolean deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        if (!file.delete()) {
                            logger.warn("Failed to delete file: {}", file.getAbsolutePath());
                        }
                    }
                }
            }
        }
        return directory.delete();
    }

    /**
     * Save and extract a zip file specifically for Java projects (used by controller endpoints)
     * This method handles the common case where a Java project is zipped with a root folder
     * 
     * @param zipFile The MultipartFile containing the zip
     * @return Map containing the path to the extracted directory and the application name derived from zip filename
     * @throws IOException If file operations fail
     */
    public Map<String, String> saveAndExtractJavaZipFile(MultipartFile zipFile) throws IOException {
        // Create directory for uploaded files if it doesn't exist
        File uploadDir = new File("uploaded-files");
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }
        
        // Create a unique directory name for this upload
        String uniqueDirName = "upload_" + System.currentTimeMillis();
        File extractDir = new File(uploadDir, uniqueDirName);
        extractDir.mkdirs();
        
        // Get original filename and derive appName from it (remove .zip extension)
        String originalFilename = zipFile.getOriginalFilename();
        String appName = originalFilename;
        if (appName != null && appName.toLowerCase().endsWith(".zip")) {
            appName = appName.substring(0, appName.length() - 4);
        }
        
        // Save the zip file to disk using streams
        File zipFilePath = new File(extractDir, zipFile.getOriginalFilename());

        logger.info("Saving Java zip file to path: {}", zipFilePath.getAbsolutePath());
        
        try (InputStream inputStream = zipFile.getInputStream();
             OutputStream outputStream = new FileOutputStream(zipFilePath)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
        
        // Verify file was saved correctly
        if (!zipFilePath.exists()) {
            throw new IOException("Failed to save uploaded zip file to " + zipFilePath.getAbsolutePath());
        }
        
        // Extract the zip file
        String extractedPath = extractDir.getAbsolutePath() + File.separator + "extracted";
        File extractedDir = new File(extractedPath);
        extractedDir.mkdirs();
        
        // Use ZipFile for extraction
        try (java.util.zip.ZipFile zipArchive = new java.util.zip.ZipFile(zipFilePath)) {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zipArchive.entries();
            
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();
                
                // Skip macOS-specific files and directories
                if (entryName.startsWith("__MACOSX/") || entryName.contains("/._") || entryName.equals(".DS_Store")) {
                    logger.debug("Skipping macOS metadata file: {}", entryName);
                    continue;
                }
                
                String filePath = extractedPath + File.separator + entryName;
                
                if (entry.isDirectory()) {
                    new File(filePath).mkdirs();
                } else {
                    new File(filePath).getParentFile().mkdirs();
                    
                    try (InputStream in = zipArchive.getInputStream(entry);
                         BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath))) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            bos.write(buffer, 0, read);
                        }
                    }
                }
            }
        }
        
        logger.info("Extracted Java zip file to: {}", extractedPath);

        File[] rootContents = extractedDir.listFiles();
        if (rootContents != null && rootContents.length == 1 && rootContents[0].isDirectory()) {
            File singleRootDir = rootContents[0];
            boolean isJavaProject = isJavaProjectDirectory(singleRootDir);
            
            if (isJavaProject) {
                logger.info("Detected Java project in single root folder '{}', flattening structure", singleRootDir.getName());
                
                // Move all contents from the single root directory to the extracted directory
                File[] javaProjectContents = singleRootDir.listFiles();
                if (javaProjectContents != null) {
                    for (File content : javaProjectContents) {
                        File destination = new File(extractedDir, content.getName());
                        if (content.isDirectory()) {
                            // Move directory
                            if (!content.renameTo(destination)) {
                                logger.warn("Failed to move directory {} to {}", content.getAbsolutePath(), destination.getAbsolutePath());
                            }
                        } else {
                            // Move file
                            if (!content.renameTo(destination)) {
                                logger.warn("Failed to move file {} to {}", content.getAbsolutePath(), destination.getAbsolutePath());
                            }
                        }
                    }
                }
                if (!singleRootDir.delete()) {
                    logger.warn("Failed to delete empty root directory: {}", singleRootDir.getAbsolutePath());
                }
                
                logger.info("Successfully flattened Java project structure");
            }
        }
        
        logger.info("Using application name from zip file: {}", appName);
        Map<String, String> result = new HashMap<>();
        result.put("path", extractedPath);
        result.put("appName", appName);
        return result;
    }
    
    /**
     * Check if a directory contains Java project files
     * 
     * @param directory Directory to check
     * @return true if it's a Java project directory
     */
    private boolean isJavaProjectDirectory(File directory) {
        if (!directory.exists() || !directory.isDirectory()) {
            return false;
        }
        
        File[] files = directory.listFiles();
        if (files == null) {
            return false;
        }
        for (File file : files) {
            String fileName = file.getName().toLowerCase();
            if (fileName.equals("pom.xml")) {
                return true;
            }
            if (fileName.equals("build.gradle") || fileName.equals("build.gradle.kts")) {
                return true;
            }
            if (fileName.equals("mvnw") || fileName.equals("gradlew")) {
                return true;
            }
            if (file.isDirectory() && fileName.equals("src")) {
                File mainDir = new File(file, "main");
                if (mainDir.exists() && mainDir.isDirectory()) {
                    File javaDir = new File(mainDir, "java");
                    if (javaDir.exists() && javaDir.isDirectory()) {
                        return true;
                    }
                }
            }
        }
        for (File file : files) {
            if (file.isFile() && file.getName().toLowerCase().endsWith(".java")) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Smart save and extract method that detects project type and uses appropriate extraction
     * 
     * @param zipFile The MultipartFile containing the zip
     * @return Map containing the path to the extracted directory and the application name derived from zip filename
     * @throws IOException If file operations fail
     */
    public Map<String, String> saveAndExtractZipFileAuto(MultipartFile zipFile) throws IOException {
        // First, do a quick detection by checking the zip contents without full extraction
        if (isJavaProjectZip(zipFile)) {
            logger.info("Detected Java project in zip file, using Java-specific extraction");
            return saveAndExtractJavaZipFile(zipFile);
        } else {
            logger.info("Using generic zip extraction for non-Java project");
            return saveAndExtractZipFile(zipFile);
        }
    }
    
    /**
     * Quick check to see if a zip file contains a Java project by examining its contents
     * 
     * @param zipFile The zip file to check
     * @return true if it appears to be a Java project
     */
    private boolean isJavaProjectZip(MultipartFile zipFile) {
        try (InputStream inputStream = zipFile.getInputStream();
             java.util.zip.ZipInputStream zipIn = new java.util.zip.ZipInputStream(inputStream)) {
            
            java.util.zip.ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                String entryName = entry.getName().toLowerCase();
                
                // Skip macOS metadata files
                if (entryName.startsWith("__macosx/") || entryName.contains("/._") || entryName.equals(".ds_store")) {
                    continue;
                }
                
                // Check for Java project indicators at any level
                String fileName = entry.getName();
                if (fileName.endsWith("/")) {
                    continue;
                }
                
                // Get just the filename (last part after /)
                String baseFileName = fileName.substring(fileName.lastIndexOf('/') + 1).toLowerCase();
                if (baseFileName.equals("pom.xml")) {
                    logger.debug("Found pom.xml, detected as Java project");
                    return true;
                }
                if (baseFileName.equals("build.gradle") || baseFileName.equals("build.gradle.kts")) {
                    logger.debug("Found Gradle build file, detected as Java project");
                    return true;
                }
                if (baseFileName.equals("mvnw") || baseFileName.equals("gradlew")) {
                    logger.debug("Found wrapper script, detected as Java project");
                    return true;
                }
                if (baseFileName.endsWith(".java")) {
                    logger.debug("Found Java source file, detected as Java project");
                    return true;
                }
                if (fileName.toLowerCase().contains("src/main/java/")) {
                    logger.debug("Found src/main/java structure, detected as Java project");
                    return true;
                }
            }
            
            return false;
        } catch (IOException e) {
            logger.warn("Error checking zip file contents, defaulting to generic extraction: {}", e.getMessage());
            return false;
        }
    }
}
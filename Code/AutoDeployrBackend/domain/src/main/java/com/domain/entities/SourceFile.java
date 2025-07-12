package com.domain.entities;

import com.domain.exceptions.ValidationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a source code file
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SourceFile {
    private String path;
    private String relativePath;
    private String content;
    private String language;

    /**
     * Validate the source file
     *
     * @throws ValidationException If the source file is invalid
     */
    public void validate() {
        if (path == null || path.trim().isEmpty()) {
            throw new ValidationException("path", "File path cannot be empty");
        }

        if (content == null) {
            throw new ValidationException("content", "File content cannot be null");
        }

        if (language == null || language.trim().isEmpty()) {
            throw new ValidationException("language", "Programming language cannot be empty");
        }
    }

    /**
     * Check if the file is empty
     *
     * @return true if the file is empty, false otherwise
     */
    public boolean isEmpty() {
        return content == null || content.trim().isEmpty();
    }

}
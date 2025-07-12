package com.infrastructure.services.transformer;

import com.domain.exceptions.ValidationException;
import com.infrastructure.exceptions.CodeAnalysisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Factory for creating appropriate function transformers based on language and framework
 */
@Component
public class FunctionTransformerFactory {
    private static final Logger logger = LoggerFactory.getLogger(FunctionTransformerFactory.class);

    private final PythonFunctionTransformer pythonTransformer;
    private final JavaFunctionTransformer javaTransformer;
    private final CSharpFunctionTransformer csharpTransformer;
    private final PhpServerlessFunctionTransformer phpTransformer;

    @Autowired
    public FunctionTransformerFactory(
            PythonFunctionTransformer pythonTransformer,
            JavaFunctionTransformer javaTransformer,
            CSharpFunctionTransformer csharpTransformer,
            PhpServerlessFunctionTransformer phpTransformer) {
        this.pythonTransformer = pythonTransformer;
        this.javaTransformer = javaTransformer;
        this.csharpTransformer = csharpTransformer;
        this.phpTransformer = phpTransformer;
    }

    /**
     * Create a function transformer for the given language and framework
     *
     * @param language Language identifier (e.g., "python", "java", "php")
     * @param framework Framework identifier (e.g., "flask", "spring", "laravel")
     * @return Function transformer
     * @throws ValidationException If language or framework is invalid
     * @throws CodeAnalysisException If the language/framework combination is not supported
     */
    public AbstractFunctionTransformer createTransformer(String language, String framework) {
        if (language == null || language.trim().isEmpty()) {
            throw new ValidationException("language", "Language cannot be empty");
        }

        if (framework == null || framework.trim().isEmpty()) {
            throw new ValidationException("framework", "Framework cannot be empty");
        }

        logger.info("Creating function transformer for language: {}, framework: {}", language, framework);

        if ("python".equalsIgnoreCase(language)) {
            if ("flask".equalsIgnoreCase(framework)) {
                return pythonTransformer;
            }
            throw new CodeAnalysisException("python", "Unsupported Python framework: " + framework);
        } else if ("java".equalsIgnoreCase(language)) {
            if ("spring".equalsIgnoreCase(framework)) {
                return javaTransformer;
            }
            throw new CodeAnalysisException("java", "Unsupported Java framework: " + framework);
        } else if ("csharp".equalsIgnoreCase(language) || "c#".equalsIgnoreCase(language)) {
            if ("aspnet".equalsIgnoreCase(framework)) {
                return csharpTransformer;
            }
            throw new CodeAnalysisException("csharp", "Unsupported C# framework: " + framework);
        } else if ("php".equalsIgnoreCase(language)) {
            if ("laravel".equalsIgnoreCase(framework)) {
                return phpTransformer;
            }
            throw new CodeAnalysisException("php", "Unsupported PHP framework: " + framework);
        }

        throw new CodeAnalysisException("Unsupported language: " + language);
    }
}
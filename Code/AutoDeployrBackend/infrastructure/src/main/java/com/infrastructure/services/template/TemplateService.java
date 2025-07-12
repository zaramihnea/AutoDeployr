package com.infrastructure.services.template;

import com.infrastructure.exceptions.TemplateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for loading and processing template files
 */
@Service
public class TemplateService {
    private static final Logger logger = LoggerFactory.getLogger(TemplateService.class);

    /**
     * Load a template from the classpath and process it with the given variables
     *
     * @param templatePath Path to the template file relative to classpath
     * @param variables Map of variable names to values
     * @return Processed template string
     * @throws TemplateException If the template cannot be loaded or processed
     */
    public String processTemplate(String templatePath, Map<String, String> variables) {
        try {
            String templateContent = loadTemplate(templatePath);
            return replaceVariables(templateContent, variables);
        } catch (IOException e) {
            logger.error("Error loading template {}: {}", templatePath, e.getMessage(), e);
            throw new TemplateException("Failed to load template: " + templatePath, e);
        } catch (Exception e) {
            logger.error("Error processing template {}: {}", templatePath, e.getMessage(), e);
            throw new TemplateException("Failed to process template: " + templatePath, e);
        }
    }

    /**
     * Load a template from the classpath
     *
     * @param templatePath Path to the template file relative to classpath
     * @return Template content as string
     * @throws IOException If the template cannot be loaded
     */
    private String loadTemplate(String templatePath) throws IOException {
        logger.debug("Loading template from: {}", templatePath);
        ClassPathResource resource = new ClassPathResource(templatePath);
        
        if (!resource.exists()) {
            logger.error("Template not found: {}", templatePath);
            throw new IOException("Template not found: " + templatePath);
        }
        
        byte[] bytes = resource.getInputStream().readAllBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Replace template variables with their values
     *
     * @param template Template string with variables in {{VARIABLE_NAME}} format
     * @param variables Map of variable names to values
     * @return Processed template with variables replaced
     */
    private String replaceVariables(String template, Map<String, String> variables) {
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("#")) {
                String varName = key.substring(1); // Remove the # prefix
                String value = entry.getValue();
                Pattern pattern = Pattern.compile("\\{\\{#" + varName + "\\}\\}((?s).*?)\\{\\{/" + varName + "\\}\\}");
                Matcher matcher = pattern.matcher(result);
                
                StringBuilder sb = new StringBuilder();
                int lastEnd = 0;
                
                while (matcher.find()) {
                    sb.append(result, lastEnd, matcher.start());
                    if (value != null && !value.isEmpty()) {
                        sb.append(matcher.group(1));
                    }
                    
                    lastEnd = matcher.end();
                }
                
                sb.append(result.substring(lastEnd));
                result = sb.toString();
            }
        }
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (!key.startsWith("#") && value != null) {
                String placeholder = "{{" + key + "}}";
                result = result.replace(placeholder, value);
            }
        }
        
        return result;
    }
} 
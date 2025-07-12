package com.infrastructure.services.template;

/**
 * Constants for template paths
 */
public class TemplateConstants {
    // Python templates
    public static final String PYTHON_DOCKERFILE_TEMPLATE = "templates/python/python_dockerfile_template.txt";
    public static final String PYTHON_FUNCTION_WRAPPER_TEMPLATE = "templates/python/python_function_wrapper_template.txt";
    public static final String PYTHON_ADAPTER_TEMPLATE = "templates/python/python_adapter_template.txt";
    public static final String PYTHON_INIT_TEMPLATE = "templates/python/python_init_template.txt";

    // Java templates
    public static final String JAVA_DOCKERFILE_TEMPLATE = "templates/java/java_dockerfile_template.txt";
    public static final String JAVA_FUNCTION_WRAPPER_TEMPLATE = "templates/java/java_function_wrapper_template.txt";
    public static final String JAVA_MAIN_APPLICATION_TEMPLATE = "templates/java/java_main_application_template.txt";
    public static final String JAVA_POM_TEMPLATE = "templates/java/java_pom_template.txt";
    public static final String JAVA_CONTROLLER_TEMPLATE = "templates/java/java_controller_template.txt";

    // C# templates
    public static final String CSHARP_DOCKERFILE_TEMPLATE = "templates/csharp/csharp_dockerfile_template.txt";
    public static final String CSHARP_FUNCTION_WRAPPER_TEMPLATE = "templates/csharp/csharp_function_wrapper_template.txt";
    public static final String CSHARP_PROJECT_TEMPLATE = "templates/csharp/csharp_project_template.txt";
    public static final String CSHARP_CONTROLLER_TEMPLATE = "templates/csharp/csharp_controller_template.txt";
    public static final String CSHARP_PROGRAM_TEMPLATE = "templates/csharp/csharp_program_template.txt";
    public static final String CSHARP_POST_FUNCTION_TEMPLATE = "templates/csharp/csharp_post_function_template.txt";
    public static final String CSHARP_JSON_ELEMENT_TEMPLATE = "templates/csharp/csharp_json_element_template.txt";
    
    // PHP templates (Serverless Mode - Like Java)
    public static final String PHP_DOCKERFILE_TEMPLATE = "templates/php/php_serverless_dockerfile_template.txt";
    public static final String PHP_FUNCTION_WRAPPER_TEMPLATE = "templates/php/php_serverless_function_template.txt";
    public static final String PHP_COMPOSER_TEMPLATE = "templates/php/php_composer_template.txt";
    public static final String PHP_SERVERLESS_DOCKERFILE_TEMPLATE = "templates/php/php_serverless_dockerfile_template.txt";
    public static final String PHP_SERVERLESS_FUNCTION_TEMPLATE = "templates/php/php_serverless_function_template.txt";
    
    private TemplateConstants() {
    }
} 
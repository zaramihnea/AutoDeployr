#!/bin/bash
echo "Building Spring Analyzer JAR..."
# Ensure we're in the correct directory
cd "$(dirname "$0")"

# Clean everything first to prevent TypeTag compilation errors
echo "Cleaning previous build artifacts..."
# Remove target directory completely to ensure clean state
rm -rf target/
# Clean with Maven
mvn clean

# Compile and package in one step to avoid intermediate state issues
echo "Compiling and packaging..."
mvn clean compile package -DskipTests

echo "Copying JAR to target location..."
cp target/SpringApplicationAnalyzer-*.jar target/spring-analyzer.jar
echo "Done!"
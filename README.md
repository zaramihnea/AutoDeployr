# AutoDeployr

🚀 **AutoDeployr** is an intelligent serverless platform that automatically transforms traditional applications into serverless functions with zero-configuration deployment across multiple programming languages and frameworks.

## 🎯 Overview

AutoDeployr is a serverless platform developed as a bachelor's thesis project at the Faculty of Computer Science, Alexandru Ioan Cuza University. It bridges the gap between traditional application development and serverless architecture by automatically analyzing, transforming, and deploying applications as serverless functions.

### Key Features

- **🔍 Intelligent Code Analysis**: Advanced AST parsing and static analysis for automatic dependency detection
- **🌐 Multi-Language Support**: Python (Flask), Java (Spring Boot), PHP (Laravel), and more
- **⚡ Zero-Configuration Deployment**: Automatic environment detection and configuration
- **📊 Real-time Metrics**: Comprehensive monitoring with execution times, success rates, and failure tracking
- **🔒 Secure Multi-Tenancy**: User isolation, API key management, and role-based access control
- **🎨 RESTful API**: Clean, documented API for all platform operations
- **📈 Scalable Architecture**: Domain-driven design with modular components

## 🏗️ Architecture

AutoDeployr follows a clean architecture pattern with clear separation of concerns:

```
AutoDeployr/
├── Code/
│   ├── AutoDeployrBackend/          # Spring Boot Backend
│   │   ├── domain/                  # Core business logic
│   │   ├── application/             # Application services
│   │   ├── infrastructure/          # External integrations
│   │   ├── webapi/                  # REST API layer
│   │   └── analyzers/               # Code analysis modules
│   ├── AutoDeployrFrontend/         # React Frontend
│   └── db_dump.sql                  # Database schema
└── Demo/                            # Example applications
    ├── bookmanager/                 # Flask + PostgreSQL + AI
    ├── news/                        # Spring Boot + RSS
    ├── taskmanager/                 # Laravel + Supabase
    └── weather/                     # Flask + External API
```

### Core Components

- **Function Entity Management**: Stores and manages serverless functions with metadata
- **Code Analyzers**: Language-specific analyzers for dependency extraction and transformation
- **Deployment Engine**: Handles containerization and deployment to serverless platforms
- **Metrics System**: Tracks function performance and usage statistics
- **Security Layer**: User authentication, authorization, and API key management

## 🚀 Getting Started

### Prerequisites

- Java 21+
- Maven 3.8+
- PostgreSQL 12+
- Node.js 16+ (for frontend)
- Docker (optional, for containerization)

### Backend Setup

1. **Clone the repository:**
```bash
git clone https://github.com/yourusername/AutoDeployr.git
cd AutoDeployr
```

2. **Database Setup:**
```bash
# Create PostgreSQL database
createdb autodeployr

# Import schema
psql -d autodeployr -f Code/db_dump.sql
```

3. **Build and Run:**
```bash
mvn clean install
mvn spring-boot:run
```

### Demo Application Setup

Each demo application has its own setup requirements:

#### Book Manager (Python)
```bash
cd Demo/bookmanager
pip install -r requirements.txt

# Configure environment variables
DB_HOST=localhost
DB_PASSWORD=your_password
GEMINI_API_KEY=your_gemini_key

python app.py
```

#### News Aggregator (Java)
```bash
cd Demo/news
mvn spring-boot:run
```

#### Task Manager (PHP)
```bash
cd Demo/taskmanager
composer install
php artisan serve
```

#### Weather Service (Python)
```bash
cd Demo/weather
pip install -r requirements.txt

# Configure environment variables
WEATHER_API_KEY=your_openweather_key

python app.py
```

## 🛠️ Supported Languages

| Language | Framework | Status | Example |
|----------|-----------|---------|---------|
| Python | Flask | ✅ Full Support | Book Manager, Weather Service |
| Java | Spring Boot | ✅ Full Support | News Aggregator |
| PHP | Laravel | ✅ Full Support | Task Manager |
| C# | .NET | 🔄 Coming Soon | - |
| Go | Gin | 🔄 Coming Soon | - |

## 📊 Database Schema

The platform uses PostgreSQL with the following key entities:

- **Users**: User management and authentication
- **Functions**: Serverless function metadata and source code
- **Function Metrics**: Performance and usage tracking
- **Environment Variables**: Configuration management
- **Dependencies**: Automatic dependency tracking

## 🔧 API Documentation

### Function Management

#### Create Function
```http
POST /api/v1/functions
Content-Type: application/json

{
  "name": "my-function",
  "language": "python",
  "framework": "flask",
  "source": "# Function source code here"
}
```

#### Deploy Function
```http
POST /api/v1/functions/{id}/deploy
```

#### Get Function Metrics
```http
GET /api/v1/functions/{id}/metrics
```

### User Management

#### Register User
```http
POST /api/v1/auth/register
Content-Type: application/json

{
  "username": "user",
  "email": "user@example.com",
  "password": "password"
}
```

#### Login
```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "username": "user",
  "password": "password"
}
```

## 🔒 Security Features

- **JWT Authentication**: Secure token-based authentication
- **API Key Management**: Per-function API keys for secure access
- **Role-Based Access Control**: User roles and permissions
- **Input Validation**: Comprehensive input sanitization
- **Rate Limiting**: Protection against abuse
- **CORS Configuration**: Cross-origin request handling

## 📈 Monitoring & Metrics

AutoDeployr provides comprehensive monitoring capabilities:

- **Execution Metrics**: Response times, success/failure rates
- **Usage Statistics**: Invocation counts, resource utilization
- **Error Tracking**: Detailed error logs and stack traces
- **Performance Analytics**: Historical performance data
- **Real-time Dashboards**: Live monitoring interfaces

## 📚 Academic Context

This project was developed as part of a bachelor's thesis at the Faculty of Computer Science, Alexandru Ioan Cuza University, Iași, Romania. The research focuses on:

- **Serverless Architecture Patterns**: Best practices for serverless design
- **Code Analysis Techniques**: Static analysis for dependency detection
- **Multi-Language Support**: Framework-agnostic serverless transformation

## ⚠️ Notice: This repository is publicly visible but does not accept external contributions, issues, or pull requests.

# GreenSuite - Carbon Footprint Management System

GreenSuite is a comprehensive carbon footprint tracking and management application that helps companies and individuals monitor, analyze, and reduce their environmental impact through AI-powered insights and detailed reporting.

## 🚀 Features

- **Carbon Footprint Tracking**: Monitor emissions from various activities
- **AI-Powered Analysis**: Get intelligent insights and recommendations
- **Multi-Company Support**: Manage multiple organizations and users
- **Real-time Reporting**: Generate detailed sustainability reports
- **User Management**: Role-based access control (Owner, Manager, Employee)
- **Document Processing**: AI-powered document ingestion and analysis
- **Vector Search**: Advanced semantic search capabilities

## 📋 Prerequisites

Before running GreenSuite, ensure you have the following installed:

- **Java 21** or higher
- **Maven 3.6** or higher
- **Docker** (optional, for containerized services)

## 🛠️ Installation & Setup

### 1. Install Ollama

Ollama provides local AI model serving. Install it for your platform:

#### macOS (Homebrew)
```bash
brew install ollama
```

#### Linux (Debian/Ubuntu)
```bash
curl -fsSL https://ollama.com/install.sh | sh
```

#### Windows (winget)
```powershell
winget install Ollama.Ollama
```

#### Verify Installation
```bash
ollama --version
```

### 2. Pull Required AI Models

Download the required AI models for chat and embeddings:

```bash
# Pull chat model for AI conversations
ollama pull gemma3:4b

# Pull embedding model for vector search
ollama pull nomic-embed-text:latest
```

### 3. Start Ollama Server

Start the Ollama service:

```bash
ollama serve
```

The server will run on `http://localhost:11434` by default.

### 4. Install and Configure Redis

Redis is used for rate limiting and caching.

#### macOS (Homebrew)
```bash
brew install redis
brew services start redis
```

#### Linux (Ubuntu/Debian)
```bash
sudo apt update
sudo apt install redis-server
sudo systemctl start redis-server
sudo systemctl enable redis-server
```

#### Windows
```powershell
# Using Chocolatey
choco install redis-64

# Or download from https://redis.io/download
```

#### Verify Redis
```bash
redis-cli ping
# Should return: PONG
```

### 5. Set Up PostgreSQL Database

PostgreSQL is used for chat memory and session management.

#### Install PostgreSQL

**macOS:**
```bash
brew install postgresql
brew services start postgresql
```

**Linux (Ubuntu/Debian):**
```bash
sudo apt update
sudo apt install postgresql postgresql-contrib
sudo systemctl start postgresql
sudo systemctl enable postgresql
```

**Windows:**
Download from https://www.postgresql.org/download/windows/

#### Create Database and User

```sql
-- Connect to PostgreSQL as superuser
sudo -u postgres psql

-- Create database
CREATE DATABASE chat_memory_greensuite;

-- Create user
CREATE USER postgres WITH PASSWORD 'admin';

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE chat_memory_greensuite TO postgres;

-- Exit
\q
```

### 6. Set Up MongoDB Atlas

MongoDB Atlas is used for the main application data and vector storage.

#### Create MongoDB Atlas Account
1. Go to [MongoDB Atlas](https://www.mongodb.com/atlas)
2. Create a free account or sign in
3. Create a new cluster (M0 Free tier is sufficient for development)

#### Configure Network Access
1. In your Atlas dashboard, go to **Network Access**
2. Click **"Add IP Address"**
3. For development: Add `0.0.0.0/0` (allows access from anywhere)
4. For production: Add specific IP addresses

#### Create Database User
1. Go to **Database Access**
2. Click **"Add New Database User"**
3. Create a user with these credentials:
   - **Username**: `root`
   - **Password**: `admin` (use a strong password in production)
   - **Role**: `Atlas admin` (for development)

#### Get Connection String
1. Click **"Connect"** on your cluster
2. Choose **"Connect your application"**
3. Copy the connection string
4. Replace `<password>` with your actual password

#### Create Public User (Optional)
For public vector store access, create a separate user:
1. Go to **Database Access**
2. Create a new user:
   - **Username**: `public_user`
   - **Password**: `public_password`
   - **Role**: `Read and write to any database`

### 7. Configure Application Properties

Update `src/main/resources/application.properties` with your database credentials:

```properties
# PostgreSQL Configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/chat_memory_greensuite
spring.datasource.username=postgres
spring.datasource.password=admin

# MongoDB Atlas Configuration
spring.ai.vectorstore.mongodb.atlas.uri=mongodb+srv://root:your_password@your_cluster.reg096x.mongodb.net/?retryWrites=true&w=majority&appName=YourCluster
spring.ai.vectorstore.mongodb.atlas.database=green_suite_vectors

# Main Application Database
spring.data.mongodb.uri=mongodb://localhost:27017/mongoGreenSuite
spring.data.mongodb.database=mongoGreenSuite
```

### 8. Build and Run the Application

#### Clone the Repository
```bash
git clone <your-repository-url>
cd GreenSuite_BackEnd-main
```

#### Build the Application
```bash
mvn clean install
```

#### Run the Application
```bash
mvn spring-boot:run
```

The application will start on `http://localhost:8080`

## 🔧 Configuration

### Environment Variables

For production deployment, use environment variables:

```bash
export VECTOR_STORE_URI="your_mongodb_atlas_uri"
export VECTOR_STORE_DB="green_suite_vectors"
export MAIN_MONGODB_URI="your_main_mongodb_uri"
export MAIN_MONGODB_DATABASE="mongoGreenSuite"
export POSTGRES_URL="your_postgres_url"
export POSTGRES_USERNAME="your_postgres_username"
export POSTGRES_PASSWORD="your_postgres_password"
```

### JWT Configuration

Update JWT settings in `application.properties`:

```properties
app.jwt.secret=your-secure-jwt-secret-key
app.jwt.expiration-ms=900000
app.jwt.refresh-expiration-ms=604800000
```

## 🧪 Testing

Run the test suite:

```bash
mvn test
```

## 📚 API Documentation

Once the application is running, access the API documentation:

- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **API Endpoints**: `http://localhost:8080/api/`

## 🔐 Security

### Development vs Production

**Development:**
- Uses default passwords and local databases
- CORS enabled for localhost
- Detailed error messages

**Production:**
- Use strong, unique passwords
- Enable HTTPS
- Configure proper CORS origins
- Use environment variables for sensitive data
- Enable security headers

### User Roles

- **OWNER**: Full access to company data and settings
- **MANAGER**: Manage employees and view reports
- **EMPLOYEE**: Basic access to input data and view personal reports

## 🚀 Deployment

### Docker Deployment

```bash
# Build Docker image
docker build -t greensuite .

# Run container
docker run -p 8080:8080 greensuite
```

### Cloud Deployment

The application can be deployed to:
- **AWS**: Using Elastic Beanstalk or ECS
- **Google Cloud**: Using App Engine or GKE
- **Azure**: Using App Service or AKS
- **Heroku**: Using the provided Procfile

## 📊 Monitoring

### Health Checks
- **Health Endpoint**: `http://localhost:8080/actuator/health`
- **Metrics**: `http://localhost:8080/actuator/metrics`

### Logging
Logs are written to `logs/application.log` with the following levels:
- **INFO**: General application logs
- **DEBUG**: Detailed debugging information
- **ERROR**: Error and exception logs

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Submit a pull request

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🆘 Support

For support and questions:
- Create an issue in the repository
- Contact the development team
- Check the documentation

## 🔄 Updates

Keep your dependencies updated:

```bash
# Update Maven dependencies
mvn versions:use-latest-versions

# Update Ollama models
ollama pull gemma3:4b:latest
ollama pull nomic-embed-text:latest
```

---

**Note**: This is a development setup. For production deployment, ensure all security best practices are followed, including using strong passwords, enabling HTTPS, and properly configuring network security.

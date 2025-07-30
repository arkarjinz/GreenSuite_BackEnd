# Using Ollama with Spring AI

Follow these steps to install Ollama, pull the models you need, and configure your Spring application.

---

## 1. Install Ollama

Ollama provides a CLI to manage and serve AI models locally. Install it by following the instructions for your platform:

- **macOS (Homebrew)**  
  ```bash
  brew install ollama
  ```

- **Linux (Debian/Ubuntu)**  
  ```bash
  curl -fsSL https://ollama.com/install.sh | sh
  ```

- **Windows (winget)**  
  ```powershell
  winget install Ollama.Ollama
  ```

Check the installation:

```bash
ollama --version
```

---

## 2. Pull the Models

You can pull any Ollama-compatible models. For example, to pull a chat model and an embedding model:

```bash
ollama pull gemma3:4b
ollama pull nomic-embed-text:latest
```

> **Tip:** Replace `gemma3:4b` and `nomic-embed-text:latest` with whichever models you prefer.

---

## 3. Serve the Models

Once pulled, start the Ollama server:

```bash
ollama serve
```

By default, Ollama will host the models on `http://localhost:11434`.

---

## 4. Configure Your Spring Application

In your `application.properties` (or `application.yml`), set the model names:

```properties
spring.ai.ollama.chat.options.model=gemma3:4b
spring.ai.ollama.embedding.options.model=nomic-embed-text:latest
```

> **Note:** If you pulled different models, simply replace the values above with your chosen model names.

---

## 5. Run Your Application

Start your Spring Boot app as usual:

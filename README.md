```markdown
# sysmind-mcp
![License](https://img.shields.io/badge/license-MIT-blue.svg)
This project, `sysmind-mcp`, is a microservice designed to manage interactions with Large Language Models (LLMs) within the sysmind ecosystem. It provides robust functionalities for sending prompts and receiving conversational responses from external LLM APIs.

## ✨ Features

*   **LLM Integration:** Seamlessly integrates with external generative AI services using `WebClient`.
*   **Chat Completion Support:** Supports standard chat completion endpoints (`/v1/chat/completions`).
*   **Modular Design:** Built on a modern Java stack (Spring Boot) for scalability and maintainability.

## 🚀 Getting Started

These instructions will get you a copy of the project up and running on your local machine for development and testing purposes.

### Prerequisites

You must have Java Development Kit (JDK) 21 or newer installed, as this project utilizes modern Java features. You also need Apache Maven (`mvnw`) or Gradle to manage dependencies.

### Installation

1.  **Clone the Repository:**
```shell script
git clone <repository-url>
cd sysmind-mcp
```


2.  **Build and Run (Using Maven Wrapper):**
    Execute the following command in the project root directory to download dependencies, compile the code, and start the application:
```shell script
./mvnw spring-boot:run
    # or on Windows:
.\mvnw.cmd spring-boot:run
```


## ⚙️ Configuration

The service is configured to connect to a specific LLM endpoint by default. You may need to adjust this configuration depending on your deployment environment.

Currently, the `LLMService` targets an internal host for the LLM API:

*   **Base URL:** `http://127.0.0.1:1234`
*   **Default Model:** `gemma-4b`

### Environment Variables / Configuration Files

For production environments, update your configuration (`application.properties` or environment variables) to point to the correct LLM provider URL and model name.

## 🛠️ Usage Example (Conceptual)

The core functionality is exposed via the `LLMService`. Developers can call the `ask(String prompt)` method to retrieve a response from the configured LLM endpoint.

**Example Interaction:**

If you were using this service in another part of your application, calling:
`llmService.ask("What is quantum computing?")`
...would send the request to the remote LLM and return the generated text as a string.

## 🤝 Contributing

Contributions are welcome! If you find bugs or have ideas for improvements, please open an issue or submit a pull request.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](./LICENSE.md) file for details.
```

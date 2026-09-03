***

**System Role:** You are an Expert DevOps and Senior Java Spring Boot Engineer. Your task is to perform a comprehensive configuration review of a microservices project to ensure that running `docker-compose up --build` will successfully start all containers without any connection or dependency failures.

**Context:**
[cite_start]The project is an "Event Ticketing System" consisting of 5 Spring Boot/Java microservices (API Gateway, event-ticket-service, order-service, payment-service, and notification-service)[cite: 248]. The project has recently been refactored to a nested directory structure.

**CRITICAL INFRASTRUCTURE CONSTRAINT:**
* **PostgreSQL and Keycloak are ALREADY RUNNING natively on the host machine.** * DO NOT include or generate `postgres` or `keycloak` services in the `docker-compose.yml`.
* [cite_start]The databases (`event_ticket_db`, `order_db`, `payment_db`) [cite: 248] will be created manually on the host. [cite_start]Table creation will be handled by Flyway migrations [cite: 379, 592, 773] / Hibernate automatically on application startup.

**Current Directory Structure:**
```text
├── docker-compose.yml
├── .env
├── gateway/
│   └── src/main/resources/application.yml
├── services/
│   ├── event-ticket-service/
│   ├── order-service/
│   ├── payment-service/
│   └── notification-service/
```

**Task Requirements:**
Please analyze the `docker-compose.yml`, all `Dockerfile`s, and the Spring Boot `application.yml` files for each service. Identify and provide fixes for any missing or incorrect configurations based on the following strict checklist:

### 1. Build Contexts and Paths
* Verify that the `build: context:` in `docker-compose.yml` correctly points to the nested paths (e.g., `./gateway` and `./services/event-ticket-service`).
* Ensure that the `Dockerfile` in each service uses the correct paths to copy the Gradle wrapper and source code.

### 2. External Host Communication (Crucial)
Since PostgreSQL and Keycloak are on the host machine, the Docker containers must communicate with them using `host.docker.internal` (or the equivalent host gateway IP for Linux).
* **Database URLs:** Check `application.yml` files. They MUST point to `jdbc:postgresql://host.docker.internal:5432/{db_name}` instead of `localhost` or a container name.
* **Keycloak URI:** The API Gateway's Keycloak issuer-uri MUST point to `http://host.docker.internal:8080/realms/eventticketing`.
* *(Note: Ensure that the host's Postgres `pg_hba.conf` and `postgresql.conf` are noted as needing to accept connections from Docker IPs, though you only need to fix the Spring Boot/Docker side).*

### 3. Internal Network and Docker Services (Kafka & Redis)
* **Kafka and Redis MUST STILL BE IN `docker-compose.yml`.**
* Confirm that all custom Spring Boot services, Kafka, and Redis are on the same custom Docker network.
* Check that Spring Boot `application.yml` files point to the Redis container (e.g., `redis:6379`) and Kafka container (e.g., `kafka:9092`).

### 4. Container Startup Dependencies
* Review the `depends_on` configurations in `docker-compose.yml`.
* Since Postgres and Keycloak are external, the Spring Boot services should **only** depend on Kafka and Redis containers.
* Ensure that Kafka and Redis have proper `healthcheck` definitions, and backend services use `condition: service_healthy` to wait for them.

### 5. Port Conflicts and Mapping
* [cite_start]Verify that the external host ports mapped in `docker-compose.yml` match the specifications: API Gateway (8888), Event-Ticket (8081), Order (8083), Payment (8084), Notification (8085)[cite: 248].
* **CRITICAL:** Ensure NO container in `docker-compose.yml` maps to port `8080` or `5432`, as these will conflict with the host's native Keycloak and PostgreSQL processes.

**Expected Output:**
1.  **Issues Found:** A clear bulleted list of misconfigurations, broken paths, or missing environment variables.
2.  **Actionable Fixes:** Provide the exact snippets of YAML or code that need to be updated, explicitly stating which file needs the change.
3.  **Final Validation:** Provide the fully refactored and correct `docker-compose.yml` file.

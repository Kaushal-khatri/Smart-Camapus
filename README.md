# Smart Campus Sensor & Room Management API
### 5COSC022W Client-Server Architectures — Coursework 2025/26

**Author:** [Kaushal-khatri](https://github.com/Kaushal-khatri)

A RESTful API built with **JAX-RS (Jersey 2.39)** and an embedded **Jetty** server that manages university campus rooms and IoT sensors.

---

## API Overview

The API models three core resources:
- **Room** — A physical space with an ID, name, capacity, and a list of deployed sensor IDs.
- **Sensor** — An IoT device with a type (e.g., Temperature, CO2), a live status (ACTIVE / MAINTENANCE / OFFLINE), a current value, and a parent room reference.
- **SensorReading** — An immutable historical data point (UUID, epoch timestamp, measured value) appended to a sensor's log.

All data is stored in `ConcurrentHashMap` and `ArrayList` structures held in a singleton `DataStore`. No database is used.

---

## Build & Run Instructions

### Prerequisites
- Java 11 or higher
- Apache Maven 3.6+

### Steps

```bash
# 1. Clone the repository
git clone https://github.com/YOUR_USERNAME/smart-campus-api.git
cd smart-campus-api

# 2. Build the fat JAR (includes all dependencies)
mvn clean package

# 3. Run the server
java -jar target/smart-campus-api.jar
```

The server starts at **http://localhost:8080/api/v1**

---

## Sample curl Commands

```bash
# 1. Discovery — GET /api/v1
curl -X GET http://localhost:8080/api/v1

# 2. List all rooms — GET /api/v1/rooms
curl -X GET http://localhost:8080/api/v1/rooms

# 3. Create a room — POST /api/v1/rooms
curl -X POST http://localhost:8080/api/v1/rooms \
  -H "Content-Type: application/json" \
  -d '{"id":"ENG-205","name":"Engineering Lab","capacity":25}'

# 4. Register a sensor — POST /api/v1/sensors
curl -X POST http://localhost:8080/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id":"CO2-999","type":"CO2","status":"ACTIVE","currentValue":400,"roomId":"ENG-205"}'

# 5. Filter sensors by type — GET /api/v1/sensors?type=CO2
curl -X GET "http://localhost:8080/api/v1/sensors?type=CO2"

# 6. Post a sensor reading — POST /api/v1/sensors/CO2-999/readings
curl -X POST http://localhost:8080/api/v1/sensors/CO2-999/readings \
  -H "Content-Type: application/json" \
  -d '{"value":435.7}'

# 7. Get reading history — GET /api/v1/sensors/CO2-999/readings
curl -X GET http://localhost:8080/api/v1/sensors/CO2-999/readings

# 8. Delete an empty room — DELETE /api/v1/rooms/ENG-205 (after removing sensor first)
curl -X DELETE http://localhost:8080/api/v1/rooms/ENG-205

# 9. Attempt to delete a room with sensors (triggers 409)
curl -X DELETE http://localhost:8080/api/v1/rooms/LIB-301

# 10. Attempt to post reading to MAINTENANCE sensor (triggers 403)
curl -X POST http://localhost:8080/api/v1/sensors/OCC-001/readings \
  -H "Content-Type: application/json" \
  -d '{"value":10}'
```

---

## Conceptual Report (Answers to Coursework Questions)

---

### Part 1 — Service Architecture & Setup

**Q: Explain the default lifecycle of a JAX-RS Resource class. Is a new instance created per request or as a singleton? How does this impact in-memory data management?**

By default, JAX-RS follows a **per-request lifecycle**: the runtime instantiates a brand-new resource class object for every incoming HTTP request, and that object is garbage collected when the request completes. This is defined in the JAX-RS specification and is the default for any class annotated with `@Path`.

This design has a critical implication for in-memory state. Since each request gets its own resource object, **any instance fields declared inside the resource class would be re-initialised to their defaults on every request**, causing all stored data to be lost between calls. To prevent this, a shared, static data store is required.

In this implementation, a `DataStore` singleton (using the classic static initialiser pattern) holds all state in `ConcurrentHashMap<String, Room>` and `ConcurrentHashMap<String, Sensor>`. Because multiple concurrent requests may simultaneously read and write these maps, thread safety is essential. `ConcurrentHashMap` guarantees atomic, lock-free read and write operations at the bucket level, preventing race conditions and data corruption without the performance cost of wrapping every operation in a `synchronized` block. The `addReading()` method uses explicit `synchronized` to protect the two-step operation of `putIfAbsent` + `add`, keeping that compound action atomic.

---

**Q: Why is Hypermedia (HATEOAS) considered a hallmark of advanced RESTful design? How does it benefit client developers?**

HATEOAS — Hypermedia As The Engine Of Application State — is the principle that API responses should include links that describe what the client can do next, rather than forcing the client to hard-code URLs from documentation.

This matters for several reasons. First, it makes APIs **self-documenting at runtime**: a client hitting `GET /api/v1` immediately learns the available resource endpoints without reading any external documentation. Second, it **decouples client from server**: if the server moves a resource from `/rooms` to `/spaces`, clients following hypermedia links update automatically rather than breaking. Third, it **reduces client logic**: clients do not need to construct URLs by string concatenation; they follow the links they receive. This mirrors the way humans browse the web — you do not memorise URLs, you click provided links. The Discovery endpoint in this API provides a `resources` map returning exact paths for `rooms` and `sensors`, which is a lightweight but practical application of HATEOAS.

---

### Part 2 — Room Management

**Q: What are the implications of returning only IDs versus full room objects in a list response?**

Returning **full objects** (as implemented here) is appropriate when the client typically needs the details of each room immediately after receiving the list — for example, rendering a table in a dashboard. It reduces the number of round trips to one but increases the payload size proportionally with the number of rooms.

Returning **IDs only** is appropriate for very large datasets where the client may only need a subset of records. The client would then make individual `GET /rooms/{id}` calls for the specific rooms it needs. However, this incurs the N+1 problem: fetching a list of 500 room IDs and then making 500 individual requests is highly inefficient. A mature API design might address this through pagination (`?page=1&size=20`) or sparse fieldsets, but for a campus-scale system the full-object approach is the correct pragmatic choice.

---

**Q: Is the DELETE operation idempotent in your implementation? Justify what happens on repeated calls.**

Yes, the `DELETE /api/v1/rooms/{roomId}` operation is **idempotent** by the HTTP specification's definition: repeated calls produce the same server state. Specifically:

- **First call**: The room exists and has no sensors → the room is removed → HTTP 204 No Content is returned.
- **Second call**: The room no longer exists → HTTP 404 Not Found is returned.

Critically, the **server state after both calls is identical** — the room is absent from the system in both cases. The 404 response on the second call does not violate idempotency; the specification requires the *effect* to be the same, not necessarily the response code. The room is not created or modified by a repeated delete, which is all idempotency guarantees.

---

### Part 3 — Sensor Operations & Filtering

**Q: Explain the technical consequences if a client sends data as `text/plain` or `application/xml` to a `@Consumes(APPLICATION_JSON)` endpoint.**

When a JAX-RS resource method is annotated with `@Consumes(MediaType.APPLICATION_JSON)`, the runtime uses **content negotiation** to match the incoming `Content-Type` request header against the declared consumed media types. If a client sends a body with `Content-Type: text/plain`, the Jersey runtime cannot find a matching resource method for that content type and responds with **HTTP 415 Unsupported Media Type** — automatically, without the method body ever being invoked. No custom error handling code is required; JAX-RS handles the mismatch at the framework level. This protects the application from unexpected data formats and ensures the Jackson message body reader is only invoked when valid JSON is present.

---

**Q: Why is `@QueryParam` (`/sensors?type=CO2`) generally superior to a path segment (`/sensors/type/CO2`) for filtering?**

The distinction between path parameters and query parameters reflects a fundamental REST principle: **path segments identify resources, query parameters refine or filter them**.

`/sensors/type/CO2` implies that `type/CO2` is itself a resource — a distinct entity in the resource hierarchy. This is semantically misleading, it makes the URL structure rigid, and it conflicts with the sensor-by-ID path `/sensors/{sensorId}`, creating ambiguity that the router must resolve.

`/sensors?type=CO2` correctly communicates that the client is requesting the `sensors` collection with an optional refinement applied. Query parameters are inherently optional, composable (`?type=CO2&status=ACTIVE`), and universally understood as filters. They do not change the identity of the resource being addressed — they modify the *representation* returned for that resource. This is also consistent with how search engines and REST standards (RFC 3986) treat query strings.

---

### Part 4 — Deep Nesting with Sub-Resources

**Q: Discuss the architectural benefits of the Sub-Resource Locator pattern.**

The Sub-Resource Locator pattern allows a resource class to delegate responsibility for a nested path to a separate class instance, rather than defining every endpoint in one monolithic controller. In this API, `SensorResource.getReadingsResource()` is annotated with `@Path("/{sensorId}/readings")` but no HTTP method annotation — this tells JAX-RS to invoke it as a locator and then dispatch the actual HTTP method to the returned `SensorReadingResource` instance.

The benefits are significant:
1. **Separation of concerns**: `SensorResource` manages sensor CRUD; `SensorReadingResource` manages the reading log. Each class has a single responsibility.
2. **Contextual injection**: The locator can pass the resolved `sensorId` (and the validated `Sensor` object) directly into the constructor of the sub-resource, so the sub-resource doesn't need to re-query the data store.
3. **Scalability**: As the API grows (e.g., adding `/sensors/{id}/alerts` or `/sensors/{id}/config`), new sub-resource classes can be introduced without bloating the parent.
4. **Testability**: Each class is independently unit-testable with a focused set of responsibilities.

In contrast, defining all nested paths (`/sensors`, `/sensors/{id}`, `/sensors/{id}/readings`, `/sensors/{id}/readings/{rid}`) in one class creates a single-responsibility violation and makes the class extremely difficult to maintain.

---

### Part 5 — Error Handling & Logging

**Q: Why is HTTP 422 more semantically accurate than 404 when a referenced resource is missing inside a valid JSON payload?**

HTTP 404 Not Found means "the resource at the URL you requested does not exist." If a client sends `POST /api/v1/sensors` with a valid URL and a well-formed JSON body containing `"roomId": "ROOM-999"`, the requested resource (`/api/v1/sensors`) absolutely does exist — the endpoint is live and functioning. Returning 404 here would be misleading and would cause clients to believe the sensors endpoint itself is missing.

HTTP 422 Unprocessable Entity is semantically precise: "I understood the request, I parsed the JSON, but the business logic cannot be completed because the payload contains a reference to a non-existent entity." The problem is not with the URL, nor with the JSON syntax — it is a semantic validation failure. The RFC 4918 specification defines 422 exactly for this scenario: a well-formed request that is semantically erroneous. This gives client developers a clear, unambiguous signal that they need to fix the value inside their JSON body, not the URL they are calling.

---

**Q: From a cybersecurity standpoint, explain the risks of exposing Java stack traces to external API consumers.**

A raw Java stack trace is a detailed map of the application's internal architecture and is one of the most valuable pieces of reconnaissance data an attacker can obtain without any special tools. Specific risks include:

1. **Technology fingerprinting**: The trace reveals the exact Java version, framework (e.g., `org.glassfish.jersey`), library versions, and class names, allowing the attacker to search for known CVEs for those precise versions.
2. **Source code structure disclosure**: Package names (e.g., `com.smartcampus.resource.SensorResource`) reveal the application's internal module structure, making targeted attacks on specific classes easier.
3. **Vulnerability path discovery**: The call stack shows the exact sequence of method calls that triggered the error. An attacker can use this to understand execution flow, identify unexpected code paths, and craft inputs that exploit them.
4. **Dependency chain exposure**: Library class names in the trace (e.g., `com.fasterxml.jackson`) reveal third-party dependencies, each a potential attack surface.

The `GlobalExceptionMapper` in this API intercepts all `Throwable` instances, logs the full trace **server-side only** (where it is accessible to developers via log files), and returns only a generic `"An unexpected error occurred"` message to the client — eliminating all of the above risks.

---

**Q: Why use JAX-RS filters for cross-cutting concerns like logging instead of inserting `Logger.info()` calls into every resource method?**

Manually inserting log statements into every resource method violates the **Don't Repeat Yourself** (DRY) principle and the **Single Responsibility Principle**. A resource method's sole responsibility is to handle a specific HTTP operation — not to instrument itself. The consequences of manual logging are:

1. **Inconsistency**: Developers forget to add log statements in some methods, leading to gaps in observability.
2. **Maintenance burden**: If the log format changes, every method must be updated individually.
3. **Code clutter**: Business logic becomes interleaved with infrastructure concerns, reducing readability.
4. **No coverage of framework-level errors**: If JAX-RS rejects a request before invoking a method (e.g., 415 Unsupported Media Type), manual logs inside methods would miss it entirely.

A `ContainerRequestFilter` / `ContainerResponseFilter` pair is invoked by the JAX-RS runtime for **every** request and response, regardless of which method handles it, and regardless of whether the request even reaches a method. This guarantees complete, consistent, centrally maintained observability with zero per-method code duplication — the definition of a cross-cutting concern handled correctly.

---

## Project Structure

```
smart-campus-api/
├── pom.xml
└── src/main/java/com/smartcampus/
    ├── Main.java                          # Entry point — starts embedded Jetty
    ├── SmartCampusApplication.java        # @ApplicationPath("/api/v1")
    ├── DataStore.java                     # Singleton ConcurrentHashMap store
    ├── model/
    │   ├── Room.java
    │   ├── Sensor.java
    │   ├── SensorReading.java
    │   └── ErrorResponse.java
    ├── resource/
    │   ├── DiscoveryResource.java         # GET /api/v1
    │   ├── RoomResource.java              # GET/POST/DELETE /api/v1/rooms
    │   ├── SensorResource.java            # GET/POST /api/v1/sensors
    │   └── SensorReadingResource.java     # GET/POST /api/v1/sensors/{id}/readings
    ├── exception/
    │   ├── RoomNotEmptyException.java
    │   ├── RoomNotEmptyExceptionMapper.java      # → 409 Conflict
    │   ├── LinkedResourceNotFoundException.java
    │   ├── LinkedResourceNotFoundExceptionMapper.java  # → 422 Unprocessable Entity
    │   ├── SensorUnavailableException.java
    │   ├── SensorUnavailableExceptionMapper.java  # → 403 Forbidden
    │   └── GlobalExceptionMapper.java            # → 500 Internal Server Error
    └── filter/
        └── LoggingFilter.java             # Request + Response logging
```

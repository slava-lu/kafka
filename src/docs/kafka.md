# Kafka and Messaging Architecture Analysis

This document provides a comprehensive analysis of the Kafka and messaging setup in the WVS Certificate Declaration SCS project, built on the jEAP (Java Enterprise Application Platform) framework.

## Table of Contents

1. [Overview](#overview)
2. [jEAP Messaging Libraries](#jeap-messaging-libraries)
3. [Configuration](#configuration)
4. [Publishing Events](#publishing-events)
5. [Consuming Events](#consuming-events)
6. [Message Types (Avro)](#message-types-avro)
7. [Key Architectural Patterns](#key-architectural-patterns)
8. [Testing](#testing)
9. [Best Practices & Recommendations](#best-practices--recommendations)

---

## Overview

The project implements an event-driven architecture using Apache Kafka with the following characteristics:

- **Framework**: jEAP Messaging Infrastructure (Swiss Federal IT framework)
- **Serialization**: Apache Avro with Schema Registry
- **Pattern**: Transactional Outbox for publishing, Idempotent handlers for consuming
- **Security**: Message signing and authentication (production only)

### Topics Used

| Direction | Topic Name | Purpose |
|-----------|------------|---------|
| **Publish** | `wvs-certificate-declaration-accepted` | Certificate acceptance event |
| **Publish** | `wvs-certificate-declaration-released` | Certificate release event |
| **Consume** | `docbox-document-customerdocument-stored` | Document storage confirmation |
| **Consume** | `wvs-nationaldeclaration-goodsdeclaration-accepted-v6` | Goods declaration accepted |
| **Consume** | `wvs-nationaldeclaration-goodsdeclaration-invalidated` | Goods declaration invalidated |
| **Consume** | `wvs-nationaldeclaration-goodsdeclaration-released` | Goods declaration released |

---

## jEAP Messaging Libraries

### Dependencies (pom.xml)

```xml
<!-- Core Kafka Infrastructure -->
<dependency>
    <groupId>ch.admin.bit.jeap</groupId>
    <artifactId>jeap-messaging-infrastructure-kafka</artifactId>
</dependency>

<!-- Transactional Outbox Pattern -->
<dependency>
    <groupId>ch.admin.bit.jeap</groupId>
    <artifactId>jeap-messaging-outbox</artifactId>
</dependency>

<!-- Idempotent Message Processing -->
<dependency>
    <groupId>ch.admin.bit.jeap</groupId>
    <artifactId>jeap-messaging-idempotence</artifactId>
</dependency>

<!-- Testing Support -->
<dependency>
    <groupId>ch.admin.bit.jeap</groupId>
    <artifactId>jeap-messaging-infrastructure-kafka-test</artifactId>
    <scope>test</scope>
</dependency>

<!-- Code Generation (Annotation Processor) -->
<path>
    <groupId>ch.admin.bit.jeap</groupId>
    <artifactId>jeap-messaging-contract-annotations</artifactId>
    <version>${jeap-messaging.version}</version>
</path>
```

### Library Responsibilities

| Library | Purpose |
|---------|---------|
| `jeap-messaging-infrastructure-kafka` | Core Kafka configuration, producer/consumer factories, Schema Registry integration, message signing |
| `jeap-messaging-outbox` | `TransactionalOutbox` class for reliable event publishing with database-backed outbox table |
| `jeap-messaging-idempotence` | `@IdempotentMessageHandler` annotation for at-most-once processing semantics |
| `jeap-messaging-infrastructure-kafka-test` | Test utilities, mock Schema Registry, embedded Kafka support |
| `jeap-messaging-contract-annotations` | Annotation processor for generating Avro event builders |

---

## Configuration

### Main Configuration (`application.yml`)

```yaml
kafka:
  publisher:
    wvs-certificate-declaration-accepted: wvs-certificate-declaration-accepted
    wvs-certificate-declaration-released: wvs-certificate-declaration-released
  consumer:
    customer-document-stored: docbox-document-customerdocument-stored
    wvs-national-goods-declaration-accepted: wvs-nationaldeclaration-goodsdeclaration-accepted-v6
    wvs-national-goods-declaration-invalidated: wvs-nationaldeclaration-goodsdeclaration-invalidated
    wvs-national-goods-declaration-released: wvs-nationaldeclaration-goodsdeclaration-released

jeap:
  messaging:
    authentication:
      subscriber:
        require-signature: true  # Message signature validation
        allowed-publishers:
          DocboxCustomerdocumentStoredEvent:
            - docbox-storage-service
            - wvs-mockeventinjector
    kafka:
      errorTopicName: "wvs-eventprocessing-failed"  # Dead letter topic
      systemName: WVS
```

### Local Development (`application-local.yml`)

```yaml
jeap:
  messaging:
    authentication:
      subscriber:
        require-signature: false  # Disabled for local dev
    kafka:
      useSchemaRegistry: true
      schemaRegistryUrl: http://localhost:8081
      bootstrap-servers: http://localhost:9092
      security-protocol: PLAINTEXT
      systemName: WVS
      errorTopicName: wvs-eventprocessing-failed
```

### Test Configuration (`application-test.yml`)

```yaml
jeap:
  messaging:
    kafka:
      embedded: false
      systemName: test
      errorTopicName: errorTopic
      message-type-encryption-disabled: true
      cluster:
        default:
          bootstrapServers: "localhost:13666"  # Fixed port for tests
          securityProtocol: PLAINTEXT
          schemaRegistryUrl: "mock://my-registry"
```

### Topic Configuration Class

**File:** `infrastructure/publisher/KafkaTopicPublisherConfig.java`

```java
@Configuration
@ConfigurationProperties(prefix = "kafka.publisher")
@Getter
@Setter
public class KafkaTopicPublisherConfig {
    private String wvsCertificateDeclarationAccepted;
    private String wvsCertificateDeclarationReleased;
}
```

**Good Practice**: Externalizing topic names to configuration allows environment-specific overrides.

---

## Publishing Events

### Architecture: Domain Events → Spring Events → Kafka

The publishing flow follows Domain-Driven Design principles:

```
Domain Aggregate → Domain Event → Spring Event Listener → Avro Builder → Transactional Outbox → Kafka
```

### 1. Domain Event Definition

**File:** `domain/CertificateDeclaration.java`

```java
@Entity
public class CertificateDeclaration extends AbstractAggregateRoot<CertificateDeclaration>
        implements AggregateRoot<CertificateDeclaration, CertificateDeclarationId> {

    // Domain events as records
    public record CertificateAcceptedEvent(String referenceNumber) implements DomainEvent {}
    public record CertificateReleasedEvent(String referenceNumber) implements DomainEvent {}

    public void updateCertificateState(Certificate newCertificate, State newState) {
        switch (newState) {
            case ACCEPTED -> {
                changeState(CertificateDeclarationTransition.TO_ACCEPTED);
                this.version = this.version + 1;
                // Register domain event
                registerEvent(new CertificateAcceptedEvent(this.eur1ReferenceNumber));
            }
            // ...
        }
    }

    public void release(ZonedDateTime releasedAt) {
        changeState(CertificateDeclarationTransition.TO_RELEASED);
        this.releasedAt = releasedAt;
        registerEvent(new CertificateReleasedEvent(this.eur1ReferenceNumber));
    }
}
```

**Good Practice**: Using `AbstractAggregateRoot.registerEvent()` from Spring Data for domain event collection.

### 2. Spring Event Listener (Domain → Avro Conversion)

**File:** `service/CertificateEventListener.java`

```java
@Component
@RequiredArgsConstructor
class CertificateEventListener {

    private final EventPublisher eventPublisher;

    @EventListener
    public void on(CertificateDeclaration.CertificateAcceptedEvent event) {
        WvsCertificateDeclarationAcceptedEvent avroEvent =
            WvsCertificateDeclarationAcceptedEventBuilder.newBuilder()
                .setReferenceNumber(event.referenceNumber())
                .build();

        eventPublisher.publishCertificateDeclarationAcceptedEvent(
            avroEvent, event.referenceNumber());
    }

    @EventListener
    public void on(CertificateDeclaration.CertificateReleasedEvent event) {
        WvsCertificateDeclarationReleasedEvent avroEvent =
            WvsCertificateDeclarationReleasedEventBuilder.newBuilder()
                .setReferenceNumber(event.referenceNumber())
                .build();

        eventPublisher.publishCertificateDeclarationReleasedEvent(
            avroEvent, event.referenceNumber());
    }
}
```

**Good Practice**: Separation between domain events and integration events (Avro messages).

### 3. Event Publisher Interface

**File:** `api/provider/EventPublisher.java`

```java
public interface EventPublisher {
    void publishCertificateDeclarationAcceptedEvent(
            WvsCertificateDeclarationAcceptedEvent event, String referenceNumber);
    void publishCertificateDeclarationReleasedEvent(
            WvsCertificateDeclarationReleasedEvent event, String referenceNumber);
}
```

### 4. Transactional Outbox Implementation

**File:** `infrastructure/publisher/DomainEventPublisher.java`

```java
@Component
@Slf4j
@RequiredArgsConstructor
public class DomainEventPublisher implements EventPublisher {

    private final KafkaTopicPublisherConfig kafkaTopicPublisherConfig;
    private final TransactionalOutbox outbox;  // jEAP Transactional Outbox

    @Override
    public void publishCertificateDeclarationAcceptedEvent(
            WvsCertificateDeclarationAcceptedEvent event,
            String referenceNumber) {
        sendEvent(kafkaTopicPublisherConfig.getWvsCertificateDeclarationAccepted(),
                  referenceNumber, event);
    }

    @Override
    public void publishCertificateDeclarationReleasedEvent(
            WvsCertificateDeclarationReleasedEvent event,
            String referenceNumber) {
        sendEvent(kafkaTopicPublisherConfig.getWvsCertificateDeclarationReleased(),
                  referenceNumber, event);
    }

    private void sendEvent(final String topicName, String referenceNumber,
                          final AvroMessage event) {
        // Create message key for partitioning
        CustomsReferenceNumberMessageKey key = CustomsReferenceNumberMessageKey.newBuilder()
                .setCustomsReferenceNumber(referenceNumber)
                .build();

        log.info("Publishing event to {} with {}", LogArgs.topic(topicName),
                 LogArgs.crn(key));
        log.debug("Publishing event {}", LogArgs.event(event));

        // jEAP Transactional Outbox - atomically saves to outbox table
        outbox.sendMessage(event, key, topicName);
    }
}
```

**Key Points:**
- `TransactionalOutbox.sendMessage()` stores the message in a database outbox table
- A background relay/poller publishes messages from the outbox to Kafka
- Ensures exactly-once semantics even if the application crashes

### 5. Avro Event Builder

**File:** `api/event/WVSEventBuilder.java` (Base class)

```java
public abstract class WVSEventBuilder<B extends AvroDomainEventBuilder<B, E>,
                                      E extends AvroDomainEvent>
        extends AvroDomainEventBuilder<B, E> {

    private static final String SYSTEM_NAME = "WVS";
    private static final String SERVICE_NAME = "wvs-certificat-declaration-scs";
    private boolean isIdempotenceIdOverwritten;

    protected WVSEventBuilder(Supplier<E> constructor) {
        super(constructor);
    }

    @Override
    protected String getServiceName() {
        return SERVICE_NAME;
    }

    @Override
    protected String getSystemName() {
        return SYSTEM_NAME;
    }

    @Override
    public B idempotenceId(String idempotenceId) {
        isIdempotenceIdOverwritten = true;
        return super.idempotenceId(idempotenceId);
    }

    public B processId(String processId) {
        super.setProcessId(processId);
        return self();
    }

    @Override
    public E build() {
        // Auto-generate idempotence ID if not set
        if(!isIdempotenceIdOverwritten) {
            super.idempotenceId(UUID.randomUUID().toString());
        }
        return super.build();
    }
}
```

**File:** `api/event/WvsCertificateDeclarationAcceptedEventBuilder.java`

```java
public class WvsCertificateDeclarationAcceptedEventBuilder extends
        WVSEventBuilder<WvsCertificateDeclarationAcceptedEventBuilder,
                        WvsCertificateDeclarationAcceptedEvent> {

    private final CertificateDeclarationReference.Builder certificateDeclarationReferenceBuilder =
            CertificateDeclarationReference.newBuilder();

    public static WvsCertificateDeclarationAcceptedEventBuilder newBuilder() {
        return new WvsCertificateDeclarationAcceptedEventBuilder();
    }

    public WvsCertificateDeclarationAcceptedEventBuilder setReferenceNumber(String referenceNumber) {
        certificateDeclarationReferenceBuilder.setReferenceNumber(referenceNumber);
        return self();
    }

    @Override
    public WvsCertificateDeclarationAcceptedEvent build() {
        WvsCertificateDeclarationAcceptedEventReferences references =
            WvsCertificateDeclarationAcceptedEventReferences.newBuilder()
                .setCertificateDeclarationReference(certificateDeclarationReferenceBuilder.build())
                .build();
        super.setReferences(references);
        return super.build();
    }
}
```

**Good Practice**: Builder pattern with fluent API for constructing Avro messages with required metadata.

---

## Consuming Events

### Architecture: Consumer Annotation + Processor Delegation

```
Kafka Topic → @KafkaListener → Consumer Class → Processor (with @IdempotentMessageHandler) → Domain Logic
```

### 1. Custom Consumer Annotation (Deep Dive)

**File:** `infrastructure/consumer/WvsKafkaEventConsumerComponent.java`

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
@Conditional(WvsKafkaEventConsumerCondition.class)
public @interface WvsKafkaEventConsumerComponent {
    // Custom annotation for conditionally enabling/disabling consumers
}
```

**File:** `infrastructure/consumer/WvsKafkaEventConsumerCondition.java`

```java
@Slf4j
public class WvsKafkaEventConsumerCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment env = context.getEnvironment();

        // Global toggle to enable/disable all consumers
        boolean globallyEnabled = Boolean.parseBoolean(
            env.getProperty("kafka.listeners.enableAll", "true"));

        if (globallyEnabled) {
            return true;
        }

        // Fine-grained control via include list
        if (metadata instanceof ClassMetadata) {
            try {
                String className = ((ClassMetadata) metadata).getClassName();
                String simpleName = Class.forName(className).getSimpleName();

                String[] includes = env.getProperty("kafka.listeners.include",
                                                   String[].class, new String[0]);
                boolean isIncluded = Arrays.asList(includes).contains(simpleName);

                if (!isIncluded) {
                    log.info("Kafka listener class {} is not in the include list", simpleName);
                }

                return isIncluded;
            } catch (ClassNotFoundException e) {
                log.error("Class not found for kafka listener: {}", e.getMessage(), e);
                return false;
            }
        }

        return false;
    }
}
```

#### How the Conditional Bean Registration Works

The annotation combines Spring's `@Component` with `@Conditional` for conditional bean registration:

```
Spring Application Startup
    ↓
Spring scans for @Component classes
    ↓
Found: DocboxCustomerDocumentStoredEventConsumer with @WvsKafkaEventConsumerComponent
    ↓
Spring evaluates WvsKafkaEventConsumerCondition.matches()
    ↓
    ├─→ Check: kafka.listeners.enableAll (default: "true")
    │   ├─→ If "true": RETURN true → Bean created
    │   └─→ If "false": Continue to whitelist check
    │
    └─→ Whitelist Check: kafka.listeners.include
        ├─→ Get simple class name: "DocboxCustomerDocumentStoredEventConsumer"
        ├─→ Check if name exists in include array
        │
        ├─→ true: Bean created, @KafkaListener registered
        └─→ false: Bean NOT created, listener never registers
```

**Execution Timing:** Condition evaluated during Spring's `BeanDefinitionRegistry` phase (before bean instantiation).

#### Configuration Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `kafka.listeners.enableAll` | String (boolean) | `"true"` | Global toggle for all consumers |
| `kafka.listeners.include` | String[] | `[]` (empty) | Whitelist of consumer class names |

#### Usage Scenarios

**Scenario 1: Default Behavior (All Consumers Enabled)**
```yaml
# No configuration needed - defaults apply
# kafka.listeners.enableAll = true (implicit default)
# kafka.listeners.include = [] (implicit default)

# Result: All consumers with @WvsKafkaEventConsumerComponent are registered
# - DocboxCustomerDocumentStoredEventConsumer: ENABLED
# - WvsNationalGoodsDeclarationEventConsumer: ENABLED
```

**Scenario 2: Disable All Consumers Globally**
```yaml
kafka:
  listeners:
    enableAll: false
    # include is empty by default, so no consumers are registered

# Result: No consumers created as Spring beans
# Use case: Running without Kafka dependency, unit tests, resource-constrained environments
```

**Scenario 3: Selectively Enable Specific Consumers**
```yaml
kafka:
  listeners:
    enableAll: false
    include:
      - DocboxCustomerDocumentStoredEventConsumer
      - WvsNationalGoodsDeclarationEventConsumer

# Result: Only listed consumers are registered
# - DocboxCustomerDocumentStoredEventConsumer: ENABLED (in include list)
# - WvsNationalGoodsDeclarationEventConsumer: ENABLED (in include list)
```

**Scenario 4: Enable All Except Specific Consumers**
```yaml
kafka:
  listeners:
    enableAll: false
    include:
      - DocboxCustomerDocumentStoredEventConsumer
      # WvsNationalGoodsDeclarationEventConsumer is NOT in the list

# Result:
# - DocboxCustomerDocumentStoredEventConsumer: ENABLED
# - WvsNationalGoodsDeclarationEventConsumer: DISABLED (not in list)
```

**Scenario 5: Override via Environment Variables**
```bash
# Disable all consumers via environment variable
export KAFKA_LISTENERS_ENABLEALL=false

# Or enable specific consumers
export KAFKA_LISTENERS_ENABLEALL=false
export KAFKA_LISTENERS_INCLUDE=DocboxCustomerDocumentStoredEventConsumer
```

#### Two-Level Control: Bean Registration vs Listener Startup

The `@WvsKafkaEventConsumerComponent` annotation controls **bean registration**, but note that `@KafkaListener` also has `autoStartup = "false"`:

```java
@WvsKafkaEventConsumerComponent  // Level 1: Bean registration control
public class DocboxCustomerDocumentStoredEventConsumer {

    @KafkaListener(
        topics = "...",
        autoStartup = "false")  // Level 2: Listener thread startup control
    public void receive(...) { }
}
```

**Both levels work together:**

| Condition Result | autoStartup | Bean Created? | Listener Consuming? |
|------------------|-------------|---------------|---------------------|
| `true` | `"false"` | Yes | No (jEAP controls startup) |
| `true` | `"true"` | Yes | Yes (immediately) |
| `false` | N/A | No | No (bean doesn't exist) |

**Why `autoStartup = "false"`?**
- jEAP framework manages listener lifecycle
- Allows coordinated startup after application is fully initialized
- Enables graceful shutdown handling

#### Practical Use Cases

1. **Unit Testing Processors:**
   ```yaml
   # application-test.yml
   kafka:
     listeners:
       enableAll: false  # Don't register Kafka consumers in unit tests
   ```
   This allows testing processors directly without Kafka infrastructure.

2. **Debugging Specific Consumer:**
   ```yaml
   # Enable only the consumer you're debugging
   kafka:
     listeners:
       enableAll: false
       include:
         - WvsNationalGoodsDeclarationEventConsumer
   ```

3. **Resource-Constrained Environments:**
   ```yaml
   # Disable consumers to reduce memory/CPU usage
   kafka:
     listeners:
       enableAll: false
   ```

4. **Phased Rollout:**
   ```yaml
   # Enable consumers one by one during deployment
   kafka:
     listeners:
       enableAll: false
       include:
         - DocboxCustomerDocumentStoredEventConsumer  # Phase 1
         # Add more consumers in later phases
   ```

#### Key Benefits

1. **No Code Changes:** Enable/disable via configuration only
2. **Fine-Grained Control:** Per-consumer enable/disable
3. **Environment Flexibility:** Different consumers in dev/test/prod
4. **Spring-Native:** Uses standard `@Conditional` mechanism
5. **Startup Performance:** Disabled consumers don't create Kafka connections

### 2. Consumer Implementation

**File:** `infrastructure/consumer/DocboxCustomerDocumentStoredEventConsumer.java`

```java
@WvsKafkaEventConsumerComponent
@Slf4j
@RequiredArgsConstructor
public class DocboxCustomerDocumentStoredEventConsumer {

    private final DocboxCustomerDocumentStoredEventProcessor processor;

    @KafkaListener(
            topics = "${kafka.consumer.customer-document-stored}",
            id = "DocboxCustomerDocumentStoredEventListener",
            idIsGroup = false,
            autoStartup = "false")  // Manual startup control
    public void receive(DocboxCustomerdocumentStoredEvent event, Acknowledgment ack)
            throws CertificateException {
        processor.process(event);
        ack.acknowledge();  // Manual acknowledgment after successful processing
    }
}
```

**File:** `infrastructure/consumer/WvsNationalGoodsDeclarationEventConsumer.java`

```java
@WvsKafkaEventConsumerComponent
@Slf4j
@RequiredArgsConstructor
public class WvsNationalGoodsDeclarationEventConsumer {

    private final WvsNationalGoodsDeclarationEventProcessor processor;

    @KafkaListener(
            topics = "${kafka.consumer.wvs-national-goods-declaration-accepted}",
            id = "WvsNationalGoodsDeclarationAcceptedEvent",
            idIsGroup = false,
            autoStartup = "false")
    public void receive(WvsNationalGoodsDeclarationAcceptedEvent event, Acknowledgment ack)
            throws CertificateException {
        processor.processNationalGoodsDeclarationAcceptedEvent(event);
        ack.acknowledge();
    }

    @KafkaListener(
            topics = "${kafka.consumer.wvs-national-goods-declaration-invalidated}",
            id = "WvsNationalGoodsDeclarationInvalidatedEvent",
            idIsGroup = false,
            autoStartup = "false")
    public void receive(WvsNationalGoodsDeclarationInvalidatedEvent event, Acknowledgment ack) {
        processor.processNationalGoodsDeclarationInvalidatedEvent(event);
        ack.acknowledge();
    }

    @KafkaListener(
            topics = "${kafka.consumer.wvs-national-goods-declaration-released}",
            id = "WvsNationalGoodsDeclarationReleasedEvent",
            idIsGroup = false,
            autoStartup = "false")
    public void receive(WvsNationalGoodsDeclarationReleasedEvent event, Acknowledgment ack) {
        processor.processNationalGoodsDeclarationReleasedEvent(event);
        ack.acknowledge();
    }
}
```

**Key Configuration:**
- `autoStartup = "false"` - Consumers start manually (controlled by jEAP)
- `idIsGroup = false` - Uses `id` as listener ID, not consumer group ID
- Manual `Acknowledgment` - Offset committed only after successful processing

### 3. Event Processor with Idempotence

**File:** `service/DocboxCustomerDocumentStoredEventProcessor.java`

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class DocboxCustomerDocumentStoredEventProcessor {

    public static final String REMOVE_VERSION_REGEX = "\\..*$";

    private final CertificateDeclarationDetailService certificateDeclarationDetailService;

    @Transactional
    @IdempotentMessageHandler  // jEAP idempotence - prevents duplicate processing
    public void process(@NonNull final DocboxCustomerdocumentStoredEvent event)
            throws CertificateException {
        String docTypeId = event.getReferences().getBaseOriginReference().getDocTypeId();

        if (DocTypeIdEnum.isDocumentCreated(docTypeId)) {
            String eur1ReferenceNumber = event.getReferences()
                .getBaseOriginReference()
                .getSourceId()
                .replaceAll(REMOVE_VERSION_REGEX, "");
            certificateDeclarationDetailService.setDocumentCreatedByRefNumber(eur1ReferenceNumber);
        }
    }
}
```

**File:** `service/WvsNationalGoodsDeclarationEventProcessor.java`

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class WvsNationalGoodsDeclarationEventProcessor {

    private final CertificateDeclarationRepository certificateDeclarationRepository;
    private final NationalDeclarationClient nationalDeclarationClient;

    @Transactional
    @IdempotentMessageHandler
    public void processNationalGoodsDeclarationAcceptedEvent(
            WvsNationalGoodsDeclarationAcceptedEvent event) {
        final String gdrn = event.getReferences()
            .getNationalGoodsDeclarationReference()
            .getReferenceNumber();
        final int version = event.getReferences()
            .getNationalGoodsDeclarationReference()
            .getDeclarationVersion();

        MDC.put(LogKey.GDRN, gdrn);

        // Early exit for non-export declarations
        if (!GoodsDeclarationReferenceNumberUtil.isExport(gdrn)) {
            return;
        }

        // Business logic...
        final GoodsDeclarationReference goodsDeclarationReference =
            new GoodsDeclarationReference(gdrn, version);

        List<CertificateDeclaration> certificateDeclarations =
            certificateDeclarationRepository.findAllByGdrn(gdrn);
        certificateDeclarations.forEach(CertificateDeclaration::unlinkGoodsDeclaration);

        NationalGoodsDeclaration nationalGoodsDeclaration =
            nationalDeclarationClient.findNationalGoodsDeclaration(goodsDeclarationReference)
                .orElseThrow(() -> CertificateException
                    .nationalGoodsDeclarationNotFoundByGdrn(gdrn));

        List<String> eur1ReferenceNumbers = nationalGoodsDeclaration
            .getSupportingDocuments()
            .stream()
            .filter(SupportingDocument::isEur1)
            .map(SupportingDocument::referenceNumber)
            .filter(Eur1ReferenceNumberUtil::isValid)
            .toList();

        eur1ReferenceNumbers.forEach(eur1ReferenceNumber -> {
           CertificateDeclaration certificateDeclaration =
               certificateDeclarationRepository.findByEur1ReferenceNumber(eur1ReferenceNumber)
                   .orElseThrow(() -> CertificateException
                       .certificateNotFoundByReferenceNumber(eur1ReferenceNumber));
           certificateDeclaration.linkGoodsDeclaration(gdrn);
        });
    }

    @Transactional
    @IdempotentMessageHandler
    public void processNationalGoodsDeclarationInvalidatedEvent(
            WvsNationalGoodsDeclarationInvalidatedEvent event) {
        // Similar pattern...
    }

    @Transactional
    @IdempotentMessageHandler
    public void processNationalGoodsDeclarationReleasedEvent(
            WvsNationalGoodsDeclarationReleasedEvent event) {
        // Similar pattern...
    }
}
```

**Key Points:**
- `@IdempotentMessageHandler` uses the event's idempotence ID to track processed messages
- `@Transactional` ensures atomicity - all-or-nothing processing
- MDC logging for correlation (GDRN reference number)
- Early exit pattern for filtering irrelevant events

---

## Message Types (Avro)

### Published Events (Dependencies)

```xml
<dependency>
    <groupId>ch.admin.bazg.messagetype.wvs</groupId>
    <artifactId>wvs-certificate-declaration-accepted-event</artifactId>
    <version>${wvs-certificate-declaration-accepted-event.version}</version>
</dependency>

<dependency>
    <groupId>ch.admin.bazg.messagetype.wvs</groupId>
    <artifactId>wvs-certificate-declaration-released-event</artifactId>
    <version>${wvs-certificate-declaration-released-event.version}</version>
</dependency>
```

### Consumed Events (Dependencies)

```xml
<dependency>
    <groupId>ch.admin.bazg.messagetype.wvs</groupId>
    <artifactId>wvs-national-goods-declaration-accepted-event</artifactId>
    <version>${wvs-national-goods-declaration-accepted-event.version}</version>
</dependency>

<dependency>
    <groupId>ch.admin.bazg.messagetype.wvs</groupId>
    <artifactId>wvs-national-goods-declaration-invalidated-event</artifactId>
    <version>${wvs-national-goods-declaration-invalidated-event.version}</version>
</dependency>

<dependency>
    <groupId>ch.admin.bazg.messagetype.wvs</groupId>
    <artifactId>wvs-national-goods-declaration-released-event</artifactId>
    <version>${wvs-national-goods-declaration-released-event.version}</version>
</dependency>

<dependency>
    <groupId>ch.admin.bazg.messagetype.docbox</groupId>
    <artifactId>docbox-customerdocument-stored-event</artifactId>
    <version>${docbox-customerdocument-stored-event.version}</version>
</dependency>
```

### Message Key

All messages use `CustomsReferenceNumberMessageKey` for partitioning:

```java
CustomsReferenceNumberMessageKey key = CustomsReferenceNumberMessageKey.newBuilder()
        .setCustomsReferenceNumber(referenceNumber)
        .build();
```

**Benefit**: Ensures all events for the same certificate go to the same partition → ordering guarantee.

---

## Key Architectural Patterns

### 1. Transactional Outbox Pattern

**What it does:**
- Event is written to an outbox table in the same database transaction as the business data
- A background relay/poller reads from the outbox and publishes to Kafka
- Ensures exactly-once semantics and consistency between DB and Kafka

**Implementation:**
```java
outbox.sendMessage(event, key, topicName);  // Saves to outbox table
```

**Benefits:**
- No dual-write problem (DB + Kafka)
- Events are never lost even if Kafka is temporarily unavailable
- Atomic commit with business data

#### Database Table Structure (Outbox)

**File:** `src/main/resources/sql/db/migration/common/V1_0_1__create-outbox-schema.sql`

```sql
-- Outbox table for reliable publishing (OUTGOING - messages to send)
CREATE TABLE deferred_message (
    id                     bigint PRIMARY KEY,
    message                bytea                    NOT NULL,  -- Serialized Avro message
    key                    bytea,                              -- Message key (partitioning)
    topic                  varchar                  NOT NULL,  -- Target Kafka topic
    message_id             varchar                  NOT NULL,  -- Unique message ID
    message_idempotence_id varchar                  NOT NULL,  -- For deduplication
    message_type_name      varchar                  NOT NULL,  -- Event type name
    message_type_version   varchar,                            -- Schema version
    cluster_name           varchar,                            -- Kafka cluster
    created                timestamp with time zone NOT NULL,
    send_immediately       boolean,                            -- Priority flag
    schedule_after         timestamp with time zone,           -- Delayed sending
    sent_immediately       timestamp with time zone,           -- When sent (immediate)
    sent_scheduled         timestamp with time zone            -- When sent (scheduled)
);

-- Distributed lock to prevent multiple relays running simultaneously
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
```

#### Outbox Flow

```
1. Business transaction starts
2. Domain entity saved to database
3. outbox.sendMessage() → INSERT into deferred_message (same transaction)
4. Transaction commits (both entity + outbox message atomically)
5. Background relay (with shedlock) polls deferred_message
6. Relay sends message to Kafka
7. On success: DELETE from deferred_message
8. On failure: Retry later (message stays in outbox)
```

**Important:** The `deferred_message` table is for **outgoing** messages only. It is separate from `idempotent_processing` which tracks **incoming** messages.

### 2. Idempotent Message Handler (Deep Dive)

**What it does:**
- Uses the event's `idempotenceId` field to track processed messages
- If the same idempotence ID is seen again, processing is skipped
- Ensures at-most-once semantics

**Implementation:**
```java
@IdempotentMessageHandler
public void process(SomeEvent event) {
    // This method will only execute once per unique idempotenceId
}
```

**Benefits:**
- Safe reprocessing on consumer restart
- Handles Kafka rebalancing gracefully
- No duplicate side effects

#### Database Storage for Idempotence Tracking

The jEAP framework creates a dedicated database table via Flyway migration to track processed messages:

**File:** `src/main/resources/sql/db/migration/common/V1_0_1__create-outbox-schema.sql`

```sql
-- Table for tracking processed message IDs (CONSUMING - incoming messages)
CREATE TABLE idempotent_processing (
    idempotence_id         varchar                  NOT NULL,
    idempotence_id_context varchar                  NOT NULL,
    created_at             timestamp with time zone NOT NULL,
    CONSTRAINT pk_idempotent_processing PRIMARY KEY (idempotence_id, idempotence_id_context)
);
```

**Important:** This table is **separate** from the `deferred_message` outbox table. They serve different purposes:

| Table | Purpose | Direction | jEAP Library |
|-------|---------|-----------|--------------|
| `idempotent_processing` | Track processed messages | Incoming (Consumer) | `jeap-messaging-idempotence` |
| `deferred_message` | Store messages before sending | Outgoing (Producer) | `jeap-messaging-outbox` |

They are created in the same migration file but are **completely independent mechanisms**.

**Table Structure:**

| Column | Type | Description |
|--------|------|-------------|
| `idempotence_id` | varchar | Unique ID from the event (UUID) |
| `idempotence_id_context` | varchar | Handler method name or processor class identifier |
| `created_at` | timestamp | When the message was first processed |

**Primary Key:** Composite `(idempotence_id, idempotence_id_context)` - allows same message ID to be processed by different handlers.

#### How the Annotation Works Internally

The `@IdempotentMessageHandler` annotation is processed via Spring AOP (Aspect-Oriented Programming):

```
1. Method annotated with @IdempotentMessageHandler is intercepted
2. AOP aspect extracts idempotenceId from the event parameter
3. Query: SELECT FROM idempotent_processing WHERE idempotence_id = ? AND idempotence_id_context = ?
4. Decision:
   ├── Record NOT found → Execute method → INSERT into idempotent_processing → Return result
   └── Record found → Skip execution → Return without calling method (duplicate detected)
5. All within same transaction (combined with @Transactional)
```

**ID Extraction:** All jEAP domain events inherit from `AvroDomainEvent` which has an `idempotenceId` field in the event's identity metadata.

#### Idempotence ID Generation

The idempotence ID is set during event building:

**File:** `api/event/WVSEventBuilder.java`

```java
public abstract class WVSEventBuilder<B extends AvroDomainEventBuilder<B, E>,
                                      E extends AvroDomainEvent>
        extends AvroDomainEventBuilder<B, E> {

    private boolean isIdempotenceIdOverwritten;

    @Override
    public B idempotenceId(String idempotenceId) {
        isIdempotenceIdOverwritten = true;
        return super.idempotenceId(idempotenceId);
    }

    @Override
    public E build() {
        // Auto-generate UUID if not explicitly set
        if(!isIdempotenceIdOverwritten) {
            super.idempotenceId(UUID.randomUUID().toString());
        }
        return super.build();
    }
}
```

**Two Options:**
1. **Automatic (Default):** Random UUID generated on `build()` - suitable for most cases
2. **Deterministic:** Explicitly set via `idempotenceId(String)` - useful when you want to control deduplication (e.g., use certificate's `creationIdempotenceId`)

#### Complete Processing Flow

```
Kafka Message Arrives
    ↓
@KafkaListener receives event with idempotenceId in metadata
    ↓
Consumer calls processor.process(event)
    ↓
AOP Aspect intercepts @IdempotentMessageHandler method
    ↓
Extract idempotenceId from event.getIdentity().getIdempotenceId()
    ↓
Query idempotent_processing table
    │
    ├── NOT FOUND (first time)
    │   ↓
    │   Execute business logic
    │   ↓
    │   INSERT (idempotenceId, "processMethod", now()) into idempotent_processing
    │   ↓
    │   Commit transaction
    │   ↓
    │   Return to consumer → ack.acknowledge()
    │
    └── FOUND (duplicate)
        ↓
        Skip execution (log warning)
        ↓
        Return to consumer → ack.acknowledge() (idempotent)
```

#### Key Characteristics

| Aspect | Details |
|--------|---------|
| **Storage** | PostgreSQL `idempotent_processing` table |
| **Primary Key** | Composite: `(idempotence_id, idempotence_id_context)` |
| **ID Source** | `AvroDomainEvent.identity.idempotenceId` |
| **Generation** | Auto UUID or explicit via builder |
| **Atomicity** | Combined with `@Transactional` |
| **Scope** | Per-handler (same message can be processed by different handlers) |
| **Persistence** | Survives application restarts |
| **Configuration** | Automatic via jEAP; no explicit YAML config needed |

### 3. Domain Event Sourcing

**What it does:**
- Domain aggregates emit domain events via `AbstractAggregateRoot.registerEvent()`
- Spring Data publishes events after repository save
- Event listeners convert to integration events (Avro)

**Flow:**
```
1. CertificateDeclaration.release()
2. registerEvent(new CertificateReleasedEvent(...))
3. repository.save(certificate)  // Spring publishes domain events
4. CertificateEventListener.on(CertificateReleasedEvent)
5. DomainEventPublisher.publishCertificateDeclarationReleasedEvent()
6. TransactionalOutbox.sendMessage()
```

### 4. Consumer-Processor Separation

**What it does:**
- `@KafkaListener` consumer handles Kafka protocol (deserialization, acknowledgment)
- Processor handles business logic with `@IdempotentMessageHandler` and `@Transactional`

**Benefits:**
- Clean separation of concerns
- Easier to unit test processors
- Processors can be reused for replay/batch processing

### 5. Message Signing & Authentication

**Configuration (Production):**
```yaml
jeap:
  messaging:
    authentication:
      subscriber:
        require-signature: true
        allowed-publishers:
          DocboxCustomerdocumentStoredEvent:
            - docbox-storage-service
            - wvs-mockeventinjector
```

**What it does:**
- Publishers sign messages with a private key
- Consumers validate signatures against allowed publishers
- Prevents message tampering and unauthorized publishing

---

## Testing

### Consumer Unit Test

**File:** `infrastructure/consumer/WvsNationalGoodsDeclarationEventConsumerTest.java`

```java
@ExtendWith(MockitoExtension.class)
class WvsNationalGoodsDeclarationEventConsumerTest {

    @Mock
    private WvsNationalGoodsDeclarationEventProcessor processor;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private WvsNationalGoodsDeclarationEventConsumer consumer;

    @Test
    void receive_shouldCallProcessorAndAcknowledge_whenAcceptedEvent() {
        WvsNationalGoodsDeclarationAcceptedEvent event = mock();

        consumer.receive(event, acknowledgment);

        verify(processor).processNationalGoodsDeclarationAcceptedEvent(event);
        verify(acknowledgment).acknowledge();
    }
}
```

### Publisher Unit Test

**File:** `infrastructure/publisher/DomainEventPublisherTest.java`

```java
@ExtendWith(MockitoExtension.class)
class DomainEventPublisherTest {

    @Mock
    private KafkaTopicPublisherConfig kafkaTopicPublisherConfig;

    @Mock
    private TransactionalOutbox outbox;

    @InjectMocks
    private DomainEventPublisher domainEventPublisher;

    @Test
    void publishCertificateDeclarationAcceptedEvent_shouldSendToOutbox() {
        String topic = "test-topic";
        String refNumber = "EUR1-123";
        when(kafkaTopicPublisherConfig.getWvsCertificateDeclarationAccepted()).thenReturn(topic);

        WvsCertificateDeclarationAcceptedEvent event =
            WvsCertificateDeclarationAcceptedEventBuilder.newBuilder()
                .setReferenceNumber(refNumber)
                .build();

        domainEventPublisher.publishCertificateDeclarationAcceptedEvent(event, refNumber);

        verify(outbox).sendMessage(eq(event), any(CustomsReferenceNumberMessageKey.class), eq(topic));
    }
}
```

### Integration Test Configuration

```yaml
# application-test.yml
jeap:
  messaging:
    kafka:
      embedded: false
      cluster:
        default:
          bootstrapServers: "localhost:13666"  # Fixed port
          schemaRegistryUrl: "mock://my-registry"
```

---

## Best Practices & Recommendations

### What's Done Well ✅

1. **Transactional Outbox Pattern** - Ensures reliable event publishing with exactly-once semantics
2. **Idempotent Message Handlers** - Prevents duplicate processing on redelivery
3. **Domain Event Sourcing** - Clean separation between domain and integration events
4. **Consumer-Processor Separation** - Better testability and separation of concerns
5. **Custom Consumer Annotation** - Flexible enable/disable of consumers
6. **Message Key Strategy** - Reference number as key ensures ordering per certificate
7. **Manual Acknowledgment** - Explicit control over offset commits
8. **Configuration Externalization** - Topics defined in YAML, not hardcoded
9. **Structured Logging** - MDC with correlation IDs (GDRN)
10. **Message Signing** - Security for production environments

### Potential Improvements 🔄

1. **Error Handling / Dead Letter Queue (DLQ)**
   - Current: `errorTopicName` configured but not explicitly used in consumers
   - Recommendation: Add explicit error handling with retry + DLQ pattern
   ```java
   @KafkaListener(topics = "...", errorHandler = "kafkaErrorHandler")
   ```

2. **Consumer Group Configuration**
   - Current: `idIsGroup = false` - each listener uses unique ID
   - Consider: Explicit consumer group naming for better monitoring

3. **Health Checks**
   - Current: `@KafkaHealthConfig` annotation present but not detailed
   - Recommendation: Implement liveness/readiness probes for Kafka connectivity

4. **Metrics & Observability**
   - Add Micrometer metrics for:
     - Messages published per topic
     - Processing time per event type
     - Failure rates

5. **Retry Configuration**
   - Current: No explicit retry policy visible
   - Consider: Add `@Retryable` or configure `DefaultErrorHandler` with backoff

6. **Schema Evolution Strategy**
   - Document forward/backward compatibility rules for Avro schemas
   - Consider using `FULL_TRANSITIVE` compatibility mode in Schema Registry

7. **Testing with Embedded Kafka**
   - Current: Uses mock Schema Registry and fixed port
   - Consider: Testcontainers for more realistic integration tests

### Reuse Checklist for New Projects

When setting up Kafka in a new project using this architecture:

1. Add jEAP dependencies:
   - `jeap-messaging-infrastructure-kafka`
   - `jeap-messaging-outbox`
   - `jeap-messaging-idempotence`

2. Create configuration classes:
   - `KafkaTopicPublisherConfig` for topic names
   - `KafkaTopicConsumerConfig` (if needed)

3. Implement the publishing flow:
   - Domain events as records in aggregate
   - Spring event listener for conversion
   - Event builder extending `AvroDomainEventBuilder`
   - Publisher using `TransactionalOutbox`

4. Implement the consuming flow:
   - Custom conditional annotation (optional)
   - Consumer class with `@KafkaListener`
   - Processor with `@IdempotentMessageHandler` and `@Transactional`

5. Configure YAML:
   - Topic names
   - jEAP messaging settings
   - Authentication (production)

6. Add Avro message type dependencies

---

## Configuration Reference

| Setting | Description | Default |
|---------|-------------|---------|
| `kafka.publisher.<name>` | Publishing topic name | - |
| `kafka.consumer.<name>` | Consuming topic name | - |
| `kafka.listeners.enableAll` | Global consumer toggle | `true` |
| `kafka.listeners.include` | Whitelist of consumer classes | `[]` |
| `jeap.messaging.kafka.systemName` | System identifier | - |
| `jeap.messaging.kafka.errorTopicName` | Dead letter topic | - |
| `jeap.messaging.kafka.bootstrap-servers` | Kafka brokers | - |
| `jeap.messaging.kafka.schemaRegistryUrl` | Schema Registry URL | - |
| `jeap.messaging.authentication.subscriber.require-signature` | Signature validation | `false` |
| `jeap.messaging.authentication.subscriber.allowed-publishers` | Publisher whitelist | - |

---

## Docker Infrastructure

**File:** `docker/kafka/docker-compose.yml`

Provides local development environment:
- **Kafka** (KRaft mode) on port 9092
- **Schema Registry** on port 8081
- **Conduktor Console** on port 8090 (Kafka UI)

Start with:
```bash
cd docker/kafka
docker-compose up -d
```

---

*Generated: 2025-12-09*

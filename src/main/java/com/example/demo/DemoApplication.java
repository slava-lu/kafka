package com.example.demo;

import com.example.model.EchoRequest;
import lombok.extern.log4j.Log4j;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.TopicBuilder;


@SpringBootApplication
@Slf4j
public class DemoApplication {

	// Swagger UI http://localhost:8080/swagger-ui/index.html
	// Conduktor  http://localhost:8090

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

	@Bean
	public NewTopic topic() {
		return TopicBuilder.name("topic1")
				.partitions(10)
				.replicas(1)
				.build();
	}

    @KafkaListener(topics = "topicEcho")
	public void listen(EchoRequest message) {
    log.info("Received EchoRequest: " + message);
	}
}

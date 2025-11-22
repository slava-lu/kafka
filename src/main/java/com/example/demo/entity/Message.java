package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "messages", schema = "kafka")
@Getter
@Setter
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "topic", nullable = false)
    private String topic;

    @Column(name = "message", nullable = false)
    private String message;

    @CreationTimestamp
    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;
}

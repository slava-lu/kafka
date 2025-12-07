package com.example.demo.spec;

import com.example.demo.entity.Message;
import org.springframework.data.jpa.domain.Specification;

public class MessageSpecs {
    public static Specification<Message> hasTopic(String topic) {
        return (root, criteriaQuery, criteriaBuilder) ->
          criteriaBuilder.equal(root.get("topic"), topic);
    }

    public static Specification<Message> messageContains(String text) {
        return (root, criteriaQuery, criteriaBuilder) ->
          criteriaBuilder.like(criteriaBuilder.lower(root.get("message")),
            "%" + text.toLowerCase() + "%"
          );
    }
}

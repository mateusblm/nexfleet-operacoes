package br.com.nexfleet.operacoes.publicareventos.infrastructure;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "nexfleet.messaging.provider", havingValue = "rabbit", matchIfMissing = true)
public class RabbitTopologyConfig {
  @Bean
  RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
    return new RabbitAdmin(connectionFactory);
  }

  @Bean
  TopicExchange eventosExchange(@Value("${nexfleet.eventos.exchange}") String nome) {
    return new TopicExchange(nome, true, false);
  }
}

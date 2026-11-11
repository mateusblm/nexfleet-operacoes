package br.com.nexfleet.operacoes.publicareventos.infrastructure;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "nexfleet.messaging.provider", havingValue = "servicebus")
public class ServiceBusConfig {
  @Bean(destroyMethod = "close")
  ServiceBusSenderClient serviceBusSender(
      @Value("${nexfleet.servicebus.fully-qualified-namespace}") String namespace,
      @Value("${nexfleet.servicebus.topic}") String topic) {
    if (namespace == null || namespace.isBlank()) {
      throw new IllegalStateException("SERVICEBUS_FULLY_QUALIFIED_NAMESPACE é obrigatório.");
    }
    return new ServiceBusClientBuilder()
        .credential(namespace, new DefaultAzureCredentialBuilder().build())
        .sender()
        .topicName(topic)
        .buildClient();
  }
}

package br.com.nexfleet.operacoes.publicareventos.infrastructure;

import br.com.nexfleet.operacoes.observability.CorrelationIds;
import br.com.nexfleet.operacoes.publicareventos.application.EventoPendente;
import br.com.nexfleet.operacoes.publicareventos.application.PublicadorEventos;
import com.azure.core.util.BinaryData;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "nexfleet.messaging.provider", havingValue = "servicebus")
public final class ServiceBusPublicadorEventos implements PublicadorEventos {
  private static final Logger LOG = LoggerFactory.getLogger(ServiceBusPublicadorEventos.class);
  private final ServiceBusSenderClient sender;

  public ServiceBusPublicadorEventos(ServiceBusSenderClient sender) {
    this.sender = sender;
  }

  @Override
  public void publicar(EventoPendente evento) {
    try (MDC.MDCCloseable ignored =
        MDC.putCloseable(CorrelationIds.MDC_KEY, evento.correlationId().toString())) {
      String eventType = evento.tipo() + ".v" + evento.versao();
      ServiceBusMessage message = new ServiceBusMessage(BinaryData.fromString(evento.payload()))
          .setMessageId(evento.id().toString())
          .setCorrelationId(evento.correlationId().toString())
          .setContentType("application/json")
          .setSubject(eventType);
      message.getApplicationProperties().put("eventoId", evento.id().toString());
      message.getApplicationProperties().put("eventType", eventType);
      message.getApplicationProperties().put("eventoTipo", evento.tipo());
      message.getApplicationProperties().put("eventoVersao", evento.versao());
      message.getApplicationProperties().put("agregadoTipo", evento.agregadoTipo());
      message.getApplicationProperties().put("agregadoId", evento.agregadoId().toString());
      message.getApplicationProperties().put("ocorridoEm", evento.ocorridoEm().toString());
      message.getApplicationProperties().put(CorrelationIds.HEADER, evento.correlationId().toString());
      sender.sendMessage(message);
      LOG.info("event_published eventId={} eventType={} broker=servicebus", evento.id(), eventType);
    }
  }
}

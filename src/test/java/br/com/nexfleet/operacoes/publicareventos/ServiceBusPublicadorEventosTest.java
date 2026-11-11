package br.com.nexfleet.operacoes.publicareventos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.nexfleet.operacoes.publicareventos.application.EventoPendente;
import br.com.nexfleet.operacoes.publicareventos.infrastructure.ServiceBusPublicadorEventos;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ServiceBusPublicadorEventosTest {
  @Test
  void preservaIdentidadeTipoCorrelacaoEConteudoDoEvento() {
    var sender = mock(ServiceBusSenderClient.class);
    var publicador = new ServiceBusPublicadorEventos(sender);
    UUID eventoId = UUID.randomUUID();
    UUID agregadoId = UUID.randomUUID();
    UUID correlationId = UUID.randomUUID();
    var evento = new EventoPendente(
        eventoId,
        "Abastecimento",
        agregadoId,
        "AbastecimentoRegistrado",
        1,
        correlationId,
        "{\"eventoId\":\"" + eventoId + "\"}",
        Instant.parse("2026-11-08T18:00:00Z"),
        0);

    publicador.publicar(evento);

    var captor = ArgumentCaptor.forClass(ServiceBusMessage.class);
    verify(sender).sendMessage(captor.capture());
    ServiceBusMessage mensagem = captor.getValue();
    assertEquals(eventoId.toString(), mensagem.getMessageId());
    assertEquals(correlationId.toString(), mensagem.getCorrelationId());
    assertEquals("AbastecimentoRegistrado.v1", mensagem.getSubject());
    assertEquals("application/json", mensagem.getContentType());
    assertEquals(evento.payload(), mensagem.getBody().toString());
    assertEquals(
        "AbastecimentoRegistrado.v1",
        mensagem.getApplicationProperties().get("eventType"));
    assertEquals(agregadoId.toString(), mensagem.getApplicationProperties().get("agregadoId"));
  }
}

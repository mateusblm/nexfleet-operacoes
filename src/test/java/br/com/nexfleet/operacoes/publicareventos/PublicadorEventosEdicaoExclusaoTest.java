package br.com.nexfleet.operacoes.publicareventos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import br.com.nexfleet.operacoes.publicareventos.application.EventoPendente;
import br.com.nexfleet.operacoes.publicareventos.infrastructure.RabbitPublicadorEventos;
import br.com.nexfleet.operacoes.publicareventos.infrastructure.ServiceBusPublicadorEventos;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/** Os eventos de edição e exclusão saem pelas rotas e tipos combinados com o Indicadores. */
class PublicadorEventosEdicaoExclusaoTest {
  @ParameterizedTest
  @CsvSource({
    "ABASTECIMENTO, AbastecimentoAtualizado, operacoes.abastecimento.atualizado.v1",
    "ABASTECIMENTO, AbastecimentoExcluido, operacoes.abastecimento.excluido.v1",
    "MANUTENCAO, ManutencaoAtualizada, operacoes.manutencao.atualizada.v1",
    "MANUTENCAO, ManutencaoExcluida, operacoes.manutencao.excluida.v1"
  })
  void rabbitPublicaCadaTipoNovoNaSuaRoutingKey(String agregado, String tipo, String rota) {
    RabbitTemplate rabbit = mock(RabbitTemplate.class);
    doAnswer(invocacao -> {
      CorrelationData correlacao = invocacao.getArgument(3);
      correlacao.getFuture().complete(new CorrelationData.Confirm(true, null));
      return null;
    }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    var adaptador = new RabbitPublicadorEventos(rabbit, "nexfleet.eventos",
        "operacoes.abastecimento.registrado.v1", "operacoes.manutencao.registrada.v1",
        "operacoes.abastecimento.atualizado.v1", "operacoes.abastecimento.excluido.v1",
        "operacoes.manutencao.atualizada.v1", "operacoes.manutencao.excluida.v1");

    adaptador.publicar(evento(agregado, tipo));

    ArgumentCaptor<Message> mensagem = ArgumentCaptor.forClass(Message.class);
    verify(rabbit).send(eq("nexfleet.eventos"), eq(rota), mensagem.capture(),
        any(CorrelationData.class));
    assertEquals(tipo, mensagem.getValue().getMessageProperties().getHeader("eventoTipo"));
  }

  @ParameterizedTest
  @CsvSource({
    "ABASTECIMENTO, AbastecimentoAtualizado, AbastecimentoAtualizado.v1",
    "ABASTECIMENTO, AbastecimentoExcluido, AbastecimentoExcluido.v1",
    "MANUTENCAO, ManutencaoAtualizada, ManutencaoAtualizada.v1",
    "MANUTENCAO, ManutencaoExcluida, ManutencaoExcluida.v1"
  })
  void serviceBusPublicaCadaTipoNovoComEventType(String agregado, String tipo, String eventType) {
    var sender = mock(ServiceBusSenderClient.class);

    new ServiceBusPublicadorEventos(sender).publicar(evento(agregado, tipo));

    var captor = ArgumentCaptor.forClass(ServiceBusMessage.class);
    verify(sender).sendMessage(captor.capture());
    assertEquals(eventType, captor.getValue().getApplicationProperties().get("eventType"));
    assertEquals(eventType, captor.getValue().getSubject());
  }

  private static EventoPendente evento(String agregado, String tipo) {
    return new EventoPendente(UUID.randomUUID(), agregado, UUID.randomUUID(), tipo, 1,
        UUID.randomUUID(), "{}", Instant.parse("2026-11-10T12:00:00Z"), 0);
  }
}

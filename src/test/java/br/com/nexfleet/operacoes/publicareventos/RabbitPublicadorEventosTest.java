package br.com.nexfleet.operacoes.publicareventos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import br.com.nexfleet.operacoes.publicareventos.application.EventoPendente;
import br.com.nexfleet.operacoes.publicareventos.infrastructure.RabbitPublicadorEventos;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class RabbitPublicadorEventosTest {
  @Test
  void enviaJsonPersistenteEEsperaConfirmacaoDoBroker() {
    RabbitTemplate rabbit = mock(RabbitTemplate.class);
    doAnswer(invocacao -> {
      CorrelationData correlacao = invocacao.getArgument(3);
      correlacao.getFuture().complete(new CorrelationData.Confirm(true, null));
      return null;
    }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    var adaptador = new RabbitPublicadorEventos(
        rabbit, "nexfleet.eventos", "operacoes.abastecimento.registrado.v1",
        "operacoes.manutencao.registrada.v1");
    UUID eventoId = UUID.randomUUID();
    UUID correlationId = UUID.randomUUID();
    EventoPendente evento = new EventoPendente(
        eventoId, "ABASTECIMENTO", UUID.randomUUID(), "AbastecimentoRegistrado",
        1, correlationId, "{\"eventoId\":\"" + eventoId + "\"}",
        Instant.parse("2026-11-05T15:00:00Z"), 0);

    adaptador.publicar(evento);

    ArgumentCaptor<Message> mensagem = ArgumentCaptor.forClass(Message.class);
    verify(rabbit).send(
        eq("nexfleet.eventos"),
        eq("operacoes.abastecimento.registrado.v1"),
        mensagem.capture(),
        any(CorrelationData.class));
    assertEquals("application/json", mensagem.getValue().getMessageProperties().getContentType());
    assertEquals(
        MessageDeliveryMode.PERSISTENT,
        mensagem.getValue().getMessageProperties().getDeliveryMode());
    assertEquals(eventoId.toString(), mensagem.getValue().getMessageProperties().getMessageId());
    assertEquals(correlationId.toString(),
        mensagem.getValue().getMessageProperties().getHeader("X-Correlation-ID"));
  }

  @Test
  void falhaQuandoExchangeNaoPossuiFilaVinculada() {
    RabbitTemplate rabbit = mock(RabbitTemplate.class);
    doAnswer(invocacao -> {
      Message mensagem = invocacao.getArgument(2);
      CorrelationData correlacao = invocacao.getArgument(3);
      correlacao.setReturned(new ReturnedMessage(
          mensagem, 312, "NO_ROUTE", "nexfleet.eventos",
          "operacoes.abastecimento.registrado.v1"));
      correlacao.getFuture().complete(new CorrelationData.Confirm(true, null));
      return null;
    }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    var adaptador = new RabbitPublicadorEventos(
        rabbit, "nexfleet.eventos", "operacoes.abastecimento.registrado.v1",
        "operacoes.manutencao.registrada.v1");
    UUID eventoId = UUID.randomUUID();
    EventoPendente evento = new EventoPendente(
        eventoId, "ABASTECIMENTO", UUID.randomUUID(), "AbastecimentoRegistrado",
        1, UUID.randomUUID(), "{\"eventoId\":\"" + eventoId + "\"}",
        Instant.parse("2026-11-05T15:00:00Z"), 0);

    assertThrows(IllegalStateException.class, () -> adaptador.publicar(evento));
  }

  @Test
  void selecionaRotaPeloTipoDoEvento() {
    RabbitTemplate rabbit = mock(RabbitTemplate.class);
    doAnswer(invocacao -> {
      CorrelationData correlacao = invocacao.getArgument(3);
      correlacao.getFuture().complete(new CorrelationData.Confirm(true, null));
      return null;
    }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    var adaptador = new RabbitPublicadorEventos(rabbit, "nexfleet.eventos",
        "operacoes.abastecimento.registrado.v1", "operacoes.manutencao.registrada.v1");
    UUID eventoId = UUID.randomUUID();
    adaptador.publicar(new EventoPendente(eventoId, "MANUTENCAO", UUID.randomUUID(),
        "ManutencaoRegistrada", 1, UUID.randomUUID(), "{}",
        Instant.parse("2026-11-06T12:00:00Z"), 0));
    verify(rabbit).send(eq("nexfleet.eventos"),
        eq("operacoes.manutencao.registrada.v1"), any(Message.class), any(CorrelationData.class));
  }
}

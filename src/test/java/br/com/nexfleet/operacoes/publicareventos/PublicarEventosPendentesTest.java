package br.com.nexfleet.operacoes.publicareventos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import br.com.nexfleet.operacoes.publicareventos.application.*;
import java.time.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublicarEventosPendentesTest {
  private static final Instant AGORA = Instant.parse("2026-11-05T15:00:00Z");

  private final EventosOutbox outbox = mock(EventosOutbox.class);
  private final PublicadorEventos publicador = mock(PublicadorEventos.class);
  private final PublicarEventosPendentes caso = new PublicarEventosPendentes(
      outbox, publicador, Clock.fixed(AGORA, ZoneOffset.UTC), Duration.ofSeconds(5));

  @Test
  void marcaComoPublicadoSomenteDepoisDaConfirmacao() {
    EventoPendente evento = evento();
    when(outbox.bloquearPendentes(20, AGORA)).thenReturn(List.of(evento));

    var resultado = caso.executar(20);

    var ordem = inOrder(publicador, outbox);
    ordem.verify(publicador).publicar(evento);
    ordem.verify(outbox).marcarPublicado(evento.id(), AGORA);
    verify(outbox, never()).registrarFalha(any(), any(), any());
    assertEquals(new PublicarEventosPendentes.Resultado(1, 1, 0), resultado);
  }

  @Test
  void preservaPendenteEAgendaRetryQuandoBrokerFalha() {
    EventoPendente evento = evento();
    when(outbox.bloquearPendentes(20, AGORA)).thenReturn(List.of(evento));
    doThrow(new IllegalStateException("broker indisponível"))
        .when(publicador).publicar(evento);

    var resultado = caso.executar(20);

    verify(outbox, never()).marcarPublicado(any(), any());
    verify(outbox).registrarFalha(
        evento.id(), "broker indisponível", AGORA.plusSeconds(5));
    assertEquals(new PublicarEventosPendentes.Resultado(1, 0, 1), resultado);
  }

  private EventoPendente evento() {
    return new EventoPendente(
        UUID.randomUUID(), "ABASTECIMENTO", UUID.randomUUID(),
        "AbastecimentoRegistrado", 1, UUID.randomUUID(), "{\"litros\":40}",
        AGORA.minusSeconds(60), 0);
  }
}

package br.com.nexfleet.operacoes.publicareventos.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import br.com.nexfleet.operacoes.SqlServerIntegracao;
import br.com.nexfleet.operacoes.publicareventos.application.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class JdbcEventosOutboxIT extends SqlServerIntegracao {
  private static final Instant AGORA = Instant.parse("2026-11-08T12:00:00Z");
  private static final Duration RETRY = Duration.ofSeconds(30);

  @Autowired JdbcTemplate jdbc;
  @Autowired EventosOutbox outbox;
  @Autowired TransactionTemplate transacao;

  @BeforeEach
  @AfterEach
  void limparOutbox() {
    jdbc.update("DELETE FROM operacoes.outbox_evento");
  }

  @Test
  void doisPublicadoresConcorrentesPublicamCadaEventoExatamenteUmaVez() throws Exception {
    var semeados = new HashSet<UUID>();
    for (int i = 0; i < 10; i++) semeados.add(semear(AGORA.minusSeconds(600 - i)));
    var publicados = new ConcurrentLinkedQueue<UUID>();
    var ambosComLote = new CyclicBarrier(2);
    var desencontro = new AtomicBoolean();

    var executor = Executors.newFixedThreadPool(2);
    try {
      var primeiro = executor.submit(() -> publicarEmTransacao(publicados, ambosComLote, desencontro));
      var segundo = executor.submit(() -> publicarEmTransacao(publicados, ambosComLote, desencontro));
      var resultados = List.of(primeiro.get(60, TimeUnit.SECONDS),
          segundo.get(60, TimeUnit.SECONDS));

      assertAll(
          () -> assertFalse(desencontro.get(),
              "Os dois publicadores deveriam segurar lotes disjuntos ao mesmo tempo."),
          () -> assertEquals(List.of(5, 5),
              resultados.stream().map(PublicarEventosPendentes.Resultado::publicados).toList()),
          () -> assertEquals(10, publicados.size()),
          () -> assertEquals(semeados, new HashSet<>(publicados)),
          () -> assertTrue(outbox.bloquearPendentes(100, AGORA.plus(Duration.ofDays(1)))
              .isEmpty()));
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void falhaDePublicacaoIncrementaTentativasEAgendaNovaTentativa() {
    Instant ocorridoEm = AGORA.minusSeconds(60);
    UUID id = semear(ocorridoEm);
    PublicadorEventos brokerFora = evento -> { throw new IllegalStateException("broker fora"); };
    var casoDeUso = new PublicarEventosPendentes(
        outbox, brokerFora, Clock.fixed(AGORA, ZoneOffset.UTC), RETRY);

    var resultado = transacao.execute(status -> casoDeUso.executar(10));

    var antesDoRetry = outbox.bloquearPendentes(10, AGORA.plus(RETRY).minusMillis(1));
    var noRetry = outbox.bloquearPendentes(10, AGORA.plus(RETRY));
    assertAll(
        () -> assertEquals(new PublicarEventosPendentes.Resultado(1, 0, 1), resultado),
        () -> assertTrue(antesDoRetry.isEmpty()),
        () -> assertEquals(1, noRetry.size()),
        () -> assertEquals(id, noRetry.get(0).id()),
        () -> assertEquals(1, noRetry.get(0).tentativas()),
        () -> assertEquals("ManutencaoRegistrada", noRetry.get(0).tipo()),
        () -> assertEquals(1, noRetry.get(0).versao()),
        () -> assertEquals(ocorridoEm, noRetry.get(0).ocorridoEm()),
        () -> assertEquals("{\"eventoId\":\"" + id + "\"}", noRetry.get(0).payload()),
        () -> assertEquals("broker fora", jdbc.queryForObject(
            "SELECT ultimo_erro FROM operacoes.outbox_evento WHERE id=?", String.class, id)));
  }

  private PublicarEventosPendentes.Resultado publicarEmTransacao(
      Collection<UUID> publicados, CyclicBarrier ambosComLote, AtomicBoolean desencontro) {
    var primeiraChamada = new boolean[] {true};
    PublicadorEventos fake = evento -> {
      if (primeiraChamada[0]) {
        primeiraChamada[0] = false;
        try {
          ambosComLote.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException | BrokenBarrierException | TimeoutException e) {
          desencontro.set(true);
        }
      }
      publicados.add(evento.id());
    };
    var casoDeUso = new PublicarEventosPendentes(
        outbox, fake, Clock.fixed(AGORA, ZoneOffset.UTC), RETRY);
    return transacao.execute(status -> casoDeUso.executar(5));
  }

  private UUID semear(Instant ocorridoEm) {
    UUID id = UUID.randomUUID();
    var momento = OffsetDateTime.ofInstant(ocorridoEm, ZoneOffset.UTC);
    jdbc.update("INSERT INTO operacoes.outbox_evento(id,agregado_tipo,agregado_id,evento_tipo,"
        + "evento_versao,correlation_id,payload,ocorrido_em,proxima_tentativa_em) "
        + "VALUES (?,'MANUTENCAO',?,'ManutencaoRegistrada',1,?,?,?,?)",
        id, UUID.randomUUID(), UUID.randomUUID(), "{\"eventoId\":\"" + id + "\"}",
        momento, momento);
    return id;
  }
}

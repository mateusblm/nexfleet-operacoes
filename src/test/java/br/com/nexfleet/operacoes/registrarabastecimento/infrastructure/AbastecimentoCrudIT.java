package br.com.nexfleet.operacoes.registrarabastecimento.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import br.com.nexfleet.operacoes.SqlServerIntegracao;
import br.com.nexfleet.operacoes.registrarabastecimento.api.AbastecimentosController;
import br.com.nexfleet.operacoes.registrarabastecimento.application.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;

/** Consulta, edição e exclusão de abastecimento pelo controller, com SQL Server real. */
class AbastecimentoCrudIT extends SqlServerIntegracao {
  private static final Instant AGORA = Instant.parse("2026-11-10T12:00:00Z");
  private static final UUID VEICULO = UUID.fromString("77777777-7777-7777-7777-777777777777");
  private static final UUID MOTORISTA = UUID.fromString("88888888-8888-8888-8888-888888888888");

  @Autowired JdbcTemplate jdbc;
  @Autowired Abastecimentos repositorio;
  private AbastecimentosController controller;

  @BeforeEach
  void montarCasoDeUso() {
    limparBanco();
    ReferenciasFrota referenciasValidas = (token, veiculo, motorista, realizadoEm) -> true;
    controller = new AbastecimentosController(new GerenciarAbastecimentos(
        repositorio, referenciasValidas, Clock.fixed(AGORA, ZoneOffset.UTC)));
  }

  @AfterEach
  void limparBanco() {
    jdbc.update("DELETE FROM operacoes.outbox_evento");
    jdbc.update("DELETE FROM operacoes.abastecimento");
    jdbc.update("DELETE FROM operacoes.hodometro");
  }

  @Test
  void consultaPorIdDevolveAbastecimentoComVersaoUm() {
    var registrado = registrar(AGORA.minusSeconds(3600), 1_500L);

    var resposta = controller.buscar(jwt("GESTOR_FROTA"), registrado.id());

    assertAll(
        () -> assertEquals(200, resposta.getStatusCode().value()),
        () -> assertEquals(registrado.id(), resposta.getBody().id()),
        () -> assertEquals(VEICULO, resposta.getBody().veiculoId()),
        () -> assertEquals("DIESEL S10", resposta.getBody().combustivel()),
        () -> assertEquals(new BigDecimal("42.50"), resposta.getBody().litros()),
        () -> assertEquals(1_500L, resposta.getBody().quilometragem()),
        () -> assertEquals(1, resposta.getBody().versao()));
  }

  @Test
  void consultaDeIdInexistenteResponde404() {
    var falha = assertThrows(FalhaAbastecimento.class,
        () -> controller.buscar(jwt("ADMINISTRADOR"), UUID.randomUUID()));

    assertEquals(404, controller.falha(falha).getStatusCode().value());
  }

  @Test
  void edicaoValidaGravaIncrementaVersaoEPublicaAbastecimentoAtualizadoNaOutbox() {
    var registrado = registrar(AGORA.minusSeconds(3600), 1_500L);
    UUID gestor = UUID.fromString("99999999-9999-9999-9999-999999999999");

    var resposta = controller.atualizar(jwt("GESTOR_FROTA", gestor), registrado.id(),
        new GerenciarAbastecimentos.Edicao(" etanol ", new BigDecimal("40.10"),
            new BigDecimal("199.99"), 1_650L));
    var consultado = controller.buscar(jwt("GESTOR_FROTA"), registrado.id()).getBody();
    var evento = jdbc.queryForMap("SELECT agregado_tipo,"
        + " CAST(agregado_id AS NVARCHAR(36)) AS agregado_id, evento_versao,"
        + " CAST(id AS NVARCHAR(36)) AS evento_id,"
        + " CAST(correlation_id AS NVARCHAR(36)) AS correlation_id,"
        + " JSON_VALUE(payload,'$.eventoId') AS evento_id_payload,"
        + " JSON_VALUE(payload,'$.abastecimentoId') AS abastecimento_id,"
        + " JSON_VALUE(payload,'$.veiculoId') AS veiculo_id,"
        + " JSON_VALUE(payload,'$.motoristaId') AS motorista_id,"
        + " JSON_VALUE(payload,'$.realizadoEm') AS realizado_em,"
        + " JSON_VALUE(payload,'$.combustivel') AS combustivel,"
        + " JSON_VALUE(payload,'$.litros') AS litros,"
        + " JSON_VALUE(payload,'$.valorTotal') AS valor_total,"
        + " JSON_VALUE(payload,'$.quilometragem') AS quilometragem,"
        + " JSON_VALUE(payload,'$.versao') AS versao,"
        + " JSON_VALUE(payload,'$.atualizadoPor') AS atualizado_por,"
        + " JSON_VALUE(payload,'$.correlationId') AS correlation_payload,"
        + " (SELECT COUNT(*) FROM OPENJSON(payload)) AS campos"
        + " FROM operacoes.outbox_evento WHERE evento_tipo='AbastecimentoAtualizado'");

    assertAll(
        () -> assertEquals(200, resposta.getStatusCode().value()),
        () -> assertEquals(2, resposta.getBody().versao()),
        () -> assertEquals("ETANOL", consultado.combustivel()),
        () -> assertEquals(new BigDecimal("40.10"), consultado.litros()),
        () -> assertEquals(new BigDecimal("199.99"), consultado.valorTotal()),
        () -> assertEquals(1_650L, consultado.quilometragem()),
        () -> assertEquals(VEICULO, consultado.veiculoId()),
        () -> assertEquals(MOTORISTA, consultado.motoristaId()),
        () -> assertEquals(AGORA.minusSeconds(3600), consultado.realizadoEm()),
        () -> assertEquals(2, consultado.versao()),
        () -> assertEquals(1_650L, hodometro()),
        () -> assertEquals("ABASTECIMENTO", evento.get("agregado_tipo")),
        () -> assertEquals(registrado.id().toString(),
            String.valueOf(evento.get("agregado_id")).toLowerCase()),
        () -> assertEquals(1, ((Number) evento.get("evento_versao")).intValue()),
        () -> assertEquals(String.valueOf(evento.get("evento_id")).toLowerCase(),
            evento.get("evento_id_payload")),
        () -> assertEquals(registrado.id().toString(), evento.get("abastecimento_id")),
        () -> assertEquals(VEICULO.toString(), evento.get("veiculo_id")),
        () -> assertEquals(MOTORISTA.toString(), evento.get("motorista_id")),
        () -> assertEquals("2026-11-10T11:00:00Z", evento.get("realizado_em")),
        () -> assertEquals("ETANOL", evento.get("combustivel")),
        () -> assertEquals("40.10", evento.get("litros")),
        () -> assertEquals("199.99", evento.get("valor_total")),
        () -> assertEquals("1650", evento.get("quilometragem")),
        () -> assertEquals("2", evento.get("versao")),
        () -> assertEquals(gestor.toString(), evento.get("atualizado_por")),
        () -> assertEquals(String.valueOf(evento.get("correlation_id")).toLowerCase(),
            evento.get("correlation_payload")),
        () -> assertEquals(12, ((Number) evento.get("campos")).intValue()));
  }

  @Test
  void edicaoDoIntermediarioDentroDoIntervaloNaoMudaOHodometro() {
    registrar(AGORA.minusSeconds(7200), 1_000L);
    var meio = registrar(AGORA.minusSeconds(5400), 2_000L);
    registrar(AGORA.minusSeconds(3600), 3_000L);

    var resposta = controller.atualizar(jwt("ADMINISTRADOR"), meio.id(), edicao(2_999L));

    assertAll(
        () -> assertEquals(2_999L, resposta.getBody().quilometragem()),
        () -> assertEquals(3_000L, hodometro()));
  }

  @Test
  void edicaoQueReduzOMaiorKmRecalculaOHodometro() {
    registrar(AGORA.minusSeconds(7200), 1_000L);
    var ultimo = registrar(AGORA.minusSeconds(3600), 3_000L);

    controller.atualizar(jwt("GESTOR_FROTA"), ultimo.id(), edicao(1_001L));

    assertEquals(1_001L, hodometro());
  }

  @Test
  void quilometragemForaDoIntervaloEntreVizinhosResponde422SemAlterarNada() {
    registrar(AGORA.minusSeconds(7200), 1_000L);
    var meio = registrar(AGORA.minusSeconds(5400), 2_000L);
    registrar(AGORA.minusSeconds(3600), 3_000L);
    long eventosAntes = contar("operacoes.outbox_evento");

    var igualAnterior = assertThrows(FalhaAbastecimento.class,
        () -> controller.atualizar(jwt("GESTOR_FROTA"), meio.id(), edicao(1_000L)));
    var igualSeguinte = assertThrows(FalhaAbastecimento.class,
        () -> controller.atualizar(jwt("GESTOR_FROTA"), meio.id(), edicao(3_000L)));
    var acimaSeguinte = assertThrows(FalhaAbastecimento.class,
        () -> controller.atualizar(jwt("GESTOR_FROTA"), meio.id(), edicao(5_000L)));

    assertAll(
        () -> assertEquals(422, controller.falha(igualAnterior).getStatusCode().value()),
        () -> assertEquals(422, controller.falha(igualSeguinte).getStatusCode().value()),
        () -> assertEquals(422, controller.falha(acimaSeguinte).getStatusCode().value()),
        () -> assertEquals(meio, controller.buscar(jwt("GESTOR_FROTA"), meio.id()).getBody()),
        () -> assertEquals(3_000L, hodometro()),
        () -> assertEquals(eventosAntes, contar("operacoes.outbox_evento")));
  }

  @Test
  void litrosValorOuCombustivelInvalidosRespondem422() {
    var registrado = registrar(AGORA.minusSeconds(3600), 1_500L);

    var litros = assertThrows(FalhaAbastecimento.class,
        () -> controller.atualizar(jwt("GESTOR_FROTA"), registrado.id(),
            new GerenciarAbastecimentos.Edicao("DIESEL", new BigDecimal("40.123"),
                new BigDecimal("199.99"), 1_600L)));
    var valor = assertThrows(FalhaAbastecimento.class,
        () -> controller.atualizar(jwt("GESTOR_FROTA"), registrado.id(),
            new GerenciarAbastecimentos.Edicao("DIESEL", new BigDecimal("40.10"),
                BigDecimal.ZERO, 1_600L)));
    var combustivel = assertThrows(FalhaAbastecimento.class,
        () -> controller.atualizar(jwt("GESTOR_FROTA"), registrado.id(),
            new GerenciarAbastecimentos.Edicao("  ", new BigDecimal("40.10"),
                new BigDecimal("199.99"), 1_600L)));

    assertAll(
        () -> assertEquals(422, controller.falha(litros).getStatusCode().value()),
        () -> assertEquals(422, controller.falha(valor).getStatusCode().value()),
        () -> assertEquals(422, controller.falha(combustivel).getStatusCode().value()),
        () -> assertEquals(registrado,
            controller.buscar(jwt("GESTOR_FROTA"), registrado.id()).getBody()));
  }

  @Test
  void edicaoDeIdInexistenteResponde404SemGravarEvento() {
    var falha = assertThrows(FalhaAbastecimento.class,
        () -> controller.atualizar(jwt("GESTOR_FROTA"), UUID.randomUUID(), edicao(1_000L)));

    assertAll(
        () -> assertEquals(404, controller.falha(falha).getStatusCode().value()),
        () -> assertEquals(0L, contar("operacoes.outbox_evento")));
  }

  private static GerenciarAbastecimentos.Edicao edicao(long km) {
    return new GerenciarAbastecimentos.Edicao("DIESEL S10", new BigDecimal("42.50"),
        new BigDecimal("289.90"), km);
  }

  private long contar(String tabela) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabela, Long.class);
  }

  private Long hodometro() {
    return jdbc.query("SELECT quilometragem FROM operacoes.hodometro WHERE veiculo_id=?",
        (rs, row) -> rs.getLong(1), VEICULO).stream().findFirst().orElse(null);
  }

  private Abastecimentos.Dados registrar(Instant realizadoEm, long km) {
    return controller.registrar(jwt("GESTOR_FROTA"), "Bearer t",
        new GerenciarAbastecimentos.Entrada(VEICULO, MOTORISTA, realizadoEm, "Diesel S10",
            new BigDecimal("42.50"), new BigDecimal("289.90"), km)).getBody();
  }

  private static Jwt jwt(String perfil) {
    return jwt(perfil, UUID.randomUUID());
  }

  private static Jwt jwt(String perfil, UUID sujeito) {
    return Jwt.withTokenValue("token-integracao")
        .header("alg", "none")
        .subject(sujeito.toString())
        .claim("perfil", perfil)
        .build();
  }
}

package br.com.nexfleet.operacoes.registrarabastecimento.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import br.com.nexfleet.operacoes.SqlServerIntegracao;
import br.com.nexfleet.operacoes.registrarabastecimento.api.AbastecimentosController;
import br.com.nexfleet.operacoes.registrarabastecimento.application.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;

class JdbcAbastecimentosIT extends SqlServerIntegracao {
  private static final Instant AGORA = Instant.parse("2026-11-08T12:00:00Z");
  private static final Instant REALIZADO_EM = AGORA.minus(Duration.ofHours(1));

  @Autowired JdbcTemplate jdbc;
  @Autowired Abastecimentos repositorio;
  private AbastecimentosController controller;

  @BeforeEach
  void montarCasoDeUso() {
    assertTrue(AopUtils.isAopProxy(repositorio),
        "O repositório precisa passar pelo proxy transacional do Spring.");
    ReferenciasFrota referenciasValidas = (token, veiculo, motorista, realizadoEm) -> true;
    var casoDeUso = new GerenciarAbastecimentos(
        repositorio, referenciasValidas, Clock.fixed(AGORA, ZoneOffset.UTC));
    controller = new AbastecimentosController(casoDeUso);
  }

  @AfterEach
  void limparBanco() {
    jdbc.execute("ALTER TABLE operacoes.outbox_evento "
        + "DROP CONSTRAINT IF EXISTS cq04_falha_injetada");
    jdbc.update("DELETE FROM operacoes.outbox_evento");
    jdbc.update("DELETE FROM operacoes.abastecimento");
    jdbc.update("DELETE FROM operacoes.hodometro");
  }

  @Test
  void cq03QuilometragemRegressivaRetorna422SemAlterarAsTresTabelas() {
    UUID veiculo = UUID.randomUUID();
    UUID motorista = UUID.randomUUID();
    jdbc.update("INSERT INTO operacoes.hodometro(veiculo_id,quilometragem) VALUES (?,?)",
        veiculo, 1_000L);

    FalhaAbastecimento falha = assertThrows(FalhaAbastecimento.class,
        () -> controller.registrar(jwt(), "Bearer teste-cq03",
            entrada(veiculo, motorista, 999L)));
    var resposta = controller.falha(falha);

    assertAll(
        () -> assertEquals(FalhaAbastecimento.Tipo.INVALIDO, falha.tipo()),
        () -> assertEquals(422, resposta.getStatusCode().value()),
        () -> assertTrue(resposta.getBody().get("message").contains("1000 km")),
        () -> assertEquals(0L, quantidadeAbastecimentos()),
        () -> assertEquals(0L, quantidadeEventos()),
        () -> assertEquals(1_000L, quilometragem(veiculo)));
  }

  @Test
  void quilometragemInferiorAoAbastecimentoAnteriorRetorna422SemGravarNada() {
    UUID veiculo = UUID.randomUUID();
    UUID motorista = UUID.randomUUID();
    controller.registrar(jwt(), "Bearer t", entrada(veiculo, motorista, 2_000L));

    FalhaAbastecimento falha = assertThrows(FalhaAbastecimento.class,
        () -> controller.registrar(jwt(), "Bearer t", entrada(veiculo, motorista, 1_999L)));

    assertAll(
        () -> assertEquals(422, controller.falha(falha).getStatusCode().value()),
        () -> assertEquals(1L, quantidadeAbastecimentos()),
        () -> assertEquals(1L, quantidadeEventos()),
        () -> assertEquals(2_000L, quilometragem(veiculo)));
  }

  @Test
  void cq04FalhaNaOutboxDesfazAbastecimentoHodometroEEvento() {
    UUID veiculo = UUID.randomUUID();
    UUID motorista = UUID.randomUUID();
    jdbc.execute("ALTER TABLE operacoes.outbox_evento "
        + "ADD CONSTRAINT cq04_falha_injetada "
        + "CHECK (agregado_tipo <> 'ABASTECIMENTO')");

    assertThrows(DataAccessException.class,
        () -> controller.registrar(jwt(), "Bearer teste-cq04",
            entrada(veiculo, motorista, 1_500L)));

    assertAll(
        () -> assertEquals(0L, quantidadeAbastecimentos()),
        () -> assertEquals(0L, quantidadeEventos()),
        () -> assertEquals(0L, quantidadeHodometros()),
        () -> assertFalse(repositorio.listar(veiculo, 0, 20).iterator().hasNext()));
  }

  @Test
  void registroGravaEventoAbastecimentoRegistradoComPayloadDoContrato() {
    UUID veiculo = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UUID motorista = UUID.fromString("22222222-2222-2222-2222-222222222222");

    var registrado = controller.registrar(jwt(), "Bearer t", entrada(veiculo, motorista, 1_500L))
        .getBody();

    Map<String, Object> evento = jdbc.queryForMap(
        "SELECT agregado_tipo, agregado_id, evento_tipo, evento_versao, correlation_id,"
            + " ISJSON(payload) AS json_valido,"
            + " JSON_VALUE(payload,'$.abastecimentoId') AS abastecimento_id,"
            + " JSON_VALUE(payload,'$.eventoId') AS evento_id_payload,"
            + " CAST(id AS NVARCHAR(36)) AS evento_id,"
            + " JSON_VALUE(payload,'$.veiculoId') AS veiculo_id,"
            + " JSON_VALUE(payload,'$.motoristaId') AS motorista_id,"
            + " JSON_VALUE(payload,'$.realizadoEm') AS realizado_em,"
            + " JSON_VALUE(payload,'$.combustivel') AS combustivel,"
            + " JSON_VALUE(payload,'$.litros') AS litros,"
            + " JSON_VALUE(payload,'$.valorTotal') AS valor_total,"
            + " JSON_VALUE(payload,'$.quilometragem') AS quilometragem,"
            + " JSON_VALUE(payload,'$.correlationId') AS correlation_payload"
            + " FROM operacoes.outbox_evento");

    assertAll(
        () -> assertEquals("ABASTECIMENTO", evento.get("agregado_tipo")),
        () -> assertEquals("AbastecimentoRegistrado", evento.get("evento_tipo")),
        () -> assertEquals(1, ((Number) evento.get("evento_versao")).intValue()),
        () -> assertEquals(1, ((Number) evento.get("json_valido")).intValue()),
        () -> assertEquals(registrado.id().toString(), evento.get("abastecimento_id")),
        () -> assertEquals(String.valueOf(evento.get("evento_id")).toLowerCase(),
            evento.get("evento_id_payload")),
        () -> assertEquals("11111111-1111-1111-1111-111111111111", evento.get("veiculo_id")),
        () -> assertEquals("22222222-2222-2222-2222-222222222222", evento.get("motorista_id")),
        () -> assertEquals("2026-11-08T11:00:00Z", evento.get("realizado_em")),
        () -> assertEquals("Diesel S10", evento.get("combustivel")),
        () -> assertEquals("42.50", evento.get("litros")),
        () -> assertEquals("289.90", evento.get("valor_total")),
        () -> assertEquals("1500", evento.get("quilometragem")),
        () -> assertEquals(String.valueOf(evento.get("correlation_id")).toLowerCase(),
            evento.get("correlation_payload")),
        () -> assertEquals(1_500L, quilometragem(veiculo)));
  }

  private GerenciarAbastecimentos.Entrada entrada(
      UUID veiculo, UUID motorista, long quilometragem) {
    return new GerenciarAbastecimentos.Entrada(
        veiculo,
        motorista,
        REALIZADO_EM,
        "Diesel S10",
        new BigDecimal("42.50"),
        new BigDecimal("289.90"),
        quilometragem);
  }

  private Jwt jwt() {
    return Jwt.withTokenValue("token-integracao")
        .header("alg", "none")
        .subject(UUID.randomUUID().toString())
        .claim("perfil", "GESTOR_FROTA")
        .build();
  }

  private long quantidadeAbastecimentos() {
    return jdbc.queryForObject("SELECT COUNT(*) FROM operacoes.abastecimento", Long.class);
  }

  private long quantidadeEventos() {
    return jdbc.queryForObject("SELECT COUNT(*) FROM operacoes.outbox_evento", Long.class);
  }

  private long quantidadeHodometros() {
    return jdbc.queryForObject("SELECT COUNT(*) FROM operacoes.hodometro", Long.class);
  }

  private long quilometragem(UUID veiculo) {
    return jdbc.queryForObject(
        "SELECT quilometragem FROM operacoes.hodometro WHERE veiculo_id=?",
        Long.class,
        veiculo);
  }
}

package br.com.nexfleet.operacoes.registrarmanutencao.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import br.com.nexfleet.operacoes.SqlServerIntegracao;
import br.com.nexfleet.operacoes.registrarmanutencao.api.ManutencoesController;
import br.com.nexfleet.operacoes.registrarmanutencao.application.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;

class JdbcManutencoesIT extends SqlServerIntegracao {
  private static final Instant AGORA = Instant.parse("2026-11-08T12:00:00Z");
  private static final LocalDate REALIZADA_EM = LocalDate.parse("2026-11-07");

  @Autowired JdbcTemplate jdbc;
  @Autowired Manutencoes repositorio;
  private ManutencoesController controller;

  @BeforeEach
  void montarCasoDeUso() {
    assertTrue(AopUtils.isAopProxy(repositorio),
        "O repositório precisa passar pelo proxy transacional do Spring.");
    limparBanco();
    VeiculosFrota veiculoAtivo = (token, veiculo) -> true;
    controller = new ManutencoesController(new GerenciarManutencoes(
        repositorio, veiculoAtivo, Clock.fixed(AGORA, ZoneOffset.UTC)));
  }

  @AfterEach
  void limparBanco() {
    jdbc.update("DELETE FROM operacoes.outbox_evento");
    jdbc.update("DELETE FROM operacoes.manutencao");
  }

  @Test
  void proximaManutencaoAnteriorAoRegistroRetorna422SemGravarManutencaoNemEvento() {
    var entrada = entrada(UUID.randomUUID(), LocalDate.parse("2026-11-01"));

    FalhaManutencao falha = assertThrows(FalhaManutencao.class,
        () -> controller.registrar(jwt(), "Bearer t", entrada));
    var resposta = controller.falha(falha);

    assertAll(
        () -> assertEquals(422, resposta.getStatusCode().value()),
        () -> assertEquals(0L, contar("operacoes.manutencao")),
        () -> assertEquals(0L, contar("operacoes.outbox_evento")));
  }

  @Test
  void manutencaoValidaGravaRegistroEEventoManutencaoRegistradaNaOutbox() {
    UUID veiculo = UUID.fromString("33333333-3333-3333-3333-333333333333");

    var resposta = controller.registrar(jwt(), "Bearer t",
        entrada(veiculo, LocalDate.parse("2027-05-07")));
    var registrada = resposta.getBody();

    Map<String, Object> evento = jdbc.queryForMap(
        "SELECT agregado_tipo, CAST(agregado_id AS NVARCHAR(36)) AS agregado_id, evento_tipo,"
            + " evento_versao, CAST(correlation_id AS NVARCHAR(36)) AS correlation_id,"
            + " CAST(id AS NVARCHAR(36)) AS evento_id, ISJSON(payload) AS json_valido,"
            + " JSON_VALUE(payload,'$.eventoId') AS evento_id_payload,"
            + " JSON_VALUE(payload,'$.manutencaoId') AS manutencao_id,"
            + " JSON_VALUE(payload,'$.veiculoId') AS veiculo_id,"
            + " JSON_VALUE(payload,'$.tipo') AS tipo,"
            + " JSON_VALUE(payload,'$.realizadaEm') AS realizada_em,"
            + " JSON_VALUE(payload,'$.quilometragem') AS quilometragem,"
            + " JSON_VALUE(payload,'$.descricao') AS descricao,"
            + " JSON_VALUE(payload,'$.oficina') AS oficina,"
            + " JSON_VALUE(payload,'$.valor') AS valor,"
            + " JSON_VALUE(payload,'$.proximaManutencao') AS proxima,"
            + " JSON_VALUE(payload,'$.correlationId') AS correlation_payload"
            + " FROM operacoes.outbox_evento");

    assertAll(
        () -> assertEquals(201, resposta.getStatusCode().value()),
        () -> assertEquals(1L, contar("operacoes.manutencao")),
        () -> assertEquals(veiculo, registrada.veiculoId()),
        () -> assertEquals(REALIZADA_EM, registrada.realizadaEm()),
        () -> assertEquals(LocalDate.parse("2027-05-07"), registrada.proximaManutencao()),
        () -> assertEquals(new BigDecimal("350.00"), registrada.valor()),
        () -> assertEquals(AGORA, registrada.criadoEm()),
        () -> assertEquals("MANUTENCAO", evento.get("agregado_tipo")),
        () -> assertEquals(registrada.id().toString(),
            String.valueOf(evento.get("agregado_id")).toLowerCase()),
        () -> assertEquals("ManutencaoRegistrada", evento.get("evento_tipo")),
        () -> assertEquals(1, ((Number) evento.get("evento_versao")).intValue()),
        () -> assertEquals(1, ((Number) evento.get("json_valido")).intValue()),
        () -> assertEquals(String.valueOf(evento.get("evento_id")).toLowerCase(),
            evento.get("evento_id_payload")),
        () -> assertEquals(registrada.id().toString(), evento.get("manutencao_id")),
        () -> assertEquals("33333333-3333-3333-3333-333333333333", evento.get("veiculo_id")),
        () -> assertEquals("TROCA_OLEO", evento.get("tipo")),
        () -> assertEquals("2026-11-07", evento.get("realizada_em")),
        () -> assertEquals("48000", evento.get("quilometragem")),
        () -> assertEquals("Troca de óleo e filtro", evento.get("descricao")),
        () -> assertEquals("Oficina Central", evento.get("oficina")),
        () -> assertEquals("350.00", evento.get("valor")),
        () -> assertEquals("2027-05-07", evento.get("proxima")),
        () -> assertEquals(String.valueOf(evento.get("correlation_id")).toLowerCase(),
            evento.get("correlation_payload")));
  }

  @Test
  void listagemPaginaManutencoesDoVeiculoDaMaisRecenteParaAMaisAntiga() {
    UUID veiculo = UUID.randomUUID();
    for (int dia = 1; dia <= 3; dia++)
      controller.registrar(jwt(), "Bearer t", new GerenciarManutencoes.Entrada(veiculo,
          TipoManutencao.REVISAO, LocalDate.of(2026, 10, dia), 1_000L * dia, "Revisão",
          "Oficina Central", new BigDecimal("100.00"), null));
    controller.registrar(jwt(), "Bearer t", entrada(UUID.randomUUID(), null));

    var segundaPagina = controller.listar(jwt(), veiculo, 1, 2).getBody();
    var todas = controller.listar(jwt(), null, 0, 20).getBody();

    assertAll(
        () -> assertEquals(1, segundaPagina.size()),
        () -> assertEquals(LocalDate.of(2026, 10, 1), segundaPagina.get(0).realizadaEm()),
        () -> assertEquals(4, todas.size()),
        () -> assertEquals(REALIZADA_EM, todas.get(0).realizadaEm()));
  }

  private GerenciarManutencoes.Entrada entrada(UUID veiculo, LocalDate proxima) {
    return new GerenciarManutencoes.Entrada(veiculo, TipoManutencao.TROCA_OLEO, REALIZADA_EM,
        48_000L, "Troca de óleo e filtro", "Oficina Central", new BigDecimal("350.00"), proxima);
  }

  private Jwt jwt() {
    return Jwt.withTokenValue("token-integracao")
        .header("alg", "none")
        .subject(UUID.randomUUID().toString())
        .claim("perfil", "GESTOR_FROTA")
        .build();
  }

  private long contar(String tabela) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabela, Long.class);
  }
}

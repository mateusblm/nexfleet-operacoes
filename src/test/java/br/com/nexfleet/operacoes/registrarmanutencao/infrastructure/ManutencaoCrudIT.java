package br.com.nexfleet.operacoes.registrarmanutencao.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import br.com.nexfleet.operacoes.SqlServerIntegracao;
import br.com.nexfleet.operacoes.registrarmanutencao.api.ManutencoesController;
import br.com.nexfleet.operacoes.registrarmanutencao.application.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;

/** Consulta, edição e exclusão de manutenção pelo controller, com SQL Server real. */
class ManutencaoCrudIT extends SqlServerIntegracao {
  private static final Instant AGORA = Instant.parse("2026-11-10T12:00:00Z");
  private static final UUID VEICULO = UUID.fromString("44444444-4444-4444-4444-444444444444");

  @Autowired JdbcTemplate jdbc;
  @Autowired Manutencoes repositorio;
  private ManutencoesController controller;

  @BeforeEach
  void montarCasoDeUso() {
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
  void consultaPorIdDevolveManutencaoComVersaoUm() {
    var registrada = registrar();

    var resposta = controller.buscar(jwt("GESTOR_FROTA"), registrada.id());

    assertAll(
        () -> assertEquals(200, resposta.getStatusCode().value()),
        () -> assertEquals(registrada.id(), resposta.getBody().id()),
        () -> assertEquals("Troca de óleo e filtro", resposta.getBody().descricao()),
        () -> assertEquals(new BigDecimal("350.00"), resposta.getBody().valor()),
        () -> assertEquals(1, resposta.getBody().versao()));
  }

  @Test
  void consultaDeIdInexistenteResponde404() {
    var falha = assertThrows(FalhaManutencao.class,
        () -> controller.buscar(jwt("ADMINISTRADOR"), UUID.randomUUID()));

    assertEquals(404, controller.falha(falha).getStatusCode().value());
  }

  @Test
  void edicaoValidaGravaIncrementaVersaoEPublicaManutencaoAtualizadaNaOutbox() {
    var registrada = registrar();
    UUID gestor = UUID.fromString("55555555-5555-5555-5555-555555555555");

    var resposta = controller.atualizar(jwt("GESTOR_FROTA", gestor), registrada.id(),
        new GerenciarManutencoes.Edicao(TipoManutencao.CORRETIVA, LocalDate.parse("2026-11-08"),
            48_500L, " Troca da correia ", " Oficina Norte ", new BigDecimal("420.50"), null));
    var consultada = controller.buscar(jwt("GESTOR_FROTA"), registrada.id()).getBody();
    var evento = jdbc.queryForMap("SELECT agregado_tipo,"
        + " CAST(agregado_id AS NVARCHAR(36)) AS agregado_id, evento_tipo, evento_versao,"
        + " CAST(id AS NVARCHAR(36)) AS evento_id,"
        + " CAST(correlation_id AS NVARCHAR(36)) AS correlation_id,"
        + " JSON_VALUE(payload,'$.eventoId') AS evento_id_payload,"
        + " JSON_VALUE(payload,'$.manutencaoId') AS manutencao_id,"
        + " JSON_VALUE(payload,'$.veiculoId') AS veiculo_id,"
        + " JSON_VALUE(payload,'$.tipo') AS tipo,"
        + " JSON_VALUE(payload,'$.realizadaEm') AS realizada_em,"
        + " JSON_VALUE(payload,'$.quilometragem') AS quilometragem,"
        + " JSON_VALUE(payload,'$.descricao') AS descricao,"
        + " JSON_VALUE(payload,'$.oficina') AS oficina,"
        + " JSON_VALUE(payload,'$.valor') AS valor,"
        + " JSON_QUERY(payload,'$.proximaManutencao') AS proxima_objeto,"
        + " JSON_VALUE(payload,'$.versao') AS versao,"
        + " JSON_VALUE(payload,'$.atualizadoPor') AS atualizado_por,"
        + " JSON_VALUE(payload,'$.correlationId') AS correlation_payload"
        + " FROM operacoes.outbox_evento WHERE evento_tipo='ManutencaoAtualizada'");

    assertAll(
        () -> assertEquals(200, resposta.getStatusCode().value()),
        () -> assertEquals(2, resposta.getBody().versao()),
        () -> assertEquals(VEICULO, resposta.getBody().veiculoId()),
        () -> assertEquals(TipoManutencao.CORRETIVA, consultada.tipo()),
        () -> assertEquals(LocalDate.parse("2026-11-08"), consultada.realizadaEm()),
        () -> assertEquals(48_500L, consultada.quilometragem()),
        () -> assertEquals("Troca da correia", consultada.descricao()),
        () -> assertEquals("Oficina Norte", consultada.oficina()),
        () -> assertEquals(new BigDecimal("420.50"), consultada.valor()),
        () -> assertNull(consultada.proximaManutencao()),
        () -> assertEquals(2, consultada.versao()),
        () -> assertEquals("MANUTENCAO", evento.get("agregado_tipo")),
        () -> assertEquals(registrada.id().toString(),
            String.valueOf(evento.get("agregado_id")).toLowerCase()),
        () -> assertEquals(1, ((Number) evento.get("evento_versao")).intValue()),
        () -> assertEquals(String.valueOf(evento.get("evento_id")).toLowerCase(),
            evento.get("evento_id_payload")),
        () -> assertEquals(registrada.id().toString(), evento.get("manutencao_id")),
        () -> assertEquals(VEICULO.toString(), evento.get("veiculo_id")),
        () -> assertEquals("CORRETIVA", evento.get("tipo")),
        () -> assertEquals("2026-11-08", evento.get("realizada_em")),
        () -> assertEquals("48500", evento.get("quilometragem")),
        () -> assertEquals("Troca da correia", evento.get("descricao")),
        () -> assertEquals("Oficina Norte", evento.get("oficina")),
        () -> assertEquals("420.50", evento.get("valor")),
        () -> assertNull(evento.get("proxima_objeto")),
        () -> assertEquals("2", evento.get("versao")),
        () -> assertEquals(gestor.toString(), evento.get("atualizado_por")),
        () -> assertEquals(String.valueOf(evento.get("correlation_id")).toLowerCase(),
            evento.get("correlation_payload")));
  }

  @Test
  void edicaoQueViolaOCadastroResponde422SemAlterarRegistroNemOutbox() {
    var registrada = registrar();
    long eventosAntes = contar("operacoes.outbox_evento");

    var falha = assertThrows(FalhaManutencao.class,
        () -> controller.atualizar(jwt("ADMINISTRADOR"), registrada.id(),
            new GerenciarManutencoes.Edicao(TipoManutencao.REVISAO,
                LocalDate.parse("2026-11-08"), 49_000L, "Revisão", "Oficina",
                new BigDecimal("100.00"), LocalDate.parse("2026-11-08"))));

    assertAll(
        () -> assertEquals(422, controller.falha(falha).getStatusCode().value()),
        () -> assertEquals(registrada,
            controller.buscar(jwt("GESTOR_FROTA"), registrada.id()).getBody()),
        () -> assertEquals(eventosAntes, contar("operacoes.outbox_evento")));
  }

  @Test
  void edicaoComDescricaoAcimaDoLimiteResponde422() {
    var registrada = registrar();

    var falha = assertThrows(FalhaManutencao.class,
        () -> controller.atualizar(jwt("ADMINISTRADOR"), registrada.id(),
            new GerenciarManutencoes.Edicao(TipoManutencao.REVISAO,
                LocalDate.parse("2026-11-08"), 49_000L, "x".repeat(501), "Oficina",
                new BigDecimal("100.00"), null)));

    assertEquals(422, controller.falha(falha).getStatusCode().value());
  }

  @Test
  void edicaoDeIdInexistenteResponde404SemGravarEvento() {
    var falha = assertThrows(FalhaManutencao.class,
        () -> controller.atualizar(jwt("GESTOR_FROTA"), UUID.randomUUID(), edicaoValida()));

    assertAll(
        () -> assertEquals(404, controller.falha(falha).getStatusCode().value()),
        () -> assertEquals(0L, contar("operacoes.outbox_evento")));
  }

  private static GerenciarManutencoes.Edicao edicaoValida() {
    return new GerenciarManutencoes.Edicao(TipoManutencao.REVISAO, LocalDate.parse("2026-11-08"),
        49_000L, "Revisão", "Oficina", new BigDecimal("100.00"), null);
  }

  private long contar(String tabela) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabela, Long.class);
  }

  private Manutencoes.Dados registrar() {
    return controller.registrar(jwt("GESTOR_FROTA"), "Bearer t",
        new GerenciarManutencoes.Entrada(VEICULO, TipoManutencao.TROCA_OLEO,
            LocalDate.parse("2026-11-07"), 48_000L, "Troca de óleo e filtro", "Oficina Central",
            new BigDecimal("350.00"), LocalDate.parse("2027-05-07"))).getBody();
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

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

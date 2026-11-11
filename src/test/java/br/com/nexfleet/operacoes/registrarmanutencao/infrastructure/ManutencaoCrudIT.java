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

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

/** O evento de registro passa a levar a versão do registro (1). */
class AbastecimentoRegistradoVersaoIT extends SqlServerIntegracao {
  private static final Instant AGORA = Instant.parse("2026-11-10T12:00:00Z");

  @Autowired JdbcTemplate jdbc;
  @Autowired Abastecimentos repositorio;

  @AfterEach
  void limparBanco() {
    jdbc.update("DELETE FROM operacoes.outbox_evento");
    jdbc.update("DELETE FROM operacoes.abastecimento");
    jdbc.update("DELETE FROM operacoes.hodometro");
  }

  @Test
  void registroPublicaAbastecimentoRegistradoComVersaoUm() {
    ReferenciasFrota referenciasValidas = (token, veiculo, motorista, realizadoEm) -> true;
    var controller = new AbastecimentosController(new GerenciarAbastecimentos(
        repositorio, referenciasValidas, Clock.fixed(AGORA, ZoneOffset.UTC)));
    var jwt = Jwt.withTokenValue("token-integracao").header("alg", "none")
        .subject(UUID.randomUUID().toString()).claim("perfil", "GESTOR_FROTA").build();

    var registrado = controller.registrar(jwt, "Bearer t", new GerenciarAbastecimentos.Entrada(
        UUID.randomUUID(), UUID.randomUUID(), AGORA.minusSeconds(3600), "Diesel S10",
        new BigDecimal("42.50"), new BigDecimal("289.90"), 1_500L)).getBody();

    assertEquals("1", jdbc.queryForObject("SELECT JSON_VALUE(payload,'$.versao')"
        + " FROM operacoes.outbox_evento WHERE evento_tipo='AbastecimentoRegistrado'"
        + " AND agregado_id=?", String.class, registrado.id()));
  }
}

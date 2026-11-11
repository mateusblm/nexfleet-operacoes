package br.com.nexfleet.operacoes.registrarabastecimento.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import br.com.nexfleet.operacoes.SqlServerIntegracao;
import br.com.nexfleet.operacoes.registrarabastecimento.api.AbastecimentosController;
import br.com.nexfleet.operacoes.registrarabastecimento.application.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;

class Cq05HistoricoVeiculoIT extends SqlServerIntegracao {
  private static final Instant AGORA = Instant.parse("2026-11-08T12:00:00Z");
  private static final Instant REALIZADO_EM = Instant.parse("2026-11-08T10:30:00Z");

  @Autowired Abastecimentos repositorio;

  @Test
  void cq05HistoricoDoVeiculoMantemDataEResponsavelDoAbastecimento() {
    UUID veiculoId = UUID.randomUUID();
    UUID motoristaId = UUID.randomUUID();
    ReferenciasFrota referenciasValidas = (token, veiculo, motorista, data) -> true;
    var controller = new AbastecimentosController(new GerenciarAbastecimentos(
        repositorio, referenciasValidas, Clock.fixed(AGORA, ZoneOffset.UTC)));

    var criado = controller.registrar(jwt(), "Bearer teste-cq05",
        new GerenciarAbastecimentos.Entrada(veiculoId, motoristaId, REALIZADO_EM,
            "Diesel S10", new BigDecimal("35.50"), new BigDecimal("245.90"), 1_250L));
    var resposta = controller.listar(jwt(), veiculoId, 0, 20);

    var historico = resposta.getBody();
    assertNotNull(historico);
    assertEquals(1, historico.size());
    var operacao = historico.get(0);
    assertAll(
        () -> assertEquals(201, criado.getStatusCode().value()),
        () -> assertEquals(200, resposta.getStatusCode().value()),
        () -> assertEquals(veiculoId, operacao.veiculoId()),
        () -> assertEquals(REALIZADO_EM, operacao.realizadoEm()),
        () -> assertEquals(motoristaId, operacao.motoristaId()),
        () -> assertEquals(new BigDecimal("35.50"), operacao.litros()),
        () -> assertEquals(1_250L, operacao.quilometragem()),
        () -> assertEquals(AGORA, operacao.criadoEm()));
  }

  private Jwt jwt() {
    return Jwt.withTokenValue("token-integracao-cq05")
        .header("alg", "none")
        .subject(UUID.randomUUID().toString())
        .claim("perfil", "GESTOR_FROTA")
        .build();
  }
}

package br.com.nexfleet.operacoes.registrarabastecimento.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AbastecimentoTest {
  private static final Instant AGORA = Instant.parse("2026-11-10T12:00:00Z");
  private static final UUID VEICULO = UUID.randomUUID();
  private static final UUID MOTORISTA = UUID.randomUUID();

  @Test
  void cadastroNormalizaCombustivel() {
    var medicao = Abastecimento.validarCadastro(VEICULO, MOTORISTA, AGORA, " diesel ",
        new BigDecimal("40.00"), new BigDecimal("250.10"), 1_000L, AGORA);
    assertEquals("DIESEL", medicao.combustivel());
    assertEquals(1_000L, medicao.quilometragem());
  }

  @Test
  void cadastroRecusaIncompletoAntesDaDataEDataAntesDaMedicao() {
    assertEquals(MedicaoAbastecimento.PREENCHA, assertThrows(AbastecimentoInvalido.class,
        () -> Abastecimento.validarCadastro(VEICULO, null, AGORA.plusSeconds(1), "X",
            BigDecimal.ZERO, BigDecimal.ONE, 1L, AGORA)).getMessage());
    assertEquals("A data do abastecimento não pode estar no futuro.",
        assertThrows(AbastecimentoInvalido.class,
            () -> Abastecimento.validarCadastro(VEICULO, MOTORISTA, AGORA.plusSeconds(1), "X",
                BigDecimal.ZERO, BigDecimal.ONE, 1L, AGORA)).getMessage());
  }

  @Test
  void medicaoRecusaCombustivelLongoDecimaisEQuilometragemNaoPositiva() {
    assertEquals(MedicaoAbastecimento.PREENCHA, assertThrows(AbastecimentoInvalido.class,
        () -> MedicaoAbastecimento.de("x".repeat(41), BigDecimal.ONE, BigDecimal.ONE, 1L))
        .getMessage());
    assertEquals("Litros deve ser positivo e possuir no máximo duas casas decimais.",
        assertThrows(AbastecimentoInvalido.class,
            () -> MedicaoAbastecimento.de("X", new BigDecimal("1.001"), BigDecimal.ONE, 1L))
            .getMessage());
    assertEquals("A quilometragem deve ser positiva.", assertThrows(AbastecimentoInvalido.class,
        () -> MedicaoAbastecimento.de("X", BigDecimal.ONE, BigDecimal.ONE, 0L)).getMessage());
  }

  @Test
  void quilometragemNaoRegrideDoHodometro() {
    assertDoesNotThrow(() -> Abastecimento.validarContraHodometro(null, 0L));
    assertDoesNotThrow(() -> Abastecimento.validarContraHodometro(1_000L, 1_000L));
    assertTrue(assertThrows(AbastecimentoInvalido.class,
        () -> Abastecimento.validarContraHodometro(1_000L, 999L)).getMessage()
        .contains("1000 km"));
  }

  @Test
  void faixaEntreVizinhosEhAberta() {
    var faixa = new FaixaQuilometragem(1_000L, 3_000L);
    assertDoesNotThrow(() -> faixa.validar(1_001L));
    assertDoesNotThrow(() -> faixa.validar(2_999L));
    assertThrows(AbastecimentoInvalido.class, () -> faixa.validar(1_000L));
    assertThrows(AbastecimentoInvalido.class, () -> faixa.validar(3_000L));
    assertDoesNotThrow(() -> new FaixaQuilometragem(null, null).validar(1L));
  }

  @Test
  void versaoSeguinteIncrementaUm() {
    assertEquals(2, Abastecimento.versaoSeguinte(Abastecimento.VERSAO_INICIAL));
  }
}

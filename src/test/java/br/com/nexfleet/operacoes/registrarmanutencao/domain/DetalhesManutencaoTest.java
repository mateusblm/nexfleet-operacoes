package br.com.nexfleet.operacoes.registrarmanutencao.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DetalhesManutencaoTest {
  private static final LocalDate DIA = LocalDate.parse("2026-11-01");
  private static final TipoManutencao TIPO = TipoManutencao.OUTROS;

  @Test
  void normalizaDescricaoEOficina() {
    var detalhes = DetalhesManutencao.de(TipoManutencao.REVISAO, DIA, 10L, "  Revisão ",
        " Oficina ", new BigDecimal("10.50"), DIA.plusDays(1));
    assertEquals("Revisão", detalhes.descricao());
    assertEquals("Oficina", detalhes.oficina());
  }

  @Test
  void recusaDadosIncompletos() {
    assertEquals(DetalhesManutencao.PREENCHA, falha(null, DIA, 10L, "d", BigDecimal.ONE, null));
    assertEquals(DetalhesManutencao.PREENCHA, falha(TIPO, null, 10L, "d", BigDecimal.ONE, null));
    assertEquals(DetalhesManutencao.PREENCHA, falha(TIPO, DIA, null, "d", BigDecimal.ONE, null));
    assertEquals(DetalhesManutencao.PREENCHA, falha(TIPO, DIA, 10L, " ", BigDecimal.ONE, null));
    assertEquals(DetalhesManutencao.PREENCHA, falha(TIPO, DIA, 10L, "d", null, null));
  }

  @Test
  void recusaQuilometragemNegativaLimitesValorEProximaNaoPosterior() {
    assertEquals("A quilometragem não pode ser negativa.",
        falha(TIPO, DIA, -1L, "d", BigDecimal.ONE, null));
    assertEquals("Descrição ou oficina excede o limite permitido.",
        falha(TIPO, DIA, 0L, "x".repeat(501), BigDecimal.ONE, null));
    assertTrue(falha(TIPO, DIA, 0L, "d", new BigDecimal("1.001"), null)
        .startsWith("O valor não pode ser negativo"));
    assertTrue(falha(TIPO, DIA, 0L, "d", BigDecimal.ONE, DIA)
        .startsWith("A próxima manutenção deve ser posterior"));
  }

  @Test
  void cadastroExigeVeiculoEVersaoSeguinteIncrementaUm() {
    assertEquals(DetalhesManutencao.PREENCHA,
        assertThrows(ManutencaoInvalida.class, () -> Manutencao.exigirVeiculo(null))
            .getMessage());
    assertEquals(2, Manutencao.versaoSeguinte(Manutencao.VERSAO_INICIAL));
  }

  private static String falha(TipoManutencao tipo, LocalDate realizadaEm, Long km,
      String descricao, BigDecimal valor, LocalDate proxima) {
    return assertThrows(ManutencaoInvalida.class,
        () -> DetalhesManutencao.de(tipo, realizadaEm, km, descricao, "Oficina", valor, proxima))
        .getMessage();
  }
}

package br.com.nexfleet.operacoes.registrarabastecimento;

import static br.com.nexfleet.operacoes.registrarabastecimento.application.FalhaAbastecimento.Tipo.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import br.com.nexfleet.operacoes.registrarabastecimento.application.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class GerenciarAbastecimentosTest {
  final Abastecimentos repositorio = mock(Abastecimentos.class);
  final ReferenciasFrota frota = mock(ReferenciasFrota.class);
  final Clock relogio = Clock.fixed(Instant.parse("2026-11-05T15:00:00Z"), ZoneOffset.UTC);
  final GerenciarAbastecimentos caso = new GerenciarAbastecimentos(repositorio, frota, relogio);
  final UUID veiculo = UUID.fromString("b176f1b1-bc54-4f22-9b2e-fb69469ab033");
  final UUID motorista = UUID.fromString("3ce0bdb7-f1de-47c2-baa3-7b99e343650c");
  final UUID autor = UUID.fromString("e2f6b588-00cf-45bc-927e-c4a08647dfde");

  @Test
  void registraQuandoDadosEVinculoSaoValidos() {
    var entrada = entrada(12000L, "42.50", "250.90");
    when(frota.operacaoPermitida("Bearer token", veiculo, motorista, entrada.realizadoEm()))
        .thenReturn(true);
    when(repositorio.registrar(any(), eq(veiculo), eq(motorista), eq(entrada.realizadoEm()),
        eq("DIESEL"), eq(new BigDecimal("42.50")), eq(new BigDecimal("250.90")),
        eq(12000L), eq(autor), eq(Instant.now(relogio))))
        .thenReturn(dados(12000L));

    assertEquals(12000L,
        caso.registrar("GESTOR_FROTA", autor, "Bearer token", entrada).quilometragem());
  }

  @Test
  void recusaVinculoInvalidoAntesDePersistir() {
    var entrada = entrada(12000L, "42.50", "250.90");
    assertEquals(INVALIDO, assertThrows(FalhaAbastecimento.class,
        () -> caso.registrar("ADMINISTRADOR", autor, "Bearer token", entrada)).tipo());
    verifyNoInteractions(repositorio);
  }

  @Test
  void recusaValoresComMaisDeDuasCasasEDataFutura() {
    assertEquals(INVALIDO, assertThrows(FalhaAbastecimento.class,
        () -> caso.registrar("ADMINISTRADOR", autor, "Bearer token",
            entrada(12000L, "42.555", "250.90"))).tipo());
    var futura = new GerenciarAbastecimentos.Entrada(veiculo, motorista,
        Instant.parse("2026-11-05T15:00:01Z"), "DIESEL",
        new BigDecimal("42.50"), new BigDecimal("250.90"), 12000L);
    assertEquals(INVALIDO, assertThrows(FalhaAbastecimento.class,
        () -> caso.registrar("ADMINISTRADOR", autor, "Bearer token", futura)).tipo());
    verifyNoInteractions(frota, repositorio);
  }

  @Test
  void recusaPerfilSemPermissao() {
    assertEquals(PROIBIDO, assertThrows(FalhaAbastecimento.class,
        () -> caso.registrar("MOTORISTA", autor, "Bearer token",
            entrada(12000L, "42.50", "250.90"))).tipo());
  }

  private GerenciarAbastecimentos.Entrada entrada(long km, String litros, String valor) {
    return new GerenciarAbastecimentos.Entrada(veiculo, motorista,
        Instant.parse("2026-11-05T14:00:00Z"), "diesel",
        new BigDecimal(litros), new BigDecimal(valor), km);
  }

  private Abastecimentos.Dados dados(long km) {
    return new Abastecimentos.Dados(UUID.randomUUID(), veiculo, motorista,
        Instant.parse("2026-11-05T14:00:00Z"), "DIESEL",
        new BigDecimal("42.50"), new BigDecimal("250.90"), km, Instant.now(relogio));
  }
}

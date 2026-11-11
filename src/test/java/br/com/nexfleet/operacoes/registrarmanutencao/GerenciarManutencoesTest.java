package br.com.nexfleet.operacoes.registrarmanutencao;

import static br.com.nexfleet.operacoes.registrarmanutencao.application.FalhaManutencao.Tipo.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import br.com.nexfleet.operacoes.registrarmanutencao.application.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GerenciarManutencoesTest {
  final Manutencoes repositorio = mock(Manutencoes.class);
  final VeiculosFrota frota = mock(VeiculosFrota.class);
  final Clock relogio = Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC);
  final GerenciarManutencoes caso = new GerenciarManutencoes(repositorio, frota, relogio);
  final UUID veiculo = UUID.randomUUID();
  final UUID autor = UUID.randomUUID();

  @Test
  void registraTodosOsDadosQuandoVeiculoEstaAtivo() {
    var entrada = entrada(LocalDate.of(2026, 12, 6));
    when(frota.permiteManutencao("Bearer token", veiculo)).thenReturn(true);
    when(repositorio.registrar(any(), eq(veiculo), eq(TipoManutencao.PREVENTIVA),
        eq(LocalDate.of(2026, 11, 6)), eq(15000L), eq("Revisão de rotina"),
        eq("Oficina Central"), eq(new BigDecimal("450.00")),
        eq(LocalDate.of(2026, 12, 6)), eq(autor), eq(Instant.now(relogio))))
        .thenReturn(dados());

    assertEquals(veiculo,
        caso.registrar("GESTOR_FROTA", autor, "Bearer token", entrada).veiculoId());
  }

  @Test
  void recusaVeiculoInativoOuInexistenteAntesDePersistir() {
    assertEquals(INVALIDO, assertThrows(FalhaManutencao.class,
        () -> caso.registrar("ADMINISTRADOR", autor, "Bearer token",
            entrada(LocalDate.of(2026, 12, 6)))).tipo());
    verifyNoInteractions(repositorio);
  }

  @Test
  void recusaProximaManutencaoNaMesmaDataOuAntes() {
    assertEquals(INVALIDO, assertThrows(FalhaManutencao.class,
        () -> caso.registrar("ADMINISTRADOR", autor, "Bearer token",
            entrada(LocalDate.of(2026, 11, 6)))).tipo());
    verifyNoInteractions(frota, repositorio);
  }

  @Test
  void permiteValorZeroEProximaDataAusenteSemInventarRegra() {
    var entrada = new GerenciarManutencoes.Entrada(veiculo, TipoManutencao.CORRETIVA,
        LocalDate.of(2026, 11, 6), 0L, "Garantia", "Concessionária",
        BigDecimal.ZERO, null);
    when(frota.permiteManutencao("Bearer token", veiculo)).thenReturn(true);
    when(repositorio.registrar(any(), any(), any(), any(), anyLong(), any(), any(), any(),
        isNull(), any(), any())).thenReturn(dados());
    assertNotNull(caso.registrar("ADMINISTRADOR", autor, "Bearer token", entrada));
  }

  private GerenciarManutencoes.Entrada entrada(LocalDate proxima) {
    return new GerenciarManutencoes.Entrada(veiculo, TipoManutencao.PREVENTIVA,
        LocalDate.of(2026, 11, 6), 15000L, " Revisão de rotina ", " Oficina Central ",
        new BigDecimal("450.00"), proxima);
  }

  private Manutencoes.Dados dados() {
    return new Manutencoes.Dados(UUID.randomUUID(), veiculo, TipoManutencao.PREVENTIVA,
        LocalDate.of(2026, 11, 6), 15000L, "Revisão de rotina", "Oficina Central",
        new BigDecimal("450.00"), LocalDate.of(2026, 12, 6), Instant.now(relogio));
  }
}

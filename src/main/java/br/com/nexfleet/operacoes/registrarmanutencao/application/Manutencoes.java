package br.com.nexfleet.operacoes.registrarmanutencao.application;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public interface Manutencoes {
  record Dados(UUID id, UUID veiculoId, TipoManutencao tipo, LocalDate realizadaEm,
      long quilometragem, String descricao, String oficina, BigDecimal valor,
      LocalDate proximaManutencao, Instant criadoEm, int versao) {
    public Dados(UUID id, UUID veiculoId, TipoManutencao tipo, LocalDate realizadaEm,
        long quilometragem, String descricao, String oficina, BigDecimal valor,
        LocalDate proximaManutencao, Instant criadoEm) {
      this(id, veiculoId, tipo, realizadaEm, quilometragem, descricao, oficina, valor,
          proximaManutencao, criadoEm, 1);
    }
  }

  Dados registrar(UUID id, UUID veiculoId, TipoManutencao tipo, LocalDate realizadaEm,
      long quilometragem, String descricao, String oficina, BigDecimal valor,
      LocalDate proximaManutencao, UUID autor, Instant criadoEm);

  List<Dados> listar(UUID veiculoId, int offset, int limite);

  Optional<Dados> buscar(UUID id);

  /** Grava a edição, incrementa a versão e registra o evento; vazio se o id não existe. */
  Optional<Dados> atualizar(UUID id, TipoManutencao tipo, LocalDate realizadaEm,
      long quilometragem, String descricao, String oficina, BigDecimal valor,
      LocalDate proximaManutencao, UUID autor, Instant atualizadoEm);

  /** Apaga a manutenção e registra o evento de exclusão; false se o id não existe. */
  boolean excluir(UUID id, UUID autor, Instant excluidoEm);
}

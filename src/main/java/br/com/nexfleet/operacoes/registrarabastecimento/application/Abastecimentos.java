package br.com.nexfleet.operacoes.registrarabastecimento.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public interface Abastecimentos {
  record Dados(UUID id, UUID veiculoId, UUID motoristaId, Instant realizadoEm,
      String combustivel, BigDecimal litros, BigDecimal valorTotal,
      long quilometragem, Instant criadoEm, int versao) {
    public Dados(UUID id, UUID veiculoId, UUID motoristaId, Instant realizadoEm,
        String combustivel, BigDecimal litros, BigDecimal valorTotal,
        long quilometragem, Instant criadoEm) {
      this(id, veiculoId, motoristaId, realizadoEm, combustivel, litros, valorTotal,
          quilometragem, criadoEm, 1);
    }
  }

  Dados registrar(UUID id, UUID veiculoId, UUID motoristaId, Instant realizadoEm,
      String combustivel, BigDecimal litros, BigDecimal valorTotal,
      long quilometragem, UUID autor, Instant criadoEm);

  List<Dados> listar(UUID veiculoId, int offset, int limite);

  Optional<Dados> buscar(UUID id);

  /**
   * Grava a edição, incrementa a versão, recalcula o hodômetro e registra o evento; vazio se o
   * id não existe. Recusa com {@link FalhaAbastecimento} quilometragem fora do intervalo entre
   * o abastecimento anterior e o seguinte do mesmo veículo.
   */
  Optional<Dados> atualizar(UUID id, String combustivel, BigDecimal litros,
      BigDecimal valorTotal, long quilometragem, UUID autor, Instant atualizadoEm);

  /**
   * Apaga o abastecimento, recalcula o hodômetro pelo maior km restante e registra o evento de
   * exclusão; false se o id não existe.
   */
  boolean excluir(UUID id, UUID autor, Instant excluidoEm);
}

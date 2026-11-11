package br.com.nexfleet.operacoes.registrarmanutencao.application;

import java.util.UUID;

public interface VeiculosFrota {
  boolean permiteManutencao(String token, UUID veiculoId);
}

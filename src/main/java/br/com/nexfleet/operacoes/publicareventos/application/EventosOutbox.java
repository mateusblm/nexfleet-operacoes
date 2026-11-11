package br.com.nexfleet.operacoes.publicareventos.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EventosOutbox {
  List<EventoPendente> bloquearPendentes(int limite, Instant agora);

  void marcarPublicado(UUID id, Instant publicadoEm);

  void registrarFalha(UUID id, String erro, Instant proximaTentativaEm);
}

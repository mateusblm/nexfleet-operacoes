package br.com.nexfleet.operacoes.publicareventos.infrastructure;

import br.com.nexfleet.operacoes.publicareventos.application.PublicarEventosPendentes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxPublicacaoJob {
  private final PublicarEventosPendentes casoDeUso;
  private final int lote;

  public OutboxPublicacaoJob(
      PublicarEventosPendentes casoDeUso,
      @Value("${outbox.publicacao.lote}") int lote) {
    this.casoDeUso = casoDeUso;
    this.lote = lote;
  }

  @Scheduled(
      fixedDelayString = "${outbox.publicacao.intervalo-ms}",
      initialDelayString = "${outbox.publicacao.atraso-inicial-ms}")
  @Transactional
  public void publicar() {
    casoDeUso.executar(lote);
  }
}

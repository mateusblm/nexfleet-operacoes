package br.com.nexfleet.operacoes.publicareventos.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public class PublicarEventosPendentes {
  public record Resultado(int selecionados, int publicados, int falhas) {}

  private final EventosOutbox outbox;
  private final PublicadorEventos publicador;
  private final Clock relogio;
  private final Duration intervaloRetry;

  public PublicarEventosPendentes(
      EventosOutbox outbox,
      PublicadorEventos publicador,
      Clock relogio,
      Duration intervaloRetry) {
    this.outbox = outbox;
    this.publicador = publicador;
    this.relogio = relogio;
    this.intervaloRetry = intervaloRetry;
  }

  public Resultado executar(int limite) {
    if (limite < 1 || limite > 100) {
      throw new IllegalArgumentException("O lote deve conter de 1 a 100 eventos.");
    }
    Instant agora = Instant.now(relogio);
    var pendentes = outbox.bloquearPendentes(limite, agora);
    int publicados = 0;
    int falhas = 0;
    for (var evento : pendentes) {
      try {
        publicador.publicar(evento);
        outbox.marcarPublicado(evento.id(), agora);
        publicados++;
      } catch (RuntimeException falha) {
        outbox.registrarFalha(
            evento.id(), resumir(falha), agora.plus(intervaloRetry));
        falhas++;
      }
    }
    return new Resultado(pendentes.size(), publicados, falhas);
  }

  private String resumir(RuntimeException falha) {
    String mensagem = falha.getMessage();
    if (mensagem == null || mensagem.isBlank()) {
      mensagem = falha.getClass().getSimpleName();
    }
    return mensagem.substring(0, Math.min(mensagem.length(), 500));
  }
}

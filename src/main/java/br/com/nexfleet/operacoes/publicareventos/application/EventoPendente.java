package br.com.nexfleet.operacoes.publicareventos.application;

import java.time.Instant;
import java.util.UUID;

public record EventoPendente(
    UUID id,
    String agregadoTipo,
    UUID agregadoId,
    String tipo,
    int versao,
    UUID correlationId,
    String payload,
    Instant ocorridoEm,
    int tentativas) {}

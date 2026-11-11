package br.com.nexfleet.operacoes.registrarmanutencao.domain;

/** Violação de uma regra de negócio da manutenção; a mensagem é a que o usuário vê. */
public class ManutencaoInvalida extends RuntimeException {
  public ManutencaoInvalida(String mensagem) {
    super(mensagem);
  }
}

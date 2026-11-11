package br.com.nexfleet.operacoes.registrarabastecimento.domain;

/** Violação de uma regra de negócio do abastecimento; a mensagem é a que o usuário vê. */
public class AbastecimentoInvalido extends RuntimeException {
  public AbastecimentoInvalido(String mensagem) {
    super(mensagem);
  }
}

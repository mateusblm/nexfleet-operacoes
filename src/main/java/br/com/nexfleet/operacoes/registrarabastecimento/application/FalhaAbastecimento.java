package br.com.nexfleet.operacoes.registrarabastecimento.application;

public class FalhaAbastecimento extends RuntimeException {
  public enum Tipo { PROIBIDO, INVALIDO, NAO_ENCONTRADO, CONFLITO, INDISPONIVEL }

  private final Tipo tipo;

  public FalhaAbastecimento(Tipo tipo, String mensagem) {
    super(mensagem);
    this.tipo = tipo;
  }

  public Tipo tipo() { return tipo; }
}

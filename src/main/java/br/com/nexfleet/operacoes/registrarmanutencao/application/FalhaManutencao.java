package br.com.nexfleet.operacoes.registrarmanutencao.application;

public class FalhaManutencao extends RuntimeException {
  public enum Tipo { PROIBIDO, INVALIDO, NAO_ENCONTRADO, INDISPONIVEL }
  private final Tipo tipo;

  public FalhaManutencao(Tipo tipo, String mensagem) {
    super(mensagem);
    this.tipo = tipo;
  }

  public Tipo tipo() { return tipo; }
}

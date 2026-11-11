package br.com.nexfleet.operacoes.registrarmanutencao.application;

import static br.com.nexfleet.operacoes.registrarmanutencao.application.FalhaManutencao.Tipo.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class GerenciarManutencoes {
  public record Entrada(UUID veiculoId, TipoManutencao tipo, LocalDate realizadaEm,
      Long quilometragem, String descricao, String oficina, BigDecimal valor,
      LocalDate proximaManutencao) {}

  /** Corpo da edição: o veículo da manutenção não muda. */
  public record Edicao(TipoManutencao tipo, LocalDate realizadaEm, Long quilometragem,
      String descricao, String oficina, BigDecimal valor, LocalDate proximaManutencao) {}

  private final Manutencoes manutencoes;
  private final VeiculosFrota frota;
  private final Clock relogio;

  public GerenciarManutencoes(Manutencoes manutencoes, VeiculosFrota frota, Clock relogio) {
    this.manutencoes = manutencoes;
    this.frota = frota;
    this.relogio = relogio;
  }

  public Manutencoes.Dados registrar(String perfil, UUID autor, String token, Entrada entrada) {
    autorizar(perfil);
    if (autor == null) throw new FalhaManutencao(PROIBIDO, "Identidade inválida.");
    validar(entrada);
    if (!frota.permiteManutencao(token, entrada.veiculoId()))
      throw new FalhaManutencao(INVALIDO,
          "O veículo precisa existir e não pode estar inativo.");
    return manutencoes.registrar(UUID.randomUUID(), entrada.veiculoId(), entrada.tipo(),
        entrada.realizadaEm(), entrada.quilometragem(), entrada.descricao().trim(),
        entrada.oficina().trim(), entrada.valor(), entrada.proximaManutencao(), autor,
        Instant.now(relogio));
  }

  public List<Manutencoes.Dados> listar(String perfil, UUID veiculoId, int pagina, int tamanho) {
    autorizar(perfil);
    if (pagina < 0 || pagina > 100000 || tamanho < 1 || tamanho > 100)
      throw new FalhaManutencao(INVALIDO,
          "Página deve ser de 0 a 100000 e tamanho de 1 a 100.");
    return manutencoes.listar(veiculoId, pagina * tamanho, tamanho);
  }

  public Manutencoes.Dados buscar(String perfil, UUID id) {
    autorizar(perfil);
    return manutencoes.buscar(id).orElseThrow(GerenciarManutencoes::naoEncontrada);
  }

  public Manutencoes.Dados atualizar(String perfil, UUID autor, UUID id, Edicao edicao) {
    autorizar(perfil);
    if (autor == null) throw new FalhaManutencao(PROIBIDO, "Identidade inválida.");
    validar(edicao);
    return manutencoes.atualizar(id, edicao.tipo(), edicao.realizadaEm(), edicao.quilometragem(),
        edicao.descricao().trim(), edicao.oficina().trim(), edicao.valor(),
        edicao.proximaManutencao(), autor, Instant.now(relogio))
        .orElseThrow(GerenciarManutencoes::naoEncontrada);
  }

  public void excluir(String perfil, UUID autor, UUID id) {
    autorizar(perfil);
    if (autor == null) throw new FalhaManutencao(PROIBIDO, "Identidade inválida.");
    if (!manutencoes.excluir(id, autor, Instant.now(relogio))) throw naoEncontrada();
  }

  private static FalhaManutencao naoEncontrada() {
    return new FalhaManutencao(NAO_ENCONTRADO, "Manutenção não encontrada.");
  }

  private void validar(Entrada entrada) {
    if (entrada == null || entrada.veiculoId() == null)
      throw new FalhaManutencao(INVALIDO, "Preencha todos os dados da manutenção.");
    validar(new Edicao(entrada.tipo(), entrada.realizadaEm(), entrada.quilometragem(),
        entrada.descricao(), entrada.oficina(), entrada.valor(), entrada.proximaManutencao()));
  }

  private void validar(Edicao entrada) {
    if (entrada == null || entrada.tipo() == null
        || entrada.realizadaEm() == null || entrada.quilometragem() == null
        || entrada.descricao() == null || entrada.descricao().isBlank()
        || entrada.oficina() == null || entrada.oficina().isBlank()
        || entrada.valor() == null)
      throw new FalhaManutencao(INVALIDO, "Preencha todos os dados da manutenção.");
    if (entrada.quilometragem() < 0)
      throw new FalhaManutencao(INVALIDO, "A quilometragem não pode ser negativa.");
    if (entrada.descricao().trim().length() > 500 || entrada.oficina().trim().length() > 150)
      throw new FalhaManutencao(INVALIDO, "Descrição ou oficina excede o limite permitido.");
    if (entrada.valor().signum() < 0 || entrada.valor().scale() > 2
        || entrada.valor().precision() - entrada.valor().scale() > 10)
      throw new FalhaManutencao(INVALIDO,
          "O valor não pode ser negativo e deve ter no máximo duas casas decimais.");
    if (entrada.proximaManutencao() != null
        && !entrada.proximaManutencao().isAfter(entrada.realizadaEm()))
      throw new FalhaManutencao(INVALIDO,
          "A próxima manutenção deve ser posterior à manutenção registrada.");
  }

  private void autorizar(String perfil) {
    if (!("ADMINISTRADOR".equals(perfil) || "GESTOR_FROTA".equals(perfil)))
      throw new FalhaManutencao(PROIBIDO, "Acesso exclusivo da equipe de Frota.");
  }
}

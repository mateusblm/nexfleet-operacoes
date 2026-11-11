package br.com.nexfleet.operacoes.registrarmanutencao.application;

import static br.com.nexfleet.operacoes.registrarmanutencao.application.FalhaManutencao.Tipo.*;

import br.com.nexfleet.operacoes.registrarmanutencao.domain.*;
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
    var detalhes = validar(entrada);
    if (!frota.permiteManutencao(token, entrada.veiculoId()))
      throw new FalhaManutencao(INVALIDO,
          "O veículo precisa existir e não pode estar inativo.");
    return manutencoes.registrar(UUID.randomUUID(), entrada.veiculoId(), detalhes.tipo(),
        detalhes.realizadaEm(), detalhes.quilometragem(), detalhes.descricao(),
        detalhes.oficina(), detalhes.valor(), detalhes.proximaManutencao(), autor,
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
    var detalhes = validar(edicao);
    return manutencoes.atualizar(id, detalhes.tipo(), detalhes.realizadaEm(),
        detalhes.quilometragem(), detalhes.descricao(), detalhes.oficina(), detalhes.valor(),
        detalhes.proximaManutencao(), autor, Instant.now(relogio))
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

  private DetalhesManutencao validar(Entrada entrada) {
    if (entrada == null)
      throw new FalhaManutencao(INVALIDO, DetalhesManutencao.PREENCHA);
    try {
      Manutencao.exigirVeiculo(entrada.veiculoId());
    } catch (ManutencaoInvalida e) {
      throw new FalhaManutencao(INVALIDO, e.getMessage());
    }
    return validar(new Edicao(entrada.tipo(), entrada.realizadaEm(), entrada.quilometragem(),
        entrada.descricao(), entrada.oficina(), entrada.valor(), entrada.proximaManutencao()));
  }

  private DetalhesManutencao validar(Edicao edicao) {
    if (edicao == null)
      throw new FalhaManutencao(INVALIDO, DetalhesManutencao.PREENCHA);
    try {
      return DetalhesManutencao.de(edicao.tipo(), edicao.realizadaEm(), edicao.quilometragem(),
          edicao.descricao(), edicao.oficina(), edicao.valor(), edicao.proximaManutencao());
    } catch (ManutencaoInvalida e) {
      throw new FalhaManutencao(INVALIDO, e.getMessage());
    }
  }

  private void autorizar(String perfil) {
    if (!("ADMINISTRADOR".equals(perfil) || "GESTOR_FROTA".equals(perfil)))
      throw new FalhaManutencao(PROIBIDO, "Acesso exclusivo da equipe de Frota.");
  }
}

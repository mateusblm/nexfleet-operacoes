package br.com.nexfleet.operacoes.registrarabastecimento.application;

import static br.com.nexfleet.operacoes.registrarabastecimento.application.FalhaAbastecimento.Tipo.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class GerenciarAbastecimentos {
  public record Entrada(UUID veiculoId, UUID motoristaId, Instant realizadoEm,
      String combustivel, BigDecimal litros, BigDecimal valorTotal, Long quilometragem) {}

  /** Corpo da edição: data, veículo e motorista do abastecimento não mudam. */
  public record Edicao(String combustivel, BigDecimal litros, BigDecimal valorTotal,
      Long quilometragem) {}

  private final Abastecimentos abastecimentos;
  private final ReferenciasFrota frota;
  private final Clock relogio;

  public GerenciarAbastecimentos(Abastecimentos abastecimentos, ReferenciasFrota frota, Clock relogio) {
    this.abastecimentos = abastecimentos;
    this.frota = frota;
    this.relogio = relogio;
  }

  public Abastecimentos.Dados registrar(String perfil, UUID autor, String token, Entrada entrada) {
    autorizar(perfil);
    if (autor == null) throw new FalhaAbastecimento(PROIBIDO, "Identidade inválida.");
    validar(entrada);
    if (!frota.operacaoPermitida(token, entrada.veiculoId(), entrada.motoristaId(), entrada.realizadoEm()))
      throw new FalhaAbastecimento(INVALIDO,
          "Veículo e motorista precisam ter vínculo válido na data informada.");
    return abastecimentos.registrar(UUID.randomUUID(), entrada.veiculoId(), entrada.motoristaId(),
        entrada.realizadoEm(), entrada.combustivel().trim().toUpperCase(Locale.ROOT),
        entrada.litros(), entrada.valorTotal(), entrada.quilometragem(), autor,
        Instant.now(relogio));
  }

  public List<Abastecimentos.Dados> listar(String perfil, UUID veiculoId, int pagina, int tamanho) {
    autorizar(perfil);
    if (pagina < 0 || pagina > 100000 || tamanho < 1 || tamanho > 100)
      throw new FalhaAbastecimento(INVALIDO,
          "Página deve ser de 0 a 100000 e tamanho de 1 a 100.");
    return abastecimentos.listar(veiculoId, pagina * tamanho, tamanho);
  }

  public Abastecimentos.Dados buscar(String perfil, UUID id) {
    autorizar(perfil);
    return abastecimentos.buscar(id).orElseThrow(GerenciarAbastecimentos::naoEncontrado);
  }

  public Abastecimentos.Dados atualizar(String perfil, UUID autor, UUID id, Edicao edicao) {
    autorizar(perfil);
    if (autor == null) throw new FalhaAbastecimento(PROIBIDO, "Identidade inválida.");
    validar(edicao);
    return abastecimentos.atualizar(id, edicao.combustivel().trim().toUpperCase(Locale.ROOT),
        edicao.litros(), edicao.valorTotal(), edicao.quilometragem(), autor,
        Instant.now(relogio)).orElseThrow(GerenciarAbastecimentos::naoEncontrado);
  }

  private static FalhaAbastecimento naoEncontrado() {
    return new FalhaAbastecimento(NAO_ENCONTRADO, "Abastecimento não encontrado.");
  }

  private void validar(Entrada entrada) {
    if (entrada == null || entrada.veiculoId() == null || entrada.motoristaId() == null
        || entrada.realizadoEm() == null || entrada.combustivel() == null
        || entrada.combustivel().isBlank() || entrada.combustivel().trim().length() > 40
        || entrada.litros() == null || entrada.valorTotal() == null
        || entrada.quilometragem() == null)
      throw new FalhaAbastecimento(INVALIDO, "Preencha todos os dados do abastecimento.");
    if (entrada.realizadoEm().isAfter(Instant.now(relogio)))
      throw new FalhaAbastecimento(INVALIDO, "A data do abastecimento não pode estar no futuro.");
    validarMedicao(entrada.litros(), entrada.valorTotal(), entrada.quilometragem());
  }

  private void validar(Edicao edicao) {
    if (edicao == null || edicao.combustivel() == null || edicao.combustivel().isBlank()
        || edicao.combustivel().trim().length() > 40 || edicao.litros() == null
        || edicao.valorTotal() == null || edicao.quilometragem() == null)
      throw new FalhaAbastecimento(INVALIDO, "Preencha todos os dados do abastecimento.");
    validarMedicao(edicao.litros(), edicao.valorTotal(), edicao.quilometragem());
  }

  private void validarMedicao(BigDecimal litros, BigDecimal valorTotal, long quilometragem) {
    validarDecimal(litros, "Litros");
    validarDecimal(valorTotal, "Valor total");
    if (quilometragem <= 0)
      throw new FalhaAbastecimento(INVALIDO, "A quilometragem deve ser positiva.");
  }

  private void validarDecimal(BigDecimal valor, String campo) {
    if (valor.signum() <= 0 || valor.scale() > 2 || valor.precision() - valor.scale() > 10)
      throw new FalhaAbastecimento(INVALIDO,
          campo + " deve ser positivo e possuir no máximo duas casas decimais.");
  }

  private void autorizar(String perfil) {
    if (!("ADMINISTRADOR".equals(perfil) || "GESTOR_FROTA".equals(perfil)))
      throw new FalhaAbastecimento(PROIBIDO, "Acesso exclusivo da equipe de Frota.");
  }
}

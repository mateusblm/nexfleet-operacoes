package br.com.nexfleet.operacoes.registrarmanutencao.api;

import br.com.nexfleet.operacoes.registrarmanutencao.application.*;
import java.net.URI;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/operacoes/manutencoes")
public class ManutencoesController {
  private final GerenciarManutencoes manutencoes;
  public ManutencoesController(GerenciarManutencoes manutencoes) { this.manutencoes = manutencoes; }

  @GetMapping
  public ResponseEntity<List<Manutencoes.Dados>> listar(@AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) UUID veiculoId,
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "20") int tamanho) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(manutencoes.listar(perfil(jwt), veiculoId, pagina, tamanho));
  }

  @GetMapping("/{id}")
  public ResponseEntity<Manutencoes.Dados> buscar(@AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(manutencoes.buscar(perfil(jwt), id));
  }

  @PostMapping
  public ResponseEntity<Manutencoes.Dados> registrar(@AuthenticationPrincipal Jwt jwt,
      @RequestHeader(value = "Authorization", required = false) String token,
      @RequestBody GerenciarManutencoes.Entrada entrada) {
    var novo = manutencoes.registrar(perfil(jwt), autor(jwt), token, entrada);
    return ResponseEntity.created(URI.create("/api/v1/operacoes/manutencoes/" + novo.id()))
        .cacheControl(CacheControl.noStore()).body(novo);
  }

  private String perfil(Jwt jwt) { return jwt == null ? null : jwt.getClaimAsString("perfil"); }
  private UUID autor(Jwt jwt) {
    try { return jwt == null ? null : UUID.fromString(jwt.getSubject()); }
    catch (IllegalArgumentException erro) {
      throw new FalhaManutencao(FalhaManutencao.Tipo.PROIBIDO, "Identidade inválida.");
    }
  }

  @ExceptionHandler(FalhaManutencao.class)
  public ResponseEntity<Map<String, String>> falha(FalhaManutencao erro) {
    int status = switch (erro.tipo()) {
      case PROIBIDO -> 403;
      case INVALIDO -> 422;
      case NAO_ENCONTRADO -> 404;
      case INDISPONIVEL -> 503;
    };
    return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
        .body(Map.of("message", erro.getMessage()));
  }
}

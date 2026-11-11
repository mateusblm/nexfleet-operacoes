package br.com.nexfleet.operacoes.registrarabastecimento.api;

import br.com.nexfleet.operacoes.registrarabastecimento.application.*;
import java.net.URI;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/operacoes/abastecimentos")
public class AbastecimentosController {
  private final GerenciarAbastecimentos abastecimentos;

  public AbastecimentosController(GerenciarAbastecimentos abastecimentos) {
    this.abastecimentos = abastecimentos;
  }

  @GetMapping
  public ResponseEntity<List<Abastecimentos.Dados>> listar(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) UUID veiculoId,
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "20") int tamanho) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(abastecimentos.listar(perfil(jwt), veiculoId, pagina, tamanho));
  }

  @GetMapping("/{id}")
  public ResponseEntity<Abastecimentos.Dados> buscar(@AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(abastecimentos.buscar(perfil(jwt), id));
  }

  @PostMapping
  public ResponseEntity<Abastecimentos.Dados> registrar(
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader(value = "Authorization", required = false) String token,
      @RequestBody GerenciarAbastecimentos.Entrada entrada) {
    var novo = abastecimentos.registrar(perfil(jwt), autor(jwt), token, entrada);
    return ResponseEntity.created(URI.create("/api/v1/operacoes/abastecimentos/" + novo.id()))
        .cacheControl(CacheControl.noStore()).body(novo);
  }

  private String perfil(Jwt jwt) { return jwt == null ? null : jwt.getClaimAsString("perfil"); }

  private UUID autor(Jwt jwt) {
    try { return jwt == null ? null : UUID.fromString(jwt.getSubject()); }
    catch (IllegalArgumentException error) {
      throw new FalhaAbastecimento(FalhaAbastecimento.Tipo.PROIBIDO, "Identidade inválida.");
    }
  }

  @ExceptionHandler(FalhaAbastecimento.class)
  public ResponseEntity<Map<String, String>> falha(FalhaAbastecimento error) {
    int status = switch (error.tipo()) {
      case PROIBIDO -> 403;
      case INVALIDO -> 422;
      case NAO_ENCONTRADO -> 404;
      case CONFLITO -> 409;
      case INDISPONIVEL -> 503;
    };
    return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
        .body(Map.of("message", error.getMessage()));
  }
}

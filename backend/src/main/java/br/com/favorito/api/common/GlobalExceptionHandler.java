package br.com.favorito.api.common;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(RiotApiException.class)
  public ResponseEntity<Map<String, Object>> riot(RiotApiException e) {
    return ResponseEntity.status(e.httpStatus())
        .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Falha na Riot API"));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException e) {
    String msg =
        e.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining("; "));
    return ResponseEntity.badRequest().body(Map.of("error", msg));
  }

  @ExceptionHandler({NoSuchElementException.class})
  public ResponseEntity<Map<String, Object>> notFound(NoSuchElementException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Não encontrado."));
  }

  /** Violação de formato/salt white list (gameName, tagLine, platform). */
  @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
  public ResponseEntity<Map<String, Object>> constraint(
      jakarta.validation.ConstraintViolationException e) {
    String msg =
        e.getConstraintViolations().stream()
            .map(v -> v.getMessage())
            .distinct()
            .collect(Collectors.joining("; "));
    return ResponseEntity.badRequest().body(Map.of("error", msg));
  }

  /** Argumento rejeitado na camada de domínio (platform fora da whitelist, p.ex.). */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException e) {
    return ResponseEntity.badRequest()
        .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Requisição inválida."));
  }

  /**
   * Rota inexistente (ex.: springdoc desligado em produção): 404 limpo — sem o catch-all abaixo,
   * cairia como 500 e mascararia o diagnóstico.
   */
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<Map<String, Object>> noResource(NoResourceFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(Map.of("error", "Rota não encontrada: " + e.getResourcePath()));
  }

  /**
   * ResponseStatusException (o 429 do rate limit do login, p.ex.): sem este handler, a exceção
   * cairia no catch-all genérico como 500 e mascararia o status original.
   */
  @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
  public ResponseEntity<Map<String, Object>> status(
      org.springframework.web.server.ResponseStatusException e) {
    String msg =
        e.getReason() != null && !e.getReason().isBlank()
            ? e.getReason()
            : e.getStatusCode().toString();
    return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", msg));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> generic(Exception e) {
    // detalhe completo só no log — a mensagem interna não vaza para o client
    log.error("Erro não tratado", e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(Map.of("error", "Erro interno."));
  }
}

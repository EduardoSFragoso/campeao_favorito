package br.com.favorito.api.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Erro ao chamar a Riot API (404, 403, 429 etc). */
@ResponseStatus(HttpStatus.BAD_GATEWAY)
public class RiotApiException extends RuntimeException {
  public final int upstreamStatus;

  public RiotApiException(int upstreamStatus, String message) {
    super(message);
    this.upstreamStatus = upstreamStatus;
  }

  public HttpStatus httpStatus() {
    if (upstreamStatus == 404) return HttpStatus.NOT_FOUND;
    if (upstreamStatus == 429) return HttpStatus.TOO_MANY_REQUESTS;
    return HttpStatus.BAD_GATEWAY;
  }
}

/** Extrai mensagem amigável de um erro desconhecido (fetch/HTTP/validação). */
export function messageOf(e: unknown, fallback = 'Algo deu errado.'): string {
  if (typeof e === 'object' && e !== null) {
    const anyErr = e as { error?: { error?: string }; message?: string };
    if (typeof anyErr.error?.error === 'string') return anyErr.error.error;
    if (typeof anyErr.message === 'string') return anyErr.message;
  }
  if (typeof e === 'string') return e;
  return fallback;
}

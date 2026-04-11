/**
 * Thin fetch wrapper that:
 *  - Always sends credentials (session cookie)
 *  - Reads the XSRF-TOKEN cookie and attaches it as X-XSRF-TOKEN on
 *    mutating requests (POST, PUT, PATCH, DELETE)
 *  - On error, throws an HttpError with `status` and `body` fields so
 *    callers can handle specific status codes (e.g. 409 duplicate check)
 */

const CSRF_COOKIE  = 'XSRF-TOKEN';
const CSRF_HEADER  = 'X-XSRF-TOKEN';
const MUTATING     = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

/** Error thrown for non-2xx responses. Carries the HTTP status and parsed body. */
export class HttpError extends Error {
  constructor(status, body) {
    super(body?.message || body?.error || `HTTP ${status}`);
    this.status = status;
    this.body   = body;
  }
}

function getCsrfToken() {
  const match = document.cookie
    .split('; ')
    .find(row => row.startsWith(CSRF_COOKIE + '='));
  return match ? decodeURIComponent(match.split('=')[1]) : '';
}

async function request(method, path, body) {
  const headers = { 'Content-Type': 'application/json' };
  if (MUTATING.has(method)) {
    headers[CSRF_HEADER] = getCsrfToken();
  }

  const res = await fetch(path, {
    method,
    credentials: 'include',
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (res.status === 204) return null;

  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new HttpError(res.status, data);
  }
  return data;
}

/**
 * Multipart file upload helper.
 * Does NOT set Content-Type — the browser sets it automatically with the
 * correct boundary when the body is a FormData instance.
 */
async function uploadFile(path, formData) {
  const res = await fetch(path, {
    method: 'POST',
    credentials: 'include',
    headers: { [CSRF_HEADER]: getCsrfToken() },
    body: formData,
  });

  if (res.status === 204) return null;
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new HttpError(res.status, data);
  return data;
}

export const api = {
  get:    (path)              => request('GET',    path),
  post:   (path, body)        => request('POST',   path, body),
  put:    (path, body)        => request('PUT',    path, body),
  patch:  (path, body)        => request('PATCH',  path, body),
  delete: (path)              => request('DELETE', path),
  upload: (path, formData)    => uploadFile(path, formData),
};

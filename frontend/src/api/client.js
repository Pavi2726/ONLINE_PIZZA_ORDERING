export class ApiError extends Error {
    constructor(status, message, fieldErrors) {
        super(message);
        this.name = 'ApiError';
        this.status = status;
        this.fieldErrors = fieldErrors || {};
    }
}

let onUnauthorized = () => {};

/** Registered by <ApiBridge/> inside the Router so it can navigate. */
export function setUnauthorizedHandler(fn) {
    onUnauthorized = fn;
}

export async function request(path, { method = 'GET', body, form } = {}) {
    const init = { method, credentials: 'include' };

    if (form) {
        // Never set Content-Type here — the browser must add the multipart boundary.
        init.body = form;
    } else if (body !== undefined) {
        init.headers = { 'Content-Type': 'application/json' };
        init.body = JSON.stringify(body);
    }

    const res = await fetch('/api' + path, init);
    const payload = res.status === 204 ? null : await res.json().catch(() => null);

    if (!res.ok) {
        if (res.status === 401) onUnauthorized(path);
        throw new ApiError(
            res.status,
            payload?.message ?? 'Something went wrong. Please try again later.',
            payload?.fieldErrors
        );
    }
    return payload;
}

export const get = (path) => request(path);
export const post = (path, body) => request(path, { method: 'POST', body });
export const put = (path, body) => request(path, { method: 'PUT', body });
export const del = (path) => request(path, { method: 'DELETE' });
export const postForm = (path, form) => request(path, { method: 'POST', form });

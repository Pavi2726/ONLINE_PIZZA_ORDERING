import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, del, get, post, postForm, setUnauthorizedHandler } from '../api/client';

function mockResponse(status, body) {
    return {
        ok: status >= 200 && status < 300,
        status,
        json: () => Promise.resolve(body),
    };
}

describe('api client', () => {
    beforeEach(() => {
        global.fetch = vi.fn();
        setUnauthorizedHandler(() => {});
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('prefixes /api and always sends the session cookie', async () => {
        global.fetch.mockResolvedValue(mockResponse(200, { itemCount: 0 }));

        await get('/cart');

        const [url, init] = global.fetch.mock.calls[0];
        expect(url).toBe('/api/cart');
        expect(init.credentials).toBe('include');
    });

    it('sends a JSON body with the right content type', async () => {
        global.fetch.mockResolvedValue(mockResponse(200, {}));

        await post('/cart/items', { pizzaId: 7 });

        const [, init] = global.fetch.mock.calls[0];
        expect(init.method).toBe('POST');
        expect(init.headers['Content-Type']).toBe('application/json');
        expect(JSON.parse(init.body)).toEqual({ pizzaId: 7 });
    });

    // The browser must set the multipart boundary itself; setting Content-Type breaks it.
    it('never sets Content-Type when posting FormData', async () => {
        global.fetch.mockResolvedValue(mockResponse(201, {}));
        const form = new FormData();
        form.append('name', 'Margherita');

        await postForm('/admin/pizzas', form);

        const [, init] = global.fetch.mock.calls[0];
        expect(init.headers).toBeUndefined();
        expect(init.body).toBe(form);
    });

    it('turns an error response into an ApiError carrying the field errors', async () => {
        global.fetch.mockResolvedValue(
            mockResponse(400, {
                status: 400,
                message: 'Please correct the highlighted fields.',
                fieldErrors: { price: 'Price must be greater than 0' },
            })
        );

        await expect(post('/admin/coupons', {})).rejects.toThrowError(ApiError);

        try {
            await post('/admin/coupons', {});
        } catch (error) {
            expect(error.status).toBe(400);
            expect(error.fieldErrors.price).toBe('Price must be greater than 0');
        }
    });

    it('fires the unauthorized handler on a 401 so the app can route to login', async () => {
        const onUnauthorized = vi.fn();
        setUnauthorizedHandler(onUnauthorized);
        global.fetch.mockResolvedValue(mockResponse(401, { message: 'Please log in to continue.' }));

        await expect(get('/cart')).rejects.toThrowError(ApiError);
        expect(onUnauthorized).toHaveBeenCalledWith('/cart');
    });

    it('still throws a usable ApiError when the body is not JSON at all', async () => {
        global.fetch.mockResolvedValue({
            ok: false,
            status: 500,
            json: () => Promise.reject(new Error('not json')),
        });

        try {
            await del('/cart/items/1');
            throw new Error('expected the request to reject');
        } catch (error) {
            expect(error).toBeInstanceOf(ApiError);
            expect(error.status).toBe(500);
            expect(error.message).toBe('Something went wrong. Please try again later.');
        }
    });
});

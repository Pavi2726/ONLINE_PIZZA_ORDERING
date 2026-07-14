import { del, get, post, postForm, put } from './client';

const query = (params) => {
    const search = new URLSearchParams();
    Object.entries(params ?? {}).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== '') search.append(key, value);
    });
    const string = search.toString();
    return string ? `?${string}` : '';
};

export const auth = {
    me: () => get('/me'),
    register: (body) => post('/auth/register', body),
    login: (body) => post('/auth/login', body),
    logout: () => post('/auth/logout'),
};

export const pizzas = {
    list: (params) => get(`/pizzas${query(params)}`),
};

export const drinks = {
    list: (params) => get(`/drinks${query(params)}`),
    get: (id) => get(`/drinks/${id}`),
    categories: () => get('/drinks/categories'),
};

export const cart = {
    get: () => get('/cart'),
    addItem: (pizzaId) => post('/cart/items', { pizzaId }),
    addDrink: (drinkId) => post('/cart/drinks', { drinkId }),
    removeItem: (cartItemId) => del(`/cart/items/${cartItemId}`),
    increase: (cartItemId) => post(`/cart/items/${cartItemId}/increase`),
    decrease: (cartItemId) => post(`/cart/items/${cartItemId}/decrease`),
    applyCoupon: (couponCode) => post('/cart/coupon', { couponCode }),
    removeCoupon: () => del('/cart/coupon'),
};

export const orders = {
    place: () => post('/orders'),
    history: () => get('/orders'),
    byNumber: (orderNumber) => get(`/orders/by-number/${orderNumber}`),
    forEdit: (orderId) => get(`/orders/${orderId}`),
    cancel: (orderId) => post(`/orders/${orderId}/cancel`),
    reorder: (orderId) => post(`/orders/${orderId}/reorder`),
    updateDetails: (orderId, body) => put(`/orders/${orderId}`, body),
    addItem: (orderId, pizzaId, quantity = 1) => post(`/orders/${orderId}/items`, { pizzaId, quantity }),
    addDrink: (orderId, drinkId, quantity = 1) => post(`/orders/${orderId}/drinks`, { drinkId, quantity }),
    increaseItem: (orderId, itemId) => post(`/orders/${orderId}/items/${itemId}/increase`),
    decreaseItem: (orderId, itemId) => post(`/orders/${orderId}/items/${itemId}/decrease`),
    removeItem: (orderId, itemId) => del(`/orders/${orderId}/items/${itemId}`),
};

export const admin = {
    login: (body) => post('/admin/login', body),
    logout: () => post('/admin/logout'),
    dashboard: () => get('/admin/dashboard'),

    pizzas: {
        list: (params) => get(`/admin/pizzas${query(params)}`),
        get: (id) => get(`/admin/pizzas/${id}`),
        // Multipart: the browser sets the boundary, so the client never sets Content-Type.
        create: (form) => postForm('/admin/pizzas', form),
        update: (id, form) => postForm(`/admin/pizzas/${id}`, form),
        remove: (id) => del(`/admin/pizzas/${id}`),
    },

    drinks: {
        list: (params) => get(`/admin/drinks${query(params)}`),
        get: (id) => get(`/admin/drinks/${id}`),
        create: (form) => postForm('/admin/drinks', form),
        update: (id, form) => postForm(`/admin/drinks/${id}`, form),
        remove: (id) => del(`/admin/drinks/${id}`),
    },

    coupons: {
        list: () => get('/admin/coupons'),
        get: (id) => get(`/admin/coupons/${id}`),
        create: (body) => post('/admin/coupons', body),
        update: (id, body) => put(`/admin/coupons/${id}`, body),
        remove: (id) => del(`/admin/coupons/${id}`),
    },

    customers: {
        list: (params) => get(`/admin/customers${query(params)}`),
        get: (id) => get(`/admin/customers/${id}`),
        update: (id, body) => put(`/admin/customers/${id}`, body),
    },

    orders: {
        list: (params) => get(`/admin/orders${query(params)}`),
        get: (id) => get(`/admin/orders/${id}`),
        updateStatus: (id, targetStatus) => post(`/admin/orders/${id}/status`, { targetStatus }),
        bulkStatus: (orderIds, targetStatus) =>
            post('/admin/orders/bulk-status', { orderIds, targetStatus }),
    },
};

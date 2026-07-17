import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import Cart from '../pages/Cart';
import OrderHistory from '../pages/OrderHistory';
import { cart as cartApi, orders as orderApi } from '../api';
import { ApiError } from '../api/client';
import { AlertProvider } from '../context/AlertContext';
import * as SessionContext from '../context/SessionContext';

vi.mock('../api', () => ({
    cart: {
        get: vi.fn(),
        applyCoupon: vi.fn(),
        removeCoupon: vi.fn(),
        increase: vi.fn(),
        decrease: vi.fn(),
        removeItem: vi.fn(),
    },
    orders: {
        history: vi.fn(),
        cancel: vi.fn(),
        reorder: vi.fn(),
    },
}));

function renderWithProviders(ui) {
    return render(
        <AlertProvider>
            <MemoryRouter>{ui}</MemoryRouter>
        </AlertProvider>
    );
}

describe('Cart', () => {
    afterEach(() => vi.restoreAllMocks());

    // CartApiController#applyCoupon clears the session's coupon before rethrowing on an
    // invalid code, so the page must re-sync rather than keep showing the old one as applied.
    it('re-syncs after a rejected coupon instead of leaving the old one showing as applied', async () => {
        vi.spyOn(SessionContext, 'useSession').mockReturnValue({ setCartItemCount: vi.fn() });

        const withCoupon = {
            items: [{ id: 1, pizzaName: 'Margherita', unitPrice: 199, quantity: 1, itemTotal: 199 }],
            subtotal: 199,
            discount: 19.9,
            grandTotal: 179.1,
            appliedCoupon: { couponCode: 'SAVE10', discountPercentage: 10 },
            activeCoupons: [],
            itemCount: 1,
        };
        const clearedByServer = { ...withCoupon, discount: 0, grandTotal: 199, appliedCoupon: null };

        cartApi.get.mockResolvedValueOnce(withCoupon).mockResolvedValueOnce(clearedByServer);
        cartApi.applyCoupon.mockRejectedValue(new ApiError(404, 'Invalid coupon code.'));

        const user = userEvent.setup();
        renderWithProviders(<Cart />);

        expect(await screen.findByText('SAVE10')).toBeInTheDocument();

        await user.type(screen.getByPlaceholderText('Enter Coupon Code'), 'BADCODE');
        await user.click(screen.getByRole('button', { name: 'Apply Coupon' }));

        await waitFor(() => expect(cartApi.get).toHaveBeenCalledTimes(2));
        await waitFor(() => expect(screen.queryByText('SAVE10')).not.toBeInTheDocument());
    });
});

describe('OrderHistory', () => {
    afterEach(() => vi.restoreAllMocks());

    // ApiMappers: cancellable and editable are the same condition - PLACED AND < 5 minutes
    // old. Once the window closes the order is locked: neither action is available again
    // until an admin advances the order past PLACED.
    it('hides both Cancel and Edit Order once the edit window has closed', async () => {
        vi.spyOn(SessionContext, 'useSession').mockReturnValue({ refresh: vi.fn() });

        orderApi.history.mockResolvedValue([
            {
                id: 42,
                orderNumber: 'ORD-1',
                status: 'PLACED',
                stepIndex: 0,
                estimatedWindow: '45–60 min',
                items: [{ id: 1, pizzaName: 'Margherita', quantity: 1, price: 199, lineTotal: 199 }],
                subtotal: 199,
                discountAmount: null,
                tax: 10,
                totalAmount: 209,
                createdAt: '2026-07-14T09:00:00',
                updatedAt: null,
                cancellable: false,
                editable: false,
            },
        ]);

        renderWithProviders(<OrderHistory />);

        expect(await screen.findByRole('link', { name: /view details/i })).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /cancel order/i })).not.toBeInTheDocument();
        expect(screen.queryByRole('link', { name: /edit order/i })).not.toBeInTheDocument();
    });

    it('still shows Edit Order while inside the five-minute window', async () => {
        vi.spyOn(SessionContext, 'useSession').mockReturnValue({ refresh: vi.fn() });

        orderApi.history.mockResolvedValue([
            {
                id: 42,
                orderNumber: 'ORD-1',
                status: 'PLACED',
                stepIndex: 0,
                estimatedWindow: '45–60 min',
                items: [{ id: 1, pizzaName: 'Margherita', quantity: 1, price: 199, lineTotal: 199 }],
                subtotal: 199,
                discountAmount: null,
                tax: 10,
                totalAmount: 209,
                createdAt: '2026-07-14T09:00:00',
                updatedAt: null,
                cancellable: true,
                editable: true,
            },
        ]);

        renderWithProviders(<OrderHistory />);

        expect(await screen.findByRole('link', { name: /edit order/i })).toBeInTheDocument();
    });
});

import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import AlertStack from '../components/AlertStack';
import Pagination from '../components/Pagination';
import SubmitButton from '../components/SubmitButton';
import OrderStatusStepper from '../components/OrderStatusStepper';
import { RequireAdmin, RequireCustomer } from '../components/Guards';
import { AlertProvider, useAlerts } from '../context/AlertContext';
import * as SessionContext from '../context/SessionContext';

function AlertHarness({ type, message }) {
    const { pushAlert } = useAlerts();
    return (
        <>
            <button type="button" onClick={() => pushAlert(type, message)}>
                raise
            </button>
            <AlertStack bare />
        </>
    );
}

describe('AlertStack', () => {
    it('renders the message the API returned', async () => {
        const user = userEvent.setup();
        render(
            <AlertProvider>
                <AlertHarness type="success" message="Pizza added to cart successfully!" />
            </AlertProvider>
        );

        await user.click(screen.getByRole('button', { name: 'raise' }));

        expect(screen.getByRole('alert')).toHaveTextContent('Pizza added to cart successfully!');
        expect(screen.getByRole('alert')).toHaveClass('alert-success');
    });

    // Bootstrap's own data-bs-dismiss would rip the node out from under React.
    it('dismisses through React when the close button is clicked', async () => {
        const user = userEvent.setup();
        render(
            <AlertProvider>
                <AlertHarness type="danger" message="Something went wrong." />
            </AlertProvider>
        );

        await user.click(screen.getByRole('button', { name: 'raise' }));
        expect(screen.getByRole('alert')).toBeInTheDocument();

        await user.click(screen.getByRole('button', { name: 'Close' }));
        expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    });
});

describe('AlertStack auto-dismiss', () => {
    beforeEach(() => vi.useFakeTimers());
    afterEach(() => vi.useRealTimers());

    it('clears itself after five seconds, as app.js did', async () => {
        // Driven through act rather than userEvent: user-event schedules its own real
        // timers, which deadlock against the fake ones this test needs.
        render(
            <AlertProvider>
                <AlertHarness type="success" message="Coupon applied successfully!" />
            </AlertProvider>
        );

        act(() => screen.getByRole('button', { name: 'raise' }).click());
        expect(screen.getByRole('alert')).toBeInTheDocument();

        act(() => vi.advanceTimersByTime(5200));

        expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    });
});

describe('SubmitButton', () => {
    it('disables itself while its action is in flight, preventing a double submit', () => {
        const { container, rerender } = render(<SubmitButton pending={false}>Place Order</SubmitButton>);
        expect(screen.getByRole('button')).toBeEnabled();
        expect(container.querySelector('.spinner-border')).toBeNull();

        rerender(<SubmitButton pending>Place Order</SubmitButton>);
        expect(screen.getByRole('button')).toBeDisabled();
        // The spinner is aria-hidden (Bootstrap's own markup), so it is deliberately
        // absent from the accessibility tree - assert on the DOM instead.
        expect(container.querySelector('.spinner-border')).toBeInTheDocument();
    });
});

describe('Pagination', () => {
    // The "disabled" CSS class alone doesn't stop a click; the control itself must be
    // disabled or « on page 1 sends the view to page 0 (an empty, unlabeled grid).
    it('actually disables the prev control on the first page', () => {
        const onChange = vi.fn();
        render(<Pagination page={1} pageCount={3} onChange={onChange} />);

        const prev = screen.getByRole('button', { name: '«' });
        expect(prev).toBeDisabled();

        prev.click();
        expect(onChange).not.toHaveBeenCalled();
    });

    it('actually disables the next control on the last page', () => {
        const onChange = vi.fn();
        render(<Pagination page={3} pageCount={3} onChange={onChange} />);

        const next = screen.getByRole('button', { name: '»' });
        expect(next).toBeDisabled();

        next.click();
        expect(onChange).not.toHaveBeenCalled();
    });
});

describe('OrderStatusStepper', () => {
    it('shows a cancellation banner instead of the progress steps', () => {
        render(<OrderStatusStepper status="CANCELLED" stepIndex={-1} estimatedWindow="" />);

        expect(screen.getByText(/This order was cancelled\./)).toBeInTheDocument();
        expect(screen.queryByText('Processing')).not.toBeInTheDocument();
    });

    it('renders the four steps and the estimate the server computed', () => {
        render(<OrderStatusStepper status="PLACED" stepIndex={0} estimatedWindow="45–60 min" />);

        expect(screen.getByText('Placed')).toBeInTheDocument();
        expect(screen.getByText('Delivered')).toBeInTheDocument();
        expect(screen.getByText(/45–60 min/)).toBeInTheDocument();
    });
});

describe('route guards', () => {
    function renderGuarded(Guard, session, startAt = '/protected') {
        vi.spyOn(SessionContext, 'useSession').mockReturnValue(session);

        return render(
            <MemoryRouter initialEntries={[startAt]}>
                <Routes>
                    <Route element={<Guard />}>
                        <Route path="/protected" element={<p>secret</p>} />
                    </Route>
                    <Route path="/login" element={<p>customer login</p>} />
                    <Route path="/admin/login" element={<p>admin login</p>} />
                </Routes>
            </MemoryRouter>
        );
    }

    afterEach(() => vi.restoreAllMocks());

    it('sends an anonymous visitor to the customer login', () => {
        renderGuarded(RequireCustomer, { customer: null, admin: null, loading: false });
        expect(screen.getByText('customer login')).toBeInTheDocument();
    });

    it('lets a logged-in customer through', () => {
        renderGuarded(RequireCustomer, { customer: { id: 1 }, admin: null, loading: false });
        expect(screen.getByText('secret')).toBeInTheDocument();
    });

    it('sends a non-admin to the admin login', () => {
        renderGuarded(RequireAdmin, { customer: { id: 1 }, admin: null, loading: false });
        expect(screen.getByText('admin login')).toBeInTheDocument();
    });

    // Bouncing before /api/me resolves would eject a logged-in user on every refresh.
    it('waits rather than redirecting while the session is still loading', () => {
        renderGuarded(RequireCustomer, { customer: null, admin: null, loading: true });
        expect(screen.queryByText('customer login')).not.toBeInTheDocument();
        expect(screen.queryByText('secret')).not.toBeInTheDocument();
    });
});

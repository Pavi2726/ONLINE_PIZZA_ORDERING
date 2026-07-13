import { useState } from 'react';
import { Link } from 'react-router-dom';
import { cart as cartApi } from '../api';
import { ApiError } from '../api/client';
import { useAlerts } from '../context/AlertContext';
import { useSession } from '../context/SessionContext';
import { useApi, useSubmit } from '../hooks/useApi';
import { Spinner } from '../components/Guards';
import { money } from '../lib/format';

export default function Cart() {
    const { data: cart, setData: setCart, loading } = useApi(() => cartApi.get(), []);
    const [couponCode, setCouponCode] = useState('');
    const [pending, run] = useSubmit();
    const { setCartItemCount } = useSession();
    const { pushAlert, pushFromResponse } = useAlerts();

    /**
     * Every cart mutation answers with the whole refreshed cart, so one handler covers
     * all of them: swap in the new cart, sync the navbar badge, show the message.
     */
    async function mutate(action) {
        await run(async () => {
            try {
                const result = await action();
                setCart(result.data);
                setCartItemCount(result.data.itemCount);
                pushFromResponse(result);
            } catch (error) {
                if (error instanceof ApiError) pushAlert('danger', error.message);
            }
        });
    }

    function applyCoupon(event) {
        event.preventDefault();
        mutate(() => cartApi.applyCoupon(couponCode));
    }

    if (loading) return <Spinner />;

    const items = cart?.items ?? [];
    const applied = cart?.appliedCoupon;
    const activeCoupons = cart?.activeCoupons ?? [];

    return (
        <section className="container my-5">
            <h2 className="fw-bold mb-4">
                <i className="bi bi-cart" /> Shopping Cart
            </h2>

            {items.length === 0 ? (
                <div className="alert alert-info">Your cart is empty.</div>
            ) : (
                <>
                    <table className="table table-bordered align-middle">
                        <thead className="table-dark">
                            <tr>
                                <th>Pizza</th>
                                <th>Price</th>
                                <th>Quantity</th>
                                <th>Total</th>
                                <th>Action</th>
                            </tr>
                        </thead>
                        <tbody>
                            {items.map((item) => (
                                <tr key={item.id}>
                                    <td>{item.pizzaName}</td>
                                    <td>{money(item.unitPrice)}</td>
                                    <td>
                                        <div className="d-flex align-items-center gap-2">
                                            <button
                                                type="button"
                                                className="btn btn-sm btn-warning"
                                                disabled={pending}
                                                onClick={() => mutate(() => cartApi.decrease(item.id))}
                                            >
                                                -
                                            </button>
                                            <span className="fw-bold px-2">{item.quantity}</span>
                                            <button
                                                type="button"
                                                className="btn btn-sm btn-success"
                                                disabled={pending}
                                                onClick={() => mutate(() => cartApi.increase(item.id))}
                                            >
                                                +
                                            </button>
                                        </div>
                                    </td>
                                    <td>{money(item.itemTotal)}</td>
                                    <td>
                                        <button
                                            type="button"
                                            className="btn btn-sm btn-danger"
                                            disabled={pending}
                                            onClick={() => mutate(() => cartApi.removeItem(item.id))}
                                        >
                                            <i className="bi bi-trash" /> Remove
                                        </button>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>

                    <div className="row mt-4">
                        <div className="col-md-6">
                            <form onSubmit={applyCoupon}>
                                <div className="input-group">
                                    <input
                                        type="text"
                                        className="form-control"
                                        placeholder="Enter Coupon Code"
                                        value={couponCode}
                                        onChange={(event) => setCouponCode(event.target.value)}
                                        required
                                    />
                                    <button className="btn btn-primary" type="submit" disabled={pending}>
                                        Apply Coupon
                                    </button>
                                </div>
                            </form>

                            {applied && (
                                <div className="mt-3 d-flex align-items-center gap-2">
                                    <span className="badge bg-success">
                                        <span>{applied.couponCode}</span> applied
                                    </span>
                                    <button
                                        type="button"
                                        className="btn btn-outline-danger btn-sm"
                                        disabled={pending}
                                        onClick={() => mutate(() => cartApi.removeCoupon())}
                                    >
                                        Remove Coupon
                                    </button>
                                </div>
                            )}

                            {activeCoupons.length > 0 && (
                                <div className="mt-3">
                                    <h6>Available Offers</h6>
                                    <div className="d-flex flex-wrap gap-2">
                                        {activeCoupons.map((c) => {
                                            const isApplied = applied?.couponCode === c.couponCode;
                                            return (
                                                <button
                                                    key={c.id}
                                                    type="button"
                                                    className={
                                                        isApplied
                                                            ? 'btn btn-success btn-sm'
                                                            : 'btn btn-outline-success btn-sm'
                                                    }
                                                    // Only one coupon at a time: the others are inert while one is on.
                                                    disabled={pending || (applied && !isApplied)}
                                                    onClick={() => mutate(() => cartApi.applyCoupon(c.couponCode))}
                                                >
                                                    <span>{c.couponCode}</span> — <span>{c.discountPercentage}</span>%
                                                    off{isApplied && <span> (Applied)</span>}
                                                </button>
                                            );
                                        })}
                                    </div>
                                </div>
                            )}
                        </div>
                    </div>

                    <div className="d-flex justify-content-end mt-4">
                        <div className="card p-4" style={{ minWidth: '350px' }}>
                            <h4 className="mb-3">Order Summary</h4>

                            <div className="d-flex justify-content-between mb-2">
                                <span>Subtotal</span>
                                <span>{money(cart.subtotal)}</span>
                            </div>

                            <div className="d-flex justify-content-between mb-2">
                                <span>Discount</span>
                                <span className="text-success">- {money(cart.discount)}</span>
                            </div>

                            <hr />

                            <div className="d-flex justify-content-between fw-bold fs-5">
                                <span>Grand Total</span>
                                <span className="text-success">{money(cart.grandTotal)}</span>
                            </div>

                            <hr />

                            <div className="d-grid mt-3">
                                <Link to="/checkout" className="btn btn-success btn-lg">
                                    <i className="bi bi-bag-check" /> Proceed to Checkout
                                </Link>
                            </div>
                        </div>
                    </div>
                </>
            )}
        </section>
    );
}

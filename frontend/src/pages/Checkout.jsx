import { Link, useNavigate } from 'react-router-dom';
import { cart as cartApi, orders as orderApi } from '../api';
import { ApiError } from '../api/client';
import { useAlerts } from '../context/AlertContext';
import { useSession } from '../context/SessionContext';
import { useApi, useSubmit } from '../hooks/useApi';
import { Spinner } from '../components/Guards';
import SubmitButton from '../components/SubmitButton';
import { money } from '../lib/format';

// Matches OrderStatus.PLACED.estimatedWindowLabel(), which the old page read via SpEL.
const PLACED_ESTIMATE = '45–60 min';

export default function Checkout() {
    const { data: cart, loading } = useApi(() => cartApi.get(), []);
    const [pending, run] = useSubmit();
    const { setCartItemCount } = useSession();
    const { pushAlert, pushFromResponse } = useAlerts();
    const navigate = useNavigate();

    async function placeOrder() {
        await run(async () => {
            try {
                const result = await orderApi.place();
                setCartItemCount(0);
                pushFromResponse(result);
                navigate(`/orders/success/${result.data.orderNumber}`);
            } catch (error) {
                if (error instanceof ApiError) pushAlert('danger', error.message);
            }
        });
    }

    if (loading) return <Spinner />;

    const items = cart?.items ?? [];

    return (
        <div className="container mt-5">
            <h2 className="mb-4 text-center">Checkout</h2>

            {items.length === 0 ? (
                <div className="alert alert-warning">Your cart is empty.</div>
            ) : (
                <>
                    <table className="table table-bordered align-middle">
                        <thead className="table-dark">
                            <tr>
                                <th>Item</th>
                                <th>Price</th>
                                <th>Quantity</th>
                                <th>Total</th>
                            </tr>
                        </thead>
                        <tbody>
                            {items.map((item) => (
                                <tr key={item.id}>
                                    <td>
                                        <span className="fw-semibold">
                                            {item.itemType === 'DRINK' ? item.drinkName : item.pizzaName}
                                        </span>
                                        {item.itemType === 'DRINK' && (
                                            <span className="ms-2 badge bg-primary bg-opacity-10 text-primary border border-primary border-opacity-25">
                                                🥤 Drink
                                            </span>
                                        )}
                                    </td>
                                    <td>{money(item.unitPrice)}</td>
                                    <td>{item.quantity}</td>
                                    <td>{money(item.itemTotal)}</td>
                                </tr>
                            ))}
                        </tbody>
                    </table>

                    <div className="card">
                        <div className="card-body">
                            <p>
                                <strong>Subtotal:</strong> {money(cart.subtotal)}
                            </p>
                            <p>
                                <strong>Discount:</strong> {money(cart.discount)}
                            </p>
                            {cart.appliedCoupon && (
                                <p>
                                    <strong>Coupon:</strong>{' '}
                                    <span className="text-success">{cart.appliedCoupon.couponCode}</span>
                                </p>
                            )}

                            <h4>
                                Grand Total : <span className="text-success">{money(cart.grandTotal)}</span>
                            </h4>

                            <hr />

                            <SubmitButton
                                type="button"
                                pending={pending}
                                className="btn btn-success btn-lg"
                                onClick={placeOrder}
                            >
                                Place Order
                            </SubmitButton>{' '}
                            <Link to="/cart" className="btn btn-secondary btn-lg">
                                Back to Cart
                            </Link>

                            <p className="text-muted small mt-2 mb-0">
                                Estimated delivery: <span>{PLACED_ESTIMATE}</span> after placing your order.
                            </p>
                        </div>
                    </div>
                </>
            )}
        </div>
    );
}

import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { orders as orderApi } from '../api';
import { ApiError } from '../api/client';
import { useAlerts } from '../context/AlertContext';
import { useSession } from '../context/SessionContext';
import { useApi, useSubmit } from '../hooks/useApi';
import { Spinner } from '../components/Guards';
import OrderStatusStepper from '../components/OrderStatusStepper';
import StatusBadge from '../components/StatusBadge';
import { dateTime, money } from '../lib/format';

export default function OrderHistory() {
    const { data: orders, loading, reload } = useApi(() => orderApi.history(), []);
    const [pending, run] = useSubmit();
    const [busyId, setBusyId] = useState(null);
    const { refresh } = useSession();
    const { pushAlert, pushFromResponse } = useAlerts();
    const navigate = useNavigate();

    async function cancel(orderId) {
        // Same confirmation guard the old page attached via onsubmit.
        if (!window.confirm('Are you sure you want to cancel this order?')) return;

        setBusyId(orderId);
        await run(async () => {
            try {
                pushFromResponse(await orderApi.cancel(orderId));
                await reload();
            } catch (error) {
                if (error instanceof ApiError) pushAlert('danger', error.message);
            } finally {
                setBusyId(null);
            }
        });
    }

    async function reorder(orderId) {
        setBusyId(orderId);
        await run(async () => {
            try {
                pushFromResponse(await orderApi.reorder(orderId));
                // Reorder merges items into the cart, so the navbar badge is now stale.
                await refresh();
                navigate('/cart');
            } catch (error) {
                if (error instanceof ApiError) pushAlert('danger', error.message);
            } finally {
                setBusyId(null);
            }
        });
    }

    if (loading) return <Spinner />;

    const list = orders ?? [];

    return (
        <section className="container my-5">
            <h2 className="mb-4">
                <i className="bi bi-clock-history" /> My Order History
            </h2>

            {list.length === 0 ? (
                <div className="alert alert-info">You haven&apos;t placed any orders yet.</div>
            ) : (
                list.map((order) => (
                    <div className="card shadow-sm mb-3" key={order.id}>
                        <div className="card-body">
                            <div className="row">
                                <div className="col-md-8">
                                    <h5 className="fw-bold mb-3">Ordered Items</h5>

                                    <table className="table table-bordered table-sm">
                                        <thead className="table-light">
                                            <tr>
                                                <th>Pizza</th>
                                                <th>Qty</th>
                                                <th>Price</th>
                                                <th>Total</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {order.items.map((item) => (
                                                <tr key={item.id}>
                                                    <td>{item.pizzaName}</td>
                                                    <td>{item.quantity}</td>
                                                    <td>{money(item.price)}</td>
                                                    <td>{money(item.lineTotal)}</td>
                                                </tr>
                                            ))}
                                        </tbody>
                                    </table>

                                    <p className="mb-1">
                                        <strong>Order Number:</strong> <span>{order.orderNumber}</span>
                                    </p>
                                    <p className="mb-1">
                                        <strong>Subtotal:</strong> {money(order.subtotal)}
                                    </p>

                                    {order.discountAmount != null && (
                                        <p className="mb-1 text-success">
                                            <strong>Discount:</strong> -{money(order.discountAmount)}
                                        </p>
                                    )}

                                    <p className="mb-1">
                                        <strong>Tax:</strong> {money(order.tax)}
                                    </p>
                                    <p className="mb-1 fw-bold">
                                        <strong>Grand Total:</strong> {money(order.totalAmount)}
                                    </p>

                                    {order.couponCode && (
                                        <div className="mb-1">
                                            <strong>Coupon:</strong>{' '}
                                            <span className="badge bg-success">{order.couponCode}</span>
                                            <span className="text-success ms-2">
                                                (<span>{order.discountPercentage}</span>% OFF)
                                            </span>
                                            <br />
                                            <small className="text-success">
                                                Saved {money(order.discountAmount)}
                                            </small>
                                        </div>
                                    )}

                                    <p className="mb-1">
                                        <strong>Status:</strong> <StatusBadge status={order.status} />
                                    </p>

                                    <OrderStatusStepper
                                        status={order.status}
                                        stepIndex={order.stepIndex}
                                        estimatedWindow={order.estimatedWindow}
                                    />

                                    <p className="text-muted mb-0">
                                        <strong>{order.updatedAt ? 'Updated On:' : 'Ordered On:'}</strong>{' '}
                                        <span>{dateTime(order.updatedAt ?? order.createdAt, ' ')}</span>
                                    </p>
                                </div>
                            </div>

                            {order.cancellable && (
                                <div className="d-flex justify-content-end gap-2 mt-3">
                                    <Link
                                        to={`/orders/success/${order.orderNumber}`}
                                        className="btn btn-info btn-sm"
                                    >
                                        <i className="bi bi-eye" /> View Details
                                    </Link>
                                    {/* Editing closes after five minutes, well before the order
                                        leaves PLACED status, so this needs its own flag. */}
                                    {order.editable && (
                                        <Link
                                            to={`/orders/edit/${order.id}`}
                                            className="btn btn-warning btn-sm"
                                        >
                                            <i className="bi bi-pencil-square" /> Edit Order
                                        </Link>
                                    )}
                                    <button
                                        type="button"
                                        className="btn btn-danger btn-sm"
                                        disabled={pending && busyId === order.id}
                                        onClick={() => cancel(order.id)}
                                    >
                                        <i className="bi bi-x-circle" /> Cancel Order
                                    </button>
                                </div>
                            )}

                            {/* Reorder is offered on every order, whatever its status. */}
                            <div className="d-flex justify-content-end mt-2">
                                <button
                                    type="button"
                                    className="btn btn-outline-primary btn-sm"
                                    disabled={pending && busyId === order.id}
                                    onClick={() => reorder(order.id)}
                                >
                                    <i className="bi bi-arrow-repeat" /> Reorder
                                </button>
                            </div>
                        </div>
                    </div>
                ))
            )}
        </section>
    );
}

import { Link, useParams } from 'react-router-dom';
import { admin as adminApi } from '../../api';
import { ApiError } from '../../api/client';
import { useAlerts } from '../../context/AlertContext';
import { useApi, useSubmit } from '../../hooks/useApi';
import { Spinner } from '../../components/Guards';
import StatusBadge from '../../components/StatusBadge';
import { dateTime, money } from '../../lib/format';

// The button style each transition gets, matching the old detail page.
const TRANSITION_STYLES = {
    PROCESSING: 'btn btn-info',
    OUT_FOR_DELIVERY: 'btn btn-primary',
    DELIVERED: 'btn btn-success',
    CANCELLED: 'btn btn-danger',
};

export default function AdminOrderDetail() {
    const { id } = useParams();
    const { pushAlert, pushFromResponse } = useAlerts();
    const [pending, run] = useSubmit();

    const { data: order, setData: setOrder, loading } = useApi(() => adminApi.orders.get(id), [id]);

    async function transition(targetStatus) {
        await run(async () => {
            try {
                const result = await adminApi.orders.updateStatus(id, targetStatus);
                setOrder(result.data);
                pushFromResponse(result);
            } catch (error) {
                if (error instanceof ApiError) pushAlert('danger', error.message);
            }
        });
    }

    if (loading) return <Spinner />;
    if (!order) return null;

    return (
        <>
            <div className="d-flex justify-content-between align-items-center flex-wrap mb-4">
                <h2 className="fw-bold mb-0">
                    <i className="bi bi-receipt" /> Order {order.orderNumber}
                </h2>
                <Link to="/admin/orders" className="btn btn-outline-secondary">
                    <i className="bi bi-arrow-left" /> Back to Orders
                </Link>
            </div>

            <div className="row g-4">
                <div className="col-12 col-lg-8">
                    <div className="card shadow-sm">
                        <div className="card-body">
                            <h5 className="fw-semibold mb-3">Ordered Items</h5>

                            <table className="table table-bordered align-middle">
                                <thead className="table-light">
                                    <tr>
                                        <th>Item</th>
                                        <th>Qty</th>
                                        <th className="text-end">Price</th>
                                        <th className="text-end">Total</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {order.items.map((item) => (
                                        <tr key={item.id}>
                                            <td>
                                                {item.itemType === 'DRINK'
                                                    ? item.drinkName
                                                    : item.itemType === 'PIZZA'
                                                        ? item.pizzaName
                                                        : 'Item no longer available'}
                                            </td>
                                            <td>{item.quantity}</td>
                                            <td className="text-end">{money(item.price)}</td>
                                            <td className="text-end">{money(item.lineTotal)}</td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>

                            <div className="row mb-2">
                                <div className="col-6 text-muted">Subtotal</div>
                                <div className="col-6 text-end">{money(order.subtotal)}</div>
                            </div>

                            {order.couponCode && (
                                <div className="row mb-2">
                                    <div className="col-6 text-muted">
                                        Coupon <span className="badge bg-success">{order.couponCode}</span>
                                    </div>
                                    <div className="col-6 text-end text-success">
                                        -{money(order.discountAmount)}
                                    </div>
                                </div>
                            )}

                            <div className="row mb-2">
                                <div className="col-6 text-muted">Tax</div>
                                <div className="col-6 text-end">{money(order.tax)}</div>
                            </div>

                            <div className="row fw-bold">
                                <div className="col-6">Grand Total</div>
                                <div className="col-6 text-end text-danger">{money(order.totalAmount)}</div>
                            </div>
                        </div>
                    </div>
                </div>

                <div className="col-12 col-lg-4">
                    <div className="card shadow-sm mb-4">
                        <div className="card-body">
                            <h5 className="fw-semibold mb-3">Customer</h5>
                            <p className="mb-1 fw-semibold">{order.customer?.fullName}</p>
                            <p className="mb-1 small text-muted">{order.customer?.email}</p>
                            <hr />
                            <p className="mb-1">
                                <strong>Delivery Address</strong>
                                <br />
                                {order.deliveryAddress}
                            </p>
                            <p className="mb-0">
                                <strong>Phone</strong>
                                <br />
                                {order.phone}
                            </p>
                        </div>
                    </div>

                    <div className="card shadow-sm">
                        <div className="card-body">
                            <h5 className="fw-semibold mb-3">Status</h5>
                            <p className="mb-2">
                                <StatusBadge status={order.status} />
                            </p>
                            <p className="text-muted small">Placed on {dateTime(order.createdAt)}</p>

                            {/*
                             * The server decides which transitions are legal for the current status,
                             * so the rules are not restated here. A terminal order offers none.
                             */}
                            {order.allowedNextStatuses.length > 0 ? (
                                <>
                                    <hr />
                                    <p className="small fw-semibold mb-2">Move this order to</p>
                                    <div className="d-flex flex-wrap gap-2">
                                        {order.allowedNextStatuses.map((target) => (
                                            <button
                                                key={target}
                                                type="button"
                                                className={`${TRANSITION_STYLES[target] ?? 'btn btn-secondary'} btn-sm`}
                                                disabled={pending}
                                                onClick={() => transition(target)}
                                            >
                                                {target.replace(/_/g, ' ')}
                                            </button>
                                        ))}
                                    </div>
                                </>
                            ) : (
                                <p className="text-muted small mb-0">
                                    This order has reached a final status.
                                </p>
                            )}
                        </div>
                    </div>
                </div>
            </div>
        </>
    );
}

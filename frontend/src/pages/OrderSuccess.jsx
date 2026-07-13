import { Link, useParams } from 'react-router-dom';
import { orders as orderApi } from '../api';
import { useApi } from '../hooks/useApi';
import { useSession } from '../context/SessionContext';
import { Spinner } from '../components/Guards';
import OrderStatusStepper from '../components/OrderStatusStepper';
import StatusBadge from '../components/StatusBadge';
import { dateTime, money } from '../lib/format';

export default function OrderSuccess() {
    const { orderNumber } = useParams();
    const { customer } = useSession();
    const { data: order, loading } = useApi(() => orderApi.byNumber(orderNumber), [orderNumber]);

    if (loading) return <Spinner />;
    if (!order) return null;

    return (
        <section className="container my-5">
            <div className="row justify-content-center">
                <div className="col-12 col-lg-7">
                    <div className="card shadow-sm text-center">
                        <div className="card-body p-4 p-md-5">
                            <i className="bi bi-check-circle-fill text-success" style={{ fontSize: '4rem' }} />
                            <h2 className="fw-bold mt-3">Thank You for Your Order!</h2>
                            <p className="text-muted">
                                Your delicious pizza is on its way to being prepared.
                            </p>

                            <div className="text-start bg-light rounded p-4 mt-4">
                                <div className="row mb-2">
                                    <div className="col-6 text-muted">Order Number</div>
                                    <div className="col-6 text-end fw-bold">{order.orderNumber}</div>
                                </div>

                                <hr />

                                <h5 className="mb-3">Ordered Items</h5>

                                <table className="table table-bordered">
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

                                <div className="row mb-2">
                                    <div className="col-6 text-muted">Subtotal</div>
                                    <div className="col-6 text-end">{money(order.subtotal)}</div>
                                </div>

                                {order.discountAmount != null && (
                                    <div className="row mb-2">
                                        <div className="col-6 text-muted">Discount</div>
                                        <div className="col-6 text-end text-success">
                                            -{money(order.discountAmount)}
                                        </div>
                                    </div>
                                )}

                                <div className="row mb-2">
                                    <div className="col-6 text-muted">Tax</div>
                                    <div className="col-6 text-end">{money(order.tax)}</div>
                                </div>

                                <div className="row mb-2 fw-bold">
                                    <div className="col-6">Grand Total</div>
                                    <div className="col-6 text-end text-danger">{money(order.totalAmount)}</div>
                                </div>

                                <hr />

                                <div className="row mb-2">
                                    <div className="col-6 text-muted">Customer</div>
                                    {/* The order is the logged-in customer's own, so the session already has this. */}
                                    <div className="col-6 text-end">{customer?.fullName}</div>
                                </div>

                                <div className="row mb-2">
                                    <div className="col-6 text-muted">Delivery Address</div>
                                    <div className="col-6 text-end">{order.deliveryAddress}</div>
                                </div>

                                <div className="row mb-2">
                                    <div className="col-6 text-muted">Status</div>
                                    <div className="col-6 text-end">
                                        <StatusBadge status={order.status} />
                                    </div>
                                </div>

                                <div className="row mb-2">
                                    <div className="col-12">
                                        <OrderStatusStepper
                                            status={order.status}
                                            stepIndex={order.stepIndex}
                                            estimatedWindow={order.estimatedWindow}
                                        />
                                    </div>
                                </div>

                                <div className="row">
                                    <div className="col-6 text-muted">Order Date</div>
                                    <div className="col-6 text-end">
                                        {dateTime(order.updatedAt ?? order.createdAt, ' ')}
                                    </div>
                                </div>
                            </div>

                            <div className="d-flex gap-2 justify-content-center mt-4">
                                <Link to="/pizzas" className="btn btn-danger">
                                    <i className="bi bi-list-ul" /> Order More
                                </Link>
                                <Link to="/" className="btn btn-outline-secondary">
                                    <i className="bi bi-house" /> Home
                                </Link>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </section>
    );
}

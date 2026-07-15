import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { orders as orderApi } from '../api';
import { ApiError } from '../api/client';
import { useAlerts } from '../context/AlertContext';
import { useApi, useSubmit } from '../hooks/useApi';
import { Spinner } from '../components/Guards';
import SubmitButton from '../components/SubmitButton';
import { dateTime, money } from '../lib/format';

/**
 * Editing an order inside its five-minute window.
 *
 * <p>The old page had to smuggle the unsaved address and phone through every
 * item-mutation form as hidden inputs, because each one redirected and re-rendered
 * the page from the database. Here the fields are just component state — they are
 * only sent when the user actually saves.</p>
 */
export default function EditOrder() {
    const { orderId } = useParams();
    const navigate = useNavigate();
    const { pushAlert, pushFromResponse } = useAlerts();
    const [pending, run] = useSubmit();

    const { data: order, setData: setOrder, loading, error } = useApi(
        () => orderApi.forEdit(orderId),
        [orderId]
    );

    const [details, setDetails] = useState({ deliveryAddress: '', phone: '' });
    const [errors, setErrors] = useState({});

    // Seed the form once the order arrives; later item edits must not clobber typing.
    useEffect(() => {
        if (order) {
            setDetails({ deliveryAddress: order.deliveryAddress ?? '', phone: order.phone ?? '' });
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [order?.id]);

    // The window closes server-side (409). Send the user back with the reason.
    useEffect(() => {
        if (error instanceof ApiError && error.status === 409) {
            pushAlert('danger', error.message);
            navigate('/orders/history');
        }
    }, [error, pushAlert, navigate]);

    async function mutate(action) {
        await run(async () => {
            try {
                const result = await action();
                setOrder(result.data);
                pushFromResponse(result);
            } catch (err) {
                if (err instanceof ApiError) pushAlert('danger', err.message);
            }
        });
    }

    function removeItem(itemId) {
        if (!window.confirm('Remove this item from the order?')) return;
        mutate(() => orderApi.removeItem(orderId, itemId));
    }

    async function save(event) {
        event.preventDefault();
        setErrors({});
        await run(async () => {
            try {
                pushFromResponse(await orderApi.updateDetails(orderId, details));
                navigate('/orders/history');
            } catch (err) {
                if (err instanceof ApiError) {
                    setErrors(err.fieldErrors);
                    if (Object.keys(err.fieldErrors).length === 0) pushAlert('danger', err.message);
                }
            }
        });
    }

    if (loading) return <Spinner />;
    if (!order) return null;

    return (
        <div className="container py-5">
            <div className="row justify-content-center">
                <div className="col-lg-10">
                    <div className="card shadow">
                        <div className="card-header bg-warning">
                            <h3 className="mb-0">Edit Order</h3>
                        </div>

                        <div className="card-body">
                            <div className="mb-4">
                                <h5>
                                    Order Number : <span>{order.orderNumber}</span>
                                </h5>
                                <p className="mb-1">
                                    <strong>Status:</strong> <span>{order.status}</span>
                                </p>
                                <p className="mb-0">
                                    <strong>Order Date:</strong> <span>{dateTime(order.createdAt, ' ')}</span>
                                </p>
                            </div>

                            <table className="table table-bordered table-hover align-middle">
                                <thead className="table-dark">
                                    <tr>
                                        <th>Item</th>
                                        <th className="text-center">Quantity</th>
                                        <th className="text-end">Price</th>
                                        <th className="text-end">Line Total</th>
                                        <th className="text-center">Action</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {order.items.map((item) => (
                                        <tr key={item.id}>
                                            <td>
                                                {item.itemType === 'DRINK' ? item.drinkName : item.pizzaName}
                                                {item.itemType === 'DRINK' && (
                                                    <span className="ms-2 badge bg-primary bg-opacity-10 text-primary border border-primary border-opacity-25">
                                                         Drink
                                                    </span>
                                                )}
                                            </td>
                                            <td className="text-center">
                                                <div className="d-flex justify-content-center align-items-center gap-2">
                                                    <button
                                                        type="button"
                                                        className="btn btn-outline-secondary btn-sm"
                                                        disabled={pending}
                                                        onClick={() =>
                                                            mutate(() => orderApi.decreaseItem(orderId, item.id))
                                                        }
                                                    >
                                                        −
                                                    </button>
                                                    <span className="fw-bold">{item.quantity}</span>
                                                    <button
                                                        type="button"
                                                        className="btn btn-outline-success btn-sm"
                                                        disabled={pending}
                                                        onClick={() =>
                                                            mutate(() => orderApi.increaseItem(orderId, item.id))
                                                        }
                                                    >
                                                        +
                                                    </button>
                                                </div>
                                            </td>
                                            <td className="text-end">{money(item.price)}</td>
                                            <td className="text-end">{money(item.lineTotal)}</td>
                                            <td className="text-center">
                                                <button
                                                    type="button"
                                                    className="btn btn-outline-danger btn-sm"
                                                    disabled={pending}
                                                    onClick={() => removeItem(item.id)}
                                                >
                                                    Remove
                                                </button>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>

                            <form onSubmit={save}>
                                <div className="row mt-4">
                                    <div className="col-md-6">
                                        <label className="form-label" htmlFor="deliveryAddress">
                                            Delivery Address
                                        </label>
                                        <textarea
                                            className={`form-control ${errors.deliveryAddress ? 'is-invalid' : ''}`}
                                            id="deliveryAddress"
                                            rows="3"
                                            value={details.deliveryAddress}
                                            onChange={(event) =>
                                                setDetails((d) => ({ ...d, deliveryAddress: event.target.value }))
                                            }
                                        />
                                        <div className="invalid-feedback">{errors.deliveryAddress}</div>
                                    </div>

                                    <div className="col-md-6">
                                        <label className="form-label" htmlFor="phone">
                                            Phone
                                        </label>
                                        <input
                                            type="text"
                                            className={`form-control ${errors.phone ? 'is-invalid' : ''}`}
                                            id="phone"
                                            value={details.phone}
                                            onChange={(event) =>
                                                setDetails((d) => ({ ...d, phone: event.target.value }))
                                            }
                                        />
                                        <div className="invalid-feedback">{errors.phone}</div>
                                    </div>
                                </div>

                                <div className="row justify-content-end">
                                    <div className="col-md-5">
                                        <table className="table">
                                            <tbody>
                                                <tr>
                                                    <th>Subtotal</th>
                                                    <td className="text-end">{money(order.subtotal)}</td>
                                                </tr>
                                                <tr>
                                                    <th>Discount</th>
                                                    <td className="text-end text-success">
                                                        - {money(order.discountAmount)}
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <th>Tax</th>
                                                    <td className="text-end">{money(order.tax)}</td>
                                                </tr>
                                                <tr className="table-warning">
                                                    <th>Grand Total</th>
                                                    <th className="text-end">{money(order.totalAmount)}</th>
                                                </tr>
                                            </tbody>
                                        </table>
                                    </div>
                                </div>

                                <div className="d-flex justify-content-between mt-4">
                                    <Link to="/orders/history" className="btn btn-secondary">
                                        Back
                                    </Link>

                                    <div className="mt-4 d-flex gap-2">
                                        <Link to={`/pizzas?orderId=${order.id}`} className="btn btn-primary">
                                            🍕 Add More Pizzas
                                        </Link>
                                        <Link to={`/drinks?orderId=${order.id}`} className="btn btn-info">
                                             Add Drinks
                                        </Link>
                                        <SubmitButton pending={pending} className="btn btn-success">
                                            Update Order
                                        </SubmitButton>
                                    </div>
                                </div>
                            </form>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}

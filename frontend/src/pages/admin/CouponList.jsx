import { Link } from 'react-router-dom';
import { admin as adminApi } from '../../api';
import { ApiError } from '../../api/client';
import { useAlerts } from '../../context/AlertContext';
import { useApi, useSubmit } from '../../hooks/useApi';
import { dateTime } from '../../lib/format';

export default function CouponList() {
    const { data: coupons, loading, reload } = useApi(() => adminApi.coupons.list(), []);
    const { pushAlert, pushFromResponse } = useAlerts();
    const [pending, run] = useSubmit();

    async function remove(id) {
        if (!window.confirm('Are you sure you want to delete this coupon?')) return;
        await run(async () => {
            try {
                pushFromResponse(await adminApi.coupons.remove(id));
                await reload();
            } catch (error) {
                if (error instanceof ApiError) pushAlert('danger', error.message);
            }
        });
    }

    const list = coupons ?? [];

    return (
        <>
            <div className="d-flex justify-content-between align-items-center flex-wrap mb-4">
                <h2 className="fw-bold mb-0">
                    <i className="bi bi-ticket-perforated" /> Manage Coupons
                </h2>
                <Link to="/admin/coupons/add" className="btn btn-danger">
                    <i className="bi bi-plus-circle" /> Add Coupon
                </Link>
            </div>

            {!loading && list.length === 0 ? (
                <div className="text-center text-muted py-5">
                    <i className="bi bi-inbox fs-1" />
                    <p className="mt-2">No coupons found.</p>
                </div>
            ) : (
                <div className="table-responsive">
                    <table className="table table-hover align-middle bg-white shadow-sm rounded">
                        <thead className="table-dark">
                            <tr>
                                <th>Code</th>
                                <th>Discount</th>
                                <th>Status</th>
                                <th>Created</th>
                                <th className="text-end">Actions</th>
                            </tr>
                        </thead>
                        <tbody>
                            {list.map((coupon) => (
                                <tr key={coupon.id}>
                                    <td className="fw-semibold">{coupon.couponCode}</td>
                                    <td>{coupon.discountPercentage}% off</td>
                                    <td>
                                        <span
                                            className={`badge ${coupon.active ? 'bg-success' : 'bg-secondary'}`}
                                        >
                                            {coupon.active ? 'Active' : 'Inactive'}
                                        </span>
                                    </td>
                                    <td>{dateTime(coupon.createdAt)}</td>
                                    <td className="text-end">
                                        <Link
                                            to={`/admin/coupons/edit/${coupon.id}`}
                                            className="btn btn-sm btn-outline-primary"
                                        >
                                            <i className="bi bi-pencil" /> Edit
                                        </Link>{' '}
                                        <button
                                            type="button"
                                            className="btn btn-sm btn-outline-danger"
                                            disabled={pending}
                                            onClick={() => remove(coupon.id)}
                                        >
                                            <i className="bi bi-trash" /> Delete
                                        </button>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}
        </>
    );
}

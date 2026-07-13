import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { admin as adminApi } from '../../api';
import { ApiError } from '../../api/client';
import { useAlerts } from '../../context/AlertContext';
import { useApi, useSubmit } from '../../hooks/useApi';
import StatusBadge from '../../components/StatusBadge';
import SubmitButton from '../../components/SubmitButton';
import { dateTime, money } from '../../lib/format';

const STATUSES = ['PLACED', 'PROCESSING', 'OUT_FOR_DELIVERY', 'DELIVERED', 'CANCELLED'];

export default function AdminOrderList() {
    const [params, setParams] = useSearchParams();
    const { pushAlert, pushFromResponse } = useAlerts();
    const [pending, run] = useSubmit();

    const search = params.get('search') ?? '';
    const status = params.get('status') ?? '';
    const sort = params.get('sort') ?? '';

    const { data: orders, loading, reload } = useApi(
        () => adminApi.orders.list({ search, status, sort }),
        [search, status, sort]
    );

    const [selected, setSelected] = useState(new Set());
    const [targetStatus, setTargetStatus] = useState('PROCESSING');

    const list = orders ?? [];
    const allSelected = list.length > 0 && selected.size === list.length;

    function applyFilters(event) {
        event.preventDefault();
        const form = new FormData(event.currentTarget);
        const next = {};
        ['search', 'status', 'sort'].forEach((key) => {
            const value = form.get(key);
            if (value) next[key] = value;
        });
        setParams(next);
        setSelected(new Set());
    }

    function toggle(id) {
        setSelected((current) => {
            const next = new Set(current);
            if (next.has(id)) next.delete(id);
            else next.add(id);
            return next;
        });
    }

    function toggleAll() {
        setSelected(allSelected ? new Set() : new Set(list.map((order) => order.id)));
    }

    async function applyBulkStatus() {
        if (selected.size === 0) {
            pushAlert('warning', 'Select at least one order first.');
            return;
        }
        await run(async () => {
            try {
                // Orders whose current status forbids the transition are skipped, and the
                // server answers with a warning rather than a success in that case.
                pushFromResponse(await adminApi.orders.bulkStatus([...selected], targetStatus));
                setSelected(new Set());
                await reload();
            } catch (error) {
                if (error instanceof ApiError) pushAlert('danger', error.message);
            }
        });
    }

    return (
        <>
            <div className="d-flex justify-content-between align-items-center flex-wrap mb-4">
                <h2 className="fw-bold mb-0">
                    <i className="bi bi-receipt" /> Manage Orders
                </h2>
            </div>

            <form onSubmit={applyFilters} className="row g-2 align-items-end mb-3 p-3 bg-light rounded">
                <div className="col-12 col-md-5">
                    <label className="form-label small fw-semibold" htmlFor="search">
                        Search by order # or customer
                    </label>
                    <div className="input-group">
                        <span className="input-group-text">
                            <i className="bi bi-search" />
                        </span>
                        <input
                            type="text"
                            id="search"
                            name="search"
                            className="form-control"
                            defaultValue={search}
                            placeholder="e.g. ORD-1024 or Priya"
                        />
                    </div>
                </div>
                <div className="col-6 col-md-3">
                    <label className="form-label small fw-semibold" htmlFor="status">
                        Status
                    </label>
                    <select id="status" name="status" className="form-select" defaultValue={status}>
                        <option value="">All statuses</option>
                        {STATUSES.map((s) => (
                            <option key={s} value={s}>
                                {s.replace(/_/g, ' ')}
                            </option>
                        ))}
                    </select>
                </div>
                <div className="col-6 col-md-2">
                    <label className="form-label small fw-semibold" htmlFor="sort">
                        Sort by
                    </label>
                    <select id="sort" name="sort" className="form-select" defaultValue={sort}>
                        <option value="">Newest first</option>
                        <option value="oldest">Oldest first</option>
                        <option value="totalAsc">Total: Low &rarr; High</option>
                        <option value="totalDesc">Total: High &rarr; Low</option>
                    </select>
                </div>
                <div className="col-12 col-md-2 d-grid">
                    <button type="submit" className="btn btn-danger">
                        <i className="bi bi-funnel" /> Filter
                    </button>
                </div>
            </form>

            {!loading && list.length === 0 ? (
                <div className="text-center text-muted py-5">
                    <i className="bi bi-inbox fs-1" />
                    <p className="mt-2">No orders found.</p>
                </div>
            ) : (
                <>
                    <div className="d-flex align-items-end gap-2 mb-3 p-3 bg-light rounded flex-wrap">
                        <div>
                            <label className="form-label small fw-semibold" htmlFor="targetStatus">
                                Set selected orders to
                            </label>
                            <select
                                id="targetStatus"
                                className="form-select"
                                value={targetStatus}
                                onChange={(event) => setTargetStatus(event.target.value)}
                            >
                                {STATUSES.map((s) => (
                                    <option key={s} value={s}>
                                        {s.replace(/_/g, ' ')}
                                    </option>
                                ))}
                            </select>
                        </div>
                        <SubmitButton
                            type="button"
                            pending={pending}
                            className="btn btn-warning"
                            onClick={applyBulkStatus}
                        >
                            Apply to Selected
                        </SubmitButton>
                        <span className="text-muted small ms-2">{selected.size} selected</span>
                    </div>

                    <div className="table-responsive">
                        <table className="table table-hover align-middle bg-white shadow-sm rounded">
                            <thead className="table-dark">
                                <tr>
                                    <th>
                                        <input
                                            type="checkbox"
                                            className="form-check-input"
                                            aria-label="Select all orders"
                                            checked={allSelected}
                                            onChange={toggleAll}
                                        />
                                    </th>
                                    <th>Order #</th>
                                    <th>Customer</th>
                                    <th>Items</th>
                                    <th>Total</th>
                                    <th>Status</th>
                                    <th>Placed On</th>
                                    <th className="text-end">Actions</th>
                                </tr>
                            </thead>
                            <tbody>
                                {list.map((order) => (
                                    <tr key={order.id}>
                                        <td>
                                            <input
                                                type="checkbox"
                                                className="form-check-input"
                                                aria-label={`Select order ${order.orderNumber}`}
                                                checked={selected.has(order.id)}
                                                onChange={() => toggle(order.id)}
                                            />
                                        </td>
                                        <td className="fw-semibold">{order.orderNumber}</td>
                                        <td>{order.customer?.fullName}</td>
                                        <td>{order.items.length}</td>
                                        <td>{money(order.totalAmount)}</td>
                                        <td>
                                            <StatusBadge status={order.status} />
                                        </td>
                                        <td>{dateTime(order.createdAt)}</td>
                                        <td className="text-end">
                                            <Link
                                                to={`/admin/orders/${order.id}`}
                                                className="btn btn-sm btn-outline-primary"
                                            >
                                                <i className="bi bi-eye" /> View
                                            </Link>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </>
            )}
        </>
    );
}

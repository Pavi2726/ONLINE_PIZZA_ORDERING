import { Link, useSearchParams } from 'react-router-dom';
import { admin as adminApi } from '../../api';
import { useApi } from '../../hooks/useApi';
import { dateTime } from '../../lib/format';

export default function CustomerList() {
    const [params, setParams] = useSearchParams();

    const search = params.get('search') ?? '';
    const sort = params.get('sort') ?? '';

    const { data: customers, loading } = useApi(
        () => adminApi.customers.list({ search, sort }),
        [search, sort]
    );

    function applyFilters(event) {
        event.preventDefault();
        const form = new FormData(event.currentTarget);
        const next = {};
        ['search', 'sort'].forEach((key) => {
            const value = form.get(key);
            if (value) next[key] = value;
        });
        setParams(next);
    }

    const list = customers ?? [];

    return (
        <>
            <div className="d-flex justify-content-between align-items-center flex-wrap mb-4">
                <h2 className="fw-bold mb-0">
                    <i className="bi bi-people" /> Manage Customers
                </h2>
            </div>

            <form onSubmit={applyFilters} className="row g-2 align-items-end mb-3 p-3 bg-light rounded">
                <div className="col-12 col-md-6">
                    <label className="form-label small fw-semibold" htmlFor="search">
                        Search by name or email
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
                            placeholder="e.g. Priya or priya@example.com"
                        />
                    </div>
                </div>
                <div className="col-8 col-md-4">
                    <label className="form-label small fw-semibold" htmlFor="sort">
                        Sort by
                    </label>
                    <select id="sort" name="sort" className="form-select" defaultValue={sort}>
                        <option value="">Default</option>
                        <option value="nameAsc">Name A &rarr; Z</option>
                        <option value="nameDesc">Name Z &rarr; A</option>
                        <option value="registeredAsc">Registered: Oldest first</option>
                        <option value="registeredDesc">Registered: Newest first</option>
                    </select>
                </div>
                <div className="col-4 col-md-2 d-grid">
                    <button type="submit" className="btn btn-danger">
                        <i className="bi bi-funnel" /> Filter
                    </button>
                </div>
            </form>

            {!loading && list.length === 0 ? (
                <div className="text-center text-muted py-5">
                    <i className="bi bi-inbox fs-1" />
                    <p className="mt-2">No customers found.</p>
                </div>
            ) : (
                <div className="table-responsive">
                    <table className="table table-hover align-middle bg-white shadow-sm rounded">
                        <thead className="table-dark">
                            <tr>
                                <th>ID</th>
                                <th>Name</th>
                                <th>Email</th>
                                <th>Phone</th>
                                <th>Address</th>
                                <th>Registered</th>
                                <th className="text-end">Actions</th>
                            </tr>
                        </thead>
                        <tbody>
                            {list.map((customer) => (
                                <tr key={customer.id}>
                                    <td>{customer.id}</td>
                                    <td className="fw-semibold">{customer.fullName}</td>
                                    <td>{customer.email}</td>
                                    <td>{customer.phone}</td>
                                    <td>{customer.address}</td>
                                    <td>{dateTime(customer.createdAt)}</td>
                                    <td className="text-end">
                                        <Link
                                            to={`/admin/customers/edit/${customer.id}`}
                                            className="btn btn-sm btn-outline-primary"
                                        >
                                            <i className="bi bi-pencil" /> Edit
                                        </Link>
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

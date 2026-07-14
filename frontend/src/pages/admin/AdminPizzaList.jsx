import { Link, useSearchParams } from 'react-router-dom';
import { admin as adminApi } from '../../api';
import { ApiError } from '../../api/client';
import { useAlerts } from '../../context/AlertContext';
import { useApi, useSubmit } from '../../hooks/useApi';
import { usePagination } from '../../hooks/usePagination';
import Pagination from '../../components/Pagination';
import { money, onImageError } from '../../lib/format';

export default function AdminPizzaList() {
    const [params, setParams] = useSearchParams();
    const { pushAlert, pushFromResponse } = useAlerts();
    const [pending, run] = useSubmit();

    const search = params.get('search') ?? '';
    const category = params.get('category') ?? '';

    const { data, loading, reload } = useApi(
        () => adminApi.pizzas.list({ search, category }),
        [search, category]
    );

    const list = data?.pizzas ?? [];
    const categories = data?.categories ?? [];
    const { pageItems, page, pageCount, setPage } = usePagination(list);

    function applyFilters(event) {
        event.preventDefault();
        const form = new FormData(event.currentTarget);
        const next = {};
        ['search', 'category'].forEach((key) => {
            const value = form.get(key);
            if (value) next[key] = value;
        });
        setParams(next);
    }

    async function remove(id) {
        if (
            !window.confirm(
                'Are you sure you want to delete this pizza? This will also remove its image.'
            )
        ) {
            return;
        }
        await run(async () => {
            try {
                pushFromResponse(await adminApi.pizzas.remove(id));
                await reload();
            } catch (error) {
                // A pizza referenced by existing orders cannot be deleted — a 409.
                if (error instanceof ApiError) pushAlert('danger', error.message);
            }
        });
    }

    return (
        <>
            <div className="d-flex justify-content-between align-items-center flex-wrap mb-4">
                <h2 className="fw-bold mb-0">
                    <i className="bi bi-card-list" /> Manage Pizzas
                </h2>
                <Link to="/admin/pizzas/add" className="btn btn-danger">
                    <i className="bi bi-plus-circle" /> Add Pizza
                </Link>
            </div>

            <form onSubmit={applyFilters} className="row g-2 align-items-end mb-3 p-3 bg-light rounded">
                <div className="col-12 col-md-6">
                    <label className="form-label small fw-semibold" htmlFor="search">
                        Search by name
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
                            placeholder="e.g. Margherita"
                        />
                    </div>
                </div>
                <div className="col-8 col-md-4">
                    <label className="form-label small fw-semibold" htmlFor="category">
                        Category
                    </label>
                    <select id="category" name="category" className="form-select" defaultValue={category}>
                        <option value="">All categories</option>
                        {categories.map((cat) => (
                            <option key={cat} value={cat}>
                                {cat}
                            </option>
                        ))}
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
                    <p className="mt-2">No pizzas found.</p>
                </div>
            ) : (
                <div className="table-responsive">
                    <table className="table table-hover align-middle bg-white shadow-sm rounded">
                        <thead className="table-dark">
                            <tr>
                                <th>Image</th>
                                <th>Name</th>
                                <th>Category</th>
                                <th>Price</th>
                                <th>Status</th>
                                <th className="text-end">Actions</th>
                            </tr>
                        </thead>
                        <tbody>
                            {pageItems.map((pizza) => (
                                <tr key={pizza.id}>
                                    <td>
                                        <img
                                            src={pizza.imageUrl}
                                            className="admin-thumb"
                                            alt={pizza.name}
                                            onError={onImageError}
                                        />
                                    </td>
                                    <td className="fw-semibold">{pizza.name}</td>
                                    <td>
                                        <span className="badge bg-light text-dark border">{pizza.category}</span>
                                    </td>
                                    <td>{money(pizza.price)}</td>
                                    <td>
                                        <span
                                            className={`badge ${pizza.available ? 'bg-success' : 'bg-secondary'}`}
                                        >
                                            {pizza.available ? 'Available' : 'Out of Stock'}
                                        </span>
                                    </td>
                                    <td className="text-end">
                                        <Link
                                            to={`/admin/pizzas/edit/${pizza.id}`}
                                            className="btn btn-sm btn-outline-primary"
                                        >
                                            <i className="bi bi-pencil" /> Edit
                                        </Link>{' '}
                                        <button
                                            type="button"
                                            className="btn btn-sm btn-outline-danger"
                                            disabled={pending}
                                            onClick={() => remove(pizza.id)}
                                        >
                                            <i className="bi bi-trash" /> Delete
                                        </button>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>

                    <Pagination page={page} pageCount={pageCount} onChange={setPage} />
                </div>
            )}
        </>
    );
}

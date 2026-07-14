import { Link, useSearchParams } from 'react-router-dom';
import { admin as adminApi } from '../../api';
import { ApiError } from '../../api/client';
import { useAlerts } from '../../context/AlertContext';
import { useApi, useSubmit } from '../../hooks/useApi';
import { usePagination } from '../../hooks/usePagination';
import Pagination from '../../components/Pagination';
import { money, onImageError } from '../../lib/format';

export default function AdminDrinkList() {
    const [params, setParams] = useSearchParams();
    const { pushAlert, pushFromResponse } = useAlerts();
    const [pending, run] = useSubmit();

    const search = params.get('search') ?? '';
    const category = params.get('category') ?? '';

    const { data, loading, reload } = useApi(
        () => adminApi.drinks.list({ search, category }),
        [search, category]
    );

    const list = data?.drinks ?? [];
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

    async function remove(id, name) {
        if (!window.confirm(`Are you sure you want to delete "${name}"? This will also remove its image.`)) {
            return;
        }
        await run(async () => {
            try {
                pushFromResponse(await adminApi.drinks.remove(id));
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
                    🥤 Manage Drinks
                </h2>
                <Link to="/admin/drinks/add" className="btn btn-primary">
                    <i className="bi bi-plus-circle" /> Add Drink
                </Link>
            </div>

            <form onSubmit={applyFilters} className="row g-2 align-items-end mb-3 p-3 bg-light rounded">
                <div className="col-12 col-md-6">
                    <label className="form-label small fw-semibold" htmlFor="admin-drink-search">
                        Search by name
                    </label>
                    <div className="input-group">
                        <span className="input-group-text">
                            <i className="bi bi-search" />
                        </span>
                        <input
                            type="text"
                            id="admin-drink-search"
                            name="search"
                            className="form-control"
                            defaultValue={search}
                            placeholder="e.g. Coca-Cola"
                        />
                    </div>
                </div>
                <div className="col-8 col-md-4">
                    <label className="form-label small fw-semibold" htmlFor="admin-drink-category">
                        Category
                    </label>
                    <select id="admin-drink-category" name="category" className="form-select" defaultValue={category}>
                        <option value="">All categories</option>
                        {categories.map((cat) => (
                            <option key={cat} value={cat}>
                                {cat}
                            </option>
                        ))}
                    </select>
                </div>
                <div className="col-4 col-md-2 d-grid">
                    <button type="submit" className="btn btn-primary">
                        <i className="bi bi-funnel" /> Filter
                    </button>
                </div>
            </form>

            {!loading && list.length === 0 ? (
                <div className="text-center text-muted py-5">
                    <i className="bi bi-cup-straw fs-1" />
                    <p className="mt-2">No drinks found.</p>
                </div>
            ) : (
                <div className="table-responsive">
                    <table className="table table-hover align-middle bg-white shadow-sm rounded">
                        <thead className="table-dark">
                            <tr>
                                <th>Image</th>
                                <th>Name</th>
                                <th>Category</th>
                                <th>Size</th>
                                <th>Price</th>
                                <th>Status</th>
                                <th className="text-end">Actions</th>
                            </tr>
                        </thead>
                        <tbody>
                            {pageItems.map((drink) => (
                                <tr key={drink.id}>
                                    <td>
                                        <img
                                            src={drink.imageUrl}
                                            className="admin-thumb"
                                            alt={drink.name}
                                            onError={onImageError}
                                        />
                                    </td>
                                    <td className="fw-semibold">{drink.name}</td>
                                    <td>
                                        <span className="badge bg-light text-dark border">{drink.category}</span>
                                    </td>
                                    <td>
                                        {drink.size && (
                                            <span className="badge bg-secondary">{drink.size}</span>
                                        )}
                                    </td>
                                    <td>{money(drink.price)}</td>
                                    <td>
                                        <span
                                            className={`badge ${drink.available ? 'bg-success' : 'bg-secondary'}`}
                                        >
                                            {drink.available ? 'Available' : 'Out of Stock'}
                                        </span>
                                    </td>
                                    <td className="text-end">
                                        <Link
                                            to={`/admin/drinks/edit/${drink.id}`}
                                            className="btn btn-sm btn-outline-primary"
                                        >
                                            <i className="bi bi-pencil" /> Edit
                                        </Link>{' '}
                                        <button
                                            type="button"
                                            className="btn btn-sm btn-outline-danger"
                                            disabled={pending}
                                            onClick={() => remove(drink.id, drink.name)}
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

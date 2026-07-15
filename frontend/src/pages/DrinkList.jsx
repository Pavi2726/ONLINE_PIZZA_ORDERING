import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { cart as cartApi, drinks as drinkApi, orders as orderApi } from '../api';
import { ApiError } from '../api/client';
import { useAlerts } from '../context/AlertContext';
import { useSession } from '../context/SessionContext';
import { useApi, useSubmit } from '../hooks/useApi';
import { usePagination } from '../hooks/usePagination';
import Pagination from '../components/Pagination';
import { money, onImageError } from '../lib/format';

const SIZE_BADGE = {
    SMALL: 'bg-info text-dark',
    MEDIUM: 'bg-warning text-dark',
    LARGE: 'bg-danger',
};

/**
 * Customer drink catalogue. Supports browse, search, category filter, and sort.
 * With ?orderId= it adds the drink directly to an in-flight order edit instead of the cart.
 */
export default function DrinkList() {
    const [params, setParams] = useSearchParams();
    const navigate = useNavigate();
    const { customer, setCartItemCount } = useSession();
    const { pushAlert, pushFromResponse } = useAlerts();
    const [pending, run] = useSubmit();
    const [busyId, setBusyId] = useState(null);

    const search = params.get('search') ?? '';
    const category = params.get('category') ?? '';
    const sort = params.get('sort') ?? '';
    const orderId = params.get('orderId');

    const { data, loading } = useApi(
        () => drinkApi.list({ search, category, sort }),
        [search, category, sort]
    );

    const list = data?.drinks ?? [];
    const categories = data?.categories ?? [];
    const { pageItems, page, pageCount, setPage } = usePagination(list);

    function applyFilters(event) {
        event.preventDefault();
        const form = new FormData(event.currentTarget);
        const next = {};
        ['search', 'category', 'sort'].forEach((key) => {
            const value = form.get(key);
            if (value) next[key] = value;
        });
        if (orderId) next.orderId = orderId;
        setParams(next);
    }

    async function addToCart(drinkId) {
        if (!customer) {
            navigate('/login');
            return;
        }
        setBusyId(drinkId);
        await run(async () => {
            try {
                const result = await cartApi.addDrink(drinkId);
                setCartItemCount(result.data.itemCount);
                pushFromResponse(result);
            } catch (error) {
                if (error instanceof ApiError) pushAlert('danger', error.message);
            } finally {
                setBusyId(null);
            }
        });
    }

    async function addToOrder(drinkId) {
        setBusyId(drinkId);
        await run(async () => {
            try {
                const result = await orderApi.addDrink(orderId, drinkId, 1);
                pushFromResponse(result);
                navigate(`/orders/edit/${orderId}`);
            } catch (error) {
                if (error instanceof ApiError) pushAlert('danger', error.message);
            } finally {
                setBusyId(null);
            }
        });
    }

    return (
        <section className="container my-5">
            <div className="mb-4">
                <h2 className="fw-bold mb-0">
                     Drinks Menu
                </h2>
                <p className="text-muted mt-1 mb-0">Refreshing beverages to go with your order</p>
            </div>

            <form
                onSubmit={applyFilters}
                className="row g-2 align-items-end mb-4 p-3 bg-light rounded"
            >
                <div className="col-12 col-md-4">
                    <label className="form-label small fw-semibold" htmlFor="drink-search">
                        Search by name
                    </label>
                    <div className="input-group">
                        <span className="input-group-text">
                            <i className="bi bi-search" />
                        </span>
                        <input
                            type="text"
                            id="drink-search"
                            name="search"
                            className="form-control"
                            defaultValue={search}
                            placeholder="e.g. Coca-Cola"
                        />
                    </div>
                </div>
                <div className="col-6 col-md-3">
                    <label className="form-label small fw-semibold" htmlFor="drink-category">
                        Category
                    </label>
                    <select id="drink-category" name="category" className="form-select" defaultValue={category}>
                        <option value="">All categories</option>
                        {categories.map((cat) => (
                            <option key={cat} value={cat}>
                                {cat}
                            </option>
                        ))}
                    </select>
                </div>
                <div className="col-6 col-md-3">
                    <label className="form-label small fw-semibold" htmlFor="drink-sort">
                        Sort by price
                    </label>
                    <select id="drink-sort" name="sort" className="form-select" defaultValue={sort}>
                        <option value="">Default</option>
                        <option value="priceAsc">Low → High</option>
                        <option value="priceDesc">High → Low</option>
                    </select>
                </div>
                <div className="col-12 col-md-2 d-grid">
                    <button type="submit" className="btn btn-primary">
                        <i className="bi bi-funnel" /> Apply
                    </button>
                </div>
            </form>

            {!loading && list.length === 0 && (
                <div className="text-center text-muted py-5">
                    <i className="bi bi-cup-straw fs-1" />
                    <p className="mt-2">No drinks match your search.</p>
                </div>
            )}

            <div className="row g-4">
                {pageItems.map((drink) => (
                    <div className="col-12 col-sm-6 col-lg-4 col-xl-3" key={drink.id}>
                        <div className="card h-100 pizza-card shadow-sm">
                            <div className="position-relative">
                                <img
                                    src={drink.imageUrl}
                                    className="card-img-top pizza-img"
                                    alt={drink.name}
                                    onError={onImageError}
                                />
                                <span
                                    className={`badge position-absolute top-0 end-0 m-2 ${
                                        drink.available ? 'bg-success' : 'bg-secondary'
                                    }`}
                                >
                                    {drink.available ? 'Available' : 'Out of Stock'}
                                </span>
                            </div>
                            <div className="card-body d-flex flex-column">
                                <div className="d-flex justify-content-between align-items-start mb-1">
                                    <h5 className="card-title fw-semibold mb-0">{drink.name}</h5>
                                    <span className="badge bg-light text-dark border">{drink.category}</span>
                                </div>
                                {drink.size && (
                                    <div className="mb-1">
                                        <span className={`badge ${SIZE_BADGE[drink.size] ?? 'bg-secondary'} me-1`}>
                                            {drink.size}
                                        </span>
                                    </div>
                                )}
                                <p className="card-text small text-muted flex-grow-1">{drink.description}</p>
                                <div className="d-flex justify-content-between align-items-center mt-2">
                                    <span className="fs-5 fw-bold text-primary">{money(drink.price)}</span>

                                    {!drink.available ? (
                                        <button className="btn btn-sm btn-secondary" disabled>
                                            Out of Stock
                                        </button>
                                    ) : orderId ? (
                                        <button
                                            type="button"
                                            className="btn btn-sm btn-primary"
                                            disabled={pending && busyId === drink.id}
                                            onClick={() => addToOrder(drink.id)}
                                        >
                                             Add to This Order
                                        </button>
                                    ) : (
                                        <button
                                            type="button"
                                            className="btn btn-sm btn-warning"
                                            disabled={pending && busyId === drink.id}
                                            onClick={() => addToCart(drink.id)}
                                        >
                                            <i className="bi bi-cart-plus" /> Add to Cart
                                        </button>
                                    )}
                                </div>
                            </div>
                        </div>
                    </div>
                ))}
            </div>

            <Pagination page={page} pageCount={pageCount} onChange={setPage} />
        </section>
    );
}

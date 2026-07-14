import { Link } from 'react-router-dom';
import { admin as adminApi } from '../../api';
import { useApi } from '../../hooks/useApi';

const CARDS = [
    { key: 'totalPizzas', label: 'Total Pizzas', bg: 'bg-primary', icon: 'bi-pizza' },
    { key: 'availablePizzas', label: 'Available', bg: 'bg-success', icon: 'bi-check-circle' },
    { key: 'outOfStockPizzas', label: 'Out of Stock', bg: 'bg-secondary', icon: 'bi-x-circle' },
];

export default function Dashboard() {
    const { data } = useApi(() => adminApi.dashboard(), []);

    return (
        <>
            <h2 className="fw-bold mb-4">
                <i className="bi bi-speedometer2" /> Dashboard
            </h2>

            <div className="row g-4 mb-4">
                {CARDS.map((card) => (
                    <div className="col-12 col-md-4" key={card.key}>
                        <div className={`card stat-card border-0 shadow-sm text-white ${card.bg}`}>
                            <div className="card-body d-flex justify-content-between align-items-center">
                                <div>
                                    <div className="small text-uppercase">{card.label}</div>
                                    <div className="display-6 fw-bold">{data?.[card.key] ?? 0}</div>
                                </div>
                                <i className={`bi ${card.icon} fs-1 opacity-50`} />
                            </div>
                        </div>
                    </div>
                ))}
            </div>

            <h5 className="fw-semibold mb-3">Quick Actions</h5>
            <div className="d-flex flex-wrap gap-2">
                <Link to="/admin/pizzas/add" className="btn btn-danger">
                    <i className="bi bi-plus-circle" /> Add Pizza
                </Link>
                <Link to="/admin/pizzas" className="btn btn-outline-danger">
                    <i className="bi bi-card-list" /> View Pizza List
                </Link>
            </div>
        </>
    );
}

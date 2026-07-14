// Was repeated inline across order-history, order-success and the admin views.
const BADGES = {
    PLACED: { className: 'badge bg-warning text-dark', label: 'PLACED' },
    PROCESSING: { className: 'badge bg-info text-dark', label: 'PROCESSING' },
    OUT_FOR_DELIVERY: { className: 'badge bg-primary', label: 'OUT FOR DELIVERY' },
    DELIVERED: { className: 'badge bg-success', label: 'DELIVERED' },
    CANCELLED: { className: 'badge bg-danger', label: 'CANCELLED' },
};

export default function StatusBadge({ status }) {
    const badge = BADGES[status] ?? { className: 'badge bg-secondary', label: status };
    return <span className={badge.className}>{badge.label}</span>;
}

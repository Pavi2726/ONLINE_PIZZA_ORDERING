import { useEffect, useState } from 'react';
import { useAlerts } from '../context/AlertContext';

const ICONS = {
    success: 'bi-check-circle-fill',
    warning: 'bi-exclamation-circle-fill',
    danger: 'bi-exclamation-triangle-fill',
};

const AUTO_DISMISS_MS = 5000;
const FADE_MS = 150; // Bootstrap's .fade transition.

/**
 * Note the close button is wired to React rather than data-bs-dismiss="alert":
 * Bootstrap's alert plugin removes the element from the DOM itself, which React
 * still believes it owns, and the next render throws.
 */
function Alert({ alert, onDismiss }) {
    const [visible, setVisible] = useState(true);

    useEffect(() => {
        const hide = setTimeout(() => setVisible(false), AUTO_DISMISS_MS);
        const remove = setTimeout(() => onDismiss(alert.id), AUTO_DISMISS_MS + FADE_MS);
        return () => {
            clearTimeout(hide);
            clearTimeout(remove);
        };
    }, [alert.id, onDismiss]);

    return (
        <div
            className={`alert alert-${alert.type} alert-dismissible fade ${visible ? 'show' : ''}`}
            role="alert"
        >
            <i className={`bi ${ICONS[alert.type] ?? ICONS.success}`} /> <span>{alert.message}</span>
            <button
                type="button"
                className="btn-close"
                aria-label="Close"
                onClick={() => onDismiss(alert.id)}
            />
        </div>
    );
}

/** `bare` drops the .container wrapper — the admin shell already sits inside one. */
export default function AlertStack({ bare = false }) {
    const { alerts, dismiss } = useAlerts();

    if (alerts.length === 0) return bare ? null : <div className="container mt-3" />;

    const stack = alerts.map((alert) => <Alert key={alert.id} alert={alert} onDismiss={dismiss} />);
    return bare ? <>{stack}</> : <div className="container mt-3">{stack}</div>;
}

import { createContext, useCallback, useContext, useMemo, useState } from 'react';

/**
 * Successor to the server's flash attributes. Mutations used to redirect with a
 * successMessage/warningMessage/errorMessage; now the API returns {message,
 * messageType} and the client queues it here.
 */
const AlertContext = createContext(null);

let nextId = 0;

export function AlertProvider({ children }) {
    const [alerts, setAlerts] = useState([]);

    const dismiss = useCallback((id) => {
        setAlerts((current) => current.filter((alert) => alert.id !== id));
    }, []);

    const pushAlert = useCallback((type, message) => {
        if (!message) return;
        setAlerts((current) => [...current, { id: nextId++, type, message }]);
    }, []);

    /** Maps the API's messageType onto the Bootstrap contextual class. */
    const pushFromResponse = useCallback(
        (payload) => {
            if (!payload?.message) return;
            const type = payload.messageType === 'error' ? 'danger' : payload.messageType || 'success';
            pushAlert(type, payload.message);
        },
        [pushAlert]
    );

    const value = useMemo(
        () => ({ alerts, pushAlert, pushFromResponse, dismiss }),
        [alerts, pushAlert, pushFromResponse, dismiss]
    );

    return <AlertContext.Provider value={value}>{children}</AlertContext.Provider>;
}

export function useAlerts() {
    const context = useContext(AlertContext);
    if (!context) throw new Error('useAlerts must be used inside <AlertProvider>');
    return context;
}

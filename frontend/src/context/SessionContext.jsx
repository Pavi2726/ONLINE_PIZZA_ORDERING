import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { get } from '../api/client';

/**
 * Successor to GlobalModelAdvice: the logged-in customer, the logged-in admin, and
 * the navbar's cart count, available to every screen.
 */
const SessionContext = createContext(null);

export function SessionProvider({ children }) {
    const [session, setSession] = useState({ customer: null, admin: null, cartItemCount: 0 });
    // Guards the first paint. Without it the navbar renders logged-out for a frame
    // and the route guards bounce an authenticated user to /login on every refresh.
    const [loading, setLoading] = useState(true);

    const refresh = useCallback(async () => {
        try {
            setSession(await get('/me'));
        } catch {
            setSession({ customer: null, admin: null, cartItemCount: 0 });
        }
    }, []);

    useEffect(() => {
        refresh().finally(() => setLoading(false));
    }, [refresh]);

    const setCustomer = useCallback((customer, cartItemCount = 0) => {
        setSession((current) => ({ ...current, customer, cartItemCount }));
    }, []);

    const setAdmin = useCallback((admin) => {
        setSession((current) => ({ ...current, admin }));
    }, []);

    /** Cart mutations return the new count, so the badge updates without a refetch. */
    const setCartItemCount = useCallback((cartItemCount) => {
        setSession((current) => ({ ...current, cartItemCount }));
    }, []);

    const value = useMemo(
        () => ({ ...session, loading, refresh, setCustomer, setAdmin, setCartItemCount }),
        [session, loading, refresh, setCustomer, setAdmin, setCartItemCount]
    );

    return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession() {
    const context = useContext(SessionContext);
    if (!context) throw new Error('useSession must be used inside <SessionProvider>');
    return context;
}

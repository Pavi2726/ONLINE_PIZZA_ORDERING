import { useCallback, useEffect, useState } from 'react';

const STORAGE_KEY = 'pizza-theme';

/**
 * Dark mode, driven exactly as before: a data-bs-theme attribute on <html> plus a
 * localStorage preference. The key is unchanged, so a preference set on the old
 * server-rendered app carries over. The initial value is read back off the
 * attribute, which the inline script in index.html has already set pre-paint.
 */
export function useTheme() {
    const [theme, setTheme] = useState(
        () => document.documentElement.getAttribute('data-bs-theme') || 'light'
    );

    useEffect(() => {
        document.documentElement.setAttribute('data-bs-theme', theme);
        localStorage.setItem(STORAGE_KEY, theme);
    }, [theme]);

    const toggleTheme = useCallback(() => {
        setTheme((current) => (current === 'dark' ? 'light' : 'dark'));
    }, []);

    return { theme, toggleTheme };
}

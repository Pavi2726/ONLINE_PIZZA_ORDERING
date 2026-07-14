import { useTheme } from '../hooks/useTheme';

export default function ThemeToggle() {
    const { theme, toggleTheme } = useTheme();

    return (
        <button
            type="button"
            className="btn btn-sm btn-outline-light"
            aria-label="Toggle dark mode"
            onClick={toggleTheme}
        >
            <i className={theme === 'dark' ? 'bi bi-sun-fill' : 'bi bi-moon-stars'} />
        </button>
    );
}

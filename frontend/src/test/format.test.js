import { describe, expect, it } from 'vitest';
import { abbreviate, dateTime, money } from '../lib/format';

describe('money', () => {
    // The whole point: Jackson serializes BigDecimal 12.50 as 12.5, so a raw render
    // would show "₹12.5".
    it('always shows two decimals, even when the wire value dropped one', () => {
        expect(money(12.5)).toBe('₹12.50');
        expect(money(10)).toBe('₹10.00');
        expect(money('7.125')).toBe('₹7.13');
    });

    it('falls back to zero for a missing amount rather than rendering NaN', () => {
        expect(money(null)).toBe('₹0.00');
        expect(money(undefined)).toBe('₹0.00');
    });
});

describe('dateTime', () => {
    it('formats a LocalDateTime as dd-MMM-yyyy HH:mm', () => {
        expect(dateTime('2026-07-13T09:05:00')).toBe('13-Jul-2026 09:05');
    });

    it('supports the space-separated variant the order views used', () => {
        expect(dateTime('2026-07-13T09:05:00', ' ')).toBe('13 Jul 2026 09:05');
    });

    it('renders nothing for a missing or unparseable timestamp', () => {
        expect(dateTime(null)).toBe('');
        expect(dateTime('not-a-date')).toBe('');
    });
});

describe('abbreviate', () => {
    // Thymeleaf's #strings.abbreviate fits the ellipsis *within* maxLength.
    it('keeps the ellipsis inside the maximum length', () => {
        const result = abbreviate('a'.repeat(100), 70);
        expect(result).toHaveLength(70);
        expect(result.endsWith('...')).toBe(true);
    });

    it('leaves short text alone', () => {
        expect(abbreviate('Margherita', 70)).toBe('Margherita');
    });
});

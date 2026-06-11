/* eslint-env jest */
import { toBeforeIso, toSinceIso } from 'custom/notifications/utils/dateFilters';

const DATE = '01/15/2026';

describe('dateFilters', () => {
  describe('toSinceIso', () => {
    it('converts a MM/DD/YYYY date to the UTC start-of-day instant', () => {
      expect(toSinceIso(DATE)).toBe('2026-01-15T00:00:00.000Z');
    });

    it('returns undefined for empty input', () => {
      expect(toSinceIso('')).toBeUndefined();
      expect(toSinceIso(null)).toBeUndefined();
      expect(toSinceIso(undefined)).toBeUndefined();
    });

    it('returns undefined for an unparseable value', () => {
      expect(toSinceIso('not-a-date')).toBeUndefined();
    });
  });

  describe('toBeforeIso', () => {
    it('converts a MM/DD/YYYY date to the UTC end-of-day instant', () => {
      expect(toBeforeIso(DATE)).toBe('2026-01-15T23:59:59.999Z');
    });

    it('returns undefined for empty input', () => {
      expect(toBeforeIso('')).toBeUndefined();
      expect(toBeforeIso(null)).toBeUndefined();
      expect(toBeforeIso(undefined)).toBeUndefined();
    });

    it('returns undefined for an unparseable value', () => {
      expect(toBeforeIso('99/99/9999')).toBeUndefined();
    });
  });

  it('anchors both boundaries to the same UTC calendar day', () => {
    expect(toSinceIso(DATE)).toBe('2026-01-15T00:00:00.000Z');
    expect(toBeforeIso(DATE)).toBe('2026-01-15T23:59:59.999Z');
  });
});

/* eslint-env jest */
import {
  buildExpiredTooltip,
  expiredRowClassName,
  isRowExpired,
  TOOLTIP_DEFAULT,
  TOOLTIP_KEY,
} from 'custom/outboundExpiryRestrictions/utils/expiryHelpers';

import DateFormat from 'consts/dateFormat';

const daysFromToday = (delta) => {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  d.setDate(d.getDate() + delta);
  return d.toISOString();
};

describe('isRowExpired', () => {
  it.each([
    ['null row', null],
    ['null expirationDate', { expirationDate: null }],
    ['undefined expirationDate', {}],
    ['unparseable expirationDate', { expirationDate: 'not-a-date' }],
    ['today (strict-< parity with backend ExpiryRule)', { expirationDate: daysFromToday(0) }],
    ['tomorrow', { expirationDate: daysFromToday(1) }],
    ['30 days in the future', { expirationDate: daysFromToday(30) }],
  ])('returns false for %s', (_label, row) => {
    expect(isRowExpired(row)).toBe(false);
  });

  it.each([
    ['yesterday', { expirationDate: daysFromToday(-1) }],
    ['30 days in the past', { expirationDate: daysFromToday(-30) }],
  ])('returns true for %s', (_label, row) => {
    expect(isRowExpired(row)).toBe(true);
  });
});

describe('expiredRowClassName', () => {
  it('exposes the upstream-compatible class string', () => {
    expect(expiredRowClassName).toBe('text-disabled outbound-expired-row');
  });
});

describe('buildExpiredTooltip', () => {
  const expirationDate = '2025-01-15T00:00:00.000Z';

  it('returns the default English message when no translate is supplied', () => {
    expect(buildExpiredTooltip(null, null, expirationDate))
      .toBe(`Cannot ship — expired on ${expirationDate}.`);
  });

  it('formats the date via formatLocalizedDate when supplied', () => {
    const formatLocalizedDate = jest.fn(() => '01/15/2025');
    expect(buildExpiredTooltip(null, formatLocalizedDate, expirationDate))
      .toBe('Cannot ship — expired on 01/15/2025.');
    expect(formatLocalizedDate).toHaveBeenCalledWith(expirationDate, DateFormat.COMMON);
  });

  it('delegates to translate when supplied', () => {
    const translate = jest.fn(() => 'No se puede enviar — expirado el 15/01/2025.');
    const formatLocalizedDate = jest.fn(() => '15/01/2025');

    const result = buildExpiredTooltip(translate, formatLocalizedDate, expirationDate);

    expect(translate).toHaveBeenCalledWith(
      TOOLTIP_KEY,
      'Cannot ship — expired on 15/01/2025.',
      { 0: '15/01/2025' },
    );
    expect(result).toBe('No se puede enviar — expirado el 15/01/2025.');
  });

  it('exposes a stable i18n key', () => {
    expect(TOOLTIP_KEY).toBe('outboundExpiryRestrictions.expired.tooltip');
  });

  it('exposes a stable English template', () => {
    expect(TOOLTIP_DEFAULT).toBe('Cannot ship — expired on {0}.');
  });
});

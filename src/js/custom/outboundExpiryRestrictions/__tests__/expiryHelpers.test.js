/* eslint-env jest */
import {
  buildExpiredTooltip,
  EXPIRED_HINT_DEFAULT,
  EXPIRED_HINT_KEY,
  expiredRowClassName,
  isRowExpired,
  renderAvailableCell,
  TOOLTIP_DEFAULT,
  TOOLTIP_KEY,
} from 'custom/outboundExpiryRestrictions/utils/expiryHelpers';
import { renderToStaticMarkup } from 'react-dom/server';

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

describe('renderAvailableCell', () => {
  const renderCellHTML = (rendered) => renderToStaticMarkup(rendered);

  it('returns the row unchanged when it is null', () => {
    expect(renderAvailableCell(null)(null)).toBe(null);
  });

  it('returns the row unchanged when it is undefined', () => {
    expect(renderAvailableCell(null)(undefined)).toBe(undefined);
  });

  it('renders a single number when all stock is fresh', () => {
    const cell = renderAvailableCell(null)({ quantityAvailable: 50, quantityPickable: 50 });
    expect(cell).toBe('50');
  });

  it('renders a single number when quantityPickable is missing (backend not yet deployed)', () => {
    const cell = renderAvailableCell(null)({ quantityAvailable: 50 });
    expect(cell).toBe('50');
  });

  it('renders 0 when quantityAvailable is 0 and quantityPickable is 0', () => {
    const cell = renderAvailableCell(null)({ quantityAvailable: 0, quantityPickable: 0 });
    expect(cell).toBe(0);
  });

  it('renders pickable plus a red expired tail when some stock is expired', () => {
    const cell = renderAvailableCell(null)({ quantityAvailable: 83, quantityPickable: 33 });
    expect(renderCellHTML(cell))
      .toBe('33<span class="text-danger ml-1">(50 expired)</span>');
  });

  it('renders 0 plus a red expired tail when every lot is expired', () => {
    const cell = renderAvailableCell(null)({ quantityAvailable: 50, quantityPickable: 0 });
    expect(renderCellHTML(cell))
      .toBe('0<span class="text-danger ml-1">(50 expired)</span>');
  });

  it('formats expired counts with thousands separators', () => {
    const cell = renderAvailableCell(null)({ quantityAvailable: 1500, quantityPickable: 500 });
    const expiredCount = (1500 - 500).toLocaleString();
    expect(renderCellHTML(cell))
      .toBe(`500<span class="text-danger ml-1">(${expiredCount} expired)</span>`);
  });

  it('delegates to translate when supplied', () => {
    const translate = jest.fn(() => '(50 vencidos)');
    const cell = renderAvailableCell(translate)({ quantityAvailable: 83, quantityPickable: 33 });
    expect(translate).toHaveBeenCalledWith(
      EXPIRED_HINT_KEY,
      EXPIRED_HINT_DEFAULT.replace('{0}', '50'),
      { 0: '50' },
    );
    expect(renderCellHTML(cell))
      .toBe('33<span class="text-danger ml-1">(50 vencidos)</span>');
  });

  it('exposes a stable i18n key', () => {
    expect(EXPIRED_HINT_KEY).toBe('outboundExpiryRestrictions.edit.expiredHint');
  });

  it('exposes a stable English template', () => {
    expect(EXPIRED_HINT_DEFAULT).toBe('({0} expired)');
  });
});

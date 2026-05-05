import { isBefore, isValid, startOfDay } from 'date-fns';

import DateFormat from 'consts/dateFormat';

export const isRowExpired = (row) => {
  if (!row || !row.expirationDate) {
    return false;
  }
  const expiry = new Date(row.expirationDate);
  if (!isValid(expiry)) {
    return false;
  }
  return isBefore(startOfDay(expiry), startOfDay(new Date()));
};

export const expiredRowClassName = 'text-disabled outbound-expired-row';

export const TOOLTIP_KEY = 'outboundExpiryRestrictions.expired.tooltip';
export const TOOLTIP_DEFAULT = 'Cannot ship — expired on {0}.';

export const buildExpiredTooltip = (translate, formatLocalizedDate, expirationDate) => {
  const formatted = formatLocalizedDate
    ? formatLocalizedDate(expirationDate, DateFormat.COMMON)
    : String(expirationDate || '');
  const fallback = TOOLTIP_DEFAULT.replace('{0}', formatted);
  return translate
    ? translate(TOOLTIP_KEY, fallback, { 0: formatted })
    : fallback;
};

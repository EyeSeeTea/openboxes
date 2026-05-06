import React from 'react';

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

export const EXPIRED_HINT_KEY = 'outboundExpiryRestrictions.edit.expiredHint';
export const EXPIRED_HINT_DEFAULT = '({0} expired)';

const formatNumber = (value) => (value ? value.toLocaleString('en-US') : value);

export const renderAvailableCell = (translate) => (value) => {
  if (!value) {
    return value;
  }
  const available = value.quantityAvailable || 0;
  const pickable = value.quantityPickable == null ? available : value.quantityPickable;
  const expired = available - pickable;
  if (expired <= 0) {
    return formatNumber(available);
  }
  const expiredStr = expired.toLocaleString('en-US');
  const pickableStr = pickable ? pickable.toLocaleString('en-US') : '0';
  const fallback = EXPIRED_HINT_DEFAULT.replace('{0}', expiredStr);
  const hintText = translate
    ? translate(EXPIRED_HINT_KEY, fallback, { 0: expiredStr })
    : fallback;
  return (
    <>
      {pickableStr}
      <span className="text-danger ml-1">{hintText}</span>
    </>
  );
};

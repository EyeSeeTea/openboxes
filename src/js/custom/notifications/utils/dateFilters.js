import { formatDistanceToNow, parse } from 'date-fns';

// DateFilter emits values in this display format; the backend expects ISO-8601.
const FILTER_DATE_FORMAT = 'MM/dd/yyyy';

// Relative timestamp shown next to a notification ("2 hours ago"); shared by the
// inbox list and the bell dropdown. Guards against an unparseable createdAt.
export const formatRelative = (value) => {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return formatDistanceToNow(date, { addSuffix: true });
};

const parseFilterDate = (value) => {
  if (!value) return null;
  // Primary path: the DateFilter display format. Fall back to native parsing so
  // an ISO/date-only value (e.g. round-tripped through the URL) still converts
  // instead of being silently dropped.
  const fromDisplay = parse(value, FILTER_DATE_FORMAT, new Date());
  if (!Number.isNaN(fromDisplay.getTime())) return fromDisplay;
  const fromNative = new Date(value);
  return Number.isNaN(fromNative.getTime()) ? null : fromNative;
};

// The upstream DateFilter runs the picker in UTC (utcOffset={0}), so anchor the
// day boundaries to the picked calendar day in UTC — otherwise a local-time
// boundary skews the range by the browser's offset and drops edge notifications.
const utcBoundaryIso = (value, h, m, s, ms) => {
  const date = parseFilterDate(value);
  if (!date) return undefined;
  return new Date(Date.UTC(
    date.getFullYear(), date.getMonth(), date.getDate(), h, m, s, ms,
  )).toISOString();
};

// "From" date: inclusive from the start of the chosen day (UTC).
export const toSinceIso = (value) => utcBoundaryIso(value, 0, 0, 0, 0);

// "To" date: inclusive through the end of the chosen day (UTC).
export const toBeforeIso = (value) => utcBoundaryIso(value, 23, 59, 59, 999);

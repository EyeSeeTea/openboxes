import DateFilter from 'components/form-elements/DateFilter/DateFilter';
import FilterSelectField from 'components/form-elements/FilterSelectField';

// Single source of truth for the filter enums — also consumed by the filters hook
// to validate query-string values.
export const NOTIFICATION_TYPE_VALUES = [
  'SHIPMENT', 'REQUISITION', 'FULFILLMENT', 'STOCK_ALERT',
  'USER_ACCOUNT', 'SYSTEM', 'PRODUCT', 'EMAIL_TRIGGER',
];

export const READ_STATUS_VALUES = ['UNREAD', 'READ'];

// Consumed by the filters hook and the empty-values builder — keep in sync with the fields below.
export const FILTER_FIELD_KEYS = ['read', 'type', 'since', 'before'];

// Fresh object each call — callers mutate it to seed defaults from the query string.
export const buildEmptyFilterValues = () =>
  FILTER_FIELD_KEYS.reduce((acc, key) => ({ ...acc, [key]: '' }), {});

// "STOCK_ALERT" -> "Stock alert" — the human-readable fallback shown when the
// i18n key is not present in the localize store.
const humanize = (value) => {
  const spaced = value.replace(/_/g, ' ').toLowerCase();
  return spaced.charAt(0).toUpperCase() + spaced.slice(1);
};

// FilterSelectField renders option labels verbatim (no i18n), so labels must be
// pre-translated strings rather than message keys.
const toTranslatedOptions = (values, keyFor) =>
  values.map((value) => ({ id: value, value, label: keyFor(value) }));

const buildNotificationInboxFilterFields = (translate) => ({
  read: {
    type: FilterSelectField,
    attributes: {
      filterElement: true,
      placeholder: 'react.notification.inbox.filter.read.label',
      defaultPlaceholder: 'Status',
      showLabelTooltip: true,
      options: toTranslatedOptions(
        READ_STATUS_VALUES,
        (value) => translate(`react.notification.inbox.filter.read.${value.toLowerCase()}`, humanize(value)),
      ),
    },
  },
  type: {
    type: FilterSelectField,
    attributes: {
      filterElement: true,
      placeholder: 'react.notification.inbox.filter.type.label',
      defaultPlaceholder: 'Type',
      showLabelTooltip: true,
      options: toTranslatedOptions(
        NOTIFICATION_TYPE_VALUES,
        (value) => translate(`react.notification.type.${value}`, humanize(value)),
      ),
    },
  },
  since: {
    type: DateFilter,
    attributes: {
      dateFormat: 'MM/DD/YYYY',
      filterElement: true,
      label: 'react.notification.inbox.filter.since.label',
      defaultMessage: 'From date',
    },
  },
  before: {
    type: DateFilter,
    attributes: {
      dateFormat: 'MM/DD/YYYY',
      filterElement: true,
      label: 'react.notification.inbox.filter.before.label',
      defaultMessage: 'To date',
    },
  },
});

export default buildNotificationInboxFilterFields;

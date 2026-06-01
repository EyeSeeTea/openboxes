/* eslint-env jest */
import buildNotificationInboxFilterFields, { FILTER_FIELD_KEYS } from 'custom/notifications/components/NotificationInboxFilterFields';

// Stub deep-dependency form components so the module loads in isolation.
jest.mock('components/form-elements/DateFilter/DateFilter', () => 'DateFilter');
jest.mock('components/form-elements/FilterSelectField', () => 'FilterSelectField');

// translate that falls back to the default message — mirrors a store without the keys loaded.
const fallbackTranslate = (id, defaultMessage) => defaultMessage;

const labelsFor = (fields, key) => fields[key].attributes.options.map((o) => o.label);

describe('buildNotificationInboxFilterFields', () => {
  it('exposes the field keys consumed by the filters hook', () => {
    expect(FILTER_FIELD_KEYS).toEqual(['read', 'type', 'since', 'before']);
  });

  it('humanizes read status labels when the translation is absent', () => {
    const fields = buildNotificationInboxFilterFields(fallbackTranslate);
    expect(labelsFor(fields, 'read')).toEqual(['Unread', 'Read']);
  });

  it('humanizes notification type labels when the translation is absent', () => {
    const fields = buildNotificationInboxFilterFields(fallbackTranslate);
    expect(labelsFor(fields, 'type')).toEqual([
      'Shipment', 'Requisition', 'Fulfillment', 'Stock alert',
      'User account', 'System', 'Product', 'Email trigger',
    ]);
  });

  it('prefers the translated label when the react.* key resolves', () => {
    const translate = (id) => (id === 'react.notification.type.STOCK_ALERT' ? 'Stock Alert' : id);
    const fields = buildNotificationInboxFilterFields(translate);
    const stockAlert = fields.type.attributes.options.find((o) => o.value === 'STOCK_ALERT');
    expect(stockAlert.label).toBe('Stock Alert');
  });

  it('keeps the enum value as the option id and value', () => {
    const fields = buildNotificationInboxFilterFields(fallbackTranslate);
    const stockAlert = fields.type.attributes.options.find((o) => o.value === 'STOCK_ALERT');
    expect(stockAlert).toEqual({ id: 'STOCK_ALERT', value: 'STOCK_ALERT', label: 'Stock alert' });
  });
});

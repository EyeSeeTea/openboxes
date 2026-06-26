/* eslint-env jest */
import { renderHook } from '@testing-library/react-hooks';
import { useSelector } from 'react-redux';

import useInboundAddItemsV2Validation from 'hooks/inboundV2/addItems/useInboundAddItemsValidation';

jest.mock('hooks/useTranslate', () => () => (id, defaultMessage) => defaultMessage || id);

jest.mock('react-redux', () => ({
  useSelector: jest.fn(),
}));

const CONTROLLED_PRODUCT = {
  id: 'prod-1',
  value: 'prod-1',
  label: 'Test Product',
  lotAndExpiryControl: true,
};

const UNCONTROLLED_PRODUCT = {
  id: 'prod-2',
  value: 'prod-2',
  label: 'Other Product',
  lotAndExpiryControl: false,
};

const buildLineItem = (overrides = {}) => ({
  product: CONTROLLED_PRODUCT,
  lotNumber: undefined,
  expirationDate: undefined,
  quantityRequested: 10,
  ...overrides,
});

const parseLineItem = (schema, lineItem) => schema.safeParse({
  values: { lineItems: [lineItem] },
});

const expectPaths = (parsed, ...expected) => {
  expect(parsed.success).toBe(false);
  const paths = parsed.error.issues.map((i) => i.path.join('.'));
  expected.forEach((p) => expect(paths).toContain(p));
};

describe('useInboundAddItemsV2Validation', () => {
  describe('flag OFF (deferLotControlToReceipt = false) — upstream behavior', () => {
    let schema;

    beforeEach(() => {
      useSelector.mockImplementation((selector) =>
        selector({ session: { deferLotControlToReceipt: false } }));
      const { result } = renderHook(() => useInboundAddItemsV2Validation());
      schema = result.current.validationSchema;
    });

    it('fails validation when controlled product has blank lot and expiry', () => {
      const parsed = parseLineItem(schema, buildLineItem());
      expectPaths(parsed, 'values.lineItems.0.expirationDate', 'values.lineItems.0.lotNumber');
    });

    it('fails validation when controlled product has expiry but no lot', () => {
      const parsed = parseLineItem(schema, buildLineItem({ expirationDate: '2027-01-01' }));
      expectPaths(parsed, 'values.lineItems.0.lotNumber');
    });

    it('passes validation when controlled product has both lot and expiry', () => {
      const parsed = parseLineItem(schema, buildLineItem({ lotNumber: 'LOT-001', expirationDate: '2027-01-01' }));
      expect(parsed.success).toBe(true);
    });

    it('passes validation for uncontrolled product with blank lot and expiry', () => {
      const parsed = parseLineItem(schema, buildLineItem({ product: UNCONTROLLED_PRODUCT }));
      expect(parsed.success).toBe(true);
    });
  });

  describe('flag ON (deferLotControlToReceipt = true)', () => {
    let schema;

    beforeEach(() => {
      useSelector.mockImplementation((selector) =>
        selector({ session: { deferLotControlToReceipt: true } }));
      const { result } = renderHook(() => useInboundAddItemsV2Validation());
      schema = result.current.validationSchema;
    });

    it('passes validation when controlled product has blank lot and expiry', () => {
      const parsed = parseLineItem(schema, buildLineItem());
      expect(parsed.success).toBe(true);
    });

    it('still fails when expiry is entered but lot is blank (expiry-without-lot guard)', () => {
      const parsed = parseLineItem(schema, buildLineItem({ expirationDate: '2027-01-01' }));
      expectPaths(parsed, 'values.lineItems.0.lotNumber');
    });

    it('passes validation when controlled product has both lot and expiry', () => {
      const parsed = parseLineItem(schema, buildLineItem({ lotNumber: 'LOT-001', expirationDate: '2027-01-01' }));
      expect(parsed.success).toBe(true);
    });
  });
});

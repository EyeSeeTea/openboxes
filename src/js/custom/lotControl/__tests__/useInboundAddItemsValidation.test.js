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
  expirationDate: null,
  quantityRequested: 10,
  ...overrides,
});

const parseLineItem = (schema, lineItem) => schema.safeParse({
  values: { lineItems: [lineItem] },
});

const getLineItemErrors = (result) => {
  if (result.success) return [];
  return result.error.issues.map((i) => i.path.join('.'));
};

describe('useInboundAddItemsV2Validation', () => {
  describe('flag OFF (deferLotControlToReceipt = false) — upstream behavior', () => {
    beforeEach(() => {
      useSelector.mockImplementation((selector) =>
        selector({ session: { deferLotControlToReceipt: false } }));
    });

    it('fails validation when controlled product has blank lot and expiry', () => {
      const { result } = renderHook(() => useInboundAddItemsV2Validation());
      const parsed = parseLineItem(result.current.validationSchema, buildLineItem());

      expect(parsed.success).toBe(false);
      const paths = getLineItemErrors(parsed);
      expect(paths).toContain('values.lineItems.0.expirationDate');
      expect(paths).toContain('values.lineItems.0.lotNumber');
    });

    it('fails validation when controlled product has expiry but no lot', () => {
      const { result } = renderHook(() => useInboundAddItemsV2Validation());
      const parsed = parseLineItem(
        result.current.validationSchema,
        buildLineItem({ expirationDate: '2027-01-01' }),
      );

      expect(parsed.success).toBe(false);
      const paths = getLineItemErrors(parsed);
      expect(paths).toContain('values.lineItems.0.lotNumber');
    });

    it('passes validation when controlled product has both lot and expiry', () => {
      const { result } = renderHook(() => useInboundAddItemsV2Validation());
      const parsed = parseLineItem(
        result.current.validationSchema,
        buildLineItem({ lotNumber: 'LOT-001', expirationDate: '2027-01-01' }),
      );

      expect(parsed.success).toBe(true);
    });

    it('passes validation for uncontrolled product with blank lot and expiry', () => {
      const { result } = renderHook(() => useInboundAddItemsV2Validation());
      const parsed = parseLineItem(
        result.current.validationSchema,
        buildLineItem({ product: UNCONTROLLED_PRODUCT }),
      );

      expect(parsed.success).toBe(true);
    });
  });

  describe('flag ON (deferLotControlToReceipt = true)', () => {
    beforeEach(() => {
      useSelector.mockImplementation((selector) =>
        selector({ session: { deferLotControlToReceipt: true } }));
    });

    it('passes validation when controlled product has blank lot and expiry', () => {
      const { result } = renderHook(() => useInboundAddItemsV2Validation());
      const parsed = parseLineItem(result.current.validationSchema, buildLineItem());

      expect(parsed.success).toBe(true);
    });

    it('still fails when expiry is entered but lot is blank (expiry-without-lot guard)', () => {
      const { result } = renderHook(() => useInboundAddItemsV2Validation());
      const parsed = parseLineItem(
        result.current.validationSchema,
        buildLineItem({ expirationDate: '2027-01-01' }),
      );

      expect(parsed.success).toBe(false);
      const paths = getLineItemErrors(parsed);
      expect(paths).toContain('values.lineItems.0.lotNumber');
    });

    it('passes validation when controlled product has both lot and expiry', () => {
      const { result } = renderHook(() => useInboundAddItemsV2Validation());
      const parsed = parseLineItem(
        result.current.validationSchema,
        buildLineItem({ lotNumber: 'LOT-001', expirationDate: '2027-01-01' }),
      );

      expect(parsed.success).toBe(true);
    });
  });
});

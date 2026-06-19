/* eslint-env jest */
import { formatAmc, stripAmcColumn, withAmcColumn } from '../utils/amcColumn';

const AMC_FIELD = { label: 'react.stockMovement.amc.label', defaultMessage: 'AMC' };

// AddItemsPage wraps its columns under `lineItems`; EditPage under `editPageItems`.
// Use the matching container key per helper so the fixtures reflect real usage.
const wrapLineItems = (fields) => ({ lineItems: { virtualized: true, fields } });
const wrapEditPageItems = (fields) => ({ editPageItems: { virtualized: true, fields } });

describe('formatAmc', () => {
  it('rounds a decimal value to one decimal place', () => {
    expect(formatAmc(9.89)).toBe(9.9);
    expect(formatAmc(0.1648)).toBe(0.2);
    expect(formatAmc(12.44)).toBe(12.4);
    expect(formatAmc(12.45)).toBe(12.5);
  });

  it('rounds a numeric string to one decimal place', () => {
    expect(formatAmc('90')).toBe(90);
    expect(formatAmc('0.16')).toBe(0.2);
  });

  it('passes through null, undefined and empty string unchanged', () => {
    expect(formatAmc(null)).toBeNull();
    expect(formatAmc(undefined)).toBeUndefined();
    expect(formatAmc('')).toBe('');
  });

  it('keeps zero as zero', () => {
    expect(formatAmc(0)).toBe(0);
  });
});

describe('stripAmcColumn (AddItemsPage flag off)', () => {
  it('removes the amc column while preserving the other columns and their order', () => {
    const config = wrapLineItems({
      product: {},
      quantityOnHand: {},
      monthlyDemand: {},
      amc: AMC_FIELD,
      quantityRequested: {},
    });

    const result = stripAmcColumn(config);

    expect(Object.keys(result.lineItems.fields)).toEqual([
      'product', 'quantityOnHand', 'monthlyDemand', 'quantityRequested',
    ]);
    expect(result.lineItems.virtualized).toBe(true);
  });

  it('does not mutate the input config', () => {
    const config = wrapLineItems({ monthlyDemand: {}, amc: AMC_FIELD });

    stripAmcColumn(config);

    expect(Object.keys(config.lineItems.fields)).toEqual(['monthlyDemand', 'amc']);
  });

  it('is a no-op when there is no amc column', () => {
    const config = wrapLineItems({ product: {}, monthlyDemand: {} });

    const result = stripAmcColumn(config);

    expect(Object.keys(result.lineItems.fields)).toEqual(['product', 'monthlyDemand']);
  });
});

describe('withAmcColumn (EditPage flag on)', () => {
  it('inserts amc beside the requesting Demand column in the ad-hoc variant', () => {
    // AD_HOCK shows BOTH a requesting and a fulfilling demand column.
    const config = wrapEditPageItems({
      product: {},
      quantityDemandRequesting: {},
      quantityRequested: {},
      quantityDemandFulfilling: {},
      detailsButton: {},
    });

    const result = withAmcColumn(config, AMC_FIELD);

    expect(Object.keys(result.editPageItems.fields)).toEqual([
      'product',
      'quantityDemandRequesting',
      'amc',
      'quantityRequested',
      'quantityDemandFulfilling',
      'detailsButton',
    ]);
    expect(result.editPageItems.fields.amc).toBe(AMC_FIELD);
  });

  it('inserts amc beside the fulfilling Demand column in the push-type stocklist variant', () => {
    const config = wrapEditPageItems({
      product: {},
      quantityAvailable: {},
      quantityDemandFulfilling: {},
      detailsButton: {},
    });

    const result = withAmcColumn(config, AMC_FIELD);

    expect(Object.keys(result.editPageItems.fields)).toEqual([
      'product', 'quantityAvailable', 'quantityDemandFulfilling', 'amc', 'detailsButton',
    ]);
  });

  it('inserts amc beside the fulfilling Demand column in the pull-type stocklist variant', () => {
    const config = wrapEditPageItems({
      product: {},
      quantityOnHand: {},
      quantityDemandFulfilling: {},
      detailsButton: {},
    });

    const result = withAmcColumn(config, AMC_FIELD);

    expect(Object.keys(result.editPageItems.fields)).toEqual([
      'product', 'quantityOnHand', 'quantityDemandFulfilling', 'amc', 'detailsButton',
    ]);
  });

  it('does not mutate the input config', () => {
    const config = wrapEditPageItems({ quantityDemandFulfilling: {}, detailsButton: {} });

    withAmcColumn(config, AMC_FIELD);

    expect(Object.keys(config.editPageItems.fields)).toEqual([
      'quantityDemandFulfilling', 'detailsButton',
    ]);
  });

  // The grouped header's flexWidth must stay equal to the sum of its columns'
  // flexWidths, or the header misaligns from the body. AMC's flexWidth ('1') must
  // be added to the group that contains the demand column it sits beside.
  const AMC_FIELD_WITH_WIDTH = { ...AMC_FIELD, flexWidth: '1' };
  const wrapGrouped = (headerGroupings, fields) => ({
    editPageItems: { virtualized: true, headerGroupings, fields },
  });

  it('bumps the requestInformation group when AMC sits beside the requesting demand', () => {
    const config = wrapGrouped(
      {
        requestInformation: { flexWidth: 0.5 + 3 + 1 + 1 + 1 },
        availability: { flexWidth: 1 + 1 + 1 + 1 },
        edit: { flexWidth: 1 + 1 + 1 + 0.5 },
      },
      {
        productCode: { flexWidth: '0.5' },
        product: { flexWidth: '3' },
        quantityOnHandRequesting: { flexWidth: '1' },
        quantityDemandRequesting: { flexWidth: '1' },
        quantityRequested: { flexWidth: '1' },
        quantityOnHand: { flexWidth: '1' },
        quantityAvailable: { flexWidth: '1' },
        quantityDemandFulfilling: { flexWidth: '1' },
        detailsButton: { flexWidth: '1' },
      },
    );

    const result = withAmcColumn(config, AMC_FIELD_WITH_WIDTH);

    // requestInformation grew by AMC's width (1); other groups unchanged.
    expect(result.editPageItems.headerGroupings.requestInformation.flexWidth).toBe(7.5);
    expect(result.editPageItems.headerGroupings.availability.flexWidth).toBe(4);
    expect(result.editPageItems.headerGroupings.edit.flexWidth).toBe(3.5);
  });

  it('bumps the availability group when AMC sits beside the fulfilling demand', () => {
    const config = wrapGrouped(
      {
        requestInformation: { flexWidth: 0.5 + 3 + 1 },
        availability: { flexWidth: 1 + 1 + 1 },
        edit: { flexWidth: 1 },
      },
      {
        productCode: { flexWidth: '0.5' },
        product: { flexWidth: '3' },
        quantityOnHand: { flexWidth: '1' },
        quantityAvailable: { flexWidth: '1' },
        quantityDemandFulfilling: { flexWidth: '1' },
        anotherAvailabilityCol: { flexWidth: '1' },
        detailsButton: { flexWidth: '1' },
      },
    );

    const result = withAmcColumn(config, AMC_FIELD_WITH_WIDTH);

    expect(result.editPageItems.headerGroupings.requestInformation.flexWidth).toBe(4.5);
    expect(result.editPageItems.headerGroupings.availability.flexWidth).toBe(4);
    expect(result.editPageItems.headerGroupings.edit.flexWidth).toBe(1);
  });
});

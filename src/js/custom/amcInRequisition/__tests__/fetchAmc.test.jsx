/* eslint-env jest */
import * as Sentry from '@sentry/react';
import fetchAmc from 'custom/amcInRequisition/utils/fetchAmc';

import apiClient from 'utils/apiClient';

jest.mock('utils/apiClient', () => ({
  get: jest.fn(),
}));

jest.mock('@sentry/react', () => ({
  captureException: jest.fn(),
}));

const ENDPOINT = '/consumptionDemand/getMonthlyConsumption';

describe('fetchAmc', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('GETs the endpoint with productId/locationId and resolves the amc value', async () => {
    apiClient.get.mockResolvedValueOnce({ data: { amc: 9.9 } });

    const result = await fetchAmc('p1', 'l1');

    expect(apiClient.get).toHaveBeenCalledWith(ENDPOINT, {
      params: { productId: 'p1', locationId: 'l1' },
    });
    expect(result).toBe(9.9);
  });

  it('resolves 0 (not blank) when the service returns zero', async () => {
    apiClient.get.mockResolvedValueOnce({ data: { amc: 0 } });

    const result = await fetchAmc('p1', 'l1');

    expect(result).toBe(0);
  });

  it('resolves empty string without calling the API when inputs are missing', async () => {
    const result = await fetchAmc(null, 'l1');

    expect(result).toBe('');
    expect(apiClient.get).not.toHaveBeenCalled();
  });

  it('resolves empty string and reports to Sentry when the request fails', async () => {
    const error = new Error('boom');
    apiClient.get.mockRejectedValueOnce(error);

    const result = await fetchAmc('p1', 'l1');

    expect(result).toBe('');
    expect(Sentry.captureException).toHaveBeenCalledWith(error, {
      tags: { custom_amcInRequisition: 'fetchAmc' },
    });
  });
});

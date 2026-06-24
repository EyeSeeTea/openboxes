import * as Sentry from '@sentry/react';

import apiClient from 'utils/apiClient';

// Fetches the Average Monthly Consumption for a product at the requesting
// (destination) location so the AMC column can populate on product-select, at
// parity with how Demand is fetched. Backed by the custom
// ConsumptionDemandController; the service returns 0 when showAmcInRequisition is
// off. Resolves to a number, or '' when inputs are missing or the request fails —
// so the column degrades to blank rather than throwing inside the wizard. The
// error is reported to Sentry (not swallowed) before falling back.
const AMC_ENDPOINT = '/consumptionDemand/getMonthlyConsumption';

const fetchAmc = (productId, locationId) => {
  if (!productId || !locationId) {
    return Promise.resolve('');
  }

  return apiClient
    .get(AMC_ENDPOINT, { params: { productId, locationId } })
    .then((response) => {
      const { amc } = response.data;
      return amc === null || amc === undefined ? '' : amc;
    })
    .catch((err) => {
      Sentry.captureException(err, { tags: { custom_amcInRequisition: 'fetchAmc' } });
      return '';
    });
};

export default fetchAmc;

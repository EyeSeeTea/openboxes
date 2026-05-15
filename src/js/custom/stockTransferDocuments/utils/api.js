import apiClient from 'utils/apiClient';

export const stockTransferDocumentsUrl = (stockTransferId) =>
  `/api/custom/stockTransfers/${stockTransferId}/documents`;

export const fetchStockTransferDocuments = (stockTransferId) =>
  apiClient.get(stockTransferDocumentsUrl(stockTransferId));

export const uploadStockTransferDocument = (stockTransferId, file) => {
  const formData = new FormData();
  formData.append('fileContents', file);
  return apiClient.post(stockTransferDocumentsUrl(stockTransferId), formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
};

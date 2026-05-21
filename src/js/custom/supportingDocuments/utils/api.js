import apiClient from 'utils/apiClient';

export const buildDocumentsUrl = (apiBasePath, entityId) =>
  `${apiBasePath}/${entityId}/documents`;

export const fetchDocuments = (apiBasePath, entityId) =>
  apiClient.get(buildDocumentsUrl(apiBasePath, entityId));

export const uploadDocument = (apiBasePath, entityId, file) => {
  const formData = new FormData();
  formData.append('fileContents', file);
  return apiClient.post(buildDocumentsUrl(apiBasePath, entityId), formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
};

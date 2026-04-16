import React, {
  useCallback, useEffect, useRef, useState,
} from 'react';

import {
  fetchStockTransferDocuments,
  uploadStockTransferDocument,
} from 'custom/stockTransferDocuments/utils/api';
import M from 'custom/stockTransferDocuments/utils/messages';
import PropTypes from 'prop-types';
import Dropzone from 'react-dropzone';

import Translate from 'utils/Translate';

import 'custom/stockTransferDocuments/components/StockTransferDocumentsPanel.scss';

const BLOCK = 'custom-stock-transfer-documents';

const Warning = ({ messageKey, defaultMessage }) => (
  <div className={`${BLOCK}__warning`} role="alert">
    <Translate id={messageKey} defaultMessage={defaultMessage} />
  </div>
);

Warning.propTypes = {
  messageKey: PropTypes.string.isRequired,
  defaultMessage: PropTypes.string.isRequired,
};

const StockTransferDocumentsPanel = ({
  stockTransferId,
  disabled,
  onCanCompleteChange,
}) => {
  const [documents, setDocuments] = useState([]);
  const [documentRequired, setDocumentRequired] = useState(false);
  const [pendingFiles, setPendingFiles] = useState([]);
  const [uploading, setUploading] = useState(false);
  const [fetchError, setFetchError] = useState(false);
  const [uploadError, setUploadError] = useState(false);

  const isMountedRef = useRef(true);
  useEffect(() => () => {
    isMountedRef.current = false;
  }, []);

  const reportCanComplete = useCallback((required, docs) => {
    if (onCanCompleteChange) {
      onCanCompleteChange(!required || docs.length > 0);
    }
  }, [onCanCompleteChange]);

  const loadDocuments = useCallback(() => {
    if (!stockTransferId) return;
    fetchStockTransferDocuments(stockTransferId)
      .then((response) => {
        if (!isMountedRef.current) return;
        const payload = response?.data?.data ?? {};
        const nextDocuments = payload.documents ?? [];
        const nextRequired = Boolean(payload.documentRequired);
        setDocuments(nextDocuments);
        setDocumentRequired(nextRequired);
        setFetchError(false);
        reportCanComplete(nextRequired, nextDocuments);
      })
      .catch(() => {
        if (!isMountedRef.current) return;
        setFetchError(true);
        if (onCanCompleteChange) onCanCompleteChange(false);
      });
  }, [stockTransferId, reportCanComplete, onCanCompleteChange]);

  useEffect(() => {
    loadDocuments();
  }, [loadDocuments]);

  const onDrop = useCallback((accepted) => {
    if (!accepted || accepted.length === 0) return;
    setPendingFiles((current) => [...current, ...accepted]);
  }, []);

  const removePendingFile = useCallback((name) => {
    setPendingFiles((current) => current.filter((file) => file.name !== name));
  }, []);

  const uploadPendingFiles = useCallback(async () => {
    if (pendingFiles.length === 0 || !stockTransferId) return;
    setUploading(true);
    setUploadError(false);
    try {
      // Serial — addToDocuments mutates a single Hibernate entity, concurrent writes race.
      await pendingFiles.reduce(
        (chain, file) =>
          chain.then(() => uploadStockTransferDocument(stockTransferId, file)),
        Promise.resolve(),
      );
      if (!isMountedRef.current) return;
      setPendingFiles([]);
      loadDocuments();
    } catch {
      if (!isMountedRef.current) return;
      setUploadError(true);
    } finally {
      if (isMountedRef.current) setUploading(false);
    }
  }, [pendingFiles, stockTransferId, loadDocuments]);

  const showRequiredWarning = documentRequired && documents.length === 0;

  return (
    <section className={BLOCK}>
      <header className={`${BLOCK}__header`}>
        <h4 className={`${BLOCK}__title`}>
          <Translate id={M.panelTitle.id} defaultMessage={M.panelTitle.defaultMessage} />
        </h4>
      </header>

      {showRequiredWarning && (
        <Warning
          messageKey={M.requiredWarning.id}
          defaultMessage={M.requiredWarning.defaultMessage}
        />
      )}

      {fetchError && (
        <Warning
          messageKey={M.fetchError.id}
          defaultMessage={M.fetchError.defaultMessage}
        />
      )}

      {documents.length === 0 ? (
        <p className={`${BLOCK}__empty`}>
          <Translate id={M.panelEmpty.id} defaultMessage={M.panelEmpty.defaultMessage} />
        </p>
      ) : (
        <ul className={`${BLOCK}__list`}>
          {documents.map((document) => (
            <li key={document.id} className={`${BLOCK}__list-item`}>
              <a href={document.uri} target="_blank" rel="noopener noreferrer">
                {document.name}
              </a>
              <span>{document.contentType}</span>
            </li>
          ))}
        </ul>
      )}

      <Dropzone onDrop={onDrop} disabled={disabled || uploading} multiple>
        {({ getRootProps, getInputProps, isDragActive }) => {
          const rootClassName = [
            `${BLOCK}__dropzone`,
            isDragActive ? `${BLOCK}__dropzone--active` : '',
            disabled || uploading ? `${BLOCK}__dropzone--disabled` : '',
          ]
            .filter(Boolean)
            .join(' ');
          return (
            <div {...getRootProps({ className: rootClassName })}>
              <input {...getInputProps()} />
              <Translate id={M.dropzone.id} defaultMessage={M.dropzone.defaultMessage} />
            </div>
          );
        }}
      </Dropzone>

      {pendingFiles.length > 0 && (
        <div className={`${BLOCK}__pending`}>
          {pendingFiles.map((file) => (
            <div key={file.name} className={`${BLOCK}__pending-item`}>
              <span>{file.name}</span>
              <button
                type="button"
                className={`${BLOCK}__remove-button`}
                onClick={() => removePendingFile(file.name)}
              >
                <Translate
                  id={M.removeButton.id}
                  defaultMessage={M.removeButton.defaultMessage}
                />
              </button>
            </div>
          ))}
          <div className={`${BLOCK}__actions`}>
            <button
              type="button"
              className="btn btn-primary btn-xs"
              onClick={uploadPendingFiles}
              disabled={uploading || disabled}
            >
              <Translate
                id={M.uploadButton.id}
                defaultMessage={M.uploadButton.defaultMessage}
              />
            </button>
          </div>
        </div>
      )}

      {uploadError && (
        <Warning
          messageKey={M.uploadError.id}
          defaultMessage={M.uploadError.defaultMessage}
        />
      )}
    </section>
  );
};

StockTransferDocumentsPanel.propTypes = {
  stockTransferId: PropTypes.string,
  disabled: PropTypes.bool,
  onCanCompleteChange: PropTypes.func,
};

StockTransferDocumentsPanel.defaultProps = {
  stockTransferId: null,
  disabled: false,
  onCanCompleteChange: null,
};

export default StockTransferDocumentsPanel;

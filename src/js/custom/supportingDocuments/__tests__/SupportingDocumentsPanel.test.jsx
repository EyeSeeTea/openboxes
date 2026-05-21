/* eslint-env jest */
import React from 'react';

import {
  fireEvent, render, screen, waitFor,
} from '@testing-library/react';
import SupportingDocumentsPanel from 'custom/stockTransferDocuments/components/SupportingDocumentsPanel';
import {
  fetchDocuments,
  uploadDocument,
} from 'custom/stockTransferDocuments/utils/api';

import '@testing-library/jest-dom';

jest.mock('custom/stockTransferDocuments/utils/api');

jest.mock('utils/Translate', () => ({
  __esModule: true,
  default: ({ defaultMessage }) => <span>{defaultMessage}</span>,
}));

const STOCK_TRANSFER_API_BASE = '/api/custom/stockTransfers';
const STOCK_TRANSFER_ID = 'st-123';
const SAMPLE_DOCUMENT = {
  id: 'doc-1',
  name: 'certificate.pdf',
  contentType: 'application/pdf',
  uri: '/openboxes/document/download/doc-1',
};

const REQUIRED_WARNING = {
  id: 'react.custom.stockTransferDocuments.required.warning',
  defaultMessage:
    'A document must be attached before this stock transfer can be completed',
};

const LABELS = {
  panelTitle: 'Supporting documents',
  empty: 'No documents attached yet',
  requiredWarning: REQUIRED_WARNING.defaultMessage,
  fetchError: 'Unable to load documents. Please refresh to try again.',
  uploadError: 'Document upload failed',
  partialUploadError: 'Some documents failed to upload. The remaining files above can be retried.',
  invalidTypeError: 'Unsupported file type. Allowed: PDF, image, Word, Excel, CSV, ZIP.',
  tooLargeError: 'File is too large.',
  uploadButton: 'Upload',
  removeButton: 'Remove',
};

const mockFetchResolved = (payload) => {
  fetchDocuments.mockResolvedValueOnce({ data: { data: payload } });
};

const mockFetchRejected = () => {
  fetchDocuments.mockRejectedValueOnce(new Error('network'));
};

const renderPanel = (overrides = {}) => {
  const onCanCompleteChange = jest.fn();
  const utils = render(
    <SupportingDocumentsPanel
      entityId={STOCK_TRANSFER_ID}
      apiBasePath={STOCK_TRANSFER_API_BASE}
      requiredWarning={REQUIRED_WARNING}
      onCanCompleteChange={onCanCompleteChange}
      {...overrides}
    />,
  );
  return { ...utils, onCanCompleteChange };
};

const createFile = (name = SAMPLE_DOCUMENT.name, type = 'application/pdf', size) => {
  const file = new File(['hello'], name, { type });
  if (size != null) {
    Object.defineProperty(file, 'size', { value: size });
  }
  return file;
};

const dropFiles = async (container, files) => {
  const dropzone = container.querySelector('[role="presentation"]');
  const dataTransfer = {
    files,
    items: files.map((file) => ({
      kind: 'file', type: file.type, getAsFile: () => file,
    })),
    types: ['Files'],
  };
  fireEvent.drop(dropzone, { dataTransfer });
  await waitFor(() => {
    files.forEach((file) => {
      expect(screen.getByText(file.name)).toBeInTheDocument();
    });
  });
};

const dropFile = (container, file) => dropFiles(container, [file]);

beforeEach(() => {
  jest.clearAllMocks();
});

describe('SupportingDocumentsPanel', () => {
  describe('initial load', () => {
    it('shows the empty state and reports canComplete=true when not required', async () => {
      mockFetchResolved({ documentRequired: false, documents: [] });

      const { onCanCompleteChange } = renderPanel();
      expect(screen.getByText(LABELS.panelTitle)).toBeInTheDocument();

      await waitFor(() => {
        expect(onCanCompleteChange).toHaveBeenLastCalledWith(true);
      });

      // Panel is collapsed when not required — expand it to verify empty state
      fireEvent.click(screen.getByText(LABELS.panelTitle));
      expect(screen.getByText(LABELS.empty)).toBeInTheDocument();
      expect(screen.queryByText(LABELS.requiredWarning)).not.toBeInTheDocument();
    });

    it('shows the requiredWarning passed via props and reports canComplete=false', async () => {
      mockFetchResolved({ documentRequired: true, documents: [] });

      const { onCanCompleteChange } = renderPanel();

      await waitFor(() => {
        expect(screen.getByText(LABELS.requiredWarning)).toBeInTheDocument();
      });
      expect(onCanCompleteChange).toHaveBeenLastCalledWith(false);
    });

    it('renders a different requiredWarning when the caller supplies one', async () => {
      const customWarning = {
        id: 'react.custom.putawayDocuments.required.warning',
        defaultMessage: 'A document must be attached before this putaway can be completed',
      };
      mockFetchResolved({ documentRequired: true, documents: [] });

      renderPanel({ requiredWarning: customWarning });

      await waitFor(() => {
        expect(screen.getByText(customWarning.defaultMessage)).toBeInTheDocument();
      });
      expect(screen.queryByText(LABELS.requiredWarning)).not.toBeInTheDocument();
    });

    it('lists loaded documents and reports canComplete=true when required with documents', async () => {
      mockFetchResolved({ documentRequired: true, documents: [SAMPLE_DOCUMENT] });

      const { onCanCompleteChange } = renderPanel();

      const link = await screen.findByRole('link', { name: SAMPLE_DOCUMENT.name });
      expect(link).toHaveAttribute('href', SAMPLE_DOCUMENT.uri);
      expect(onCanCompleteChange).toHaveBeenLastCalledWith(true);
    });

    it('fails closed on network error and auto-expands to show the fetch error alert', async () => {
      mockFetchRejected();

      const { onCanCompleteChange } = renderPanel();

      await waitFor(() => {
        expect(onCanCompleteChange).toHaveBeenLastCalledWith(false);
      });

      // Panel auto-expands on fetchError so the warning is visible without user interaction.
      expect(await screen.findByText(LABELS.fetchError)).toBeInTheDocument();
    });
  });

  describe('uploading', () => {
    it('uploads pending files and refreshes the list on success', async () => {
      mockFetchResolved({ documentRequired: true, documents: [] });
      uploadDocument.mockResolvedValueOnce({
        data: { data: 'Document was uploaded successfully' },
      });
      mockFetchResolved({ documentRequired: true, documents: [SAMPLE_DOCUMENT] });

      const { container } = renderPanel();

      await waitFor(() => {
        expect(screen.getByText(LABELS.requiredWarning)).toBeInTheDocument();
      });

      const file = createFile();
      await dropFile(container, file);
      fireEvent.click(screen.getByRole('button', { name: LABELS.uploadButton }));

      await waitFor(() => {
        expect(uploadDocument).toHaveBeenCalledWith(
          STOCK_TRANSFER_API_BASE,
          STOCK_TRANSFER_ID,
          file,
        );
      });
      expect(await screen.findByText(SAMPLE_DOCUMENT.name)).toBeInTheDocument();
    });

    it('keeps only the failed files pending and shows partialError on partial failure', async () => {
      mockFetchResolved({ documentRequired: true, documents: [] });
      uploadDocument
        .mockResolvedValueOnce({ data: { data: 'ok' } })
        .mockRejectedValueOnce(new Error('boom'))
        .mockResolvedValueOnce({ data: { data: 'ok' } });
      mockFetchResolved({ documentRequired: true, documents: [SAMPLE_DOCUMENT] });

      const { container } = renderPanel();

      await waitFor(() => {
        expect(screen.getByText(LABELS.requiredWarning)).toBeInTheDocument();
      });

      const fileA = createFile('a.pdf');
      const fileB = createFile('b.pdf');
      const fileC = createFile('c.pdf');
      await dropFiles(container, [fileA, fileB, fileC]);

      fireEvent.click(screen.getByRole('button', { name: LABELS.uploadButton }));

      await waitFor(() => {
        expect(uploadDocument).toHaveBeenCalledTimes(3);
      });

      expect(uploadDocument)
        .toHaveBeenNthCalledWith(1, STOCK_TRANSFER_API_BASE, STOCK_TRANSFER_ID, fileA);
      expect(uploadDocument)
        .toHaveBeenNthCalledWith(2, STOCK_TRANSFER_API_BASE, STOCK_TRANSFER_ID, fileB);
      expect(uploadDocument)
        .toHaveBeenNthCalledWith(3, STOCK_TRANSFER_API_BASE, STOCK_TRANSFER_ID, fileC);

      await waitFor(() => {
        expect(screen.getByText(LABELS.partialUploadError)).toBeInTheDocument();
      });
      expect(screen.queryByText('a.pdf')).not.toBeInTheDocument();
      expect(screen.queryByText('c.pdf')).not.toBeInTheDocument();
      expect(screen.getByText('b.pdf')).toBeInTheDocument();
    });

    it('does not re-send already-uploaded files when the user retries after a partial failure', async () => {
      mockFetchResolved({ documentRequired: true, documents: [] });
      uploadDocument
        .mockResolvedValueOnce({ data: { data: 'ok' } }) // a.pdf
        .mockRejectedValueOnce(new Error('boom')); // b.pdf
      mockFetchResolved({ documentRequired: true, documents: [] });
      uploadDocument
        .mockResolvedValueOnce({ data: { data: 'ok' } }); // b.pdf retry
      mockFetchResolved({ documentRequired: true, documents: [SAMPLE_DOCUMENT] });

      const { container } = renderPanel();

      await waitFor(() => {
        expect(screen.getByText(LABELS.requiredWarning)).toBeInTheDocument();
      });

      await dropFiles(container, [createFile('a.pdf'), createFile('b.pdf')]);

      fireEvent.click(screen.getByRole('button', { name: LABELS.uploadButton }));
      await waitFor(() => {
        expect(screen.getByText(LABELS.partialUploadError)).toBeInTheDocument();
      });

      fireEvent.click(screen.getByRole('button', { name: LABELS.uploadButton }));

      await waitFor(() => {
        expect(uploadDocument).toHaveBeenCalledTimes(3);
      });
      const calledFiles = uploadDocument.mock.calls.map(([, , file]) => file.name);
      expect(calledFiles).toEqual(['a.pdf', 'b.pdf', 'b.pdf']);
    });

    it('shows an upload error and keeps pending files when upload fails', async () => {
      mockFetchResolved({ documentRequired: true, documents: [] });
      uploadDocument.mockRejectedValueOnce(new Error('boom'));

      const { container } = renderPanel();

      await waitFor(() => {
        expect(screen.getByText(LABELS.requiredWarning)).toBeInTheDocument();
      });

      await dropFile(container, createFile());
      fireEvent.click(screen.getByRole('button', { name: LABELS.uploadButton }));

      await waitFor(() => {
        expect(screen.getByText(LABELS.uploadError)).toBeInTheDocument();
      });
      expect(screen.getByText(SAMPLE_DOCUMENT.name)).toBeInTheDocument();
    });

    it('rejects an unsupported file type with an inline warning', async () => {
      mockFetchResolved({ documentRequired: true, documents: [] });

      const { container } = renderPanel();

      await waitFor(() => {
        expect(screen.getByText(LABELS.requiredWarning)).toBeInTheDocument();
      });

      const dropzone = container.querySelector('[role="presentation"]');
      const file = createFile('virus.exe', 'application/x-msdownload');
      fireEvent.drop(dropzone, {
        dataTransfer: {
          files: [file],
          items: [{ kind: 'file', type: file.type, getAsFile: () => file }],
          types: ['Files'],
        },
      });

      await waitFor(() => {
        expect(screen.getByText(LABELS.invalidTypeError)).toBeInTheDocument();
      });
      expect(uploadDocument).not.toHaveBeenCalled();
    });

    it('rejects an oversize file with an inline warning', async () => {
      mockFetchResolved({ documentRequired: true, documents: [] });

      const { container } = renderPanel();

      await waitFor(() => {
        expect(screen.getByText(LABELS.requiredWarning)).toBeInTheDocument();
      });

      const oversize = createFile('big.pdf', 'application/pdf', 11 * 1024 * 1024);
      const dropzone = container.querySelector('[role="presentation"]');
      fireEvent.drop(dropzone, {
        dataTransfer: {
          files: [oversize],
          items: [{ kind: 'file', type: oversize.type, getAsFile: () => oversize }],
          types: ['Files'],
        },
      });

      await waitFor(() => {
        expect(screen.getByText(LABELS.tooLargeError)).toBeInTheDocument();
      });
      expect(uploadDocument).not.toHaveBeenCalled();
    });

    it('removes a pending file when the remove button is clicked', async () => {
      mockFetchResolved({ documentRequired: true, documents: [] });

      const { container } = renderPanel();

      await waitFor(() => {
        expect(screen.getByText(LABELS.requiredWarning)).toBeInTheDocument();
      });

      await dropFile(container, createFile());

      fireEvent.click(screen.getByRole('button', { name: LABELS.removeButton }));
      expect(screen.queryByText(SAMPLE_DOCUMENT.name)).not.toBeInTheDocument();
    });
  });
});

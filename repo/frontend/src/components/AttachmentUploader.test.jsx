import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import AttachmentUploader from './AttachmentUploader';

jest.mock('../api/client', () => ({
  api: { upload: jest.fn() },
}));

const { api } = require('../api/client');

const UPLOAD_URL = '/api/residents/r1/agreements/a1/attachments';

function makeFile(name, type, sizeBytes) {
  const file = new File(['x'.repeat(sizeBytes)], name, { type });
  Object.defineProperty(file, 'size', { value: sizeBytes });
  return file;
}

describe('AttachmentUploader', () => {
  beforeEach(() => jest.clearAllMocks());

  test('renders drop zone with upload prompt initially', () => {
    render(<AttachmentUploader uploadUrl={UPLOAD_URL} />);
    expect(screen.getByRole('button', { name: /upload file/i })).toBeInTheDocument();
    expect(screen.getByText(/drag a file here/i)).toBeInTheDocument();
  });

  test('shows error for disallowed file type', () => {
    render(<AttachmentUploader uploadUrl={UPLOAD_URL} />);
    const input = document.querySelector('input[type="file"]');
    const badFile = makeFile('doc.docx', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 1024);
    fireEvent.change(input, { target: { files: [badFile] } });
    expect(screen.getByRole('alert')).toHaveTextContent(/only pdf, jpeg, and png/i);
  });

  test('shows error for file exceeding 15 MB', () => {
    render(<AttachmentUploader uploadUrl={UPLOAD_URL} />);
    const input = document.querySelector('input[type="file"]');
    const bigFile = makeFile('big.pdf', 'application/pdf', 16 * 1024 * 1024);
    fireEvent.change(input, { target: { files: [bigFile] } });
    expect(screen.getByRole('alert')).toHaveTextContent(/too large/i);
  });

  test('shows error for empty file', () => {
    render(<AttachmentUploader uploadUrl={UPLOAD_URL} />);
    const input = document.querySelector('input[type="file"]');
    const emptyFile = makeFile('empty.pdf', 'application/pdf', 0);
    fireEvent.change(input, { target: { files: [emptyFile] } });
    expect(screen.getByRole('alert')).toHaveTextContent(/empty/i);
  });

  test('stages a valid file and shows Upload/Cancel buttons', () => {
    render(<AttachmentUploader uploadUrl={UPLOAD_URL} />);
    const input = document.querySelector('input[type="file"]');
    const validFile = makeFile('lease.pdf', 'application/pdf', 1024);
    fireEvent.change(input, { target: { files: [validFile] } });
    expect(screen.getByText('lease.pdf')).toBeInTheDocument();
    // Both the drop zone div (role=button, aria-label) and the <button> match "upload file"
    expect(screen.getAllByRole('button', { name: /upload file/i }).length).toBeGreaterThan(0);
    expect(screen.getByRole('button', { name: /cancel/i })).toBeInTheDocument();
  });

  test('cancel button clears staged file', () => {
    render(<AttachmentUploader uploadUrl={UPLOAD_URL} />);
    const input = document.querySelector('input[type="file"]');
    const validFile = makeFile('lease.pdf', 'application/pdf', 1024);
    fireEvent.change(input, { target: { files: [validFile] } });
    fireEvent.click(screen.getByRole('button', { name: /cancel/i }));
    expect(screen.queryByText('lease.pdf')).not.toBeInTheDocument();
    expect(screen.getByText(/drag a file here/i)).toBeInTheDocument();
  });

  test('calls api.upload and shows success message on successful upload', async () => {
    const onUploaded = jest.fn();
    const uploaded = { id: 'att-new', originalFilename: 'lease.pdf' };
    api.upload.mockResolvedValueOnce(uploaded);

    render(<AttachmentUploader uploadUrl={UPLOAD_URL} onUploaded={onUploaded} />);
    const input = document.querySelector('input[type="file"]');
    const validFile = makeFile('lease.pdf', 'application/pdf', 1024);
    fireEvent.change(input, { target: { files: [validFile] } });
    // The drop zone div also has role=button with this name; target the <button> element specifically
    const uploadBtn = screen.getAllByRole('button', { name: /upload file/i })
      .find(el => el.tagName === 'BUTTON');
    fireEvent.click(uploadBtn);

    await waitFor(() => {
      expect(api.upload).toHaveBeenCalledWith(UPLOAD_URL, expect.any(FormData));
      expect(onUploaded).toHaveBeenCalledWith(uploaded);
    });
    expect(await screen.findByText(/file uploaded successfully/i)).toBeInTheDocument();
  });

  test('shows error message when upload fails', async () => {
    api.upload.mockRejectedValueOnce(new Error('Server error'));

    render(<AttachmentUploader uploadUrl={UPLOAD_URL} />);
    const input = document.querySelector('input[type="file"]');
    const validFile = makeFile('lease.pdf', 'application/pdf', 1024);
    fireEvent.change(input, { target: { files: [validFile] } });
    const uploadBtn = screen.getAllByRole('button', { name: /upload file/i })
      .find(el => el.tagName === 'BUTTON');
    fireEvent.click(uploadBtn);

    expect(await screen.findByRole('alert')).toHaveTextContent(/server error/i);
  });
});

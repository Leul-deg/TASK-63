import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import AttachmentList from './AttachmentList';

jest.mock('../api/client', () => ({
  api: { delete: jest.fn() },
}));

const { api } = require('../api/client');

const STUB_ATTACHMENTS = [
  {
    id: 'att-1',
    originalFilename: 'lease-agreement.pdf',
    contentType: 'application/pdf',
    fileSizeBytes: 102400,
    uploadedBy: 'staff',
    uploadedAt: '2026-04-01T10:00:00Z',
  },
  {
    id: 'att-2',
    originalFilename: 'id-photo.jpeg',
    contentType: 'image/jpeg',
    fileSizeBytes: 51200,
    uploadedBy: 'admin',
    uploadedAt: '2026-04-02T09:00:00Z',
  },
];

const DOWNLOAD_BASE = '/api/residents/r1/agreements/a1/attachments';

describe('AttachmentList', () => {
  beforeEach(() => jest.clearAllMocks());

  test('shows empty message when no attachments', () => {
    render(<AttachmentList attachments={[]} downloadBase={DOWNLOAD_BASE} />);
    expect(screen.getByText(/no attachments yet/i)).toBeInTheDocument();
  });

  test('shows empty message when attachments is null', () => {
    render(<AttachmentList attachments={null} downloadBase={DOWNLOAD_BASE} />);
    expect(screen.getByText(/no attachments yet/i)).toBeInTheDocument();
  });

  test('renders each attachment filename', () => {
    render(<AttachmentList attachments={STUB_ATTACHMENTS} downloadBase={DOWNLOAD_BASE} />);
    expect(screen.getByText('lease-agreement.pdf')).toBeInTheDocument();
    expect(screen.getByText('id-photo.jpeg')).toBeInTheDocument();
  });

  test('renders download links for each attachment', () => {
    render(<AttachmentList attachments={STUB_ATTACHMENTS} downloadBase={DOWNLOAD_BASE} />);
    const links = screen.getAllByRole('link', { name: /↓ download/i });
    expect(links).toHaveLength(2);
    expect(links[0]).toHaveAttribute('href', `${DOWNLOAD_BASE}/att-1/content`);
  });

  test('shows delete confirmation after clicking Delete', () => {
    render(<AttachmentList attachments={STUB_ATTACHMENTS} downloadBase={DOWNLOAD_BASE} />);
    const deleteButtons = screen.getAllByRole('button', { name: /delete/i });
    fireEvent.click(deleteButtons[0]);
    expect(screen.getByText(/remove\?/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /yes/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /no/i })).toBeInTheDocument();
  });

  test('cancels delete when No is clicked', () => {
    render(<AttachmentList attachments={STUB_ATTACHMENTS} downloadBase={DOWNLOAD_BASE} />);
    const deleteButtons = screen.getAllByRole('button', { name: /delete/i });
    fireEvent.click(deleteButtons[0]);
    fireEvent.click(screen.getByRole('button', { name: /no/i }));
    expect(screen.queryByText(/remove\?/i)).not.toBeInTheDocument();
  });

  test('calls api.delete and onDeleted callback when Yes is confirmed', async () => {
    const onDeleted = jest.fn();
    api.delete.mockResolvedValueOnce({});

    render(
      <AttachmentList
        attachments={STUB_ATTACHMENTS}
        downloadBase={DOWNLOAD_BASE}
        onDeleted={onDeleted}
      />
    );

    const deleteButtons = screen.getAllByRole('button', { name: /delete/i });
    fireEvent.click(deleteButtons[0]);
    fireEvent.click(screen.getByRole('button', { name: /yes/i }));

    await waitFor(() => {
      expect(api.delete).toHaveBeenCalledWith(`${DOWNLOAD_BASE}/att-1`);
      expect(onDeleted).toHaveBeenCalledWith('att-1');
    });
  });

  test('shows error message when delete API call fails', async () => {
    api.delete.mockRejectedValueOnce(new Error('Permission denied'));

    render(<AttachmentList attachments={STUB_ATTACHMENTS} downloadBase={DOWNLOAD_BASE} />);
    const deleteButtons = screen.getAllByRole('button', { name: /delete/i });
    fireEvent.click(deleteButtons[0]);
    fireEvent.click(screen.getByRole('button', { name: /yes/i }));

    expect(await screen.findByText(/permission denied/i)).toBeInTheDocument();
  });
});

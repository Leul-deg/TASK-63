import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import ImportExportPage from './ImportExportPage';
import { api } from '../api/client';

jest.mock('../api/client', () => ({
  api: {
    upload: jest.fn(),
    post: jest.fn(),
  },
}));

jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useNavigate: () => jest.fn(),
}));

describe('ImportExportPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test('export link has the correct download href', () => {
    render(<ImportExportPage />);
    const link = screen.getByRole('link', { name: /download csv/i });
    expect(link).toHaveAttribute('href', '/api/residents/export.csv');
    expect(link).toHaveAttribute('download');
  });

  test('shows invalid-row skip warning when preview contains invalid rows', async () => {
    api.upload.mockResolvedValueOnce({
      totalRows: 2, newCount: 1, mergeCount: 0, invalidCount: 1,
      rows: [
        { rowNumber: 1, status: 'NEW',
          data: { studentId: 'S-1', firstName: 'Alex', lastName: 'Chen', email: 'a@b.c', phone: '', dateOfBirth: '', enrollmentStatus: '', department: '', classYear: '', roomNumber: '', buildingName: '' },
          errors: [], match: null },
        { rowNumber: 2, status: 'INVALID',
          data: { studentId: '', firstName: '', lastName: '', email: 'bad', phone: '', dateOfBirth: '', enrollmentStatus: '', department: '', classYear: '', roomNumber: '', buildingName: '' },
          errors: ['Email is invalid'], match: null },
      ],
    });

    const { container } = render(<ImportExportPage />);
    fireEvent.change(container.querySelector('input[type="file"]'), {
      target: { files: [new File([''], 'r.csv', { type: 'text/csv' })] },
    });
    fireEvent.click(screen.getByRole('button', { name: /preview rows/i }));

    expect(await screen.findByText(/1 invalid row.*will be skipped/i)).toBeInTheDocument();
  });

  test('merge-all button sets all duplicate rows to merge before commit', async () => {
    api.upload.mockResolvedValueOnce({
      totalRows: 1, newCount: 0, mergeCount: 1, invalidCount: 0,
      rows: [
        { rowNumber: 1, status: 'MERGE_CANDIDATE',
          data: { studentId: 'S-1', firstName: 'Alex', lastName: 'Chen', email: 'a@b.c', phone: '', dateOfBirth: '', enrollmentStatus: '', department: '', classYear: '', roomNumber: '', buildingName: '' },
          errors: [],
          match: { id: 'existing-1', studentId: 'S-1', firstName: 'Alex', lastName: 'Chen', email: 'old@b.c', matchReason: 'studentId', sourceRowNumber: null } },
      ],
    });
    api.post.mockResolvedValueOnce({ created: 0, merged: 1, skipped: 0, failed: 0, failures: [] });

    const { container } = render(<ImportExportPage />);
    fireEvent.change(container.querySelector('input[type="file"]'), {
      target: { files: [new File([''], 'r.csv', { type: 'text/csv' })] },
    });
    fireEvent.click(screen.getByRole('button', { name: /preview rows/i }));

    await screen.findByRole('button', { name: /merge all/i });
    fireEvent.click(screen.getByRole('button', { name: /merge all/i }));
    fireEvent.click(screen.getByRole('button', { name: /commit 1 row/i }));

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith('/api/residents/import/commit', {
        rows: [expect.objectContaining({ rowNumber: 1, action: 'MERGE', mergeTargetId: 'existing-1' })],
      });
    });
  });

  test('result pane shows failure details when import rows fail', async () => {
    api.upload.mockResolvedValueOnce({
      totalRows: 1, newCount: 1, mergeCount: 0, invalidCount: 0,
      rows: [
        { rowNumber: 1, status: 'NEW',
          data: { studentId: 'S-1', firstName: 'Alex', lastName: 'Chen', email: 'a@b.c', phone: '', dateOfBirth: '', enrollmentStatus: '', department: '', classYear: '', roomNumber: '', buildingName: '' },
          errors: [], match: null },
      ],
    });
    api.post.mockResolvedValueOnce({
      created: 0, merged: 0, skipped: 0, failed: 1,
      failures: [{ rowNumber: 1, reason: 'Duplicate email detected' }],
    });

    const { container } = render(<ImportExportPage />);
    fireEvent.change(container.querySelector('input[type="file"]'), {
      target: { files: [new File([''], 'r.csv', { type: 'text/csv' })] },
    });
    fireEvent.click(screen.getByRole('button', { name: /preview rows/i }));

    await screen.findByRole('button', { name: /commit 1 row/i });
    fireEvent.click(screen.getByRole('button', { name: /commit 1 row/i }));

    expect(await screen.findByText(/import complete/i)).toBeInTheDocument();
    expect(screen.getByText(/duplicate email detected/i)).toBeInTheDocument();
  });

  test('commits same-file duplicate merges using mergeTargetRowNumber', async () => {
    api.upload.mockResolvedValueOnce({
      totalRows: 2,
      newCount: 1,
      mergeCount: 1,
      invalidCount: 0,
      rows: [
        {
          rowNumber: 1,
          status: 'NEW',
          data: {
            studentId: 'S-200',
            firstName: 'Alex',
            lastName: 'Chen',
            email: 'alex.one@campus.edu',
            phone: '555-123-4567',
            dateOfBirth: '2005-01-01',
            enrollmentStatus: 'ENROLLED',
            department: 'CS',
            classYear: '2027',
            roomNumber: '101',
            buildingName: 'Maple Hall',
          },
          errors: [],
          match: null,
        },
        {
          rowNumber: 2,
          status: 'MERGE_CANDIDATE',
          data: {
            studentId: 'S-200',
            firstName: 'Alex',
            lastName: 'Chen',
            email: 'alex.two@campus.edu',
            phone: '555-123-4567',
            dateOfBirth: '2005-01-01',
            enrollmentStatus: 'ENROLLED',
            department: 'CS',
            classYear: '2027',
            roomNumber: '101',
            buildingName: 'Maple Hall',
          },
          errors: [],
          match: {
            id: null,
            studentId: 'S-200',
            firstName: 'Alex',
            lastName: 'Chen',
            email: 'alex.one@campus.edu',
            matchReason: 'studentId',
            sourceRowNumber: 1,
          },
        },
      ],
    });
    api.post.mockResolvedValueOnce({
      created: 1,
      merged: 1,
      skipped: 0,
      failed: 0,
      failures: [],
    });

    const { container } = render(<ImportExportPage />);

    const fileInput = container.querySelector('input[type="file"]');
    const file = new File(['studentId,firstName,lastName,email\n'], 'residents.csv', { type: 'text/csv' });
    fireEvent.change(fileInput, { target: { files: [file] } });

    fireEvent.click(screen.getByRole('button', { name: /preview rows/i }));

    expect(await screen.findByText(/matches row in this file/i)).toBeInTheDocument();

    fireEvent.click(screen.getAllByLabelText(/merge/i)[0]);
    fireEvent.click(screen.getByRole('button', { name: /commit 2 rows/i }));

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith('/api/residents/import/commit', {
        rows: [
          expect.objectContaining({
            rowNumber: 1,
            action: 'CREATE',
            mergeTargetId: null,
            mergeTargetRowNumber: null,
          }),
          expect.objectContaining({
            rowNumber: 2,
            action: 'MERGE',
            mergeTargetId: null,
            mergeTargetRowNumber: 1,
          }),
        ],
      });
    });
  });
});

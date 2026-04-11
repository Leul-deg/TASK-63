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

import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import ResidentFormPage from './ResidentFormPage';

jest.mock('../context/AuthContext', () => ({
  useAuth: () => ({
    user: { id: 'staff-1', roles: ['RESIDENCE_STAFF'] },
  }),
}));

jest.mock('../api/client', () => ({
  HttpError: class HttpError extends Error {
    constructor(status, body) {
      super(body?.message || `HTTP ${status}`);
      this.status = status;
      this.body = body;
    }
  },
  api: {
    get: jest.fn(),
    post: jest.fn(),
    put: jest.fn(),
    delete: jest.fn(),
  },
}));

jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useNavigate: () => jest.fn(),
  useParams: () => ({}),
}));

const { api } = require('../api/client');

describe('ResidentFormPage', () => {
  const completeBasicsStep = (container) => {
    fireEvent.change(screen.getByPlaceholderText('Jane'), { target: { value: 'Alex' } });
    fireEvent.change(screen.getByPlaceholderText('Smith'), { target: { value: 'Chen' } });
    fireEvent.change(screen.getByPlaceholderText('jane.smith@example.edu'), { target: { value: 'alex@campus.edu' } });
    fireEvent.change(container.querySelector('input[type="date"]'), { target: { value: '2005-01-01' } });
  };

  beforeEach(() => {
    jest.useFakeTimers();
    jest.clearAllMocks();
    api.get.mockImplementation((path) => {
      if (path === '/api/residents/filter-options') {
        return Promise.resolve({ buildings: ['Maple Hall'], classYears: [2027] });
      }
      if (path.startsWith('/api/residents/duplicate-check')) {
        return Promise.resolve({
          candidates: [
            {
              id: 'resident-2',
              studentId: 'S-100',
              firstName: 'Alex',
              lastName: 'Chen',
              email: 'alex@campus.edu',
              matchReason: 'name+dob',
            },
          ],
        });
      }
      return Promise.resolve({});
    });
  });

  afterEach(() => {
    jest.runOnlyPendingTimers();
    jest.useRealTimers();
  });

  test('shows duplicate warning after debounced self-check', async () => {
    let container;
    await act(async () => {
      ({ container } = render(<ResidentFormPage />));
    });

    completeBasicsStep(container);

    await act(async () => {
      jest.advanceTimersByTime(700);
    });

    expect(await screen.findByText(/possible duplicates detected/i)).toBeInTheDocument();
    expect(screen.getByText(/name\+dob/i)).toBeInTheDocument();
  });

  test('shows free-text building input when Other / unlisted is selected', async () => {
    let container;
    await act(async () => {
      ({ container } = render(<ResidentFormPage />));
    });

    completeBasicsStep(container);
    fireEvent.click(screen.getByRole('button', { name: /next/i }));

    const buildingSelect = screen.getAllByRole('combobox')[1];
    fireEvent.change(buildingSelect, { target: { value: '__other__' } });

    await waitFor(() => {
      expect(screen.getByPlaceholderText(/enter building name/i)).toBeInTheDocument();
    });
  });
});

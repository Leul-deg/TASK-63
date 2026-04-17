import React from 'react';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import AnalyticsDashboard from './AnalyticsDashboard';

jest.mock('../api/client', () => ({
  api: {
    get: jest.fn(),
    post: jest.fn(),
  },
}));

const { api } = require('../api/client');

// Keys match what the component accesses: metrics?.booking_conversion, etc.
const STUB_METRICS = {
  booking_conversion: {
    data: {
      byStatus: { CONFIRMED: 10, COMPLETED: 8, REQUESTED: 3, CANCELLED: 1, NO_SHOW: 0 },
      conversionRate: 80,
      monthlyTrend: [],
    },
    computedAt: '2026-04-01T00:00:00Z',
  },
  no_show_rate: null,
  slot_utilization: null,
  settlement_completion: null,
};

describe('AnalyticsDashboard', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test('renders analytics heading after loading', async () => {
    api.get.mockResolvedValueOnce(STUB_METRICS);

    render(<AnalyticsDashboard />);

    expect(await screen.findByRole('heading', { name: /analytics/i })).toBeInTheDocument();
  });

  test('shows booking conversion panel when data loads', async () => {
    api.get.mockResolvedValueOnce(STUB_METRICS);

    render(<AnalyticsDashboard />);

    await waitFor(() => {
      expect(screen.getByText(/booking conversion/i)).toBeInTheDocument();
    });
  });

  test('shows error banner when API fails', async () => {
    api.get.mockRejectedValueOnce(new Error('Fetch failed'));

    render(<AnalyticsDashboard />);

    expect(await screen.findByText(/fetch failed/i)).toBeInTheDocument();
  });

  test('refresh button triggers API calls', async () => {
    api.get.mockResolvedValue(STUB_METRICS);
    api.post.mockResolvedValueOnce({});

    render(<AnalyticsDashboard />);

    await screen.findByRole('heading', { name: /analytics/i });

    const btn = screen.getByRole('button', { name: /refresh now/i });
    fireEvent.click(btn);

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith('/api/admin/analytics/refresh');
    });
  });
});

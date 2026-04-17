import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import CrawlerPage from './CrawlerPage';

jest.mock('../api/client', () => ({
  api: { get: jest.fn(), post: jest.fn(), put: jest.fn(), delete: jest.fn() },
}));

const { api } = require('../api/client');

const STUB_SOURCE = {
  id: 'src-1',
  name: 'Housing Portal',
  baseUrl: 'http://intranet.example.com/housing',
  siteType: 'HTML',
  city: 'Boston',
  scheduleCron: '0 0 */6 * * *',
  scheduleIntervalSeconds: null,
  active: true,
  lastCrawledAt: null,
};

function mockEmpty() {
  api.get.mockImplementation(url => {
    if (url.includes('engine/status'))
      return Promise.resolve({ activeWorkers: 0, maxConcurrent: 4, runningJobIds: [] });
    if (url.includes('/sources'))
      return Promise.resolve({ content: [], totalPages: 0 });
    if (url.includes('/jobs'))
      return Promise.resolve({ content: [], totalPages: 0 });
    return Promise.resolve({});
  });
}

function mockWithSource() {
  api.get.mockImplementation(url => {
    if (url.includes('engine/status'))
      return Promise.resolve({ activeWorkers: 0, maxConcurrent: 4, runningJobIds: [] });
    if (url.includes('/sources'))
      return Promise.resolve({ content: [STUB_SOURCE], totalPages: 1 });
    if (url.includes('/jobs'))
      return Promise.resolve({ content: [], totalPages: 0 });
    return Promise.resolve({});
  });
}

describe('CrawlerPage', () => {
  beforeEach(() => jest.clearAllMocks());

  test('renders "Data Collector" heading', () => {
    mockEmpty();
    render(<CrawlerPage />);
    expect(screen.getByText('Data Collector')).toBeInTheDocument();
  });

  test('Sources tab is active by default and shows empty state', async () => {
    mockEmpty();
    render(<CrawlerPage />);
    expect(await screen.findByText('No sources yet')).toBeInTheDocument();
  });

  test('renders a source row when sources exist', async () => {
    mockWithSource();
    render(<CrawlerPage />);
    expect(await screen.findByText('Housing Portal')).toBeInTheDocument();
    expect(screen.getByText('http://intranet.example.com/housing')).toBeInTheDocument();
  });

  test('clicking "All Jobs" tab shows the jobs panel with empty state', async () => {
    mockEmpty();
    render(<CrawlerPage />);
    fireEvent.click(screen.getByRole('button', { name: /all jobs/i }));
    expect(await screen.findByText('No jobs')).toBeInTheDocument();
  });

  test('"+ New source" button opens the source creation form', async () => {
    mockEmpty();
    render(<CrawlerPage />);
    await screen.findByText('No sources yet');
    fireEvent.click(screen.getByRole('button', { name: /new source/i }));
    expect(screen.getByText('New Crawl Source')).toBeInTheDocument();
  });
});

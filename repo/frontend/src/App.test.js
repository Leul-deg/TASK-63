import React from 'react';
import { render, screen } from '@testing-library/react';
import App from './App';

let mockAuthState;

jest.mock('./context/AuthContext', () => ({
  AuthProvider: ({ children }) => <>{children}</>,
  useAuth: () => mockAuthState,
}));

jest.mock('./components/NotificationBell', () => () => <div data-testid="notification-bell" />);
jest.mock('./pages/LoginPage', () => () => <h1>Sign in</h1>);
jest.mock('./pages/ResidentsPage', () => () => <div>Residents Page</div>);
jest.mock('./pages/ResidentFormPage', () => () => <div>Resident Form Page</div>);
jest.mock('./pages/ResidentAgreementsPage', () => () => <div>Resident Agreements Page</div>);
jest.mock('./pages/ResidentBookingsPage', () => () => <div>Resident Bookings Page</div>);
jest.mock('./pages/ImportExportPage', () => () => <div>Import Export Page</div>);
jest.mock('./pages/MessagesPage', () => () => <div>Messages Page</div>);
jest.mock('./pages/NotificationsPage', () => () => <div>Notifications Page</div>);
jest.mock('./pages/StudentSelfPage', () => () => <div>My Profile Page</div>);
jest.mock('./pages/BookingPolicyPage', () => () => <div>Booking Policy Page</div>);
jest.mock('./pages/IntegrationKeysPage', () => () => <div>Integration Keys Page</div>);
jest.mock('./pages/CrawlerPage', () => () => <div>Crawler Page</div>);
jest.mock('./pages/AnalyticsDashboard', () => () => <div>Analytics Dashboard Page</div>);
jest.mock('./pages/UserManagementPage', () => () => <div>User Management Page</div>);

beforeEach(() => {
  mockAuthState = {
    user: null,
    loading: false,
    error: null,
    setError: jest.fn(),
    login: jest.fn(),
    logout: jest.fn(),
  };
  window.history.pushState({}, '', '/');
});

test('shows login page when unauthenticated', async () => {
  render(<App />);
  expect(await screen.findByRole('heading', { name: /sign in/i })).toBeInTheDocument();
});

test('routes students from home to the implemented self-service page', async () => {
  mockAuthState.user = {
    id: 'student-1',
    firstName: 'Student',
    lastName: 'User',
    roles: ['STUDENT'],
  };

  render(<App />);

  expect(await screen.findByText('My Profile Page')).toBeInTheDocument();
});

test('routes staff from home to the resident directory instead of a placeholder', async () => {
  mockAuthState.user = {
    id: 'staff-1',
    firstName: 'Residence',
    lastName: 'Staff',
    roles: ['RESIDENCE_STAFF'],
  };

  render(<App />);

  expect(await screen.findByText('Residents Page')).toBeInTheDocument();
});

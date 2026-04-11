import React from 'react';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import LoginPage from './LoginPage';

// Provide a minimal AuthContext so LoginPage can render in isolation
jest.mock('../context/AuthContext', () => ({
  useAuth: () => ({
    login: jest.fn(),
    error: null,
    setError: jest.fn(),
  }),
}));

function renderLoginPage() {
  return render(
    <MemoryRouter>
      <LoginPage />
    </MemoryRouter>
  );
}

test('renders the sign-in heading', () => {
  renderLoginPage();
  expect(screen.getByRole('heading', { name: /sign in/i })).toBeInTheDocument();
});

test('renders username/email field', () => {
  renderLoginPage();
  expect(screen.getByLabelText(/username or email/i)).toBeInTheDocument();
});

test('renders password field', () => {
  renderLoginPage();
  expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
});

test('renders sign in submit button', () => {
  renderLoginPage();
  expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument();
});

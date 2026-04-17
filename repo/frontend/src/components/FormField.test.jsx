import React from 'react';
import { render, screen } from '@testing-library/react';
import FormField from './FormField';

describe('FormField', () => {
  test('renders label text', () => {
    render(
      <FormField label="Email address">
        <input type="email" />
      </FormField>
    );
    expect(screen.getByText('Email address')).toBeInTheDocument();
  });

  test('shows required asterisk when required=true', () => {
    render(
      <FormField label="Username" required>
        <input type="text" />
      </FormField>
    );
    expect(screen.getByText('*')).toBeInTheDocument();
  });

  test('does not show asterisk when required is not set', () => {
    render(
      <FormField label="Optional field">
        <input type="text" />
      </FormField>
    );
    expect(screen.queryByText('*')).not.toBeInTheDocument();
  });

  test('shows error message and role="alert" when error prop is set', () => {
    render(
      <FormField label="Email" error="Email is required">
        <input type="email" />
      </FormField>
    );
    const alert = screen.getByRole('alert');
    expect(alert).toHaveTextContent('Email is required');
  });

  test('sets aria-invalid on child input when error is present', () => {
    render(
      <FormField label="Email" error="Invalid email">
        <input type="email" />
      </FormField>
    );
    expect(screen.getByRole('textbox')).toHaveAttribute('aria-invalid', 'true');
  });

  test('shows hint text when no error is present', () => {
    render(
      <FormField label="Password" hint="At least 8 characters">
        <input type="password" />
      </FormField>
    );
    expect(screen.getByText('At least 8 characters')).toBeInTheDocument();
  });

  test('hides hint text when error is present', () => {
    render(
      <FormField label="Password" hint="At least 8 characters" error="Too short">
        <input type="password" />
      </FormField>
    );
    expect(screen.queryByText('At least 8 characters')).not.toBeInTheDocument();
    expect(screen.getByText('Too short')).toBeInTheDocument();
  });

  test('renders without label when label prop is not provided', () => {
    render(
      <FormField>
        <input type="text" placeholder="bare input" />
      </FormField>
    );
    expect(screen.getByPlaceholderText('bare input')).toBeInTheDocument();
  });
});

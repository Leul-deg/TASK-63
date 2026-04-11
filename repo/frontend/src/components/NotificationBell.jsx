import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';

const POLL_MS = 30_000;

/**
 * Bell icon with unread + pending-acknowledgment badges.
 * Polls /api/notifications/count every 30 s.
 * Clicking navigates to /notifications.
 */
export default function NotificationBell() {
  const navigate = useNavigate();
  const [counts, setCounts] = useState({ unread: 0, pendingAcknowledgment: 0 });

  const refresh = useCallback(() => {
    api.get('/api/notifications/count')
      .then(c => setCounts(c))
      .catch(() => {});
  }, []);

  useEffect(() => {
    refresh();
    const id = setInterval(refresh, POLL_MS);
    return () => clearInterval(id);
  }, [refresh]);

  const urgent  = counts.pendingAcknowledgment > 0;
  const hasUnread = counts.unread > 0;

  return (
    <button
      onClick={() => navigate('/notifications')}
      title={urgent
        ? `${counts.pendingAcknowledgment} notification(s) require acknowledgment`
        : hasUnread ? `${counts.unread} unread notification(s)` : 'Notifications'}
      style={{
        position: 'relative',
        background: 'none',
        border: 'none',
        cursor: 'pointer',
        fontSize: '1.2rem',
        padding: '4px 6px',
        lineHeight: 1,
        display: 'flex',
        alignItems: 'center',
      }}
    >
      🔔
      {(hasUnread || urgent) && (
        <span style={{
          position: 'absolute',
          top: 0,
          right: 0,
          minWidth: 16,
          height: 16,
          borderRadius: 8,
          background: urgent ? '#c00' : '#1a73e8',
          color: '#fff',
          fontSize: '0.6rem',
          fontWeight: 700,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: '0 3px',
          lineHeight: 1,
        }}>
          {urgent ? '!' : (counts.unread > 99 ? '99+' : counts.unread)}
        </span>
      )}
    </button>
  );
}

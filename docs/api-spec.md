# API Spec

## Overview

This document defines the initial HTTP API for the `Meridian Check-In & Commerce Operations Portal`.

Stack assumptions:
- Backend language: `rust`
- Backend framework: `actix-web`
- Database: `postgresql`
- API style: JSON over HTTP

Base path:
- `/api`

Common response rules:
- Success responses return JSON
- Errors return:

```json
{
  "error": {
    "code": "string_code",
    "message": "Human-readable message"
  }
}
```

## Health

### `GET /api/health`

Purpose:
- Service health check

Response:

```json
{
  "status": "ok"
}
```

## Authentication

### `POST /api/auth/login`

Request:

```json
{
  "email": "operator@example.com",
  "password": "secret"
}
```

Response:

```json
{
  "user": {
    "id": "uuid",
    "name": "Alex Doe",
    "email": "operator@example.com",
    "role": "admin"
  },
  "token": "session-or-jwt-token"
}
```

### `POST /api/auth/logout`

Purpose:
- End authenticated session

Response:

```json
{
  "success": true
}
```

### `GET /api/auth/me`

Purpose:
- Return current authenticated user

Response:

```json
{
  "id": "uuid",
  "name": "Alex Doe",
  "email": "operator@example.com",
  "role": "admin"
}
```

## Locations

### `GET /api/locations`

Purpose:
- List Meridian sites, counters, or check-in stations

Response:

```json
[
  {
    "id": "uuid",
    "name": "Main Lobby",
    "code": "MAIN-LOBBY",
    "active": true
  }
]
```

## Check-Ins

### `GET /api/checkins`

Query params:
- `location_id`
- `status`
- `from`
- `to`
- `q`

Response:

```json
{
  "items": [
    {
      "id": "uuid",
      "customer_name": "Jane Smith",
      "reference_no": "CHK-1001",
      "location_id": "uuid",
      "status": "checked_in",
      "checked_in_at": "2026-04-11T10:30:00Z"
    }
  ],
  "total": 1
}
```

### `POST /api/checkins`

Request:

```json
{
  "customer_name": "Jane Smith",
  "reference_no": "CHK-1001",
  "location_id": "uuid",
  "notes": "Walk-in customer"
}
```

Response:

```json
{
  "id": "uuid",
  "status": "checked_in"
}
```

### `POST /api/checkins/{id}/complete`

Purpose:
- Mark a check-in as completed

Response:

```json
{
  "id": "uuid",
  "status": "completed"
}
```

### `POST /api/checkins/{id}/cancel`

Purpose:
- Cancel an active check-in

Request:

```json
{
  "reason": "Customer left before service"
}
```

## Commerce

### `GET /api/products`

Purpose:
- List products or services sold at Meridian counters

### `POST /api/orders`

Request:

```json
{
  "checkin_id": "uuid",
  "items": [
    {
      "product_id": "uuid",
      "quantity": 2
    }
  ]
}
```

Response:

```json
{
  "id": "uuid",
  "order_no": "ORD-1001",
  "status": "open",
  "total_amount": 49.98
}
```

### `POST /api/orders/{id}/pay`

Request:

```json
{
  "payment_method": "cash",
  "amount": 49.98
}
```

Response:

```json
{
  "id": "uuid",
  "status": "paid"
}
```

## Dashboard

### `GET /api/dashboard/summary`

Purpose:
- Return top-level portal metrics

Response:

```json
{
  "active_checkins": 12,
  "completed_today": 45,
  "orders_today": 31,
  "revenue_today": 1240.50
}
```

## Admin

### `GET /api/admin/users`
- List platform users

### `POST /api/admin/users`
- Create platform user

### `PATCH /api/admin/users/{id}`
- Update role or active state

### `GET /api/admin/settings`
- Read system settings

### `PUT /api/admin/settings`
- Update system settings

## Suggested Status Codes

- `200 OK` for reads and successful actions
- `201 Created` for new resources
- `400 Bad Request` for validation errors
- `401 Unauthorized` for unauthenticated requests
- `403 Forbidden` for role violations
- `404 Not Found` for missing resources
- `409 Conflict` for duplicate or invalid state transitions
- `500 Internal Server Error` for unexpected failures

# API Specification: Meridian Check-In & Commerce

## Overview
This document defines the RESTful HTTP API for the `Meridian Check-In & Commerce Operations Portal`.

**Stack Specifications:**
- **Base Path:** `/api/v1`
- **Transport:** HTTP/HTTPS
- **Content-Type:** `application/json`

**Standard Behaviors:**
- Successful requests return appropriate `2xx` HTTP codes with JSON bodies.
- Pagination is standard for all collection returns (`GET` requests on plural nouns), implemented via `?page=1&limit=50` or `?cursor=xyz`.
- Errors return a standardized format:

```json
{
  "error": {
    "code": "ERROR_CODE_STRING",
    "message": "Human-readable description of what went wrong",
    "details": {
      "field_name": "Specific validation failure message"
    }
  }
}
```

---

## 1. System Health & Utilities

### `GET /api/v1/health`
**Purpose:** Service health check and load balancer hook.
**Access:** Public
**Response (200 OK):**
```json
{
  "status": "ok",
  "version": "1.0.0",
  "uptime_seconds": 3600
}
```

---

## 2. Authentication

### `POST /api/v1/auth/login`
**Purpose:** Authenticate an operator and begin a session.
**Request:**
```json
{
  "email": "operator@meridian.test",
  "password": "secure_password"
}
```
**Response (200 OK):**
```json
{
  "user": {
    "id": "uuid",
    "name": "Alex Doe",
    "email": "operator@meridian.test",
    "role": "Operator"
  },
  "token": "jwt.or.session.token",
  "expires_in": 3600
}
```

### `POST /api/v1/auth/logout`
**Purpose:** Terminate current session.
**Access:** Authenticated
**Response (200 OK):**
```json
{ "success": true }
```

### `GET /api/v1/auth/me`
**Purpose:** Return the profile of the currently authenticated user.
**Access:** Authenticated
**Response (200 OK):** *(Same user object as login)*

---

## 3. Locations

### `GET /api/v1/locations`
**Purpose:** List Meridian sites, counters, or check-in stations.
**Query Parameters:**
- `active` (bool) - Filter by active status
**Response (200 OK):**
```json
{
  "data": [
    {
      "id": "uuid",
      "name": "Main Lobby Counter A",
      "code": "MAIN-A",
      "active": true
    }
  ],
  "meta": { "total_count": 1 }
}
```

### `POST /api/v1/locations` *(Admin/Manager Only)*
**Request:** `{"name": "New Counter", "code": "NEW-1"}`
**Response (201 Created):** `Location object`

---

## 4. Check-Ins

### `GET /api/v1/checkins`
**Purpose:** Paginated list of check-ins.
**Query Parameters:**
- `location_id` (uuid)
- `status` (Expected, CheckedIn, Completed, Cancelled)
- `date_from` (ISO8601)
- `date_to` (ISO8601)
- `search` (Partial match on customer_name or reference_no)
- `page`, `limit`

**Response (200 OK):**
```json
{
  "data": [
    {
      "id": "uuid",
      "reference_no": "CHK-1002",
      "customer_name": "Jane Smith",
      "location_id": "uuid",
      "status": "CheckedIn",
      "notes": "VIP Guest",
      "checked_in_at": "2026-04-13T10:30:00Z",
      "completed_at": null
    }
  ],
  "meta": {
    "total_count": 120,
    "page": 1,
    "limit": 50
  }
}
```

### `POST /api/v1/checkins`
**Purpose:** Create a new check-in or walk-in record.
**Request:**
```json
{
  "customer_name": "Jane Smith",
  "location_id": "uuid",
  "notes": "Walk-in customer"
}
```
**Response (201 Created):**
```json
{
  "id": "uuid",
  "reference_no": "CHK-1003",
  "status": "CheckedIn"
}
```

### `POST /api/v1/checkins/{id}/status`
**Purpose:** Progress or cancel a check-in.
**Request:**
```json
{
  "status": "Completed", 
  "reason": "Service Finished" 
}
```
**Response (200 OK):**
```json
{
  "id": "uuid",
  "status": "Completed",
  "completed_at": "2026-04-13T11:00:00Z"
}
```

---

## 5. Catalog & Products

### `GET /api/v1/products`
**Purpose:** List available products/services.
**Response (200 OK):**
```json
{
  "data": [
    {
      "id": "uuid",
      "name": "Premium Service Package",
      "sku": "SRV-PRM",
      "price": 149.99,
      "active": true
    }
  ]
}
```

---

## 6. Orders & Commerce

### `GET /api/v1/orders`
**Purpose:** Retrieve latest orders, with filtering capabilities.
**Query Parameters:** `status`, `checkin_id`, `date_from`, `date_to`

### `POST /api/v1/orders`
**Purpose:** Create a cart/order attached to a check-in.
**Request:**
```json
{
  "checkin_id": "uuid",
  "items": [
    {
      "product_id": "uuid",
      "quantity": 1
    }
  ]
}
```
**Response (201 Created):**
```json
{
  "id": "uuid",
  "order_no": "ORD-5001",
  "status": "Open",
  "total_amount": 149.99,
  "items": [
    {
      "product_id": "uuid",
      "quantity": 1,
      "unit_price": 149.99,
      "line_total": 149.99
    }
  ]
}
```

### `POST /api/v1/orders/{id}/pay`
**Purpose:** Record a successful payment.
**Request:**
```json
{
  "payment_method": "Card",
  "amount": 149.99
}
```
**Response (200 OK):**
```json
{
  "id": "uuid",
  "status": "Paid"
}
```

---

## 7. Reporting & Dashboard

### `GET /api/v1/dashboard/summary`
**Purpose:** Retrieve top-level operational metrics.
**Query Parameters:** `date_from`, `date_to`, `location_id`
**Response (200 OK):**
```json
{
  "active_checkins": 12,
  "completed_checkins": 45,
  "total_orders": 31,
  "revenue_total": 1240.50,
  "average_wait_time_mins": 8.5
}
```

---

## 8. Admin 

### `GET /api/v1/admin/users`
**Response:** Paginated list of users with roles.

### `POST /api/v1/admin/users`
**Request:** Provision a new operator/manager.

### `PATCH /api/v1/admin/users/{id}`
**Request:** Update role, active status, or reset password.

### `GET /api/v1/admin/audit-logs`
**Response:** System audit log trail for security/compliance.

---

## Standard Error Codes Matrix

| HTTP Code | Suggested Occurrences / Meaning                                   |
|-----------|-------------------------------------------------------------------|
| `400`     | Schema validation failures, invalid date formats, missing fields. |
| `401`     | Missing or invalid JWT/Session.                                   |
| `403`     | User role insufficient (e.g. Operator trying to reach `/admin`).  |
| `404`     | Requested UUID entity does not exist.                             |
| `409`     | Lifecycle error (e.g., Paying an already paid order, State clash) |
| `422`     | Logical unprocessable entity (e.g., negative payment amount)      |
| `500`     | Unhandled server crashes, database disconnects.                   |

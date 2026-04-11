# Design

## System Goal

`Meridian Check-In & Commerce Operations Portal` is a full-stack application for managing:
- customer or visitor check-ins
- counter or location operations
- simple commerce and order handling
- administrative settings and user access

## Stack

- Frontend language: `rust`
- Frontend framework: `yew`
- Backend language: `rust`
- Backend framework: `actix-web`
- Database: `postgresql`

## High-Level Architecture

```text
Yew frontend
    ↓
Actix-web HTTP API
    ↓
PostgreSQL
```

## Frontend Design

### Main areas

- Login
- Dashboard
- Check-ins
- Orders / Commerce
- Locations
- Admin settings

### Frontend responsibilities

- route-based navigation
- authenticated session handling
- form validation before submission
- data tables, filters, and action buttons
- dashboard summary rendering

### Suggested page structure

```text
frontend/
  src/
    app/
    pages/
    components/
    services/
    models/
```

## Backend Design

### Backend responsibilities

- authentication and authorization
- business rules for check-in lifecycle
- order and payment processing rules
- validation and error normalization
- data access and reporting queries

### Suggested module structure

```text
backend/
  src/
    auth/
    checkins/
    orders/
    products/
    locations/
    dashboard/
    admin/
    db/
    errors/
```

## Core Domain Model

### User

- `id`
- `name`
- `email`
- `password_hash`
- `role`
- `active`
- `created_at`
- `updated_at`

### Location

- `id`
- `name`
- `code`
- `active`

### CheckIn

- `id`
- `reference_no`
- `customer_name`
- `location_id`
- `status`
- `notes`
- `checked_in_at`
- `completed_at`
- `cancelled_at`

### Product

- `id`
- `name`
- `sku`
- `price`
- `active`

### Order

- `id`
- `order_no`
- `checkin_id`
- `status`
- `total_amount`
- `created_at`

### OrderItem

- `id`
- `order_id`
- `product_id`
- `quantity`
- `unit_price`
- `line_total`

### Payment

- `id`
- `order_id`
- `payment_method`
- `amount`
- `paid_at`

## State Transitions

### Check-in lifecycle

```text
created -> checked_in -> completed
created -> checked_in -> cancelled
```

### Order lifecycle

```text
open -> paid
open -> cancelled
```

## Security Design

- authenticated access for all non-public routes
- role-based admin access
- hashed passwords
- server-side validation of all write operations
- audit-friendly timestamps on important entities

## Database Notes

- PostgreSQL is the system of record
- use UUID primary keys where possible
- index common filter fields:
  - `reference_no`
  - `location_id`
  - `status`
  - `created_at`

## Non-Goals for First Version

- advanced inventory management
- external payment gateway integration
- multi-tenant partitioning
- offline desktop sync

## Delivery Recommendation

Build in this order:

1. Auth and app shell
2. Locations and users
3. Check-in flow
4. Orders and payments
5. Dashboard and reporting
6. Admin settings and polish

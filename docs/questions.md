# Questions

## Product Questions

1. Who is the primary actor for the portal?
- front-desk operator
- store or branch manager
- administrator
- all of the above

2. What does “Meridian” refer to in this product?
- a single site
- multiple branches
- a check-in service brand
- an internal operations platform

3. Who is being checked in?
- retail customers
- visitors
- members
- patients or clients

4. Should check-ins support appointments, walk-ins, or both?

5. Is `reference_no` system-generated, user-entered, or imported from another system?

## Commerce Questions

6. What does “commerce” include in v1?
- product sales only
- service purchases
- payments and receipts
- refunds

7. Are taxes, discounts, or promo codes required?

8. Which payment methods should be supported in the first version?
- cash
- card
- transfer
- manual mark-as-paid only

9. Should an order always be attached to a check-in, or can standalone orders exist?

## Operations Questions

10. Are locations fixed physical branches, counters, or movable stations?

11. Do operators need queue ordering or wait-time estimation?

12. Should completed and cancelled check-ins remain editable?

13. Do we need receipt printing or export in v1?

## Access and Roles

14. What roles are required in the first version?
- admin
- operator
- manager
- viewer

15. What actions should be admin-only?

16. Can managers view all locations or only assigned ones?

## Reporting Questions

17. What metrics matter most on the dashboard?
- active check-ins
- average handling time
- completed today
- revenue today
- orders by location

18. Do reports need CSV export in v1?

19. Are date-range filters needed for dashboard and reporting views?

## Technical Questions

20. Should authentication be session-based or token-based?

21. Are file uploads needed anywhere in the product?

22. Do we need soft delete / audit history for users, orders, or check-ins?

23. Should the system be designed for offline-first branch use, or always-on local network use?

24. Are there external integrations planned later?
- POS
- CRM
- printer
- badge scanner
- queue display

## Delivery Questions

25. What is the smallest acceptable v1 scope for launch?

26. Which of these documents should become the source of truth next?
- `docs/api-spec.md`
- `docs/design.md`
- a separate product requirements document

# Authentication schema reference

Source: [Entity-Relationship-Diagram.drawio](https://drive.google.com/file/d/1WZ6RRWdvjOv5qoMa_TZ3pkPSDcH56mPF/view), updated 2026-10-28. The current physical model is on **Page-2**; Page-1 retains an older model and must not be used for authentication mappings.

| Table | Relevant columns |
| --- | --- |
| `USER_ACCOUNT` | `account_id` (PK), `email` (unique), `account_type`, `password_hash`, `avatar_url`, `status`, `created_at`, `updated_at` |
| `CUSTOMER_PROFILE` | `account_id` (PK/FK), `full_name`, `phone`, `avatar_url`, `created_at`, `updated_at` |
| `EMPLOYEE_PROFILE` | `employee_id` (PK), `account_id` (FK), `role_id` (FK), `full_name`, `phone`, `avatar_url`, `created_at`, `updated_at` |
| `REFRESH_TOKEN` | `refresh_token_id` (PK), `account_id` (FK), `token_hash`, `expires_at`, `revoked_at`, `created_at` |

The physical model does not contain `auth_sessions` or `google_identities`. Refresh records must reference the account directly. Registration does not accept `fullName`; the customer profile may be completed later. Account email is normalized with `Locale.ROOT` in entity factories/setters and request adapters.

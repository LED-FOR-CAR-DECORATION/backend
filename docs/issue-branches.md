# Phân chia code theo GitHub issue

Repository: https://github.com/LED-FOR-CAR-DECORATION/backend

Tên branch theo dạng hiện có của repo: `<issue-number>-iam-service-authentication-<feature>`. Chuỗi branch được xây từ `develop`; mỗi diff so với base bên dưới chỉ chứa phần bổ sung của issue đó và các chỉnh sửa tích hợp cần thiết.

| Thứ tự | Issue | Branch | Base review / PR | Phạm vi | Kiểm thử |
| --- | --- | --- | --- | --- | --- |
| 1 | [#3 Register](https://github.com/LED-FOR-CAR-DECORATION/backend/issues/3) | `3-iam-service-authentication-register-account` | `develop` | Register DTO/use case/API, validate, BCrypt, Customer mặc định, lỗi và Swagger | 14 test RegistrationTest |
| 2 | [#2 Login — follow-up](https://github.com/LED-FOR-CAR-DECORATION/backend/issues/2) | `2-iam-service-authentication-login-with-email` | `3-iam-service-authentication-register-account` | Management login, session/token type, role authorities, lockout/reset/audit, Flyway V1/V2, security filter | 20 test |
| 3 | [#5 Refresh](https://github.com/LED-FOR-CAR-DECORATION/backend/issues/5) | `5-iam-service-authentication-refresh-token` | `2-iam-service-authentication-login-with-email` | Refresh endpoint, rotation atomic, chặn replay, kiểm tra request | 22 test |
| 4 | [#4 Google](https://github.com/LED-FOR-CAR-DECORATION/backend/issues/4) | `4-iam-service-authentication-google-oauth-login` | `5-iam-service-authentication-refresh-token` | Google verifier/link/create, management Google, dependency OAuth, migration V3 | 30 test |
| 5 | [#6 Logout](https://github.com/LED-FOR-CAR-DECORATION/backend/issues/6) | `6-iam-service-authentication-logout` | `4-iam-service-authentication-google-oauth-login` | Thu hồi mọi phiên, blacklist Redis, kiểm tra cache/DB, tài liệu tổng hợp | 33 test |

## Review và merge

Review theo base trong bảng để tránh xem lại code của issue phụ thuộc. Merge theo thứ tự #3 → #2 follow-up → #5 → #4 → #6. Khi branch trước đã được merge vào `develop`, đổi base của PR tiếp theo sang `develop`. Nếu dùng squash/rebase merge, cần restack các branch phụ thuộc trước khi tiếp tục; không force-push ngoài quy trình review của nhóm.

Issue #2 đã đóng trước khi chia code. Branch #2 chỉ chứa phần bổ sung và chỉnh sửa cho login; việc tạo branch không mở lại issue. Không thay đổi trạng thái hoặc checklist issue trong thao tác này.

Issue [#7 Forgot Password](https://github.com/LED-FOR-CAR-DECORATION/backend/issues/7) và [#8 Reset Password](https://github.com/LED-FOR-CAR-DECORATION/backend/issues/8) chưa có code backend trong bản triển khai này, vì vậy không tạo branch rỗng hay đánh dấu hoàn thành.

## Chạy test

Branch #3: `./mvnw test -Dtest=RegistrationTest`. Từ branch #2 trở đi: `./mvnw clean test`, dùng H2 test độc lập, migration thật và cấu hình startup test. Kết quả trong bảng là tổng số test hiện diện tại từng mốc phụ thuộc.

## Bảo toàn code và migration

Bản trước khi chia nằm trên branch local `backup/auth-before-issue-split-20261027` tại commit `142455861df573b7b8e76b8e17149bc17deaf815`. `develop` giữ nguyên commit `17463c5f9bda469a56630fad707b78b62b8f3a62` khi bắt đầu thao tác. Frontend không được đưa vào các branch backend.

Code Java, test và cấu hình ứng dụng ở branch cuối giữ nguyên bản triển khai đã kiểm thử; migration gộp cũ được chia thành V2 (`auth_sessions`, issue #2) và V3 (`google_identities`, issue #4). Migration gộp chưa được chạy trên database thực tế trong phiên làm việc này. Nếu môi trường khác đã áp dụng migration gộp cũ, không thay file migration có checksum đã ghi; cần đối chiếu history trước khi triển khai.

Chi tiết API và yêu cầu cấu hình: [auth-api.md](auth-api.md). Ghi chú từng issue nằm trong [issues/](issues/).

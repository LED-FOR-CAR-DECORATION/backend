# Authentication APIs — IAM-01 đến IAM-05

Thử API qua `/swagger-ui/index.html`; OpenAPI JSON tại `/v3/api-docs`. Response thành công có `{ "data": ... }`; lỗi dùng `{ code, message, status, timestamp, traceId, details }`.

## Endpoints

| Method | Endpoint | JSON request | Kết quả |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | `fullName`, `email`, `password`, `confirmPassword` | 201, tạo Customer và hồ sơ |
| POST | `/api/auth/login` | `email`, `password` | 200, cặp JWT |
| POST | `/api/management/auth/login` | `email`, `password` | 200, cặp JWT; chỉ Employee đang hoạt động |
| POST | `/api/auth/google` | `idToken`, `password` khi cần xác nhận liên kết | 200, cặp JWT; liên kết hoặc tạo Customer |
| POST | `/api/management/auth/google` | `idToken`, `password` khi cần xác nhận liên kết | 200, cặp JWT; Employee phải có sẵn |
| POST | `/api/auth/refresh` | `refreshToken` | 200, cặp JWT mới; token cũ mất hiệu lực |
| POST | `/api/auth/logout` | Không có body; `Authorization: Bearer <accessToken>` | 204, thu hồi mọi phiên trên mọi thiết bị |

Login công khai vẫn hỗ trợ Employee để tương thích client hiện tại. Route management luôn kiểm tra account type tại backend; không tự tạo tài khoản nội bộ.

## Token và phiên

```json
{
  "data": {
    "accessToken": "<access-token>",
    "refreshToken": "<refresh-token>",
    "tokenType": "Bearer",
    "expiresIn": 900
  }
}
```

Access token sống 15 phút; phiên refresh sống tuyệt đối 30 ngày từ lần đăng nhập đầu tiên. Rotation không gia hạn mốc này. Token có `jti` riêng, `sid` phiên và `type` là `access` hoặc `refresh`. Refresh token không dùng được làm Bearer; access token không dùng được để refresh. Account không hoạt động hoặc đang lockout không được cấp token mới.

`auth_sessions` chỉ lưu SHA-256 của refresh token. Issue/refresh/logout khóa cùng bản ghi tài khoản; refresh xác minh phiên/hash/thời hạn rồi thay hash trong cùng transaction. Hai request dùng cùng refresh token chỉ có tối đa một request thành công. Client cần lưu cặp token mới và gộp request refresh đồng thời; không tự retry sau timeout vì token cũ có thể đã được tiêu thụ.

API protected kiểm tra phiên trong database trên từng request. Logout thu hồi mọi phiên trong database trước, rồi ghi key Redis `auth:blacklist:<jti>` với TTL bằng thời gian access token còn lại. Redis gián đoạn không làm mất hiệu lực logout vì database vẫn kiểm tra phiên bị thu hồi. Lỗi database không được coi là xác thực thành công. Đăng nhập lại tạo phiên mới.

## Lockout và audit

Sai mật khẩu 5 lần khóa tài khoản 15 phút. Lần sai thứ 5 trả 401; request tiếp theo trong thời gian khóa trả 423. Đăng nhập thành công reset bộ đếm và thời gian khóa. Khi khóa hết hạn, lần thất bại mới bắt đầu lại từ 1. Bộ đếm nằm trong database với transaction riêng và pessimistic lock, không bị mất khi Redis phục hồi hoặc khi request đăng nhập thất bại rollback.

Employee đăng nhập thành công phát `StaffLoginSuccessEvent`; đạt ngưỡng khóa phát `StaffAccountLockedEvent`. Listener ghi audit log theo user ID/role, không ghi password/token/email. Audit hiện ở application log; chưa có bảng audit riêng.

## Google ID token

Đặt `GOOGLE_CLIENT_ID` là OAuth client ID của ứng dụng. Chưa cấu hình thì Google login trả 503 `GOOGLE_UNAVAILABLE`, các API email/password vẫn hoạt động.

Client gửi Google **ID token** nhận từ Google Identity Services, không gửi Google access token:

```json
{ "idToken": "<google-id-token>" }
```

Nimbus JWT decoder xác minh chữ ký RSA bằng JWKS cố định của Google; kiểm tra issuer, audience, expiry, subject và `email_verified`. HTTP lấy public key có connect/read timeout 3 giây, tối đa một retry với backoff 100 ms khi lỗi provider; không retry token bị từ chối. Xác minh bên ngoài kết thúc trước khi mở transaction database.

Liên kết được lưu bằng Google `sub` ổn định trong `google_identities`. Nếu email đã có tài khoản thì liên kết với tài khoản đó. Email mà Google không quản lý (không phải Gmail/Workspace) phải xác nhận mật khẩu tài khoản hiện có để liên kết:

```json
{ "idToken": "<google-id-token>", "password": "<existing-account-password>" }
```

Nếu email chưa tồn tại, chỉ API công khai được tạo Customer. API management từ chối email chưa có Employee, không tạo tài khoản nội bộ. Các lần sau dùng liên kết `sub` đã lưu.

Tham chiếu: [Google: xác minh ID token phía server](https://developers.google.com/identity/gsi/web/guides/verify-google-id-token).

## Mã lỗi

| HTTP | Code | Trường hợp |
| --- | --- | --- |
| 400 | `VALIDATION_FAILED`, `INVALID_REQUEST` | Dữ liệu hoặc JSON không hợp lệ |
| 401 | `INVALID_CREDENTIALS` | Sai thông tin đăng nhập |
| 401 | `INVALID_TOKEN`, `UNAUTHENTICATED` | Token sai loại, hết hạn, bị thu hồi, refresh cũ hoặc thiếu xác thực |
| 401 | `INVALID_GOOGLE_TOKEN` | Google token sai hoặc email chưa xác minh |
| 403 | `MANAGEMENT_ACCESS_DENIED`, `ACCOUNT_DISABLED`, `ACCESS_DENIED` | Không có quyền hoặc account không hoạt động |
| 403 | `GOOGLE_LINK_REQUIRED` | Cần mật khẩu để liên kết email ngoài Gmail/Workspace |
| 409 | `EMAIL_ALREADY_REGISTERED`, `DATA_CONFLICT` | Email/identity trùng hoặc liên kết đồng thời xung đột |
| 423 | `ACCOUNT_LOCKED` | Đang khóa đăng nhập |
| 503 | `GOOGLE_UNAVAILABLE`, `AUTH_UNAVAILABLE` | Lỗi provider hoặc không thể kiểm tra phiên |

## Database migration

Flyway dùng `V1__iam_baseline.sql`, `V2__auth_sessions.sql` và `V3__google_identities.sql`. Hibernate dùng `validate`, thay cấu hình dev cũ `create`, để giữ dữ liệu qua các lần khởi động.

- Database mới: giữ `FLYWAY_BASELINE_ON_MIGRATE=false` (mặc định); chạy V1, V2 và V3.
- Database IAM cũ chưa có Flyway history: kiểm tra schema khớp V1, sao lưu database, đặt `FLYWAY_BASELINE_ON_MIGRATE=true` cho lần khởi động chuyển đổi đầu tiên. Flyway ghi baseline V1 và chạy V2 và V3. Sau đó bỏ biến này hoặc đặt về `false`.
- Database đã có history: giữ `false`; chỉ chạy migration chưa áp dụng.

Phiên làm việc này không chạy migration lên database phát triển/triển khai. Token cũ thiếu `type`/`sid` phải đăng nhập lại sau khi cập nhật. Không xóa bảng tự động khi rollback ứng dụng; cần đánh giá tương thích phiên/token trước khi rollback. Các bản ghi phiên hết hạn hiện được giữ lại; có thể bổ sung job dọn theo chính sách lưu trữ.

## Kiểm thử và phạm vi client

Chạy `./mvnw test`. Test dùng H2 ở PostgreSQL mode và migration thật, không kết nối database phát triển. Bao gồm đăng ký/rollback, login management, thời hạn/loại token, rotation đồng thời, logout mọi thiết bị, Redis outage, lockout/reset/audit, Google liên kết/tạo tài khoản và chữ ký RSA thật. Test khởi động Spring Boot kiểm tra các endpoint trong OpenAPI.

Google test dùng key RSA test và mock provider khi kiểm thử luồng tài khoản; chưa đăng nhập bằng tài khoản Google thật. Migration đã test trên H2, chưa áp dụng lên PostgreSQL triển khai.

Frontend hiện đã nối login/register. Các API Google, refresh và logout server đã sẵn sàng để client tích hợp; frontend hiện tại chưa gọi các API mới này.

# API Contract: Danh mục Nhân vật của Designer (Designer Character Catalog)

Tài liệu đặc tả hợp đồng API dành cho đội ngũ phát triển Frontend và Backend phục vụ việc tích hợp tính năng **Truy xuất danh mục nhân vật cá nhân của Designer (List My Characters)**.

Tài liệu được chuẩn hóa trực tiếp dựa trên bộ kiểm thử tự động [DesignerCharacterControllerTest.java](file:///d:/Download%20Folder/University/SE_9/SEP_Project/BE/backend/src/test/java/com/lumintra/backend/character/DesignerCharacterControllerTest.java) và controller [DesignerCharacterController.java](file:///d:/Download%20Folder/University/SE_9/SEP_Project/BE/backend/src/main/java/com/lumintra/backend/character/api/controller/DesignerCharacterController.java).

---

## 1. Thông tin chung (General Information)

- **Base URL**: `http://localhost:8080` (hoặc domain môi trường server)
- **Chuẩn dữ liệu**: `application/json; charset=UTF-8`
- **Xác thực (Authentication)**:
  - Giao thức: `Bearer Token (JWT)`
  - Header bắt buộc: `Authorization: Bearer <access_token>`
  - Phân quyền bắt buộc (RBAC): Người dùng phải có Role **`DESIGNER`**.
  - Quy tắc nghiệp vụ (BR-FL5-07 / BR-FL5-10): Hệ thống tự động trích xuất `employeeId` từ JWT Token và chỉ trả về các nhân vật do chính Designer đó tạo (`creator_employee_id = authenticated_employee_id`).
- **Quy ước phản hồi chuẩn (ApiResponse Envelope)**:
  ```json
  {
    "code": "SUCCESS",
    "message": "Thành công",
    "data": [ ... ],
    "meta": {
      "page": 0,
      "size": 10,
      "totalElements": 25,
      "totalPages": 3,
      "hasNext": true,
      "hasPrevious": false
    }
  }
  ```
- **Quy ước phản hồi lỗi chuẩn (ErrorResponse)**:
  ```json
  {
    "code": "VALIDATION_FAILED",
    "message": "Invalid request parameter",
    "status": 400,
    "timestamp": "2026-11-15T08:30:00Z",
    "traceId": "trace-12345",
    "details": [
      {
        "field": "page",
        "message": "page must be greater than or equal to 0"
      }
    ]
  }
  ```

---

## 2. Chi tiết API: Lấy Danh sách Nhân vật của Tôi (List My Characters)

- **Mã chức năng**: `CHR-01`
- **Endpoint**: `GET /api/management/designer/characters/my`
- **Method**: `GET`
- **Controller**: `DesignerCharacterController.java`
- **Use Case**: `ListMyCharactersUseCase.java`
- **Phân quyền**: Role `DESIGNER`
- **Mục đích**: Lấy danh sách phân trang các nhân vật đồ họa pixel art do chính Designer đang đăng nhập tạo ra, sắp xếp theo thời gian khởi tạo mới nhất trước (`createdAt DESC`). Hỗ trợ tìm kiếm từ khóa không phân biệt hoa thường theo tên và mô tả nhân vật, tính toán tổng số phiên bản (`totalVersions`) và trích xuất thông tin tóm tắt của phiên bản hoạt ảnh hiện hành (`currentVersion`).

---

### A. Request Headers

| Header | Giá trị | Bắt buộc | Mô tả |
| :--- | :--- | :---: | :--- |
| `Authorization` | `Bearer <access_token>` | **Có** | JWT Token của tài khoản Designer |
| `Accept` | `application/json` | **Có** | Định dạng dữ liệu mong muốn nhận về |

---

### B. Request Parameters (Query Parameters)

Các tham số được đóng gói trong đối tượng `DesignerCharacterFilterRequest`:

| Tham số | Kiểu dữ liệu | Bắt buộc | Mặc định | Ràng buộc / Validation | Mô tả |
| :--- | :--- | :---: | :---: | :--- | :--- |
| `query` | `String` | Không | `null` | Chuỗi ký tự tùy ý (tự động strip khoảng trắng) | Từ khóa tìm kiếm gần đúng trong tên hoặc mô tả nhân vật (không phân biệt hoa thường). |
| `page` | `Integer` | Không | `0` | `@Min(0)` | Chỉ số trang (0-based). Bắt buộc `>= 0`. |
| `size` | `Integer` | Không | `10` | `@Min(1)`, `@Max(100)` | Số lượng nhân vật trên mỗi trang. Nằm trong khoảng `[1, 100]`. |

*Ví dụ gọi API:*
```bash
# Lấy trang đầu tiên, 10 nhân vật
curl -X GET "http://localhost:8080/api/management/designer/characters/my?page=0&size=10" \
  -H "Authorization: Bearer <designer_jwt_token>" \
  -H "Accept: application/json"

# Tìm kiếm nhân vật có chứa từ khóa "cyber"
curl -X GET "http://localhost:8080/api/management/designer/characters/my?query=cyber&page=0&size=20" \
  -H "Authorization: Bearer <designer_jwt_token>" \
  -H "Accept: application/json"
```

---

### C. Response Schema 200 OK (`ApiResponse<List<DesignerCharacterItem>>`)

Dữ liệu trả về nằm trong mảng `data`, kèm thông tin phân trang chuẩn trong `meta`:

#### Cấu trúc từng đối tượng `DesignerCharacterItem` trong mảng `data`:
| Thuộc tính | Kiểu dữ liệu | Nullable | Mô tả |
| :--- | :--- | :---: | :--- |
| `characterId` | `Long` | Không | ID định danh duy nhất của nhân vật (`CharacterEntity` PK) |
| `characterName` | `String` | Không | Tên hiển thị của nhân vật đồ họa (ví dụ: `Cyber Fox`) |
| `description` | `String` | Có | Mô tả chi tiết về nhân vật, phong cách vẽ, ý tưởng thiết kế |
| `totalVersions` | `Integer` | Không | Tổng số phiên bản hoạt ảnh/graphic versions đã đăng tải cho nhân vật này |
| `currentVersion` | `Object` | Có | Thông tin tóm tắt của phiên bản hoạt ảnh đang hoạt động (`isCurrent = true`). Sẽ là `null` nếu nhân vật chưa có phiên bản nào. |
| `currentVersion.versionId` | `Long` | Không | ID định danh duy nhất của phiên bản hoạt ảnh (`CharacterVersionEntity` PK) |
| `currentVersion.versionNumber`| `Integer`| Không | Số thứ tự tăng dần của phiên bản (ví dụ: `1`, `2`, `3`) |
| `currentVersion.previewGifUrl` | `String` | Không | Đường dẫn CDN công khai xem trước file ảnh động GIF hoạt họa |
| `createdAt` | `String (ISO-8601)` | Không | Thời điểm khởi tạo hồ sơ nhân vật gốc (UTC, ví dụ: `2026-11-14T08:30:00Z`) |

#### Cấu trúc đối tượng `PaginationMeta` trong `meta`:
| Thuộc tính | Kiểu dữ liệu | Mô tả |
| :--- | :--- | :--- |
| `page` | `Integer` | Trang hiện tại (0-based) |
| `size` | `Integer` | Số bản ghi tối đa trên một trang |
| `totalElements` | `Long` | Tổng số lượng nhân vật thuộc quyền sở hữu của Designer thỏa mãn bộ lọc |
| `totalPages` | `Integer` | Tổng số trang |
| `hasNext` | `Boolean` | `true` nếu còn trang kế tiếp, `false` nếu đã ở trang cuối |
| `hasPrevious` | `Boolean` | `true` nếu có trang trước đó, `false` nếu đang ở trang 0 |

---

### D. Ví dụ Response Thực tế

#### 1. Trường hợp nhân vật đã có phiên bản hoạt ảnh GIF (`currentVersion` khác null):
```json
{
  "code": "SUCCESS",
  "message": "Thành công",
  "data": [
    {
      "characterId": 10,
      "characterName": "Cyber Fox",
      "description": "Mascot pixel cyberpunk",
      "totalVersions": 3,
      "currentVersion": {
        "versionId": 25,
        "versionNumber": 3,
        "previewGifUrl": "https://cdn.lumintra.com/characters/cyber-fox-v3.gif"
      },
      "createdAt": "2026-11-14T08:30:00Z"
    }
  ],
  "meta": {
    "page": 0,
    "size": 10,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false,
    "hasPrevious": false
  }
}
```

#### 2. Trường hợp nhân vật mới khởi tạo, chưa đăng tải phiên bản GIF nào (`totalVersions: 0`, `currentVersion: null`):
```json
{
  "code": "SUCCESS",
  "message": "Thành công",
  "data": [
    {
      "characterId": 31,
      "characterName": "Anh sói fudi",
      "description": "Nhân vật sói đồ họa Pixel Art",
      "totalVersions": 0,
      "currentVersion": null,
      "createdAt": "2026-11-17T03:00:00Z"
    }
  ],
  "meta": {
    "page": 0,
    "size": 10,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false,
    "hasPrevious": false
  }
}
```

---

## 3. Các Mã Lỗi và Kịch bản Kiểm thử (Error Scenarios)

Dựa theo toàn bộ test case UTCID01 - UTCID07 trong [DesignerCharacterControllerTest.java](file:///d:/Download%20Folder/University/SE_9/SEP_Project/BE/backend/src/test/java/com/lumintra/backend/character/DesignerCharacterControllerTest.java):

| Test Case | Kịch bản kiểm thử | HTTP Code | Mã lỗi (`code`) | Mô tả nguyên nhân |
| :--- | :--- | :---: | :--- | :--- |
| **UTCID01** | Designer hợp lệ gửi yêu cầu | `200` | — | Thành công, trả về danh sách nhân vật và metadata phân trang. |
| **UTCID02** | Người dùng có Role `CUSTOMER` gọi API | `403` | `FORBIDDEN` | Từ chối truy cập: Endpoint này chỉ dành riêng cho tài khoản nội bộ có Role `DESIGNER`. |
| **UTCID03** | Nhân viên có Role `STAFF` gọi API | `403` | `FORBIDDEN` | Từ chối truy cập: Nhân viên vận hành không có quyền truy cập kho thiết kế cá nhân của Designer. |
| **UTCID04** | Yêu cầu không có JWT Token hoặc Token hết hạn | `401` | `UNAUTHORIZED` | Thiếu xác thực: Yêu cầu bắt buộc phải đăng nhập. |
| **UTCID05** | Truyền tham số `page < 0` (ví dụ `page=-1`) | `400` | `VALIDATION_FAILED` | `page` phải là số nguyên lớn hơn hoặc bằng 0. |
| **UTCID06** | Truyền tham số `size <= 0` (ví dụ `size=0`) | `400` | `VALIDATION_FAILED` | `size` phải là số nguyên lớn hơn hoặc bằng 1. |
| **UTCID07** | Truyền tham số `size > 100` (ví dụ `size=101`) | `400` | `VALIDATION_FAILED` | `size` không được vượt quá giới hạn tối đa 100 bản ghi/trang. |
| — | Sự cố cơ sở dữ liệu hoặc máy chủ nội bộ | `500` | `INTERNAL_SERVER_ERROR` | Lỗi máy chủ ngoài dự kiến. |

---

## 4. Bảng Tổng hợp Mã Lỗi (Error Codes Reference)

| Mã lỗi (`code`) | HTTP Status | Giải thích |
| :--- | :---: | :--- |
| `VALIDATION_FAILED` | 400 | Dữ liệu tham số phân trang vi phạm ràng buộc validation (`page < 0`, `size < 1` hoặc `size > 100`). Danh sách trường vi phạm chi tiết nằm trong `details`. |
| `UNAUTHORIZED` | 401 | Thiếu JWT Bearer Token, token bị hỏng hoặc đã hết hạn phiên làm việc. |
| `FORBIDDEN` | 403 | Tài khoản gọi API không sở hữu vai trò `DESIGNER` hoặc tài khoản nhân viên đã bị vô hiệu hóa. |
| `INTERNAL_SERVER_ERROR` | 500 | Lỗi hệ thống server hoặc kết nối cơ sở dữ liệu. |

---

## 5. Hướng dẫn Tích hợp dành cho Frontend (Client Integration Guidelines)

1. **Hiển thị Ảnh xem trước (Preview Art)**:
   * Nếu `item.currentVersion != null && item.currentVersion.previewGifUrl`: Hiển thị ảnh động GIF qua URL CDN với thuộc tính CSS `image-rendering: pixelated` để đảm bảo giữ nguyên độ sắc nét của đồ họa Pixel Art.
   * Nếu `item.currentVersion == null`: Hiển thị placeholder thông báo *"Chưa có bản vẽ GIF"* kèm nút dẫn tới trang tải phiên bản hoặc mở Studio vẽ tiếp.
2. **Xử lý Tìm kiếm (Debounced Search)**:
   * Khi Designer gõ từ khóa vào ô tìm kiếm, nên debounce khoảng 300ms - 500ms trước khi gửi request với tham số `query=<keyword>` và reset về `page=0`.
3. **Điều hướng tải phiên bản lên Store**:
   * Mỗi thẻ nhân vật nên kèm theo liên kết nhanh dẫn sang trang Đăng tải Cửa hàng:
     ```
     /designer/store-uploads?characterId={characterId}
     ```
     giúp Designer không phải chọn lại nhân vật khi muốn upload phiên bản mới.


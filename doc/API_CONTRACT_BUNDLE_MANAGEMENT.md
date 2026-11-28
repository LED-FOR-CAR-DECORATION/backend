# API Contract: Quản lý Gói Sản phẩm (Store Bundle Management)

Tài liệu đặc tả hợp đồng API dành cho đội ngũ phát triển Frontend và Backend phục vụ việc tích hợp tính năng **Quản lý Bundle (Gói Combo)** cho quyền **MANAGER** (hoặc **ADMIN**).

---

## 1. Thông tin chung (General Information)

- **Base URL**: `http://localhost:8080` (hoặc domain môi trường deployment)
- **Chuẩn dữ liệu**: `application/json; charset=UTF-8`
- **Xác thực (Authentication)**: 
  - Giao thức: `Bearer Token (JWT)`
  - Header: `Authorization: Bearer <access_token>`
  - Yêu cầu phân quyền (RBAC): Người dùng phải có Role `MANAGER` (hoặc `ADMIN`).
- **Quy ước phản hồi chuẩn (ApiResponse Envelope)**:
  ```json
  {
    "code": "SUCCESS",
    "message": "Thành công",
    "data": { ... },
    "meta": null
  }
  ```

---

## 2. Quy trình tải ảnh sản phẩm (Media Upload Flow)

> [!IMPORTANT]
> Bundle yêu cầu URL ảnh công khai (`thumbnailUrl`, `galleryUrls`). Frontend **không gửi file nhị phân (binary/multipart)** trực tiếp lên endpoint tạo bundle, mà tái sử dụng **Media Service** theo chuẩn Cloud V4 Pre-signed URL:

### Bước 1: Xin Signed Upload URL
- **Endpoint**: `POST /api/media/upload-url`
- **Headers**:
  - `Authorization: Bearer <access_token>`
  - `Content-Type: application/json`
- **Request Body**:
  ```json
  {
    "fileName": "bundle_summer_deal.png",
    "contentType": "image/png",
    "fileSize": 1048576,
    "purpose": "STORE_PRODUCT"
  }
  ```
- **Response 200 OK**:
  ```json
  {
    "code": "SUCCESS",
    "message": "Tạo URL tải lên thành công",
    "data": {
      "signedUploadUrl": "https://storage.googleapis.com/...X-Goog-Signature=...",
      "publicUrl": "https://cdn.lumintra.com/store-products/2026/11/bundle_summer_deal.png"
    }
  }
  ```

### Bước 2: Tải trực tiếp file nhị phân lên Cloud Storage
- **Method**: `PUT`
- **URL**: `signedUploadUrl` (nhận được từ Bước 1)
- **Headers**:
  - `Content-Type`: MIME type của file (ví dụ: `image/png`)
- **Body**: File binary (Blob / File object)
- **Response**: `200 OK` từ Google Cloud Storage.
- **Kết quả**: Sử dụng chuỗi `publicUrl` để gán vào `thumbnailUrl` hoặc mảng `galleryUrls` của Bundle.

---

## 3. Danh sách các API Quản lý Bundle

### 3.1. Tạo mới Gói Combo Bundle (Create Bundle)

- **Mã chức năng**: `STR-15`
- **Endpoint**: `POST /api/management/admin/store/bundles`
- **Controller**: `CreateBundleController.java`
- **Use Case**: `CreateBundleUseCase.java`
- **Phân quyền**: Role `MANAGER`

#### A. Request Headers
| Header | Giá trị | Bắt buộc | Mô tả |
| :--- | :--- | :--- | :--- |
| `Authorization` | `Bearer <token>` | Có | JWT Token của tài khoản Manager |
| `Content-Type` | `application/json` | Có | Định dạng payload JSON |

#### B. Request Body Schema (`CreateBundleRequest`)
| Thuộc tính | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả |
| :--- | :--- | :--- | :--- | :--- |
| `bundleName` | `String` | **Có** | `@NotBlank`, `@Size(max = 255)` | Tên hiển thị của gói combo bundle |
| `description` | `String` | Không | Văn bản tùy ý | Mô tả chi tiết nội dung, ưu đãi gói bundle |
| `thumbnailUrl` | `String` | Không | `@Size(max = 500)`, `@URL` | Ảnh đại diện gói sản phẩm (từ Media Service) |
| `galleryUrls` | `List<String>`| Không | Mảng URL hợp lệ, mỗi URL max 500 ký tự | Danh sách ảnh bổ sung cho slider sản phẩm |
| `bundlePrice` | `BigDecimal` | **Có** | `@NotNull`, `@DecimalMin("0")`, max 2 số thập phân | Giá bán ưu đãi của combo (VND). **Phải <= `originalPrice`** |
| `tagIds` | `List<Long>` | Không | Danh sách ID tag nguyên dương | ID danh mục tag gán cho sản phẩm |
| `items` | `List<Item>` | **Có** | `@NotEmpty`, tối thiểu 1 phần tử | Danh sách linh kiện/animation cấu thành bundle |

##### Ràng buộc đối với từng phần tử `items[i]`:
- Mỗi phần tử có cấu trúc:
  ```json
  {
    "modelId": 1,
    "versionId": null,
    "quantity": 1
  }
  ```
- **Validation quy định**: Phải cung cấp **chính xác 1 trong 2**: hoặc `modelId` (cho thiết bị phần cứng LED), hoặc `versionId` (cho animation Character pixel). Không được đồng thời truyền cả hai hoặc bỏ trống cả hai (`(modelId == null) != (versionId == null)`).
- `quantity`: Số nguyên dương `>= 1`.

#### C. Quy tắc nghiệp vụ (Business Rules)
1. **Tính toán tự động giá gốc (`originalPrice`)**:
   - Hệ thống tự động tra cứu đơn giá gốc (`basePrice`) của từng item cấu thành từ cơ sở dữ liệu (`productType = LED` hoặc `productType = CHARACTER`).
   - `originalPrice = ∑ (basePrice * quantity)`.
2. **Kiểm tra giá hợp lệ**:
   - `bundlePrice` **bắt buộc phải nhỏ hơn hoặc bằng** `originalPrice`. Nếu vi phạm sẽ trả về lỗi `400 INVALID_BUNDLE_PRICE`.
   - `originalPrice` không vượt quá `9,999,999,999.99 VND`.
3. **Khởi tạo trạng thái**:
   - Tự động tạo bản ghi `PRODUCT` với `productType = BUNDLE` và trạng thái ban đầu là `UNPUBLISHED`.
   - Tự động tạo bản ghi `BUNDLE` và các liên kết `BUNDLE_ITEM` tương ứng.

#### D. Ví dụ Request (cURL)
```bash
curl -X POST "http://localhost:8080/api/management/admin/store/bundles" \
  -H "Authorization: Bearer YOUR_MANAGER_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "bundleName": "Combo Khởi Đầu: Bảng LED Matrix 64x64 + Animation Pixel Rồng",
    "description": "Gói combo siêu tiết kiệm bao gồm 1 bảng LED Matrix 64x64 Pro và 1 animation Rồng Cyber độc quyền.",
    "thumbnailUrl": "https://cdn.lumintra.com/store-products/2026/11/bundle_combo1.png",
    "galleryUrls": [
      "https://cdn.lumintra.com/store-products/2026/11/bundle_combo1_preview1.png"
    ],
    "bundlePrice": 380000.00,
    "tagIds": [1, 3],
    "items": [
      {
        "modelId": 5,
        "quantity": 1
      },
      {
        "versionId": 12,
        "quantity": 1
      }
    ]
  }'
```

#### E. Response 201 Created (`CreatedBundleResponse`)
```json
{
  "code": "SUCCESS",
  "message": "Thành công",
  "data": {
    "bundleId": 10,
    "productId": 42,
    "bundleName": "Combo Khởi Đầu: Bảng LED Matrix 64x64 + Animation Pixel Rồng",
    "description": "Gói combo siêu tiết kiệm bao gồm 1 bảng LED Matrix 64x64 Pro và 1 animation Rồng Cyber độc quyền.",
    "thumbnailUrl": "https://cdn.lumintra.com/store-products/2026/11/bundle_combo1.png",
    "galleryUrls": [
      "https://cdn.lumintra.com/store-products/2026/11/bundle_combo1_preview1.png"
    ],
    "originalPrice": 450000.00,
    "bundlePrice": 380000.00,
    "status": "UNPUBLISHED",
    "tagIds": [1, 3],
    "items": [
      {
        "modelId": 5,
        "versionId": null,
        "quantity": 1
      },
      {
        "modelId": null,
        "versionId": 12,
        "quantity": 1
      }
    ]
  },
  "meta": null
}
```

#### F. Mã lỗi có thể xảy ra (Error Codes)
| HTTP Status | Error Code | Mô tả |
| :--- | :--- | :--- |
| `400 Bad Request` | `VALIDATION_FAILED` | Tên trống, URL sai định dạng, `items` rỗng, hoặc một item có cả 2 hoặc không có ID nào |
| `400 Bad Request` | `INVALID_BUNDLE_PRICE` | `bundlePrice` lớn hơn `originalPrice` của tổng các thành phần cấu thành |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Chưa đăng nhập hoặc token đã hết hạn |
| `403 Forbidden` | `ACCESS_DENIED` | Người dùng không có vai trò `MANAGER` |
| `404 Not Found` | `TAG_NOT_FOUND` | Một trong các `tagIds` được gửi không tồn tại trong hệ thống |
| `404 Not Found` | `PRODUCT_NOT_FOUND` | Một trong các LED model hoặc Character version không tìm thấy sản phẩm hợp lệ tương ứng |

---

### 3.2. Cập nhật Gói Combo Bundle (Update Bundle)

- **Mã chức năng**: `STR-16`
- **Endpoint**: `PUT /api/management/admin/store/bundles/{id}`
- **Controller**: `UpdateBundleController.java`
- **Use Case**: `UpdateBundleUseCase.java`
- **Phân quyền**: Role `MANAGER`

#### A. Path Parameters
| Tham số | Kiểu dữ liệu | Bắt buộc | Mô tả |
| :--- | :--- | :--- | :--- |
| `id` | `Long` | **Có** | ID của gói bundle (`bundleId`) cần chỉnh sửa (phải `> 0`) |

#### B. Request Body Schema (`UpdateBundleRequest`)
| Thuộc tính | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả |
| :--- | :--- | :--- | :--- | :--- |
| `bundleName` | `String` | **Có** | `@NotBlank`, `@Size(max = 255)` | Tên mới của gói bundle |
| `description` | `String` | Không | Văn bản | Mô tả chi tiết cập nhật |
| `thumbnailUrl` | `String` | Không | `@Size(max = 500)`, `@URL` | URL ảnh đại diện mới |
| `bundlePrice` | `BigDecimal` | **Có** | `@NotNull`, `@DecimalMin("0")` | Giá bán cập nhật. Phải `<= originalPrice` |
| `items` | `List<Item>` | Không | Nếu truyền: `@Size(min = 1)` | Danh sách linh kiện thay thế toàn bộ danh sách cũ |

#### C. Quy tắc nghiệp vụ (Business Rules)
1. **Kiểm tra sự tồn tại**:
   - Nếu `id` không tồn tại, trả về `404 BUNDLE_NOT_FOUND`.
2. **Cập nhật linh kiện (`items`)**:
   - Nếu client truyền mảng `items`: Toàn bộ các phần tử `BUNDLE_ITEM` cũ của bundle sẽ bị xóa và thay thế bằng danh sách mới.
   - Giá gốc `originalPrice` được tính toán lại theo danh sách `items` mới.
   - Nếu client **không truyền `items`**: Giữ nguyên danh sách linh kiện và giá gốc hiện tại.
3. **Bảo toàn lịch sử mua hàng (Historical Price Preservation)**:
   - Các đơn hàng đã đặt trước đó vẫn giữ nguyên đơn giá và thành tiền theo snapshot lịch sử mua, việc sửa giá không ảnh hưởng đến đơn cũ.
4. **Xóa Cache (Redis Eviction)**:
   - Hệ thống tự động phát tán sự kiện `StoreProductUpdatedEvent` và xóa cache Redis `store:product:{productId}` để dữ liệu cập nhật có hiệu lực ngay lập tức.

#### D. Ví dụ Request (cURL)
```bash
curl -X PUT "http://localhost:8080/api/management/admin/store/bundles/10" \
  -H "Authorization: Bearer YOUR_MANAGER_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "bundleName": "Combo Khởi Đầu Nâng Cấp: LED 64x64 + 2 Animation",
    "description": "Bản combo cập nhật bổ sung thêm 1 animation pixel cao cấp.",
    "thumbnailUrl": "https://cdn.lumintra.com/store-products/2026/11/bundle_combo1_v2.png",
    "bundlePrice": 420000.00,
    "items": [
      {
        "modelId": 5,
        "quantity": 1
      },
      {
        "versionId": 12,
        "quantity": 1
      },
      {
        "versionId": 15,
        "quantity": 1
      }
    ]
  }'
```

#### E. Response 200 OK (`UpdatedBundleResponse`)
```json
{
  "code": "SUCCESS",
  "message": "Thành công",
  "data": {
    "bundleId": 10,
    "productId": 42,
    "bundleName": "Combo Khởi Đầu Nâng Cấp: LED 64x64 + 2 Animation",
    "description": "Bản combo cập nhật bổ sung thêm 1 animation pixel cao cấp.",
    "thumbnailUrl": "https://cdn.lumintra.com/store-products/2026/11/bundle_combo1_v2.png",
    "galleryUrls": [
      "https://cdn.lumintra.com/store-products/2026/11/bundle_combo1_preview1.png"
    ],
    "originalPrice": 520000.00,
    "bundlePrice": 420000.00,
    "status": "UNPUBLISHED",
    "tagIds": [1, 3],
    "items": [
      {
        "modelId": 5,
        "versionId": null,
        "quantity": 1
      },
      {
        "modelId": null,
        "versionId": 12,
        "quantity": 1
      },
      {
        "modelId": null,
        "versionId": 15,
        "quantity": 1
      }
    ]
  },
  "meta": null
}
```

#### F. Mã lỗi có thể xảy ra (Error Codes)
| HTTP Status | Error Code | Mô tả |
| :--- | :--- | :--- |
| `400 Bad Request` | `VALIDATION_FAILED` | `id` không hợp lệ hoặc dữ liệu body vi phạm ràng buộc |
| `400 Bad Request` | `INVALID_BUNDLE_PRICE` | `bundlePrice` lớn hơn `originalPrice` |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Chưa xác thực |
| `403 Forbidden` | `ACCESS_DENIED` | Không có quyền `MANAGER` |
| `404 Not Found` | `BUNDLE_NOT_FOUND` | Không tìm thấy bundle theo ID cung cấp |

---

### 3.3. Xóa hoặc Lưu trữ Gói Combo Bundle (Delete/Archive Bundle)

- **Mã chức năng**: `STR-17`
- **Endpoint**: `DELETE /api/management/admin/store/bundles/{id}`
- **Controller**: `DeleteBundleController.java`
- **Use Case**: `DeleteBundleUseCase.java`
- **Phân quyền**: Role `MANAGER`

#### A. Path Parameters
| Tham số | Kiểu dữ liệu | Bắt buộc | Mô tả |
| :--- | :--- | :--- | :--- |
| `id` | `Long` | **Có** | ID gói bundle (`bundleId`) cần xóa/ngưng phát hành |

#### B. Quy tắc nghiệp vụ (Business Rules)
1. **Kiểm tra sự tồn tại**:
   - Nếu `id` không tồn tại, trả về `404 BUNDLE_NOT_FOUND`.
2. **Bảo toàn dữ liệu đơn hàng (Order History Preservation)**:
   - Hệ thống tự động kiểm tra xem `productId` của bundle này đã từng có khách hàng đặt mua trong quá khứ hay chưa (`OrderItemRepository.existsByProductId`).
   - **Trường hợp đã phát sinh đơn hàng**: Hệ thống thực hiện Soft-delete an toàn: chuyển `status` của `BUNDLE` và `PRODUCT` sang `DELETED` (hoặc `ARCHIVED`), ẩn khỏi cửa hàng nhưng giữ nguyên tính toàn vẹn dữ liệu đơn hàng cũ.
   - **Trường hợp chưa từng có đơn hàng**: Thực hiện cascade soft-delete làm sạch danh mục.
3. **Xóa Cache (Redis Eviction)**:
   - Xóa ngay lập tức cache `store:product:{productId}` trên Redis để bundle biến mất khỏi kết quả tìm kiếm của khách hàng.

#### C. Ví dụ Request (cURL)
```bash
curl -X DELETE "http://localhost:8080/api/management/admin/store/bundles/10" \
  -H "Authorization: Bearer YOUR_MANAGER_JWT_TOKEN"
```

#### D. Response 204 No Content
- **HTTP Status**: `204 No Content`
- **Body**: Trống (Không có body trả về)

#### E. Mã lỗi có thể xảy ra (Error Codes)
| HTTP Status | Error Code | Mô tả |
| :--- | :--- | :--- |
| `400 Bad Request` | `INVALID_PARAMETER` | `id` không phải số nguyên dương |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Chưa xác thực |
| `403 Forbidden` | `ACCESS_DENIED` | Không có quyền `MANAGER` |
| `404 Not Found` | `BUNDLE_NOT_FOUND` | Bundle không tồn tại trong hệ thống |

---

## 4. Bổ trợ: Các API hỗ trợ tương tác với Bundle

Để xây dựng giao diện hoàn chỉnh, Frontend cần phối hợp các API bổ trợ sau:

| Thao tác | Method & Endpoint | Mô tả |
| :--- | :--- | :--- |
| **Xem danh sách Bundle** | `GET /api/management/admin/store/bundles?page=0&size=10&status=UNPUBLISHED` | Lấy danh sách bundle có phân trang, lọc theo status (`UNPUBLISHED`, `PUBLISHED`, `ARCHIVED`, `DELETED`). Trả về cả `originalPrice`, `bundlePrice`, và `itemCount`. |
| **Đổi trạng thái Publish/Unpublish** | `PATCH /api/management/admin/store/products/{productId}/status` | Chuyển trạng thái phát hành của bundle (`UNPUBLISHED` ↔ `PUBLISHED` / `ARCHIVED`). |
| **Lấy danh sách LED Hardware** | `GET /api/management/admin/led-models` | Lấy danh sách LED models có sẵn trong kho để chọn vào dropdown cấu thành (`modelId`). |
| **Lấy danh sách Character Version** | `GET /api/management/admin/characters` & `GET /api/management/characters/{id}/versions` | Lấy danh sách phiên bản animation có sẵn để chọn vào dropdown cấu thành (`versionId`). |
| **Lấy danh mục Tags** | `GET /api/store/tags` | Lấy danh sách nhãn danh mục để gán vào bundle (`tagIds`). |

---

## 5. Ví dụ TypeScript Types cho Frontend Integration

```typescript
export interface BundleConstituentItem {
  modelId?: number | null;
  versionId?: number | null;
  quantity: number;
}

export interface CreateBundlePayload {
  bundleName: string;
  description?: string;
  thumbnailUrl?: string;
  galleryUrls?: string[];
  bundlePrice: number;
  tagIds?: number[];
  items: BundleConstituentItem[];
}

export interface UpdateBundlePayload {
  bundleName: string;
  description?: string;
  thumbnailUrl?: string;
  bundlePrice: number;
  items?: BundleConstituentItem[];
}

export interface ManagedBundleItem {
  bundleId: number;
  productId: number;
  bundleName: string;
  description: string | null;
  originalPrice: number;
  bundlePrice: number;
  status: 'UNPUBLISHED' | 'PUBLISHED' | 'ARCHIVED' | 'DELETED';
  itemCount: number;
}
```


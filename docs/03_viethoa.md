# Tóm tắt lý thuyết Spring Web & REST API (Thi trắc nghiệm & Rà soát nhanh)

## 1. Mục tiêu (Objectives)
* Nắm vững luồng đi của HTTP Request qua `DispatcherServlet` (Spring MVC).
* Tạo REST Controllers với các request mapping rõ ràng.
* Phân biệt và chọn đúng giữa Path Variable, Query Parameter, Request Header và Request Body.
* Thiết kế mô hình DTO (Request/Response) tách biệt với JPA Entities.
* Tách biệt rõ ràng trách nhiệm giữa Controller - Service - Repository.
* Áp dụng nguyên tắc RESTful, mã trạng thái HTTP (Status Codes) chuẩn xác.

## 2. Spring MVC trong Spring Boot Application
* **Khai báo dependency:** Thêm `spring-boot-starter-web` vào `pom.xml`.
* **Tự động cung cấp:**
  * Framework Spring MVC.
  * Web Server nhúng (Embedded Servlet Container - mặc định là Tomcat).
  * Thư viện Jackson phục vụ chuyển đổi JSON (Serialization / Deserialization).
  * Cấu hình mặc định cho MVC và Validation.

## 3. Front Controller Pattern & DispatcherServlet
* **Mẫu thiết kế Front Controller:** Sử dụng một Servlet trung tâm duy nhất (`DispatcherServlet`) để tiếp nhận mọi HTTP Request đầu vào và điều phối tới các linh kiện chuyên biệt.
* **Vai trò của `DispatcherServlet`:** Điều phối luồng xử lý (Mapping, Parameter Binding, Validation, Serialization, Exception handling). **TUYỆT ĐỐI KHÔNG** chứa logic nghiệp vụ ứng dụng ở đây.
* **Anti-pattern cần tránh:** Rò rỉ các đối tượng Servlet thô (như `HttpServletRequest`, `HttpServletResponse`) vào tầng Service. Tầng Service phải độc lập hoàn toàn với hạ tầng Web.

## 4. Luồng đi chi tiết của HTTP Request (Detailed Request Flow)
* **Quy trình 11 bước khi client gọi `GET /api/books/42`:**
  1. Client gửi HTTP Request.
  2. Servlet Container (Tomcat) chuyển request tới `DispatcherServlet`.
  3. `HandlerMapping` tìm kiếm phương thức Controller phù hợp với URL/HTTP Method.
  4. Chọn phương thức Controller tương ứng.
  5. `HandlerAdapter` chuẩn bị và thực thi phương thức được chọn.
  6. **Argument Resolvers** bóc tách các tham số (`@PathVariable`, `@RequestParam`, `@RequestBody`...).
  7. Controller ủy quyền xử lý nghiệp vụ cho Service.
  8. Controller nhận kết quả (Object/DTO) trả về từ Service.
  9. **Return-value handlers** xử lý HTTP Status Code và Response Headers.
  10. **`HttpMessageConverter`** (Jackson) dịch Object Java thành chuỗi JSON.
  11. Trả HTTP Response (JSON) về cho Client.
* **Các linh kiện trọng yếu (Key Components):**
  * `HandlerMapping`: Bản đồ dò tìm Controller.
  * `HandlerAdapter`: Bộ thực thi Controller.
  * `ArgumentResolvers`: Bộ nhặt tham số từ request.
  * `HttpMessageConverter`: Bộ dịch chuyển đổi dữ liệu (Java <-> JSON).
  * `ExceptionResolvers`: Bộ bắt lỗi và chuyển đổi thành HTTP Response.

## 5. Controllers và `@RestController`
* **`@Controller`:** Dùng cho ứng dụng Web truyền thống render HTML ở server. Phương thức trả về tên View (giao diện).
* **`@RestController`:** Kết hợp giữa `@Controller` + `@ResponseBody`. Dùng cho REST API. Kết quả trả về của hàm sẽ được tự động dịch sang chuỗi JSON và ghi trực tiếp vào HTTP Response Body.

## 6. Request Mappings - Ánh xạ HTTP Request
* **`@RequestMapping`:** Khai báo đường dẫn gốc (Base URL) chung cho cả Class Controller (ví dụ: `@RequestMapping("/api/books")`).
* **Các Annotation HTTP chuyên biệt:**
  * `@GetMapping`: Đọc dữ liệu (Read).
  * `@PostMapping`: Tạo mới dữ liệu (Create).
  * `@PutMapping`: Thay thế hoàn toàn một đối tượng (Full Update).
  * `@PatchMapping`: Cập nhật một phần đối tượng (Partial Update).
  * `@DeleteMapping`: Xóa dữ liệu (Delete).
* **Quy tắc đặt tên URI chuẩn RESTful:**
  * **ĐÚNG:** Dùng danh từ số nhiều thể hiện tài nguyên (`GET /api/books`, `POST /api/books`, `DELETE /api/books/42`).
  * **SAI (Anti-pattern):** Chèn động từ hành động vào URL (`POST /api/createBook`, `GET /api/getAllBooks`). Lấy phương thức HTTP để thể hiện hành động.

## 7. Path Variables vs Query Parameters
* **`@PathVariable` (Đường dẫn):** Dùng để **định danh một tài nguyên cụ thể** hoặc mối quan hệ phân cấp (`GET /api/books/42`, `GET /api/authors/7/books/42`).
* **`@RequestParam` (Tham số truy vấn):** Dùng để **lọc (filter), tìm kiếm, phân trang (pagination), và sắp xếp (sorting)** trên một tập hợp danh sách (`GET /api/books?title=spring&page=0&size=20`).
* **Bảng quy tắc chọn lựa:**
  * Định danh 1 tài nguyên cụ thể -> `@PathVariable` (VD: `/books/{id}`)
  * Thể hiện mối quan hệ phân cấp -> `@PathVariable` (VD: `/authors/{authorId}/books/{bookId}`)
  * Lọc / Phân trang / Sắp xếp danh sách -> `@RequestParam` (VD: `?status=OPEN&page=0`)
  * Dữ liệu tạo mới/cập nhật phức tạp -> `@RequestBody`
  * Dữ liệu metadata (Token, Locale...) -> `@RequestHeader`
* **Lỗi thiết kế (Bad design):** Dùng `GET /api/books?id=42` để lấy 1 quyển sách thay vì `GET /api/books/42`.

## 8. Request Headers và Request Bodies
* **`@RequestHeader`:** Đọc các thông tin metadata của HTTP Request (như Authorization, Correlation-Id, Accept-Language...).
* **`@RequestBody`:** Đọc toàn bộ chuỗi JSON từ HTTP Request Body và cho `HttpMessageConverter` dịch thành một Java Request DTO. Một HTTP Request chỉ có tối đa 1 Body.

## 9. DTOs (Data Transfer Objects) - Mô hình Request & Response
* **Khái niệm:** DTO là đối tượng vận chuyển dữ liệu qua ranh giới ứng dụng (giữa Client và Server).
* **Tại sao TUYỆT ĐỐI KHÔNG trả về JPA Entity trực tiếp ra API?**
  1. Rò rỉ các thuộc tính nhạy cảm hoặc nội bộ (như `passwordHash`, DB audit fields).
  2. Lỗi `LazyInitializationException` hoặc truy vấn ngầm rác (N+1) do quan hệ Lazy Loading.
  3. Lỗi lặp vô tận (Infinite Recursion) do quan hệ 2 chiều giữa các Entity.
  4. Thay đổi bảng DB sẽ làm hỏng (break) giao ước API của Client.
  5. Client có thể tự gửi các trường không được phép sửa (như `id`, `createdAt`, `availableSeats`).
* **Quy tắc phân tách DTO:**
  * **Request DTO (ví dụ `CreateEventRequest`):** Chỉ chứa các trường Client ĐƯỢC PHÉP gửi lên (không chứa `id`, `createdAt`).
  * **Response DTO (ví dụ `EventResponse`):** Chứa các trường trả về cho Client hiển thị.
* **Anti-pattern:** Dùng chung 1 DTO hoặc dùng thẳng Entity cho mọi thao tác Create, Update, Response.

## 10. Chuyển đổi dữ liệu (Mapping Entities <-> DTOs)
* **Mapper Class:** Tạo một class `@Component` (ví dụ `EventMapper`) chứa các hàm chuyển đổi thuần túy: `toEntity(CreateRequest)` và `toResponse(Entity)`.
* **Quy tắc Mapper:** Mapper chỉ làm nhiệm vụ ánh xạ thuộc tính, **TUYỆT ĐỐI KHÔNG thực hiện thao tác chọc Database (I/O)** bên trong Mapper.

## 11. Phân tầng kiến trúc (Layered Design)
* **Mô hình 3 tầng chuẩn:** `Client -> Controller -> Service -> Repository -> Database`. Chiều phụ thuộc luôn hướng từ ngoài vào trong.
* **Trách nhiệm tầng Controller (HTTP Boundary):**
  * Khai báo route, HTTP Method, nhận tham số (`@PathVariable`, `@RequestBody`...).
  * Kích hoạt validation (`@Valid`).
  * Gọi sang Service xử lý.
  * Quyết định HTTP Status Code và Response Headers.
  * **KHÔNG ĐƯỢC:** Viết logic nghiệp vụ, gọi DB, tự mở Transaction, hay trả về Entity.
* **Trách nhiệm tầng Service (Business Boundary):**
  * Độc lập hoàn toàn với hạ tầng Web (không biết URL, không trả `ResponseEntity`).
  * Thực thi logic nghiệp vụ, kiểm tra điều kiện (ví dụ check trùng email).
  * Quản lý giao dịch (`@Transactional`).
* **Trách nhiệm tầng Repository (Data Access Boundary):**
  * Chỉ lo đọc/ghi dữ liệu với Database. KHÔNG quyết định HTTP Status Code hay chứa logic nghiệp vụ.

## 12 & 13. Mẫu Controller mỏng (Thin Controller) & Anti-patterns
* **Thin Controller:** Controller chỉ đóng vai trò "người gác cổng" hứng HTTP request rồi ủy quyền cho Service.
* **Anti-pattern 1: Fat Controller:** Viết hết logic kiểm tra, lưu DB, gửi mail... bên trong Controller. Dẫn đến code khó Unit Test và không thể tái sử dụng.
* **Anti-pattern 2: Pass-through Service:** Service chỉ là một hàm rỗng bọc lại lệnh gọi `repository.findAll()`. Service phải đóng vai trò là ranh giới quản lý nghiệp vụ và giao dịch.

## 14. Nguyên tắc REST (REST Principles)
* **14.1. Thiết kế đường dẫn hướng tài nguyên (Resource-oriented URIs):**
  * URI phải là danh từ số nhiều (tài nguyên), tránh dùng động từ.
  * **NÊN DÙNG:** `GET /api/books`, `POST /api/books`, `DELETE /api/books/42`.
  * **TRÁNH DÙNG:** `POST /api/createBook`, `GET /api/getAllBooks`, `DELETE /api/deleteBook?id=42`.
  * Với hành động đặc thù (không phải CRUD), mô tả như một tài nguyên phụ: `POST /api/orders/91/cancellations`.
* **14.2. Đồng nhất ngữ nghĩa HTTP (Uniform HTTP semantics):**
  * `GET`: An toàn (Safe), không làm thay đổi dữ liệu trên hệ thống.
  * `PUT` & `DELETE`: Lũy đẳng (Idempotent) - gọi nhiều lần có kết quả cuối cùng trên DB như gọi 1 lần.
  * `POST`: Không lũy đẳng (mỗi lần gọi tạo 1 tài nguyên mới).
  * `PATCH`: Cập nhật một phần của tài nguyên.
* **14.3. Không trạng thái (Stateless requests):** Mỗi request phải mang đầy đủ thông tin để xử lý (ví dụ Token), không dùng session/hội thoại lưu tạm trên server.
* **14.4. Định dạng biểu diễn (Representations):** Sử dụng các header `Content-Type` và `Accept` để giao tiếp định dạng dữ liệu (thường là `application/json`).
* **14.5. Cache Semantics:** Vì các request `GET` mặc định được lưu cache, tuyệt đối không làm thay đổi trạng thái dữ liệu (ghi DB/xóa dữ liệu) trong các phương thức `@GetMapping`.

## 15. Mã trạng thái HTTP (HTTP Status Codes)
* **Thành công (2xx):**
  * `200 OK`: Thành công (GET/PUT/PATCH có trả dữ liệu).
  * `201 Created`: Tạo tài nguyên thành công (POST), thường đi kèm header `Location`.
  * `204 No Content`: Thành công nhưng không có dữ liệu ở body (DELETE/Hủy).
* **Lỗi phía Client (4xx):**
  * `400 Bad Request`: Sai định dạng cú pháp, lỗi validation đầu vào.
  * `401 Unauthorized`: Chưa đăng nhập hoặc token không hợp lệ (Chưa xác thực).
  * `403 Forbidden`: Đã đăng nhập nhưng không có quyền hạn thao tác (Chưa phân quyền).
  * `404 Not Found`: Tài nguyên không tồn tại.
  * `409 Conflict`: Xung đột dữ liệu (Trùng email, trùng lịch đăng ký).
* **Lỗi phía Server (5xx):**
  * `500 Internal Server Error`: Lỗi hệ thống phát sinh ngầm chưa được xử lý.
* **Anti-pattern:** Trả về `200 OK` cho tất cả các lỗi rồi đóng gói cờ lỗi trong body.
* **Phân biệt `401` vs `403`:** `401` là chưa xác định danh tính (chưa đăng nhập); `403` là đã biết danh tính nhưng từ chối thực hiện vì thiếu quyền.

## 16. ResponseEntity - Tự do kiểm soát HTTP Response
* **Khái niệm:** Đại diện cho toàn bộ gói tin phản hồi HTTP (gồm Status Code, Headers, và Body).
* **Khi nào sử dụng:**
  * Cần tự cấu hình HTTP Status Code khác `200 OK` (như `201 Created` kèm Location header, `204 No Content`).
  * Cần thêm các custom headers vào HTTP Response.
* **Quy tắc sử dụng:**
  * Nếu endpoint chỉ cần trả về dữ liệu thuần túy với mã mặc định `200 OK`, có thể trả trực tiếp DTO mà không cần bọc trong `ResponseEntity`.
  * **TUYỆT ĐỐI KHÔNG** trả về `ResponseEntity` từ tầng Service. Nó là mối quan tâm thuộc về tầng Web (Controller).

## 17. Giao ước Phân trang (Pagination Contract)
* **Tại sao bắt buộc:** Tránh tải lượng lớn dữ liệu làm chậm đường truyền, nghẽn bộ nhớ RAM và treo ứng dụng Client.
* **Cơ chế hoạt động:**
  * Tham số gửi lên (Request): `page` (chỉ số trang), `size` (kích thước trang), `sort` (quy tắc sắp xếp).
  * **Chỉ số 0-based:** Trong Spring, trang đầu tiên có chỉ số là **0**. Trang có chỉ số là **1** thực chất là trang thứ **2** trong thực tế.
  * Hợp đồng trả về (Response): Không trả về trực tiếp đối tượng Page của Spring Data mà nên tự bọc trong một DTO phân trang (ví dụ `PageResponse<T>`) chứa: dữ liệu trang hiện tại, số trang, kích thước, tổng số trang, tổng số bản ghi.
* **Quy tắc thiết kế:**
  * Phải giới hạn kích thước trang tối đa (`size` limit) để tránh bị tấn công làm sập hệ thống.
  * Phải sắp xếp đồng nhất (luôn có ID làm tie-breaker khi sắp xếp) để dữ liệu không bị đảo lộn giữa các trang.

## 18. Thiết kế API chuẩn hóa (API Design Best Practices)
* **Đồng nhất kiểu đặt tên (Consistent naming):** Chọn duy nhất một kiểu định dạng đặt tên JSON (thường dùng `camelCase`) cho toàn bộ dự án (ví dụ `createdAt`, `eventId`), tránh trộn lẫn các style khác nhau.
* **Định dạng dữ liệu rõ ràng:**
  * Ngày giờ: Dùng chuẩn quốc tế ISO 8601 kèm múi giờ UTC (chữ `Z` ở cuối, ví dụ: `2026-07-20T09:20:48Z`).
  * Tiền tệ: Bắt buộc dùng kiểu dữ liệu `BigDecimal` trong Java để tính toán số lẻ chính xác, không dùng `float` hay `double` do sai số nhị phân.
* **Không rò rỉ chi tiết lỗi hệ thống (No internal leaks):** Không bao giờ trả về Stack Trace, mã SQL thô, cấu trúc thư mục hay các thông tin mật cho Client khi xảy ra lỗi (tránh lỗ hổng bảo mật).
* **Phân chia tầng kiểm tra dữ liệu:**
  * Validate cấu trúc (kiểm tra định dạng, không trống...) -> Xử lý ở tầng Controller.
  * Validate nghiệp vụ (kiểm tra logic, độc nhất...) -> Xử lý ở tầng Service và Database constraints.

## 19. Các lỗi thiết kế API thường gặp (Common Anti-patterns)
* **Fat Controller (Controller cồng kềnh):** Viết trực tiếp logic nghiệp vụ, gọi repository hoặc thao tác lưu DB ngay trong Controller. Cần chuyển logic này xuống tầng Service để dễ Unit Test và tái sử dụng.
* **Pass-through Service (Service trung chuyển vô nghĩa):** Service chỉ gọi trực tiếp Repository mà không áp dụng thêm bất kỳ nghiệp vụ, phân quyền hay giao dịch nào.
* **Generic Response Envelope cho mọi API:** Bọc tất cả phản hồi trong một dạng `{ status, message, data }` chung chung, lặp lại thông tin của giao thức HTTP và gây rác payload.
* **One DTO for everything (Dùng chung 1 model):** Dùng duy nhất một Class cho tất cả các hành động tạo mới, cập nhật và hiển thị. Dễ làm rò rỉ dữ liệu hoặc cho phép client ghi đè các trường cấm (ví dụ: client tự gửi `id` hoặc `role` khi cập nhật).
* **Trả về `null` khi thiếu tài nguyên:** Khi không tìm thấy dữ liệu, trả về `null` kèm mã `200 OK` thay vì quăng Exception để trả về mã lỗi `404 Not Found` tương ứng. Dễ gây ra lỗi `NullPointerException` (NPE) ngầm.

## 20. Phân tích thiết kế thực tế (Endpoint Design Exercise)
* Chức năng thực hiện hành động nghiệp vụ phức tạp nên được mô hình hóa thành một tài nguyên phụ (sub-resource) bằng phương thức `POST` thay vì dùng `PATCH` sửa trạng thái đơn giản (ví dụ: `POST /api/loans/{id}/returns` tốt hơn `PATCH /api/loans/{id}`).
* Khi phân tích DTO: Cần bóc tách các trường Client được gửi lên và các trường Server tự động sinh ra/quản lý.
* Lỗi xung đột nghiệp vụ logic (như mượn sách đã hết, đăng ký sự kiện hết chỗ) được coi là lỗi xung đột trạng thái hệ thống, nên được ánh xạ về mã **`409 Conflict`**.

## 21. Danh sách kiểm duyệt API (Review Checklist)
* **URI:** Danh từ số nhiều, không chứa động từ hành động.
* **HTTP Method:** Đúng ngữ nghĩa (GET để đọc, POST tạo mới, PUT/PATCH sửa, DELETE xóa).
* **Tham số:** `@PathVariable` định danh tài nguyên, `@RequestParam` để lọc/phân trang/sắp xếp.
* **DTO:** Sử dụng DTO đầu vào/đầu ra riêng biệt. Không phơi bày JPA Entities trực tiếp.
* **Controller mỏng:** Chỉ xử lý đầu vào HTTP, validate cơ bản, chuyển giao cho Service và quyết định mã Status trả về.
* **Service độc lập với Web:** Không chứa các kiểu dữ liệu của Web Spring (như `ResponseEntity`, `HttpServletRequest`).
* **HTTP Status rõ ràng:** Trả về mã lỗi/thành công chuẩn xác thay vì luôn trả về 200 OK.
* **Giới hạn phân trang:** Mọi API lấy danh sách phải có phân trang và giới hạn size tối đa.
* **Đồng nhất:** camelCase cho JSON và ISO 8601 UTC cho ngày giờ.

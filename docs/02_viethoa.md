# Tóm tắt lý thuyết Spring Core (Thi trắc nghiệm & Rà soát nhanh)

## 1. Mục tiêu (Objectives)
* Nắm vững IoC, DI, 3 kiểu Injection.
* Phân biệt Spring Bean vs POJO thông thường.
* Hiểu vai trò của `ApplicationContext` và Startup Flow.
* Nắm được Bean Scope (Singleton vs Prototype) & Vòng đời (Bean Lifecycle).
* Xử lý lỗi DI phổ biến (`NoSuchBeanDefinitionException`, `NoUniqueBeanDefinitionException`).

## 2. Inversion of Control (IoC) - Đảo ngược quyền kiểm soát
* **Định nghĩa:** Quyền tạo và quản lý đối tượng chuyển từ class nghiệp vụ sang cho Container/Framework.
* **Không dùng IoC:** Class tự dùng `new` để tạo phụ thuộc -> Tự kiểm soát -> Code bị dính chặt (tight coupling), khó viết unit test.
* **Dùng IoC:** Class khai báo những gì nó cần, Container tự tạo và bơm vào -> Đảo ngược quyền kiểm soát.

## 3. Dependency Injection (DI) - Tiêm phụ thuộc
* **Định nghĩa:** Kỹ thuật hiện thực hóa IoC, đối tượng nhận phụ thuộc từ bên ngoài thay vì tự tạo.
* **3 kiểu Injection:**
  1. **Constructor Injection (Khuyên dùng):** 
     * Phụ thuộc bắt buộc.
     * Cho phép dùng biến `final` (an toàn thread-safe).
     * Tránh đối tượng bị rỗng (invalid state).
     * Unit test cực dễ (không cần chạy Spring).
     * Tự phát hiện lỗi vòng lặp (Circular Dependency) lúc chạy app.
     * Chỉ có 1 constructor thì không cần viết `@Autowired`.
  2. **Setter Injection:** Phụ thuộc tùy chọn (optional/reconfigurable). Dùng `@Autowired` trên hàm setter. Biến không thể để `final`.
  3. **Field Injection (Tránh dùng):** Dùng `@Autowired` trên thuộc tính private. 
     * *Nhược điểm:* Ẩn giấu phụ thuộc, không dùng được `final`, khó unit test (phải dùng Reflection hoặc Spring context), dễ vi phạm SRP (class phình to).
* **Chú ý:** DI là một mẫu thiết kế, có thể thực hiện thủ công bằng Java thuần mà không cần Spring.

## 4. Spring Bean
* **Định nghĩa:** Đối tượng Java được tạo, cấu hình và quản lý bởi Spring Container.
* **Phân biệt Bean vs Non-Bean:**
  * **LÀ Bean (Ứng dụng chạy lâu dài, lo hạ tầng/logic):** Controller, Service, Repository, Configuration, HttpClient, Mapper, Clock...
  * **KHÔNG LÀ Bean (Dữ liệu ngắn hạn, động):** JPA Entities (dữ liệu DB), DTOs (Request/Response), Value Objects, kiểu dữ liệu cơ bản (`String`, `List`, `Map`...).
* **Bean Definition:** Bản thiết kế của một Bean chứa: kiểu dữ liệu, tên bean, các dependency cần tiêm, scope, và các callback vòng đời.

## 5. ApplicationContext - Spring IoC Container
* **Định nghĩa:** Bộ não/Container của Spring quản lý vòng đời và cấu hình của Beans.
* **Quy trình Startup Flow:**
  1. Đọc metadata (Annotations `@Component`, `@Configuration`, auto-config).
  2. Đăng ký Bean Definitions (Bản thiết kế).
  3. Giải quyết quan hệ phụ thuộc (Dependency Resolution).
  4. Khởi tạo & nạp các Singleton Beans vào bộ nhớ.
  5. Sẵn sàng hoạt động (Application Ready).
* **Lưu ý thiết kế:** Tránh gọi trực tiếp `context.getBean(...)` vì sẽ biến Container thành Service Locator (một anti-pattern). Nên dùng Constructor Injection.

## 6. Component Scanning & Stereotypes - Quét linh kiện tự động
* **Cơ chế:** Spring quét toàn bộ các class trong dự án từ package của class Main đi xuống. Những class nào có đánh dấu **Stereotype Annotation** sẽ được tự động tạo làm Bean.
* **Các Stereotype chính:**
  * `@Component`: Chú thích chung chung cho các class logic phụ trợ.
  * `@RestController`: Dùng cho tầng Controller (nhận request HTTP và trả JSON).
  * `@Service`: Dùng cho tầng Service xử lý logic nghiệp vụ.
  * `@Repository`: Dùng cho tầng Repository giao tiếp DB (tự dịch lỗi DB sang Spring exception).
  * `@Configuration`: Dùng cho class cấu hình, chứa các hàm `@Bean`.
* **Lưu ý package:** Mặc định chỉ quét từ thư mục cha chứa class Main đi xuống các thư mục con. Class nằm ở nhánh ngang hàng hoặc ngoài thư mục chính sẽ bị bỏ sót.

## 7. Java Configuration (`@Configuration` & `@Bean`)
* **Khái niệm:** Dùng code Java để định nghĩa cách khởi tạo và cấu hình đối tượng.
* **Khi nào sử dụng:**
  1. Thư viện bên thứ 3 (không sửa được source code để gắn `@Component`).
  2. Khởi tạo đối tượng cần truyền tham số cấu hình phức tạp.
  3. Cần tạo nhiều Bean khác nhau từ cùng một Class (ví dụ 2 DataSource).
* **Quy tắc `@Bean`:** Tham số truyền vào hàm `@Bean` sẽ được Spring Container tự tìm kiếm và tiêm vào tự động (như Constructor Injection).
* **So sánh nhanh:**
  * `@Component`: Đặt ở đầu Class. Dành cho code mình viết. Spring tự `new`.
  * `@Bean`: Đặt ở đầu phương thức (Method) bên trong class `@Configuration`. Dành cho thư viện ngoài. Dev tự viết lệnh khởi tạo (`new`).

## 8. Dependency Resolution - Giải quyết tranh chấp Bean
* **Nguyên tắc mặc định:** Spring tìm và tiêm Bean theo **Kiểu dữ liệu (By Type)**.
* **Xử lý tranh chấp (Trùng kiểu dữ liệu):**
  * `@Primary`: Đánh dấu Bean ưu tiên mặc định. Khi có tranh chấp, Spring tự chọn Bean này.
  * `@Qualifier("tên_bean")`: Chỉ định chính xác tên Bean muốn bơm. `@Qualifier` **luôn có độ ưu tiên cao hơn và sẽ ghi đè (override)** `@Primary`.
* **Phụ thuộc tùy chọn (Optional):** Dùng `Optional<T>` hoặc `ObjectProvider<T>`. Tránh dùng bừa bãi cho các dependency bắt buộc chỉ để giấu lỗi khởi động.

## 9. Bean Scope - Phạm vi hoạt động của Bean
* **Định nghĩa:** Xác định số lượng instance của Bean được tạo ra và thời gian chúng tồn tại.
* **Các loại Scope chính:**
  * `singleton` (Mặc định): Chỉ có duy nhất 1 instance trên toàn bộ Spring Container.
  * `prototype`: Mỗi lần yêu cầu (inject hoặc gọi `getBean`) sẽ tạo 1 instance hoàn toàn mới.
  * `request` (Web): 1 instance duy nhất cho mỗi HTTP request.
  * `session` (Web): 1 instance duy nhất cho mỗi phiên làm việc (HTTP Session) của user.
* **Lưu ý chí mạng:** Vì Controller và Service mặc định là `singleton` (nhiều request chạy song song cùng dùng chung), nên **TUYỆT ĐỐI KHÔNG lưu dữ liệu động của request vào thuộc tính (field) của Class** để tránh lỗi tranh chấp dữ liệu (Race Condition). Service/Controller phải giữ trạng thái **không lưu dữ liệu động (stateless)**, chỉ truyền nhận dữ liệu qua tham số hàm (local variables).

## 10. Bean Lifecycle - Vòng đời của Bean
* **Quy trình vòng đời rút gọn của Singleton Bean (Bắt buộc nhớ thứ tự):**
  1. **Instantiate:** Gọi Constructor để tạo đối tượng thô trong bộ nhớ trước.
  2. **Populate properties:** Tiêm các dependency cần thiết vào (DI).
  3. **PostConstruct:** Chạy phương thức được đánh dấu `@PostConstruct` để khởi tạo cấu hình nâng cao.
  4. **Ready:** Bean sẵn sàng phục vụ ứng dụng.
  5. **PreDestroy:** Chạy phương thức được đánh dấu `@PreDestroy` để dọn dẹp tài nguyên trước khi đóng Container (tắt app).
* **Cơ chế Proxy (Bọc Bean):** Sau khi khởi tạo, Spring có thể bọc Bean thật trong một Proxy đối tượng giả lập để áp dụng các tính năng nâng cao (như `@Transactional` quản lý transaction, bảo mật, cache). Cuộc gọi từ bên ngoài sẽ đi qua Proxy trước rồi mới tới Bean thật.

## 11. Circular Dependencies - Lỗi vòng lặp vô tận
* **Khái niệm:** Xảy ra khi Bean A phụ thuộc Bean B, và Bean B cũng phụ thuộc Bean A (`A -> B -> A`). Với Constructor Injection, Spring Container không thể xác định được nên khởi tạo Bean nào trước -> Crash app ngay khi boot.
* **Cách khắc phục đúng đắn (Redesign):** Không nên dùng `@Lazy` để chữa cháy tạm thời vì đó là biểu hiện của thiết kế code bị lỗi (code smell). Thay vào đó nên:
  1. Tách phần logic chung ra một Service thứ ba.
  2. Sử dụng cơ chế phát/lắng nghe Sự kiện (Application Events) thay vì gọi trực tiếp.

## 12. Các lỗi thường gặp (Common Problems)
* **NoSuchBeanDefinitionException:** Thiếu Bean trong Container. Kiểm tra xem đã thêm annotation chưa, package scan có quét trúng không, hoặc profile/condition có tắt nó không.
* **NoUniqueBeanDefinitionException:** Trùng kiểu dữ liệu Bean. Xử lý bằng cách chỉ định rõ bằng `@Qualifier` hoặc đặt `@Primary`.
* **Tự dùng từ khóa `new` trên Bean do Spring quản lý (Lỗi kinh điển):** Nếu tự dùng `new` để tạo đối tượng của một Service/Controller, đối tượng đó sẽ không được Spring quản lý -> các phụ thuộc bên trong nó sẽ bị `null` (gây `NullPointerException` khi gọi) và `@Transactional` sẽ không chạy.

## 13. Hướng dẫn thực chiến (Practical Guidelines)
* Luôn ưu tiên dùng **Constructor Injection** cho phụ thuộc bắt buộc.
* Giữ các Singleton Beans luôn **stateless** (không lưu trạng thái động).
* Sử dụng đúng **Stereotype Annotations** cho từng tầng (Controller, Service, Repository).
* Dùng `@Bean` cho thư viện bên thứ ba hoặc đối tượng cần cấu hình phức tạp.
* Không chọc trực tiếp vào `ApplicationContext` từ code nghiệp vụ.

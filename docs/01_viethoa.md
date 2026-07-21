# Tóm tắt lý thuyết Spring Framework Overview (Thi trắc nghiệm & Rà soát nhanh)

## 1. Mục tiêu (Objectives)
* Giải thích được các vấn đề Spring giải quyết trong ứng dụng Java Enterprise.
* Phân biệt được các module trong Spring Framework.
* Phân biệt được Spring Framework vs Spring Boot.
* Hiểu khái niệm Starters, Convention over Configuration, và Auto-configuration.

## 2. Tại sao Spring tồn tại? (Why Spring Exists)
* **Vấn đề Java truyền thống:** Dev phải tự viết code hạ tầng phức tạp (kết nối DB, mapping HTTP, quản lý transaction, bảo mật...). Code nghiệp vụ bị dính chặt với code cấu hình -> Khó bảo trì, khó unit test.
* **Giải pháp của Spring:** Cung cấp các công cụ cấu hình sẵn và các lớp trừu tượng (abstractions) để giải quyết các vấn đề hạ tầng này, giúp dev tập trung vào logic nghiệp vụ.

## 3. Spring Framework là gì?
* Là một bộ khung (framework) modular gồm nhiều module xử lý các mảng khác nhau:
  * **Core/Context:** Quản lý đối tượng (IoC, DI, Beans).
  * **Web MVC:** Xử lý HTTP Request, xây dựng REST API.
  * **Data Access / JDBC / ORM:** Kết nối và thao tác với Database.
  * **TX (Transactions):** Quản lý giao dịch tự động thông qua `@Transactional`.
  * **AOP (Aspect-Oriented Programming):** Xử lý các vấn đề dùng chung như Logging, Security, Transactions thông qua cơ chế proxy/interception.
  * **Test:** Hỗ trợ viết test dễ dàng (Spring Test).
* **Hệ sinh thái rộng lớn:** Gồm Spring Framework (lõi), Spring Boot (setup nhanh), Spring Data (thao tác dữ liệu), Spring Security (bảo mật), Spring Cloud (Microservices)...

## 4. Lợi ích & Đánh đổi của Spring
* **Lợi ích (Benefits):**
  * **Loose coupling (Liên kết lỏng lẻo):** Lập trình hướng giao diện (interface) nhờ DI.
  * **Testability:** Constructor injection giúp viết Unit Test nhanh bằng Mock mà không cần khởi tạo Spring context.
  * Giảm code rác (boilerplate code), đồng nhất mô hình lập trình trên toàn hệ sinh thái.
* **Đánh đổi/Chi phí (Costs):**
  * Auto-configuration dễ gây "ảo thuật" nếu dev không hiểu cơ chế bên dưới.
  * Cơ chế Proxy làm luồng chạy runtime khác với gọi hàm thông thường.
  * Tạo lỗi mất/trùng Bean nếu cấu hình Component Scan sai.
  * Loading Spring Context làm test chạy chậm hơn.

## 5. Spring Framework vs Spring Boot
* **Spring Boot được xây dựng TRÊN Spring Framework** (không thay thế Spring Framework).
* **So sánh nhanh:**
  * **Spring Framework:** Cung cấp container quản lý Bean, dev phải tự cấu hình mọi thứ từ đầu (web server, XML/Java config).
  * **Spring Boot:** Auto-config các cấu hình mặc định (như nhúng sẵn Tomcat, tự cấu hình MVC, tự tạo Datasource khi thấy driver DB), cung cấp các gói thư viện đi kèm sẵn (Starters) và Actuator để giám sát.

## 6. Starters & Dependency Management
* **Starter:** Là gói thư viện gom sẵn các dependency tương thích với nhau để giải quyết một chức năng cụ thể (ví dụ `spring-boot-starter-web` chứa Spring MVC, Jackson, Tomcat...).
* **Lưu ý:** Starter **không sinh ra code** (không phải code generator). Nó chỉ đặt các thư viện tương thích lên classpath để dùng.

## 7. Auto-configuration & Convention over Configuration
* **Auto-configuration:** Tự cấu hình ứng dụng dựa trên: thư viện có trên classpath, các bean đã tự khai báo trước đó, các properties cấu hình, và kiểu ứng dụng.
* **Cơ chế Back-off:** Nếu dev đã khai báo một Bean thủ công, auto-config của Spring Boot sẽ tự động rút lui (nhường quyền cho Bean của dev).
* **Convention over Configuration (Cấu hình theo quy ước):** Mặc định chạy cổng `8080`, đọc file `application.properties` hoặc `application.yml`, tự động scan package từ vị trí class main đi xuống. Nếu muốn thay đổi chỉ cần ghi đè cấu hình.

## 8. Hiểu lầm phổ biến (Common Misconceptions)
* *Spring Boot bỏ cấu hình:* Sai, nó chỉ cung cấp cấu hình mặc định, dự án thực tế vẫn phải cấu hình DB, Security...
* *Mọi đối tượng đều là bean:* Sai, chỉ Controller, Service, Repository... là bean. Entity, DTO, Request/Response là POJO thường.
* *Annotation chứa logic nghiệp vụ:* Sai, annotation chỉ là metadata, logic vẫn nằm trong code Java.

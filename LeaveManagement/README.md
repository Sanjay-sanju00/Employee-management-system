# Employee Leave Management System (Java Swing + MySQL)

A desktop-based Employee Leave Management System developed using Java Swing and MySQL.  
The application allows employees to apply for leave, check leave status, and enables managers to manage employees and approve or reject leave requests.

---

## Features

### Employee
- Submit leave requests
- View leave request status
- Leave balance tracking

### Manager
- Approve or reject pending leave requests
- Add new employees
- Delete employees by ID
- View all pending leave requests

---

## Technologies Used

- Java (Swing for GUI)
- MySQL (Database)
- JDBC (Database Connectivity)

---

## Project Structure

- `LeaveManagementApp.java`  
  Contains:
  - MVC-based architecture
  - GUI (Swing)
  - Controller logic
  - Database interaction
  - Business logic classes

---

## Database Details

### Database Name

### Tables Required

#### employees
- id (VARCHAR, PRIMARY KEY)
- name (VARCHAR)
- leave_balance (INT)
- is_manager (BOOLEAN)

#### leave_requests
- request_id (INT, PRIMARY KEY, AUTO_INCREMENT)
- employee_id (VARCHAR, FOREIGN KEY)
- leave_type (VARCHAR)
- start_date (DATE)
- end_date (DATE)
- status (VARCHAR)

---

## Configuration (IMPORTANT)

Update database credentials in `DatabaseManager` class before running:

```java
private static final String DB_URL = "jdbc:mysql://localhost:3306/leave_system_db";
private static final String USER = "YOUR DB USERNAME";
private static final String PASS = "YOUR DB PASSWORD";

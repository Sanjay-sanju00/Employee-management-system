import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class LeaveManagementApp {

    public static final int LEAVE_DURATION_DAYS = 6;
    public static final int DEFAULT_LEAVE_BALANCE = 6;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                LeaveSystem model = new LeaveSystem();
                LeaveManagementView view = new LeaveManagementView();
                new LeaveManagementController(model, view);
                view.setVisible(true);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null,
                        "Could not connect to the database. Please check connection settings and ensure MySQL is running.\nError: " + e.getMessage(),
                        "Database Connection Error",
                        JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }

    static class LeaveManagementController {
        private LeaveSystem model;
        private LeaveManagementView view;
        private Manager currentManager;

        public LeaveManagementController(LeaveSystem model, LeaveManagementView view) {
            this.model = model;
            this.view = view;

            view.addMenuSubmitListener(_ -> view.switchToPanel("Submit"));
            view.addMenuViewStatusListener(_ -> view.switchToPanel("Status"));
            view.addMenuProcessListener(new ManagerActionAuthenticator("Process"));
            view.addMenuAddEmployeeListener(new ManagerActionAuthenticator("AddEmployee"));
            view.addMenuDeleteEmployeeListener(new ManagerActionAuthenticator("DeleteEmployee")); 
            view.addMenuExitListener(_ -> System.exit(0));

            ActionListener backToMenu = _ -> view.switchToPanel("Menu");
            ActionListener backToMenuAndClearManager = _ -> {
                currentManager = null;
                view.switchToPanel("Menu");
            };

            view.addSubmitBackListener(backToMenu);
            view.addStatusBackListener(backToMenu);
            view.addProcessBackListener(backToMenuAndClearManager);
            view.addAddEmployeeBackListener(backToMenuAndClearManager);
            view.addDeleteEmployeeBackListener(backToMenuAndClearManager); 

            view.addSubmitRequestListener(new SubmitRequestListener());
            view.addViewStatusSearchListener(new ViewStatusSearchListener());
            view.addAddEmployeeConfirmListener(new AddEmployeeConfirmListener());
            view.addDeleteEmployeeConfirmListener(new DeleteEmployeeConfirmListener()); 
        }

        class ManagerActionAuthenticator implements ActionListener {
            private final String targetPanel;

            public ManagerActionAuthenticator(String targetPanel) {
                this.targetPanel = targetPanel;
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                String managerId = JOptionPane.showInputDialog(view, "Please enter your Manager ID to proceed:", "Manager Verification", JOptionPane.PLAIN_MESSAGE);

                if (managerId == null || managerId.trim().isEmpty()) {
                    if (managerId != null) view.showError("Manager ID cannot be empty.");
                    return;
                }

                Employee emp = model.findEmployeeById(managerId.trim());
                if (emp != null && emp instanceof Manager) {
                    currentManager = (Manager) emp;
                    
                    if ("Process".equals(targetPanel)) {
                        List<LeaveRequest> pendingRequests = model.getPendingRequests();
                        view.updateProcessRequestsView(pendingRequests, new ProcessRequestListener(currentManager));
                    }
                    
                    view.switchToPanel(targetPanel);
                } else {
                    view.showError("Invalid Manager ID or you do not have manager privileges.");
                }
            }
        }
        
        class DeleteEmployeeConfirmListener implements ActionListener {
            @Override
            public void actionPerformed(ActionEvent e) {
                String empIdToRemove = view.getDeleteEmpId();
                
                if (empIdToRemove.isEmpty()) {
                    view.showError("Please enter the Employee ID to remove.");
                    return;
                }

                Employee targetEmp = model.findEmployeeById(empIdToRemove);
                if (targetEmp == null) {
                    view.showError("Employee with ID '" + empIdToRemove + "' not found.");
                    return;
                }

                if (currentManager != null && currentManager.getId().equals(empIdToRemove)) {
                    view.showError("You cannot remove your own Manager account.");
                    return;
                }

                int confirm = JOptionPane.showConfirmDialog(view,
                        "Are you sure you want to remove Employee '" + targetEmp.getName() + "' (" + empIdToRemove + ")?\nThis action cannot be undone and will delete all their leave requests.",
                        "Confirm Employee Removal",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);

                if (confirm == JOptionPane.YES_OPTION) {
                    model.removeEmployee(empIdToRemove);
                    view.showMessage("Employee '" + targetEmp.getName() + "' has been successfully removed.");
                    view.resetDeleteEmployeeForm();
                }
            }
        }


        class SubmitRequestListener implements ActionListener {
            @Override
            public void actionPerformed(ActionEvent e) {
                String empId = view.getSubmitEmpId();
                Employee employee = model.findEmployeeById(empId);
                if (employee == null) {
                    view.showError("Employee with ID '" + empId + "' not found.");
                    return;
                }
                String leaveType = view.getLeaveType();
                String startDate = view.getStartDate();
                String endDate = view.getEndDate();
                if (leaveType.isEmpty() || startDate.isEmpty() || endDate.isEmpty()) {
                    view.showError("All leave request fields are required.");
                    return;
                }
                try {
                    LeaveRequest request = new LeaveRequest(employee.getId(), leaveType, startDate, endDate);
                    employee.submitLeaveRequest(model, request);
                    view.showMessage("Leave request submitted successfully for " + employee.getName() + "!");
                    view.resetSubmitForm();
                } catch (InsufficientLeaveException ex) {
                    view.showError("Submission failed: " + ex.getMessage());
                }
            }
        }

        class ViewStatusSearchListener implements ActionListener {
            @Override
            public void actionPerformed(ActionEvent e) {
                String empId = view.getStatusEmpId();
                Employee employee = model.findEmployeeById(empId);
                if (employee == null) {
                    view.showError("Employee with ID '" + empId + "' not found.");
                    view.updateStatusResults("Enter a valid Employee ID to see status.");
                    return;
                }
                List<LeaveRequest> requests = model.getRequestsForEmployee(empId);
                view.updateStatusResults(requests);
            }
        }
        
        class AddEmployeeConfirmListener implements ActionListener {
            @Override
            public void actionPerformed(ActionEvent e) {
                String id = view.getNewEmpId();
                String name = view.getNewEmpName();
                boolean isManager = view.isNewEmpManager();
                if (id.isEmpty() || name.isEmpty()) {
                    view.showError("Employee ID and Name fields are required.");
                    return;
                }
                if (model.findEmployeeById(id) != null) {
                    view.showError("An employee with ID '" + id + "' already exists.");
                    return;
                }
                Employee newEmp = isManager ? new Manager(id, name, DEFAULT_LEAVE_BALANCE) : new Employee(id, name, DEFAULT_LEAVE_BALANCE);
                model.addEmployee(newEmp);
                view.showMessage("Employee '" + name + "' added successfully!");
                view.resetAddEmployeeForm();
            }
        }

        class ProcessRequestListener implements ActionListener {
            private Manager manager;
            public ProcessRequestListener(Manager manager) {
                this.manager = manager;
            }
            @Override
            public void actionPerformed(ActionEvent e) {
                String command = e.getActionCommand();
                String[] parts = command.split("_");
                String action = parts[0];
                int requestId = Integer.parseInt(parts[1]);
                LeaveRequest request = model.findRequestById(requestId);
                if (request != null) {
                    if ("approve".equals(action)) {
                        manager.approveLeaveRequest(model, request);
                        view.showMessage("Request approved.");
                    } else if ("reject".equals(action)) {
                        manager.rejectLeaveRequest(model, request);
                        view.showMessage("Request rejected.");
                    }
                    List<LeaveRequest> pendingRequests = model.getPendingRequests();
                    view.updateProcessRequestsView(pendingRequests, this);
                }
            }
        }
    }

    static class LeaveManagementView extends JFrame {
        private CardLayout cardLayout = new CardLayout();
        private JPanel mainPanel = new JPanel(cardLayout);

        private JButton menuSubmitBtn, menuViewStatusBtn, menuProcessBtn, menuAddEmpBtn, menuDeleteEmpBtn, menuExitBtn;

        private JTextField submitEmpId, leaveType, startDate, endDate;
        private JButton submitRequestBtn, submitBackBtn;

        private JTextField statusEmpId;
        private JButton statusSearchBtn, statusBackBtn;
        private JTextArea statusResultsArea;

        private JPanel processRequestsPanel;
        private JButton processBackBtn;

        private JTextField addEmpId, addEmpName;
        private JCheckBox addEmpIsManager;
        private JButton addEmpConfirmBtn, addEmpBackBtn;

        private JTextField deleteEmpIdField;
        private JButton deleteEmpConfirmBtn, deleteEmpBackBtn;
        
        public LeaveManagementView() {
            super("Employee Leave Management System");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(800, 600);
            setLocationRelativeTo(null);
            
            mainPanel.add(createMenuPanel(), "Menu");
            mainPanel.add(createSubmitPanel(), "Submit");
            mainPanel.add(createStatusPanel(), "Status");
            mainPanel.add(createProcessPanel(), "Process");
            mainPanel.add(createAddEmployeePanel(), "AddEmployee");
            mainPanel.add(createDeleteEmployeePanel(), "DeleteEmployee"); 
            
            add(mainPanel);
            switchToPanel("Menu");
        }
        
        private JPanel createMenuPanel() {
            JPanel panel = new JPanel(new GridLayout(6, 1, 10, 10)); 
            panel.setBorder(new EmptyBorder(50, 150, 50, 150));
            
            menuSubmitBtn = new JButton("Submit Leave Request");
            menuViewStatusBtn = new JButton("Check My Leave Status");
            menuProcessBtn = new JButton("Process Pending Requests (Manager)");
            menuAddEmpBtn = new JButton("Add New Employee (Manager)");
            menuDeleteEmpBtn = new JButton("Delete Employee by ID (Manager)"); 
            menuExitBtn = new JButton("Exit");
            
            panel.add(menuSubmitBtn);
            panel.add(menuViewStatusBtn);
            panel.add(menuProcessBtn);
            panel.add(menuAddEmpBtn);
            panel.add(menuDeleteEmpBtn); 
            panel.add(menuExitBtn);
            return panel;
        }

        private JPanel createDeleteEmployeePanel() {
            JPanel panel = new JPanel(new BorderLayout(10, 10));
            panel.setBorder(new EmptyBorder(100, 150, 100, 150));
            
            JLabel title = new JLabel("Delete Employee by ID", SwingConstants.CENTER);
            title.setFont(new Font("SansSerif", Font.BOLD, 18));
            panel.add(title, BorderLayout.NORTH);

            JPanel formPanel = new JPanel(new GridLayout(2, 1, 5, 5));
            
            deleteEmpIdField = new JTextField();
            
            JPanel inputGroup = new JPanel(new BorderLayout(5, 5));
            inputGroup.add(new JLabel("Employee ID to Remove:"), BorderLayout.NORTH);
            inputGroup.add(deleteEmpIdField, BorderLayout.CENTER);

            deleteEmpConfirmBtn = new JButton("Confirm Deletion");
            deleteEmpBackBtn = new JButton("Back to Main Menu");
            
            formPanel.add(inputGroup);
            
            JPanel buttonPanel = new JPanel();
            buttonPanel.add(deleteEmpConfirmBtn);
            buttonPanel.add(deleteEmpBackBtn);

            panel.add(formPanel, BorderLayout.CENTER);
            panel.add(buttonPanel, BorderLayout.SOUTH);
            return panel;
        }

        private JPanel createSubmitPanel() {
            JPanel panel = new JPanel(new BorderLayout(10, 10));
            panel.setBorder(new EmptyBorder(20, 20, 20, 20));
            panel.add(new JLabel("Submit Leave Request", SwingConstants.CENTER), BorderLayout.NORTH);
            JPanel formPanel = new JPanel(new GridLayout(4, 2, 5, 5));
            submitEmpId = new JTextField();
            leaveType = new JTextField();
            startDate = new JTextField();
            endDate = new JTextField();
            formPanel.add(new JLabel("Your Employee ID:"));
            formPanel.add(submitEmpId);
            formPanel.add(new JLabel("Leave Type (e.g., Vacation):"));
            formPanel.add(leaveType);
            formPanel.add(new JLabel("Start Date (YYYY-MM-DD):"));
            formPanel.add(startDate);
            formPanel.add(new JLabel("End Date (YYYY-MM-DD):"));
            formPanel.add(endDate);
            panel.add(formPanel, BorderLayout.CENTER);
            submitRequestBtn = new JButton("Submit Request");
            submitBackBtn = new JButton("Back to Main Menu");
            JPanel buttonPanel = new JPanel();
            buttonPanel.add(submitRequestBtn);
            buttonPanel.add(submitBackBtn);
            panel.add(buttonPanel, BorderLayout.SOUTH);
            return panel;
        }

        private JPanel createStatusPanel() {
            JPanel panel = new JPanel(new BorderLayout(10, 10));
            panel.setBorder(new EmptyBorder(20, 20, 20, 20));
            JPanel topPanel = new JPanel();
            statusEmpId = new JTextField(15);
            statusSearchBtn = new JButton("Show Status");
            topPanel.add(new JLabel("Enter Your Employee ID:"));
            topPanel.add(statusEmpId);
            topPanel.add(statusSearchBtn);
            panel.add(topPanel, BorderLayout.NORTH);
            statusResultsArea = new JTextArea();
            statusResultsArea.setEditable(false);
            panel.add(new JScrollPane(statusResultsArea), BorderLayout.CENTER);
            statusBackBtn = new JButton("Back to Main Menu");
            panel.add(statusBackBtn, BorderLayout.SOUTH);
            return panel;
        }

        private JPanel createProcessPanel() {
            JPanel panel = new JPanel(new BorderLayout(10, 10));
            panel.setBorder(new EmptyBorder(20, 20, 20, 20));
            panel.add(new JLabel("Process Pending Requests", SwingConstants.CENTER), BorderLayout.NORTH);
            processRequestsPanel = new JPanel();
            processRequestsPanel.setLayout(new BoxLayout(processRequestsPanel, BoxLayout.Y_AXIS));
            panel.add(new JScrollPane(processRequestsPanel), BorderLayout.CENTER);
            processBackBtn = new JButton("Back to Main Menu");
            panel.add(processBackBtn, BorderLayout.SOUTH);
            return panel;
        }

        private JPanel createAddEmployeePanel() {
            JPanel panel = new JPanel(new BorderLayout(10, 10));
            panel.setBorder(new EmptyBorder(50, 50, 50, 50));
            panel.add(new JLabel("Add New Employee", SwingConstants.CENTER), BorderLayout.NORTH);
            JPanel formPanel = new JPanel(new GridLayout(3, 2, 5, 5));
            addEmpId = new JTextField();
            addEmpName = new JTextField();
            addEmpIsManager = new JCheckBox("Assign Manager Privileges");
            formPanel.add(new JLabel("New Employee ID:"));
            formPanel.add(addEmpId);
            formPanel.add(new JLabel("New Employee Name:"));
            formPanel.add(addEmpName);
            formPanel.add(new JLabel(""));
            formPanel.add(addEmpIsManager);
            panel.add(formPanel, BorderLayout.CENTER);
            addEmpConfirmBtn = new JButton("Add Employee");
            addEmpBackBtn = new JButton("Back to Main Menu");
            JPanel buttonPanel = new JPanel();
            buttonPanel.add(addEmpConfirmBtn);
            buttonPanel.add(addEmpBackBtn);
            panel.add(buttonPanel, BorderLayout.SOUTH);
            return panel;
        }
        
        public void updateStatusResults(List<LeaveRequest> requests) {
            if (requests.isEmpty()) {
                statusResultsArea.setText("No leave requests found for this employee.");
                return;
            }
            StringBuilder sb = new StringBuilder("Leave History:\n");
            for (LeaveRequest req : requests) {
                sb.append(req.toString()).append("\n");
            }
            statusResultsArea.setText(sb.toString());
        }

        public void updateStatusResults(String message) {
            statusResultsArea.setText(message);
        }

        public void updateProcessRequestsView(List<LeaveRequest> requests, ActionListener listener) {
            processRequestsPanel.removeAll();
            if (requests.isEmpty()) {
                processRequestsPanel.add(new JLabel("No pending requests."));
            } else {
                for (LeaveRequest req : requests) {
                    JPanel entryPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
                    entryPanel.add(new JLabel(req.toString()));
                    JButton approve = new JButton("Approve");
                    approve.setActionCommand("approve_" + req.getId());
                    approve.addActionListener(listener);
                    JButton reject = new JButton("Reject");
                    reject.setActionCommand("reject_" + req.getId());
                    reject.addActionListener(listener);
                    entryPanel.add(approve);
                    entryPanel.add(reject);
                    processRequestsPanel.add(entryPanel);
                }
            }
            processRequestsPanel.revalidate();
            processRequestsPanel.repaint();
        }

        public String getSubmitEmpId() { return submitEmpId.getText().trim(); }
        public String getLeaveType() { return leaveType.getText().trim(); }
        public String getStartDate() { return startDate.getText().trim(); }
        public String getEndDate() { return endDate.getText().trim(); }
        public String getStatusEmpId() { return statusEmpId.getText().trim(); }
        public String getNewEmpId() { return addEmpId.getText().trim(); }
        public String getNewEmpName() { return addEmpName.getText().trim(); }
        public boolean isNewEmpManager() { return addEmpIsManager.isSelected(); }
        public String getDeleteEmpId() { return deleteEmpIdField.getText().trim(); } 
        
        public void resetSubmitForm() { submitEmpId.setText(""); leaveType.setText(""); startDate.setText(""); endDate.setText(""); }
        public void resetAddEmployeeForm() { addEmpId.setText(""); addEmpName.setText(""); addEmpIsManager.setSelected(false); }
        public void resetDeleteEmployeeForm() { deleteEmpIdField.setText(""); } 

        public void addMenuSubmitListener(ActionListener l) { menuSubmitBtn.addActionListener(l); }
        public void addMenuViewStatusListener(ActionListener l) { menuViewStatusBtn.addActionListener(l); }
        public void addMenuProcessListener(ActionListener l) { menuProcessBtn.addActionListener(l); }
        public void addMenuAddEmployeeListener(ActionListener l) { menuAddEmpBtn.addActionListener(l); }
        public void addMenuDeleteEmployeeListener(ActionListener l) { menuDeleteEmpBtn.addActionListener(l); } 
        public void addMenuExitListener(ActionListener l) { menuExitBtn.addActionListener(l); }
        
        public void addSubmitBackListener(ActionListener l) { submitBackBtn.addActionListener(l); }
        public void addStatusBackListener(ActionListener l) { statusBackBtn.addActionListener(l); }
        public void addProcessBackListener(ActionListener l) { processBackBtn.addActionListener(l); }
        public void addAddEmployeeBackListener(ActionListener l) { addEmpBackBtn.addActionListener(l); }
        public void addDeleteEmployeeBackListener(ActionListener l) { deleteEmpBackBtn.addActionListener(l); } 

        public void addSubmitRequestListener(ActionListener l) { submitRequestBtn.addActionListener(l); }
        public void addViewStatusSearchListener(ActionListener l) { statusSearchBtn.addActionListener(l); }
        public void addAddEmployeeConfirmListener(ActionListener l) { addEmpConfirmBtn.addActionListener(l); }
        public void addDeleteEmployeeConfirmListener(ActionListener l) { deleteEmpConfirmBtn.addActionListener(l); } 
        
        public void switchToPanel(String name) { cardLayout.show(mainPanel, name); }
        public void showMessage(String msg) { JOptionPane.showMessageDialog(this, msg); }
        public void showError(String msg) { JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE); }
    }

    static class DatabaseManager {
        private static final String DB_URL = "jdbc:mysql://localhost:3306/leave_system_db";
        private static final String USER = "YOUR DB USERNAME";
        private static final String PASS = "YOUR DB PASSWORD";

        public DatabaseManager() throws SQLException {
            getConnection().close(); 
        }

        private Connection getConnection() throws SQLException {
            return DriverManager.getConnection(DB_URL, USER, PASS);
        }
        
        public List<Employee> getAllEmployees() {
            List<Employee> employees = new ArrayList<>();
            String sql = "SELECT * FROM employees ORDER BY name";
            try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    String id = rs.getString("id");
                    String name = rs.getString("name");
                    int balance = rs.getInt("leave_balance");
                    boolean isManager = rs.getBoolean("is_manager");
                    if (isManager) {
                        employees.add(new Manager(id, name, balance));
                    } else {
                        employees.add(new Employee(id, name, balance));
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return employees;
        }

        public Employee findEmployeeById(String employeeId) {
            String sql = "SELECT * FROM employees WHERE id = ?";
            try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, employeeId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        String name = rs.getString("name");
                        int balance = rs.getInt("leave_balance");
                        boolean isManager = rs.getBoolean("is_manager");
                        if (isManager) {
                            return new Manager(employeeId, name, balance);
                        } else {
                            return new Employee(employeeId, name, balance);
                        }
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }

        public void addEmployee(Employee emp) {
            String sql = "INSERT INTO employees(id, name, leave_balance, is_manager) VALUES(?, ?, ?, ?)";
            try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, emp.getId());
                pstmt.setString(2, emp.getName());
                pstmt.setInt(3, emp.getLeaveBalance());
                pstmt.setBoolean(4, emp instanceof Manager);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        public void removeEmployee(String employeeId) {
            String deleteRequestsSql = "DELETE FROM leave_requests WHERE employee_id = ?";
            String deleteEmployeeSql = "DELETE FROM employees WHERE id = ?";
            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement pstmtRequests = conn.prepareStatement(deleteRequestsSql)) {
                    pstmtRequests.setString(1, employeeId);
                    pstmtRequests.executeUpdate();
                }
                try (PreparedStatement pstmtEmployee = conn.prepareStatement(deleteEmployeeSql)) {
                    pstmtEmployee.setString(1, employeeId);
                    pstmtEmployee.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        public void addLeaveRequest(LeaveRequest req) {
            String sql = "INSERT INTO leave_requests(employee_id, leave_type, start_date, end_date, status) VALUES(?, ?, ?, ?, ?)";
            try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, req.getEmployeeId());
                pstmt.setString(2, req.getLeaveType());
                pstmt.setString(3, req.getStartDate());
                pstmt.setString(4, req.getEndDate());
                pstmt.setString(5, req.getStatus());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        public List<LeaveRequest> getRequestsForEmployee(String employeeId) {
            List<LeaveRequest> requests = new ArrayList<>();
            String sql = "SELECT * FROM leave_requests WHERE employee_id = ?";
            try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, employeeId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        requests.add(mapRowToLeaveRequest(rs));
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return requests;
        }

        public List<LeaveRequest> getPendingRequests() {
            List<LeaveRequest> requests = new ArrayList<>();
            String sql = "SELECT * FROM leave_requests WHERE status = 'Pending'";
            try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    requests.add(mapRowToLeaveRequest(rs));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return requests;
        }
        
        public LeaveRequest findRequestById(int requestId) {
            String sql = "SELECT * FROM leave_requests WHERE request_id = ?";
            try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, requestId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return mapRowToLeaveRequest(rs);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }

        public void updateLeaveRequestStatus(int requestId, String status) {
            String sql = "UPDATE leave_requests SET status = ? WHERE request_id = ?";
            try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, status);
                pstmt.setInt(2, requestId);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        
        public void updateEmployeeBalance(String employeeId, int newBalance) {
            String sql = "UPDATE employees SET leave_balance = ? WHERE id = ?";
            try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, newBalance);
                pstmt.setString(2, employeeId);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        private LeaveRequest mapRowToLeaveRequest(ResultSet rs) throws SQLException {
            int id = rs.getInt("request_id");
            String empId = rs.getString("employee_id");
            String type = rs.getString("leave_type");
            String start = rs.getString("start_date");
            String end = rs.getString("end_date");
            String status = rs.getString("status");
            return new LeaveRequest(id, empId, type, start, end, status);
        }
    }

    static class InsufficientLeaveException extends Exception {
        public InsufficientLeaveException(String message) { super(message); }
    }

    static class Employee {
        private String id;
        private String name;
        private int leaveBalance;
        public Employee(String id, String name, int leaveBalance) {
            this.id = id;
            this.name = name;
            this.leaveBalance = leaveBalance;
        }
        public String getId() { return id; }
        public String getName() { return name; }
        public int getLeaveBalance() { return leaveBalance; }
        public void submitLeaveRequest(LeaveSystem system, LeaveRequest request) throws InsufficientLeaveException {
            if (this.leaveBalance < 1) {
                throw new InsufficientLeaveException("Insufficient leave balance. You have " + this.leaveBalance + " days left.");
            }
            system.addLeaveRequest(request);
        }
    }

    static class Manager extends Employee {
        public Manager(String id, String name, int leaveBalance) { super(id, name, leaveBalance); }
        public void approveLeaveRequest(LeaveSystem system, LeaveRequest request) {
            system.updateLeaveRequestStatus(request.getId(), "Approved");
            system.updateLeaveBalance(request.getEmployeeId(), -1); 
        }
        public void rejectLeaveRequest(LeaveSystem system, LeaveRequest request) { 
            system.updateLeaveRequestStatus(request.getId(), "Rejected");
        }
    }

    static class LeaveRequest {
        private String employeeId;
        private String leaveType;
        private String startDate;
        private String endDate;
        private String status;
        private int id;

        public LeaveRequest(String employeeId, String leaveType, String startDate, String endDate) {
            this.employeeId = employeeId;
            this.leaveType = leaveType;
            this.startDate = startDate;
            this.endDate = endDate;
            this.status = "Pending";
            this.id = -1;
        }

        public LeaveRequest(int id, String employeeId, String leaveType, String startDate, String endDate, String status) {
            this.id = id;
            this.employeeId = employeeId;
            this.leaveType = leaveType;
            this.startDate = startDate;
            this.endDate = endDate;
            this.status = status;
        }
        public int getId() { return id; }
        public String getEmployeeId() { return employeeId; }
        public String getStatus() { return status; }
        public String getLeaveType() { return leaveType; }
        public String getStartDate() { return startDate; }
        public String getEndDate() { return endDate; }

        @Override
        public String toString() {
            return String.format("[Request #%d] Emp ID: %s, Type: %s, From: %s To: %s, Status: %s", 
                        id, employeeId, leaveType, startDate, endDate, status);
        }
    }

    static class LeaveSystem {
        private DatabaseManager dbManager;

        public LeaveSystem() throws SQLException {
            dbManager = new DatabaseManager();
        }

        public void addEmployee(Employee emp) { dbManager.addEmployee(emp); }
        public void removeEmployee(String employeeId) { dbManager.removeEmployee(employeeId); }
        public void addLeaveRequest(LeaveRequest request) { dbManager.addLeaveRequest(request); }
        public List<LeaveRequest> getRequestsForEmployee(String employeeId) { return dbManager.getRequestsForEmployee(employeeId); }
        public List<LeaveRequest> getPendingRequests() { return dbManager.getPendingRequests(); }
        public Employee findEmployeeById(String employeeId) { return dbManager.findEmployeeById(employeeId); }
        public LeaveRequest findRequestById(int requestId) { return dbManager.findRequestById(requestId); }
        
        public void updateLeaveBalance(String employeeId, int days) {
            Employee emp = findEmployeeById(employeeId);
            if (emp != null) {
                int newBalance = emp.getLeaveBalance() + days;
                dbManager.updateEmployeeBalance(employeeId, newBalance);
            }
        }
        public void updateLeaveRequestStatus(int requestId, String status) {
            dbManager.updateLeaveRequestStatus(requestId, status);
        }
    }
}

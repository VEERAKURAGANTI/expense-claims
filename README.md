Expense Claims Management System

A full-stack expense claim management application that allows employees to submit expenses, managers to review and approve claims, and Finance to handle final approval and payment.

The application implements JWT authentication, role-based access control, approval routing, claim status management, receipt attachments, and a Finance payment workflow.

Live Application

Deployed Application:
https://expense-claims-i42r.onrender.com

Source Code:
https://github.com/VEERAKURAGANTI/expense-claims

Note: The application is hosted on Render's free tier, so the backend may take up to a minute to start after a period of inactivity.

1. Project Overview

The goal of this project is to provide a simple but realistic workflow for managing employee expense claims.

The system supports three main roles:

Staff – create, edit, submit, and track their own expense claims

Manager – review and approve/reject claims submitted by assigned team members

Finance – handle escalated manager claims, review approved claims, and mark claims as paid

A key design principle is that important business rules are enforced by the backend rather than relying only on frontend restrictions.

2. Technology Stack

Frontend

* React

* Vite

* JavaScript

* Axios

* React Router

* CSS

Backend

* Java 17

* Spring Boot 3.3.4

* Spring Security

* JWT Authentication

* Spring Data JPA

* Hibernate

* Maven

* REST APIs

Database

* PostgreSQL

Testing

* JUnit

* Mockito

* H2 in-memory database

Deployment

* GitHub

* Render

3. High-Level Architecture

                    React Frontend
                         |
                         | REST API
                         v
                Spring Boot Backend
                         |
              -----------------------
              |          |          |
             Auth      Claims     Finance
              |          |          |
              -----------------------
                         |
                         v
                    PostgreSQL

Authentication

The application uses JWT (JSON Web Token) authentication with Spring Security.

The authentication flow is:

User Login
    |
    v
Spring Boot Authentication
    |
    v
Credentials Validated
    |
    v
JWT Token Generated
    |
    v
Frontend Stores Token
    |
    v
Token Sent With Protected API Requests
Authorization: Bearer <token>
    |
    v
Spring Security Validates JWT
    |
    v
Role-Based Authorization

JWT provides stateless authentication between the React frontend and Spring Boot backend.

JWT configuration is controlled using environment variables such as:

JWT_SECRET

JWT_EXPIRY_MINUTES

Claims

The Claims module handles:

Claim creation

Claim editing

Claim submission

Claim status tracking

Receipt attachments

Manager approval/rejection

Finance

The Finance module handles:

Escalated manager claims

Review of approved claims

Final approval

Marking claims as paid

All modules use the same PostgreSQL database.

4. Roles & Workflow

Role

Responsibilities

Staff

Create, edit, submit, and track their own claims

Manager

Review and approve/reject claims submitted by assigned team members

Finance

Handle manager claims, review approved claims, and mark claims as paid

Claim Lifecycle

Draft
  |
  v
Submitted
  |
  +------------------+
  |                  |
  v                  v
Approved           Rejected
  |
  v
Paid

A paid claim cannot move backwards to an earlier status.

5. Approval Routing

The system uses an approver relationship between users.

The current demonstration hierarchy is:

                Ramesh
                Finance
               /      \
              /        \
          Deepak      Ananya
          Manager     Manager
            |        /  |  \
          Rahul    Kavya Farhan Meera

The important business rule is:

A user cannot approve their own claim.

Examples:

Rahul's claim → Deepak

Kavya's claim → Ananya

Farhan's claim → Ananya

Meera's claim → Ananya

Deepak's own claim → Ramesh / Finance

Ananya's own claim → Ramesh / Finance

This prevents managers from approving their own expenses.

The rule is enforced by the backend approval service.

6. Database Design

The application uses PostgreSQL as its production relational database.

Users

Users contain information required for authentication and authorization, including:

User ID

Name

Email

Password

Role

Approver relationship

Roles include:

STAFF
MANAGER
FINANCE

The approver relationship allows the backend to determine who should review a user's claim.

Claims

Claims store the information required to process an expense, including:

Claim ID

Claim owner

Amount

Category

Description

Claim status

Receipt information

Approval information

Payment information

Claims are associated with the user who created them.

7. Security & Authorization

Spring Security protects the backend APIs.

JWT authentication identifies the logged-in user, while role-based authorization determines which APIs the user can access.

The application uses the following roles:

STAFF
MANAGER
FINANCE

The backend applies authorization rules to protected API endpoints.

For example:

/api/auth/**

is available for authentication.

Manager APIs require:

MANAGER
FINANCE

Finance APIs require:

FINANCE

Other protected APIs require an authenticated user.

Authorization is enforced server-side, so changing or bypassing frontend controls does not grant additional permissions.

8. Business Rules

Self-approval prevention

A user cannot approve their own claim.

Manager claim routing

When a manager submits their own claim, the claim is routed to their approver rather than back to the same manager.

Finance approval

Finance handles claims escalated from managers and performs the final approval/payment workflow.

Paid claims

Once a claim is marked as PAID, it cannot return to an earlier approval state.

Claim ownership

Staff users can access their own claims. Managers can access claims assigned to them for approval, and Finance can access claims required for the Finance workflow.

9. Claim Statuses

The application uses the following claim statuses:

PENDING_REVIEW
SUBMITTED
APPROVED
REJECTED
PAID

The normal processing flow is:

PENDING_REVIEW
      |
      v
  SUBMITTED
      |
      +------------+
      |            |
      v            v
 APPROVED       REJECTED
      |
      v
    PAID

10. Receipt Attachments

Claims can include receipt attachments.

The backend provides an attachment endpoint:

POST /api/claims/{claimId}/attachment

The application also supports claims where a physical receipt may not be available, depending on the claim scenario.

11. Getting Started

Prerequisites

Java 17

Node.js

npm

PostgreSQL

Git

1. Clone the Repository

git clone https://github.com/VEERAKURAGANTI/expense-claims.git
cd expense-claims

2. Create the Database

Create a PostgreSQL database:

CREATE DATABASE expense_claims;

3. Start the Backend

cd backend
mvn spring-boot:run

The backend runs on:

http://localhost:8080

Backend configuration is available in:

backend/src/main/resources/application.properties

Production/deployment configuration can be supplied through environment variables such as:

DB_URL
DB_USER
DB_PASSWORD
JWT_SECRET
JWT_EXPIRY_MINUTES
CORS_ORIGINS
UPLOADS_DIR
SEED_ENABLED
PORT

4. Start the Frontend

Open another terminal:

cd frontend
npm install

Create:

frontend/.env

with:

VITE_API_URL=http://localhost:8080

Then run:

npm run dev

The frontend runs on:

http://localhost:5173

12. Demo Users

The application includes seeded demonstration users for testing the workflow.

The seeded demo password is:

password123

The demo data includes Staff, Manager, and Finance users and demonstrates the approval hierarchy.

13. Testing

The backend contains unit and integration tests using JUnit, Mockito, and H2.

Test coverage includes areas such as:

Claim processing

Approval routing

Self-approval prevention

Finance workflow

Duplicate-claim protection

Receipt parsing

HTTP workflow testing

Run the tests with:

cd backend
mvn test

The tests use an in-memory H2 database so test execution does not modify the local PostgreSQL database.

Some integration tests are still being refined. The production application builds and runs successfully.

14. Deployment

The application is deployed using Render.

Backend

The Spring Boot backend is deployed as a Docker service.

GitHub
   |
   v
Render
   |
   v
Docker
   |
   v
Spring Boot

The backend connects to the PostgreSQL production database.

Frontend

The React application is deployed as a Render Static Site.

The frontend uses:

VITE_API_URL

to determine the backend API URL.

Production configuration:

VITE_API_URL=https://expense-claims-backend.onrender.com

15. Key Design Decisions & Assumptions

Backend-first authorization

Important business rules are enforced by Spring Security and backend services instead of relying only on frontend UI restrictions.

Approver relationship

Each user can have an assigned approver. This allows the approval workflow to be determined from user relationships rather than hard-coding every claim.

Manager self-approval

Managers cannot approve their own claims. Their claims are routed to their own approver/Finance.

Finance as final payment authority

Finance handles the final payment stage and can mark approved claims as paid.

PostgreSQL

PostgreSQL was selected as the production relational database because the application contains relationships between users, claims, approval workflows, and payment status.

JWT authentication

JWT was selected to provide stateless authentication between the React frontend and Spring Boot backend.

Environment configuration

Database credentials, JWT secrets, CORS configuration, and other deployment settings are provided through environment variables instead of hard-coding production credentials.

16. AI Tools Used

### ChatGPT

ChatGPT was used for:

- Understanding Spring Boot and Spring Security concepts
- Designing and reviewing the expense claim workflow
- JWT authentication and role-based authorization guidance
- Debugging backend and frontend issues
- Troubleshooting PostgreSQL and Render deployment configuration
- Reviewing approval-routing business rules
- Improving project documentation and README content
- Discussing test cases and edge cases

### Claude Code

Claude Code was used for:

- Assisting with implementation and modification of source code
- Reviewing existing backend and frontend code
- Debugging and fixing implementation issues
- Refactoring code where required
- Helping implement business logic and workflow changes
- Assisting with project structure and development tasks

AI tools were used as development assistants, while the final implementation, testing, configuration, and deployment decisions were reviewed and validated during development.

17. Challenges Solved

Approval routing

Ensuring that manager claims do not get assigned back to the same manager required explicit backend routing logic.

Role-based access

The application needed to distinguish between Staff, Manager, and Finance permissions.

JWT authentication

The frontend and backend needed to correctly exchange and validate JWT bearer tokens for protected APIs.

Deployment configuration

The application required separate production configuration for:

Database connection

JWT secret

CORS

Frontend API URL

Render deployment

Database workflow

The database relationships needed to support both normal employee approval and manager-to-Finance escalation.

18. What I Would Improve With One More Week

1. Finance Reporting

Add a dedicated Finance dashboard showing:

Monthly total spending

Spending by category

Spending by employee

Claims over configured limits

Pending approvals

Paid claims

2. Better Receipt Processing

Improve receipt parsing to support more real-world receipt formats and validation errors.

3. Detailed Audit Trail

Add a detailed claim history showing:

Created
Submitted
Reviewed
Approved/Rejected
Paid

with timestamps and the user responsible for each action.

4. Better Validation

Improve:

Amount validation

Receipt validation

Duplicate detection

Claim category validation

Date validation

5. Improved UI/UX

Add:

Better dashboards

Loading states

Error messages

Confirmation dialogs

Mobile responsiveness

Better filtering and searching

6. Production File Storage

Move receipt attachments from local filesystem storage to persistent object storage so uploaded files are not lost when the application container restarts.

7. Automated Deployment

Add stronger CI/CD checks so builds and tests are automatically validated before deployment.

19. Project Structure

expense-claims/
│
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   └── resources/
│   │   └── test/
│   │
│   ├── pom.xml
│   ├── Dockerfile
│   └── mvnw
│
├── frontend/
│   ├── src/
│   ├── public/
│   ├── package.json
│   └── vite.config.js
│
└── README.md

20. Submission Links

Live Application

https://expense-claims-i42r.onrender.com/login

Backend

https://expense-claims-backend.onrender.com

GitHub Repository

https://github.com/VEERAKURAGANTI/expense-claims

About

Built by Veera Kuraganti as a full-stack demonstration of expense management, JWT authentication, role-based authorization, approval workflows, and Finance processing using React, Spring Boot, and PostgreSQL.

# Expense Claims (Java / React edition)

Staff file claims (paste a receipt, or attach a photo of one), a manager
signs them off, finance pays them out and watches monthly spend. Same
brief, same scenarios as the Node prototype in this repo's sibling
`expense-claims/` folder - this is a from-scratch rebuild on Spring Boot +
React, for a stack that's a closer match to how this would actually get
built at a company.

**Spring Boot 3.3 / Java 21** REST API + **PostgreSQL**, JWT auth
**React (Vite)** single-page app, talks to the API over `fetch`/axios with a bearer token

## Before you run it - one important thing

I built and tested the React frontend for real in this environment (it
compiles clean, lints clean, and I drove it against a mock server matching
the real API's shape to check the login/routing logic). **The Spring Boot
backend I could not compile or run here** - this sandbox can reach npm but
its network blocks Maven Central outright, so `mvn` has nothing to
download from. I wrote and carefully re-read every file (checked brace
balance, checked every DTO/entity a class references actually exists,
checked every repository method a service calls is actually declared,
traced the JWT claims through the auth filter into the controllers), but
none of that is a substitute for an actual compile. **Please run
`mvn spring-boot:run` yourself first and send me anything that breaks -
I'd expect it to be a handful of small things, not a redesign.**

## How to run it

### 1. Database
```bash
createdb expense_claims   # needs a local Postgres running
```

### 2. Backend
```bash
cd backend
mvn spring-boot:run
```
Runs on `http://localhost:8080`. On first run it seeds demo data
automatically (same cast as the Node version - see below). Config is in
`src/main/resources/application.properties`, all overridable by env var
(`DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `CORS_ORIGINS`, `PORT`,
...) - defaults assume Postgres on localhost with user/password `postgres`.

### 3. Frontend
```bash
cd frontend
npm install
cp .env.example .env   # VITE_API_URL, defaults to localhost:8080
npm run dev
```
Runs on `http://localhost:5173`. Log in as anyone from the table the login
page shows you - every demo account uses the password `password123`.

| Person | Role | Why |
|---|---|---|
| Kavya Sundaram | staff | an already-paid claim, a pending one, and a near-duplicate meal receipt filed twice, worded differently |
| Ananya Krishnan | manager | approves her team, but her *own* claim escalates to Deepak - she can't sign herself off |
| Deepak Verma | manager, nobody above him | his own claims escalate straight to finance |
| Meera Joshi | staff | close to her monthly limit |
| Ramesh Iyer | finance | pays out approved claims, runs the monthly report |

Re-run the backend against an empty database to reset the demo state.

## The decisions and assumptions I made

**Who approves a manager's own claim.** The brief says a manager can't
sign off their own claim but doesn't say who does instead. Every user has
an `approver` (normally their manager); a manager with nobody above them
escalates to finance. That's a column on the `User` entity, not a
special-cased role check, so "a manager can never end up as their own
approver" is true by construction. `ApprovalService.decide()` also checks
`claim.owner.id != approver.id` explicitly as a second line of defence,
and the manager/finance approval endpoints share the same
authorization path, so the rule holds no matter who's signing off.

**Duplicate detection is a flag, not a block.** A likely repeat (same
person, amount within ~2%, expense date within 30 days, fuzzy match on the
merchant name - tuned to catch "Swiggy" vs "Swiggy Meghana Foods" as the
same place) surfaces as a warning at submission; the submitter has to
explicitly confirm it's separate to send it on. The flag travels with the
claim, so the manager and finance see it too. Finance gets a sharper
second check: if a claim's flagged duplicate has *already been paid*,
paying this one requires a typed reason - that's the stricter one,
because "flag and let a human decide" is fine at submission time, but
paying the same receipt twice is the actual thing finance said they want
stopped.

**What counts as "monthly spend" for the limit.** Submitted, approved and
paid claims count; rejected claims and un-submitted drafts don't - a
draft sitting in someone's review queue isn't committed spend yet.

**A paid claim is frozen.** Every endpoint that could mutate a claim
checks its current status server-side before doing anything, so there's
no route - not even a hand-crafted request - that can push a paid claim
backwards.

**Receipt parsing is heuristic, not ML** (`ReceiptParserService`) -
pattern-matched against realistic Indian receipt/SMS text (Ola/Uber
fares, Swiggy/Zomato orders, IRCTC tickets, OYO bookings, etc). It's
always shown back to the person as an editable form before anything is
sent on, per the brief - it doesn't need to be perfect, it needs to beat
retyping six fields from scratch.

**Photo uploads are stored but not OCR'd on this stack.** The Node
prototype ran OCR through `tesseract.js`. Porting that to the Java side
needed a dependency I couldn't fetch in this Maven-less sandbox, and I'd
rather ship an honest gap than a Tesseract integration I never got to
run once. The photo is kept as evidence attached to the claim; if
someone doesn't also paste the receipt text, they fill the fields in by
hand on the review screen instead. This is the first thing I'd wire up
with real internet access - see "what's next" below.

**Auth is a JWT bearer token**, issued on login and sent as
`Authorization: Bearer <token>` on every request, stored in
`localStorage` on the frontend. No self-serve signup - accounts are
provisioned by seeding, closer to how a company would actually roll this
out (finance/IT creates accounts) than letting anyone register as
"finance."

**Categories are a fixed enum** (Travel, Meals, Taxi, Accommodation,
Supplies, Other) rather than free text, so "spend by category" in the
report actually groups sensibly instead of fragmenting into near-duplicate
strings.

**No real payment integration** - "paying" a claim is a status change
plus an audit-log entry, as the brief says to emulate.

## AI tools used

I used Claude (Anthropic) throughout - architecture, the bulk of the
Spring Boot services/controllers and the React pages, and porting the
demo data across from the Node version so both builds tell the same
story. I made the call on every design decision above, wrote the demo
data by hand for realism, and did everything I actually *could* verify
in this environment: built and lint-checked the React app for real,
manually cross-checked every backend file against the ones it calls
into (DTOs, repository methods, JWT claim names) rather than just
trusting it compiles. The one thing I couldn't do here - compile the
Java - is called out above rather than glossed over.

## What I'd do next with another week

- **Actually compile the backend and fix whatever a real `mvn` run
  turns up** - the honest first item, see the callout above.
- **OCR for photo uploads** on the Java side (Tess4J, or call out to a
  hosted OCR API) so this stack matches the Node prototype's "attach a
  photo and skip typing" experience.
- **Bulk actions** for managers/finance - approving or paying claims one
  at a time is fine for a demo, painful for a real month-end inbox.
- **Notifications** - email/Slack for "you have claims waiting," "your
  claim was rejected," "N claims ready to pay."
- **Exportable monthly report** (CSV/PDF) - finance will want to hand
  this to someone else, not just look at a screen.
- **Multi-currency** - everything currently assumes INR.
- **Cross-team duplicate detection** - currently per-user only; a
  manager expensing a team dinner that someone on the team also claims
  individually wouldn't be caught.
- **Broader test coverage** - see Testing below for what's there now;
  I'd add controller-layer validation tests (malformed JSON, missing
  fields) and a few more duplicate-detection edge cases (same amount,
  same day, three-way ties) next.

## Testing

`pom.xml` already has `spring-boot-starter-test`, `spring-security-test`,
and an `h2` test-scope dependency. Tests run against an in-memory H2
database (`src/test/resources/application-test.properties`) - they never
touch your local Postgres or its demo data.

```bash
cd backend
mvn test
```

What's covered:

- **`ReceiptParserServiceTest`** - pure unit tests on the parsing
  heuristics. Writing these caught a real bug: on an itemised receipt
  (subtotal listed above a grand total, like the Swiggy example in the
  seed data), the amount regex was matching the word "total" inside
  "Item total" before it ever reached "Grand Total" - so it would have
  silently claimed the subtotal instead of what was actually paid. Fixed
  in `ReceiptParserService` by splitting the match into a strong tier
  ("grand total", "amount paid", "net payable" - never ambiguous) and a
  weak tier (bare "total" - takes the *last* match, since the real total
  line sits below the itemised ones on a real receipt).
- **`DuplicateDetectionServiceTest`** - the exact scenario from the
  brief (same order, reworded, filed days later) gets flagged; a
  different merchant at the same price, the same merchant at a very
  different price, and the same merchant/price far enough apart in time
  all correctly don't.
- **`ApprovalServiceTest`** - the self-approval rule, rejecting a
  decision from someone who isn't the assigned approver, and refusing
  to re-decide an already-decided claim.
- **`FinanceServiceTest`** - a normal payout, the paid-duplicate block
  and its override, and that an unpaid flagged duplicate does *not*
  block payment (the block is specifically about paying the same
  receipt twice, not about the flag existing at all). Also a monthly
  report over/under-limit check using Meera's seeded numbers.
- **`ClaimServiceSubmitTest`** - zero-amount claims can't submit, an
  already-submitted claim can't resubmit, and the duplicate-acknowledgement
  gate (blocks silently, then goes through and keeps the flag once
  confirmed).
- **`ExpenseClaimsWorkflowIntegrationTest`** - the one that matters most:
  drives the real HTTP stack (controllers + Spring Security's JWT filter
  + JPA) end to end with `MockMvc` against the seeded demo data. Covers
  wrong password, anonymous access, staff blocked from finance's queue,
  Ananya blocked from approving her own seeded claim over real HTTP (not
  just at the service layer), and the full draft → submit → approve →
  pay → "can't pay it twice" happy path.

I wrote all of this without being able to run `mvn test` myself (see the
callout at the top) - I traced every assertion by hand against the actual
service code, and in the parser's case, verified the specific regex
behaviour with a throwaway Node script before deciding it needed fixing
rather than the test needing to match a bug. Since you can compile
locally, `mvn test` is the thing to run first - if anything fails, it's
either a typo I made writing test code blind, or one more real bug like
the parser one.

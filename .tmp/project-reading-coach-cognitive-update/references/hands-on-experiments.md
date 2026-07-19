# Hands-On Experiments

Use one observable experiment for the current concept. Do not bundle the happy path, failure path, edge cases, and implementation rewrite into one exercise.

## HTTP API

When reaching an endpoint, let the learner personally send the request with Postman or Swagger and inspect:

```text
request method
URL
one relevant header
body or query fields
HTTP status
response body
```

If Swagger is not configured, say so. If Postman is unavailable, use a browser for GET or provide one minimal `curl`/PowerShell request.

Choose one request first:

- For a new Controller method, send the normal request.
- For authentication, first send the request without a Token or with the exact required header, depending on the selected concept.
- For `@RequestBody`, change one body field and inspect the received result.
- For `@PathVariable`, change the path value.
- For `@RequestAttribute`, prove where the server placed the value before the Controller read it.

## Database

For an insert or generated key:

```text
call the method
  -> inspect the returned object ID
  -> query the inserted row
```

For update or delete, inspect the affected-row count and the resulting row. Explain transactions only when the selected operation changes multiple pieces of data or rollback is being observed.

## Runtime and Framework Behavior

For callbacks, interceptors, proxies, or asynchronous methods, use one breakpoint or one temporary log at the boundary. Ask the learner to predict which method runs first, then compare with the observed order.

For a removable annotation or registration line:

```text
predict
  -> remove or comment one line
  -> compile/run
  -> read the first actionable error
  -> restore the line
```

Do not use the final Maven or Gradle wrapper error when an earlier error states the actual cause.

## External Boundaries

For file upload, model calls, external APIs, queues, or caches, choose the smallest input that reaches the boundary and inspect one output or state change. Avoid introducing production deployment, resilience patterns, or security hardening unless the current observation requires them.

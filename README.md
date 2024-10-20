# Unexpected invocation of error handler in Vert.x application

This is a small Vert.x application that demonstrates the unexpected (duplicate) invocation of error handlers for 400's
when a HTTP 1.1 request is sent without the host header.
The same behavior can be observed for requests without a parth and for requests whose path does not start with a slash in combination with error handlers for 404's.

This application starts two HTTP servers.
One of those servers listens to requests on port 8080 and has an error handler for requests that lead to 400s.
The other server listens to requests on port 8081 and has both an error handler for requests that lead to 400s and
a failure handler.

## Expected behavior

Start the application and perform a request to http://localhost:8080/failWithStatusCode and
http://localhost:8081/failWithStatusCode to see the expected behavior in case of a bad request (400).

```shell
curl http://localhost:8080/failWithStatusCode -v
```

In this case, the logs will show that the error handler is invoked once.

```shell
curl http://localhost:8081/failWithStatusCode -v
```

In this case, the logs wil show that the failure handler is invoked once.

## Behavior in case of missing host header

While the app is still running, use telnet to connect to the first server and create a request without the host header:

```shell
telnet localhost 8080
Trying ::1...
Connected to localhost.
Escape character is '^]'.
GET / HTTP/1.1

HTTP/1.1 400 Bad Request
content-type: application/json
content-length: 47

{"error":"Bad request","source":"errorHandler"}
```

You'll see that in the application's logs that the error handler is invoked twice.
The second invocation will lead to an exception because the response header is already sent during the first invocation.

Similar behavior is observed for the second HTTP server:

```shell
telnet localhost 8081
Trying ::1...
Connected to localhost.
Escape character is '^]'.
GET / HTTP/1.1

HTTP/1.1 400 Bad Request
content-type: application/json
content-length: 39

{"error":400,"source":"failureHandler"}
```

In this case, the failure handler is invoked first.
Afterwards, the error handler is invoked, which leads to the same exception.

See [ApplicationTest.java](src/test/java/nl/cofx/errors/ApplicationTest.java) for tests that demonstrate these scenarios.

## Root cause

The first invocation of the failure or error handler is due to the invocation of `fail` in the constructor of `RoutingContextImpl`:

```java
  public RoutingContextImpl(String mountPoint, RouterImpl router, HttpServerRequest request, Set<RouteImpl> routes) {
    super(mountPoint, routes, router);
    this.router = router;
    this.request = new HttpServerRequestWrapper(request, router.getAllowForward());
    this.body = new RequestBodyImpl(this);

    String path = request.path();
    HostAndPort authority = request.authority();

    if ((authority == null && request.version() != HttpVersion.HTTP_1_0) || path == null || path.isEmpty()) {
        // Authority must be present (HTTP/1.x host header // HTTP/2 :authority pseudo header)
        // HTTP paths must start with a '/'
        fail(400);
    } else if (path.charAt(0) != '/') {
        // For compatiblity we return `Not Found` when a path does not start with `/`
        fail(404);
    }
}
```

The invocation of the error handler that follows is due to the invocation if `next` on the resulting `RoutingContext`:

```java

@Override
public void handle(HttpServerRequest request) {
    if (LOG.isTraceEnabled()) {
        LOG.trace("Router: " + System.identityHashCode(this) + " accepting request " + request.method() + " " + request.absoluteURI());
    }

    new RoutingContextImpl(null, this, request, state.getRoutes()).next();
}
```

The fix could be as simple as this, but I don't know which other consequences this change may have:

```java

@Override
public void handle(HttpServerRequest request) {
    if (LOG.isTraceEnabled()) {
        LOG.trace("Router: " + System.identityHashCode(this) + " accepting request " + request.method() + " " + request.absoluteURI());
    }

    var routingContext = new RoutingContextImpl(null, this, request, state.getRoutes());
    if (!routingContext.failed()) routingContext.next();
}
```

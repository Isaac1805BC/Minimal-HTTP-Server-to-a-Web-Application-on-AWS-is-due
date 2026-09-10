# Reflection — the eight discussion questions

Answers to section 8.2 of the laboratory, written against what this project actually does.

---

## 1. Why does a single HTML page cause several HTTP requests?

Because HTML is a document that *references* other resources instead of containing them. The
browser asks for the document first; while parsing it, it finds a `<link>` to a style sheet, a
`<script>` and two `<img>` elements, and each of those is a separate URL that has to be fetched with
its own request. Nothing in HTTP bundles them: one request carries one resource.

For this application, opening the home page produces five requests before a single button is
pressed — `/`, `/css/styles.css`, `/js/app.js`, `/images/logo.png`, `/images/cloud.jpg` — plus the
`/app/health` call that `app.js` starts on load. That is six requests for what a user calls "opening
the page", and it is why "100 users" never means "100 requests".

## 2. Why must image responses be treated as bytes rather than text?

Because a PNG or a JPEG is not text in any encoding. Reading it through a `Reader` decodes bytes
into characters, and any byte sequence that is not valid UTF-8 is replaced by `U+FFFD` — silently,
irreversibly. Writing those characters back out produces a different, corrupt file: the browser
reports a broken image, and the size does not even match.

`Content-Length` fails for the same reason. It counts **bytes on the wire**, not characters. For an
image the two are unrelated, and even for a UTF-8 page they differ as soon as one accented letter
appears: `"á"` is one character and two bytes. Announcing the character count would truncate the
body or leave the client waiting for bytes that never arrive.

That is why `HttpResponse` only knows `byte[]`, and `Content-Length` always comes from
`body.length`. Text and binary follow one identical path; only the declared type differs.

## 3. What is the role of the response content type?

It tells the browser how to interpret the bytes it just received. The same sequence of bytes is a
page to render, a script to execute, an image to decode or a document to download depending only on
`Content-Type` — not on the file name, not on the extension in the URL.

It is also a safety boundary. Serving an unknown file as `application/octet-stream` (the default in
`MimeTypes`) means the browser will not execute it. Declaring `application/json` on the services
tells `app.js` the body can be parsed; the client checks the header before trusting the payload.
And `charset=utf-8` on the textual types is what makes `María` arrive as `María`.

## 4. What is hardcoded in this design, and what would a routing framework eventually generalise?

Hardcoded: the mapping from URL to behaviour. `HttpServer.route()` is a chain of
`if (path.equals("/app/hello")) return Services.hello(request);`. Also hardcoded: the set of
supported methods (`GET`), the extension → content type table, and the name of the public resources
directory.

A framework would generalise exactly that chain into a **route table**: a declarative association
between a method, a path pattern and a handler, plus everything that follows from it — path
parameters (`/app/square/{value}`), automatic parameter binding and validation, content negotiation,
serialisation of a returned object into JSON, filters and middleware, and a lookup structure that
does not degrade as routes are added.

The trade is visibility for reuse. Here the mechanism is three readable lines; in a framework it is
annotation scanning and reflection that you have to trust. Writing it by hand once is what makes the
framework version comprehensible later.

## 5. Why can the browser remain responsive while the server still handles requests sequentially?

Because they are two different machines doing two different things. `fetch()` is asynchronous **in
the browser**: it hands the request to the network stack and returns immediately, so the JavaScript
main thread keeps running, the page keeps rendering, and buttons keep responding. The answer arrives
later as an event.

None of that reaches the server. On the other side there is still one thread that accepts one
connection, serves it and only then calls `accept()` again. Asynchrony describes *who waits* on the
client; concurrency describes *how many things happen at once* on the server. The measurement in
`docs/evidence/local-sequential-limit.txt` shows the difference precisely: while a 5-second request
was being served, a second client's trivial request took 4504 ms — its page was perfectly
interactive the whole time, and its request was simply sitting in the accept queue.

## 6. What changed when the server moved to EC2? What did not change?

**Changed** — everything about location and boundaries:

- The address: `localhost` became a public IPv4 address reachable from any network.
- The network boundary: a security group now decides who may reach port 8080 and port 22.
- The lifecycle: instead of a terminal, systemd starts the process at boot, restarts it on failure
  and keeps it alive after logout, writing to `/var/log/arep/server.log`.
- The environment: a different CPU, a different amount of memory, a different Java installation, a
  different clock and time zone — which is exactly why `/app/time` now returns a value the browser
  clock does not match.
- Latency: milliseconds of loopback became tens of milliseconds of internet.
- Failure modes that did not exist locally: a closed port, a dropped packet, a stopped instance.

**Did not change** — everything about the architecture:

- The same jar, byte for byte. Only `PORT` differs, and it was configuration from the start.
- One thread, one connection at a time. The queue is the same queue, just longer.
- The same routes, the same content types, the same responses, the same statelessness.
- The same single point of failure: EC2 moved *where* the limit lives, not the limit itself.

Cloud is not scale. It is somebody else's computer, with a good API for asking for another one.

## 7. What happens when two users send slow requests at almost the same time?

They serialise. The first connection is accepted and served; the second one waits in the accept
queue of the listening socket, and its total time becomes *its own cost plus everything still ahead
of it*. Two five-second requests mean the second user waits about ten seconds. Ten of them mean the
last user waits about fifty, and their browser will likely give up first.

Two things make it worse than a simple queue. The delay is **unfair**: a cheap request that arrives
one millisecond after an expensive one pays the expensive one's price in full. And the backlog is
**finite** — the listening socket's queue has a limit (`ServerSocket` default 50), so once it fills,
new connections are refused outright and users see a connection error rather than a slow page.

Nothing in the browser can compensate: no amount of asynchrony on the client makes a busy server
answer sooner.

## 8. What is the next architectural limitation you would address — and why should concurrency come before load balancing?

**Concurrency**, on this single server: serving each accepted connection without blocking the accept
loop, whether with a thread per connection, a bounded thread pool, or non-blocking I/O. A bounded
pool is the natural next step, because it also forces the useful questions — how many requests can
be in flight, what happens when the pool is full, what is shared between requests and therefore
needs to be safe.

Concurrency comes first for three reasons.

1. **A load balancer in front of sequential servers multiplies a defect.** Each instance still stops
   completely on one slow request. Three instances buy three simultaneous requests, at three times
   the cost, and any of them can still stall for five seconds. The unit being replicated has to be
   sound before replicating it makes sense.
2. **A single machine is nowhere near its limit.** This server uses one core and blocks on I/O most
   of the time. Paying for more instances before using the one you have is spending money to avoid
   an easier engineering problem.
3. **Concurrency is what forces statelessness to become real.** Distribution only works if any
   instance can answer any request — which is exactly the discipline that surviving concurrent
   requests on one machine imposes. This application is already stateless, so that step is cheap
   here, and that is not an accident.

Load balancing, health checks, autoscaling and multiple availability zones come afterwards, and then
they address the limitation that actually remains: one instance is still one point of failure.

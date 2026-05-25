# Helix Netty Server Architecture

The networking layer is the public face of Helix. It must sustain many concurrent TCP connections with predictable latency and without blocking the event loop.

---

## 1. Why Netty

| Requirement | Netty feature |
|-------------|---------------|
| Many connections | NIO, epoll/kqueue on Linux/macOS |
| Custom protocol | `ByteToMessageDecoder` / encoders |
| Backpressure | `Channel.isWritable()`, watermarks |
| Clean shutdown | `EventLoopGroup.shutdownGracefully()` |

Spring MVC / Tomcat would add HTTP overhead and wrong abstraction for a cache wire protocol.

---

## 2. Thread model

```
┌─────────────────┐
│   bossGroup     │  1 thread: accept()
│  (NioEventLoop) │
└────────┬────────┘
         │ new SocketChannel
         ▼
┌─────────────────┐
│  workerGroup    │  N threads (default 2× cores)
│  per-channel    │  sequential pipeline execution
│  event loop     │
└────────┬────────┘
         │ optional offload
         ▼
┌─────────────────┐
│ storageExecutor │  FixedThreadPool + bounded queue
│ (business)      │  runs CacheService commands
└─────────────────┘
```

**Rule:** Decoding and encoding stay on the worker thread when cheap. Storage calls that may contend on segment locks go to `storageExecutor`; completion writes back via `ctx.channel().eventLoop().execute(...)`.

**Why not run everything on the event loop?** A single hot `SET` triggering LRU eviction under write lock could stall thousands of idle connections on that loop's channels sharing the loop — segment offload isolates tail latency.

**Phase 1 simplification:** Inline GET/SET on event loop acceptable until benchmarks prove contention; Phase 2+ introduces executor offload by default.

---

## 3. Channel pipeline

```java
pipeline.addLast("idleState", new IdleStateHandler(readTimeout, 0, 0));
pipeline.addLast("frameDecoder", new HelixLineDecoder(maxLineLength));
pipeline.addLast("commandDecoder", new HelixCommandDecoder());
pipeline.addLast("responseEncoder", new HelixResponseEncoder());
pipeline.addLast("commandHandler", new HelixCommandHandler(service, executor));
```

| Handler | Role |
|---------|------|
| `IdleStateHandler` | Close idle clients (default 5 min) |
| `HelixLineDecoder` | Accumulate bytes until `\n`, emit `ByteBuf` line |
| `HelixCommandDecoder` | Line → `Command` POJO |
| `HelixResponseEncoder` | `Response` → `ByteBuf` |
| `HelixCommandHandler` | Invoke service, write `ChannelFuture` |

---

## 4. Event loop semantics

- Each `Channel` is pinned to one `EventLoop` for its lifetime.
- Handler methods must not block (no `Thread.sleep`, no JDBC).
- `writeAndFlush` is async; listener for errors on `ChannelFuture`.

**Connection lifecycle hooks:**

- `channelActive` → increment `connections_active`
- `channelInactive` → decrement
- `exceptionCaught` → log, close channel

---

## 5. Buffer management

- Prefer **pooled** allocators: `PooledByteBufAllocator.DEFAULT`
- Decoder releases input via Netty ref-count rules
- Response encoder writes to `ByteBuf` allocated from `ctx.alloc()`

**GC separation:** Network buffers (direct/pooled) vs cache values (`byte[]` on heap) — do not mix Netty buffers into storage engine.

---

## 6. Async request processing pattern

```java
@Override
protected void channelRead0(ChannelHandlerContext ctx, Command cmd) {
    CompletableFuture<Response> future = cacheService.executeAsync(cmd);
    future.whenComplete((resp, err) -> {
        Channel ch = ctx.channel();
        Runnable write = () -> {
            if (err != null) ctx.writeAndFlush(ErrorResponse.internal());
            else ctx.writeAndFlush(resp);
        };
        if (ch.eventLoop().inEventLoop()) write.run();
        else ch.eventLoop().execute(write);
    });
}
```

`executeAsync` may use `CompletableFuture.supplyAsync(..., storageExecutor)`.

---

## 7. Server bootstrap

```java
ServerBootstrap b = new ServerBootstrap();
b.group(bossGroup, workerGroup)
 .channel(NioServerSocketChannel.class)
 .option(ChannelOption.SO_BACKLOG, 1024)
 .childOption(ChannelOption.TCP_NODELAY, true)
 .childOption(ChannelOption.SO_KEEPALIVE, true)
 .childHandler(new HelixChannelInitializer(config, service));
```

**TCP_NODELAY:** Reduces small-packet latency for interactive telnet and pipelined clients.

---

## 8. Graceful shutdown

1. Stop accepting: `bossGroup.shutdownGracefully()`
2. Signal health: `service.stopAccepting()`
3. Wait in-flight on `storageExecutor` (timeout 30s)
4. Flush persistence (Phase 5+)
5. `workerGroup.shutdownGracefully()`

Hook JVM `Runtime.addShutdownHook`.

---

## 9. Capacity planning

| Resource | Starting default |
|----------|------------------|
| Worker threads | `2 × availableProcessors()` |
| Storage pool | same |
| SO_BACKLOG | 1024 |
| Max connections | 10_000 (soft, later enforce) |

Monitor: event loop blocked time (Netty `ResourceLeakDetector` in dev only), executor queue depth.

---

## 10. Interview talking points

- Difference between **boss** and **worker** event loops
- Why blocking the event loop is catastrophic
- How `ChannelPipeline` is a doubly-linked list of handlers
- When to offload vs inline (measurement-driven)

---

*Code lives in `helix-network`; bootstrap in `helix-server`.*

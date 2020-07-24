# (PoC) Asynchronous API to synchronous

Demo application to show how to wrap asynchronous api with synchronous in multi instance environment. While it's only 99 lines of code, basic kowledge of vert.x, EventBus and async programming would really help.



### Whats used

- Kotlin/JVM
- Vert.x framework (EventBus, SharedData, Web)
- Hazelcast cluster manager (clustering, shared Set)



### API

1. Initiate waiting request (timeout 10 seconds)

   ```
   GET /hello?id=<request-id>
   ```

2. Reply to waitng request

   ```
   GET /callback?id=<request-id>&value=<string value to reply>
   ```

3. Basic information on running instances and id's still waiting for response. 

   ```
   GET /status
   ```



### Run

```
./gradlew run
```

Application would join/start hazelcast and use next free HTTP port in cluster, starting from `8081`. 


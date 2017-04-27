# JSON-RPC OkHttp Logging Interceptor

An [OkHttp interceptor](https://github.com/square/okhttp/wiki/Interceptors) which logs [JSON-RPC](http://www.jsonrpc.org/specification) request and response data.

You can format it with your favorite json formatter.

For example using [JSON in Java (org.json)](https://github.com/stleary/JSON-java) is like this code.

```java
JsonRpcLoggingInterceptor logging = new JsonRpcLoggingInterceptor(new JsonRpcLoggingInterceptor.JsonFormatter() {
    @Override
    public String format(String s) {
        try {
            return new JSONArray(s).toString(2);
        } catch (JSONException e) {
            return "";
        }
    }
});
logging.setLevel(JsonRpcLoggingInterceptor.Level.REQUEST_BODY);
OkHttpClient client = new OkHttpClient.Builder()
        .addInterceptor(logging)
        .build();
```

You can change the log level at any time by calling `setLevel`.

To log to a custom location, pass a `Logger` instance to the constructor.

```java
JsonRpcLoggingInterceptor logging = new JsonRpcLoggingInterceptor(new JsonRpcLoggingInterceptor.Logger() {
    @Override
    public void log(String message) {
        Timber.tag("JsonRpcLoggingInterceptor").d(message);
    }
}, new JsonRpcLoggingInterceptor.JsonFormatter() {
    @Override
    public String format(String s) {
        try {
            return new JSONArray(s).toString(2);
        } catch (JSONException e) {
            return "";
        }
    }
});
```

### Download

[![Download](https://api.bintray.com/packages/operandoos/maven/okhttp-json-rpc-logging-interceptor/images/download.svg?version=1.0.0) ](https://bintray.com/operandoos/maven/okhttp-json-rpc-logging-interceptor/1.0.0/link) or grab via Gradle:

```gradle
compile 'com.os.operando.okhttp3.jsonrpc.logging:okhttp-json-rpc-logging-interceptor:1.0.0'
```

or Maven:

```
<dependency>
  <groupId>com.os.operando.okhttp3.jsonrpc.logging</groupId>
  <artifactId>okhttp-json-rpc-logging-interceptor</artifactId>
  <version>1.0.0</version>
  <type>pom</type>
</dependency>
```


### License

```
Apache Version 2.0

Copyright (C) 2017 Shinobu Okano

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
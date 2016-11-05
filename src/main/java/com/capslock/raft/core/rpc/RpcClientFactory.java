package com.capslock.raft.core.rpc;

import com.capslock.raft.core.model.Endpoint;
import com.jakewharton.retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import org.springframework.stereotype.Component;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

/**
 * Created by alvin.
 */
@Component
public class RpcClientFactory {
    public RpcClient createRpcClient(final String host, final int port) {
        return new Retrofit.Builder()
                .baseUrl(buildBaseUrl(host, port))
                .addConverterFactory(JacksonConverterFactory.create())
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build()
                .create(RpcClient.class);
    }

    public RpcClient createRpcClient(final Endpoint endpoint) {
        return createRpcClient(endpoint.getHost(), endpoint.getPort());
    }

    private String buildBaseUrl(final String host, final int port) {
        String url = "http://".concat(host);
        if (port != 80) {
            url = url.concat(":" + port);
        }
        return url;
    }


}

package com.reedelk.rabbitmq.internal;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.reedelk.rabbitmq.component.ConnectionConfiguration;
import com.reedelk.runtime.api.exception.PlatformException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeoutException;

import static com.reedelk.rabbitmq.component.ConnectionConfiguration.*;

public class ConnectionFactoryProvider {

    public static Connection from(String uri) {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        try {
            connectionFactory.setUri(uri);
            return connectionFactory.newConnection();
        } catch (IOException |
                TimeoutException |
                NoSuchAlgorithmException |
                KeyManagementException |
                URISyntaxException exception) {
            throw new PlatformException(exception);
        }
    }

    public static Connection from(ConnectionConfiguration configuration) {
        ConnectionFactory factory = new ConnectionFactory();
        try {
            factory.setUsername(userName(configuration));
            factory.setPassword(password(configuration));
            factory.setVirtualHost(virtualHost(configuration));
            factory.setHost(hostName(configuration));
            factory.setPort(port(configuration));
            factory.setAutomaticRecoveryEnabled(isAutomaticRecovery(configuration));
            return factory.newConnection();
        } catch (IOException | TimeoutException e) {
            throw new PlatformException(e);
        }
    }
}

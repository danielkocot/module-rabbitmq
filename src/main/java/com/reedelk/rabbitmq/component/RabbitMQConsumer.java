package com.reedelk.rabbitmq.component;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.reedelk.rabbitmq.internal.*;
import com.reedelk.rabbitmq.internal.attribute.RabbitMQConsumerAttributes;
import com.reedelk.rabbitmq.internal.exception.RabbitMQConsumerException;
import com.reedelk.runtime.api.annotation.*;
import com.reedelk.runtime.api.component.AbstractInbound;
import com.reedelk.runtime.api.message.content.MimeType;
import org.osgi.service.component.annotations.Component;

import java.io.IOException;

import static com.reedelk.rabbitmq.internal.commons.Messages.RabbitMQConsumer.CONSUME_ERROR;
import static com.reedelk.runtime.api.commons.ComponentPrecondition.Configuration.requireNotBlank;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static org.osgi.service.component.annotations.ServiceScope.PROTOTYPE;

@ModuleComponent("RabbitMQ Consumer")
@ComponentOutput(
        attributes = RabbitMQConsumerAttributes.class,
        payload = { String.class, byte[].class },
        description = "The data consumed from the broker queue")
@Description("Consumes messages from a RabbitMQ broker queue whenever a message " +
                "is published to the subscribed queue. The component might be configured " +
                "to create the source queue if it does not exists already. " +
                "The RabbitMQ Consumer is an Inbound component and it can only be placed " +
                "at the beginning of a flow.")
@Component(service = RabbitMQConsumer.class, scope = PROTOTYPE)
public class RabbitMQConsumer extends AbstractInbound {

    @DialogTitle("RabbitMQ Connection Factory")
    @Property("Connection")
    private ConnectionConfiguration connection;

    @Property("Connection URI")
    @Hint("amqp://guest:guest@localhost:5672")
    @InitValue("amqp://guest:guest@localhost:5672")
    @Example("amqp://guest:guest@localhost:5672")
    @When(propertyName = "configuration", propertyValue = When.NULL)
    @When(propertyName = "configuration", propertyValue = "{'ref': '" + When.BLANK + "'}")
    @Description("Configure a connection using the provided AMQP URI " +
            "containing the connection data.")
    private String connectionURI;

    @Property("Queue Name")
    @Hint("queue_inbound")
    @Example("queue_inbound")
    @Description("Defines the name of the queue this consumer will be consuming messages from.")
    private String queueName;

    @Property("Queue Configuration")
    @Group("Queue Configuration")
    private RabbitMQConsumerQueueConfiguration queueConfiguration;

    @Property("Content Mime Type")
    @MimeTypeCombo
    @DefaultValue(MimeType.AsString.TEXT_PLAIN)
    @Example(MimeType.AsString.APPLICATION_BINARY)
    @Description("The Mime Type of the consumed content allows to create " +
            "a flow message with a suitable content type for the following flow components " +
            "(e.g a 'text/plain' mime type converts the consumed content to a string, " +
            "a 'application/octet-stream' keeps the consumed content as byte array).")
    private String messageMimeType;

    @Example("true")
    @InitValue("true")
    @DefaultValue("false")
    @Property("Auto Acknowledge")
    @Description("True to immediately consider messages delivered by the broker as soon as the flow starts." +
            " False to acknowledge the message only if the flow executed successfully.")
    private boolean autoAck;

    private Channel channel;
    private Connection client;

    @Override
    public void onStart() {
        requireNotBlank(RabbitMQConsumer.class, queueName, "Queue Name must not be empty");
        if (connection == null) {
            requireNotBlank(RabbitMQConsumer.class, connectionURI, "Connection URI must not be empty");
            client = ConnectionFactoryProvider.from(connectionURI);
        } else {
            client = ConnectionFactoryProvider.from(connection);
        }

        try {
            channel = client.createChannel();
            createQueueIfNeeded();
            MimeType queueMessageContentType = MimeType.parse(messageMimeType, MimeType.TEXT_PLAIN);
            if (autoAck) {
                channel.basicConsume(queueName, true,
                        new ConsumerDeliverCallbackAutoAck( this, queueMessageContentType),
                        new ConsumerCancelCallback());
            } else {
                channel.basicConsume(queueName, false,
                        new ConsumerDeliverCallbackExplicitAck( this, queueMessageContentType, channel),
                        new ConsumerCancelCallback());
            }

        } catch (IOException exception) {
            String error = CONSUME_ERROR.format(queueName, exception.getMessage());
            throw new RabbitMQConsumerException(error, exception);
        }
    }

    @Override
    public void onShutdown() {
        ChannelUtils.closeSilently(channel);
        ChannelUtils.closeSilently(client);
    }

    public void setConnection(ConnectionConfiguration connection) {
        this.connection = connection;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public void setMessageMimeType(String messageMimeType) {
        this.messageMimeType = messageMimeType;
    }

    public void setQueueConfiguration(RabbitMQConsumerQueueConfiguration queueConfiguration) {
        this.queueConfiguration = queueConfiguration;
    }

    public void setConnectionURI(String connectionURI) {
        this.connectionURI = connectionURI;
    }

    public void setAutoAck(Boolean autoAck) {
        this.autoAck = autoAck;
    }

    private boolean shouldDeclareQueue() {
        return ofNullable(queueConfiguration)
                .flatMap(queueConfiguration ->
                        of(RabbitMQConsumerQueueConfiguration.isCreateNew(queueConfiguration)))
                .orElse(false);
    }

    private void createQueueIfNeeded() throws IOException {
        boolean shouldDeclareQueue = shouldDeclareQueue();
        if (shouldDeclareQueue) {
            boolean durable = RabbitMQConsumerQueueConfiguration.isDurable(queueConfiguration);
            boolean exclusive = RabbitMQConsumerQueueConfiguration.isExclusive(queueConfiguration);
            boolean autoDelete = RabbitMQConsumerQueueConfiguration.isAutoDelete(queueConfiguration);
            channel.queueDeclare(queueName, durable, exclusive, autoDelete, null);
        }
    }
}

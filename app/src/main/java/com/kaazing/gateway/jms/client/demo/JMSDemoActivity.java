package com.kaazing.gateway.jms.client.demo;

import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

// General classes used for the program
import java.net.PasswordAuthentication;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

// These classes are used for JMS connections and messages
import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

// These classes are used for the TUI
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

// Include these statements with any client
import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.api.GoogleApiClient;
import com.kaazing.net.ws.WebSocketFactory; // WebSocket
import com.kaazing.gateway.jms.client.JmsConnectionFactory; // JMS
import com.kaazing.gateway.jms.client.ConnectionDisconnectedException; // Exceptions
import com.kaazing.gateway.jms.client.util.Tracer; // Logging
import com.kaazing.net.http.HttpRedirectPolicy; // Sets HTTP redirect policy

// Include these statements when a client must authenticate with the Gateway
import com.kaazing.net.auth.BasicChallengeHandler;
import com.kaazing.net.auth.ChallengeHandler;
import com.kaazing.net.auth.LoginHandler;

import static com.kaazing.gateway.jms.client.JmsConnectionFactory.*;

public class JMSDemoActivity extends AppCompatActivity {

    private static String TAG = "com.kaazing.gateway.jms.client.demo";

    private Button connectBtn;
    private Button disconnectBtn;
    private Button subscribeBtn;
    private Button unsubscribeBtn;
    private Button sendBtn;
    private Button clearBtn;
    private CheckBox sendBinaryCheckBox;

    private EditText locationText;
    private EditText destinationText;
    private EditText msgText;
    private TextView logView;

    private JmsConnectionFactory connectionFactory;
    private Connection connection;
    private Session session;

    private DispatchQueue dispatchQueue;

    private HashMap<String, ArrayDeque<MessageConsumer>> consumers = new HashMap<String, ArrayDeque<MessageConsumer>>();
    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private GoogleApiClient client;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        setContentView(R.layout.main);
        Tracer.DEBUG = true;
        Logger logger = Logger.getLogger("com.kaazing.gateway.jms.client");
        logger.setLevel(Level.FINE);

        connectBtn = (Button) findViewById(R.id.connectBtn);
        disconnectBtn = (Button) findViewById(R.id.disconnectBtn);
        subscribeBtn = (Button) findViewById(R.id.subscribeBtn);
        unsubscribeBtn = (Button) findViewById(R.id.unsubscribeBtn);
        sendBtn = (Button) findViewById(R.id.sendBtn);
        clearBtn = (Button) findViewById(R.id.clearBtn);
        sendBinaryCheckBox = (CheckBox) findViewById(R.id.sendBinaryCheckBox);

        locationText = (EditText) findViewById(R.id.locationText);
        destinationText = (EditText) findViewById(R.id.destinationText);
        msgText = (EditText) findViewById(R.id.msgText);
        logView = (TextView) findViewById(R.id.logView);

        //Creem websocket connection
        if (connectionFactory == null) {
            try {
                connectionFactory = JmsConnectionFactory.createConnectionFactory();
                WebSocketFactory webSocketFactory = WebSocketFactory.createWebSocketFactory();
                webSocketFactory.setDefaultFollowRedirect(HttpRedirectPolicy.SAME_DOMAIN);
            } catch (JMSException e) {
                e.printStackTrace();
                logMessage("EXCEPTION 1" + e.getMessage());
            }
        }

        //Se intampla cand apasam butonul connect
        connectBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                connectBtn.setEnabled(false);
                dispatchQueue = new DispatchQueue("DispatchQueue");
                dispatchQueue.start();
                dispatchQueue.waitUntilReady();
                connect();
            }
        });

        //Se intampla cand apasam butonul disconnect
        disconnectBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                disconnect();
            }
        });

        //Se intampla cand apasam butonul subscribe
        subscribeBtn.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                final String destinationName = destinationText.getText().toString();
                logMessage("SUBSCRIBE " + destinationName);
                dispatchQueue.dispatchAsync(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            Destination destination = getDestination(destinationName);
                            if (destination == null) {
                                return;
                            }
                            MessageConsumer consumer = session.createConsumer(destination);
                            ArrayDeque<MessageConsumer> consumerToDestination = consumers.get(destinationName);
                            if (consumerToDestination == null) {
                                consumerToDestination = new ArrayDeque<MessageConsumer>();
                                consumers.put(destinationName, consumerToDestination);
                            }
                            consumerToDestination.add(consumer);
                            consumer.setMessageListener(new DestinationMessageListener());
                        } catch (JMSException e) {
                            e.printStackTrace();
                            logMessage("EXCEPTION 2" + e.getMessage());
                        }
                    }
                });
            }
        });

        //Se intampla cand apsam butonul unsubscribe
        unsubscribeBtn.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                String destinationName = destinationText.getText().toString();
                logMessage("UNSUBSCRIBE - " + destinationName);
                ArrayDeque<MessageConsumer> consumerToDestination = consumers.get(destinationName);
                if (consumerToDestination == null) {
                    return;
                }
                final MessageConsumer consumer = consumerToDestination.poll();
                if (consumer == null) {
                    return;
                }
                dispatchQueue.dispatchAsync(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            consumer.close();
                        } catch (JMSException e) {
                            e.printStackTrace();
                            logMessage("EXCEPTION 3" + e.getMessage());
                        }
                    }
                });

            }
        });

        //Se intampla cand apasam butonul send
        sendBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                final boolean sendBinary = sendBinaryCheckBox.isChecked();
                final String text = msgText.getText().toString();
                logMessage("SENT " + text);
                dispatchQueue.dispatchAsync(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            String destinationName = destinationText.getText().toString();
                            MessageProducer producer = session.createProducer(getDestination(destinationName));
                            Message message;
                            if (sendBinary) {
                                BytesMessage bytesMessage = session.createBytesMessage();
                                bytesMessage.writeUTF(text);
                                message = bytesMessage;
                            } else {
                                message = session.createTextMessage(text);
                            }
                            producer.send(message);
                            producer.close();
                        } catch (JMSException e) {
                            e.printStackTrace();
                            logMessage("EXCEPTION 4" + e.getMessage());
                        }
                    }
                });
            }
        });

        //Se intampla cand apasam butonul clear
        clearBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                logView.setText("");
            }
        });
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();
    }

    public void onPause() {
        if (connection != null) {
            dispatchQueue.dispatchAsync(new Runnable() {
                @Override
                public void run() {
                    try {
                        connection.stop();
                    } catch (JMSException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
        super.onPause();
    }

    public void onResume() {
        if (connection != null) {
            dispatchQueue.dispatchAsync(new Runnable() {
                @Override
                public void run() {
                    try {
                        connection.start();
                    } catch (JMSException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
        super.onResume();
    }

    public void onDestroy() {
        if (connection != null) {
            disconnect();
        }
        super.onDestroy();
    }

    private void connect() {
        logMessage("CONNECTING");
        dispatchQueue.dispatchAsync(new Runnable() {
            @Override
            public void run() {
                try {
                    String location = locationText.getText().toString();
                    logMessage(location);
                    connectionFactory.setGatewayLocation(URI.create(location));

                    connection = connectionFactory.createConnection();
                    connection.start();
                    session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                    logMessage("CONNECTED");
                    connection.setExceptionListener(new ConnectionExceptionListener());
                    updateButtonsForConnected();
                } catch (Exception e) {
                    updateButtonsForDisconnected();
                    e.printStackTrace();
                    logMessage("EXCEPTION 5 " + e.getMessage());
                }
            }
        });
    }

    private void disconnect() {
        logMessage("DISCONNECTING");
        dispatchQueue.removePendingJobs();
        dispatchQueue.quit();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    connection.close();
                    logMessage("DISCONNECTED");
                } catch (JMSException e) {
                    e.printStackTrace();
                    logMessage("EXCEPTION 6" + e.getMessage());
                } finally {
                    connection = null;
                    updateButtonsForDisconnected();
                }
            }
        }).start();
    }

    private Destination getDestination(String destinationName) throws JMSException {
        Destination destination;
        if (destinationName.startsWith("/topic/")) {
            destination = session.createTopic(destinationName);
        } else if (destinationName.startsWith("/queue/")) {
            destination = session.createQueue(destinationName);
        } else {
            logMessage("INVALID DESTINATION NAME: " + destinationName);
            return null;
        }
        return destination;
    }

    @Override
    public void onStart() {
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.connect();
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "JMSDemo Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app deep link URI is correct.
                Uri.parse("android-app://com.kaazing.gateway.jms.client.demo/http/host/path")
        );
        AppIndex.AppIndexApi.start(client, viewAction);
    }

    @Override
    public void onStop() {
        super.onStop();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "JMSDemo Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app deep link URI is correct.
                Uri.parse("android-app://com.kaazing.gateway.jms.client.demo/http/host/path")
        );
        AppIndex.AppIndexApi.end(client, viewAction);
        client.disconnect();
    }

    private class ConnectionExceptionListener implements ExceptionListener {
        public void onException(final JMSException exception) {
            logMessage(exception.getMessage());
            if (exception instanceof ConnectionDisconnectedException) {
                updateButtonsForDisconnected();
            }
        }
    }

    private class DestinationMessageListener implements MessageListener {
        public void onMessage(Message message) {
            try {
                if (message instanceof TextMessage) {
                    logMessage("RECEIVED Message" + ((TextMessage) message).getText());
                } else if (message instanceof BytesMessage) {
                    BytesMessage bytesMessage = (BytesMessage) message;
                    long len = bytesMessage.getBodyLength();
                    byte b[] = new byte[(int) len];
                    bytesMessage.readByte();
                    logMessage("RECEIVED Message " + hexDump(b));
                } else if (message instanceof MapMessage) {
                    MapMessage mapMessage = (MapMessage) message;
                    Enumeration mapNames = mapMessage.getMapNames();
                    while (mapNames.hasMoreElements()) {
                        String key = (String) mapNames.nextElement();
                        Object value = mapMessage.getObject(key);
                        if (value == null) {
                            logMessage(key + ": null");
                        } else if (value instanceof byte[]) {
                            byte[] arr = (byte[]) value;
                            StringBuilder s = new StringBuilder();
                            s.append("[");
                            for (int i = 0; i < arr.length; i++) {
                                if (i > 0) {
                                    s.append(",");
                                }
                                s.append(arr[i]);
                            }
                            s.append("]");
                            logMessage(key + ": " + s.toString() + "(Byte[])");
                        } else {
                            logMessage(key + ": " + value.toString() + "(" + value.getClass().getSimpleName() + ")");
                        }
                    }
                    logMessage("RECEIVED MapMessage: ");
                } else {
                    logMessage("UNKNOWN MESSAGE TYPE" + message.getClass().getSimpleName());
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                logMessage("EXCEPTION 7" + ex.getMessage());
            }
        }

        public String hexDump(byte[] b) {
            if (b.length == 0) {
                return "empty";
            }
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < b.length; i++) {
                out.append(Integer.toHexString(b[i])).append(" ");
            }
            return out.toString();
        }
    }

    private void updateButtonsForConnected() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                connectBtn.setEnabled(false);
                disconnectBtn.setEnabled(true);
                subscribeBtn.setEnabled(true);
                unsubscribeBtn.setEnabled(true);
                sendBtn.setEnabled(true);
            }
        });
    }

    public void updateButtonsForDisconnected() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                connectBtn.setEnabled(true);
                disconnectBtn.setEnabled(false);
                subscribeBtn.setEnabled(false);
                unsubscribeBtn.setEnabled(false);
                sendBtn.setEnabled(false);
            }
        });
    }

    private void logMessage(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (logView.getLineCount() > 100) {
                    logView.setText(message);
                } else {
                    logView.setText(message + "\n" + logView.getText());
                }
            }
        });
    }


}


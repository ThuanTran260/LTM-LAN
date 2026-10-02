package network;

import model.ProtocolMessage;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/** Callback chung cho 3 handler UDP. */
public interface MessageListener {
    void onMessage(ProtocolMessage msg, InetAddress address, int datagramLen);
}

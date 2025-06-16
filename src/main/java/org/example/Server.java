package org.example;

import org.example.protobuf.ChatMessageProto.ChatMessage;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class Server {
    private static final int PORT = 9797;
    private static final Map<String, OutputStream> clients = new HashMap<>();
    private static final Map<String, Queue<ChatMessage>> messageQueues = new HashMap<>();

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("Server läuft auf Port " + PORT);

        while (true) {
            Socket socket = serverSocket.accept();
            new ClientHandler(socket).start();
        }
    }

    private static class ClientHandler extends Thread {
        private final Socket socket;
        private String username;
        private InputStream in;
        private OutputStream out;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            System.out.println("ClientHandler gestartet!");

            try {
                in = socket.getInputStream();
                out = socket.getOutputStream();

                ChatMessage login = ChatMessage.parseDelimitedFrom(in);
                username = login.getFrom().trim();  // Trimmt Leerzeichen
                System.out.println(" Benutzer '" + username + "' meldet sich an.");

                synchronized (clients) {
                    clients.put(username, out);
                    System.out.println(" OutputStream gespeichert für '" + username + "'");
                }

                // Gespeicherte Nachrichten senden
                synchronized (messageQueues) {
                    Queue<ChatMessage> queue = messageQueues.get(username);
                    if (queue != null) {
                        System.out.println("Gespeicherte Nachrichten für '" + username + "': " + queue.size());
                        while (!queue.isEmpty()) {
                            ChatMessage storedMsg = queue.poll();
                            storedMsg.writeTo(out);
                            System.out.println("Nachricht geliefert: " + storedMsg.getFrom() + " -> " + username + ": " + storedMsg.getMessage());
                        }
                        messageQueues.remove(username);
                    }
                }

                while (true) {
                    ChatMessage msg = ChatMessage.parseDelimitedFrom(in);
                    System.out.println(" Neue Nachricht von " + msg.getFrom() + " an " + msg.getTo() + ": " + msg.getMessage());

                    OutputStream recipientOut;
                    synchronized (clients) {
                        recipientOut = clients.get(msg.getTo().trim());
                        System.out.println(" Empfänger '" + msg.getTo() + "' gefunden? " + (recipientOut != null));
                    }

                    if (recipientOut != null) {
                        msg.writeDelimitedTo(recipientOut);
                        recipientOut.flush();
                        System.out.println(" Nachricht gesendet an " + msg.getTo());
                    } else {
                        synchronized (messageQueues) {
                            messageQueues.putIfAbsent(msg.getTo(), new LinkedList<>());
                            messageQueues.get(msg.getTo()).add(msg);
                        }
                        System.out.println(" Empfänger '" + msg.getTo() + "' offline. Nachricht zwischengespeichert.");

                        ChatMessage info = ChatMessage.newBuilder()
                                .setFrom("Server")
                                .setTo(msg.getFrom())
                                .setMessage("Empfänger '" + msg.getTo() + "' ist nicht online. Nachricht gespeichert.")
                                .build();
                        info.writeDelimitedTo(out);
                        out.flush();
                    }
                }
            } catch (IOException e) {
                System.out.println(" Verbindung zu " + username + " getrennt.");
            } finally {
                try {
                    socket.close();
                } catch (IOException ignored) {}

                synchronized (clients) {
                    clients.remove(username);
                }
                System.out.println(" Abgemeldet: " + username);
            }
        }

    }
}

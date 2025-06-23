package org.example;

import org.example.protobuf.ChatMessageProto.ChatMessage;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class Server {
    private static final int PORT = 9797; //Der Server lauscht auf Port 9797
    private static final Map<String, OutputStream> clients = new HashMap<>(); //Speichert für jeden angemeldeten Benutzernamen den zugehörigen OutputStream
    private static final Map<String, Queue<ChatMessage>> messageQueues = new HashMap<>();//Warteschlange pro Benutzername

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("Server läuft auf Port " + PORT);

        while (true) {
            Socket socket = serverSocket.accept(); //auf eingehende Verbindung wirt gewartet
            new ClientHandler(socket).start(); // jede neue Verbindung ClientHandlerThread gestartet
        }
    }

    private static class ClientHandler extends Thread {
        private final Socket socket;
        private String username;
        private InputStream in;
        private OutputStream out;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        } //Thread pro Client (Speichert die Socket-Verbindung, die von main übergeben wurde)

        public void run() {
            System.out.println("ClientHandler gestartet!");

            try {
                in = socket.getInputStream();
                out = socket.getOutputStream();

                ChatMessage login = ChatMessage.parseDelimitedFrom(in); //Der Client sendet zuerst eine Nachricht mit seinem username
                username = login.getFrom().trim();  // Trimmt Leerzeichen
                System.out.println(" Benutzer '" + username + "' meldet sich an.");

                synchronized (clients) {
                    clients.put(username, out);
                    System.out.println(" OutputStream gespeichert für '" + username + "'"); //Der OutputStream des Clients wird gespeichert, um Nachrichten an ihn senden zu können.
                }

                // Gespeicherte Nachrichten senden
                synchronized (messageQueues) {
                    Queue<ChatMessage> queue = messageQueues.get(username);
                    if (queue != null) {
                        System.out.println("Gespeicherte Nachrichten für '" + username + "': " + queue.size());
                        while (!queue.isEmpty()) {
                            ChatMessage storedMsg = queue.poll();
                            storedMsg.writeDelimitedTo(out);
                            out.flush();
                            System.out.println("Nachricht geliefert: " + storedMsg.getFrom() + " -> " + username + ": " + storedMsg.getMessage());
                        }
                        messageQueues.remove(username);
                    }
                }

                while (true) {
                    ChatMessage msg = ChatMessage.parseDelimitedFrom(in); //Der Server liest dauerhaft neue Nachrichten vom Client ein.
                    System.out.println(" Neue Nachricht von " + msg.getFrom() + " an " + msg.getTo() + ": " + msg.getMessage());

                    OutputStream recipientOut; //Der Server prüft, ob der Empfänger online ist.
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

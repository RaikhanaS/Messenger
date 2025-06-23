package org.example;

import org.example.protobuf.ChatMessageProto.ChatMessage;
import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Clients {

    private String username;
    private Socket socket;
    private InputStream in;
    private OutputStream out;

    public Clients(String serverAddress, int port, String username) throws IOException {
        this.username = username;
        this.socket = new Socket(serverAddress, port);
        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();
    }

    public void login() throws IOException {
        ChatMessage login = ChatMessage.newBuilder()
                .setFrom(username)
                .setTo("Server")
                .setMessage("LOGIN")
                .build();
        login.writeDelimitedTo(out);
        out.flush();

    }

    public void sendMessage(String to, String message) throws IOException {
        ChatMessage msg = ChatMessage.newBuilder()
                .setFrom(username)
                .setTo(to)
                .setMessage(message)
                .build();
        msg.writeDelimitedTo(out);
        out.flush();
    }

    //Startet einen neuen Thread, der ständig in überwacht
    public void startListening() {
        new Thread(() -> {
            try {
                while (true) {
                    ChatMessage msg = ChatMessage.parseDelimitedFrom(in);
                    System.out.println("Nachricht empfangen!");
                    System.out.println("<< " + msg.getFrom() + ": " + msg.getMessage());
                }
            } catch (IOException e) {
                System.out.println(" Verbindung beendet.");
            }
        }).start();
    }


    public void close() throws IOException {
        socket.close();
    }

    public static void main(String[] args) throws IOException {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Benutzername: ");
        String username = scanner.nextLine();

        // Erzeuge Clients-Objekt
        Clients client = new Clients("localhost", 9797, username);
        client.login();
        client.startListening();

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // Eingabe-Schleife zum Senden
        try {
            while (true) {
                System.out.print("An (Empfänger): ");
                String to = scanner.nextLine();
                System.out.print("Nachricht: ");
                String message = scanner.nextLine();

                client.sendMessage(to, message);
            }
        } finally {
            client.close();
            System.out.println("Verbindung geschlossen.");
        }

    }


}

package org.example;

import jakarta.websocket.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

@SpringBootApplication
public class ImageSenderApplication implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(ImageSenderApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        String websocketUrl = "ws://localhost:9998/ws";
        String imagePath = "src/main/java/org/example/test2.png";

        File imageFile = new File(imagePath);
        if (!imageFile.exists()) {
            System.err.println("❌ Brak pliku: " + imageFile.getAbsolutePath());
            return;
        }

        byte[] imageBytes = Files.readAllBytes(imageFile.toPath());

        System.out.println("🔌 Łączenie z WebSocket: " + websocketUrl);
        WebSocketClient client = new WebSocketClient(websocketUrl);

        System.out.println("📤 Wysyłam dane...");
        client.sendMessage(imageBytes, client.getSession());

        byte[] response = client.waitForResponse();
        if (response == null) {
            System.err.println("⚠️ Brak odpowiedzi od serwera!");
            return;
        }

        System.out.println("✅ Otrzymano odpowiedź. Długość: " + response.length);
        System.out.println("📂 Odpowiedź: " + Arrays.toString(response));
    }

    /**
     * Wczytuje plik do tablicy bajtów.
     */
    private byte[] loadFile(File file) throws IOException {
        return java.nio.file.Files.readAllBytes(file.toPath());
    }

    /**
     * Przetwarza odpowiedź binarną od serwera i zapisuje jako PNG.
     */
    private void processResponse(ByteBuffer buffer) {
        try {
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);

            BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
            if (image != null) {
                ImageIO.write(image, "png", new File("output_result.png"));
                System.out.println("💾 Zapisano wynik: output_result.png");
            } else {
                System.err.println("⚠️ Błąd dekodowania obrazu!");
            }
        } catch (IOException e) {
            System.err.println("❌ Błąd zapisu odpowiedzi: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

package org.example;

import jakarta.websocket.*;

import java.awt.image.DataBufferByte;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import javax.imageio.ImageIO;
import jakarta.websocket.Session;
import java.awt.image.BufferedImage;

@ClientEndpoint
public class WebSocketClient {

    private Session session;
    private final CountDownLatch latch = new CountDownLatch(1);
    private byte[] response;

    public WebSocketClient(String serverUri) throws Exception {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.setDefaultMaxBinaryMessageBufferSize(15 * 1024 * 1024); // 15 MB
        container.setDefaultMaxTextMessageBufferSize(15 * 1024 * 1024); // 15 MB
        container.connectToServer(this, new URI(serverUri));
    }

    @OnOpen
    public void onOpen(Session session) {
        System.out.println("✅ Połączono z serwerem WebSocket!");
        this.session = session;
    }

    @OnMessage
    public void onMessage(ByteBuffer message) {
        System.out.println("📩 Otrzymano odpowiedź od serwera!");
        response = new byte[message.remaining()];
        message.get(response);
        latch.countDown();
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        System.out.println("❌ Połączenie zamknięte: " + reason);
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        System.err.println("⚠️ Błąd WebSocket: " + throwable.getMessage());
    }

    /**
     * Wysyła obraz w postaci fragmentów. Pierwszy fragment zawiera wymiary obrazu.
     *
     * @param data    bajty obrazu w formacie PNG
     * @param session aktywna sesja WebSocket
     * @throws IOException gdy wystąpi problem z odczytem obrazu lub wysyłką
     */
    public void sendMessage(byte[] data, Session session) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
        int width = image.getWidth();
        int height = image.getHeight();

        // Wysyłaj wymiary tylko w pierwszym fragmencie
        ByteBuffer firstBuffer = ByteBuffer.allocate(8); // 2 inty = 8 bajtów
        firstBuffer.putInt(width);
        firstBuffer.putInt(height);
        firstBuffer.flip();
        session.getAsyncRemote().sendBinary(firstBuffer);

        // Reszta danych bez wymiarów
        byte[] imageBytes = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        final int CHUNK_SIZE = 8 * 1024; // 8 KB

        for (int i = 0; i < imageBytes.length; i += CHUNK_SIZE) {
            int end = Math.min(imageBytes.length, i + CHUNK_SIZE);
            ByteBuffer chunk = ByteBuffer.wrap(imageBytes, i, end - i);
            session.getAsyncRemote().sendBinary(chunk);
        }

        session.getAsyncRemote().sendText("KONIEC");
    }

    public Session getSession() {
        return this.session;
    }

    public byte[] waitForResponse() throws InterruptedException {
        latch.await();
        ByteBuffer buffer = ByteBuffer.wrap(response);
        processResponse(buffer);
        return response;
    }
    private void processResponse(ByteBuffer buffer) {
        // Upewnij się, że folder "ekg" istnieje
        File folder = new File("ekg");
        if (!folder.exists()) {
            folder.mkdirs();
        }

        // Ustawienie pozycji bufora na początek
        buffer.rewind();
        // Pierwszy int - liczba obrazów
        int numImages = buffer.getInt();
        System.out.println("Liczba obrazów: " + numImages);

        // Lista nazw plików odpowiadających wykresom
        String[] fileNames = {
                "I.png", "II.png", "III.png",
                "aVR.png", "aVL.png", "aVF.png",
                "V1.png", "V2.png", "V3.png",
                "V4.png", "V5.png", "V6.png"
        };

        for (int i = 0; i < numImages; i++) {
            // Odczyt parametrów obrazu
            int smallPx = buffer.getInt();
            System.out.println("Px na kratkę: " + (double)smallPx/1000000.0);
            int width = buffer.getInt();
            int height = buffer.getInt();
            int n = buffer.getInt(); // liczba intów z danymi bitmapy

            // Odczyt danych bitmapy
            int[] imageData = new int[n];
            for (int j = 0; j < n; j++) {
                imageData[j] = buffer.getInt();
            }

            // Utwórz obraz w trybie czarno-białym
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
            // Dla każdego piksela obliczamy, w którym int'ie i pod jakim offset'cie się znajduje
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int bitIndex = y * width + x;
                    int intIndex = bitIndex / 32;
                    int bitOffset = bitIndex % 32;
                    int bit = (imageData[intIndex] >> bitOffset) & 1;
                    int rgb = (bit == 1) ? 0x000000 : 0xFFFFFF;
                    image.setRGB(x, y, rgb);
                }
            }

            // Określenie nazwy pliku (jeśli liczba obrazów jest mniejsza niż 12, nadajemy domyślną nazwę)
            String fileName = (i < fileNames.length) ? fileNames[i] : ("chart_" + i + ".png");
            File outputFile = new File(folder, fileName);
            try {
                ImageIO.write(image, "png", outputFile);
                System.out.println("Zapisano obraz: " + outputFile.getAbsolutePath());
            } catch (IOException e) {
                System.err.println("Błąd zapisu obrazu " + fileName + ": " + e.getMessage());
            }
        }
    }
}
